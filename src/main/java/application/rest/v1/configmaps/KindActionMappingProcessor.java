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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.namespace.QName;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import application.rest.v1.KAppNavEndpoint;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;

import com.ibm.kappnav.logging.Logger;

/**
 * KindActionMapping provides mapping rules that map a resource to a set of action configmaps.
 * This class provides the methods that facilitate a process of getting a configmap for a given
 * resource following the mapping rules and configmap hierarchy & precedence. 
 * 
 * Desing document link: https://github.com/kappnav/design/blob/master/kind-action-mapping.md
 */
public class KindActionMappingProcessor {

    private static final String className = KindActionMappingProcessor.class.getName();

    // KindActionMapping/KAM definitions
    private static final String KAM_PLURAL = "kindactionmappings";
    private static final String KAM_GROUP = "actions.kappnav.io";
    private static final String KAM_VERSION = "v1";
    private static final String WILDCARD = "*";

    private static final int MAX_PRECEDENCE = 9;
    private static final int TOTAL_KSN_VALUES = 4;
    private static final int KAM_N = 10;

    private static final int KSN = 3; // Kind, Subkind, name - instance specific
    private static final int KN = 2; // Kind, name - instance specific
    private static final int KS = 1; // Kind, Subkind - subkind specific
    private static final int K = 0; // Kind - kind specific
    private static final int NO_KSN = -1;  // Null or Empty KSN 

    private static final String METADATA_PROPERTY_NAME = "metadata";
    private static final String NAMESPACE_PROPERTY_NAME = "namespace";
    private static final String ITEMS_PROPERTY_NAME = "items";
    private static final String SPEC_PROPERTY_NAME = "spec";
    private static final String PRECEDENCE_PROPERTY_NAME = "precedence";
    private static final String MAPPINGS_PROPERTY_NAME = "mappings";

    private static final String APIVERSION_PROPERTY_NAME = "apiVersion";
    private static final String NAME_PROPERTY_NAME = "name";
    private static final String SUBKIND_PROPERTY_NAME = "subkind";
    private static final String KIND_PROPERTY_NAME = "kind";
    private static final String MAPNAME_PROPERTY_NAME = "mapname";

    private String compNamespace;
    private String compApiVersion;
    private String compName;
    private String compSubkind;
    private String compKind;

    public KindActionMappingProcessor(String namespace, String apiVersion, String name,
                                      String subkind, String kind) {
        this.compNamespace = namespace;
        this.compApiVersion = normalizeApiVersion(apiVersion);
        this.compName = name;
        this.compSubkind = subkind;
        this.compKind = kind;
    }

