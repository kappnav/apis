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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import application.rest.v1.KAppNavEndpoint;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;

import com.ibm.kappnav.logging.Logger;

/**
 * KindActionMappings provide mapping rules that map a resource to a set of action configmaps.
 * This class provides the methods that facilitates a process of getting a configmap for a given
 * resource following the mapping rules and configmap hierarchy & precedence. 
 */
public class KindActionMappingProcessor {

    private static final String className = KindActionMappingProcessor.class.getName();

    // KindActionMapping/KAM definitions
    private static final String KAM_PLURAL = "kindactionmappings";
    private static final String KAM_GROUP = "actions.kappnav.io";
    private static final String KAM_VERSION = "v1beta1";
    private static final String WILDCARD = "*";

    private static final int MAX_PRECEDENCE = 9;
    private static final int TOTAL_INDIVIDUAL_MAPPINGS = 4;

    private static final int KSN = 3; // Kind, Subkind, name - instance specific
    private static final int KN = 2; // Kind, name - instance specific
    private static final int KS = 1; // Kind, Subkind - subkind specific
    private static final int K = 0; // Kind - kind specific

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
        this.compApiVersion = apiVersion;
        this.compName = name;
        this.compSubkind = subkind;
        this.compKind = kind;
    }

    /**
     * Get configmaps defined in the mappings in the KindActionMapping custom resources
     * for a given resource. One or more action configmaps may exist to which the same 
     * resource maps. 
     * 
     * @param client
     * @return configmaps matched the defined action configmap mappings in KAM in order of
     *         configmap hierarchy & precedence
     */
    protected ArrayList<String> getConfigMapsFromKAMs(ApiClient client) {

        if (Logger.isEntryEnabled()) 
                Logger.log(className, "getConfigMapsFromKAMs", Logger.LogType.ENTRY,"");

        String[][] mapNamesFound = new String[MAX_PRECEDENCE][TOTAL_INDIVIDUAL_MAPPINGS];
        ArrayList<String> configMapsList = null;
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
                                Logger.log(className, "getConfigMapsFromKAMs", Logger.LogType.DEBUG,
                                           "\nKindActionMapping found: " + item);

                            if (item != null) {
                                JsonElement element = item.getAsJsonObject().get(SPEC_PROPERTY_NAME);
                                String kamNamespace = getKAMNamespace(item);
                                if (element != null) {
                                    JsonObject spec = element.getAsJsonObject();
                                    int precedenceIndex = spec.get(PRECEDENCE_PROPERTY_NAME).getAsInt()-1;
                                    JsonArray mappings = spec.getAsJsonArray(MAPPINGS_PROPERTY_NAME);

                                    // iterate through each mapping within a KAM resource
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
                                                Logger.log(className, "getConfigMapsFromKAMs", Logger.LogType.DEBUG,
                                                           "\nmapping info: " + 
                                                           "\napiVersion = " + apiVersion +
                                                           "\nname = " + name +
                                                           "\nsubkind = " + subkind +
                                                           "\nkind = " + kind + 
                                                           "\nmapname = " + mapname);

                                                if (isApiVersionMatch(apiVersion, compApiVersion)) {
                                                    int compPropsIdx = exemineMappingProperties(compName, compSubkind, compKind);
                                                    int kamMappingPropIdx = exemineMappingProperties(name, subkind, kind);
                                                    boolean found = foundMatchedConfigMap(compPropsIdx, kamMappingPropIdx, 
                                                                                          compName, compSubkind, compKind,
                                                                                          name, subkind, kind);

                                                    if (found){
                                                        if (Logger.isDebugEnabled()) 
                                                            Logger.log(className, "getQualifiedKindActionMappings", Logger.LogType.DEBUG, 
                                                             "mapName " + mapname + " is stored in configMapsFound["+precedenceIndex+"][" +
                                                             kamMappingPropIdx+"]");
                                                        // each mapname found is stored in the 2D array as "mapname@namespace"
                                                        if ((kamMappingPropIdx == KSN) || (kamMappingPropIdx == KN))
                                                            mapNamesFound[precedenceIndex][kamMappingPropIdx] = mapname+"@"+compNamespace;
                                                        else
                                                            mapNamesFound[precedenceIndex][kamMappingPropIdx] = mapname+"@"+kamNamespace;
                                                    } else {
                                                        if (Logger.isDebugEnabled()) 
                                                            Logger.log(className, "getQualifiedKindActionMappings", Logger.LogType.DEBUG, 
                                                             "no match!!!");
                                                    }                                                
                                                } else {
                                                    if (apiVersion == null) {
                                                        if (Logger.isErrorEnabled()) 
                                                            Logger.log(className, "getQualifiedKindActionMappings", Logger.LogType.ERROR, 
                                                             "apiVersion of the KindActionMapping resource " + name + " is null.");
                                                    }
                                                }
                                            }
                                        }
                                    });  // mappings.forEach
                                }
                            }
                        }
                    }); // itemsArray.forEach
                }
            });  // kamList.forEach
             
            // process candidate mapnames including a string substitution as needed and then store 
            // them to a list according the configmap hierarchy and (high to low) precedence 
            configMapsList = processCandidateMapnames(mapNamesFound, compNamespace,
                                                      compName, compSubkind, compKind);
        } catch  (ApiException e) {
            if (Logger.isErrorEnabled()) {
                Logger.log(className, "getQualifiedKindActionMappings", Logger.LogType.ERROR, 
                 "Caught PatternException returning status: " + e.toString());}
        }

        if (Logger.isExitEnabled()) 
                Logger.log(className, "getConfigMapsFromKAMs", Logger.LogType.EXIT,"");
        return configMapsList;
    }

    /**
     * Get all "KindActionMapping" custom resources in a cluster
     * 
     * @param client
     * @return
     * @throws ApiException
     */
    protected List<JsonObject> listKAMCustomResources(ApiClient client) 
        throws ApiException {
        final CustomObjectsApi coa = new CustomObjectsApi();
        coa.setApiClient(client);
        if (Logger.isDebugEnabled()) {
            Logger.log(className, "listKAMCustomResources", Logger.LogType.DEBUG, 
                "\n List KAM Custom Resources for all namespaces with" +
                "\n group = " + "actions.kappnav.io" + 
                "\n namespace = kappnav and name = default");
        }

        Object kamResource = coa.listClusterCustomObject(KAM_GROUP, KAM_VERSION, KAM_PLURAL, null, 
                             null, null, null);
        return KAppNavEndpoint.getItemAsList(client, kamResource);
    }

    /**
     * Get the namespace for the given KindActionMapping resource
     * 
     * @param kam
     * @return the namespace for the given kam resource
     */
    private String getKAMNamespace(JsonElement kam) {
        if (Logger.isEntryEnabled())
            Logger.log(className, "getKAMNamespace", Logger.LogType.ENTRY,"");

        String namespaceStr = null;
        JsonElement metadata = (JsonObject) kam.getAsJsonObject().get(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            JsonObject namespace= metadata.getAsJsonObject();
            if (namespace != null) 
                namespaceStr = namespace.get(NAMESPACE_PROPERTY_NAME).getAsString();
        } else {
            if (Logger.isDebugEnabled()) 
                Logger.log(className, "getKAMNamespace", Logger.LogType.DEBUG, "kam metadata is null.");
        }

        if (Logger.isExitEnabled()) {
            Logger.log(className, "getKAMNamespace", Logger.LogType.EXIT, "kam namespace = " + namespaceStr);
        } 
        return namespaceStr;
    }

    /**
     * Check to see if the apiVersions of a component and a mapping are matching
     * 
     * @param apiVersion
     * @param compApiVersion
     * @return
     */
    private boolean isApiVersionMatch(String apiVersion, String compApiVersion) {
        if (Logger.isEntryEnabled()) 
            Logger.log(className, "isApiVersionMatch", Logger.LogType.ENTRY, "apiVerion = " + apiVersion +
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
                    if (grp_version[0].equals(comp_grp_version[0]) || (grp_version[0].equals(WILDCARD)) )
                        if (grp_version[1].equals(comp_grp_version[1]) || (grp_version[1].equals(WILDCARD)) ) {
                            match = true;
                        }
                } else if ( (grp_version.length == 1) && (comp_grp_version.length == 1) ) { // verion only
                    if (grp_version[0].equals(comp_grp_version[0]) || (grp_version[0].equals(WILDCARD)) ) {
                        match = true;
                    }
                }
            }
        }

        if (Logger.isExitEnabled()) 
            Logger.log(className, "isApiVersionMatch", Logger.LogType.EXIT, "match = " + match);
        return match;
    }

    /**
     * Examine the mapping properites passed in
     * 
     * @param name
     * @param subkind
     * @param kind
     * @return KSN, KS, KN, or K 
     */
    private int exemineMappingProperties(String name, String subkind, String kind) {
        if (Logger.isEntryEnabled()) 
            Logger.log(className, "exemineMappingProperties", Logger.LogType.ENTRY, 
                   "(name, subkind, kind) = (" + name +", " + subkind + ", " + kind + ")");

        int retVal = -1;
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
                Logger.log(className, "exemineMappingProperties", Logger.LogType.ERROR, 
                           "kind given is null or empty string");
        }

        if (Logger.isExitEnabled()) 
                Logger.log(className, "exemineMappingProperties", Logger.LogType.EXIT, 
                           "retVal = " + retVal);

        return retVal;
    }

    /**
     * Find a matched configmap defined in a given KAM 
     * 
     * @param cNumFields
     * @param kNumFields
     * @param cName
     * @param cSubkind
     * @param cKind
     * @param gName
     * @param gSubkind
     * @param gKind
     * @return true if found; otherwise false
     */
    private boolean foundMatchedConfigMap(int cNumFields, int kNumFields,
                              String cName, String cSubkind, String cKind,
                              String gName, String gSubkind, String gKind) {

        if (Logger.isEntryEnabled()) 
                Logger.log(className, "foundMatchedConfigMap", Logger.LogType.ENTRY, 
                           "cNumField = " + cNumFields + " kNumField = " + kNumFields);

        if ( (cNumFields == -1) || (kNumFields == -1)) {
            if (Logger.isExitEnabled()) 
                Logger.log(className, "isMatched", Logger.LogType.EXIT, "return false");
            return false;
        }

        boolean match = false;
        if (cNumFields == kNumFields) {
            switch (kNumFields) {
            case KSN:
                if ((gName.equals(cName)) || (gName.equals(WILDCARD)))
                    if ((gSubkind.equals(cSubkind)) || (gSubkind.equals(WILDCARD)))
                        if ((gKind.equals(cKind)) || (gKind.equals(WILDCARD)))
                            match = true;
                break;
            case KS:
                if ((gSubkind.equals(cSubkind)) || (gSubkind.equals(WILDCARD)))
                    if ((gKind.equals(cKind)) || (gKind.equals(WILDCARD)))
                        match = true;
                break;
            case KN:
                if ((gName.equals(cName)) || (gName.equals(WILDCARD)))
                    if ((gKind.equals(cKind)) || (gKind.equals(WILDCARD)))
                        match = true;
                break;
            case K:
                if (cNumFields == K)
                    if ((gKind.equals(cKind)) || (gKind.equals(WILDCARD)))
                        match = true;
            }
        } else if (kNumFields == K) {
            if ((gKind.equals(cKind)) || (gKind.equals(WILDCARD)))
                match = true;
        } else if ((kNumFields == KS) && (cNumFields == KSN)) {
            if ( (gSubkind.equals(cSubkind)) || (gSubkind.equals(WILDCARD)) ) 
                        if ( (gKind.equals(cKind)) || (gKind.equals(WILDCARD)) ) 
                            match = true;
        }
        
        if (Logger.isExitEnabled()) 
                Logger.log(className, "foundMatchedConfigMap", Logger.LogType.EXIT, "return = " + match);
        return match;
    }

    /**
     * Process mapnames with variable substitution if applies and store the mapnames along with their
     * namespaces in a list according to the configmap hierarchy and precedence in decedending order
     * 
     * @param configMapsFound
     * @param namespace
     * @param name
     * @param subkind
     * @param kind
     * @return processed configmap list
     */
    private ArrayList<String> processCandidateMapnames(String[][] configMapsFound, String namespace, 
                                                       String name, String subkind, String kind) {
        ArrayList<String> configMapList = new ArrayList<String> ();

        for (int ksnIdx=KSN; ksnIdx>=0; ksnIdx--) {
            for (int precedenceIdx=MAX_PRECEDENCE-1; precedenceIdx>=0; precedenceIdx--) {
                String rawMapName = (configMapsFound[precedenceIdx][ksnIdx]);               
                if (rawMapName != null) {
                    String[] mapname_namespace = rawMapName.split("@");
                    rawMapName = mapname_namespace[0]; // take off @namespace
                    if (Logger.isDebugEnabled()) 
                        Logger.log(className, "processCandidateMapnames", Logger.LogType.DEBUG, 
                                   "rawMapName = " + rawMapName);
                    String actualMapName = mapNameSubstitute(rawMapName, namespace, name, subkind, kind);
                    if (Logger.isDebugEnabled()) 
                        Logger.log(className, "processCandidateMapnames", Logger.LogType.DEBUG, 
                                   "actualMapName = " + actualMapName);
                    configMapList.add(actualMapName+"@"+mapname_namespace[1]);
                }
            }
        }
        return configMapList;
    }

    /**
     * Substitute the string variables like namespace, name, subkind, kind with actual values passed in
     * 
     * @param rawMapName
     * @param namespace
     * @param name
     * @param subkind
     * @param kind
     * @return substituted mapName
     */
    private String mapNameSubstitute(String rawMapName, String namespace, String name, String subkind, String kind) {
        String actualMapName = new String("");
        String[] parts = rawMapName.split("\\.");
        for (int i=0; i<parts.length; i++) {
            if ( parts[i].equals("${namespace}") ) {
                actualMapName = actualMapName + namespace;
            } else if ( parts[i].equals("${name}") ) {
                actualMapName = actualMapName + name;
            } else if ( parts[i].equals("${kind}-${subkind}") ) {
                actualMapName = actualMapName + kind.toLowerCase() + "-" + subkind.toLowerCase();
            } else if ( parts[i].equals("${kind}") ) {
                actualMapName = actualMapName + kind.toLowerCase();
            } else {
                actualMapName = actualMapName + parts[i];
            }

            if (i < parts.length-1)
                actualMapName = actualMapName + ".";
        }
        return actualMapName ;
    }

}