/*
 * Copyright 2019 IBM Corporation
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

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;

import com.ibm.kappnav.logging.Logger;

/**
 * This class contains relevant configuration from the 'kappnav-config' map.
 */
@ApplicationScoped
public class KAppNavConfig {
    private static final String className = ActionsEndpoint.class.getName();

    private static final String KAPPNAV_NAMESPACE;
    private static final String KAPPNAV_CONFIG_NAMESPACE = "KAPPNAV_CONFIG_NAMESPACE";
    private static final String KAPPNAV_DEFAULT_NAMESPACE = "kappnav";
    
    private static final String KAPPNAV_KUBE_ENV;
    private static final String KUBE_ENV = "KUBE_ENV";
    private static final String DEFAULT_KUBE_ENV = "okd";
    
    static {
        KAPPNAV_NAMESPACE = getEnvironmentVariable(KAPPNAV_CONFIG_NAMESPACE, KAPPNAV_DEFAULT_NAMESPACE);
        KAPPNAV_KUBE_ENV = getEnvironmentVariable(KUBE_ENV, DEFAULT_KUBE_ENV);
    }
    
    private static String getEnvironmentVariable(String name, String defaultValue) {
        Logger.log(className, "getEnvironmentVariable", Logger.LogType.ENTRY, "For name=" + name + ", defaultValue=" + defaultValue);
        try {
            return AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    // Check environment variable.
                    final String var = System.getenv(name);
                    if (var != null && !var.trim().isEmpty()) {
                        Logger.log(className, "getEnvironmentVariable", Logger.LogType.EXIT, var);
                        return var;
                    }
                    Logger.log(className, "getEnvironmentVariable", Logger.LogType.EXIT, defaultValue);
                    return defaultValue;
                }
            });
        } catch (SecurityException se) {
            Logger.log(className, "getEnvironmentVariable", Logger.LogType.EXIT, "Caught SecurityException returning defaultValue="+defaultValue);
            return defaultValue;
        }
    }
    
    private static final String MAP_NAME = "kappnav-config";
    
    private static final String KAPPNAV_SA_NAME = "kappnav-sa-name";
    private static final String KAPPNAV_SA_NAME_DEFAULT = "kappnav-sa";
    
    private static final String STATUS_UNKNOWN = "status-unknown";
    private static final String STATUS_UNKNOWN_DEFAULT = "Unknown";
    
    private static final String APP_STATUS_PRECDENCE = "app-status-precedence";
    private static final List<String> APP_STATUS_PRECDENCE_DEFAULT = 
            Collections.singletonList(STATUS_UNKNOWN_DEFAULT);
    
    private final String serviceAccountName;
    private final String statusUnknown;
    private final List<String> appStatusPrecedence;
    
    public KAppNavConfig() throws ApiException, IOException {
        this(KAppNavEndpoint.getApiClient());
    }
    
    public KAppNavConfig(ApiClient client) {
        String serviceAccountName = KAPPNAV_SA_NAME_DEFAULT;
        String statusUnknown = STATUS_UNKNOWN_DEFAULT;
        List<String> appStatusPrecedence = APP_STATUS_PRECDENCE_DEFAULT;
        try {
            CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            final V1ConfigMap map = api.readNamespacedConfigMap(MAP_NAME, KAPPNAV_NAMESPACE, null, null, null);
            Map<String,String> data = map.getData();
            if (data != null) {
                // Get 'kappnav-sa-name' value if it exists in the map.
                if (data.containsKey(KAPPNAV_SA_NAME)) {
                    serviceAccountName = data.get(KAPPNAV_SA_NAME);
                    if (serviceAccountName == null || serviceAccountName.trim().isEmpty()) {
                        serviceAccountName = KAPPNAV_SA_NAME_DEFAULT;
                    }
                }
                // Get 'status-unknown' value if it exists in the map.
                if (data.containsKey(STATUS_UNKNOWN)) {
                    statusUnknown = data.get(STATUS_UNKNOWN);
                    if (statusUnknown == null || statusUnknown.trim().isEmpty()) {
                        statusUnknown = STATUS_UNKNOWN_DEFAULT;
                    }
                }
                // Get 'app-status-precedence' value if it exists in the map.
                boolean setAppStatusPrecedence = false;
                if (data.containsKey(APP_STATUS_PRECDENCE)) {
                    String json = data.get(APP_STATUS_PRECDENCE);
                    if (json != null && !json.trim().isEmpty()) {
                        try {
                            JsonParser parser = new JsonParser();
                            JsonElement element = parser.parse(json);
                            if (element.isJsonArray()) {
                                List<String> values = new ArrayList<>();
                                JsonArray array = element.getAsJsonArray();
                                array.forEach(x -> {
                                    if (x.isJsonPrimitive()) {
                                        values.add(x.getAsString());
                                    }
                                    else {
                                        values.add(null);
                                    }
                                });
                                // Use the array if all of the values were good.
                                if (!values.contains(null)) {
                                    appStatusPrecedence = Collections.unmodifiableList(values);
                                    setAppStatusPrecedence = true;
                                }
                            }
                        }
                        catch (JsonSyntaxException e) {
                            Logger.log(className, "KAppNavConfig contructor", Logger.LogType.DEBUG, "Caught JsonSyntaxException " + e.toString());
                        }
                    }
                }
                if (!setAppStatusPrecedence && !STATUS_UNKNOWN_DEFAULT.equals(statusUnknown)) {
                    appStatusPrecedence = Collections.singletonList(statusUnknown);
                }
            }
        }
        catch (ApiException e) {
            Logger.log(className, "KAppNavConfig contructor", Logger.LogType.DEBUG, "Caught ApiException " + e.toString());
        }
        this.serviceAccountName = serviceAccountName;
        this.statusUnknown = statusUnknown;
        this.appStatusPrecedence = appStatusPrecedence;     
    }
    
    public static String getkAppNavNamespace() {
        return KAPPNAV_NAMESPACE;
    }
    
    public static String getkAppNavKubeEnvironment() {
        return KAPPNAV_KUBE_ENV;
    }
    
    // Returns value of 'kappnav-sa-name'.
    public String getkAppNavServiceAccountName() {
        return this.serviceAccountName;
    }
    
    // Returns value of 'status-unknown'.
    public String getStatusUnknown() {
        return this.statusUnknown;
    }
    
    // Returns value of 'app-status-precedence'.
    public List<String> getAppStatusPrecedence() {
        return this.appStatusPrecedence;
    }
    
    public boolean isKnownStatus(String statusValue) {
        return getAppStatusPrecedence().contains(statusValue) || statusUnknown.equals(statusValue);
    }
}