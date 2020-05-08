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

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

import com.ibm.kappnav.logging.Logger;

/**
 * KindActionMapping provides mapping rules that map a resource to a set of action configmaps.
 * This class provides the methods that facilitate a process of getting a configmap for a given
 * resource following the mapping rules and configmap hierarchy & precedence. 
 * 
 * Design document link: https://github.com/kappnav/design/blob/master/kind-action-mapping.md
 */
public class KindActionMappingProcessor {

    private static final String CLASS_NAME = KindActionMappingProcessor.class.getName();

    // KindActionMapping/KAM definitions

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
    private static final String OWNER_PROPERTY_NAME = "owner"; 
    private static final String UID_PROPERTY_NAME = "uid";
    private static final String NAME_PROPERTY_NAME = "name";
    private static final String SUBKIND_PROPERTY_NAME = "subkind";
    private static final String KIND_PROPERTY_NAME = "kind";
    private static final String MAPNAME_PROPERTY_NAME = "mapname";

    private String compNamespace;
    private String compApiVersion;
    private OwnerRef[] compOwners;
    private String compName;
    private String compSubkind;
    private String compKind;
    private OwnerRef compMatchingOwner;

    public KindActionMappingProcessor(String namespace, OwnerRef[] owners, String apiVersion, String name, 
                                      String subkind, String kind) {
        this.compNamespace = namespace;
        this.compApiVersion = normalizeApiVersion(apiVersion);
        this.compOwners= owners;
        this.compName = name;
        this.compSubkind = subkind;
        this.compKind = kind;
        this.compMatchingOwner = null;
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
                Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY,"");

        QName[][][] mapNamesFound = new QName[MAX_PRECEDENCE][KAM_N][TOTAL_KSN_VALUES];
        ArrayList<QName> configMapsList = null;
        List <JsonObject> kamList = null;

        try {
            kamList = KindActionMappingCache.listKAMCustomResources(client);

            if ( (kamList == null) || (kamList.isEmpty()) ){
                if (Logger.isExitEnabled()) 
                    Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "No KindActionMapping CR instance found.");
                return configMapsList;
            }

