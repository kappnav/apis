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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.validation.constraints.Pattern;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ibm.kappnav.logging.Logger;

import application.rest.v1.configmaps.ConfigMapProcessor;
import application.rest.v1.configmaps.SectionConfigMapProcessor;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Secret;

@Path("/resources")
@Tag(name = "resources", description="kAppNav Resources API")
public class ResourcesEndpoint extends KAppNavEndpoint {
    private static final String className = ResourcesEndpoint.class.getName();

    //common constants
    private static final String ACTION_MAP_PROPERTY_NAME = "action-map";
    private static final String SECTION_MAP_PROPERTY_NAME = "section-map";
    private static final String KIND_PROPERTY_NAME = "kind";
    private static final String APIVERSION_PROPERTY_NAME = "apiVersion";    
   
    private static final String APP_VERSION = "v1beta1";
    private static final String V1_VERSION = "v1";
    private static final String V2_VERSION = "v2";
    private static final String V1ALPHA1_VERSION = "v1alpha1";

    private static final String CONFIG_GROUP = "config.openshift.io";
    private static final String CONSOLE_GROUP = "console.openshift.io";
    private static final String DEPLOYMENT_GROUP = "apps";
    private static final String KAPPNAV_GROUP = "kappnav.io";
    private static final String MACHINE_GROUP = "machine.openshift.io";
    private static final String OAUTH_GROUP = "oauth.openshift.io";
    private static final String OPERATOR_GROUP = "operator.openshift.io";

    //APIServer constants
    private static final String API_SERVER_KIND = "APIServer";
    private static final String API_SERVER_PROPERTY_NAME = "APIServers";
    private static final String API_SERVER_PLURAL = "apiservers";

    //APIService constants
    private static final String API_SERVICE_KIND = "APIService";
    private static final String API_SERVICE_PROPERTY_NAME = "APIServices";
    private static final String API_SERVICE_GROUP = "apiregistration.k8s.io";
    private static final String API_SERVICE_PLURAL = "apiservices";

    //Application constants
    private static final String APPLICATION_KIND = "Application";
    private static final String APPS_PROPERTY_NAME = "Applications";
    private static final String APP_GROUP = "app.k8s.io";
    private static final String APP_PLURAL = "applications";

    //AppliedClusterResourceQuota constants
    private static final String APPLIED_CLUSTER_RESOURCE_QUOTA_KIND = "AppliedClusterResourceQuota";
    private static final String ACRQ_PROPERTY_NAME = "AppliedClusterResourceQuotas";
    private static final String ACRQ_GROUP = "quota.openshift.io";
    private static final String ACRQ_PLURAL = "appliedclusterresourcequotas";

    //Authentication constants
    private static final String AUTHENTICATION_KIND = "Authentication";
    private static final String AUTHENTICATION_PROPERTY_NAME = "Authentications";
    private static final String AUTHENTICATION_GROUP = "config.openshift.io";
    private static final String AUTHENTICATION_PLURAL = "authentications";

    //BareMetalHost constants
    private static final String BARE_METAL_HOST_KIND = "BareMetalHost";
    private static final String BARE_METAL_HOST_PROPERTY_NAME = "BareMetalHosts";
    private static final String BARE_METAL_HOST_GROUP = "metal3.io";
    private static final String BARE_METAL_HOST_PLURAL = "baremetalhosts";

    //BrokerTemplateInstance constants
    private static final String BROKER_TEMPLATE_INSTANCE_KIND = "BrokerTemplateInstance";
    private static final String BTI_PROPERTY_NAME = "BrokerTemplateInstances";
    private static final String BTI_GROUP = "template.openshift.io";
    private static final String BTI_PLURAL = "brokertemplateinstances";

    //Build constants
    private static final String BUILD_KIND = "Build";
    private static final String BUILD_PROPERTY_NAME = "Builds";
    private static final String BUILD_PLURAL = "builds";

    //BuildConfig constants
    private static final String BUILD_CONFIG_KIND = "BuildConfig";
    private static final String BUILD_CONFIG_PROPERTY_NAME = "BuildConfigs";
    private static final String BUILD_CONFIG_GROUP = "build.openshift.io";
    private static final String BUILD_CONFIG_PLURAL = "buildconfigs";

    //CatalogSource constants 
    private static final String CATALOG_SOURCE_KIND = "CatalogSource";
    private static final String CATALOG_SOURCE_PROPERTY_NAME = "CatalogSources";
    private static final String CATALOG_SOURCE_GROUP = "operators.coreos.com";
    private static final String CATALOG_SOURCE_PLURAL = "catalogsources";
 
    //CatalogSourceConfig constants 
    private static final String CATALOG_SOURCE_CONFIG_KIND = "CatalogSourceConfig";
    private static final String CATALOG_SOURCE_CONFIG_PROPERTY_NAME = "CatalogSourceConfigs";
    private static final String CATALOG_SOURCE_CONFIG_GROUP = "operators.coreos.com";
    private static final String CATALOG_SOURCE_CONFIG_PLURAL = "catalogsourceconfigs";
 
    //CephBlockPool constants 
    private static final String CEPH_BLOCK_POOL_KIND = "CephBlockPool";
    private static final String CEPH_BLOCK_POOL_PROPERTY_NAME = "CephBlockPools";
    private static final String CEPH_GROUP = "ceph.rook.io";
    private static final String CEPH_BLOCK_POOL_PLURAL = "cephblockpools";
 
    //CephFilesystem constants 
    private static final String CEPH_FILESYSTEM_KIND = "CephFilesystem";
    private static final String CEPH_FILESYSTEM_PROPERTY_NAME = "CephFilesytems";
    private static final String CEPH_FILESYSTEM_PLURAL = "cephfilesystems";
 
    //CephNFS constants 
    private static final String CEPH_NFS_KIND = "CephNFS";
    private static final String CEPH_NFS_PROPERTY_NAME = "CephNFSes";
    private static final String CEPH_NFS_PLURAL = "cephnfses";
 
    //CephObjectStore constants 
    private static final String CEPH_OBJECT_STORE_KIND = "CephObjectStore";
    private static final String CEPH_OBJECT_STORE_PROPERTY_NAME = "CephObjectStores";
    private static final String CEPH_OBJECT_STORE_PLURAL = "cephobjectstores";
 
    //CephObjectStoreUser constants 
    private static final String CEPH_OBJECT_STORE_USER_KIND = "CephObjectStoreUser";
    private static final String CEPH_OBJECT_STORE_USER_PROPERTY_NAME = "CephObjectStoreUsers";
    private static final String CEPH_OBJECT_STORE_USER_PLURAL = "cephobjectstoreusers";
 
    //CertificateSigningRequest constants
    private static final String CERTIFICATE_SIGNING_REQUEST_KIND = "CertificateSigningRequest";
    private static final String CERTIFICATE_SIGNING_REQUEST_PROPERTY_NAME = "CertificateSigningRequests";
    private static final String CERTIFICATE_SIGNING_REQUEST_GROUP = "certificates.k8s.io";
    private static final String CERTIFICATE_SIGNING_REQUEST_PLURAL = "certificatesigningrequests";

    //ClusterAutoscaler constants
    private static final String CLUSTER_AUTOSCALER_KIND = "ClusterAutoscaler";
    private static final String CLUSTER_AUTOSCALER_PROPERTY_NAME = "ClusterAutoscalers";
    private static final String CLUSTER_AUTOSCALER_GROUP = "autoscaling.openshift.io";
    private static final String CLUSTER_AUTOSCALER_PLURAL = "clusterautoscalers";

    //ClusterNetwork constants
    private static final String CLUSTER_NETWORK_KIND = "ClusterNetwork";
    private static final String CLUSTER_NETWORK_PROPERTY_NAME = "ClusterNetworks";
    private static final String CLUSTER_NETWORK_GROUP = "network.openshift.io";
    private static final String CLUSTER_NETWORK_PLURAL = "clusternetworks";
 
    //ClusterOperator constants
    private static final String CLUSTER_OPERATOR_KIND = "ClusterOperator";
    private static final String CLUSTER_OPERATOR_PROPERTY_NAME = "ClusterOperators";
    private static final String CLUSTER_OPERATOR_PLURAL = "clusteroperators";
 
    //ClusterRole constants
    private static final String CLUSTER_ROLE_KIND = "ClusterRole";
    private static final String CLUSTER_ROLE_PROPERTY_NAME = "ClusterRoles";
    private static final String CLUSTER_ROLE_GROUP = "rbac.authorization.k8s.io";
    private static final String CLUSTER_ROLE_PLURAL = "clusterroles";
    
    //ClusterRoleBinding constants
    private static final String CLUSTER_ROLE_BINDING_KIND = "ClusterRoleBinding";
    private static final String CLUSTER_ROLE_BINDING_PROPERTY_NAME = "ClusterRoleBindings";
    private static final String CLUSTER_ROLE_BINDING_GROUP = "rbac.authorization.k8s.io";
    private static final String CLUSTER_ROLE_BINDING_PLURAL = "clusterrolebindings";

    //ClusterServiceVersion constants 
    private static final String CLUSTER_SERVICE_VERSION_KIND = "ClusterServiceVersion";
    private static final String CLUSTER_SERVICE_VERSION_PROPERTY_NAME = "ClusterServiceVersions";    
    private static final String CLUSTER_SERVICE_VERSION_GROUP = "operators.coreos.com";
    private static final String CLUSTER_SERVICE_VERSION_PLURAL = "clusterserviceversions";

    //ClusterVersion constants
    private static final String CLUSTER_VERSION_KIND = "ClusterVersion";
    private static final String CLUSTER_VERSION_PROPERTY_NAME = "ClusterVersions";
    private static final String CLUSTER_VERSION_GROUP = "rbac.authorization.k8s.io";
    private static final String CLUSTER_VERSION_PLURAL = "clusterversions";    

    //ComponentStatus constants
    private static final String COMPONENT_STATUS_KIND = "ComponentStatus";
    private static final String COMPONENT_STATUS_PROPERTY_NAME = "ComponentStatuses";

    //ConfigMap constants
    private static final String CONFIG_MAP_KIND = "ConfigMap";
    private static final String CONFIG_MAP_PROPERTY_NAME = "ConfigMaps";

    //Console constants
    private static final String CONSOLE_KIND = "Console";
    private static final String CONSOLE_PROPERTY_NAME = "Consoles";
    private static final String CONSOLE_PLURAL = "consoles";
     
    //ConsoleCLIDownload constants
    private static final String CONSOLE_CLI_DOWNLOAD_KIND = "ConsoleCLIDownload";
    private static final String CONSOLE_CLI_DOWNLOAD_PROPERTY_NAME = "ConsoleCLIDownloads";
    private static final String CONSOLE_CLI_DOWNLOAD_PLURAL = "consoleclidownloads";

    //ConsoleExternalLogLink constants
    private static final String CONSOLE_EXTERNAL_LINK_KIND = "ConsoleExternalLogLink";
    private static final String CONSOLE_EXTERNAL_LINK_PROPERTY_NAME = "ConsoleExternalLogLinks";
    private static final String CONSOLE_EXTERNAL_LINK_PLURAL = "consoleexternalloglinks";

    //ConsoleLink constants
    private static final String CONSOLE_LINK_KIND = "ConsoleLink";
    private static final String CONSOLE_LINK_PROPERTY_NAME = "ConsoleLinks";
    private static final String CONSOLE_LINK_PLURAL = "consolelinks";

    //ConsoleNotfication constants
    private static final String CONSOLE_NOTIFICATION_KIND = "ConsoleNotification";
    private static final String CONSOLE_NOTIFICATION_PROPERTY_NAME = "ConsoleNotifications";
    private static final String CONSOLE_NOTIFICATION_PLURAL = "consolenotifications";

    //ConsoleYAMLSample constants
    private static final String CONSOLE_YAML_SAMPLE_KIND = "ConsoleYAMLSample";
    private static final String CONSOLE_YAML_SAMPLE_PROPERTY_NAME = "ConsoleYAMLSamples";
    private static final String CONSOLE_YAML_SAMPLE_PLURAL = "consoleyamlsamples";

    //ContainerRuntimeConfig constants
    private static final String CONTAINER_RUNTIME_CONFIG_KIND = "ContainerRuntimeConfig";
    private static final String CONTAINER_RUNTIME_CONFIG_PROPERTY_NAME = "ContainerRuntimeConfigs";
    private static final String CONTAINER_RUNTIME_CONFIG_GROUP = "machineconfiguration.openshift.io";
    private static final String CONTAINER_RUNTIME_CONFIG_PLURAL = "containerruntimeconfigs";

    //ControllerConfig constants
    private static final String CONTROLLER_CONFIG_KIND = "ControllerConfig";
    private static final String CONTROLLER_CONFIG_PROPERTY_NAME = "ControllerConfigs";
    private static final String CONTROLLER_CONFIG_GROUP = "machineconfiguration.openshift.io";
    private static final String CONTROLLER_CONFIG_PLURAL = "controllerconfigs";

    //ControllerRevision constants 
    private static final String CONTROLLER_REVISION_KIND = "ControllerRevision";
    private static final String CONTROLLER_REVISION_PROPERTY_NAME = "ControllerRevisions";
    private static final String CONTROLLER_REVISION_PLURAL = "controllerrevisions";

    //CredentialsRequest constants 
    private static final String CREDENTIALS_REQUEST_KIND = "CredentialsRequest";
    private static final String CREDENTIALS_REQUEST_PROPERTY_NAME = "CredentialsRequests";
    private static final String CREDENTIALS_REQUEST_GROUP = "cloudcredential.openshift.io";
    private static final String CREDENTIALS_REQUEST_PLURAL = "credentialsrequests";

    //CronJob constants
    private static final String CRON_JOB_KIND = "CronJob";
    private static final String CRON_JOB_PROPERTY_NAME = "CronJobs";
    private static final String CRON_JOB_GROUP = "batch";
    private static final String CRON_JOB_PLURAL = "cronjobs";

    //CSIDriver constants
    private static final String CSI_DRIVER_KIND = "CSIDriver";
    private static final String CSI_DRIVER_PROPERTY_NAME = "CSIDrivers";
    private static final String CSI_DRIVER_GROUP = "storage.k8s.io";
    private static final String CSI_DRIVER_PLURAL = "csidrivers";

