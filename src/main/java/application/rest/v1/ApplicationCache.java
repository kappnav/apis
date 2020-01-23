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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.squareup.okhttp.Call;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch.Response;

public class ApplicationCache {
    
    private static final String APP_GROUP = "app.k8s.io";
    private static final String APP_VERSION = "v1beta1";
    private static final String APP_PLURAL = "applications";
    
    // Synchronization lock used for waking up the "kAppNav ConfigMap Watcher" thread.
    private static final Object LOCK;
    
    private static final AtomicReference<Map<String,Map<String,JsonObject>>> MAP_CACHE_REF = new AtomicReference<>(null);
    
    static {
        LOCK = Watcher.start(new Watcher.Handler<Object>() {
            
            // Name of the watcher thread.
            private static final String WATCHER_THREAD_NAME = "kAppNav Application Watcher";

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

            @Override
            public void processResponse(ApiClient client, Response<Object> response) {
                Map<String,Map<String,JsonObject>> mapCache = MAP_CACHE_REF.get();
                if (mapCache == null) {
                    mapCache = new ConcurrentHashMap<>();
                    MAP_CACHE_REF.set(mapCache);
                }
                JsonObject o = KAppNavEndpoint.getItemAsObject(client, response.object);
                if (o != null) {
                    String namespace = KAppNavEndpoint.getComponentNamespace(o);
                    String name = KAppNavEndpoint.getComponentName(o);
                    switch (response.type) {
                        case "ADDED":
                        case "MODIFIED":
                            break;
                        case "DELETED":
                            break;
                    }
                } 
            }

            @Override
            public void shutdown(ApiClient client) {
                // If the watch stops or fails delete the cache.
                MAP_CACHE_REF.set(null);
            }
        });
    }
    
    public static List<JsonObject> listApplicationObject(ApiClient client) throws ApiException {
        synchronized (LOCK) {
            LOCK.notify();
        }
        return null;
    }
    
    public static List<JsonObject> listNamespacedApplicationObject(ApiClient client, String namespace) throws ApiException {
        synchronized (LOCK) {
            LOCK.notify();
        }
        return null;
    }
}
