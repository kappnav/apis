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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.namespace.QName;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonElement;
import com.ibm.kappnav.logging.Logger;
import com.squareup.okhttp.Call;

import application.rest.v1.KAppNavConfig;
import application.rest.v1.Selector;
import application.rest.v1.Watcher;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapList;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;

/**
 * Cache for frequently accessed config maps, including action/status/section maps and built-in
 * maps that have been installed by the kAppNav operator. Only use this cache for maps that are
 * monitored by the watcher thread.
 */
public class ConfigMapCache {

    private static final String CLASS_NAME = ConfigMapCache.class.getName();

    private static final String KAPPNAV_NAMESPACE = KAppNavConfig.getkAppNavNamespace();

    // AtomicReference containing the current instance of the ConfigMap cache or null if there is no cache available.
    // The map uses QNames as keys to represent the name and namespace pair identifying a ConfigMap.
    // The map uses SoftReferences as values to allow GC to reclaim the ConfigMaps if required to keep the JVM from running out of memory.
    private static final AtomicReference<Map<QName,SoftReference<V1ConfigMap>>> MAP_CACHE_REF = new AtomicReference<>(null);

    // This Special value used to represent null. A ConcurrentHashMap cannot directly store null values.
    private static final SoftReference<V1ConfigMap> NULL_REFERENCE = new SoftReference<>(null);

    // Synchronization lock used for waking up the "kAppNav ConfigMap Watcher" thread.
    private static final Object LOCK;

    // Name of the watcher thread.
    private static final String WATCHER_THREAD_NAME = "kAppNav ConfigMap Watcher";

    static {
        LOCK = Watcher.start(new Watcher.Handler<V1ConfigMap>() {

            @Override
            public String getWatcherThreadName() {
                return WATCHER_THREAD_NAME;
            }
            
            public List<V1ConfigMap> listResources(ApiClient client, AtomicReference<String> resourceVersion) throws ApiException {
                final CoreV1Api api = new CoreV1Api();
                api.setApiClient(client);
                final Selector selector = getSelector();
                final V1ConfigMapList list = api.listNamespacedConfigMap(KAPPNAV_NAMESPACE, null, null, null, null, selector.toString(), null, null, null, null);
                resourceVersion.set(list.getMetadata().getResourceVersion());
                return list.getItems();
            }

            @Override
            public Call createWatchCall(ApiClient client, String resourceVersion) throws ApiException {
                final CoreV1Api api = new CoreV1Api();
                api.setApiClient(client);
                final Selector selector = getSelector();
                return api.listNamespacedConfigMapCall(KAPPNAV_NAMESPACE, null, null, null, null, selector.toString(), null, resourceVersion, null, Boolean.TRUE, null, null);
            }
            
            @SuppressWarnings("serial")
            @Override
            public Type getWatchType() {
                return new TypeToken<Watch.Response<V1ConfigMap>>() {}.getType();
            }

            @Override
            public void processResponse(ApiClient client, String type, V1ConfigMap object) {
                // Invalidate the cache if any changes are made to the ConfigMaps under watch.
                MAP_CACHE_REF.set(new ConcurrentHashMap<>());
                if (Logger.isDebugEnabled()) {
                    V1ObjectMeta meta = object.getMetadata();
                    Logger.log(getClass().getName(), "processResponse", Logger.LogType.DEBUG, "ConfigMap Cache invalidated due to ConfigMap change event :: Type: " 
                            + type + " :: Name: " + meta.getName() + " :: Namespace: " + meta.getNamespace());
                }
            }

            @Override
            public void reset(ApiClient client) {
                // If the watch stops or fails delete the cache.
                MAP_CACHE_REF.set(null);
            }
            
            private Selector getSelector() {
                final Selector selector = new Selector();
                selector.addMatchLabel("app.kubernetes.io/managed-by", "kappnav-operator");
                return selector;
            }
        });
    }

    public static JsonElement getConfigMapAsJSON(ApiClient client, String namespace, String name) {
        V1ConfigMap map = getConfigMap(client, namespace, name);
        if (map != null) {
            return client.getJSON().getGson().toJsonTree(map);
        }
        return null;
    }

    public static V1ConfigMap getConfigMap(ApiClient client, String namespace, String name) {
        QName tuple = new QName(namespace, name);
        Map<QName,SoftReference<V1ConfigMap>> mapCache = MAP_CACHE_REF.get();
        if (mapCache != null) {
            if (mapCache.containsKey(tuple)) {
                SoftReference<V1ConfigMap> ref = mapCache.get(tuple);
                V1ConfigMap map = ref.get();
                if (map != null || ref == NULL_REFERENCE) {
                    return map;
                }
                if (Logger.isDebugEnabled()) {
                    Logger.log(CLASS_NAME, "getConfigMap", Logger.LogType.DEBUG, 
                            "The entry in the cache for ConfigMap, Name: " + name + ", Namespace: " 
                                    + namespace + " was garbage collected. Attempting to refresh the value from the cluster.");
                }
            }
        }
        else {
            // Wake up the working thread if there's no cache.
            synchronized (LOCK) {
                LOCK.notify();
            }
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "getConfigMap", Logger.LogType.DEBUG, 
                        "No ConfigMap cache available. Notify thread (" + WATCHER_THREAD_NAME + ") to awaken and re-establish the cache.");
            }
        }
        try {
            CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);

            V1ConfigMap map = api.readNamespacedConfigMap(name, namespace, null, null, null);
            if (mapCache != null) {
                mapCache.put(tuple, new SoftReference<>(map));
                if (Logger.isDebugEnabled()) {
                    Logger.log(CLASS_NAME, "getConfigMap", Logger.LogType.DEBUG, 
                            "Found ConfigMap, Name: " + name + ", Namespace: " 
                                    + namespace + ". Storing it in the cache.");
                }
            }
            return map;
        }
        catch (ApiException e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "getConfigMap", Logger.LogType.DEBUG, "Caught ApiException: " + e.toString());
            }
        }
        // No ConfigMap. Store null reference in the cache.
        if (mapCache != null) {
            mapCache.put(tuple, NULL_REFERENCE);
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, "getConfigMap", Logger.LogType.DEBUG, 
                        "ConfigMap, Name: " + name + ", Namespace: " 
                                + namespace + " not found. Storing a null reference in the cache.");
            }
        }
        return null;
    }
}