    /**
     * This method is to get configmaps defined in the mappings and precedences of the 
     * KindActionMapping custom resources for a given resource.
     * 
     * The KindActionMapping CRD defines which config maps contain the action definitions for 
     * which resource kinds. The mappings are based on the following resource fields:
     * 
     * - apiVersion is the group/version identifier of the resource. Note Kubernetes resources 
     *   with no group value (e.g. Service) specify apiVersion as version only. E.g. apiVersion: v1.
     * - kind is the resource's kind field
     * - subkind is the resource's metadata.annotations.kappnav.subkind annotation.
     * - name is the resource's metadata.name field
     * 
     * KindActionMappings provide mapping rules that map a resource to a set of action config maps. 
     * These action config maps are then combined to form the set of actions applicable to the resource. 
     * 
     * One or more action configmaps may exist to which the same resource maps. Multiple mapping rules 
     * may exist to which a resource maps; mapping rules are searched for in this order, using the match 
     * values from the from resource, searching for a matching rule from the most specific to least specific:
     * 
     * For a resource kind qualified by the subkind annotation:
     * - kind-subkind.name - instance specific
     * - kind-subkind - subkind specific
     * - kind - kind specific
     * 
     * For a resource without subkind qualification:
     * - kind.name - instance specific
     * - kind - kind specific
     * 
     * Multiple KindActionMapping resources may specify mappings rules for the same resource kind. 
     * When this happens, additional action configmap mappings are inserted into the configmap 
     * hierarchy, based on the KindActionMapping instance's precedence value.
     * 
     * @param client ApiClient
     * @return configmaps matched the defined action configmap mappings in KAM in order of
     *         configmap hierarchy & precedence
     */
    protected ArrayList<QName> getConfigMapsFromKAMs(ApiClient client) {
        String methodName = "getConfigMapsFromKAMs";
        if (Logger.isEntryEnabled()) 
                Logger.log(className, methodName, Logger.LogType.ENTRY,"");

        QName[][][] mapNamesFound = new QName[MAX_PRECEDENCE][KAM_N][TOTAL_KSN_VALUES];
        ArrayList<QName> configMapsList = null;
        List <JsonObject> kamList = null;

        try {
            kamList = listKAMCustomResources(client);
            kamList.forEach (v -> {
                JsonElement items = v.get(ITEMS_PROPERTY_NAME);
                if ((items != null) && (items.isJsonArray())) {
                    JsonArray itemsArray = items.getAsJsonArray();

                    // go though all kams to get the qualified configmaps defined in those kams   
                    // Sort the configmaps found in order of hierarchy & precedence
                    itemsArray.forEach(item-> {
                        if ( (item != null) && (item.isJsonObject()) ) {
                            if (Logger.isDebugEnabled()) 
                                Logger.log(className, methodName, Logger.LogType.DEBUG,
                                           "\nKindActionMapping found: " + item);

                            JsonElement element = item.getAsJsonObject().get(SPEC_PROPERTY_NAME);
                            String kamNamespace = getKAMNamespace(item);
                            if (element != null) {
                                JsonObject spec = element.getAsJsonObject();                           
                                JsonElement precedence = spec.get(PRECEDENCE_PROPERTY_NAME);
                                int precedenceIndex;
                                if (precedence != null)
                                    precedenceIndex = spec.get(PRECEDENCE_PROPERTY_NAME).getAsInt()-1;
                                else 
                                    precedenceIndex = 0; // No precedence specified: set default precedenceIndex as 0 
                                                         // (The default for precedence is 1)
                                JsonArray mappings = spec.getAsJsonArray(MAPPINGS_PROPERTY_NAME);

                                // iterate through each mapping within a KAM custom resource
                                mappings.forEach(mapItem-> {
                                    if (mapItem != null) {
                                        JsonObject props = mapItem.getAsJsonObject();
                                        if (props != null) {
                                            JsonElement prop = props.get(APIVERSION_PROPERTY_NAME);
                                            String apiVersion = (prop != null) ? prop.getAsString():null;
                                            prop = props.get(NAME_PROPERTY_NAME);
                                            String name = (prop != null) ? prop.getAsString():null;
                                            prop = props.get(SUBKIND_PROPERTY_NAME);
                                            String subkind = (prop != null) ? prop.getAsString():null;
                                            prop = props.get(KIND_PROPERTY_NAME);
                                            String kind = (prop != null) ? prop.getAsString():null;
                                            prop = props.get(MAPNAME_PROPERTY_NAME);
                                            String mapname = (prop != null) ? prop.getAsString():null;
                                            Logger.log(className, methodName, Logger.LogType.DEBUG,
                                                       "\nmapping info: " + 
                                                       "\napiVersion = " + apiVersion +
                                                       "\nname = " + name +
                                                       "\nsubkind = " + subkind +
                                                       "\nkind = " + kind + 
                                                       "\nmapname = " + mapname);

                                            String normalizedApiVersion = normalizeApiVersion(apiVersion);
                                            if (isApiVersionMatch(normalizedApiVersion, compApiVersion)) {
                                                int compPropsIdx = examineMappingProperties(compName, compSubkind, compKind);
                                                int kamMappingPropIdx = examineMappingProperties(name, subkind, kind);

                                                // if the resource given matches the kind action mapping rules?
                                                boolean matches = isResourceMatchesRule(compPropsIdx, kamMappingPropIdx, 
                                                                                        name, subkind, kind);

                                                if (matches){                                               
                                                    // find next available slot for a kam in same precedence
                                                    // currently, we allow 10 kams with the same precedence with this impl.
                                                    // The kam found after all slots are used are being ignored with a warning.
                                                    int kamNIndex=-1;
                                                    for (int i=0; i<KAM_N; i++) {
                                                        if (mapNamesFound[precedenceIndex][i][kamMappingPropIdx] == null) { 
                                                            kamNIndex = i;
                                                            break;
                                                        }   
                                                    }

                                                    // each mapname found is stored in the 3D array as "mapname@namespace"
                                                    // D1: precedence;
                                                    // D2: kams-in-same-precedence;
                                                    // D3: possible combination of kam mapping properties (kind, subkind, and name)
                                                    if (kamNIndex != -1) {
                                                        if ((kamMappingPropIdx == KSN) || (kamMappingPropIdx == KN))
                                                            mapNamesFound[precedenceIndex][kamNIndex][kamMappingPropIdx] = 
                                                                new QName(compNamespace, mapname);
                                                        else
                                                            mapNamesFound[precedenceIndex][kamNIndex][kamMappingPropIdx] = 
                                                                new QName(kamNamespace, mapname);
                                                        if (Logger.isDebugEnabled()) 
                                                            Logger.log(className, methodName, Logger.LogType.DEBUG, 
                                                               "mapName " + mapname + " is stored in configMapsFound["+kamMappingPropIdx+"]["+
                                                               precedenceIndex+"][" + kamNIndex+"]");
                                                    } else {
                                                        if (Logger.isWarningEnabled()) 
                                                            Logger.log(className, methodName, Logger.LogType.WARNING, 
                                                                       "The number of kams with the precedence " + precedenceIndex+1 + 
                                                                       " passes the limit of " + KAM_N);
                                                    }
                                                } else {
                                                    if (Logger.isDebugEnabled()) 
                                                        Logger.log(className, methodName, Logger.LogType.DEBUG, 
                                                        "no match!!!");
                                                }                                                
                                             } else {
                                                if (apiVersion == null) {
                                                    if (Logger.isErrorEnabled()) 
                                                        Logger.log(className, methodName, Logger.LogType.ERROR, 
                                                                   "apiVersion of the KindActionMapping resource " + name + " is null.");
                                                }
                                             }
                                        }
                                    }
                                });  // mappings.forEach
                            }   
                        }
                    }); // itemsArray.forEach
                }
            });  // kamList.forEach
             
            // process candidate mapnames including a string substitution as needed and then store 
            // them to a list according the configmap hierarchy and (high to low) precedence
            // 
            // The symbols ${namespace}, ${kind}, ${subkind}, and ${name} can specified in the mapname 
            // value to be substituted at time of use with the matching resource's namespace, kind, 
            // subkind, or name value, respectively. 
            configMapsList = processCandidateMapnames(mapNamesFound, compNamespace);
        } catch  (ApiException e) {
            if (Logger.isErrorEnabled()) {
                Logger.log(className, methodName, Logger.LogType.ERROR, 
                 "Caught ApiException: " + e.toString());}
        }

        if (Logger.isExitEnabled()) 
                Logger.log(className, methodName, Logger.LogType.EXIT,"");
        return configMapsList;
    }

