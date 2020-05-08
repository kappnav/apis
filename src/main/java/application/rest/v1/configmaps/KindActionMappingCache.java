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
 * Cache for all KindActionMappings in the cluster. Cached lists of kindactionmappings for a cluster (all
 * namespaces). Because KAMs can exist in any namespace and be created at anytime, it is impossible for API 
 * server to simply do a one time query or query at time of use to find and cache KAMs. 
 * 
 * A watcher on KAM watches all updates across all namespaces with a count indicator on those updates.
 * 
 * With a request for all KAM custom resources in the cluster, cache is queried which will either return the
 * cached values or directly read from the cluster.

 */
public class KindActionMappingCache {

    private static final String CLASS_NAME = KindActionMappingCache.class.getName();
    
    // KindActionMapping constants.
    private static final String KAM_PLURAL = "kindactionmappings";
    private static final String KAM_GROUP = "actions.kappnav.io";
    private static final String KAM_VERSION = "v1";
    
    // AtomicReference containing an object if the kam watcher is running or null otherwise.
    private static final AtomicReference<Object> KAM_WATCHER_REF = new AtomicReference<>(null);

    // KAM Cache for listKAMResources()
    private static final AtomicReference<KAMCache> KAM_CACHE_REF = new AtomicReference<>(null);

    // Synchronization lock used for waking up the "kAppNav KindActionMapping Watcher" thread.
    private static final Object LOCK;

    // Name of the watcher thread.
    private static final String WATCHER_THREAD_NAME = "kAppNav KindActionMapping Watcher";

    // Mod count.
    private static final AtomicLong MOD_COUNT = new AtomicLong(0);

    static class KAMCache {
        private volatile List<JsonObject> kamCacheList;
        private final long modCount;
        KAMCache(List<JsonObject> kamCacheList, long modCount) {
            this.kamCacheList = Collections.unmodifiableList(kamCacheList);;
            this.modCount = modCount;
        }

        List<JsonObject> getKamCacheList() {
            final List<JsonObject> _kamCacheList = this.kamCacheList;
            if (modCount == MOD_COUNT.get()) {
                return _kamCacheList;
            }
            kamCacheList = null;
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
                Object kamWatcher = KAM_WATCHER_REF.get();
                if (kamWatcher== null) {
                    updateModCount(); // Prevents a stale cached list from being returned when the list is restored.
                    kamWatcher = new Object();
                    KAM_WATCHER_REF.set(kamWatcher);
                    updateModCount();
                }

                switch (type) {
                    case "ADDED":
                    case "MODIFIED":
                    case "DELETED":
                        if (Logger.isDebugEnabled()) 
                            Logger.log(getClass().getName(), "processResponse", Logger.LogType.DEBUG, 
                                "KindActionMapping CRs are being add/modified/deleted while the kam watcher is running so update mod count");
                        updateModCount();
                        break;
                }
            }

            @Override
            public void reset(ApiClient client) {
                // If the watch stops or fails delete the cache.
                KAM_WATCHER_REF.set(null);
                KAM_CACHE_REF.set(null);
                updateModCount();
            }
        });
    }

    public static long updateModCount() {
        return MOD_COUNT.incrementAndGet();
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
        Object kamWatcher = KAM_WATCHER_REF.get();
        if (kamWatcher != null) {
            final KAMCache kamCacheObj = KAM_CACHE_REF.get();
            if (kamCacheObj != null) {
                final List<JsonObject> kamCacheList = kamCacheObj.getKamCacheList();
                if (kamCacheList != null) {
                    if (Logger.isDebugEnabled()) {
                        Logger.log(CLASS_NAME,methodName, Logger.LogType.DEBUG, 
                                   "Returning cached KindActionMapping list for all namespaces.");
                    }
                    kamList = kamCacheList;
                } 
            } 

            // No cached value. Retrieve the list directly from the cluster and cache it. 
            if (kamList == null) {                             
                List<JsonObject> kams = listKAMCustomResourcesFromCluster(client);
                if (kams != null ) {
                    final long modCount = MOD_COUNT.get();
                    KAM_CACHE_REF.set(new KAMCache(kams, modCount));
                    kamList = Collections.unmodifiableList(kams);
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
            if (kams != null)
                kamList = Collections.unmodifiableList(kams);
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
        return KAppNavEndpoint.getItemAsList(client, kamResource);
    }
}
