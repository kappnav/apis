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

import java.lang.ref.SoftReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.namespace.QName;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 * Cache for all KindActionMappings in the cluster. Cached lists of kindactionmappings for a cluster (all
 * namespaces). Because KAMs can exist in any namespace and be created at anytime, it is impossible for API 
 * server to simply do a one time query or query at time of use to find and cache KAMs. 
 * 
 * With a request for all KAM custom resources in the cluster, cache is queried which will either return the
 * cached values or directly read from the cluster.
 * 
 * A watch on KAMs across all namespaces:
 *   when a KAM is created, cache it
 *   when a KAM is updated, cache it (replace old kam with the new)
 *   when a KAM is deleted, evict it from the cache
 */
public class KindActionMappingCache {

    private static final String CLASS_NAME = KindActionMappingCache.class.getName();
    private static final String KAM_KIND_PROPERTY_VALUE = "KindActionMapping";
    
    // KindActionMapping constants.
    private static final String KAM_PLURAL = "kindactionmappings";
    private static final String KAM_GROUP = "actions.kappnav.io";
    private static final String KAM_VERSION = "v1";
    
    // AtomicReference containing the current instance of the KindActionMapping cache or null if there is no cache available.
    // The map uses QNames as keys to represent the name and namespace pair identifying a KindActionMapping.
    // The map uses SoftReferences as values to allow GC to reclaim the KindActionMappings if required to keep the JVM from running out of memory.
    private static final AtomicReference<Map<QName,SoftReference<JsonObject>>> KAM_CACHE_REF = new AtomicReference<>(null);

    // KAM Cache for listKAMResources()
    private static final AtomicReference<KAMCache> KAM_CACHE = new AtomicReference<>(null);

    // Synchronization lock used for waking up the "kAppNav KindActionMapping Watcher" thread.
    private static final Object LOCK;

    // Name of the watcher thread.
    private static final String WATCHER_THREAD_NAME = "kAppNav KindActionMapping Watcher";

    // Mod count.
    private static final AtomicLong MOD_COUNT = new AtomicLong(0);

    static class KAMCache {
        private volatile Map<QName,SoftReference<JsonObject>> kamCacheMap;
        private final long modCount;
        KAMCache(Map<QName,SoftReference<JsonObject>> kamCacheMap, long modCount) {
            this.kamCacheMap = kamCacheMap;
            this.modCount = modCount;
        }

        Map<QName,SoftReference<JsonObject>> getKamCacheMap() {
            final Map<QName,SoftReference<JsonObject>> _kamCacheMap = this.kamCacheMap;
            if (modCount == MOD_COUNT.get()) {
                return _kamCacheMap;
            }
            kamCacheMap = null;
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
                String methodName = "listResources";
                final CustomObjectsApi coa = new CustomObjectsApi();
                coa.setApiClient(client);
                if (Logger.isDebugEnabled()) {
                    Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG,
                            "\n List KAM Custom Resources for all namespaces with" + "\n group = " + 
                            KAM_GROUP + "\n version = " + KAM_VERSION + "\n plural = " + KAM_PLURAL);
                }

                Object kamCRs = coa.listClusterCustomObject(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, null, null, null);
                return Watcher.processCustomObjectsApiList(client, kamCRs, resourceVersion);
            }

            @Override
            public Call createWatchCall(ApiClient client, String resourceVersion) throws ApiException {
                final CustomObjectsApi coa = new CustomObjectsApi();
                coa.setApiClient(client);
                return coa.listClusterCustomObjectCall(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, null, resourceVersion,
                        Boolean.TRUE, null, null);
            }

            @SuppressWarnings("serial")
            @Override
            public Type getWatchType() {
                return new TypeToken<Watch.Response<Object>>() {
                }.getType();
            }