    /**
     * Get all "KindActionMapping" custom resources in a cluster
     * 
     * @param client apiVersion
     * @return a list of KAM CR instances in a cluster
     * @throws ApiException
     */
    protected List<JsonObject> listKAMCustomResources(ApiClient client) 
        throws ApiException {
        String methodName = "listKAMCustomResources";
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        if (Logger.isDebugEnabled()) {
            Logger.log(className, methodName, Logger.LogType.DEBUG, 
                "\n List KAM Custom Resources for all namespaces with" +
                "\n group = " + "actions.kappnav.io" + 
                "\n namespace = kappnav and name = default");
        }

        Object kamResource = coa.listClusterCustomObject(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, 
                             null, null, null);
        return KAppNavEndpoint.getItemAsList(client, kamResource);
    }

    /**
     * Normalize the apiVersion given with removing leading "/"
     * 
     * @param apiVersion
     * @return apiVersion normalized
     */
    private String normalizeApiVersion(String apiVersion) {
        String methodName = "normalizeApiVersion";
        if (apiVersion.startsWith("/") && (apiVersion.length() > 1) ) {
            apiVersion = apiVersion.substring(1);
            if (Logger.isDebugEnabled())
                Logger.log(className, methodName, Logger.LogType.DEBUG, "apiVersion normalized = " + apiVersion);
        }
        return apiVersion;
    }

    /**
     * Get the namespace for the given KindActionMapping resource
     * 
     * @param kam
     * @return the namespace for the given kam resource
     */
    private String getKAMNamespace(JsonElement kam) {
        String methodName = "getKAMNamespace";
        if (Logger.isEntryEnabled())
            Logger.log(className, methodName, Logger.LogType.ENTRY,"");

        String namespaceStr = null;
        JsonElement metadata = (JsonObject) kam.getAsJsonObject().get(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonObject namespace= metadata.getAsJsonObject();
            if (namespace != null) 
                namespaceStr = namespace.get(NAMESPACE_PROPERTY_NAME).getAsString();
        } else {
            if (Logger.isDebugEnabled()) 
                Logger.log(className, methodName, Logger.LogType.DEBUG, "kam metadata is null.");
        }

        if (Logger.isExitEnabled()) {
            Logger.log(className, methodName, Logger.LogType.EXIT, "kam namespace = " + namespaceStr);
        } 
        return namespaceStr;
    }