            kamList.forEach (v -> {
                JsonElement items = v.get(ITEMS_PROPERTY_NAME);
                if ((items != null) && (items.isJsonArray())) {
                    JsonArray itemsArray = items.getAsJsonArray();

                    // go though all kams to get the qualified configmaps defined in those kams   
                    // Sort the configmaps found in order of hierarchy & precedence
                    itemsArray.forEach (kam -> {
                        if ( (kam != null) && (kam.isJsonObject()) ) {
                            if (Logger.isDebugEnabled()) 
                                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG,
                                    "\nKindActionMapping found: " + kam);

                            JsonElement element = kam.getAsJsonObject().get(SPEC_PROPERTY_NAME);
                            String kamNamespace = getKAMNamespace(kam);
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
                                if (mappings != null) {
                                    mappings.forEach(mapItem-> {
                                        if (mapItem != null) {
                                            JsonObject props = mapItem.getAsJsonObject();
                                            if (props != null) {
                                                JsonElement prop = props.get(APIVERSION_PROPERTY_NAME);
                                                String apiVersion = (prop != null) ? prop.getAsString():null;

                                                String ownerKind= null;
                                                String ownerAPI= null;
                                                String ownerUID= null; 
                                                JsonObject owner= props.getAsJsonObject(OWNER_PROPERTY_NAME);
                                                if ( owner != null ) { 
                                                    prop= owner.get(APIVERSION_PROPERTY_NAME);
                                                    ownerAPI = (prop != null) ? prop.getAsString():null;
                                                    prop = owner.get(KIND_PROPERTY_NAME);
                                                    ownerKind = (prop != null) ? prop.getAsString():null;
                                                    prop = owner.get(UID_PROPERTY_NAME);
                                                    ownerUID = (prop != null) ? prop.getAsString():null;
                                                } 

                                                prop = props.get(NAME_PROPERTY_NAME);
                                                String name = (prop != null) ? prop.getAsString():null;
                                                prop = props.get(SUBKIND_PROPERTY_NAME);
                                                String subkind = (prop != null) ? prop.getAsString():null;
                                                prop = props.get(KIND_PROPERTY_NAME);
                                                String kind = (prop != null) ? prop.getAsString():null;
                                                prop = props.get(MAPNAME_PROPERTY_NAME);
                                                String mapname = (prop != null) ? prop.getAsString():null;
                                                if ( Logger.isDebugEnabled()) { 
                                                    Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG,
                                                        "\nmapping info: " + 
                                                        "\napiVersion = " + apiVersion +
                                                        "\nownerKind = " + ownerKind +
                                                        "\nownerAPI = " + ownerAPI +
                                                        "\nownerUID = " + ownerUID +
                                                        "\nname = " + name +
                                                        "\nsubkind = " + subkind +
                                                        "\nkind = " + kind + 
                                                        "\nmapname = " + mapname);
                                                }

                                                String normalizedApiVersion = normalizeApiVersion(apiVersion);
                                                if (isApiVersionMatch(normalizedApiVersion, compApiVersion) && 
                                                    ownerMatches(ownerKind, ownerAPI, ownerUID, compOwners)) {
                                                    int compPropsIdx = examineMappingProperties(compName, compSubkind, compKind);
                                                    int kamMappingPropIdx = examineMappingProperties(name, subkind, kind);

                                                    // if the resource given matches the kind action mapping rules?
                                                    boolean matches = isResourceMatchesRule(compPropsIdx, kamMappingPropIdx, 
                                                                                    name, subkind, kind);

                                                    if (matches) {                                               
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
                                                                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, 
                                                                    "mapName " + mapname + " is stored in configMapsFound["+kamMappingPropIdx+"]["
                                                                    + precedenceIndex+"][" + kamNIndex+"]");
                                                        } else {
                                                            if (Logger.isWarningEnabled()) 
                                                                Logger.log(CLASS_NAME, methodName, Logger.LogType.WARNING, 
                                                                    "The number of kams with the precedence " + precedenceIndex+1 + 
                                                                    " passes the limit of " + KAM_N);
                                                        }
                                                    } else {
                                                        if (Logger.isDebugEnabled()) 
                                                            Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, "no match!!!");
                                                    }                                                
                                                } else {
                                                    if (apiVersion == null) {
                                                        if (Logger.isErrorEnabled()) 
                                                            Logger.log(CLASS_NAME, methodName, Logger.LogType.ERROR, 
                                                                "apiVersion of the KindActionMapping resource " + name + " is null.");
                                                    }
                                                }
                                            }
                                        }
                                    });  // mappings.forEach
                                } else {
                                    String kamName = getKAMName(kam);
                                    if (Logger.isErrorEnabled()) {
                                        Logger.log(CLASS_NAME, methodName, Logger.LogType.ERROR, 
                                            "There is no mappings properties found for the KAM: " + kamName);
                                    }
                                }
                            }
                        }  
                    });  
                }
            });// kamList.forEach
             
            // process candidate mapnames including a string substitution as needed and then store 
            // them to a list according the configmap hierarchy and (high to low) precedence 
            configMapsList = processCandidateMapnames(mapNamesFound, compNamespace);
        } catch  (ApiException e) {
            if (Logger.isErrorEnabled()) 
                Logger.log(CLASS_NAME, methodName, Logger.LogType.ERROR, 
                 "Caught an apiException: " + e.toString());
        }

        if (Logger.isExitEnabled()) 
                Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT,"");
        return configMapsList;
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
                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, "apiVersion normalized = " + apiVersion);
        }
        return apiVersion;
    }

    /**
     * Get the namespace for the given KindActionMapping resource
     * 
     * @param kam
     * @return the namespace for the given kam resource
     */
    protected static String getKAMNamespace(JsonElement kam) {
        String methodName = "getKAMNamespace";
        if (Logger.isEntryEnabled())
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY,"");

        String namespaceStr = null;
        JsonElement metadata = (JsonObject) kam.getAsJsonObject().get(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonObject namespace= metadata.getAsJsonObject();
            if (namespace != null) 
                namespaceStr = namespace.get(NAMESPACE_PROPERTY_NAME).getAsString();
        } else {
            if (Logger.isDebugEnabled()) 
                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, "kam metadata is null.");
        }

        if (Logger.isExitEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "kam namespace = " + namespaceStr);
        } 
        return namespaceStr;
    }

    /**
     * Get the name for the given KindActionMapping resource
     * 
     * @param kam
     * @return the name for the given kam resource
     */
    protected static String getKAMName(JsonElement kam) {
        String methodName = "getKAMName";
        if (Logger.isEntryEnabled())
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY,"");

        String nameStr = null;
        JsonElement metadata = (JsonObject) kam.getAsJsonObject().get(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonObject name = metadata.getAsJsonObject();
            if (name != null) 
                nameStr = name.get(NAME_PROPERTY_NAME).getAsString();
        } else {
            if (Logger.isDebugEnabled()) 
                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, "kam metadata is null.");
        }

        if (Logger.isExitEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "kam name = " + nameStr);
        } 
        return nameStr;
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
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY, "apiVerion = " + apiVersion +
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
                } 
            }
        }

        if (Logger.isExitEnabled()) 
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "match = " + match);
        return match;
    }

    /**
     * Check if mapping rule has an owner (kind), make sure it matches an owner kind in 
     * resource's ownerReferences array. 
     * @param mappingRuleOwner
     * @param resourceOwnerReferences
     * @return true if mapping rule does not specify owner kind 
     *         true if mapping rule specifies owner kind wildcard ('*') 
     *         false if mapping rule specifies owner kind (not wildcard) and resource has no owner refs
     * 
     *         true if resource has owner ref kind that matches mapping rule owner kind and rule has no owner 
     *           apiVersion or uid
     * 
     *         true if resource has owner ref kind that matches mapping rule owner kind
     *           and mapping rule owner apiVersion and/or uid match resource owner apiVersion and/or uid
     * 
     *         false if resource has owner ref kind that matches mapping rule owner kind 
     *           and mapping rule apiVersion and/or uid do not match resource owner apiVersion and/or uid
     *         
     *         false if mapping rule specifies owner kind (not wildcard) but resource has no matching owner ref kind
     */
    private boolean ownerMatches(String mappingRuleOwnerKind, String mappingRuleOwnerAPI, String mappingRuleOwnerUID, OwnerRef[] resourceOwnerReferences) {

        if (Logger.isDebugEnabled()) 
           Logger.log(CLASS_NAME, "ownerMatches", Logger.LogType.DEBUG,"Parameters: "+
              "\nrule owner kind="+mappingRuleOwnerKind+
              "\nrule owner apiVersion="+mappingRuleOwnerAPI+
              "\nrule owner uid="+mappingRuleOwnerUID+
              "\nresource refs="+ownerRefsToString(resourceOwnerReferences)
            );
        // true if mapping rule does not specify owner kind
        if ( mappingRuleOwnerKind == null ) { 
            if (Logger.isDebugEnabled()) 
                Logger.log(CLASS_NAME, "ownerMatches", Logger.LogType.DEBUG, "true because mapping rule does not specify owner kind.");
            return true; 
        }
        // true if mapping rule specifies owner kind wildcard ('*')
        else if ( mappingRuleOwnerKind.equals("*") ) { 
            if (Logger.isDebugEnabled()) 
                Logger.log(CLASS_NAME, "ownerMatches", Logger.LogType.DEBUG, "true because mapping rule specifies owner kind wildcard ('*').");
            return true; 
        }
        // false if mapping rule specifies owner kind (not wildcard) and resource has no owner refs
        else if ( resourceOwnerReferences == null ) { 
            if (Logger.isDebugEnabled()) 
                Logger.log(CLASS_NAME, "ownerMatches", Logger.LogType.DEBUG, "false because mapping rule specifies owner kind (not wildcard) and resource has no owner refs.");
            return false; 
        }
        else { 
            for (int i=0; i < resourceOwnerReferences.length; i++ ) { 
                if ( resourceOwnerReferences[i].kindEquals(mappingRuleOwnerKind) ) { 
                    // true if resource has owner ref kind that matches mapping rule owner kind and rule has no owner apiVersion or uid
                    if (( mappingRuleOwnerUID == null ) && ( mappingRuleOwnerAPI == null ))  {  
                        if (Logger.isDebugEnabled()) 
                            Logger.log(CLASS_NAME, "ownerMatches", Logger.LogType.DEBUG, "true because resource has owner ref kind that matches mapping rule owner kind and rule has no owner apiVersion or uid.");
                        return true;
                    }
                    else {
                        // true if resource has owner ref kind that matches mapping rule owner kind
                        //   and mapping rule owner apiVersion and/or uid match resource owner apiVersion and/or uid
                        if (uidMatches(resourceOwnerReferences[i],mappingRuleOwnerUID) && 
                              apiMatches(resourceOwnerReferences[i],mappingRuleOwnerAPI)) {
                            this.compMatchingOwner = resourceOwnerReferences[i];
                            if (Logger.isDebugEnabled()) 
                                Logger.log(CLASS_NAME, "ownerMatches", Logger.LogType.DEBUG, "true because resource has owner ref kind that matches mapping rule owner kind and mapping rule owner apiVersion and/or uid match resource owner apiVersion and/or uid.");
                            return true;
                        // false if resource has owner ref kind that matches mapping rule owner kind 
                        //   and mapping rule apiVersion and/or uid do not match resource owner apiVersion and/or uid
                        } else {
                            if (Logger.isDebugEnabled()) 
                                Logger.log(CLASS_NAME, "ownerMatches", Logger.LogType.DEBUG,"false because resource has owner ref kind that matches mapping rule owner kind and mapping rule apiVersion and/or uid do not match resource owner apiVersion and/or uid."); 
                            return false;
                        }
                    }
                }
            }
            // false if mapping rule specifies owner kind (not wildcard) but resource has no matching owner ref kind
            if (Logger.isDebugEnabled()) 
                Logger.log(CLASS_NAME, "ownerMatches", Logger.LogType.DEBUG,"false because mapping rule specifies owner kind (not wildcard) but resource has no matching owner ref kind.");           
            return false; 
        }
    }

    // format ownerRef array into newline prefix string
    private String ownerRefsToString(OwnerRef[] refs) { 
        String result="";
        for (int i=0; i<refs.length; i++) { 
                result+= "\n"+refs[i].toString();
        }
        return result; 
    }

    /**
     *  format ownerRef array into newline prefix string
     */
    private String ownerRefsToString(OwnerRef[] refs) { 
        String result="";
        if (refs != null) {
            for (int i=0; i<refs.length; i++) { 
                result+= "\n"+refs[i].toString();
            }
        }
        return result; 
    }

    /**
     * Determine if specified mapping UID matches ownerRef UID
     * 
     * true is mapping rule does not specify UID - i.e. owner UID is not
     *      part of rule criteria
     * true if mappingRule specifies wildcard UID ("*") or UIDs actually match! 
     * false if UIDs do not match
     **/  
    private boolean uidMatches(OwnerRef owner, String mappingRuleOwnerUID) { 
        // 
        if ( mappingRuleOwnerUID == null ) { 
            // return true is mapping rule does not specify UID - i.e. owner UID is not 
            // part of rule criteria 
            return true; 
        }
        else {
            // return true if mappingRule specifies wildcard UID ("*") or UIDs actually match! 
            // return false if UIDs do not match
            return owner.uidMatches(mappingRuleOwnerUID); 
        }
    }

    /**
     * Determine if specified mapping API(Version) matches ownerRef APIVersion
     *  
     * true is mapping rule does not specify API(Version) - i.e. owner APIVersion is not
     *      part of rule criteria
     * true if mappingRule specifies wildcard API(Version) ("*") or APIVersions actually match! 
     * false if APIVersions do not match
     **/  
    private boolean apiMatches(OwnerRef owner, String mappingRuleOwnerAPI) { 
        // 
        if ( mappingRuleOwnerAPI == null ) { 
            // return true is mapping rule does not specify API(Version) - i.e. owner APIVersio is not 
            // part of rule criteria 
            return true; 
        }
        else {
            // return true if mappingRule specifies wildcard API(Version) ("*") or APIVersions actually match! 
            // return false if APIVersions do not match
            return owner.apiVersionMatches(mappingRuleOwnerAPI); 
        }
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
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY, 
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
                Logger.log(CLASS_NAME, methodName, Logger.LogType.ERROR, 
                           "kind given is null or empty string");
        }

        if (Logger.isExitEnabled()) 
                Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, 
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
                Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY, 
                           "cNumField = " + compKSNValue + " kNumField = " + kamKSNValue);

        if ( (compKSNValue == NO_KSN) || (kamKSNValue == NO_KSN)) {
            if (Logger.isErrorEnabled()) 
                Logger.log(CLASS_NAME, methodName, Logger.LogType.ERROR, "return false");
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
                Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "return = " + match);
        return match;
    }

    /**
     * Process mapnames with variable substitution if applies and store the mapnames along with their
     * namespaces in a list according to the configmap hierarchy and precedence in descending order 
     * (the kams has the same precedence in ascending order)
     * 
     * @param configMapsFound configmaps found
     * @param namespace matching resource's namespace
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
                            Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, 
                                "rawMapName = " + rawMapName);
                        String actualMapName = mapNameSubstitute(rawMapName, namespace);
                        if (Logger.isDebugEnabled()) 
                            Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, 
                                "actualMapName = " + actualMapName);
                        configMapList.add(new QName(aQName.getNamespaceURI(), actualMapName));
                    }
                }
            }
        }
        return configMapList;
    }

    /**
     * Substitute the string variables ${namespace}, ${kind}, %{subkind}, and ${name}
     * in a mapname with the namespace, kind, subkind, and name of a matching resouce
     * 
     * @param rawMapName mapName without the string substitute
     * @param namespace matching resource's namespace
     * @return substituted mapName
     */
    private String mapNameSubstitute(String rawMapName, String namespace) {
        String methodName = "mapNameSubstitute";
        if (Logger.isEntryEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.ENTRY, "rawMapName: " + rawMapName + ", namespace: " + namespace);
        }

        String actualMapName = new String("");
        boolean ownerDotSub = false;
        // TODO: regex to handle ${owner.xxx} variables
        // Temporarily substitute - for . in any ${owner.xxx} variables (for split with regex "\\.")
        if (rawMapName.indexOf("${owner.") > -1) {
            rawMapName = rawMapName.replace("${owner.kind}","${owner-kind}"). 
                                    replace("${owner.apiVersion}","${owner-apiVersion}").
                                    replace("${owner.uid}","${owner-uid}");
            ownerDotSub = true;
        }        
        String[] parts = rawMapName.split("\\.");
        for (int i=0; i<parts.length; i++) {
            if (Logger.isDebugEnabled()) {
                Logger.log(CLASS_NAME, methodName, Logger.LogType.DEBUG, "processing mapname segment: " + parts[i]);
            }

            if ( parts[i].equals("${namespace}") ) {
                actualMapName = actualMapName + namespace;
            } else if ( parts[i].equals("${name}") ) {
                actualMapName = actualMapName + this.compName;
            } else if ( parts[i].equals("${kind}-${subkind}") ) {
                actualMapName = actualMapName + this.compKind.toLowerCase(Locale.ENGLISH) + "-" + 
                                this.compSubkind.toLowerCase(Locale.ENGLISH);
            } else if ( parts[i].equals("${kind}") ) {
                actualMapName = actualMapName + this.compKind.toLowerCase(Locale.ENGLISH);
            } else if ( parts[i].equals("${owner-apiVersion}") && 
                        this.compMatchingOwner != null ) {
                actualMapName = actualMapName + this.compMatchingOwner.getApiVersion().replace('/','-');
            } else if ( parts[i].equals("${owner-kind}") && 
                        this.compMatchingOwner != null ) {
                actualMapName = actualMapName + this.compMatchingOwner.getKind().toLowerCase(Locale.ENGLISH);
            } else if ( parts[i].equals("${owner-uid}") && 
                        this.compMatchingOwner != null ) {
                actualMapName = actualMapName + this.compMatchingOwner.getUID();
            } else {
                actualMapName = actualMapName + parts[i];
            }

            if (i < parts.length-1)
                actualMapName = actualMapName + ".";
        }

        // If a sub for a ${owner.xxx} variable was not completed then undo .- substitution
        if (ownerDotSub && actualMapName.indexOf("${owner-") > -1) {
            actualMapName = actualMapName.replace("${owner-kind}","${owner.kind}"). 
                                          replace("${owner-apiVersion}","${owner.apiVersion}").
                                          replace("${owner-uid}","${owner.uid}");
        }
        
        if (Logger.isExitEnabled()) {
            Logger.log(CLASS_NAME, methodName, Logger.LogType.EXIT, "actualMapName = " + actualMapName);

        }
        return actualMapName ;
    }

}