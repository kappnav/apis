/*
 * Copyright 2019, 2020 IBM Corporation
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.text.StringEscapeUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.ibm.kappnav.logging.Logger;

import application.rest.v1.actions.ResolutionContext;
import application.rest.v1.actions.ValidationException;
import application.rest.v1.actions.ResolutionContext.ResolvedValue;
import application.rest.v1.configmaps.OwnerRef;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.openapi.Configuration;

import okhttp3.OkHttpClient;
import okhttp3.ConnectionSpec;


public abstract class KAppNavEndpoint {
    private static final String className = KAppNavEndpoint.class.getName();

    protected static final String NAME_PATTERN_ONE_OR_MORE = "^[a-z0-9-.:]+$";
    protected static final String NAME_PATTERN_ZERO_OR_MORE = "^[a-z0-9-.:]*$";
    protected static final String API_VERSION_PATTERN_ZERO_OR_MORE = "^[a-z0-9-.:/%]*$";
    
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
            b = false;
        }
        DISABLE_TRUST_ALL_CERTS = b;
    }
    
    // Resource properties.
    private static final String OWNER_REFERENCES_NAME = "ownerReferences";
    private static final String METADATA_PROPERTY_NAME = "metadata";
    private static final String ANNOTATIONS_PROPERTY_NAME = "annotations";
    private static final String NAME_PROPERTY_NAME = "name";
    private static final String NAMESPACE_PROPERTY_NAME = "namespace";
    private static final String RESOURCE_VERSION_PROPERTY_NAME = "resourceVersion";

    // Annotation properties.
    private static final String KAPPNAV_STATUS_PROPERTY_NAME = "kappnav.status";
    private static final String KAPPNAV_SUB_KIND_PROPERTY_NAME = "kappnav.subkind";
    
    // Kind actions mapping properties
    private static final String API_VERSION_PROPERTY_NAME = "apiVersion";
    private static final String KIND_PROPERTY_NAME = "kind";
    private static final String UID_PROPERTY_NAME = "uid";

     // Status object properties.
    private static final String VALUE_PROPERTY_NAME = "value";
    private static final String FLYOVER_PROPERTY_NAME = "flyover";
    private static final String FLYOVER_NLS_PROPERTY_NAME = "flyover.nls";
    private static final String STATUS_OPERATOR = "${status}";
    
    private static final String APP_GROUP = "app.k8s.io";
    private static final String APP_VERSION = "v1beta1";
    private static final String APP_PLURAL = "applications";

    private static final String DEFAULT_NAMESPACE = "default";
  
    //For junit only
    static CustomObjectsApi coau = null;
    static void setCustomObjectsApiForJunit(CustomObjectsApi coa) {
    	coau = coa;
    }
    
    CustomObjectsApi getCustomObjectsApi() {
    	if (coau == null) {
        	return new CustomObjectsApi();
        } else {
        	return coau;
        }
    }
    
    protected Object listApplicationObject(ApiClient client) throws ApiException {
    	final CustomObjectsApi coa = getCustomObjectsApi();
        coa.setApiClient(client);
        return coa.listClusterCustomObject(APP_GROUP, APP_VERSION, APP_PLURAL, null, null, null, null, 60, null, 60, false);
    }
    
    protected Object listNamespacedApplicationObject(ApiClient client, String namespace) throws ApiException {
    	final CustomObjectsApi coa = getCustomObjectsApi();
        coa.setApiClient(client);
        return coa.listNamespacedCustomObject(APP_GROUP, APP_VERSION,
                encodeURLParameter(namespace), APP_PLURAL, null, null, null, null, 60, null, 60, Boolean.FALSE);
    }

    public static List<JsonObject> getItemsAsList(ApiClient client, Object resources) {

        final List<JsonObject> result = new ArrayList<>();
        final JsonElement element = client.getJSON().getGson().toJsonTree(resources);

        if (element != null && element.isJsonObject()) {
            writeItemsToList(element.getAsJsonObject(), result);
        }
        return result;
    }
    
    public static void writeItemsToList(JsonObject root, List<? super JsonObject> destination) {
        if (root != null) {
            JsonElement items = root.get("items");
            if (items != null && items.isJsonArray()) {
                JsonArray itemsArray = items.getAsJsonArray();
                itemsArray.forEach(v -> {
                    if (v != null && v.isJsonObject()) {
                        destination.add(v.getAsJsonObject());
                    }
                });
            }
        }
    }

    public static List<JsonObject> getItemAsList(ApiClient client, Object resource) {

        final JsonElement element = client.getJSON().getGson().toJsonTree(resource);

        if (element != null && element.isJsonObject()) {
            return Collections.singletonList(element.getAsJsonObject());
        }
        return Collections.emptyList();
    }
    
    public static JsonObject getItemAsObject(ApiClient client, Object resource) {
        
        if (resource instanceof JsonObject) {
            return (JsonObject) resource;
        }
        final JsonElement element = client.getJSON().getGson().toJsonTree(resource);
        
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getItemAsObject", Logger.LogType.DEBUG, "Return null.");
        }
        return null;
    }
    
    public static String getComponentApiVersion(JsonObject component, String kind) {
        if (Logger.isEntryEnabled())
            Logger.log(className, "getComponentApiVersion", Logger.LogType.ENTRY,"kind = " + kind);

        final JsonElement apiv_e = component.get(API_VERSION_PROPERTY_NAME);
        String apiVersion = null;
        if (apiv_e != null)
            apiVersion = apiv_e.getAsString();
            
        if (apiVersion == null || apiVersion.length() == 0) {
            if (Logger.isEntryEnabled())
                Logger.log(className, "getComponentApiVersion", Logger.LogType.ENTRY,
                    "apiVersion is null or empty and get it from ComponentInfoRegistry");
            apiVersion = ComponentInfoRegistry.CORE_KIND_TO_API_VERSION_MAP.get(kind);
        }   

        if (Logger.isExitEnabled())
            Logger.log(className, "getComponentApiVersion", Logger.LogType.EXIT,"apiVersion = "
                       + apiVersion);
        return apiVersion;
    }

    public static String getComponentKind(JsonObject component) {
        if (Logger.isEntryEnabled())
            Logger.log(className, "getComponentKind", Logger.LogType.ENTRY,"");

        final JsonElement kind_e = component.get(KIND_PROPERTY_NAME);
        String kind = null;
        if (kind_e != null)
            kind = kind_e.getAsString();

        if (Logger.isExitEnabled())
                Logger.log(className, "getComponentKind", Logger.LogType.EXIT,"kind = "
                    + kind);
        return kind;
    }    

    public static String getComponentSubKind(JsonObject component) {
               if (Logger.isEntryEnabled()) {
            Logger.log(className, "getComponentSubKind", Logger.LogType.ENTRY,"");
        }
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
                    if (Logger.isExitEnabled()) {
                        Logger.log(className, "getComponentSubKind", Logger.LogType.EXIT, s);
                    }
                    return s;
                }
            }
        } else {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getComponentSubKind", Logger.LogType.DEBUG, "Metadata is null.");
            }
        }
        if (Logger.isExitEnabled()) {
            Logger.log(className, "getComponentSubKind", Logger.LogType.EXIT, "Return null.");
        }
        return null;
    }
    
    public static String getComponentName(JsonObject component) {
        final JsonObject metadata = component.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonElement e = metadata.get(NAME_PROPERTY_NAME);
            if (e != null && e.isJsonPrimitive()) {
                String componentName = e.getAsString();
                return componentName;
            }
        } else {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getComponentName", Logger.LogType.DEBUG, "Metadata is null.");
            }
        }
        return null;
    }

    public static OwnerRef[] getOwners(JsonObject component) {
        final JsonObject metadata = component.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonElement ownerReferences = metadata.get(OWNER_REFERENCES_NAME);
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getOwnerReferences", Logger.LogType.DEBUG, "ownerReferences= "+ownerReferences);
            }
            return toOwnersArray((JsonArray) ownerReferences);
        }
        else {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getOwnerReferences", Logger.LogType.DEBUG, "Metadata is null.");
            }
        }
        return null;
    }

    public static String getComponentNamespace(JsonObject component) {
        if (Logger.isEntryEnabled()) {
            Logger.log(className, "getComponentNamespace", Logger.LogType.ENTRY,"");
        }
        final JsonObject metadata = component.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonElement e = metadata.get(NAMESPACE_PROPERTY_NAME);
            if (e != null && e.isJsonPrimitive()) {
                String componentName = e.getAsString();
                if (Logger.isExitEnabled()) {
                    Logger.log(className, "getComponentNamespace", Logger.LogType.EXIT, componentName);
                }
                return componentName;
            }
        } else {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getComponentNamespace", Logger.LogType.DEBUG, "Metadata is null.");
            }
        }
        if (Logger.isExitEnabled()) {
            Logger.log(className, "getComponentName", Logger.LogType.EXIT, "Returning defaultNamespace=" + DEFAULT_NAMESPACE);
        }
        return DEFAULT_NAMESPACE;
    }

    /**
     * resolveInputPatterns evaluates patterns contained in input fields of an
     * action ConfigMap. Examples of patterns: 
     *  ${proc.podlist()}
     *  ${resource.$.metadata.name}
     * 
     * @param map        action ConfigMap
     * @param client     Kube API client
     * @param registry   ComponentInfoRegistry
     * @param kind       target resource kind
     * @param apiVersion target resource apiVersion
     * @param name       target resource name
     * @param namespace  target resource namespace
     */
    public static void resolveInputPatterns(JsonObject map, final ApiClient client, ComponentInfoRegistry registry, String kind, String apiVersion, String name, String namespace) {
        final String methodName = "resolveInputPatterns";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY, "kind: " + kind + " apiVersion: " + apiVersion + " name: " + name + " namespace: " + namespace);
        }
        if (!kind.equals("Deployment") && !kind.equals("Pod")) {
            if (Logger.isExitEnabled()) {
                Logger.log(className, methodName, Logger.LogType.EXIT, "Only kind Deployment and Pod supported");
            }
            return;
        }
        try {
            boolean found = false;
            JsonObject resource;
            try {
                resource = getResource(client, registry, name, kind, apiVersion, namespace);
                if (resource != null) {
                    found = true;
                }
            } catch (Exception e) {
                if (Logger.isDebugEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.DEBUG, "Exception getting resource: " + e);
                }
                return;
            } finally {
                if (!found) {
                    if (Logger.isExitEnabled()) {
                        Logger.log(className, methodName, Logger.LogType.EXIT, "Resource not found - name: " + name + " kind: " + kind + " apiVersion: " + apiVersion + " namespace: " + namespace + " not found");
                    }
                    return;
                }
            }
            final ResolutionContext context = new ResolutionContext(client, registry, resource, kind);
            JsonObject inputs = map.getAsJsonObject("inputs");
            if (inputs == null) {
                if (Logger.isExitEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.EXIT, "Inputs not found");
                }
                return;
            }
            if (Logger.isDebugEnabled()) {
                Logger.log(className, methodName, Logger.LogType.DEBUG, "inputs found");
            }
            Set<Entry<String, JsonElement>> inputEntries = inputs.entrySet();
            for (Map.Entry<String, JsonElement> inputEntry : inputEntries) {
                try {
                    if (Logger.isDebugEnabled()) {
                        Logger.log(className, methodName, Logger.LogType.DEBUG, "input found: " + inputEntry.getKey());
                    }
                    JsonObject fields = inputEntry.getValue().getAsJsonObject().getAsJsonObject("fields");
                    if (fields != null) {
                        if (Logger.isDebugEnabled()) {
                            Logger.log(className, methodName, Logger.LogType.DEBUG, "fields found");
                        }
                        Set<Entry<String, JsonElement>> fieldEntries = fields.entrySet();
                        for (Map.Entry<String, JsonElement> fieldEntry : fieldEntries) {
                            try {
                                if (Logger.isDebugEnabled()) {
                                    Logger.log(className, methodName, Logger.LogType.DEBUG,
                                            "field found: " + fieldEntry.getKey());
                                }
                                JsonObject field = fieldEntry.getValue().getAsJsonObject();
                                if (field != null) {
                                    if (Logger.isDebugEnabled()) {
                                        Logger.log(className, methodName, Logger.LogType.DEBUG, "field JsonObject found");
                                    }
                                    // process values field
                                    JsonArray valuesArray = field.getAsJsonArray("values");
                                    if (valuesArray != null) {
                                        if (Logger.isDebugEnabled()) {
                                            Logger.log(className, methodName, Logger.LogType.DEBUG, "values array found");
                                        }
                                        try {
                                            if (valuesArray.size() == 1 && valuesArray.get(0).getAsString().equals("${func.podlist()}")) {
                                                ResolvedValue rv = context.resolve(valuesArray.get(0).getAsString());
                                                if (Logger.isDebugEnabled()) {
                                                    Logger.log(className, methodName, Logger.LogType.DEBUG, "values value: " + valuesArray.get(0).getAsString());
                                                }
                                                if (Logger.isDebugEnabled()) {
                                                    Logger.log(className, methodName, Logger.LogType.DEBUG, "Resolved value: " + rv.getValue());
                                                }
                                                valuesArray.remove(0);
                                                JsonObject pods = new JsonParser().parse(rv.getValue()).getAsJsonObject();
                                                JsonArray podsArray = pods.getAsJsonArray("pods");
                                                // {"pods":["demo-app-56dcb8d858-sf2md"]}
                                                if (podsArray != null) {
                                                    for (JsonElement pod : podsArray) {
                                                        valuesArray.add(pod);
                                                        if (Logger.isDebugEnabled()) {
                                                            Logger.log(className, methodName, Logger.LogType.DEBUG, "Added pod: " + pod.getAsString());
                                                        }
                                                    } 
                                                }
                                            }
                                        } catch (Exception e) {
                                            if (Logger.isDebugEnabled()) {
                                                Logger.log(className, methodName, Logger.LogType.DEBUG, "Exception processing values array: " + e);
                                            }
                                        }
                                    }
                                    // process default field
                                    JsonPrimitive defaultValue = field.getAsJsonPrimitive("default");
                                    if (defaultValue != null && defaultValue.isString() && defaultValue.getAsString().equals("${resource.$.metadata.name}")) {
                                        ResolvedValue rv = context.resolve(defaultValue.getAsString());
                                        if (Logger.isDebugEnabled()) {
                                            Logger.log(className, methodName, Logger.LogType.DEBUG, "Resolved value of " + defaultValue.getAsString() + " = " + rv.getValue());
                                        }
                                        field.remove("default");
                                        field.add("default", new JsonPrimitive(rv.getValue()));
                                    }
                                }
                            } catch (Exception e) {
                                if (Logger.isDebugEnabled()) {
                                    Logger.log(className, methodName, Logger.LogType.DEBUG, "Exception processing field: " + fieldEntry.getKey() + " Exception: " + e);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    if (Logger.isDebugEnabled()) {
                        Logger.log(className, methodName, Logger.LogType.DEBUG, "Exception processing input: " + inputEntry.getKey() + " Exception: " + e);
                    }
                }
            }
        } catch (Exception e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, methodName, Logger.LogType.DEBUG, "Exception: " + e);
            }
        }
        if (Logger.isExitEnabled()) {
            Logger.log(className, methodName, Logger.LogType.EXIT, "resource = " + name + ", kind = " + kind);
        }
        return;
    }

    /**
     * getResource retrieves a resource from the ComponentInfoRegistry
     * 
     * @param client     Kube API client
     * @param registry   ComponentInfoRegistry
     * @param kind       resource kind
     * @param apiVersion resource apiVersion
     * @param name       resource name
     * @param namespace  resource namespace
     * 
     * @return JsonObject representing the resource
     * 
     * @throws ApiException if apiVersion is null or invalid
     */
    public static JsonObject getResource(final ApiClient client, ComponentInfoRegistry registry, final String name, 
                                         final String kind, String apiVersion, final String namespace) throws ApiException {
        final String methodName = "getResource";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY,
                    "Name=" + name + ", kind=" + kind + ", apiVersion=" + apiVersion + ", namespace=" + namespace);
        }
        if (apiVersion == null || apiVersion.trim().length() == 0) {
            apiVersion = ComponentInfoRegistry.CORE_KIND_TO_API_VERSION_MAP.get(kind);
            if (apiVersion == null) {
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "getResource", Logger.LogType.ERROR, "apiVersion is null.");
                }
                throw new ApiException(400, "getResource Unknown kind: " + kind);
            }
        }
        final Object o = registry.getNamespacedObject(client, kind, apiVersion, namespace, name);
        if (Logger.isExitEnabled()) {
            Logger.log(className, methodName, Logger.LogType.EXIT, "");
        }
        return getItemAsObject(client, o);
    }

    
    public static String getResourceVersion(JsonObject component) {
        if (Logger.isEntryEnabled()) {
            Logger.log(className, "getResourceVersion", Logger.LogType.ENTRY, "");
        }
        final JsonObject metadata = component.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonElement e = metadata.get(RESOURCE_VERSION_PROPERTY_NAME);
            if (e != null && e.isJsonPrimitive()) {
                String resourceVersion = e.getAsString();
                if (Logger.isExitEnabled()) {
                    Logger.log(className, "getResourceVersion", Logger.LogType.EXIT, resourceVersion);
                }
                return resourceVersion;
            }
        }
        else {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "getResourceVersion", Logger.LogType.DEBUG, "Metadata is null.");
            }
        }
        if (Logger.isExitEnabled()) {
            Logger.log(className, "getResourceVersion", Logger.LogType.EXIT, "Return null.");
        }
        return null;
    }
    
    public static List<String> getAnnotationNamespaces(ApiClient client, Object application) {
        return getAnnotationNamespaces(client.getJSON().getGson().toJsonTree(application));
    }

    public static boolean isApplicationHidden(final JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();
            JsonElement metadata = root.get(METADATA_PROPERTY_NAME);
            if (metadata != null && metadata.isJsonObject()) {
                JsonObject metadataObj = metadata.getAsJsonObject();
                JsonElement annotations = metadataObj.get(ANNOTATIONS_PROPERTY_NAME);
                if (annotations != null && annotations.isJsonObject()) {
                    JsonObject annoObj = annotations.getAsJsonObject(); 
                    JsonElement value = annoObj.get("kappnav.application.hidden");
                    if (value != null && value.isJsonPrimitive()) {  
                        return true;                     
                    }   
                    else { 
                        return false; 
                    }
                }                                      
            }
        }
        return false;
    }

    public static List<String> getAnnotationNamespaces(final JsonElement element) {
        List<String> result = new ArrayList<>();
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
        return result;
    }

    //private static ApiClient client;
    public static ApiClient getApiClient() throws IOException {
        final ApiClient client = Config.defaultClient();
        if (!DISABLE_TRUST_ALL_CERTS) {
            trustAllCerts(client);
        }
        return client;
    }
    
    // Write status object to metadata.annotations[kappnav.status].
    protected static void writeStatusToComponent(JsonObject component, JsonObject status) {
        JsonElement metadata = component.get(METADATA_PROPERTY_NAME);
        if (metadata == null) {
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
        final String statusUnknown = config.getStatusUnknown();
        return createStatusObject(statusUnknown, statusUnknown);
    }

    final 
    // Return Java array of owner references from JSON ownerReferences array
    private static OwnerRef[] toOwnersArray(JsonArray ownerRefs) { 
        OwnerRef[] owners= null; 
        if ( ownerRefs != null ) { 
            if ( ownerRefs.isJsonArray()) { 
                owners= new OwnerRef[ownerRefs.size()];
                for (int i=0; i < owners.length; i++) { 
                    JsonElement e= ownerRefs.get(i); 
                    if ( e.isJsonObject() ) { 
                        owners[i]= new OwnerRef(toOwnerApiVersion((JsonObject)e),toOwnerKind((JsonObject)e),toOwnerUID((JsonObject)e));
                    }
                    else { 
                        if ( Logger.isErrorEnabled()) { 
                            Logger.log(className, "toOwnersArray", Logger.LogType.ERROR, "ownerReferences array container elements that are not objects.");
                        } 
                    }
                }    
            }
            else { 
                if ( Logger.isErrorEnabled()) { 
                    Logger.log(className, "toOwnersArray", Logger.LogType.ERROR, "ownerReferences is not an array.");
                } 
            }
        }
        return owners; 
    }

    // return "apiVersion" value from owner object from ownerReferences array  
    private static String toOwnerApiVersion(JsonObject ownerObj) { 
        if ( ownerObj != null ) { 
            JsonElement e = ownerObj.get(API_VERSION_PROPERTY_NAME); 
            if ( e.isJsonPrimitive() ) {
                return e.getAsString(); 
            }
            else { 
                if ( Logger.isErrorEnabled()) { 
                    Logger.log(className, "toOwnerApiVersion", Logger.LogType.ERROR, "JSON element is not a primitive - should be string.");
                } 
            }
        }
        return null; 
    }

    // return "kind" value from owner object from ownerReferences array  
    private static String toOwnerKind(JsonObject ownerObj) { 
        if ( ownerObj != null ) { 
            JsonElement e = ownerObj.get(KIND_PROPERTY_NAME); 
            if ( e.isJsonPrimitive() ) {
                return e.getAsString(); 
            }
            else { 
                if ( Logger.isErrorEnabled()) { 
                    Logger.log(className, "toOwnerKind", Logger.LogType.ERROR, "JSON element is not a primitive - should be string.");
                } 
            }
        }
        return null; 
    }

    // return "uid" value from owner object from ownerReferences array  
    private static String toOwnerUID(JsonObject ownerObj) { 
        if ( ownerObj != null ) { 
            JsonElement e = ownerObj.get(UID_PROPERTY_NAME); 
            if ( e.isJsonPrimitive() ) {
                return e.getAsString(); 
            }
            else { 
                if ( Logger.isErrorEnabled()) { 
                    Logger.log(className, "toOwnerUID", Logger.LogType.ERROR, "JSON element is not a primitive - should be string.");
                } 
            }
        }
        return null; 
    }
    
    private static JsonObject createStatusObject(String value, String flyover) {
        final JsonObject status = new JsonObject();
        status.addProperty(VALUE_PROPERTY_NAME, value);
        status.addProperty(FLYOVER_PROPERTY_NAME, flyover);
        return status;
    }
    
    private static JsonObject createStatusObject(JsonObject statusObj, String value, JsonElement flyover, JsonElement flyoverNLS) {
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
        return status;
    }
    
    // Resolves the ${status} operator, replacing it with the given simple status value.
    protected static JsonObject resolveStatusOperator(JsonObject statusObj, String simpleStatus) {
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
        return statusObj;
    }
    
    // Returns true if the status object contains one of
    // the known values from the config, false otherwise.
    protected static boolean hasKnownStatus(JsonObject statusObj, KAppNavConfig config) {
        final JsonElement valueObj = statusObj.get(VALUE_PROPERTY_NAME);
        if (valueObj != null && valueObj.isJsonPrimitive()) {
            final String value = valueObj.getAsString();
            if ( Logger.isDebugEnabled()) { 
                Logger.log(className, "hasKnownStatus", Logger.LogType.DEBUG, "value:" + value);
            } 
            // return true if value not null or config contain status value
            if (config.isKnownStatus(value) || value != null) {
                return true;
            } 
        }
        return false;
    }
    
    private static void trustAllCerts(ApiClient apiClient) {
        ApiClient localApiClient = null;
        try {
            TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            } };

            SSLContext sc = SSLContext.getInstance("TLSv1.2");

            // use the same key manager as kube client
            sc.init(apiClient.getKeyManagers(), trustAllCerts, new SecureRandom());
           
            ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).allEnabledCipherSuites().build();

            localApiClient = apiClient.setHttpClient(apiClient.getHttpClient().newBuilder()
                                                    .sslSocketFactory(sc.getSocketFactory(), (X509TrustManager)trustAllCerts[0])
                                                    .connectionSpecs(Collections.singletonList((spec)))
                                                    .build());
            
            Configuration.setDefaultApiClient(localApiClient);
            
        } catch (Exception e) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "trustAllCerts", Logger.LogType.DEBUG, "Caught Exception " + e.toString());
            }
        }  
    }
    
    public static String encodeURLParameter(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException u) {
            if (Logger.isDebugEnabled()) {
                Logger.log(className, "encodeURLParameter", Logger.LogType.DEBUG, "Caught UnsupportedEncodingException " + u.toString());
            }
        }
        // Should never happen, but return the unencoded string as a fallback.
        return s;
    }

    // Escape html special chars
    public static String encodeHTML(String s) {         
        return StringEscapeUtils.escapeHtml4(s);      
    }
    
    protected static int getResponseCode(Exception e) {
        if (e instanceof ApiException) {
            final int code = ((ApiException) e).getCode();
            // If the return code is 0, the call to the Kubernetes API
            // failed on the client side (internal error).
            if (code != 0) {
                // Multi-status. The actual return code from this exception
                // will be included in the JSON message.
                return 207;
            }
        }
        else if (e instanceof ValidationException) {
            // Unprocessable Entity
            return 422;
        }
        else if (e instanceof JsonSyntaxException) {
            // Bad request.
            return 400;
        }
        // Internal Server Error
        return 500;
    }

    protected static String getStatusMessageAsJSON(Exception e) {
        if (e instanceof ApiException) {
            final int code = ((ApiException) e).getCode();          
            return getStatusMessageAsJSON(code, encodeHTML(e.getMessage()));
        }
        else if (e instanceof ValidationException) {
            return getValidationErrorMessageAsJSON(encodeHTML(e.getMessage()), ((ValidationException) e).getFieldName());
        }
        
        return getErrorMessageAsJSON(encodeHTML(e.getMessage()));
    }
    
    protected static String getStatusMessageAsJSON(String msg) {
        String escapedMsg = encodeHTML(msg);
        return getStatusMessageAsJSON(0, escapedMsg);
    }
    
    private static String getStatusMessageAsJSON(int code, String msg) {
        final JsonObject o = new JsonObject();       
        o.addProperty("message", msg);  
        return o.toString();
    }
    
    private static String getValidationErrorMessageAsJSON(String msg, String fieldName) {
        final JsonObject o = new JsonObject();
        if (fieldName != null) {
            msg = msg + " on field name: " + fieldName;
        }
        o.addProperty("message", msg);     
        return o.toString();
    }
    
    private static String getErrorMessageAsJSON(String msg) {
        final JsonObject o = new JsonObject();
        o.addProperty("message", msg);
        return o.toString();
    }

    // Generic CRUD APIs 

    protected Object getNamespacedGenericObject(ApiClient client, String group, String kindPlural, String namespace, String name) throws ApiException {
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "getNamespacedGenericObject", Logger.LogType.DEBUG, "For group=" + group + ", kindPlural="+kindPlural + ", namespace="+namespace + ", name="+name);
        }     
        final CustomObjectsApi coa = getCustomObjectsApi();
        coa.setApiClient(client);
        return coa.getNamespacedCustomObject(group, APP_VERSION,
                encodeURLParameter(namespace), kindPlural, encodeURLParameter(name));
    }

    protected Object createNamespacedGenericObject(ApiClient client, String group, String kindPlural, String namespace, JsonObject body) throws ApiException {
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "createNamespacedGenericObject", Logger.LogType.DEBUG, "For group=" + group + ", kindPlural="+kindPlural + ", namespace="+namespace);
        }
        final CustomObjectsApi coa = getCustomObjectsApi();
        coa.setApiClient(client);
        return coa.createNamespacedCustomObject(group, APP_VERSION, encodeURLParameter(namespace), kindPlural, body, "false", null, null);
    }

    protected Object replaceNamespacedGenericObject(ApiClient client, String group, String kindPlural, String namespace, String name, JsonObject body) throws ApiException {
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "replaceNamespacedGenericObject", Logger.LogType.DEBUG, "For group=" + group + ", kindPlural="+kindPlural + ", namespace="+namespace + ", name="+name);
        }
        final CustomObjectsApi coa = getCustomObjectsApi();
        coa.setApiClient(client);
        
        return coa.replaceNamespacedCustomObject(group, APP_VERSION, encodeURLParameter(namespace), kindPlural, encodeURLParameter(name), body, null, null);
    }

    protected Object deleteNamespacedGenericObject(ApiClient client, String group, String kindPlural, String namespace, String name) throws ApiException {
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "deleteNamespacedGenericObject", Logger.LogType.DEBUG, "For group=" + group + ", kindPlural="+kindPlural + ", namespace="+namespace + ", name="+name);
        }
        final CustomObjectsApi coa = getCustomObjectsApi();
        coa.setApiClient(client);
        V1DeleteOptions options= new V1DeleteOptions();
        return coa.deleteNamespacedCustomObject(group, APP_VERSION, encodeURLParameter(namespace), kindPlural, encodeURLParameter(name), 0, true, null, null, options);
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