    private static final int GROUP = 0; 
    private static final int VERSION = 1;
    private static final int GROUPLESS_VERSION = 0;
    /**
     * Check to see if the apiVersions of a component and a mapping are matching
     * 
     * @param apiVersion
     * @param compApiVersion
     * @return true when the apiVersion is matched; false otherwise
     */
    private boolean isApiVersionMatch(String apiVersion, String compApiVersion) {
        String methodName = "isApiVersionMatch";
        if (Logger.isEntryEnabled()) 
            Logger.log(className, methodName, Logger.LogType.ENTRY, "apiVerion = " + apiVersion +
                       ", compApiVersion = " + compApiVersion);

        boolean match = false;
        if ( (apiVersion != null) && (!apiVersion.isEmpty()) &&
             (compApiVersion != null) && (!compApiVersion.isEmpty()) ) {   
            if (apiVersion.equals(compApiVersion)) { // group/version - exact match
                match = true;
            } else {
                String grp_version[] = apiVersion.split("/");
                String comp_grp_version[] = compApiVersion.split("/");

                if ( (grp_version.length == 2) && (comp_grp_version.length == 2) ){ // group/version - wildcard match
                    if (grp_version[GROUP].equals(comp_grp_version[GROUP]) || (grp_version[GROUP].equals(WILDCARD)) )
                        if (grp_version[VERSION].equals(comp_grp_version[VERSION]) || (grp_version[VERSION].equals(WILDCARD)) ) {
                            match = true;
                        }
                } else if ( (grp_version.length == 1) && (comp_grp_version.length == 1) ) { // verion only
                    if (grp_version[GROUPLESS_VERSION].equals(comp_grp_version[GROUPLESS_VERSION]) || 
                       (grp_version[GROUPLESS_VERSION].equals(WILDCARD)) ) {
                        match = true;
                    }
                } else {
                    if (Logger.isDebugEnabled()) 
                        Logger.log(className, methodName, Logger.LogType.DEBUG, "No match: group_version = " + grp_version
                                   + ", component group_version = " + comp_grp_version);
                }
            }
        }

        if (Logger.isExitEnabled()) 
            Logger.log(className, methodName, Logger.LogType.EXIT, "match = " + match);
        return match;
    }

    /**
     * Examine the mapping properites passed in and return one of the four combinations of
     * the mapping properties as follows:
     * 
     * - KSN (Kind-Subkind-Name), 
     * - KS (Kind-Subkind),
     * - KN (Kind-Name), 
     * - K (Kind)
     * 
     * @param name
     * @param subkind
     * @param kind
     * @return KSN, KS, KN, or K 
     */
    private int examineMappingProperties(String name, String subkind, String kind) {
        String methodName = "examineMappingProperties";
        if (Logger.isEntryEnabled()) 
            Logger.log(className, methodName, Logger.LogType.ENTRY, 
                   "(name, subkind, kind) = (" + name +", " + subkind + ", " + kind + ")");

        int retVal = NO_KSN;
        if ((kind != null) && !kind.isEmpty()) {
            if ((subkind != null) && !subkind.isEmpty()) {
                if ((name != null) && !name.isEmpty()) {
                    retVal = KSN;
                } else {
                    retVal = KS;
                }
            } else if ((name != null) && !name.isEmpty()) {
                retVal = KN;
            } else {
                retVal = K;
            }
        } else {
            if (Logger.isErrorEnabled()) 
                Logger.log(className, methodName, Logger.LogType.ERROR, 
                           "kind given is null or empty string");
        }

        if (Logger.isExitEnabled()) 
                Logger.log(className, methodName, Logger.LogType.EXIT, 
                           "retVal = " + retVal);

        return retVal;
    }

