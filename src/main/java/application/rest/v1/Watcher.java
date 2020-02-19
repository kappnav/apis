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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.util.Watch;

/**
 * Convenience class for building a watch on a set of resources.
 */
public class Watcher {
    
    /**
     * A callback interface that the watcher invokes to set up the watch and report results.
     */
    public interface Handler<T> {
        /**
         * Returns the name of the thread.
         */
        public String getWatcherThreadName();
        /**
         * Returns the current set of resources under watch. Sets the resource version list on the AtomicReference.
         */
        public List<T> listResources(ApiClient client, AtomicReference<String> resourceVersion) throws ApiException;
        /**
         * Returns the watch call for the resource under watch, created using the given resource version.
         */
        public Call createWatchCall(ApiClient client, String resourceVersion) throws ApiException;
        /**
         * Returns the Java type of the resources under watch.
         */
        public Type getWatchType();
        /**
         * Processes a response returned from the watch.
         */
        public void processResponse(ApiClient client, String type, T object);
        /**
         * Clean up method for when the watch ends or fails.
         */
        public void shutdown(ApiClient client);
    }
    
    // Returns a synchronization object for waking up the thread.
    public static <T> Object start(final Handler<T> h) {
        return start(h, false);
    }
    
    // Returns a synchronization object for waking up the thread if autoRestart is false.
    // Returns null if autoRestart is true.
    public static <T> Object start(final Handler<T> h, boolean autoRestart) {
        // Synchronization lock used for waking up the watcher thread.
        final Object LOCK = new Object();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        ApiClient client = KAppNavEndpoint.getApiClient();
                        OkHttpClient httpClient = client.getHttpClient();
                        // Infinite timeout
                        httpClient.setReadTimeout(0, TimeUnit.SECONDS);
                        client.setHttpClient(httpClient);
                        
                        final AtomicReference<String> resourceVersion = new AtomicReference<>();
                        final long now = System.currentTimeMillis();
                        Watch<T> watch = null;
                        try {
                            List<T> list = h.listResources(client, resourceVersion);
                            list.forEach(v -> {
                                h.processResponse(client, "ADDED", v);
                            }); 
                            
                            watch = Watch.createWatch(
                                    client,
                                    h.createWatchCall(client, resourceVersion.get()),
                                    h.getWatchType());
                            
                            if (Logger.isDebugEnabled()) {
                                Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Watch started for " + h.getClass().getName() + ".");
                            }

                            // Note: While the watch is active this iterator loop will block waiting for notifications of resource changes from the Kube API. 
                            for (Watch.Response<T> item : watch) {
                                h.processResponse(client, item.type, item.object);
                            }
                        }
                        catch (Exception e) {
                            if (Logger.isDebugEnabled()) {
                                Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Caught Exception from running watch: " + e.toString());
                            }
                        }
                        finally {
                            h.shutdown(client);
                            if (watch != null) {
                                watch.close();
                            }
                            if (Logger.isDebugEnabled()) {
                                Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Watch completed for " + h.getClass().getName() + " after " + (System.currentTimeMillis() - now) + " ms.");
                            }
                        }
                    }
                    catch (Exception e) {
                        if (Logger.isDebugEnabled()) {
                            Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Caught Exception from watch initialization or shutdown: " + e.toString());
                        }
                    }
                    if (!autoRestart) {
                        // Sleep until notified by another thread, then try to re-establish the watch.
                        synchronized (LOCK) {
                            try {
                                LOCK.wait();
                            }
                            catch (InterruptedException e) {
                                if (Logger.isDebugEnabled()) {
                                    Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Thread (" + h.getWatcherThreadName() + ") awakened.");
                                }
                            }
                        }
                    }
                }
            }
        });
        t.setName(h.getWatcherThreadName());
        t.setDaemon(true);
        t.start();
        return !autoRestart ? LOCK : null;
    }
    
    /**
     * Utility method for processing a generic list from the CustomObjectsApi.
     */
    public static List<Object> processCustomObjectsApiList(ApiClient client, Object objectList, AtomicReference<String> resourceVersion) {
        JsonObject jsonList = KAppNavEndpoint.getItemAsObject(client, objectList);
        if (jsonList != null) {
            String version = KAppNavEndpoint.getResourceVersion(jsonList);
            resourceVersion.set(version);
            List<Object> list = new ArrayList<>();
            KAppNavEndpoint.writeItemsToList(jsonList, list);
            return list;
        }
        return Collections.emptyList();
    }
}