    //CSINode constants
    private static final String CSI_NODE_KIND = "CSINode";
    private static final String CSI_NODE_PROPERTY_NAME = "CSINodes";
    private static final String CSI_NODE_GROUP = "storage.k8s.io";
    private static final String CSI_NODE_PLURAL = "csinodes";
  
    //CustomResourceDefinition constants
    private static final String CUSTOM_RESOURCE_DEFINITION_KIND = "CustomResourceDefinition";
    private static final String CUSTOM_RESOURCE_DEFINITION_PROPERTY_NAME = "CustomResourceDefinitions";
    private static final String CUSTOM_RESOURCE_DEFINITION_GROUP = "apiextensions.k8s.io";
    private static final String CUSTOM_RESOURCE_DEFINITION_PLURAL = "customresourcedefinitions";

    //DaemonSet constants
    private static final String DAEMON_SET_KIND = "DaemonSet";
    private static final String DAEMON_SET_PROPERTY_NAME = "DaemonSets"; 
    private static final String DAEMON_SET_PLURAL = "daemonsets";

    //Deployment constants
    private static final String DEPLOYMENT_KIND = "Deployment";
    private static final String DEPLOYMENT_PROPERTY_NAME = "Deployments";
    private static final String DEPLOYMENT_PLURAL = "deployments";
 
    //DeploymentConfig constants
    private static final String DEPLOYMENT_CONFIG_KIND = "DeploymentConfig";
    private static final String DEPLOYMENT_CONFIG_PROPERTY_NAME = "DeploymentConfigs";
    private static final String DEPLOYMENT_CONFIG_GROUP = "apps.openshift.io";
    private static final String DEPLOYMENT_CONFIG_PLURAL = "deploymentconfigs";
 
    //DNS constants
    private static final String DNS_KIND = "DNS";
    private static final String DNS_PROPERTY_NAME = "DNSes";
    private static final String DNS_PLURAL = "dnses";

    //DNSRecord constants 
    private static final String DNS_RECORD_KIND = "DNSRecord";
    private static final String DNS_RECORD_PROPERTY_NAME = "DNSRecords";
    private static final String DNS_RECORD_GROUP = "ingress.operator.openshift.io";
    private static final String DNS_RECORD_PLURAL = "dnsrecords";

    //EgressNetworkPolicy constants 
    private static final String EGRESS_NETWORK_POLICY_KIND = "EgressNetworkPolicy";
    private static final String EGRESS_NETWORK_POLICY_PROPERTY_NAME = "EgressNetworkPolicies";
    private static final String EGRESS_NETWORK_POLICY_GROUP = "network.openshift.io";
    private static final String EGRESS_NETWORK_POLICY_PLURAL = "egressnetworkpolicies";

    //Endpoint constants
    private static final String ENDPOINT_KIND = "Endpoint";
    private static final String ENDPOINT_PROPERTY_NAME = "Endpoints";

    //Event constants
    private static final String EVENT_KIND = "Event";
    private static final String EVENT_PROPERTY_NAME = "Events";

    //FeatureGate constants
    private static final String FEATURE_GATE_KIND = "FeatureGate";
    private static final String FEATURE_GATE_PROPERTY_NAME = "FeatureGates";
    private static final String FEATURE_GATE_PLURAL = "featuregates";

    //Group constants
    private static final String GROUP_KIND = "Group";
    private static final String GROUP_PROPERTY_NAME = "Groups";
    private static final String GROUP_GROUP = "user.openshift.io";
    private static final String GROUP_PLURAL = "groups";

    //HorizontalPodAutoscaler constants
    private static final String HORIZONTAL_POD_AUTOSCALER_KIND = "HorizontalPodAutoscaler";
    private static final String HORIZONTAL_POD_AUTOSCALER_PROPERTY_NAME = "HorizontalPodAutoscalers";
    private static final String HORIZONTAL_POD_AUTOSCALER_GROUP = "autoscaling";
    private static final String HORIZONTAL_POD_AUTOSCALER_PLURAL = "horizontalpodautoscalers";  

    //HostSubnet constants
    private static final String HOST_SUBNET_KIND = "HostSubnet";
    private static final String HOST_SUBNET_PROPERTY_NAME = "HostSubnets";
    private static final String HOST_SUBNET_GROUP = "network.openshift.io";
    private static final String HOST_SUBNET_PLURAL = "hostsubnets";

    //Identify constants
    private static final String IDENTITY_KIND = "Identity";
    private static final String IDENTITY_PROPERTY_NAME = "Identities";
    private static final String IDENTITY_GROUP = "user.openshift.io";
    private static final String IDENTITY_PLURAL = "identities";

    //Image constants
    private static final String IMAGE_KIND = "Image";
    private static final String IMAGE_PROPERTY_NAME = "Images";
    private static final String IMAGE_GROUP = "config.openshift.io";
    private static final String IMAGE_PLURAL = "images";

    //ImageContentSourcePolicy constants
    private static final String IMAGE_CONTENT_SOURCE_POLICY_KIND = "ImageContentSourcePolicy";
    private static final String IMAGE_CONTENT_SOURCE_POLICY_PROPERTY_NAME = "ImageContentSourcePolicies";
    private static final String IMAGE_CONTENT_SOURCE_POLICY_PLURAL = "imagecontentsourcepolicies";

    //ImageStream constants
    private static final String IMAGE_STREAM_KIND = "ImageStream";
    private static final String IMAGE_STREAM_PROPERTY_NAME = "ImageStreams";
    private static final String IMAGE_STREAM_GROUP = "image.openshift.io";
    private static final String IMAGE_STREAM_PLURAL = "imagestreams";

    //ImageStreamTag constants 
    private static final String IMAGE_STREAM_TAG_KIND = "ImageStreamTag";
    private static final String IMAGE_STREAM_TAG_PROPERTY_NAME = "ImageStreamTags";
    private static final String IMAGE_STREAM_TAG_GROUP = "image.openshift.io";
    private static final String IMAGE_STREAM_TAG_PLURAL = "imagestreamtags";

    //Infrastructure constants
    private static final String INFRASTRUCTURE_KIND = "Infrastructure";
    private static final String INFRASTRUCTURE_PROPERTY_NAME = "Infrastructures";
    private static final String INFRASTRUCTURE_GROUP = "config.openshift.io";
    private static final String INFRASTRUCTURE_PLURAL = "infrastructures";

    //Ingress constants
    private static final String INGRESS_KIND = "Ingress";
    private static final String INGRESSE_PROPERTY_NAME = "Ingresses";
    private static final String INGRESS_GROUP = "config.openshift.io";
    private static final String INGRESS_PLURAL = "ingresses";

    //IngressController constants 
    private static final String INGRESS_CONTROLLER_KIND = "IngressController";
    private static final String INGRESS_CONTROLLER_PROPERTY_NAME = "IngressControllers";
    private static final String INGRESS_CONTROLLER_PLURAL = "ingresscontrollers";

    //Job constants
    private static final String JOB_KIND = "Job";
    private static final String JOB_PROPERTY_NAME = "Jobs";
    private static final String JOB_GROUP = "batch";
    private static final String JOB_PLURAL = "jobs";

    //Kappnav constants
    private static final String KAPPNAV_KIND = "Kappnav";
    private static final String KAPPNAV_PROPERTY_NAME = "Kappnavs";
    private static final String KAPPNAV_CR_GROUP = "kappnav.operator.kappnav.io";
    private static final String KAPPNAV_PLURAL = "kappnavs";

    //KindActionMapping constants
    private static final String KAM_KIND = "KindActionMapping";
    private static final String KAM_PROPERTY_NAME = "KindActionMappings";
    private static final String KAM_GROUP = "actions.kappnav.io";
    private static final String KAM_PLURAL = "kindactionmappings";

    //KubeAPIServer constants
    private static final String KUBE_API_SERVER_KIND = "KubeAPIServer";
    private static final String KUBE_API_SERVER_PROPERTY_NAME = "KubeAPIServers";
    private static final String KUBE_API_SERVER_PLURAL = "kubeapiservers";

    //KubeControllerManagerconstants
    private static final String KUBE_CONTROLLER_MANAGER_KIND = "KubeControllerManager";
    private static final String KUBE_CONTROLLER_MANAGER_PROPERTY_NAME = "KubeControllerManagers";
    private static final String KUBE_CONTROLLER_MANAGER_PLURAL = "kubecontrollermanagers";

    //KubeScheduler constants
    private static final String KUBE_SCHEDULER_KIND = "KubeScheduler";
    private static final String KUBE_SCHEDULER_PROPERTY_NAME = "KubeSchedulers";
    private static final String KUBE_SCHEDULER_PLURAL = "kubeschedulers";

    //KubeletConfig constants
    private static final String KUBELET_CONFIG_KIND = "KubeletConfig";
    private static final String KUBELET_CONFIG_PROPERTY_NAME = "KubeletConfigs";
    private static final String KUBELET_CONFIG_GROUP = "machineconfiguration.openshift.io";
    private static final String KUBELET_CONFIG_PLURAL = "kubeletconfigs";
   
    //Lease constants 
    private static final String LEASE_KIND = "Lease";
    private static final String LEASE_PROPERTY_NAME = "Leases";
    private static final String LEASE_GROUP = "coordination.k8s.io";
    private static final String LEASE_PLURAL = "leases";

    //Liberty-App constants
    private static final String LIBERTY_APP_KIND = "Liberty-App";
    private static final String LIBERTY_APP_PROPERTY_NAME = "Liberty-Apps";
    private static final String LIBERTY_APP_PLURAL = "liberty-apps";

    //Liberty-Collective constants
    private static final String LIBERTY_COLLECTIVE_KIND = "Liberty-Collective";
    private static final String LIBERTY_COLLECTIVE_PROPERTY_NAME = "Liberty-Collectives";   
    private static final String LIBERTY_COLLECTIVE_PLURAL = "liberty-collectives";

    //LimitRange constants 
    private static final String LIMIT_RANGE_KIND = "LimitRange";
    private static final String LIMIT_RANGE_PROPERTY_NAME = "LimitRanges";

    //Machine constants
    private static final String MACHINE_KIND = "Machine";
    private static final String MACHINE_PROPERTY_NAME = "Machines";
    private static final String MACHINE_PLURAL = "machines";
 
    //MachineAutoscaler constants 
    private static final String MACHINE_AUTOSCALER_KIND = "MachineAutoscaler";
    private static final String MACHINE_AUTOSCALER_PROPERTY_NAME = "MachineAutoscalers";
    private static final String MACHINE_AUTOSCALER_GROUP = "autoscaling.openshift.io";
    private static final String MACHINE_AUTOSCALER_PLURAL = "machineautoscalers";
 
    //MachineHealthCheck constants 
    private static final String MACHINE_HEALTH_CHECK_KIND = "MachineHealthCheck";
    private static final String MACHINE_HEALTH_CHECK_PROPERTY_NAME = "MachineHealthChecks";
    private static final String MACHINE_HEALTH_CHECK_PLURAL = "machinehealthchecks";
 
    //MachineConfig constants
    private static final String MACHINE_CONFIG_KIND = "MachineConfig";
    private static final String MACHINE_CONFIG_PROPERTY_NAME = "MachineConfigs";
    private static final String MACHINE_CONFIG_GROUP = "machineconfiguration.openshift.io";
    private static final String MACHINE_CONFIG_PLURAL = "machineconfigs";
 
    //MachineConfigPool constants
    private static final String MACHINE_CONFIG_POOL_KIND = "MachineConfigPool";
    private static final String MACHINE_CONFIG_POOL_PROPERTY_NAME = "MachineConfigPools";
    private static final String MACHINE_CONFIG_POOL_PLURAL = "machineconfigpools";
 
    //MachineSet constants 
    private static final String MACHINE_SET_KIND = "MachineSet";
    private static final String MACHINE_SET_PROPERTY_NAME = "MachineSets";
    private static final String MACHINE_SET_PLURAL = "machinesets";
 
    //MCOConfig constants 
    private static final String MCO_CONFIG_KIND = "MCOConfig";
    private static final String MCO_CONFIG_PROPERTY_NAME = "MCOConfigs";
    private static final String MCO_CONFIG_PLURAL = "mcoconfigs";

    //MutatingWebhookConfiguration constants
    private static final String MUTATING_WEBHOOK_CONFIGURATION_KIND = "MutatingWebhookConfiguration";
    private static final String MUTATING_WEBHOOK_CONFIGURATION_PROPERTY_NAME = "MutatingWebhookConfigurations";
    private static final String MUTATING_WEBHOOK_CONFIGURATION_GROUP = "admissionregistration.k8s.io";
    private static final String MUTATING_WEBHOOK_CONFIGURATION_PLURAL = "mutatingwebhookconfigurations";

    //Namespace constants
    private static final String NAMESPACE_KIND = "Namespace";
    private static final String NAMESPACE_PROPERTY_NAME = "Namespaces";

    //NetNamespace constants
    private static final String NET_NAMESPACE_KIND = "NetNamespace";
    private static final String NET_NAMESPACE_PROPERTY_NAME = "NetNamespaces";
    private static final String NET_NAMESPACE_GROUP = "network.openshift.io";
    private static final String NET_NAMESPACE_PLURAL = "netnamespaces";

    //Network constants 
    private static final String NETWORK_KIND = "Network";
    private static final String NETWORK_PROPERTY_NAME = "Networks";
    private static final String NETWORK_PLURAL = "networks";

    //NetworkPolicy constants 
    private static final String NETWORK_POLICY_KIND = "NetworkPolicy";
    private static final String NETWORK_POLICY_PROPERTY_NAME = "NetworkPolicies";
    private static final String NETWORK_POLICY_GROUP = "networking.k8s.io";
    private static final String NETWORK_POLICY_PLURAL = "networkpolicies";

    //Node constants
    private static final String NODE_KIND = "Node";
    private static final String NODE_PROPERTY_NAME = "Nodes";

    //OAuth user constants
    private static final String OAUTH_KIND = "OAuth";
    private static final String OAUTH_PROPERTY_NAME = "OAuths";
    private static final String OAUTH_PLURAL = "oauths";

