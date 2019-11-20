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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.squareup.okhttp.ConnectionSpec;

import application.rest.v1.actions.ValidationException;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.util.Config; 

public abstract class KAppNavEndpoint {
    
    protected static final String NAME_PATTERN_ONE_OR_MORE = "^[a-z0-9-.:]+$";
    protected static final String NAME_PATTERN_ZERO_OR_MORE = "^[a-z0-9-.:]*$";
    
    private static final boolean DISABLE_TRUST_ALL_CERTS;
    private static final String DISABLE_TRUST_ALL_CERTS_PROPERTY = "kappnav.disable.trust.all.certs";
    
    static {
        boolean b;
        try {
            String val = AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    final String prop = System.getProperty(DISABLE_TRUST_ALL_CERTS_PROPERTY);
                    if (prop != null) {
                        return prop;
                    }
                    // Check environment variable if the system property hasn't been set.
                    return System.getenv(DISABLE_TRUST_ALL_CERTS_PROPERTY);
                }
            });
            b = (val != null) && (!"false".equals(val));
        } catch (SecurityException se) {
            System.out.println("JUNIKE.. caught SecurityException " + se.toString());
            b = false;
        }
        DISABLE_TRUST_ALL_CERTS = b;
    }
    
    // Annotation properties.
    private static final String METADATA_PROPERTY_NAME = "metadata";
    private static final String ANNOTATIONS_PROPERTY_NAME = "annotations";
    private static final String NAME_PROPERTY_NAME = "name";
    private static final String NAMESPACE_PROPERTY_NAME = "namespace";
    private static final String KAPPNAV_STATUS_PROPERTY_NAME = "kappnav.status";
    private static final String KAPPNAV_SUB_KIND_PROPERTY_NAME = "kappnav.subkind";
    
    // Status object properties.
    private static final String VALUE_PROPERTY_NAME = "value";
    private static final String FLYOVER_PROPERTY_NAME = "flyover";
    private static final String FLYOVER_NLS_PROPERTY_NAME = "flyover.nls";
    private static final String STATUS_OPERATOR = "${status}";
    
    private static final String APP_GROUP = "app.k8s.io";
    private static final String APP_VERSION = "v1beta1";
    private static final String APP_PLURAL = "applications";

    private static final String DEFAULT_NAMESPACE = "default";
    
    protected Object listApplicationObject(ApiClient client) throws ApiException {
        System.out.println("JUNIKE.. listApplicationObject");
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        return coa.listClusterCustomObject(APP_GROUP, APP_VERSION, APP_PLURAL, null, null, null, null);
    }
    
    protected Object listNamespacedApplicationObject(ApiClient client, String namespace) throws ApiException {
        System.out.println("JUNIKE.. listNamespacedApplicationObject for namespace=" + namespace);
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        return coa.listNamespacedCustomObject(APP_GROUP, APP_VERSION,
                encodeURLParameter(namespace), APP_PLURAL, null, null, null, null);
    }

    public static List<JsonObject> getItemsAsList(ApiClient client, Object resources) {
        System.out.println("JUNIKE.. getItemsAsList for resources=" + resources.toString());
        final List<JsonObject> result = new ArrayList<>();
        final JsonElement element = client.getJSON().getGson().toJsonTree(resources);

        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();
            JsonElement items = root.get("items");
            if (items != null && items.isJsonArray()) {
                JsonArray itemsArray = items.getAsJsonArray();
                itemsArray.forEach(v -> {
                    if (v != null && v.isJsonObject()) {
                        result.add(v.getAsJsonObject());
                    }
                });
            }
        }
        System.out.println("JUNIKE.. getItemsAsList return " + result.toString());
        return result;
    }

    public static List<JsonObject> getItemAsList(ApiClient client, Object resource) {
        System.out.println("JUNIKE.. getItemAsList for resource=" + resource);
        final JsonElement element = client.getJSON().getGson().toJsonTree(resource);
        System.out.println("JUNIKE.. element=" + element.toString());
        if (element != null && element.isJsonObject()) {
            return Collections.singletonList(element.getAsJsonObject());
        }
        return Collections.emptyList();
    }
    
    public static JsonObject getItemAsObject(ApiClient client, Object resource) {
        System.out.println("JUNIKE.. getItemAsObject for resource=" + resource);
        final JsonElement element = client.getJSON().getGson().toJsonTree(resource);
        System.out.println("JUNIKE.. element=" + element.toString());
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }
    
    public static String getComponentSubKind(JsonObject component) {
        System.out.println("JUNIKE.. getComponentSubKind for component=" + component.toString());
        final JsonObject metadata = component.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            final JsonObject annotations = metadata.getAsJsonObject(ANNOTATIONS_PROPERTY_NAME);
            if (annotations != null) {
                JsonElement e = annotations.get(KAPPNAV_SUB_KIND_PROPERTY_NAME);
                if (e != null && e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if (s != null) {
                        s = s.toLowerCase(Locale.ENGLISH);
                    }
                    return s;
                }
            }
        }
        System.out.println("JUNIKE.. metadata is null");
        return null;
    }
    
    public static String getComponentName(JsonObject component) {
        System.out.println("JUNIKE.. getComponentName for component=" + component.toString());
        final JsonObject metadata = component.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonElement e = metadata.get(NAME_PROPERTY_NAME);
            if (e != null && e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        System.out.println("JUNIKE.. metadata is null");
        return null;
    }

    public static String getComponentNamespace(JsonObject component) {
        System.out.println("JUNIKE.. getComponentNamespace for component=" + component.toString());
        final JsonObject metadata = component.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonElement e = metadata.get(NAMESPACE_PROPERTY_NAME);
            if (e != null && e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        System.out.println("JUNIKE.. return default namespace");
        return DEFAULT_NAMESPACE;
    }


    public static List<String> getAnnotationNamespaces(ApiClient client, Object application) {
        System.out.println("JUNIKE.. getAnnotationNamespaces for application=" + application.toString());
        List<String> result = new ArrayList<>();

        JsonElement element = client.getJSON().getGson().toJsonTree(application);
        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();
            JsonElement metadata = root.get(METADATA_PROPERTY_NAME);
            if (metadata != null && metadata.isJsonObject()) {
                JsonObject metadataObj = metadata.getAsJsonObject();
                JsonElement annotations = metadataObj.get(ANNOTATIONS_PROPERTY_NAME);
                if (annotations != null && annotations.isJsonObject()) {
                    JsonObject annoObj = annotations.getAsJsonObject(); 
                    JsonElement value = annoObj.get("kappnav.component.namespaces");
                    if (value != null && value.isJsonPrimitive()) {                          
                        String namespaces = value.getAsString();
                        if (namespaces != null && !namespaces.equals("")) {
                            namespaces = namespaces.trim();
                            if (namespaces.indexOf(",") > 0) {
                                String[] tmplist = namespaces.split(",");
                                for (String s : tmplist) {
                                    result.add(s.trim());
                                }
                            } else {
                                result.add(namespaces);
                            }
                        }
                    }
                }                                      
            }
        }
        System.out.println("getAnnotationNamespaces return="+ result.toString());
        return result;
    }
    
    protected static ApiClient getApiClient() throws IOException {
        final ApiClient client = Config.defaultClient();
        if (!DISABLE_TRUST_ALL_CERTS) {
            trustAllCerts(client);
        }
        return client;
    }
    
    // Write status object to metadata.annotations[kappnav.status].
    protected static void writeStatusToComponent(JsonObject component, JsonObject status) {
        System.out.println("JUNIKE.. writeStatusToComponent for component=" + component.toString() + " status=" + status.toString());
        JsonElement metadata = component.get(METADATA_PROPERTY_NAME);
        if (metadata == null) {
            System.out.println("JUNIKE.. writeStatusToComponent metadata is null");
            metadata = new JsonObject();
            component.add(METADATA_PROPERTY_NAME, metadata);
        }
        if (metadata.isJsonObject()) {
            final JsonObject metadataObj = metadata.getAsJsonObject();
            JsonElement annotations = metadataObj.get(ANNOTATIONS_PROPERTY_NAME);
            if (annotations == null) {
                annotations = new JsonObject();
                metadataObj.add(ANNOTATIONS_PROPERTY_NAME, annotations);
            }
            if (annotations.isJsonObject()) {
                final JsonObject annotationsObj = annotations.getAsJsonObject();
                annotationsObj.add(KAPPNAV_STATUS_PROPERTY_NAME, status);
            }
        }
    }
    
    protected static JsonObject createUnknownStatusObject(KAppNavConfig config) {
        System.out.println("JUNIKE..createUnknownStatusObject for config=" + config.toString());
        final String statusUnknown = config.getStatusUnknown();
        return createStatusObject(statusUnknown, statusUnknown);
    }
    
    private static JsonObject createStatusObject(String value, String flyover) {
        System.out.println("JUNIKE..createStatusObject for value=" + value + " flyover=" + flyover);
        final JsonObject status = new JsonObject();
        status.addProperty(VALUE_PROPERTY_NAME, value);
        status.addProperty(FLYOVER_PROPERTY_NAME, flyover);
        System.out.println("JUNIKE..createStatusObject status=" + status.toString());
        return status;
    }
    
    private static JsonObject createStatusObject(JsonObject statusObj, String value, JsonElement flyover, JsonElement flyoverNLS) {
        System.out.println("JUNIKE..createStatusObject for statusObj=" + statusObj.toString() + " value=" + value + " flyover=" + flyover.toString() + " flyoverNLS=" + flyoverNLS);
        final JsonObject status = new JsonObject();
        status.addProperty(VALUE_PROPERTY_NAME, value);
        if (flyover != null) {
            status.add(FLYOVER_PROPERTY_NAME, flyover);
        }
        if (flyoverNLS != null) {
            status.add(FLYOVER_NLS_PROPERTY_NAME, flyoverNLS);
        }
        // Copy other values to the new map.
        statusObj.entrySet().stream().filter(e -> 
            !VALUE_PROPERTY_NAME.equals(e.getKey()) &&
            !FLYOVER_PROPERTY_NAME.equals(e.getKey()) &&
            !FLYOVER_NLS_PROPERTY_NAME.equals(e.getKey())).forEach(e -> {
                status.add(e.getKey(), e.getValue());
            });
            System.out.println("JUNIKE..createStatusObject status=" + status.toString());
        return status;
    }
    
    // Resolves the ${status} operator, replacing it with the given simple status value.
    protected static JsonObject resolveStatusOperator(JsonObject statusObj, String simpleStatus) {
        System.out.println("JUNIKE.. resolveStatusOperator for statusObj=" + statusObj.toString() + " simpleStatus=" + simpleStatus);
        final JsonElement valueObj = statusObj.get(VALUE_PROPERTY_NAME);
        if (valueObj != null && valueObj.isJsonPrimitive()) {
            String value = valueObj.getAsString();
            boolean changed = false;
            // process flyover.
            JsonElement flyoverObj = statusObj.get(FLYOVER_PROPERTY_NAME);
            if (flyoverObj != null && flyoverObj.isJsonPrimitive()) {
                String flyover = flyoverObj.getAsString();
                if (flyover.contains(STATUS_OPERATOR)) {
                    flyover = flyover.replace(STATUS_OPERATOR, simpleStatus);
                    flyoverObj = new JsonPrimitive(flyover);
                    changed = true;
                }
            }
            // process flyover.nls.
            JsonElement flyoverNLSObj = statusObj.get(FLYOVER_NLS_PROPERTY_NAME);
            if (flyoverNLSObj != null) {
                if (flyoverNLSObj.isJsonPrimitive()) {
                    String flyoverNLS = flyoverNLSObj.getAsString();
                    if (flyoverNLS.contains(STATUS_OPERATOR)) {
                        flyoverNLS = flyoverNLS.replace(STATUS_OPERATOR, simpleStatus);
                        flyoverNLSObj = new JsonPrimitive(flyoverNLS);
                        changed = true;
                    }
                }
                else if (flyoverNLSObj.isJsonArray()) {
                    final JsonArray array = flyoverNLSObj.getAsJsonArray();
                    final JsonArray result = new JsonArray();
                    final AtomicBoolean changedArray = new AtomicBoolean(false);
                    array.forEach(e -> {
                        if (e != null && e.isJsonPrimitive()) {
                            String flyoverNLS = e.getAsString();
                            if (flyoverNLS.contains(STATUS_OPERATOR)) {
                                flyoverNLS = flyoverNLS.replace(STATUS_OPERATOR, simpleStatus);
                                e = new JsonPrimitive(flyoverNLS);
                                changedArray.set(true);
                            }
                        }
                        result.add(e);
                    });
                    if (changedArray.get()) {
                        flyoverNLSObj = result;
                        changed = true;
                    }
                }
            }
            // The object passed to this method must not be mutated so create a new one.
            if (changed) {
                return createStatusObject(statusObj, value, flyoverObj, flyoverNLSObj);
            }
        }
        System.out.println("JUNIKE.. resolveStatusOperator return statusObj=" + statusObj.toString());
        return statusObj;
    }
    
    // Returns true if the status object contains one of
    // the known values from the config, false otherwise.
    protected static boolean hasKnownStatus(JsonObject statusObj, KAppNavConfig config) {
        System.out.println("JUNIKE..hasKnownStatus for statusObj=" + statusObj.toString() + " config=" + config.toString());
        final JsonElement valueObj = statusObj.get(VALUE_PROPERTY_NAME);
        if (valueObj != null && valueObj.isJsonPrimitive()) {
            final String value = valueObj.getAsString();
            return config.isKnownStatus(value);
        }
        return false;
    }
    
    private static void trustAllCerts(ApiClient client) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            } };
    
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, trustAllCerts, new SecureRandom());
            client.getHttpClient().setSslSocketFactory(sc.getSocketFactory());
            
            ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).allEnabledCipherSuites().build();
            client.getHttpClient().setConnectionSpecs(Collections.singletonList((spec)));
        }
        catch (Exception e) {}
    }
    
    protected static String encodeURLParameter(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException u) {}
        // Should never happen, but return the unencoded string as a fallback.
        return s;
    }

    protected static int getResponseCode(Exception e) {
        System.out.println("JUNIKE.. getResponseCode " + e.toString());
        if (e instanceof ApiException) {
            final int code = ((ApiException) e).getCode();
            // If the return code is 0, the call to the Kubernetes API
            // failed on the client side (internal error).
            if (code != 0) {
                System.out.println("JUNIKE.. instance of ApiException, code=" + code);
                // Multi-status. The actual return code from this exception
                // will be included in the JSON message.
                return 207;
            }
            System.out.println("JUNIKE.. code is 0");
        }
        else if (e instanceof ValidationException) {
            System.out.println("JUNIKE.. instance of ValidationException, code is 422");
            // Unprocessable Entity
            return 422;
        }
        else if (e instanceof JsonSyntaxException) {
            System.out.println("JUNIKE.. instance of JsonSyntaxException, code is 400");
            // Bad request.
            return 400;
        }
        // Internal Server Error
        System.out.println("JUNIKE.. instance of InternalServerError, code is 500");
        return 500;
    }

    protected static String getStatusMessageAsJSON(Exception e) {
        System.out.println("JUNIKE.. getStatusMessageAsJSON " + e.toString());
        if (e instanceof ApiException) {
            final int code = ((ApiException) e).getCode();
            System.out.println("JUNIKE.. instance of ApiException, code=" + code);
            return getStatusMessageAsJSON(code, e.getMessage());
        }
        else if (e instanceof ValidationException) {
            System.out.println("JUNIKE.. instance of ValidationException");
            return getValidationErrorMessageAsJSON(e.getMessage(), ((ValidationException) e).getFieldName());
        }
        System.out.println("JUNIKE.. getStatusMessageAsJSON return " + e.toString());
        return getErrorMessageAsJSON(e.getMessage());
    }
    
    protected static String getStatusMessageAsJSON(String msg) {
        System.out.println("JUNIKE.. getStatusMessageAsJSON " + msg);
        return getStatusMessageAsJSON(0, msg);
    }
    
    private static String getStatusMessageAsJSON(int code, String msg) {
        System.out.println("JUNIKE.. getStatusMessageAsJSON code=" + code + " msg=" + msg);
        final JsonObject o = new JsonObject();
        if (code != 0) {
            o.addProperty("status", code);
            o.addProperty("message", msg);
        }
        else {
            o.addProperty("status", msg);
        }
        System.out.println("JUNIKE.. getStatusMessageAsJSON return " + o.toString());
        return o.toString();
    }
    
    private static String getValidationErrorMessageAsJSON(String msg, String fieldName) {
        System.out.println("JUNIKE.. getValidationErrorMessageAsJSON msg=" + msg + " fieldName=" + fieldName);
        final JsonObject o = new JsonObject();
        o.addProperty("error", msg);
        if (fieldName != null) {
            o.addProperty("field", fieldName);
        }
        System.out.println("JUNIKE.. getValidationErrorMessageAsJSON return " + o.toString());
        return o.toString();
    }
    
    private static String getErrorMessageAsJSON(String msg) {
        System.out.println("JUNIKE.. getErrorMessageAsJSON " + msg);
        final JsonObject o = new JsonObject();
        o.addProperty("error", msg);
        System.out.println("JUNIKE.. getErrorMessageAsJSON return " + o.toString());
        return o.toString();
    }

    // Generic CRUD APIs 

    protected Object getNamespacedGenericObject(ApiClient client, String group, String kindPlural, String namespace, String name) throws ApiException {
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        return coa.getNamespacedCustomObject(group, APP_VERSION,
                encodeURLParameter(namespace), kindPlural, encodeURLParameter(name));
    }

    protected Object createNamespacedGenericObject(ApiClient client, String group, String kindPlural, String namespace, JsonObject body) throws ApiException {
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        return coa.createNamespacedCustomObject(group, APP_VERSION, encodeURLParameter(namespace), kindPlural, body, "false");
    }

    protected Object replaceNamespacedGenericObject(ApiClient client, String group, String kindPlural, String namespace, String name, JsonObject body) throws ApiException {
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        return coa.replaceNamespacedCustomObject(group, APP_VERSION, encodeURLParameter(namespace), kindPlural, encodeURLParameter(name), body);
    }

    protected Object deleteNamespacedGenericObject(ApiClient client, String group, String kindPlural, String namespace, String name) throws ApiException {
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        V1DeleteOptions options= new V1DeleteOptions();

        return coa.deleteNamespacedCustomObject(group, APP_VERSION, encodeURLParameter(namespace), kindPlural, encodeURLParameter(name), options, 0, true, "" );
    }

    // Application CRUD APIs 

    protected Object getNamespacedApplicationObject(ApiClient client, String namespace, String name) throws ApiException {
        return getNamespacedGenericObject(client, APP_GROUP, APP_PLURAL, namespace, name); 
    }

    protected Object createNamespacedApplicationObject(ApiClient client, String namespace, JsonObject body) throws ApiException {
        return createNamespacedGenericObject(client, APP_GROUP, APP_PLURAL, namespace, body);
    }

    protected Object replaceNamespacedApplicationObject(ApiClient client, String namespace, String name, JsonObject body) throws ApiException {
        return replaceNamespacedGenericObject(client, APP_GROUP, APP_PLURAL, namespace, name, body);
    }

    protected Object deleteNamespacedApplicationObject(ApiClient client, String namespace, String name) throws ApiException {
        return deleteNamespacedGenericObject(client, APP_GROUP, APP_PLURAL, namespace, name); 
    }
}
