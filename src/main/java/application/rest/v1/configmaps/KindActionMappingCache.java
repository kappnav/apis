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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * To handle the dynamic lifecycle of KAMs, all KAMs cross all namespaces are queried and cached during 
 * API server startup.
 * 
 * A watch on KAMs across all namespaces:
 *   when a KAM is created, cache it
 *   when a KAM is updated, cache it (replace old kam with the new)
 *   when a KAM is deleted, evict it from the cache
 * 
 * Anytime a resource's action list must be calculated, use only the cached KAMs - do not query for more, as they should already all be cached.
 */
public class KindActionMappingCache {

    private static final String CLASS_NAME = KindActionMappingCache.class.getName();
    private static final String KAM_KIND_PROPERTY_VALUE = "KindActionMapping";
    
    // KindActionMapping constants.
    private static final String KAM_PLURAL = "kindactionmappings";
    private static final String KAM_GROUP = "actions.kappnav.io";
    private static final String KAM_VERSION = "v1";
    
    // Synchronization lock used for waking up the "kAppNav KindActionMapping Watcher" thread.
    //private static Object LOCK;
    
    // AtomicReference containing the current instance of the KindActionMapping cache or null if there is no cache available.
    // The map uses QNames as keys to represent the name and namespace pair identifying a KindActionMapping.
    // The map uses SoftReferences as values to allow GC to reclaim the KindActionMappings if required to keep the JVM from running out of memory.
    private static final AtomicReference<Map<QName,SoftReference<JsonObject>>> MAP_CACHE_REF = new AtomicReference<>(null);
    
    // Name of the watcher thread.
    private static final String WATCHER_THREAD_NAME = "kAppNav KindActionMapping Watcher";

    private static Map<QName, SoftReference<JsonObject>> kamCache = null;

    public static void startKAMCRWatcher() {
        Watcher.start(new Watcher.Handler<Object>() {

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
                            "\n List KAM Custom Resources for all namespaces with" + "\n group = " + KAM_GROUP
                                    + "\n version = " + KAM_VERSION + "\n plural = " + KAM_PLURAL);
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
                                if (kamCache == null) {
                                    kamCache = new ConcurrentHashMap<>();
                                    MAP_CACHE_REF.set(kamCache);
                                }
                                kamCache.put(kamQName, new SoftReference<>(objItem));
                                updated = true;
                                break;
                            case "DELETED":
                                if (kamCache != null) {
                                    kamCache.remove(kamQName);
                                    updated = true;
                                }
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
                Logger.log(CLASS_NAME, "listKAMCustomResources", Logger.LogType.ENTRY, ""); 

        List<JsonObject> kamList = null;
        if (kamCache == null) {
            if (Logger.isDebugEnabled())
                Logger.log(CLASS_NAME, "listKAMCustomResources", Logger.LogType.DEBUG, "KAM cache is null and retrieve kams from the cluster."); 
            kamCache = new ConcurrentHashMap<>();
            MAP_CACHE_REF.set(kamCache);
            kamList = listKAMCustomResourcesFromCluster(client);
            
            if ( (kamList == null) || (kamList.isEmpty()) ){
                if (Logger.isExitEnabled()) 
                    Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "No KindActionMapping CR found.");
                return kamList;
            }

            // populate the KAM cache with the kams retrieved from the cluster
            populateKAMCache(kamList);
        } 

        if (Logger.isDebugEnabled())
            Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, "retrieve the kams from the cache");
        kamList = new ArrayList<>();
        Iterator<SoftReference<JsonObject>> kamIter = kamCache.values().iterator();
        while (kamIter.hasNext()) {
            kamList.add((JsonObject) kamIter.next().get());
        } 

        if (Logger.isExitEnabled())
                Logger.log(CLASS_NAME, "listKAMCustomResources", Logger.LogType.EXIT, ""); 
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
                "\n List KAM Custom Resources for all namespaces with" +
                "\n group = " + "actions.kappnav.io" + 
                "\n namespace = kappnav and name = default");
        }

        Object kamResource = coa.listClusterCustomObject(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, 
                             null, null, null);
        return KAppNavEndpoint.getItemAsList(client, kamResource);
    }

    /**
     * Populate the KAM cache with the KAM CRs retrieved from the cluster
     * 
     * @param kamList a list of KAM CR instances in a cluster
     */
    private static void populateKAMCache(List<JsonObject> kamList) {
        String methodName = "populateKAMCache";
        kamList.forEach (v -> {
            JsonElement items = v.get(KindActionMappingProcessor.ITEMS_PROPERTY_NAME);
            if ((items != null) && (items.isJsonArray())) {
                JsonArray itemsArray = items.getAsJsonArray();

                // go though all kams to get the qualified configmaps defined in those kams   
                // Sort the configmaps found in order of hierarchy & precedence
                if (itemsArray != null) {
                    itemsArray.forEach(kam-> {  
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
            }
            }
        });
    }

}
