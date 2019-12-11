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
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.enterprise.context.ApplicationScoped;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.ApisApi;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1APIGroup;
import io.kubernetes.client.models.V1APIGroupList;

/**
 * This class builds a mapping between component kinds and their 
 * associated {group, version, plural}. It provides methods for
 * accessing components by their component kind through Kubernetes'
 * REST API.
 */
@ApplicationScoped
public class ComponentInfoRegistry {

    private static final String NOT_FOUND = "Not Found";
    
    private static final Map<String,ComponentInfo> BUILT_IN_COMPONENT_KIND_MAP;
    static {
        BUILT_IN_COMPONENT_KIND_MAP = new HashMap<>();
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/ConfigMap", 
                new ComponentInfo("ConfigMap", "", "v1", "configmaps", true, new ConfigMapResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/Endpoints", 
                new ComponentInfo("Endpoints", "", "v1", "endpoints", true, new EndpointsResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/Event", 
                new ComponentInfo("Event", "", "v1", "events", true, new EventResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/LimitRange", 
                new ComponentInfo("LimitRange", "", "v1", "limitranges", true, new LimitRangeResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/Namespace", 
                new ComponentInfo("Namespace", "", "v1", "namespaces", false, new NamespaceResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/Node", 
                new ComponentInfo("Node", "", "v1", "nodes", false, new NodeResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/PersistentVolume", 
                new ComponentInfo("PersistentVolume", "", "v1", "persistentvolumes", false, new PersistentVolumeResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/PersistentVolumeClaim", 
                new ComponentInfo("PersistentVolumeClaim", "", "v1", "persistentvolumeclaims", true, new PersistentVolumeClaimResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/Pod", 
                new ComponentInfo("Pod", "", "v1", "pods", true, new PodResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/PodTemplate", 
                new ComponentInfo("PodTemplate", "", "v1", "podtemplates", true, new PodTemplateResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/ReplicationController", 
                new ComponentInfo("ReplicationController", "", "v1", "replicationcontrollers", true, new ReplicationControllerResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/ResourceQuota", 
                new ComponentInfo("ResourceQuota", "", "v1", "resourcequotas", true, new ResourceQuotaResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/Secret", 
                new ComponentInfo("Secret", "", "v1", "secrets", true, new SecretResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/Service", 
                new ComponentInfo("Service", "", "v1", "services", true, new ServiceResolver()));
        BUILT_IN_COMPONENT_KIND_MAP.put("/v1/ServiceAccount", 
                new ComponentInfo("ServiceAccount", "", "v1", "serviceaccounts", true, new ServiceAccountResolver()));
    }

    private static final Map<String,String> BUILT_IN_KIND_TO_API_VERSION_MAP;
    static {
        BUILT_IN_KIND_TO_API_VERSION_MAP = new HashMap<>();
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("ConfigMap", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("Endpoints", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("Event", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("LimitRange", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("Namespace", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("Node", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("PersistentVolume", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("PersistentVolumeClaim", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("Pod", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("PodTemplate", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("ReplicationController", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("ResourceQuota", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("Secret", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("Service", "/v1");
        BUILT_IN_KIND_TO_API_VERSION_MAP.put("ServiceAccount", "/v1");
    }

    public static final Map<String, String> CORE_KIND_TO_API_VERSION_MAP;
    static {
        CORE_KIND_TO_API_VERSION_MAP = new ConcurrentHashMap<String, String>();
        CORE_KIND_TO_API_VERSION_MAP.put("Service", "/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("Deployment", "apps/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("Route", "route.openshift.io/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("ConfigMap", "/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("Secret", "/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("Volume", "core/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("PersistentVolumeClaim", "/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("CustomResourceDefinition", "apiextensions.k8s.io/v1beta1");
        CORE_KIND_TO_API_VERSION_MAP.put("Application", "app.k8s.io/v1beta1");
        CORE_KIND_TO_API_VERSION_MAP.put("StatefulSet", "apps/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("ReplicaSet", "apps/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("Ingress", "networking.k8s.io/v1beta1");
        CORE_KIND_TO_API_VERSION_MAP.put("Job", "batch/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("ServiceAccount", "/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("ClusterRole", "rbac.authorization.k8s.io/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("ClusterRoleBinding", "rbac.authorization.k8s.io/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("Role", "rbac.authorization.k8s.io/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("RoleBinding", "rbac.authorization.k8s.io/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("StorageClass", "rbac.authorization.k8s.io/v1");
        CORE_KIND_TO_API_VERSION_MAP.put("Endpoint", "rbac.authorization.k8s.io/v1");
        
        // Initialize extensions to KAppNav
        KAppNavExtension.init();
    }

    private final AtomicReference<Map<String,ComponentInfo>> componentKindMap;

    final AtomicReference<Map<String,String>> groupKindToApiVersionMap;

    public ComponentInfoRegistry() throws ApiException, IOException {
        this(KAppNavEndpoint.getApiClient());
    }

    public ComponentInfoRegistry(ApiClient client) throws ApiException {
        groupKindToApiVersionMap = new AtomicReference<>();
        final Map<String,ComponentInfo> map = processGroupList(client);
        componentKindMap = new AtomicReference<>(map);
    }
    
    public boolean isNamespaced(ApiClient client, String componentKind, String apiVersion) throws ApiException {
        ComponentInfo info = getComponentInfo(client, componentKind, apiVersion);
        if (info != null) {
            return info.namespaced;
        }
        throw new ApiException(207, "resource kind " + componentKind + " is " + NOT_FOUND);
    }

    public Object listClusterObject(ApiClient client, String componentKind, String apiVersion, 
            String pretty, String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
        ComponentInfo info = getComponentInfo(client, componentKind, apiVersion);
        if (info != null) {
            return info.resolver.listClusterObject(client, info, pretty, labelSelector, resourceVersion, watch);
        }
        throw new ApiException(207, "resource kind " + componentKind + " is " + NOT_FOUND);
    }

    public Object listNamespacedObject(ApiClient client, String componentKind, String apiVersion, String namespace,
            String pretty, String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
        ComponentInfo info = getComponentInfo(client, componentKind, apiVersion);
        if (info != null) {
            return info.resolver.listNamespacedObject(client, info, namespace, pretty, labelSelector, resourceVersion, watch);
        }
        throw new ApiException(207, "resource kind " + componentKind + " is " + NOT_FOUND);
    }

    public Object getNamespacedObject(ApiClient client, String componentKind, String apiVersion, String namespace, String name) throws ApiException {
        ComponentInfo info = getComponentInfo(client, componentKind, apiVersion);
        if (info != null) {
            try {
                Object result = info.resolver.getNamespacedObject(client, info, namespace, name);
                return result;
            } catch (ApiException e) {
                throw new ApiException(207, e.toString());
            }
        }
        throw new ApiException(207, "resource kind " + componentKind + " is " + NOT_FOUND);
    }
    
    private ComponentInfo getComponentInfo(ApiClient client, String componentKind, String apiVersionParm) {
        Map<String,ComponentInfo> map = componentKindMap.get();
        String apiVersion = apiVersionParm;
        if (apiVersion == null || apiVersion.length() == 0) {
            apiVersion = CORE_KIND_TO_API_VERSION_MAP.get(componentKind);
            if (apiVersion == null) {
                System.out.println("getComponentInfo no apiVersion and not core kind: " + componentKind);
                return null;
            }

        }
        String key = apiVersion + "/" + componentKind;
        System.out.println("getComponentInfo using key: " + key);
        ComponentInfo info = map.get(key);
        if (info != null) {
            System.out.println("getComponentInfo using key: " + key + " returning ComponentInfo 1: " + info);
            return info;
        }
        // Cache miss. A new CRD may have been loaded recently so
        // try to refresh the cache in hopes that a mapping for the
        // new kind will be found. Should revisit to see if there's a
        // better of way doing this that would be less costly in
        // performance.
        try {
            map = processGroupList(client);
            componentKindMap.set(map);
            info = map.get(key);
            System.out.println("getComponentInfo using key: " + key + " returning ComponentInfo 2: " + info);
            return info;
        }
        catch (ApiException e) {
            System.out.println("getComponentInfo exception using key: " + key + "\nException: " + e);
        }
        System.out.println("getComponentInfo no resource info found using key: " + key);
        return null;
    }
    
    private Map<String,ComponentInfo> processGroupList(ApiClient client) throws ApiException {
        ApisApi api = new ApisApi();
        api.setApiClient(client);

        final Map<String,String> groupKindMap = new HashMap<String,String>();
        groupKindMap.putAll(BUILT_IN_KIND_TO_API_VERSION_MAP);
        
        final Map<String,ComponentInfo> map = new HashMap<>();
        map.putAll(BUILT_IN_COMPONENT_KIND_MAP);

        // {
        //   "kind": "APIGroupList",
        //   ...
        //   "groups": [
        //     {
        //       "name": "{name}",
        //       ...
        //     }
        //   ]
        // }
        V1APIGroupList list = api.getAPIVersions();
        List<V1APIGroup> groups = list.getGroups();
        groups.forEach(v -> {
            processGroupVersion(client, map, groupKindMap, v.getName(), v.getPreferredVersion().getVersion());
        });
        System.out.println("processGroupList setting groupKind map: " + groupKindMap);
        groupKindToApiVersionMap.set(groupKindMap);
        return map;
    }

    private void processGroupVersion(ApiClient client, Map<String,ComponentInfo> map, Map<String,String> groupKindMap, String group, String version) {
        try {
            System.out.println("processGroupVersion entry - group: " + group + " version: " + version);
            CustomObjectsApi coa = new CustomObjectsApi();
            coa.setApiClient(client);

            // {
            //   "kind": "APIResourceList",
            //   ...
            //   "resources": [
            //     {
            //       "name":       "{name}",
            //       ...
            //       "kind":       "{kind}",
            //       "namespaced": "{namespaced}",
            //       ...
            //     }
            //   ]
            // }
            final Object o = coa.listClusterCustomObject(group, version, ".", null, null, null, null);
            final JsonElement element = client.getJSON().getGson().toJsonTree(o);
            if (element != null && element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();
                JsonElement resources = root.get("resources");
                if (resources != null && resources.isJsonArray()) {
                    JsonArray resourcesArray = resources.getAsJsonArray();
                    resourcesArray.forEach(v -> {
                        if (v != null && v.isJsonObject()) {
                            JsonObject resource = v.getAsJsonObject();
                            String kind = resource.get("kind").getAsString();
                            String key = (group != null ? group : "") + "/" + version + "/" + kind;
                            String groupKindKey = (group != null ? group : "") + "/" + kind;
                            System.out.println("processGroupVersion ComponentInfo map key: " + key);
                            if (!map.containsKey(key)) {
                                System.out.println("processGroupVersion ComponentInfo key not found in map: " + key);
                                String plural = resource.get("name").getAsString();
                                if (!plural.contains("/")) {
                                    boolean namespaced = resource.get("namespaced").getAsBoolean();
                                    System.out.println("processGroupVersion adding ComponentInfo key: " + key);
                                    map.put(key, new ComponentInfo(kind, group, version, plural, namespaced));
                                    String value = (group != null ? group : "") + "/" + version;
                                    System.out.println("processGroupVersion adding groupKind key: " + groupKindKey + " value: " + value);
                                    groupKindMap.put(groupKindKey, value);
                                }
                            }
                        }
                    });
                }
            }
        }
        catch (ApiException e) {}
    }

    public String toString() {
        return "Component Kind Map: " + componentKindMap.toString();
    }

    static final class ComponentInfo {
        final String kind;
        final String group;
        final String version;
        final String plural;
        final boolean namespaced;
        final ComponentResolver resolver;
        ComponentInfo(String kind, String group, String version, String plural, boolean namespaced) {
            this(kind, group, version, plural, namespaced, new CustomObjectResolver());
        }
        ComponentInfo(String kind, String group, String version, String plural, boolean namespaced, ComponentResolver resolver) {
            this.kind = kind;
            this.group = group;
            this.version = version;
            this.plural = plural;
            this.namespaced = namespaced;
            this.resolver = resolver;
        }
        public String toString() {
            return "Kind: " + kind + ", Group: " + group 
                    + ", Version: " + version + ", Plural: " + plural
                    + ", Namespaced: " + namespaced
                    + ", Resolver: " + resolver.getClass().getName();
        }
    }
    
    interface ComponentResolver {
        public Object listClusterObject(ApiClient client, ComponentInfo info, 
                String pretty, String labelSelector, String resourceVersion, Boolean watch) throws ApiException;
        public Object listNamespacedObject(ApiClient client, ComponentInfo info, String namespace,
                String pretty, String labelSelector, String resourceVersion, Boolean watch) throws ApiException;
        public Object getNamespacedObject(ApiClient client, ComponentInfo info, String namespace, String name) throws ApiException;
    }
    
    static final class CustomObjectResolver implements ComponentResolver {

        @Override
        public Object listClusterObject(ApiClient client, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            CustomObjectsApi coa = new CustomObjectsApi();
            coa.setApiClient(client);
            return coa.listClusterCustomObject(info.group, info.version, info.plural,
                    pretty, labelSelector, resourceVersion, watch);
        }

        @Override
        public Object listNamespacedObject(ApiClient client, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            CustomObjectsApi coa = new CustomObjectsApi();
            coa.setApiClient(client);
            return coa.listNamespacedCustomObject(info.group, info.version,
                    namespace, info.plural, pretty, labelSelector, resourceVersion, watch);
        }

        @Override
        public Object getNamespacedObject(ApiClient client, ComponentInfo info, String namespace, String name)
                throws ApiException {
            CustomObjectsApi coa = new CustomObjectsApi();
            coa.setApiClient(client);
            try {
                Object result = coa.getNamespacedCustomObject(info.group, info.version, namespace, info.plural, name);
                return result;
            } catch (ApiException e) {
                // kubernetes only give message "NOT_FOUND", so give more info to user what can be wrong either namespace or resource name
                throw new ApiException(207, "either namespace " + namespace + " or resource name " + name + " is " + NOT_FOUND);
            }
        }
    }
    
    //
    // Support for Kubernetes built-in resources: /api/v1
    //
    
    static abstract class BuiltInKindResolver implements ComponentResolver {

        @Override
        public Object listClusterObject(ApiClient client, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            return listClusterObject(api, info, pretty, labelSelector, resourceVersion, watch);
        }

        @Override
        public Object listNamespacedObject(ApiClient client, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            return listNamespacedObject(api, info, namespace, pretty, labelSelector, resourceVersion, watch);
        }

        @Override
        public Object getNamespacedObject(ApiClient client, ComponentInfo info, String namespace, String name)
                throws ApiException {
            CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);
            return getNamespacedObject(api, info, namespace, name);
        }
        
        public abstract Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException;
        
        public abstract Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException;
        
        public abstract Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException;
    }
    
    // Kind: ConfigMap
    static final class ConfigMapResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listConfigMapForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedConfigMap(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedConfigMap(name, namespace, null, null, null);
        }
    }
    
    // Kind: Endpoints
    static final class EndpointsResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listEndpointsForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedEndpoints(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedEndpoints(name, namespace, null, null, null);
        }
    }
    
    // Kind: Event
    static final class EventResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listEventForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedEvent(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedEvent(name, namespace, null, null, null);
        }
    }
    
    // Kind: LimitRange
    static final class LimitRangeResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listLimitRangeForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedLimitRange(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedLimitRange(name, namespace, null, null, null);
        }
    }
    
    // Kind: Namespace
    static final class NamespaceResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespace(pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespace(pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespace(name, null, null, null);
        }
    }
    
    // Kind: Node
    static final class NodeResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listNode(pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNode(pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNode(name, null, null, null);
        }
    }
    
    // Kind: PersistentVolume
    static final class PersistentVolumeResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listPersistentVolume(pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listPersistentVolume(pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readPersistentVolume(name, null, null, null);
        }
    }
    
    // Kind: PersistentVolumeClaim
    static final class PersistentVolumeClaimResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listPersistentVolumeClaimForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedPersistentVolumeClaim(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedPersistentVolumeClaim(name, namespace, null, null, null);
        }
    }
    
    // Kind: Pod
    static final class PodResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listPodForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedPod(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedPod(name, namespace, null, null, null);
        }
    }
    
    // Kind: PodTemplate
    static final class PodTemplateResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listPodTemplateForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedPodTemplate(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedPodTemplate(name, namespace, null, null, null);
        }
    }
    
    // Kind: ReplicationController
    static final class ReplicationControllerResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listReplicationControllerForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedReplicationController(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedReplicationController(name, namespace, null, null, null);
        }
    }
    
    // Kind: ResourceQuota
    static final class ResourceQuotaResolver extends BuiltInKindResolver {

        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listResourceQuotaForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedResourceQuota(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedResourceQuota(name, namespace, null, null, null);
        }
    }
    
    // Kind: Secret
    static final class SecretResolver extends BuiltInKindResolver {
        
        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listSecretForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedSecret(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedSecret(name, namespace, null, null, null);
        }
    }
    
    // Kind: Service
    static final class ServiceResolver extends BuiltInKindResolver {
        
        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listServiceForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedService(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedService(name, namespace, null, null, null);
        }
    }
    
    // Kind: ServiceAccount
    static final class ServiceAccountResolver extends BuiltInKindResolver {
        
        @Override
        public Object listClusterObject(CoreV1Api api, ComponentInfo info, String pretty, String labelSelector,
                String resourceVersion, Boolean watch) throws ApiException {
            return api.listServiceAccountForAllNamespaces(null, null, null, labelSelector, null, pretty, resourceVersion, null, watch);
        }

        @Override
        public Object listNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String pretty,
                String labelSelector, String resourceVersion, Boolean watch) throws ApiException {
            return api.listNamespacedServiceAccount(namespace, pretty, null, null, null, labelSelector, null, resourceVersion, null, watch);
        }

        @Override
        public Object getNamespacedObject(CoreV1Api api, ComponentInfo info, String namespace, String name)
                throws ApiException {
            return api.readNamespacedServiceAccount(name, namespace, null, null, null);
        }
    }
}
