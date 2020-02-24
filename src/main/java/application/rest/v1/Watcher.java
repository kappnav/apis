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
         * Returns the current set of resources under watch. Sets the resource version of the list on the AtomicReference.
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
        public void reset(ApiClient client);
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
            
            // Indicates that the resource requested is no longer available and will not be available again.
            private static final int HTTP_STATUS_CODE_GONE = 410;
            
            @Override
            public void run() {
                while (true) {
                    boolean gone = false;
                    try {
                        ApiClient client = KAppNavEndpoint.getApiClient();
                        OkHttpClient httpClient = client.getHttpClient();
                        // Infinite timeout
                        httpClient.setReadTimeout(0, TimeUnit.SECONDS);
                        client.setHttpClient(httpClient);
                        
                        final AtomicReference<String> resourceVersion = new AtomicReference<>();
                        long watchStartTime = 0L;
                        Watch<T> watch = null;
                        try {
                            List<T> list = h.listResources(client, resourceVersion);
                            if (Logger.isDebugEnabled()) {
                                Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Retrieved current list of resources for " +
                                        h.getClass().getName() + " at resourceVersion (" + resourceVersion.get() + ").");
                            }
                            
                            list.forEach(v -> {
                                h.processResponse(client, "ADDED", v);
                            });
                            
                            watchStartTime = System.currentTimeMillis();
                            
                            if (Logger.isDebugEnabled()) {
                                Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Watch starting for " + h.getClass().getName() +
                                        " at resourceVersion (" + resourceVersion.get() + ").");
                            }
                            
                            OUTER: while (true) {
                                watch = Watch.createWatch(
                                        client,
                                        h.createWatchCall(client, resourceVersion.get()),
                                        h.getWatchType());
                                
                                // Note: While the watch is active this iterator loop will block waiting for notifications of resource changes from the Kube API. 
                                for (Watch.Response<T> item : watch) {
                                    if (item.status != null || "ERROR".equals(item.type)) {
                                        if (item.status != null) {
                                            final Integer code = item.status.getCode();
                                            if (code != null) {
                                                final int codeValue = code.intValue();
                                                if (codeValue == HTTP_STATUS_CODE_GONE) {
                                                    gone = true;
                                                    if (Logger.isDebugEnabled()) {
                                                        Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG,
                                                                "The resourceVersion (" + resourceVersion.get() +
                                                                ") is too old (status 410). The watch for " + h.getClass().getName() +
                                                                " will be restarted from the current resource version."); 
                                                    }
                                                }
                                                else if (Logger.isDebugEnabled()) {
                                                    Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG,
                                                            "Status (" + codeValue + ") returned from the watch on resourceVersion (" +
                                                            resourceVersion.get() + ". The watch for " + h.getClass().getName() +
                                                            " will be restarted from the current resource version.");
                                                }
                                            }
                                        }
                                        else if (Logger.isDebugEnabled()) {
                                            Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG,
                                                    "An unknown error was returned from the watch on resourceVersion (" +
                                                    resourceVersion.get() + ". The watch for " + h.getClass().getName() +
                                                    " will be restarted from the current resource version.");
                                        }
                                        // Breaking out of the outer loop in order to restart the watch from the current resource version.
                                        break OUTER;
                                    }
                                    h.processResponse(client, item.type, item.object);
                                }
                                watch.close();
                                watch = null;
                                
                                if (Logger.isDebugEnabled()) {
                                    Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Watch restarting for " + h.getClass().getName() +
                                            " at resourceVersion (" + resourceVersion.get() + ").");
                                }
                            }
                        }
                        catch (Exception e) {
                            if (Logger.isDebugEnabled()) {
                                Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Caught Exception from running watch: " + e.toString());
                            }
                        }
                        finally {
                            h.reset(client);
                            if (watch != null) {
                                watch.close();
                            }
                            if (Logger.isDebugEnabled() && watchStartTime > 0L) {
                                Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Watch completed for " + h.getClass().getName() +
                                        " at resourceVersion (" + resourceVersion.get() + ") after " + (System.currentTimeMillis() - watchStartTime) + " ms.");
                            }
                        }
                    }
                    catch (Exception e) {
                        if (Logger.isDebugEnabled()) {
                            Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Caught Exception from watch initialization or shutdown: " + e.toString());
                        }
                    }
                    // If the version of the resource being watched is gone or the creator of the watch requested an auto-restart, restart immediately.
                    if (gone || !autoRestart) {
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
