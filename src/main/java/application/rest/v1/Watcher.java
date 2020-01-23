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

import java.util.concurrent.TimeUnit;

import com.google.common.reflect.TypeToken;
import com.ibm.kappnav.logging.Logger;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.util.Watch;

public class Watcher {
    
    public interface Handler<T> {
        public String getWatcherThreadName();
        public Call createWatchCall(ApiClient client) throws ApiException;
        public void processResponse(ApiClient client, Watch.Response<T> response);
        public void shutdown(ApiClient client);
    }
    
    public static <T> Object start(final Handler<T> h) {
        // Synchronization lock used for waking up the watcher thread.
        final Object LOCK = new Object();
        Thread t = new Thread(new Runnable() {
            @SuppressWarnings("serial")
            @Override
            public void run() {
                while (true) {
                    try {
                        ApiClient client = KAppNavEndpoint.getApiClient();
                        OkHttpClient httpClient = client.getHttpClient();
                        // Infinite timeout
                        httpClient.setReadTimeout(0, TimeUnit.SECONDS);
                        client.setHttpClient(httpClient);
                        
                        Watch<T> watch = null;
                        try {
                            watch = Watch.createWatch(
                                    client,
                                    h.createWatchCall(client),
                                    new TypeToken<Watch.Response<T>>() {}.getType());

                            // Note: While the watch is active this iterator loop will block waiting for notifications of ConfigMap changes from the Kube API. 
                            for (Watch.Response<T> item : watch) {
                                h.processResponse(client, item);
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
                        }
                    }
                    catch (Exception e) {
                        if (Logger.isDebugEnabled()) {
                            Logger.log(getClass().getName(), "run", Logger.LogType.DEBUG, "Caught Exception from watch initialization or shutdown: " + e.toString());
                        }
                    }
                    // Sleep until a request is made for a ConfigMap, then try to re-establish the watch.
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
        });
        t.setName(h.getWatcherThreadName());
        t.setDaemon(true);
        t.start();
        return LOCK;
    }
}
