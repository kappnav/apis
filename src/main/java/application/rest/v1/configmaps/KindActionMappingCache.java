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

package application.rest.v1.configmaps;

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

import application.rest.v1.KAppNavEndpoint;
import application.rest.v1.Watcher;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;

/**
 * Cache for all KindActionMappings in the cluster. Cached lists of kindactionmappings for all 
 * namespaces or a specific namespace are invalidated when a CUD operation is
 * performed on a KindActionMapping. 
 */
public class KindActionMappingCache {

    private static final String CLASS_NAME = KindActionMappingCache.class.getName();
    
    // KindActionMapping constants.
    private static final String KAM_PLURAL = "kindactionmappings";
    private static final String KAM_GROUP = "actions.kappnav.io";
    private static final String KAM_VERSION = "v1";
    
    // Synchronization lock used for waking up the "kAppNav KindActionMapping Watcher" thread.
    private static final Object LOCK;
    
    // AtomicReference containing the current instance of the kindactionmapping cache or null if there is no cache available.
    // The key is the namespace. The value is a map of kindactionmappings in that namespace.
    private static final AtomicReference<Map<String,Map<String,JsonObject>>> MAP_CACHE_REF = new AtomicReference<>(null);
    
    // Cached list for listKAMCustomResources()
    private static final AtomicReference<CachedList> CACHED_LIST = new AtomicReference<>(null);
      
    // Mod count.
    private static final AtomicLong MOD_COUNT = new AtomicLong(0);
    
    // Name of the watcher thread.
    private static final String WATCHER_THREAD_NAME = "kAppNav KindActionMapping Watcher";
    
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
            public List<Object> listResources(ApiClient client, AtomicReference<String> resourceVersion)
                    throws ApiException {     
                final CustomObjectsApi coa = new CustomObjectsApi();
                coa.setApiClient(client);
                Object o = coa.listClusterCustomObject(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, null, null, null);
                return Watcher.processCustomObjectsApiList(client, o, resourceVersion);
            }

            @Override
            public Call createWatchCall(ApiClient client, String resourceVersion) throws ApiException {
                final CustomObjectsApi coa = new CustomObjectsApi();
                coa.setApiClient(client);
                return coa.listClusterCustomObjectCall(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, null, resourceVersion, Boolean.TRUE, null, null);
            }
            
            @SuppressWarnings("serial")
            @Override
            public Type getWatchType() {
                return new TypeToken<Watch.Response<Object>>() {}.getType();
            }

            @Override
            public void processResponse(ApiClient client, String type, Object object) {

                Map<String,Map<String,JsonObject>> mapCache = MAP_CACHE_REF.get();
                if (mapCache == null) {
                    updateModCount(); // Prevents a stale cached list from being returned when the map is restored.
                    mapCache = new ConcurrentHashMap<>();
                    MAP_CACHE_REF.set(mapCache);
                    updateModCount();
                }

                JsonObject o = KAppNavEndpoint.getItemAsObject(client, object);
                if (o != null) {
                    String namespace = KAppNavEndpoint.getComponentNamespace(o);
                    String name = KAppNavEndpoint.getComponentName(o);

                    Map<String, JsonObject> nsMap = mapCache.get(namespace);
                    boolean updated = false;
                    switch (type) {
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
                    if (updated && Logger.isDebugEnabled()) 
                        Logger.log(getClass().getName(), "processResponse", Logger.LogType.DEBUG, "KindActionMapping cache updated due to KindActionMapping change event :: Type: " 
                                + type + " :: Name: " + name + " :: Namespace: " + namespace);
                }
            }

            @Override
            public void reset(ApiClient client) {
                // If the watch stops or fails delete the caches.
                MAP_CACHE_REF.set(null);
                CACHED_LIST.set(null);
                updateModCount();
            }
        });
    }

    public static long updateModCount() {
        return MOD_COUNT.incrementAndGet();
    }

    public static List<JsonObject> listKAMCustomResources(ApiClient client) throws ApiException {
        String methodName = "listKAMCRObjects";
        final Map<String,Map<String,JsonObject>> mapCache = MAP_CACHE_REF.get();
        if (mapCache != null) {
            final CachedList cachedList = CACHED_LIST.get();
            if (cachedList != null) {
                final List<JsonObject> list = cachedList.getList();
                if (list != null) {
                    if (Logger.isDebugEnabled()) 
                        Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, 
                                "Returning cached KindActionMapping list for all namespaces.");
                    return list;
                }
            }

            // No cached value. Retrieve the list directly from the cluster and cache it.
            final long modCount = MOD_COUNT.get();
            final List<JsonObject> list = listKAMCustomResourcesFromCluster(client);
            CACHED_LIST.set(new CachedList(list, modCount));
            if (Logger.isDebugEnabled())
                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, 
                        "Caching KindActionMapping list for all namespaces.");

            return Collections.unmodifiableList(list);
        } else {
            // Wake up the working thread if there's no cache.
            synchronized (LOCK) {
                LOCK.notify();
            }

            // No cache. Retrieve the list directly from the cluster.
            if (Logger.isDebugEnabled()) 
                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, 
                        "No KindActionMapping cache available. Notify thread (" + WATCHER_THREAD_NAME + ") to awaken and re-establish the cache.");           
            return listKAMCustomResourcesFromCluster(client);
        }
    }

    /**
     * Get all "KindActionMapping" custom resources in a cluster
     * 
     * @param client apiVersion
     * @return a list of KAM CR instances in a cluster
     * @throws ApiException
     */
    protected static List<JsonObject> listKAMCustomResourcesFromCluster(ApiClient client) 
        throws ApiException {
        String methodName = "listKAMCustomResourcesFromCluster";
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        if (Logger.isDebugEnabled()) {
            Logger.log(CLASS_NAME, methodName,
                    Logger.LogType.DEBUG, 
                "\n List KAM Custom Resources for all namespaces with" +
                "\n group = " + KAM_GROUP + 
                "\n version = " + KAM_VERSION +
                "\n plural = " + KAM_PLURAL);
        }

        Object kamResource = coa.listClusterCustomObject(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, 
                             null, null, null);
        return KAppNavEndpoint.getItemAsList(client, kamResource);
    }
}
