/*
 * Copyright 2020 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package application.rest.v1;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;
import com.squareup.okhttp.Call;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;

/**
 * Cache for all Applications in the cluster. Cached lists of applications for all 
 * namespaces or a specific namespace are invalidated when a CRUD operation is
 * performed on an Application. 
 */
public class ApplicationCache {
    
    private static final String CLASS_NAME = ApplicationCache.class.getName();
    
    // Application constants.
    private static final String APP_GROUP = "app.k8s.io";
    private static final String APP_VERSION = "v1beta1";
    private static final String APP_PLURAL = "applications";
    
    // Synchronization lock used for waking up the "kAppNav Application Watcher" thread.
    private static final Object LOCK;
    
    // AtomicReference containing the current instance of the application cache or null if there is no cache available.
    // The key is the namespace. The value is a map of applications in that namespace.
    private static final AtomicReference<Map<String,Map<String,JsonObject>>> MAP_CACHE_REF = new AtomicReference<>(null);
    
    // Cached list for listApplicationObject()
    private static final AtomicReference<CachedList> CACHED_LIST = new AtomicReference<>(null);
    
    // Cached list for listNamespacedApplicationObject()
    private static final AtomicReference<Map<String,CachedList>> CACHED_NS_LIST_MAP = new AtomicReference<>(null);
    
    // Mod count.
    private static final AtomicLong MOD_COUNT = new AtomicLong(0);
    
    // Name of the watcher thread.
    private static final String WATCHER_THREAD_NAME = "kAppNav Application Watcher";
    
    static class CachedList {
        private volatile List<JsonObject> list;
        private final long modCount;
        CachedList(List<JsonObject> list, long modCount) {
            this.list = Collections.unmodifiableList(list);
            this.modCount = modCount;
        }
        List<JsonObject> getList() {
            final List<JsonObject> _list = list;
            if (modCount == MOD_COUNT.get()) {
                return _list;
            }
            list = null;
            return null;
        }
    }
    
    static {
        LOCK = Watcher.start(new Watcher.Handler<Object>() {
            
            @Override
            public String getWatcherThreadName() {
                return WATCHER_THREAD_NAME;
            }

            @Override
            public Call createWatchCall(ApiClient client) throws ApiException {
                final CustomObjectsApi coa = new CustomObjectsApi();
                coa.setApiClient(client);
                return coa.listClusterCustomObjectCall(APP_GROUP, APP_VERSION, APP_PLURAL, null, null, null, Boolean.TRUE, null, null);
            }
            
            @SuppressWarnings("serial")
            @Override
            public Type getWatchType() {
                return new TypeToken<Watch.Response<Object>>() {}.getType();
            }

            @Override
            public void processResponse(ApiClient client, Response<Object> response) {
                Map<String,Map<String,JsonObject>> mapCache = MAP_CACHE_REF.get();
                if (mapCache == null) {
                    updateModCount(); // Prevents a stale cached list from being returned when the map is restored.
                    mapCache = new ConcurrentHashMap<>();
                    MAP_CACHE_REF.set(mapCache);
                    updateModCount();
                }
                JsonObject o = KAppNavEndpoint.getItemAsObject(client, response.object);
                if (o != null) {
                    String namespace = KAppNavEndpoint.getComponentNamespace(o);
                    String name = KAppNavEndpoint.getComponentName(o);
                    Map<String, JsonObject> nsMap = mapCache.get(namespace);
                    boolean updated = false;
                    switch (response.type) {
                        case "ADDED":
                        case "MODIFIED":
                            if (nsMap == null) {
                                nsMap = new ConcurrentHashMap<>();
                                mapCache.put(namespace, nsMap);
                            }
                            nsMap.put(name, o);
                            updateModCount();
                            updated = true;
                            break;
                        case "DELETED":
                            if (nsMap != null) {
                                nsMap.remove(name);
                                if (nsMap.isEmpty()) {
                                    mapCache.remove(namespace);
                                }
                                updateModCount();
                                updated = true;
                            }
                            break;
                    }
                    if (updated && Logger.isDebugEnabled()) {
                        Logger.log(getClass().getName(), "processResponse", Logger.LogType.DEBUG, "Application cache updated due to Application change event :: Type: " 
                                + response.type + " :: Name: " + name + " :: Namespace: " + namespace);
                    }
                }
            }

            @Override
            public void shutdown(ApiClient client) {
                // If the watch stops or fails delete the caches.
                MAP_CACHE_REF.set(null);
                CACHED_LIST.set(null);
                CACHED_NS_LIST_MAP.set(null);
                updateModCount();
            }
        });
    }
    