            @Override
            public void processResponse(ApiClient client, String type, Object object) {
                String methodName = "processResponse";

                Map<QName,SoftReference<JsonObject>> kamCache = KAM_CACHE_REF.get();
                if (kamCache == null) {
                    updateModCount(); // Prevents a stale cached list from being returned when the map is restored.
                    kamCache = new ConcurrentHashMap<>();
                    KAM_CACHE_REF.set(kamCache);
                    updateModCount();
                }

                JsonObject objItem = KAppNavEndpoint.getItemAsObject(client, object);
                if (objItem != null) { 
                    String namespace = KAppNavEndpoint.getComponentNamespace(objItem);
                    String name = KAppNavEndpoint.getComponentName(objItem);
                    String kind = KAppNavEndpoint.getComponentKind(objItem);

                    if (kind.equals(KAM_KIND_PROPERTY_VALUE)) { // process KindActionMapping CRs only
                        QName kamQName = new QName(namespace, name);
                        boolean updated = false;
                        switch (type) {
                            case "ADDED":
                            case "MODIFIED":
                                kamCache.put(kamQName, new SoftReference<>(objItem));
                                updateModCount();
                                updated = true;
                                break;
                            case "DELETED":
                                kamCache.remove(kamQName);
                                updateModCount();
                                updated = true;
                                break;
                        }
                        if (updated && Logger.isDebugEnabled())
                            Logger.log(getClass().getName(), methodName, Logger.LogType.DEBUG,
                                    "KindActionMapping cache updated due to KindActionMapping change event :: Type: "
                                            + type + " :: Name: " + name + " :: Namespace: " + namespace);
                    } else {
                        if (Logger.isDebugEnabled())
                            Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, "Not KAM kind, kind = " + kind);
                    }
                }
            }

            @Override
            public void reset(ApiClient client) {
                // If the watch stops or fails delete the cache.
                KAM_CACHE_REF.set(null);
                KAM_CACHE.set(null);
                updateModCount();
            }
        });
    }

    /**
     * Get all "KindActionMapping" custom resources in the kam cache. If the cache is not available yet,
     * set it up and populate it with kams retrieved from the cluster.
     * 
     * @param client ApiClient
     * @return a list of KAM CR instances in the kam cache
     * @throws ApiException
     */
    public static List<JsonObject> listKAMCustomResources(ApiClient client) throws ApiException {
        String methodName = "listKAMCustomResources";
        if (Logger.isEntryEnabled())
                Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY, ""); 

        List<JsonObject> kamList = null;
        Map<QName,SoftReference<JsonObject>> kamCache = KAM_CACHE_REF.get();
        if (kamCache != null) {
            final KAMCache kamCacheObj = KAM_CACHE.get();
            if (kamCacheObj != null) {
                final Map<QName, SoftReference<JsonObject>> kamCacheMap = kamCacheObj.getKamCacheMap();
                if (kamCacheMap != null) {
                    if (Logger.isDebugEnabled()) {
                        Logger.log(CLASS_NAME,methodName, Logger.LogType.DEBUG, 
                                   "Returning cached KindActionMapping list for all namespaces.");
                    }
                    kamList = Collections.unmodifiableList(retrieveKAMsFromCache(client, kamCacheMap));
                } 
            } 

            // No cached value. Retrieve the list directly from the cluster and cache it. 
            if (kamList == null) {                             
                kamList = listKAMCustomResourcesFromCluster(client);
                if (kamList != null ) {
                    populateKAMCache(kamList, kamCache);
                    final long modCount = MOD_COUNT.get();
                    KAM_CACHE.set(new KAMCache(kamCache, modCount));
                    kamList = Collections.unmodifiableList(retrieveKAMsFromCache(client, kamCache));
                    if (Logger.isDebugEnabled()) {
                        Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, 
                           "Caching KindActionMapping list for all namespaces.");
                    }
                }
            }
        } else {  // No Cached value Retrieve the list directly from the cluster and cache it.
            // Wake up the working thread if there's no cache.
            synchronized (LOCK) {
                LOCK.notify();
            }

            if (Logger.isDebugEnabled())
                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, 
                        "No KAM cache available. Notify thread (" + WATCHER_THREAD_NAME + 
                        ") to awaken and re-establish the cache and retrieve kams from the cluster.");
            List<JsonObject> kams = listKAMCustomResourcesFromCluster(client);
            if (kams != null) {
                kamList = Collections.unmodifiableList(kams);
            }
        }
        
        if (Logger.isExitEnabled())
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "Found " + kamList.size() + " kams"); 
        return kamList;  
    }

    /**
     * Retrieve all "KindActionMapping" custom resources in a cluster
     * 
     * @param client ApiClient
     * @return a list of KAM CR instances in a cluster
     * @throws ApiException
     */
    private static  List<JsonObject> listKAMCustomResourcesFromCluster(ApiClient client) 
        throws ApiException {
        String methodName = "listKAMCustomResourcesCluster";
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        if (Logger.isDebugEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG,
                       "List KAM Custom Resources for all namespaces with" + "\n group = " + 
                       KAM_GROUP + "\n version = " + KAM_VERSION + "\n plural = " + KAM_PLURAL);
        }

        Object kamResource = coa.listClusterCustomObject(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, 
                             null, null, null);
        List<JsonObject> kams = KAppNavEndpoint.getItemAsList(client, kamResource);
        List<JsonObject> kamItemList = new ArrayList<JsonObject>();

        if (kams != null) {
            kams.forEach(v -> {
                JsonElement items = v.get(KindActionMappingProcessor.ITEMS_PROPERTY_NAME);
                if ((items != null) && (items.isJsonArray())) {
                    JsonArray itemsArray = items.getAsJsonArray();
                    if (itemsArray != null) {
                        itemsArray.forEach(kam-> {
                            kamItemList.add(kam.getAsJsonObject());
                        });
                    }
                }
            });
        }
        return kamItemList;
    }
    
    private static long updateModCount() {
        return MOD_COUNT.incrementAndGet();
    }

    /**
     * Populate the KAM cache with the KAM CRs retrieved from the cluster
     *
     * @param kamList a list of KAM CR instances in a cluster
     */
    private static void populateKAMCache(List<JsonObject> kamItemList, Map<QName,SoftReference<JsonObject>> kamCache) {
        String methodName = "populateKAMCache";
        if (Logger.isEntryEnabled())
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY, "");

        // store kam in the cache
        kamItemList.forEach (kam -> {
            if ( (kam != null) && (kam.isJsonObject()) ) {
                String name = KindActionMappingProcessor.getKAMName(kam);
                String namespace = KindActionMappingProcessor.getKAMNamespace(kam);

                if (Logger.isDebugEnabled())
                    Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG,
                            "store kam (" + name + "@" +namespace + ") in the kam cache");
                QName kamQName = new QName(namespace, name);
                kamCache.put(kamQName, new SoftReference<>(kam.getAsJsonObject()));
            }
        });

        if (Logger.isExitEnabled())
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "");
    } 
    
    /**
     * Retrieve kams from the KAM cache
     *
     * @param kamCache kam cache
     * @return kamList list of kams from cache
     */
    private static List<JsonObject> retrieveKAMsFromCache(ApiClient client, Map<QName, SoftReference<JsonObject>> kamCache) {
        String methodName = "retrieveKAMsFromCache";
        if (Logger.isEntryEnabled())
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY, "");
        
        List<JsonObject> kamList = new ArrayList<>();
        Collection<SoftReference<JsonObject>> kamCacheValues = kamCache.values();
        Iterator<SoftReference<JsonObject>> valIter = kamCacheValues.iterator();
        while (valIter.hasNext()) {
            kamList.add(valIter.next().get());
        }
        if (Logger.isExitEnabled())
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "Found " + kamList.size() + " kams from the kam cache");
		return kamList;
	}
}