    //OAuthAccessToken constants
    private static final String OAUTH_ACCESS_TOKEN_KIND = "OAuthAccessToken";
    private static final String OAUTH_ACCESS_TOKEN_PROPERTY_NAME = "OAuthAccessTokens";
    private static final String OAUTH_ACCESS_TOKEN_PLURAL = "oauthaccesstokens";

    //OAuthAuthorizeToken constants
    private static final String OAUTH_AUTHORIZE_TOKEN_KIND = "OAuthAuthorizeToken";
    private static final String OAUTH_AUTHORIZE_TOKEN_PROPERTY_NAME = "OauthAuthorizeTokens";
    private static final String OAUTH_AUTHORIZE_TOKEN_PLURAL = "oauthauthorizetokens";

    //OAuthClient constants
    private static final String OAUTH_CLIENT_KIND = "OAuthClient";
    private static final String OAUTH_CLIENT_PROPERTY_NAME = "OAuthClients";
    private static final String OAUTH_CLIENT_PLURAL = "oauthclients";

    //OAuthClientAuthorization constants
    private static final String OAUTH_CLIENT_AUTHORIZATION_KIND = "OAuthClientAuthorization";
    private static final String OAUTH_CLIENT_AUTHORIZATION_PROPERTY_NAME = "OAuthClientAuthorizations";
    private static final String OAUTH_CLIENT_AUTHORIZATION_PLURAL = "oauthclientauthorizations";

    //OpenShiftAPIServer constants
    private static final String OPEN_SHIFT_API_SERVER_KIND = "OpenShiftAPIServer";
    private static final String OPEN_SHIFT_API_SERVER_PROPERTY_NAME = "OpenShiftAPIServers";
    private static final String OPEN_SHIFT_API_SERVER_PLURAL = "openshiftapiservers";

    //OpenShiftControllerManager constants
    private static final String OPEN_SHIFT_CONTROLLER_MANAGER_KIND = "OpenShiftControllerManager";
    private static final String OPEN_SHIFT_CONTROLLER_MANAGER_PROPERTY_NAME = "OpenShiftControllerManagers";
    private static final String OPEN_SHIFT_CONTROLLER_MANAGER_PLURAL = "openshiftcontrollermanagers";

    //OperatorGroup constants 
    private static final String OPERATOR_GROUP_KIND = "OperatorGroup";
    private static final String OPERATOR_GROUP_PROPERTY_NAME = "OperatorGroups";
    private static final String OPERATOR_GROUP_GROUP = "operators.coreos.com";
    private static final String OPERATOR_GROUP_PLURAL = "operatorgroups";

    //OperatorHub constants
    private static final String OPERATOR_HUB_KIND = "OperatorHub";
    private static final String OPERATOR_HUB_PROPERTY_NAME = "OperatorHubs";
    private static final String OPERATOR_HUB_PLURAL = "operatorhubs";

    //OperatorPKI constants 
    private static final String OPERATOR_PKI_KIND = "OperatorPKI";
    private static final String OPERATOR_PKI_PROPERTY_NAME = "OperatorPKIs";
    private static final String OPERATOR_PKI_GROUP = "network.operator.openshift.io";
    private static final String OPERATOR_PKI_PLURAL = "operatorpkis";

    //OperatorSource constants 
    private static final String OPERATOR_SOURCE_KIND = "OperatorSource";
    private static final String OPERATOR_SOURCE_PROPERTY_NAME = "OperatorSources";
    private static final String OPERATOR_SOURCE_GROUP = "operators.coreos.com";
    private static final String OPERATOR_SOURCE_PLURAL = "operatorsources";

    //PackageManifest constants n
    private static final String PACKAGE_MANIFEST_KIND = "PackageManifest";
    private static final String PACKAGE_MANIFEST_PROPERTY_NAME = "PackageManifests";
    private static final String PACKAGE_MANIFEST_GROUP = "packages.operators.coreos.com";
    private static final String PACKAGE_MANIFEST_PLURAL = "packagemanifests";

    //PersistentVolume constants
    private static final String PERSISTENT_VOLUME_KIND = "PersistentVolume";
    private static final String PERSISTENT_VOLUME_PROPERTY_NAME = "PersistentVolumes";

    //PersistentVolumeClaim constants 
    private static final String PERSISTENT_VOLUME_CLAIM_KIND = "PersistentVolumeClaim";
    private static final String PERSISTENT_VOLUME_CLAIM_PROPERTY_NAME = "PersistentVolumeClaims";

    //Pod constants
    private static final String POD_KIND = "Pod";
    private static final String POD_PROPERTY_NAME = "Pods";

    //PodDisruptionBudget constants 
    private static final String POD_DISRUPTION_BUDGET_KIND = "PodDisruptionBudget";
    private static final String POD_DISRUPTION_BUDGET_PROPERTY_NAME = "PodDisruptionBudgets";
    private static final String POD_DISRUPTION_BUDGET_GROUP = "policy";
    private static final String POD_DISRUPTION_BUDGET_PLURAL = "poddisruptionbudgets";

    //PodTemplate constants
    private static final String POD_TEMPLATE_KIND = "PodTemplate";
    private static final String POD_TEMPLATE_PROPERTY_NAME = "PodTemplates";

    //PriorityClass constants
    private static final String PRIORITY_CLASS_KIND = "PriorityClass";
    private static final String PRIORITY_CLASS_PROPERTY_NAME = "Priorityclasses";
    private static final String PRIORITY_CLASS_GROUP = "scheduling.k8s.io";
    private static final String PRIORITY_CLASS_PLURAL = "priorityclasses";

    //Project constants
    private static final String PROJECT_KIND = "Project";
    private static final String PROJECT_PROPERTY_NAME = "Projects";
    private static final String PROJECT_PLURAL = "projects";
   
    //Proxy constants
    private static final String PROXY_KIND = "Proxy";
    private static final String PROXY_PROPERTY_NAME = "Proxies";
    private static final String PROXY_PLURAL = "proxies";
   
    //Prometheus constants 
    private static final String PROMETHEUS_KIND = "Prometheus";
    private static final String PROMETHEUS_PROPERTY_NAME = "Prometheuses";
    private static final String PROMETHEUS_GROUP = "monitoring.coreos.com";
    private static final String PROMETHEUS_PLURAL = "prometheuses";

    //PrometheusRule constants 
    private static final String PROMETHEUS_RULE_KIND = "PrometheusRule";
    private static final String PROMETHEUS_RULE_PROPERTY_NAME = "PrometheusRules";
    private static final String PROMETHEUS_RULE_GROUP = "monitoring.coreos.com";
    private static final String PROMETHEUS_RULE_PLURAL = "prometheusrules";

    //RangeAllocation constants
    private static final String RANGE_ALLOCATION_KIND = "RangeAllocation";
    private static final String RANGE_ALLOCATION_PROPERTY_NAME = "RangeAllocations";
    private static final String RANGE_ALLOCATION_GROUP = "security.openshift.io";
    private static final String RANGE_ALLOCATION_PLURAL = "rangeallocations";

    //ReplicaSet constants
    private static final String REPLICA_SET_KIND = "ReplicaSet";
    private static final String REPLICA_SET_PROPERTY_NAME = "ReplicaSets"; 
    private static final String REPLICA_SET_PLURAL = "replicasets";

    //ReplicaController constants 
    private static final String REPLICA_CONTROLLER_KIND = "ReplicaController";
    private static final String REPLICA_CONTROLLER_PROPERTY_NAME = "ReplicaControllers";

    //ResourceQuota constants 
    private static final String RESOURCE_QUOTA_KIND = "ResourceQuota";
    private static final String RESOURCE_QUOTA_PROPERTY_NAME = "resourceQuotas";

    //Role constants
    private static final String ROLE_KIND = "Role";
    private static final String ROLE_PROPERTY_NAME = "Roles";
    private static final String ROLE_PLURAL = "roles";

    //RoleBinding constants
    private static final String ROLE_BINDING_KIND = "RoleBinding";
    private static final String ROLE_BINDING_PROPERTY_NAME = "RoleBindings";
    private static final String ROLE_BINDING_PLURAL = "rolebindings";

    //Route constants
    private static final String ROUTE_KIND = "Route";
    private static final String ROUTE_PROPERTY_NAME = "Routes";
    private static final String ROUTE_GROUP = "route.openshift.io";
    private static final String ROUTE_PLURAL = "routes";

    //RuntimeClass constants
    private static final String RUNTIME_CLASS_KIND = "RuntimeClass";
    private static final String RUNTIME_CLASS_PROPERTY_NAME = "RuntimeClasses";
    private static final String RUNTIME_CLASS_GROUP = "node.k8s.io";
    private static final String RUNTIME_CLASS_PLURAL = "runtimeclasses";
        
    //Secret constants
    private static final String SECRET_KIND = "Secret";
    private static final String SECRET_PROPERTY_NAME = "Secrets";

    //Scheduler constants
    private static final String SCHEDULER_KIND = "Scheduler";
    private static final String SCHEDULER_PROPERTY_NAME = "Schedulers";
    private static final String SCHEDULER_PLURAL = "schedulers";    

    //Service constants
    private static final String SERVICE_KIND = "Service";
    private static final String SERVICE_PROPERTY_NAME = "Services";

    //ServiceAccount constants
    private static final String SERVICE_ACCOUNT_KIND = "ServiceAccount";
    private static final String SERVICE_ACCOUNT_PROPERTY_NAME = "ServiceAccounts";

    //ServiceMonitor constants 
    private static final String SERVICE_MONITOR_KIND = "ServiceMonitor";
    private static final String SERVICE_MONITOR_PROPERTY_NAME = "ServiceMonitors";
    private static final String SERVICE_MONITOR_GROUP = "monitoring.coreos.com";
    private static final String SERVICE_MONITOR_PLURAL = "servicemonitors";

    //StatefulSet constants 
    private static final String STATEFUL_SET_KIND = "StatefulSet";
    private static final String STATEFUL_SET_PROPERTY_NAME = "StatefulSets"; 
    private static final String STATEFUL_SET_PLURAL = "statefulsets";
   
    //StorageClass constants
    private static final String STORAGE_CLASS_KIND = "StorageClass";
    private static final String STORAGE_CLASS_PROPERTY_NAME = "StorageClasses";
    private static final String STORAGE_CLASS_GROUP = "storage.k8s.io";
    private static final String STORAGE_CLASS_PLURAL = "storageclasses";

    //Subscription constants 
    private static final String SUBSCRIPTION_KIND = "Subscription";
    private static final String SUBSCRIPTION_PROPERTY_NAME = "subscriptions";
    private static final String SUBSCRIPTION_GROUP = "operators.coreos.com";
    private static final String SUBSCRIPTION_PLURAL = "subscriptions";

    //Tuned constants 
    private static final String TUNED_KIND = "Tuned";
    private static final String TUNED_PROPERTY_NAME = "Tuneds";
    private static final String TUNED_GROUP = "tuned.openshift.io"; 
    private static final String TUNED_PLURAL = "tuneds";

    //ValidatingWebhookConfiguration constants
    private static final String VALIDATING_WEBHOOK_CONFIGURATION_KIND = "ValidatingWebhookConfiguration";
    private static final String VALIDATING_WEBHOOK_CONFIGURATION_PROPERTY_NAME = "ValidatingWebhookConfigurations";
    private static final String VALIDATING_WEBHOOK_CONFIGURATION_GROUP = "admissionregistration.k8s.io";
    private static final String VALIDATING_WEBHOOK_CONFIGURATION_PLURAL = "validatingwebhookconfigurations";

    //Volume constants
    private static final String VOLUME_KIND = "Volume";
    private static final String VOLUME_PROPERTY_NAME = "Volumes";
    private static final String VOLUME_GROUP = "rook.io";
    private static final String VOLUME_VERSION = "v1alpha2";
    private static final String VOLUME_PLURAL = "volumes";

    //VolumeAttachment constants
    private static final String VOLUME_ATTACHMENT_KIND = "VolumeAttachment";
    private static final String VOLUME_ATTACHMENT_PROPERTY_NAME = "VolumeAttachments";
    private static final String VOLUME_ATTACHMENT_GROUP = "storage.k8s.io";
    private static final String VOLUME_ATTACHMENT_PLURAL = "volumeattachments";

    //VolumeSnapshot constants 
    private static final String VOLUME_SNAPSHOT_KIND = "VolumeSnapshot";
    private static final String VOLUME_SNAPSHOT_PROPERTY_NAME = "VolumeSnapshots";
    private static final String VOLUME_SNAPSHOT_GROUP = "snapshot.storage.k8s.io";
    private static final String VOLUME_SNAPSHOT_PLURAL = "volumesnapshots";

    //VolumeSnapshotClass constants
    private static final String VOLUME_SNAPSHOT_CLASS_KIND = "VolumeSnapshotClass";
    private static final String VOLUME_SNAPSHOT_CLASS_PROPERTY_NAME = "VolumeSnapshotClasses";
    private static final String VOLUME_SNAPSHOT_CLASS_PLURAL = "volumesnapshotclasses";

    //VolumeSnapshotContent constants
    private static final String VOLUME_SNAPSHOT_CONTENT_KIND = "VolumeSnapshotContent";
    private static final String VOLUME_SNAPSHOT_CONTENT_PROPERTY_NAME = "VolumeSnapshotContents";
    private static final String VOLUME_SNAPSHOT_CONTENT_PLURAL = "volumesnapshotcontents";

    //WAS-ND-Cell constants
    private static final String WAS_ND_CELL_KIND = "WAS-ND-Cell";
    private static final String WAS_ND_CELL_PROPERTY_NAME = "WAS-ND-Cells";
    private static final String WAS_ND_CELL_PLURAL = "was-nd-cells";
   