    public static List<JsonObject> listApplicationObject(ApiClient client) throws ApiException {
        final Map<String,Map<String,JsonObject>> mapCache = MAP_CACHE_REF.get();
        if (mapCache != null) {
            final CachedList cachedList = CACHED_LIST.get();
            if (cachedList != null) {
                final List<JsonObject> list = cachedList.getList();
                if (list != null) {
                    if (Logger.isDebugEnabled()) {
                        Logger.log(CLASS_NAME, "listNamespacedApplicationObject", Logger.LogType.DEBUG, 
                                "Returning cached Application list for all namespaces.");
                    }
                    return list;
                }
            }
            // No cached value. Retrieve the list directly from the cluster and cache it.
            final long modCount = MOD_COUNT.get();
            final List<JsonObject> list = listApplicationObject0(client);
            CACHED_LIST.set(new CachedList(list, modCount));
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "listNamespacedApplicationObject", Logger.LogType.DEBUG, 
                        "Caching Application list for all namespaces.");
            }
            return Collections.unmodifiableList(list);
        }
        else {
            // Wake up the working thread if there's no cache.
            synchronized (LOCK) {
                LOCK.notify();
            }
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "listApplicationObject", Logger.LogType.DEBUG, 
                        "No Application cache available. Notify thread (" + WATCHER_THREAD_NAME + ") to awaken and re-establish the cache.");
            }
        }
        // No cache. Retrieve the list directly from the cluster.
        return listApplicationObject0(client);
    }
    
    private static List<JsonObject> listApplicationObject0(ApiClient client) throws ApiException {
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        final Object o = coa.listClusterCustomObject(APP_GROUP, APP_VERSION, APP_PLURAL, null, null, null, null);
        return KAppNavEndpoint.getItemsAsList(client, o);
    }
    
    public static List<JsonObject> listNamespacedApplicationObject(ApiClient client, String namespace) throws ApiException {
        final Map<String,Map<String,JsonObject>> mapCache = MAP_CACHE_REF.get();
        if (mapCache != null) {
            Map<String,CachedList> cachedListMap = CACHED_NS_LIST_MAP.get();
            if (cachedListMap != null) {
                CachedList cachedList = cachedListMap.get(namespace);
                if (cachedList != null) {
                    final List<JsonObject> list = cachedList.getList();
                    if (list != null) {
                        if (Logger.isDebugEnabled()) {
                            Logger.log(CLASS_NAME, "listNamespacedApplicationObject", Logger.LogType.DEBUG, 
                                    "Returning cached Application list for namespace " + namespace + ".");
                        }
                        return list;
                    }
                }
            }
            else {
                cachedListMap = new ConcurrentHashMap<>();
                CACHED_NS_LIST_MAP.set(cachedListMap);
            }
            // No cached value. Retrieve the list directly from the cluster and cache it.
            final long modCount = MOD_COUNT.get();
            List<JsonObject> list = listNamespacedApplicationObject0(client, namespace);
            cachedListMap.put(namespace, new CachedList(list, modCount));
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "listNamespacedApplicationObject", Logger.LogType.DEBUG, 
                        "Caching Application list for namespace " + namespace + ".");
            }
            return Collections.unmodifiableList(list);
        }
        else {
            // Wake up the working thread if there's no cache.
            synchronized (LOCK) {
                LOCK.notify();
            }
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "listNamespacedApplicationObject", Logger.LogType.DEBUG, 
                        "No Application cache available. Notify thread (" + WATCHER_THREAD_NAME + ") to awaken and re-establish the cache.");
            }
        }
        // No cache. Retrieve the list directly from the cluster.
        return listNamespacedApplicationObject0(client, namespace);
    }
    
    private static List<JsonObject> listNamespacedApplicationObject0(ApiClient client, String namespace) throws ApiException {
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        final Object o = coa.listNamespacedCustomObject(APP_GROUP, APP_VERSION, namespace, APP_PLURAL, null, null, null, null);
        return KAppNavEndpoint.getItemsAsList(client, o);
    }
    
    public static JsonObject getNamespacedApplicationObject(ApiClient client, String namespace, String name) throws ApiException {
        final Map<String,Map<String,JsonObject>> mapCache = MAP_CACHE_REF.get();
        if (mapCache != null) {
            Map<String,JsonObject> nsMap = mapCache.get(namespace);
            if (nsMap != null) {
                JsonObject o = nsMap.get(name);
                if (o != null) {
                    if (Logger.isDebugEnabled()) {
                        Logger.log(CLASS_NAME, "getNamespacedApplicationObject", Logger.LogType.DEBUG, 
                                "Returning Application, Name: " + name + ", Namespace: "  + namespace + " from the cache.");
                    }
                    return o;
                }
            }
        }
        else {
            // Wake up the working thread if there's no cache.
            synchronized (LOCK) {
                LOCK.notify();
            }
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "getNamespacedApplicationObject", Logger.LogType.DEBUG, 
                        "No Application cache available. Notify thread (" + WATCHER_THREAD_NAME + ") to awaken and re-establish the cache.");
            }
        }
        // No value cached. Retrieve the object directly from the cluster.
        if (Logger.isDebugEnabled()) {
            Logger.log(CLASS_NAME, "getNamespacedApplicationObject", Logger.LogType.DEBUG, 
                    "Application, Name: " + name + ", Namespace: "  + namespace + " is not available in the cache. Attempting to retrieve it from the cluster.");
        }
        return getNamespacedApplicationObject0(client, namespace, name);
    }
    
    private static JsonObject getNamespacedApplicationObject0(ApiClient client, String namespace, String name) throws ApiException {
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        final Object o = coa.getNamespacedCustomObject(APP_GROUP, APP_VERSION, namespace, APP_PLURAL, name);
        return KAppNavEndpoint.getItemAsObject(client, o);
    }
    
    public static long updateModCount() {
        return MOD_COUNT.incrementAndGet();
    }
 }