    /**
     * Test to see if the given component resource KSN value matches exactly or matches via wildcard
     * to the kam mapping KSN value
     * 
     * @param compKSNValue  can be KSN, KS, KN, K
     * @param kamKSNValue can be KSN, KS, KN, K
     * @param kamName kam name property
     * @param kamSubkind kam subkind property
     * @param kamKind kam kind property
     * @return true if kam KSN value matches component KSN value; otherwise false
     */
    private boolean isResourceMatchesRule(int compKSNValue, int kamKSNValue,
                                          String kamName, String kamSubkind, String kamKind) {

        String methodName = "isResourceMatchesRule";
        if (Logger.isEntryEnabled()) 
                Logger.log(className, methodName, Logger.LogType.ENTRY, 
                           "cNumField = " + compKSNValue + " kNumField = " + kamKSNValue);

        if ( (compKSNValue == NO_KSN) || (kamKSNValue == NO_KSN)) {
            if (Logger.isErrorEnabled()) 
                Logger.log(className, methodName, Logger.LogType.ERROR, "return false");
            return false;
        }

        boolean match = false;
        if (compKSNValue == kamKSNValue) {
            switch (kamKSNValue) {
            case KSN:
                if ((kamName.equals(this.compName)) || (kamName.equals(WILDCARD)))
                    if ((kamSubkind.equals(this.compSubkind)) || (kamSubkind.equals(WILDCARD)))
                        if ((kamKind.equals(this.compKind)) || (kamKind.equals(WILDCARD)))
                            match = true;
                break;
            case KS:
                if ((kamSubkind.equals(this.compSubkind)) || (kamSubkind.equals(WILDCARD)))
                    if ((kamKind.equals(this.compKind)) || (kamKind.equals(WILDCARD)))
                        match = true;
                break;
            case KN:
                if ((kamName.equals(this.compName)) || (kamName.equals(WILDCARD)))
                    if ((kamKind.equals(this.compKind)) || (kamKind.equals(WILDCARD)))
                        match = true;
                break;
            case K:
                if (compKSNValue == K)
                    if ((kamKind.equals(this.compKind)) || (kamKind.equals(WILDCARD)))
                        match = true;
            }
        } else if ((compKSNValue == KSN) && (kamKSNValue == KS)) {
            if ( (kamSubkind.equals(this.compSubkind)) || (kamSubkind.equals(WILDCARD)) ) 
                        if ( (kamKind.equals(this.compKind)) || (kamKind.equals(WILDCARD)) ) 
                            match = true;
        } else if (kamKSNValue == K) {
            if ((kamKind.equals(this.compKind)) || (kamKind.equals(WILDCARD)))
                match = true;
        }
        
        if (Logger.isExitEnabled()) 
                Logger.log(className, methodName, Logger.LogType.EXIT, "return = " + match);
        return match;
    }

    /**
     * Process mapnames with variable substitution if applies and store the mapnames along with their
     * namespaces in a list according to the configmap hierarchy and precedence in descending order 
     * (the kams has the same precedence in ascending order)
     * 
     * @param configMapsFound configmaps found
     * @param namespace resource's namespace
     * @return processed configmap list
     */
    private ArrayList<QName> processCandidateMapnames(QName[][][] configMapsFound, String namespace) {
        String methodName = "processCandidateMapnames";
        ArrayList<QName> configMapList = new ArrayList<QName> ();

        for (int ksnIdx=KSN; ksnIdx>=0; ksnIdx--) {
            for (int precedenceIdx=MAX_PRECEDENCE-1; precedenceIdx>=0; precedenceIdx--) {
                for (int kamNIdx=0; kamNIdx<KAM_N; kamNIdx++) { 
                    QName aQName = configMapsFound[precedenceIdx][kamNIdx][ksnIdx];        
                    if (aQName != null) {
                        String rawMapName = aQName.getLocalPart();
                        if (Logger.isDebugEnabled()) 
                            Logger.log(className, methodName, Logger.LogType.DEBUG, 
                                "rawMapName = " + rawMapName);
                        String actualMapName = mapNameSubstitute(rawMapName, namespace);
                        if (Logger.isDebugEnabled()) 
                            Logger.log(className, methodName, Logger.LogType.DEBUG, 
                                "actualMapName = " + actualMapName);
                        configMapList.add(new QName(aQName.getNamespaceURI(), actualMapName));
                    }
                }
            }
        }
        return configMapList;
    }

    /**
     * Substitute the string variables like namespace, name, subkind, kind with actual values passed in
     * For example,  mapname: ${namespace}.actions.${kind}.${name} 
     * 
     * @param rawMapName mapName without the string substitute
     * @param namespace namespace of a configmap being looked up
     * @return substituted mapName
     */
    private String mapNameSubstitute(String rawMapName, String namespace) {
        String actualMapName = new String("");
        String[] parts = rawMapName.split("\\.");
        for (int i=0; i<parts.length; i++) {
            if ( parts[i].equals("${namespace}") ) {
                actualMapName = actualMapName + namespace;
            } else if ( parts[i].equals("${name}") ) {
                actualMapName = actualMapName + this.compName;
            } else if ( parts[i].equals("${kind}-${subkind}") ) {
                actualMapName = actualMapName + this.compKind.toLowerCase(Locale.ENGLISH) + "-" + 
                                this.compSubkind.toLowerCase(Locale.ENGLISH);
            } else if ( parts[i].equals("${kind}") ) {
                actualMapName = actualMapName + this.compKind.toLowerCase(Locale.ENGLISH);
            } else {
                actualMapName = actualMapName + parts[i];
            }

            if (i < parts.length-1)
                actualMapName = actualMapName + ".";
        }
        return actualMapName ;
    }

}