    //WAS-Traditional-App constants
    private static final String WAS_TRADITIONAL_APP_KIND = "WAS-Traditional-App";
    private static final String WAS_TRADITIONAL_APP_PROPERTY_NAME = "WAS-Traditional-Apps";
    private static final String WAS_TRADITIONAL_APP_PLURAL = "was-traditional-apps";
    

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{resource-kind}")
    @Operation(
            summary = "Retrieve resource objects for the specified kubernetes resource",
            description = "Returns a JSON structure of kubernetes resource objects."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})  
    public Response getResources(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("resource-kind") @Parameter(description = "The kind of the resource in small case") final String kind, 
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @QueryParam("namespace") @Parameter(description = "The namespace of the cell") final String namespace) {           
       
        try {   
            final ApiClient client = getApiClient();
            final CustomObjectsApi coa = new CustomObjectsApi();
            coa.setApiClient(client);   
            final CoreV1Api api = new CoreV1Api();
            api.setApiClient(client);

            Response response = null;       

            if (kind.equalsIgnoreCase(API_SERVER_KIND)) {            
                final Object apiServerO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, API_SERVER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, apiServerO), API_SERVER_KIND, API_SERVER_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }
           
            if (kind.equalsIgnoreCase(API_SERVICE_KIND)) {            
                final Object apiServiceO = coa.listClusterCustomObject(API_SERVICE_GROUP, V1_VERSION, API_SERVICE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, apiServiceO), API_SERVICE_KIND, API_SERVICE_PROPERTY_NAME, getAPIVersion(API_SERVICE_GROUP, V1_VERSION)); 
            }
            
            if (kind.equalsIgnoreCase(APPLICATION_KIND)) {                
                final Object appO;    
                if (namespace == null || namespace.length() == 0)
                    appO = coa.listClusterCustomObject(APP_GROUP, APP_VERSION, APP_PLURAL, null, null, null, null);
                else 
                    appO = coa.listNamespacedCustomObject(APP_GROUP, APP_VERSION, namespace, APP_PLURAL, null, null, null, null);               
                response = processResources(client, getItemsAsList(client, appO), APPLICATION_KIND, APPS_PROPERTY_NAME, getAPIVersion(APP_GROUP, APP_VERSION));                    
            }
           
            if (kind.equalsIgnoreCase(APPLIED_CLUSTER_RESOURCE_QUOTA_KIND)) {                
                final Object acrqO;    
                if (namespace == null || namespace.length() == 0)
                    acrqO = coa.listClusterCustomObject(ACRQ_GROUP, V1_VERSION, ACRQ_PLURAL, null, null, null, null);
                else 
                    acrqO = coa.listNamespacedCustomObject(ACRQ_GROUP, V1_VERSION, namespace, ACRQ_PLURAL, null, null, null, null);               
                response = processResources(client, getItemsAsList(client, acrqO), APPLIED_CLUSTER_RESOURCE_QUOTA_KIND, ACRQ_PROPERTY_NAME, getAPIVersion(ACRQ_GROUP, V1_VERSION));                    
            }

            if (kind.equalsIgnoreCase(AUTHENTICATION_KIND)) {            
                final Object authenticationO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, AUTHENTICATION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, authenticationO), AUTHENTICATION_KIND, AUTHENTICATION_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(BARE_METAL_HOST_KIND)) {                
                final Object bareMetalHostO;    
                if (namespace == null || namespace.length() == 0)
                    bareMetalHostO = coa.listClusterCustomObject(BARE_METAL_HOST_GROUP, V1ALPHA1_VERSION, BARE_METAL_HOST_PLURAL, null, null, null, null);
                else 
                    bareMetalHostO = coa.listNamespacedCustomObject(BARE_METAL_HOST_GROUP, V1ALPHA1_VERSION, namespace, BARE_METAL_HOST_PLURAL, null, null, null, null);               
                response = processResources(client, getItemsAsList(client, bareMetalHostO), BARE_METAL_HOST_KIND, BARE_METAL_HOST_PROPERTY_NAME, getAPIVersion(BARE_METAL_HOST_GROUP, V1ALPHA1_VERSION));                    
            }

            if (kind.equalsIgnoreCase(BROKER_TEMPLATE_INSTANCE_KIND)) {                
                final Object btiO = coa.listClusterCustomObject(BTI_GROUP, V1_VERSION, BTI_PLURAL, null, null, null, null);
                response = processResources(client, getItemsAsList(client, btiO), BROKER_TEMPLATE_INSTANCE_KIND, BTI_PROPERTY_NAME, getAPIVersion(BTI_GROUP, V1_VERSION));                    
            }

            if (kind.equalsIgnoreCase(BUILD_KIND)) {            
                final Object buildO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, BUILD_PLURAL, null, null, null, null);                                                                  
                response = processResources(client, getItemsAsList(client, buildO), BUILD_KIND, BUILD_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(BUILD_CONFIG_KIND)) {            
                final Object buildConfigO;
                if (namespace == null || namespace.length() == 0)
                    buildConfigO = coa.listClusterCustomObject(BUILD_CONFIG_GROUP, V1_VERSION, BUILD_CONFIG_PLURAL, null, null, null, null);                           
                else
                    buildConfigO = coa.listNamespacedCustomObject(BUILD_CONFIG_GROUP, V1_VERSION, namespace, BUILD_CONFIG_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, buildConfigO), BUILD_CONFIG_KIND, BUILD_CONFIG_PROPERTY_NAME, getAPIVersion(BUILD_CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CATALOG_SOURCE_KIND)) {            
                final Object catalogSourceO;
                if (namespace == null || namespace.length() == 0)
                    catalogSourceO = coa.listClusterCustomObject(CATALOG_SOURCE_GROUP, V1ALPHA1_VERSION, CATALOG_SOURCE_PLURAL, null, null, null, null);                           
                else
                    catalogSourceO = coa.listNamespacedCustomObject(CATALOG_SOURCE_GROUP, V1ALPHA1_VERSION, namespace, CATALOG_SOURCE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, catalogSourceO), CATALOG_SOURCE_KIND, CATALOG_SOURCE_PROPERTY_NAME, getAPIVersion(CATALOG_SOURCE_GROUP, V1ALPHA1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CATALOG_SOURCE_CONFIG_KIND)) {            
                final Object catSourceConfigO;
                if (namespace == null || namespace.length() == 0)
                    catSourceConfigO = coa.listClusterCustomObject(CATALOG_SOURCE_CONFIG_GROUP, V2_VERSION, CATALOG_SOURCE_CONFIG_PLURAL, null, null, null, null);                           
                else
                    catSourceConfigO = coa.listNamespacedCustomObject(CATALOG_SOURCE_CONFIG_GROUP, V2_VERSION, namespace, CATALOG_SOURCE_CONFIG_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, catSourceConfigO), CATALOG_SOURCE_CONFIG_KIND, CATALOG_SOURCE_CONFIG_PROPERTY_NAME, getAPIVersion(CATALOG_SOURCE_CONFIG_GROUP, V2_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CEPH_BLOCK_POOL_KIND)) {            
                final Object cephBlockPoolO;
                if (namespace == null || namespace.length() == 0)
                    cephBlockPoolO = coa.listClusterCustomObject(CEPH_GROUP, V1_VERSION, CEPH_BLOCK_POOL_PLURAL, null, null, null, null);                           
                else
                    cephBlockPoolO = coa.listNamespacedCustomObject(CEPH_GROUP, V1_VERSION, namespace, CEPH_BLOCK_POOL_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, cephBlockPoolO), CEPH_BLOCK_POOL_KIND, CEPH_BLOCK_POOL_PROPERTY_NAME, getAPIVersion(CEPH_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CEPH_FILESYSTEM_KIND)) {            
                final Object cephFilesystemO;
                if (namespace == null || namespace.length() == 0)
                    cephFilesystemO = coa.listClusterCustomObject(CEPH_GROUP, V1_VERSION, CEPH_FILESYSTEM_PLURAL, null, null, null, null);                           
                else
                    cephFilesystemO = coa.listNamespacedCustomObject(CEPH_GROUP, V1_VERSION, namespace, CEPH_FILESYSTEM_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, cephFilesystemO), CEPH_FILESYSTEM_KIND, CEPH_FILESYSTEM_PROPERTY_NAME, getAPIVersion(CEPH_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CEPH_NFS_KIND)) {            
                final Object cephNFSO;
                if (namespace == null || namespace.length() == 0)
                    cephNFSO = coa.listClusterCustomObject(CEPH_GROUP, V1_VERSION, CEPH_NFS_PLURAL, null, null, null, null);                           
                else
                    cephNFSO = coa.listNamespacedCustomObject(CEPH_GROUP, V1_VERSION, namespace, CEPH_NFS_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, cephNFSO), CEPH_NFS_KIND, CEPH_NFS_PROPERTY_NAME, getAPIVersion(CEPH_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CEPH_OBJECT_STORE_KIND)) {            
                final Object cephObjectStoreO;
                if (namespace == null || namespace.length() == 0)
                    cephObjectStoreO = coa.listClusterCustomObject(CEPH_GROUP, V1_VERSION, CEPH_OBJECT_STORE_PLURAL, null, null, null, null);                           
                else
                    cephObjectStoreO = coa.listNamespacedCustomObject(CEPH_GROUP, V1_VERSION, namespace, CEPH_OBJECT_STORE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, cephObjectStoreO), CEPH_OBJECT_STORE_KIND, CEPH_OBJECT_STORE_PROPERTY_NAME, getAPIVersion(CEPH_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CEPH_OBJECT_STORE_USER_KIND)) {            
                final Object cephObjectStoreUserO;
                if (namespace == null || namespace.length() == 0)
                    cephObjectStoreUserO = coa.listClusterCustomObject(CEPH_GROUP, V1_VERSION, CEPH_OBJECT_STORE_USER_PLURAL, null, null, null, null);                           
                else
                    cephObjectStoreUserO = coa.listNamespacedCustomObject(CEPH_GROUP, V1_VERSION, namespace, CEPH_OBJECT_STORE_USER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, cephObjectStoreUserO), CEPH_OBJECT_STORE_USER_KIND, CEPH_OBJECT_STORE_USER_PROPERTY_NAME, getAPIVersion(CEPH_GROUP, V1_VERSION)); 
            }
    
            if (kind.equalsIgnoreCase(CERTIFICATE_SIGNING_REQUEST_KIND)) {            
                final Object certificateSigningO = coa.listClusterCustomObject(CERTIFICATE_SIGNING_REQUEST_GROUP, APP_VERSION, CERTIFICATE_SIGNING_REQUEST_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, certificateSigningO), CERTIFICATE_SIGNING_REQUEST_KIND, CERTIFICATE_SIGNING_REQUEST_PROPERTY_NAME, getAPIVersion(CERTIFICATE_SIGNING_REQUEST_GROUP, V1ALPHA1_VERSION)); 
            }
    
            if (kind.equalsIgnoreCase(CLUSTER_AUTOSCALER_KIND)) {            
                final Object clusterAutoscalerO = coa.listClusterCustomObject(CLUSTER_AUTOSCALER_GROUP, V1_VERSION, CLUSTER_AUTOSCALER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, clusterAutoscalerO), CLUSTER_AUTOSCALER_KIND, CLUSTER_AUTOSCALER_PROPERTY_NAME, getAPIVersion(CLUSTER_AUTOSCALER_GROUP, V1_VERSION)); 
            }
    
            if (kind.equalsIgnoreCase(CLUSTER_NETWORK_KIND)) {            
                final Object clusterNetworkO = coa.listClusterCustomObject(CLUSTER_NETWORK_GROUP, V1_VERSION, CLUSTER_NETWORK_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, clusterNetworkO), CLUSTER_NETWORK_KIND, CLUSTER_NETWORK_PROPERTY_NAME, getAPIVersion(CLUSTER_NETWORK_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CLUSTER_OPERATOR_KIND)) {            
                final Object clusterOperatorO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, CLUSTER_OPERATOR_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, clusterOperatorO), CLUSTER_OPERATOR_KIND, CLUSTER_OPERATOR_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CLUSTER_ROLE_KIND)) {            
                final Object clusterRoleO = coa.listClusterCustomObject(CLUSTER_ROLE_GROUP, V1_VERSION, CLUSTER_ROLE_PLURAL, null, null, null, null);                                                                     
                response = processResources(client, getItemsAsList(client, clusterRoleO), CLUSTER_ROLE_KIND, CLUSTER_ROLE_PROPERTY_NAME, getAPIVersion(CLUSTER_ROLE_GROUP, V1_VERSION)); 
            }
            
            if (kind.equalsIgnoreCase(CLUSTER_ROLE_BINDING_KIND)) {            
                final Object clusterRoleBindingO = coa.listClusterCustomObject(CLUSTER_ROLE_GROUP, V1_VERSION, CLUSTER_ROLE_BINDING_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, clusterRoleBindingO), CLUSTER_ROLE_BINDING_KIND, CLUSTER_ROLE_BINDING_PROPERTY_NAME, getAPIVersion(CLUSTER_ROLE_GROUP, V1_VERSION)); 
            }
          
            if (kind.equalsIgnoreCase(CLUSTER_SERVICE_VERSION_KIND)) {            
                final Object clusterServiceVersionO;
                if (namespace == null || namespace.length() == 0)
                    clusterServiceVersionO = coa.listClusterCustomObject(CLUSTER_SERVICE_VERSION_GROUP, V1ALPHA1_VERSION, CLUSTER_SERVICE_VERSION_PLURAL, null, null, null, null);                           
                else
                    clusterServiceVersionO = coa.listNamespacedCustomObject(CLUSTER_SERVICE_VERSION_GROUP, V1ALPHA1_VERSION, namespace, CLUSTER_SERVICE_VERSION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, clusterServiceVersionO), CLUSTER_SERVICE_VERSION_KIND, CLUSTER_SERVICE_VERSION_PROPERTY_NAME, getAPIVersion(CLUSTER_SERVICE_VERSION_GROUP, V1ALPHA1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CLUSTER_VERSION_KIND)) {            
                final Object clusterVersionO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, CLUSTER_VERSION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, clusterVersionO), CLUSTER_VERSION_KIND, CLUSTER_VERSION_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(COMPONENT_STATUS_KIND)) {  
                final Object componentStatusO = api.listComponentStatus(null, null, null, null, null, null, null, null, null);             
                response = processResources(client, getItemsAsList(client, componentStatusO), COMPONENT_STATUS_KIND, COMPONENT_STATUS_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONFIG_MAP_KIND)) {
                final Object configMapO;
                if (namespace == null || namespace.length() == 0)
                    configMapO = api.listConfigMapForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else 
                    configMapO = api.listNamespacedConfigMap(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, configMapO), CONFIG_MAP_KIND, CONFIG_MAP_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONSOLE_KIND)) {                           
                final Object consoleO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, CONSOLE_PLURAL, null, null, null, null);                                                                 
                response = processResources(client, getItemsAsList(client, consoleO), CONSOLE_KIND, CONSOLE_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONSOLE_CLI_DOWNLOAD_KIND)) {                           
                final Object consoleCLIDownloadO = coa.listClusterCustomObject(CONSOLE_GROUP, V1_VERSION, CONSOLE_CLI_DOWNLOAD_PLURAL, null, null, null, null);                                                                 
                response = processResources(client, getItemsAsList(client, consoleCLIDownloadO), CONSOLE_KIND, CONSOLE_CLI_DOWNLOAD_PROPERTY_NAME, getAPIVersion(CONSOLE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONSOLE_EXTERNAL_LINK_KIND)) {                           
                final Object consoleExternalLinkO = coa.listClusterCustomObject(CONSOLE_GROUP, V1_VERSION, CONSOLE_EXTERNAL_LINK_PLURAL, null, null, null, null);                                                                 
                response = processResources(client, getItemsAsList(client, consoleExternalLinkO), CONSOLE_EXTERNAL_LINK_KIND, CONSOLE_EXTERNAL_LINK_PROPERTY_NAME, getAPIVersion(CONSOLE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONSOLE_LINK_KIND)) {                           
                final Object consoleLinkO = coa.listClusterCustomObject(CONSOLE_GROUP, V1_VERSION, CONSOLE_LINK_PLURAL, null, null, null, null);                                                                 
                response = processResources(client, getItemsAsList(client, consoleLinkO), CONSOLE_LINK_KIND, CONSOLE_LINK_PROPERTY_NAME, getAPIVersion(CONSOLE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONSOLE_NOTIFICATION_KIND)) {                           
                final Object consoleNotificationO = coa.listClusterCustomObject(CONSOLE_GROUP, V1_VERSION, CONSOLE_NOTIFICATION_PLURAL, null, null, null, null);                                                                 
                response = processResources(client, getItemsAsList(client, consoleNotificationO), CONSOLE_NOTIFICATION_KIND, CONSOLE_NOTIFICATION_PROPERTY_NAME, getAPIVersion(CONSOLE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONSOLE_YAML_SAMPLE_KIND)) {                           
                final Object consoleYAMLSampleO = coa.listClusterCustomObject(CONSOLE_GROUP, V1_VERSION, CONSOLE_YAML_SAMPLE_PLURAL, null, null, null, null);                                                                 
                response = processResources(client, getItemsAsList(client, consoleYAMLSampleO), CONSOLE_YAML_SAMPLE_KIND, CONSOLE_YAML_SAMPLE_PROPERTY_NAME, getAPIVersion(CONSOLE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONTAINER_RUNTIME_CONFIG_KIND)) {                           
                final Object containerRuntimeO = coa.listClusterCustomObject(CONTAINER_RUNTIME_CONFIG_GROUP, V1_VERSION, CONTAINER_RUNTIME_CONFIG_PLURAL, null, null, null, null);                                                                 
                response = processResources(client, getItemsAsList(client, containerRuntimeO), CONTAINER_RUNTIME_CONFIG_KIND, CONTAINER_RUNTIME_CONFIG_PROPERTY_NAME, getAPIVersion(CONTAINER_RUNTIME_CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONTROLLER_CONFIG_KIND)) {                           
                final Object controllerConfigO = coa.listClusterCustomObject(CONTROLLER_CONFIG_GROUP, V1_VERSION, CONTROLLER_CONFIG_PLURAL, null, null, null, null);                                                                 
                response = processResources(client, getItemsAsList(client, controllerConfigO), CONTROLLER_CONFIG_KIND, CONTROLLER_CONFIG_PROPERTY_NAME, getAPIVersion(CONTROLLER_CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CONTROLLER_REVISION_KIND)) {            
                final Object controllerRevisionO;
                if (namespace == null || namespace.length() == 0)
                    controllerRevisionO = coa.listClusterCustomObject(DEPLOYMENT_GROUP, V1_VERSION, CONTROLLER_REVISION_PLURAL, null, null, null, null);                           
                else
                    controllerRevisionO = coa.listNamespacedCustomObject(DEPLOYMENT_GROUP, V1_VERSION, namespace, CONTROLLER_REVISION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, controllerRevisionO), CONTROLLER_REVISION_KIND, CONTROLLER_REVISION_PROPERTY_NAME, getAPIVersion(DEPLOYMENT_GROUP, V1_VERSION)); 
            } 

            if (kind.equalsIgnoreCase(CREDENTIALS_REQUEST_KIND)) {            
                final Object credRequestO;
                if (namespace == null || namespace.length() == 0)
                    credRequestO = coa.listClusterCustomObject(CREDENTIALS_REQUEST_GROUP, V1_VERSION, CREDENTIALS_REQUEST_PLURAL, null, null, null, null);                           
                else
                    credRequestO = coa.listNamespacedCustomObject(CREDENTIALS_REQUEST_GROUP, V1_VERSION, namespace, CREDENTIALS_REQUEST_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, credRequestO), CREDENTIALS_REQUEST_KIND, CREDENTIALS_REQUEST_PROPERTY_NAME, getAPIVersion(CREDENTIALS_REQUEST_GROUP, V1_VERSION)); 
            } 

            if (kind.equalsIgnoreCase(CRON_JOB_KIND)) {            
                final Object cronJobO;
                if (namespace == null || namespace.length() == 0)
                    cronJobO = coa.listClusterCustomObject(JOB_GROUP, APP_VERSION, CRON_JOB_PLURAL, null, null, null, null);                           
                else
                    cronJobO = coa.listNamespacedCustomObject(JOB_GROUP, APP_VERSION, namespace, CRON_JOB_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, cronJobO), CRON_JOB_KIND, CRON_JOB_PROPERTY_NAME, getAPIVersion(JOB_GROUP, APP_VERSION)); 
            } 

            if (kind.equalsIgnoreCase(CSI_DRIVER_KIND)) {           
                final Object csiDriverO = coa.listClusterCustomObject(CSI_DRIVER_GROUP, APP_VERSION, CSI_DRIVER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, csiDriverO), CSI_DRIVER_KIND, CSI_DRIVER_PROPERTY_NAME, getAPIVersion(CSI_DRIVER_GROUP, APP_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CSI_NODE_KIND)) {           
                final Object csiNodeO = coa.listClusterCustomObject(CSI_NODE_GROUP, APP_VERSION, CSI_NODE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, csiNodeO), CSI_NODE_KIND, CSI_NODE_PROPERTY_NAME, getAPIVersion(CSI_NODE_GROUP, APP_VERSION)); 
            }

            if (kind.equalsIgnoreCase(CUSTOM_RESOURCE_DEFINITION_KIND)) {           
                final Object customResourceDefinitionO = coa.listClusterCustomObject(CUSTOM_RESOURCE_DEFINITION_GROUP, APP_VERSION, CUSTOM_RESOURCE_DEFINITION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, customResourceDefinitionO), CUSTOM_RESOURCE_DEFINITION_KIND, CUSTOM_RESOURCE_DEFINITION_PROPERTY_NAME, getAPIVersion(CUSTOM_RESOURCE_DEFINITION_GROUP, APP_VERSION)); 
            }

            if (kind.equalsIgnoreCase(DAEMON_SET_KIND)) {            
                final Object daemonSetO;
                if (namespace == null || namespace.length() == 0)
                    daemonSetO = coa.listClusterCustomObject(DEPLOYMENT_GROUP, V1_VERSION, DAEMON_SET_PLURAL, null, null, null, null);                           
                else
                    daemonSetO = coa.listNamespacedCustomObject(DEPLOYMENT_GROUP, V1_VERSION, namespace, DAEMON_SET_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, daemonSetO), DAEMON_SET_KIND, DAEMON_SET_PROPERTY_NAME, getAPIVersion(DEPLOYMENT_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(DEPLOYMENT_KIND)) {                
                final Object deplO;
                if (namespace == null || namespace.length() == 0)
                    deplO = coa.listClusterCustomObject(DEPLOYMENT_GROUP, V1_VERSION, DEPLOYMENT_PLURAL, null, null, null, null);                           
                else
                    deplO = coa.listNamespacedCustomObject(DEPLOYMENT_GROUP, V1_VERSION, namespace, DEPLOYMENT_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, deplO), DEPLOYMENT_KIND, DEPLOYMENT_PROPERTY_NAME, getAPIVersion(DEPLOYMENT_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(DEPLOYMENT_CONFIG_KIND)) {                
                final Object deplConfigO;
                if (namespace == null || namespace.length() == 0)
                    deplConfigO = coa.listClusterCustomObject(DEPLOYMENT_CONFIG_GROUP, V1_VERSION, DEPLOYMENT_CONFIG_PLURAL, null, null, null, null);                           
                else
                    deplConfigO = coa.listNamespacedCustomObject(DEPLOYMENT_CONFIG_GROUP, V1_VERSION, namespace, DEPLOYMENT_CONFIG_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, deplConfigO), DEPLOYMENT_CONFIG_KIND, DEPLOYMENT_CONFIG_PROPERTY_NAME, getAPIVersion(DEPLOYMENT_CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(DNS_KIND)) {            
                final Object dnsO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, DNS_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, dnsO), DNS_KIND, DNS_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            } 

            if (kind.equalsIgnoreCase(DNS_RECORD_KIND)) {                
                final Object dnsRecordO;
                if (namespace == null || namespace.length() == 0)
                    dnsRecordO = coa.listClusterCustomObject(DNS_RECORD_GROUP, V1_VERSION, DNS_RECORD_PLURAL, null, null, null, null);                           
                else
                    dnsRecordO = coa.listNamespacedCustomObject(DNS_RECORD_GROUP, V1_VERSION, namespace, DNS_RECORD_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, dnsRecordO), DNS_RECORD_KIND, DNS_RECORD_PROPERTY_NAME, getAPIVersion(DNS_RECORD_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(EGRESS_NETWORK_POLICY_KIND)) {                
                final Object egressNetworkPolicyO;
                if (namespace == null || namespace.length() == 0)
                    egressNetworkPolicyO = coa.listClusterCustomObject(EGRESS_NETWORK_POLICY_GROUP, V1_VERSION, EGRESS_NETWORK_POLICY_PLURAL, null, null, null, null);                           
                else
                    egressNetworkPolicyO = coa.listNamespacedCustomObject(EGRESS_NETWORK_POLICY_GROUP, V1_VERSION, namespace, EGRESS_NETWORK_POLICY_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, egressNetworkPolicyO), EGRESS_NETWORK_POLICY_KIND, EGRESS_NETWORK_POLICY_PROPERTY_NAME, getAPIVersion(EGRESS_NETWORK_POLICY_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(ENDPOINT_KIND)) {  
                final Object endpointO;
                if (namespace == null || namespace.length() == 0)
                    endpointO = api.listEndpointsForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    endpointO = api.listNamespacedEndpoints(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, endpointO), ENDPOINT_KIND, ENDPOINT_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(EVENT_KIND)) {  
                final Object eventO;
                if (namespace == null || namespace.length() == 0)
                    eventO = api.listEventForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    eventO = api.listNamespacedEvent(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, eventO), EVENT_KIND, EVENT_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(FEATURE_GATE_KIND)) {                           
                final Object featureGateO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, FEATURE_GATE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, featureGateO), FEATURE_GATE_KIND, FEATURE_GATE_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(GROUP_KIND)) {            
                final Object groupO = coa.listClusterCustomObject(GROUP_GROUP, V1_VERSION, GROUP_PLURAL, null, null, null, null);                                    
                response = processResources(client, getItemsAsList(client, groupO), GROUP_KIND, GROUP_PROPERTY_NAME, getAPIVersion(GROUP_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(HORIZONTAL_POD_AUTOSCALER_KIND)) {            
                final Object horizontalPodAutoscalerO;
                if (namespace == null || namespace.length() == 0)
                    horizontalPodAutoscalerO = coa.listClusterCustomObject(HORIZONTAL_POD_AUTOSCALER_GROUP, V1_VERSION, HORIZONTAL_POD_AUTOSCALER_PLURAL, null, null, null, null);                           
                else
                    horizontalPodAutoscalerO = coa.listNamespacedCustomObject(HORIZONTAL_POD_AUTOSCALER_GROUP, V1_VERSION, namespace, HORIZONTAL_POD_AUTOSCALER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, horizontalPodAutoscalerO), HORIZONTAL_POD_AUTOSCALER_KIND, HORIZONTAL_POD_AUTOSCALER_PROPERTY_NAME, getAPIVersion(HORIZONTAL_POD_AUTOSCALER_GROUP, V1_VERSION)); 
            } 

            if (kind.equalsIgnoreCase(HOST_SUBNET_KIND)) {            
                final Object hostSubnetO = coa.listClusterCustomObject(HOST_SUBNET_GROUP, V1_VERSION, HOST_SUBNET_PLURAL, null, null, null, null);                                    
                response = processResources(client, getItemsAsList(client, hostSubnetO), HOST_SUBNET_KIND, HOST_SUBNET_PROPERTY_NAME, getAPIVersion(HOST_SUBNET_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(IDENTITY_KIND)) {            
                final Object identityO = coa.listClusterCustomObject(IDENTITY_GROUP, V1_VERSION, IDENTITY_PLURAL, null, null, null, null);                                    
                response = processResources(client, getItemsAsList(client, identityO), IDENTITY_KIND, IDENTITY_PROPERTY_NAME, getAPIVersion(IDENTITY_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(IMAGE_KIND)) {            
                final Object imageO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, IMAGE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, imageO), IMAGE_KIND, IMAGE_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(IMAGE_CONTENT_SOURCE_POLICY_KIND)) {            
                final Object imageCSPO = coa.listClusterCustomObject(OPERATOR_GROUP, V1ALPHA1_VERSION, IMAGE_CONTENT_SOURCE_POLICY_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, imageCSPO), IMAGE_CONTENT_SOURCE_POLICY_KIND, IMAGE_CONTENT_SOURCE_POLICY_PROPERTY_NAME, getAPIVersion(OPERATOR_GROUP, V1ALPHA1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(IMAGE_STREAM_KIND)) {            
                final Object imageStreamO;
                if (namespace == null || namespace.length() == 0)
                    imageStreamO = coa.listClusterCustomObject(IMAGE_STREAM_GROUP, V1_VERSION, IMAGE_STREAM_PLURAL, null, null, null, null);                           
                else
                    imageStreamO = coa.listNamespacedCustomObject(IMAGE_STREAM_GROUP, V1_VERSION, namespace, IMAGE_STREAM_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, imageStreamO), IMAGE_STREAM_KIND, IMAGE_STREAM_PROPERTY_NAME, getAPIVersion(IMAGE_STREAM_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(IMAGE_STREAM_TAG_KIND)) {            
                final Object imageStreamTagO;
                if (namespace == null || namespace.length() == 0)
                    imageStreamTagO = coa.listClusterCustomObject(IMAGE_STREAM_TAG_GROUP, V1_VERSION, IMAGE_STREAM_TAG_PLURAL, null, null, null, null);                           
                else
                    imageStreamTagO = coa.listNamespacedCustomObject(IMAGE_STREAM_TAG_GROUP, V1_VERSION, namespace, IMAGE_STREAM_TAG_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, imageStreamTagO), IMAGE_STREAM_TAG_KIND, IMAGE_STREAM_TAG_PROPERTY_NAME, getAPIVersion(IMAGE_STREAM_TAG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(INFRASTRUCTURE_KIND)) {            
                final Object infraO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, INFRASTRUCTURE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, infraO), INFRASTRUCTURE_KIND, INFRASTRUCTURE_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(INGRESS_KIND)) {            
                final Object ingressO;
                if (namespace == null || namespace.length() == 0)
                    ingressO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, INGRESS_PLURAL, null, null, null, null);                           
                else
                    ingressO = coa.listNamespacedCustomObject(CONFIG_GROUP, V1_VERSION, namespace, INGRESS_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, ingressO), INGRESS_KIND, INGRESSE_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(INGRESS_CONTROLLER_KIND)) {            
                final Object ingressControllerO;
                if (namespace == null || namespace.length() == 0)
                    ingressControllerO = coa.listClusterCustomObject(OPERATOR_GROUP, V1_VERSION, INGRESS_CONTROLLER_PLURAL, null, null, null, null);                           
                else
                    ingressControllerO = coa.listNamespacedCustomObject(OPERATOR_GROUP, V1_VERSION, namespace, INGRESS_CONTROLLER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, ingressControllerO), INGRESS_CONTROLLER_KIND, INGRESS_CONTROLLER_PROPERTY_NAME, getAPIVersion(OPERATOR_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(JOB_KIND)) {            
                final Object jobO;
                if (namespace == null || namespace.length() == 0)
                    jobO = coa.listClusterCustomObject(JOB_GROUP, V1_VERSION, JOB_PLURAL, null, null, null, null);                           
                else
                    jobO = coa.listNamespacedCustomObject(JOB_GROUP, V1_VERSION, namespace, JOB_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, jobO), JOB_KIND, JOB_PROPERTY_NAME, getAPIVersion(JOB_GROUP, V1_VERSION)); 
            } 

            if (kind.equalsIgnoreCase(KAM_KIND)) { 
                final Object kamO;
                if (namespace == null || namespace.length() == 0)
                    kamO = coa.listClusterCustomObject(KAM_GROUP, V1_VERSION, KAM_PLURAL, null, null, null, null);                           
                else
                    kamO = coa.listNamespacedCustomObject(KAM_GROUP, V1_VERSION, namespace, KAM_PLURAL, null, null, null, null);
                response = processResources(client, getItemsAsList(client, kamO), KAM_KIND, KAM_PROPERTY_NAME, getAPIVersion(KAM_GROUP, V1_VERSION));                    
            }

            if (kind.equalsIgnoreCase(KAPPNAV_KIND)) {  
                final Object kappnavO;              
                if (namespace == null || namespace.length() == 0)
                    kappnavO = coa.listClusterCustomObject(KAPPNAV_CR_GROUP, V1_VERSION, KAPPNAV_PLURAL, null, null, null, null);   
                else 
                    kappnavO = coa.listNamespacedCustomObject(KAPPNAV_CR_GROUP, V1_VERSION, namespace, KAPPNAV_PLURAL, null, null, null, null);                                                     
                response = processResources(client, getItemsAsList(client, kappnavO), KAPPNAV_KIND, KAPPNAV_PROPERTY_NAME, getAPIVersion(KAPPNAV_CR_GROUP, V1_VERSION));                    
            } 

            if (kind.equalsIgnoreCase(KUBE_API_SERVER_KIND)) {            
                final Object kubeAPIServerO = coa.listClusterCustomObject(OPERATOR_GROUP, V1_VERSION, KUBE_API_SERVER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, kubeAPIServerO), KUBE_API_SERVER_KIND, KUBE_API_SERVER_PROPERTY_NAME, getAPIVersion(OPERATOR_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(KUBE_CONTROLLER_MANAGER_KIND)) {            
                final Object kubeControllerManagerO = coa.listClusterCustomObject(OPERATOR_GROUP, V1_VERSION, KUBE_CONTROLLER_MANAGER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, kubeControllerManagerO), KUBE_CONTROLLER_MANAGER_KIND, KUBE_CONTROLLER_MANAGER_PROPERTY_NAME, getAPIVersion(OPERATOR_GROUP, V1_VERSION)); 
            }
            if (kind.equalsIgnoreCase(KUBE_SCHEDULER_KIND)) {            
                final Object kubeSchedulerO = coa.listClusterCustomObject(OPERATOR_GROUP, V1_VERSION, KUBE_SCHEDULER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, kubeSchedulerO), KUBE_SCHEDULER_KIND, KUBE_SCHEDULER_PROPERTY_NAME, getAPIVersion(OPERATOR_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(KUBELET_CONFIG_KIND)) {            
                final Object kubeletConfigO = coa.listClusterCustomObject(KUBELET_CONFIG_GROUP, V1_VERSION, KUBELET_CONFIG_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, kubeletConfigO), KUBELET_CONFIG_KIND, KUBELET_CONFIG_PROPERTY_NAME, getAPIVersion(KUBELET_CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(LEASE_KIND)) {              
                final Object leaseO;   
                if (namespace == null || namespace.length() == 0)
                    leaseO = coa.listClusterCustomObject(LEASE_GROUP, V1_VERSION, LEASE_PLURAL, null, null, null, null);                         
                else 
                    leaseO = coa.listNamespacedCustomObject(LEASE_GROUP, V1_VERSION, namespace, LEASE_PLURAL, null, null, null, null);                       
                response = processResources(client, getItemsAsList(client, leaseO), LEASE_KIND, LEASE_PROPERTY_NAME, getAPIVersion(LEASE_GROUP, V1_VERSION));                
            }

            if (kind.equalsIgnoreCase(LIBERTY_APP_KIND)) {              
                final Object libAppO;   
                if (namespace == null || namespace.length() == 0)
                    libAppO = coa.listClusterCustomObject(KAPPNAV_GROUP, APP_VERSION, LIBERTY_APP_PLURAL, null, null, null, null);                         
                else 
                    libAppO = coa.listNamespacedCustomObject(KAPPNAV_GROUP, APP_VERSION, namespace, LIBERTY_APP_PLURAL, null, null, null, null);                       
                response = processResources(client, getItemsAsList(client, libAppO), LIBERTY_APP_KIND, LIBERTY_APP_PROPERTY_NAME, getAPIVersion(KAPPNAV_GROUP, APP_VERSION));                
            }

            if (kind.equalsIgnoreCase(LIBERTY_COLLECTIVE_KIND)) {              
                final Object libCollectiveO;   
                if (namespace == null || namespace.length() == 0)
                    libCollectiveO = coa.listClusterCustomObject(KAPPNAV_GROUP, APP_VERSION, LIBERTY_COLLECTIVE_PLURAL, null, null, null, null);                                           
                else 
                    libCollectiveO = coa.listNamespacedCustomObject(KAPPNAV_GROUP, APP_VERSION, namespace, LIBERTY_COLLECTIVE_PLURAL, null, null, null, null);   
                response = processResources(client, getItemsAsList(client, libCollectiveO), LIBERTY_COLLECTIVE_KIND, LIBERTY_COLLECTIVE_PROPERTY_NAME, getAPIVersion(KAPPNAV_GROUP, APP_VERSION));                
            }

            if (kind.equalsIgnoreCase(LIMIT_RANGE_KIND)) {  
                final Object limitRangeO;
                if (namespace == null || namespace.length() == 0)
                    limitRangeO = api.listLimitRangeForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    limitRangeO = api.listNamespacedLimitRange(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, limitRangeO), LIMIT_RANGE_KIND, LIMIT_RANGE_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(MACHINE_KIND)) {            
                final Object machineO;
                if (namespace == null || namespace.length() == 0)
                    machineO = coa.listClusterCustomObject(MACHINE_GROUP, APP_VERSION, MACHINE_PLURAL, null, null, null, null);                           
                else
                    machineO = coa.listNamespacedCustomObject(MACHINE_GROUP, APP_VERSION, namespace, MACHINE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, machineO), MACHINE_KIND, MACHINE_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(MACHINE_AUTOSCALER_KIND)) {            
                final Object machineAutoscalerO;
                if (namespace == null || namespace.length() == 0)
                    machineAutoscalerO = coa.listClusterCustomObject(MACHINE_AUTOSCALER_GROUP, APP_VERSION, MACHINE_AUTOSCALER_PLURAL, null, null, null, null);                           
                else
                    machineAutoscalerO = coa.listNamespacedCustomObject(MACHINE_AUTOSCALER_GROUP, APP_VERSION, namespace, MACHINE_AUTOSCALER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, machineAutoscalerO), MACHINE_AUTOSCALER_KIND, MACHINE_AUTOSCALER_PROPERTY_NAME, getAPIVersion(MACHINE_AUTOSCALER_GROUP, APP_VERSION)); 
            }
            
            if (kind.equalsIgnoreCase(MACHINE_CONFIG_KIND)) {            
                final Object machineConfigO = coa.listClusterCustomObject(MACHINE_CONFIG_GROUP, V1_VERSION, MACHINE_CONFIG_PLURAL, null, null, null, null);                             
                response = processResources(client, getItemsAsList(client, machineConfigO), MACHINE_CONFIG_KIND, MACHINE_CONFIG_PROPERTY_NAME, getAPIVersion(MACHINE_CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(MACHINE_CONFIG_POOL_KIND)) {            
                final Object machineConfigPoolO = coa.listClusterCustomObject(MACHINE_CONFIG_GROUP, V1_VERSION, MACHINE_CONFIG_POOL_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, machineConfigPoolO), MACHINE_CONFIG_POOL_KIND, MACHINE_CONFIG_POOL_PROPERTY_NAME, getAPIVersion(MACHINE_CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(MACHINE_HEALTH_CHECK_KIND)) {            
                final Object machineHealthCheckO;
                if (namespace == null || namespace.length() == 0)
                    machineHealthCheckO = coa.listClusterCustomObject(MACHINE_GROUP, APP_VERSION, MACHINE_HEALTH_CHECK_PLURAL, null, null, null, null);                           
                else
                    machineHealthCheckO = coa.listNamespacedCustomObject(MACHINE_GROUP, APP_VERSION, namespace, MACHINE_HEALTH_CHECK_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, machineHealthCheckO), MACHINE_HEALTH_CHECK_KIND, MACHINE_HEALTH_CHECK_PROPERTY_NAME, getAPIVersion(MACHINE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(MACHINE_SET_KIND)) {            
                final Object machineSetO;
                if (namespace == null || namespace.length() == 0)
                    machineSetO = coa.listClusterCustomObject(MACHINE_GROUP, APP_VERSION, MACHINE_SET_PLURAL, null, null, null, null);                           
                else
                    machineSetO = coa.listNamespacedCustomObject(MACHINE_GROUP, APP_VERSION, namespace, MACHINE_SET_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, machineSetO), MACHINE_SET_KIND, MACHINE_SET_PROPERTY_NAME, getAPIVersion(MACHINE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(MCO_CONFIG_KIND)) {            
                final Object mcoConfigO;
                if (namespace == null || namespace.length() == 0)
                    mcoConfigO = coa.listClusterCustomObject(MACHINE_CONFIG_GROUP, V1_VERSION, MCO_CONFIG_PLURAL, null, null, null, null);                           
                else
                    mcoConfigO = coa.listNamespacedCustomObject(MACHINE_CONFIG_GROUP, V1_VERSION, namespace, MCO_CONFIG_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, mcoConfigO), MCO_CONFIG_KIND, MCO_CONFIG_PROPERTY_NAME, getAPIVersion(MACHINE_CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(MUTATING_WEBHOOK_CONFIGURATION_KIND)) {            
                final Object mwcO = coa.listClusterCustomObject(MUTATING_WEBHOOK_CONFIGURATION_GROUP, V1_VERSION, MUTATING_WEBHOOK_CONFIGURATION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, mwcO), MUTATING_WEBHOOK_CONFIGURATION_KIND, MUTATING_WEBHOOK_CONFIGURATION_PROPERTY_NAME, getAPIVersion(MUTATING_WEBHOOK_CONFIGURATION_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(NAMESPACE_KIND)) {  
                final Object namespaceO = api.listNamespace(null, null, null, null, null, null, null, null, null);               
                response = processResources(client, getItemsAsList(client, namespaceO), NAMESPACE_KIND, NAMESPACE_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }
           
            if (kind.equalsIgnoreCase(NET_NAMESPACE_KIND)) {            
                final Object netNamespaceO = coa.listClusterCustomObject(NET_NAMESPACE_GROUP, V1_VERSION, NET_NAMESPACE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, netNamespaceO), NET_NAMESPACE_KIND, NET_NAMESPACE_PROPERTY_NAME, getAPIVersion(NET_NAMESPACE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(NETWORK_KIND)) {            
                final Object networkO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, NETWORK_PLURAL, null, null, null, null);                            
                response = processResources(client, getItemsAsList(client, networkO), NETWORK_KIND, NETWORK_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }
           
            if (kind.equalsIgnoreCase(NETWORK_POLICY_KIND)) {            
                final Object networkPolicyO;
                if (namespace == null || namespace.length() == 0)
                    networkPolicyO = coa.listClusterCustomObject(NETWORK_POLICY_GROUP, V1_VERSION, NETWORK_POLICY_PLURAL, null, null, null, null);                           
                else
                    networkPolicyO = coa.listNamespacedCustomObject(NETWORK_POLICY_GROUP, V1_VERSION, namespace, NETWORK_POLICY_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, networkPolicyO), NETWORK_POLICY_KIND, NETWORK_POLICY_PROPERTY_NAME, getAPIVersion(NETWORK_POLICY_GROUP, V1_VERSION)); 
            }
          
            if (kind.equalsIgnoreCase(NODE_KIND)) {  
                final Object nodeO = api.listNode(null, null, null, null, null, null, null, null, null);             
                response = processResources(client, getItemsAsList(client, nodeO), NODE_KIND, NODE_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }
            
            if (kind.equalsIgnoreCase(OAUTH_KIND)) {            
                final Object oauthO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, OAUTH_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, oauthO), OAUTH_KIND, OAUTH_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(OAUTH_ACCESS_TOKEN_KIND)) {            
                final Object oauthO = coa.listClusterCustomObject(OAUTH_GROUP, V1_VERSION, OAUTH_ACCESS_TOKEN_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, oauthO), OAUTH_ACCESS_TOKEN_KIND, OAUTH_ACCESS_TOKEN_PROPERTY_NAME, getAPIVersion(OAUTH_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(OAUTH_AUTHORIZE_TOKEN_KIND)) {            
                final Object oauthO = coa.listClusterCustomObject(OAUTH_GROUP, V1_VERSION, OAUTH_AUTHORIZE_TOKEN_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, oauthO), OAUTH_AUTHORIZE_TOKEN_KIND, OAUTH_AUTHORIZE_TOKEN_PROPERTY_NAME, getAPIVersion(OAUTH_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(OAUTH_CLIENT_KIND)) {            
                final Object oauthO = coa.listClusterCustomObject(OAUTH_GROUP, V1_VERSION, OAUTH_CLIENT_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, oauthO), OAUTH_CLIENT_KIND, OAUTH_CLIENT_PROPERTY_NAME, getAPIVersion(OAUTH_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(OAUTH_CLIENT_AUTHORIZATION_KIND)) {            
                final Object oauthO = coa.listClusterCustomObject(OAUTH_GROUP, V1_VERSION, OAUTH_CLIENT_AUTHORIZATION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, oauthO), OAUTH_CLIENT_AUTHORIZATION_KIND, OAUTH_CLIENT_AUTHORIZATION_PROPERTY_NAME, getAPIVersion(OAUTH_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(OPEN_SHIFT_API_SERVER_KIND)) {            
                final Object openshiftAPIServerO = coa.listClusterCustomObject(OPERATOR_GROUP, V1_VERSION, OPEN_SHIFT_API_SERVER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, openshiftAPIServerO), OPEN_SHIFT_API_SERVER_KIND, OPEN_SHIFT_API_SERVER_PROPERTY_NAME, getAPIVersion(OPERATOR_GROUP, V1_VERSION)); 
            }
            if (kind.equalsIgnoreCase(OPEN_SHIFT_CONTROLLER_MANAGER_KIND)) {            
                final Object openshiftControllerO = coa.listClusterCustomObject(OPERATOR_GROUP, V1_VERSION, OPEN_SHIFT_CONTROLLER_MANAGER_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, openshiftControllerO), OPEN_SHIFT_CONTROLLER_MANAGER_KIND, OPEN_SHIFT_CONTROLLER_MANAGER_PROPERTY_NAME, getAPIVersion(OPERATOR_GROUP, V1_VERSION)); 
            }
            if (kind.equalsIgnoreCase(OPERATOR_GROUP_KIND)) {            
                final Object operatorGroupO = coa.listClusterCustomObject(OPERATOR_GROUP_GROUP, V1_VERSION, OPERATOR_GROUP_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, operatorGroupO), OPERATOR_GROUP_KIND, OPERATOR_GROUP_PROPERTY_NAME, getAPIVersion(OPERATOR_GROUP_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(OPERATOR_HUB_KIND)) {            
                final Object operatorHubO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, OPERATOR_HUB_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, operatorHubO), OPERATOR_HUB_KIND, OPERATOR_HUB_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(OPERATOR_PKI_KIND)) {            
                final Object operatorPKIO;
                if (namespace == null || namespace.length() == 0)
                    operatorPKIO = coa.listClusterCustomObject(OPERATOR_PKI_GROUP, V1_VERSION, OPERATOR_PKI_PLURAL, null, null, null, null);                           
                else
                    operatorPKIO = coa.listNamespacedCustomObject(OPERATOR_PKI_GROUP, V1_VERSION, namespace, OPERATOR_PKI_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, operatorPKIO), OPERATOR_PKI_KIND, OPERATOR_PKI_PROPERTY_NAME, getAPIVersion(OPERATOR_PKI_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(OPERATOR_SOURCE_KIND)) {            
                final Object operatorSourceO;
                if (namespace == null || namespace.length() == 0)
                    operatorSourceO = coa.listClusterCustomObject(OPERATOR_SOURCE_GROUP, V1_VERSION, OPERATOR_SOURCE_PLURAL, null, null, null, null);                           
                else
                    operatorSourceO = coa.listNamespacedCustomObject(OPERATOR_SOURCE_GROUP, V1_VERSION, namespace, OPERATOR_SOURCE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, operatorSourceO), OPERATOR_SOURCE_KIND, OPERATOR_SOURCE_PROPERTY_NAME, getAPIVersion(OPERATOR_SOURCE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(PACKAGE_MANIFEST_KIND)) {            
                final Object packageMO;
                if (namespace == null || namespace.length() == 0)
                    packageMO = coa.listClusterCustomObject(PACKAGE_MANIFEST_GROUP, V1_VERSION, PACKAGE_MANIFEST_PLURAL, null, null, null, null);                           
                else
                    packageMO = coa.listNamespacedCustomObject(PACKAGE_MANIFEST_GROUP, V1_VERSION, namespace, PACKAGE_MANIFEST_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, packageMO), PACKAGE_MANIFEST_KIND, PACKAGE_MANIFEST_PROPERTY_NAME, getAPIVersion(PACKAGE_MANIFEST_GROUP, V1_VERSION)); 
            } 
   
            if (kind.equalsIgnoreCase(PERSISTENT_VOLUME_KIND)) {  
                final Object persistentVolumeO = api.listPersistentVolume(null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, persistentVolumeO), PERSISTENT_VOLUME_KIND, PERSISTENT_VOLUME_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }           

            if (kind.equalsIgnoreCase(PERSISTENT_VOLUME_CLAIM_KIND)) {  
                final Object persistentVolumeClaimO;
                if (namespace == null || namespace.length() == 0)
                    persistentVolumeClaimO = api.listPersistentVolumeClaimForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    persistentVolumeClaimO = api.listNamespacedPersistentVolumeClaim(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, persistentVolumeClaimO), PERSISTENT_VOLUME_CLAIM_KIND, PERSISTENT_VOLUME_CLAIM_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }      

            if (kind.equalsIgnoreCase(POD_KIND)) {               
                final Object podO;
                if (namespace == null || namespace.length() == 0)
                    podO = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    podO = api.listNamespacedPod(namespace, null, null, null, null, null, null, null, null, null);                                           
                response = processResources(client, getItemsAsList(client, podO), POD_KIND, POD_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }     
            
            if (kind.equalsIgnoreCase(POD_DISRUPTION_BUDGET_KIND)) {            
                final Object podDisruptionO;
                if (namespace == null || namespace.length() == 0)
                    podDisruptionO = coa.listClusterCustomObject(POD_DISRUPTION_BUDGET_GROUP, APP_VERSION, POD_DISRUPTION_BUDGET_PLURAL, null, null, null, null);                           
                else
                    podDisruptionO = coa.listNamespacedCustomObject(POD_DISRUPTION_BUDGET_GROUP, APP_VERSION, namespace, POD_DISRUPTION_BUDGET_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, podDisruptionO), POD_DISRUPTION_BUDGET_KIND, POD_DISRUPTION_BUDGET_PROPERTY_NAME, getAPIVersion(POD_DISRUPTION_BUDGET_GROUP, APP_VERSION)); 
            }

            if (kind.equalsIgnoreCase(PRIORITY_CLASS_KIND)) {            
                final Object priorityClassO = coa.listClusterCustomObject(PRIORITY_CLASS_GROUP, V1_VERSION, PRIORITY_CLASS_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, priorityClassO), PRIORITY_CLASS_KIND, PRIORITY_CLASS_PROPERTY_NAME, getAPIVersion(PRIORITY_CLASS_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(POD_TEMPLATE_KIND)) {  
                final Object podTemplateO;
                if (namespace == null || namespace.length() == 0)
                    podTemplateO = api.listPodTemplateForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    podTemplateO = api.listNamespacedPodTemplate(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, podTemplateO), POD_TEMPLATE_KIND, POD_TEMPLATE_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(PROJECT_KIND)) {            
                final Object projectO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, PROJECT_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, projectO), PROJECT_KIND, PROJECT_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(PROXY_KIND)) {            
                final Object proxyO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, PROXY_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, proxyO), PROXY_KIND, PROXY_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }
    
            if (kind.equalsIgnoreCase(PROMETHEUS_KIND)) {            
                final Object prometheusO;
                if (namespace == null || namespace.length() == 0)
                    prometheusO = coa.listClusterCustomObject(PROMETHEUS_GROUP, V1_VERSION, PROMETHEUS_PLURAL, null, null, null, null);                           
                else
                    prometheusO = coa.listNamespacedCustomObject(PROMETHEUS_GROUP, V1_VERSION, namespace, PROMETHEUS_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, prometheusO), PROMETHEUS_KIND, PROMETHEUS_PROPERTY_NAME, getAPIVersion(PROMETHEUS_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(PROMETHEUS_RULE_KIND)) {            
                final Object prometheusRuleO;
                if (namespace == null || namespace.length() == 0)
                    prometheusRuleO = coa.listClusterCustomObject(PROMETHEUS_RULE_GROUP, V1_VERSION, PROMETHEUS_RULE_PLURAL, null, null, null, null);                           
                else
                    prometheusRuleO = coa.listNamespacedCustomObject(PROMETHEUS_RULE_GROUP, V1_VERSION, namespace, PROMETHEUS_RULE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, prometheusRuleO), PROMETHEUS_RULE_KIND, PROMETHEUS_RULE_PROPERTY_NAME, getAPIVersion(PROMETHEUS_RULE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(RANGE_ALLOCATION_KIND)) {            
                final Object rangeAllocationO = coa.listClusterCustomObject(RANGE_ALLOCATION_GROUP, V1_VERSION, RANGE_ALLOCATION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, rangeAllocationO), RANGE_ALLOCATION_KIND, RANGE_ALLOCATION_PROPERTY_NAME, getAPIVersion(RANGE_ALLOCATION_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(REPLICA_CONTROLLER_KIND)) {  
                final Object replicaControllerO;
                if (namespace == null || namespace.length() == 0)
                    replicaControllerO = api.listReplicationControllerForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    replicaControllerO = api.listNamespacedReplicationController(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, replicaControllerO), REPLICA_CONTROLLER_KIND, REPLICA_CONTROLLER_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }   

            if (kind.equalsIgnoreCase(REPLICA_SET_KIND)) {            
                final Object replicaSetO;
                if (namespace == null || namespace.length() == 0)
                    replicaSetO = coa.listClusterCustomObject(DEPLOYMENT_GROUP, V1_VERSION, REPLICA_SET_PLURAL, null, null, null, null);                           
                else
                    replicaSetO = coa.listNamespacedCustomObject(DEPLOYMENT_GROUP, V1_VERSION, namespace, REPLICA_SET_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, replicaSetO), REPLICA_SET_KIND, REPLICA_SET_PROPERTY_NAME, getAPIVersion(DEPLOYMENT_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(RESOURCE_QUOTA_KIND)) {  
                final Object resourceQuotaO;
                if (namespace == null || namespace.length() == 0)
                    resourceQuotaO = api.listResourceQuotaForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    resourceQuotaO = api.listNamespacedResourceQuota(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, resourceQuotaO), RESOURCE_QUOTA_KIND, RESOURCE_QUOTA_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(ROLE_KIND)) {            
                final Object roleO;
                if (namespace == null || namespace.length() == 0)
                    roleO = coa.listClusterCustomObject(CLUSTER_ROLE_GROUP, V1_VERSION, ROLE_PLURAL, null, null, null, null);                           
                else
                    roleO = coa.listNamespacedCustomObject(CLUSTER_ROLE_GROUP, V1_VERSION, namespace, ROLE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, roleO), ROLE_KIND, ROLE_PROPERTY_NAME, getAPIVersion(CLUSTER_ROLE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(ROLE_BINDING_KIND)) {            
                final Object roleBindingO;
                if (namespace == null || namespace.length() == 0)
                    roleBindingO = coa.listClusterCustomObject(CLUSTER_ROLE_GROUP, V1_VERSION, ROLE_BINDING_PLURAL, null, null, null, null);                           
                else
                    roleBindingO = coa.listNamespacedCustomObject(CLUSTER_ROLE_GROUP, V1_VERSION, namespace, ROLE_BINDING_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, roleBindingO), ROLE_BINDING_KIND, ROLE_BINDING_PROPERTY_NAME, getAPIVersion(CLUSTER_ROLE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(ROUTE_KIND)) {            
                final Object routeO;
                if (namespace == null || namespace.length() == 0)
                    routeO = coa.listClusterCustomObject(ROUTE_GROUP, V1_VERSION, ROUTE_PLURAL, null, null, null, null);                           
                else
                    routeO = coa.listNamespacedCustomObject(ROUTE_GROUP, V1_VERSION, namespace, ROUTE_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, routeO), ROUTE_KIND, ROUTE_PROPERTY_NAME, getAPIVersion(ROUTE_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(RUNTIME_CLASS_KIND)) {            
                final Object runtimeClassO = coa.listClusterCustomObject(RUNTIME_CLASS_GROUP, APP_VERSION, RUNTIME_CLASS_PLURAL, null, null, null, null);                            
                response = processResources(client, getItemsAsList(client, runtimeClassO), RUNTIME_CLASS_KIND, RUNTIME_CLASS_PROPERTY_NAME, getAPIVersion(RUNTIME_CLASS_GROUP, APP_VERSION)); 
            }
                    
            if (kind.equalsIgnoreCase(SECRET_KIND)) {           
                final Object secretO;
                if (namespace == null || namespace.length() == 0)
                    secretO = api.listSecretForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else 
                    secretO = api.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, secretO), SECRET_KIND, SECRET_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(SCHEDULER_KIND)) {            
                final Object schedulerO = coa.listClusterCustomObject(CONFIG_GROUP, V1_VERSION, SCHEDULER_PLURAL, null, null, null, null);                            
                response = processResources(client, getItemsAsList(client, schedulerO), SCHEDULER_KIND, SCHEDULER_PROPERTY_NAME, getAPIVersion(CONFIG_GROUP, V1_VERSION)); 
            }
            
            if (kind.equalsIgnoreCase(SERVICE_KIND)) {
                final Object serviceO;
                if (namespace == null || namespace.length() == 0)
                    serviceO = api.listServiceForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else
                    serviceO = api.listNamespacedService(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, serviceO), SERVICE_KIND, SERVICE_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(SERVICE_ACCOUNT_KIND)) {
                final Object serviceAccountO;
                if (namespace == null || namespace.length() == 0)
                    serviceAccountO = api.listServiceAccountForAllNamespaces(null, null, null, null, null, null, null, null, null);
                else 
                    serviceAccountO = api.listNamespacedServiceAccount(namespace, null, null, null, null, null, null, null, null, null);
                response = processResources(client, getItemsAsList(client, serviceAccountO), SERVICE_ACCOUNT_KIND, SERVICE_ACCOUNT_PROPERTY_NAME, getAPIVersion("", V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(SERVICE_MONITOR_KIND)) {            
                final Object serviceMonitorO;
                if (namespace == null || namespace.length() == 0)
                    serviceMonitorO = coa.listClusterCustomObject(SERVICE_MONITOR_GROUP, V1_VERSION, SERVICE_MONITOR_PLURAL, null, null, null, null);                           
                else
                    serviceMonitorO = coa.listNamespacedCustomObject(SERVICE_MONITOR_GROUP, V1_VERSION, namespace, SERVICE_MONITOR_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, serviceMonitorO), SERVICE_MONITOR_KIND, SERVICE_MONITOR_PROPERTY_NAME, getAPIVersion(SERVICE_MONITOR_GROUP, V1_VERSION)); 
            }  

            if (kind.equalsIgnoreCase(SUBSCRIPTION_KIND)) {            
                final Object subscriptionO;
                if (namespace == null || namespace.length() == 0)
                    subscriptionO = coa.listClusterCustomObject(SUBSCRIPTION_GROUP, V1ALPHA1_VERSION, SUBSCRIPTION_PLURAL, null, null, null, null);                           
                else
                    subscriptionO = coa.listNamespacedCustomObject(SUBSCRIPTION_GROUP, V1ALPHA1_VERSION, namespace, SUBSCRIPTION_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, subscriptionO), SUBSCRIPTION_KIND, SUBSCRIPTION_PROPERTY_NAME, getAPIVersion(SUBSCRIPTION_GROUP, V1ALPHA1_VERSION)); 
            }
            
            if (kind.equalsIgnoreCase(STATEFUL_SET_KIND)) {            
                final Object statefulSetO;
                if (namespace == null || namespace.length() == 0)
                    statefulSetO = coa.listClusterCustomObject(DEPLOYMENT_GROUP, V1_VERSION, STATEFUL_SET_PLURAL, null, null, null, null);                           
                else
                    statefulSetO = coa.listNamespacedCustomObject(DEPLOYMENT_GROUP, V1_VERSION, namespace, STATEFUL_SET_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, statefulSetO), STATEFUL_SET_KIND, STATEFUL_SET_PROPERTY_NAME, getAPIVersion(DEPLOYMENT_GROUP, V1_VERSION)); 
            }
           
            if (kind.equalsIgnoreCase(STORAGE_CLASS_KIND)) {            
                final Object storageClassO = coa.listClusterCustomObject(STORAGE_CLASS_GROUP, V1_VERSION, STORAGE_CLASS_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, storageClassO), STORAGE_CLASS_KIND, STORAGE_CLASS_PROPERTY_NAME, getAPIVersion(STORAGE_CLASS_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(TUNED_KIND)) {            
                final Object tunedO;
                if (namespace == null || namespace.length() == 0)
                    tunedO = coa.listClusterCustomObject(TUNED_GROUP, V1_VERSION, TUNED_PLURAL, null, null, null, null);                           
                else
                    tunedO = coa.listNamespacedCustomObject(TUNED_GROUP, V1_VERSION, namespace, TUNED_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, tunedO), TUNED_KIND, TUNED_PROPERTY_NAME, getAPIVersion(TUNED_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(VOLUME_KIND)) {                          
                final Object volumeO = coa.listClusterCustomObject(VOLUME_GROUP, VOLUME_VERSION, VOLUME_PLURAL, null, null, null, null);                                                                  
                response = processResources(client, getItemsAsList(client, volumeO), VOLUME_KIND, VOLUME_PROPERTY_NAME, getAPIVersion(VOLUME_GROUP, VOLUME_VERSION)); 
            } 

            if (kind.equalsIgnoreCase(VALIDATING_WEBHOOK_CONFIGURATION_KIND)) {                          
                final Object vwcO = coa.listClusterCustomObject(VALIDATING_WEBHOOK_CONFIGURATION_GROUP, V1_VERSION, VALIDATING_WEBHOOK_CONFIGURATION_PLURAL, null, null, null, null);                                                                  
                response = processResources(client, getItemsAsList(client, vwcO), VALIDATING_WEBHOOK_CONFIGURATION_KIND, VALIDATING_WEBHOOK_CONFIGURATION_PROPERTY_NAME, getAPIVersion(VALIDATING_WEBHOOK_CONFIGURATION_GROUP, V1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(VOLUME_ATTACHMENT_KIND)) {                          
                final Object volumeAttachmentO = coa.listClusterCustomObject(VOLUME_ATTACHMENT_GROUP, V1_VERSION, VOLUME_ATTACHMENT_PLURAL, null, null, null, null);                                                                  
                response = processResources(client, getItemsAsList(client, volumeAttachmentO), VOLUME_ATTACHMENT_KIND, VOLUME_ATTACHMENT_PROPERTY_NAME, getAPIVersion(VOLUME_ATTACHMENT_GROUP, V1_VERSION)); 
            }
            
            if (kind.equalsIgnoreCase(VOLUME_SNAPSHOT_KIND)) {            
                final Object volumeSnapshotO;
                if (namespace == null || namespace.length() == 0)
                    volumeSnapshotO = coa.listClusterCustomObject(VOLUME_SNAPSHOT_GROUP, V1ALPHA1_VERSION, VOLUME_SNAPSHOT_PLURAL, null, null, null, null);                           
                else
                    volumeSnapshotO = coa.listNamespacedCustomObject(VOLUME_SNAPSHOT_GROUP, V1ALPHA1_VERSION, namespace, VOLUME_SNAPSHOT_PLURAL, null, null, null, null);                           
                response = processResources(client, getItemsAsList(client, volumeSnapshotO), VOLUME_SNAPSHOT_KIND, VOLUME_SNAPSHOT_PROPERTY_NAME, getAPIVersion(VOLUME_SNAPSHOT_GROUP, V1ALPHA1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(VOLUME_SNAPSHOT_CLASS_KIND)) {                          
                final Object volumeSnapshotClassO = coa.listClusterCustomObject(VOLUME_SNAPSHOT_GROUP, V1ALPHA1_VERSION, VOLUME_SNAPSHOT_CLASS_PLURAL, null, null, null, null);                                                                  
                response = processResources(client, getItemsAsList(client, volumeSnapshotClassO), VOLUME_SNAPSHOT_CLASS_KIND, VOLUME_SNAPSHOT_CLASS_PROPERTY_NAME, getAPIVersion(VOLUME_SNAPSHOT_GROUP, V1ALPHA1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(VOLUME_SNAPSHOT_CONTENT_KIND)) {                          
                final Object volumeSnapshotContentO = coa.listClusterCustomObject(VOLUME_SNAPSHOT_GROUP, V1ALPHA1_VERSION, VOLUME_SNAPSHOT_CONTENT_PLURAL, null, null, null, null);                                                                  
                response = processResources(client, getItemsAsList(client, volumeSnapshotContentO), VOLUME_SNAPSHOT_CONTENT_KIND, VOLUME_SNAPSHOT_CONTENT_PROPERTY_NAME, getAPIVersion(VOLUME_SNAPSHOT_GROUP, V1ALPHA1_VERSION)); 
            }

            if (kind.equalsIgnoreCase(WAS_ND_CELL_KIND)) {
                final Object wasNDCellO;
                if (namespace == null || namespace.length() == 0)
                    wasNDCellO = coa.listClusterCustomObject(KAPPNAV_GROUP, APP_VERSION, WAS_ND_CELL_PLURAL, null, null, null, null);                         
                else
                    wasNDCellO = coa.listNamespacedCustomObject(KAPPNAV_GROUP, APP_VERSION, namespace, WAS_ND_CELL_PLURAL, null, null, null, null);                         
                response = processResources(client, getItemsAsList(client, wasNDCellO), WAS_ND_CELL_KIND, WAS_ND_CELL_PROPERTY_NAME, getAPIVersion(KAPPNAV_GROUP, APP_VERSION));               
            }

            if (kind.equalsIgnoreCase(WAS_TRADITIONAL_APP_KIND)) {
                final Object twasAppO;
                if (namespace == null || namespace.length() == 0)
                    twasAppO = coa.listClusterCustomObject(KAPPNAV_GROUP, APP_VERSION, WAS_TRADITIONAL_APP_PLURAL, null, null, null, null);                         
                else
                    twasAppO = coa.listNamespacedCustomObject(KAPPNAV_GROUP, APP_VERSION, namespace, WAS_TRADITIONAL_APP_PLURAL, null, null, null, null);                         
                response = processResources(client, getItemsAsList(client, twasAppO), WAS_TRADITIONAL_APP_KIND, WAS_TRADITIONAL_APP_PROPERTY_NAME, getAPIVersion(KAPPNAV_GROUP, APP_VERSION));               
            }
           
            return response;

        } catch (IOException | ApiException e) {
            if (Logger.isErrorEnabled()) {
                Logger.log(className, "getResources", Logger.LogType.ERROR, "Caught Exception returning status: " + getResponseCode(e) + " " + e.toString());
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(e)).build();
        }   
    }
   
    private Response processResources(final ApiClient client, final List<JsonObject> objects, final String kind, final String propName, final String apiVersion) {
        final ResourceResponse response = new ResourceResponse(propName);
        final ConfigMapProcessor processor;
        final SectionConfigMapProcessor sectionProcessor; 

        if (kind.equalsIgnoreCase(APPLICATION_KIND) || kind.equalsIgnoreCase(WAS_ND_CELL_KIND) ||
            kind.equalsIgnoreCase(LIBERTY_COLLECTIVE_KIND)) {     
            processor = new ConfigMapProcessor(kind.toLowerCase());
            sectionProcessor = new SectionConfigMapProcessor(kind.toLowerCase());
        } else {
            processor = new ConfigMapProcessor(kind);
            sectionProcessor = new SectionConfigMapProcessor(kind);
        }

        objects.forEach(v -> {
            if (kind.equalsIgnoreCase(APPLICATION_KIND)) {    
               if (! isApplicationHidden(v)) 
                    response.add(kind, propName, v, processor.getConfigMap(client, v, ConfigMapProcessor.ConfigMapType.ACTION), sectionProcessor.processSectionMap(client, v));                      
            } else {
                // Add 'kind' property to resources that are missing it.
                if (v.get(KIND_PROPERTY_NAME) == null) {
                    v.addProperty(KIND_PROPERTY_NAME, kind);
                }

                // Add 'apiVersion' property to components that are missing it.
                if (v.get(APIVERSION_PROPERTY_NAME) == null) {
                    v.addProperty(APIVERSION_PROPERTY_NAME, apiVersion);
                }

                response.add(kind, propName, v, processor.getConfigMap(client, v, ConfigMapProcessor.ConfigMapType.ACTION), sectionProcessor.processSectionMap(client, v)); 
            }
        });
        return Response.ok(response.getJSON()).build();
    }
    
    static final class ResourceResponse {
        private final JsonObject o;
        private final JsonArray resources;
        // Constructs:
        // {
        //   <resource-kind>s: [ { <resource-kind>: {...}, action-map: {...} }, ... ]
        // } 
        public ResourceResponse(final String propName) {
            o = new JsonObject();
            o.add(propName, resources = new JsonArray());           
        }     

        public void add(final String kind, final String propName, final JsonObject resource, final JsonObject actionMap, final JsonObject sectionMap) {
            final JsonObject tuple = new JsonObject();
            tuple.add(kind, resource != null ? resource : new JsonObject());
            if (actionMap.entrySet().size() > 0)
                tuple.add(ACTION_MAP_PROPERTY_NAME, actionMap);
            if (sectionMap.entrySet().size() > 0)
                tuple.add(SECTION_MAP_PROPERTY_NAME, sectionMap);
            resources.add(tuple);
        }
        public String getJSON() {
            return o.toString();
        }
    }  
    
    private String getAPIVersion(final String group, final String version) {
        String apiVersion = group + "/" + version;        
        return apiVersion;
    }
        
}
