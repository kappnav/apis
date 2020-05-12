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

import application.rest.v1.ConfigMapEndpoint;

import static org.junit.Assert.*;
import java.lang.String;

import javax.ws.rs.core.Response;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1DeleteOptions;

/**
 * @author jasuryak
 *
 */
public class ConfigMapEndpointTest {
    @SuppressWarnings("deprecation")
    private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    private final CoreV1Api cv1a = mock.mock(CoreV1Api.class);
    private final ApiClient ac = mock.mock(ApiClient.class);
    
    private final JSON json = new JSON();
    
    private final Gson gson = new Gson();

    private final ConfigMapEndpoint cmep = new ConfigMapEndpoint();
    private Response response = null;
    
    String test1 = "{" + 
            "    \"apiVersion\": \"v1\"," + 
            "    \"data\": {" + 
            "                \"snippets\": \"{\\n    \\\"get_route_host\\\": \\\"function getRouteHost(route) { \\n" +
            "                  var routeJSON = JSON.parse(route);\\n" +
            "                  var host = routeJSON.spec.host;\\n" +
            "                  return host;\\n    }\\\"\\n}\\n\",\n" + 
            "                \"url-actions\": \"[\\n  { \\n    \\\"name\\\":\\\"loyalty-home\\\", \\n" +
            "                  \\\"text\\\":\\\"View Home Page\\\", \\n" +
            "                  \\\"text.nls\\\": \\\"View Home Page\\\",\\n" +
            "                  \\\"description\\\":\\\"View loyalty-level home page.\\\", \\n" +
            "                  \\\"description.nls\\\": \\\"View loyalty-level home page.\\\",\\n" +
            "                  \\\"url-pattern\\\":\\\"http://${snippet.get_route_host(${func.kubectlGet(Route,${resource.$.metadata.name},-n,${resource.$.metadata.namespace},-o,json)})}\\\",\\n" +
            "                  \\\"open-window\\\": \\\"tab\\\", \\n" +
            "                  \\\"menu-item\\\": \\\"true\\\"\\n  }\\n]\\n\"\n" + 
            "    }," + 
            "    \"kind\": \"ConfigMap\"," + 
            "    \"metadata\": {" + 
            "        \"name\": \"kappnav-test-cell1\"," + 
            "        \"namespace\": \"stock-trader\"," + 
            "        \"resourceVersion\": \"1221195\"," + 
            "        \"selfLink\": \"/api/v1/namespaces/stock-trader/configmap/kappnav-test-cell1\"," + 
            "        \"uid\": \"7a08855f-71c7-4bf6-8cee-57c1b72175fb\"" + 
            "    }," + 
            "    \"type\": \"Opaque\"" + 
            "}" + 
            "";
    
    
    // update the version and the name
    String test2 = "{" + 
            "    \"apiVersion\": \"v1beta\"," + 
            "    \"data\": {" + 
            "                \"snippets\": \"{\\n    \\\"get_route_host\\\": \\\"function getRouteHost(route) { \\n" +
            "                  var routeJSON = JSON.parse(route);\\n" +
            "                  var host = routeJSON.spec.host;\\n" +
            "                  return host;\\n    }\\\"\\n}\\n\",\n" + 
            "                \"url-actions\": \"[\\n  { \\n    \\\"name\\\":\\\"loyalty-home\\\", \\n" +
            "                  \\\"text\\\":\\\"View Home Page\\\", \\n" +
            "                  \\\"text.nls\\\": \\\"View Home Page\\\",\\n" +
            "                  \\\"description\\\":\\\"View loyalty-level home page.\\\", \\n" +
            "                  \\\"description.nls\\\": \\\"View loyalty-level home page.\\\",\\n" +
            "                  \\\"url-pattern\\\":\\\"http://${snippet.get_route_host(${func.kubectlGet(Route,${resource.$.metadata.name},-n,${resource.$.metadata.namespace},-o,json)})}\\\",\\n" +
            "                  \\\"open-window\\\": \\\"tab\\\", \\n" +
            "                  \\\"menu-item\\\": \\\"true\\\"\\n  }\\n]\\n\"\n" + 
            "    }," + 
            "    \"kind\": \"ConfigMap\"," + 
            "    \"metadata\": {" + 
            "        \"name\": \"kappnav-test-cell1-New\"," + 
            "        \"namespace\": \"stock-trader\"," + 
            "        \"resourceVersion\": \"1221195\"," + 
            "        \"selfLink\": \"/api/v1/namespaces/stock-trader/configmap/kappnav-test-cell1\"," + 
            "        \"uid\": \"7a08855f-71c7-4bf6-8cee-57c1b72175fb\"" + 
            "    }," + 
            "    \"type\": \"Opaque\"" + 
            "}" + 
            "";
    
    String kappnavConfig = "{" + 
            "    \"apiVersion\": \"v1\"," + 
            "    \"data\": {" + 
            "        \"app-status-precedence\": \"[ \\\"Failed\\\", \\\"Problem\\\", \\\"Warning\\\", \\\"Pending\\\", \\\"In Progress\\\", \\\"Unknown\\\", \\\"Normal\\\", \\\"Completed\\\" ]\"," + 
            "        \"kappnav-sa-name\": \"kappnav-sa\",\n" + 
            "        \"status-color-mapping\": \"{ \\\"values\\\": { \\\"Normal\\\": \\\"GREEN\\\", \\\"Completed\\\": \\\"GREEN\\\", \\\"Pending\\\": \\\"YELLOW\\\", \\\"Warning\\\": \\\"YELLOW\\\", \\\"Problem\\\": \\\"RED\\\", \\\"Failed\\\": \\\"RED\\\", \\\"Unknown\\\": \\\"GREY\\\", \\\"In Progress\\\": \\\"BLUE\\\"},\\\"colors\\\": { \\\"GREEN\\\": \\\"#5aa700\\\", \\\"BLUE\\\": \\\"#4589ff\\\", \\\"YELLOW\\\": \\\"#B4B017\\\", \\\"RED\\\": \\\"#A74343\\\", \\\"GREY\\\": \\\"#808080\\\"} }\"," + 
            "        \"status-unknown\": \"Unknown\"" + 
            "    }," + 
            "    \"kind\": \"ConfigMap\"," + 
            "    \"metadata\": {" + 
            "        \"labels\": {\n" + 
            "            \"app.kubernetes.io/component\": \"kappnav-config\"," + 
            "            \"app.kubernetes.io/instance\": \"kappnav\"," + 
            "            \"app.kubernetes.io/managed-by\": \"kappnav-operator\"," + 
            "            \"app.kubernetes.io/name\": \"kappnav\"," + 
            "            \"kappnav.io/map-type\": \"builtin\"" + 
            "        }," + 
            "        \"name\": \"kappnav-config\"," + 
            "        \"namespace\": \"kappnav\"," + 
            "        \"ownerReferences\": [" + 
            "            {" + 
            "                \"apiVersion\": \"kappnav.operator.kappnav.io/v1\"," + 
            "                \"blockOwnerDeletion\": true," + 
            "                \"controller\": true," + 
            "                \"kind\": \"Kappnav\"," + 
            "                \"name\": \"kappnav\"" + 
            "            }" + 
            "        ]" + 
            "    }" + 
            "}";
    
    JsonObject jsonObject = new JsonParser().parse(test1).getAsJsonObject();

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        cmep.setCoreV1ApiForInternal(cv1a);
        cmep.setApiClientForInternal(ac);
        json.setGson(gson);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void createConfigMap_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(ac).getJSON();
                will(returnValue(json));
                oneOf(cv1a).createNamespacedConfigMap(with(any(String.class)), with(any(V1ConfigMap.class)), with(aNull(String.class)));
            } 
        });

        try {
            response = cmep.createConfigMap(test1, "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test createConfigMap_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test createConfigMap_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void createConfigMapThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(ac).getJSON();
                will(returnValue(json));
                oneOf(cv1a).createNamespacedConfigMap(with(any(String.class)), with(any(V1ConfigMap.class)), with(aNull(String.class)));
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            } 
        });

        try {
            response = cmep.createConfigMap(test1, "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test createConfigMapThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test createConfigMapThrowApiException_error failed with exception " + e.getMessage());
        }
    }

    @Test
    public void getConfigMap_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).readNamespacedConfigMap("kappnav-test-cell1",
                        "stock-trader", null, null, null);
            }
        });

        try {
            response = cmep.getConfigMap("kappnav-test-cell1", "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test getConfigMap_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test getConfigMap_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void getConfigMapKAppNavConfig_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).readNamespacedConfigMap("kappnav-config",
                        "kappnav", null, null, null);
            }
        });

        try {
            response = cmep.getConfigMap("kappnav-config", "kappnav");
            int rc = response.getStatus();
            assertEquals("Test getConfigMap_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test getConfigMap_succeeds failed with exception " + e.getMessage());
        }
    }
    
    //@Test
    public void getConfigMapThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).readNamespacedConfigMap("kappnav-test-cell1",
                        "stock-trader", null, null, null);
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }
        });

        try {
            response = cmep.getConfigMap("kappnav-test-cell1", "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test getConfigMapThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test getConfigMapThrowApiException_error failed with exception " + e.getMessage());
        }
    }

    @Test
    public void replaceConfigMap_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).replaceNamespacedConfigMap(with(any(String.class)), with(any(String.class)),
                        with(any(V1ConfigMap.class)), with(aNull(String.class)));
            }
        });

        try {
            response = cmep.replaceConfigMap(test2, "kappnav-test-cell1",
                    "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test replaceConfigMap_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test replaceConfigMap_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void replaceConfigMapThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).replaceNamespacedConfigMap(with(any(String.class)), with(any(String.class)),
                        with(any(V1ConfigMap.class)), with(aNull(String.class)));
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }
        });

        try {
            response = cmep.replaceConfigMap(test2, "kappnav-test-cell1",
                    "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test replaceConfigMapThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test replaceConfigMapThrowApiException_error failed with exception " + e.getMessage());
        }
    }

    @Test
    public void deleteConfigMap_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).deleteNamespacedConfigMap(with(any(String.class)), with(any(String.class)),
                        (V1DeleteOptions) with(any(Object.class)), with(aNull(String.class)), with(any(Integer.class)),
                        with(any(Boolean.class)), with(any(String.class)));
            }
        });

        try {
            response = cmep.deleteConfigMap("kappnav-test-cell1", "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test deleteConfigMap_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test deleteConfigMap_succeeds failed with exception " + e.getMessage());
        }
    }

    @Test
    public void deleteConfigMapThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).deleteNamespacedConfigMap(with(any(String.class)), with(any(String.class)),
                        (V1DeleteOptions) with(any(Object.class)), with(aNull(String.class)), with(any(Integer.class)),
                        with(any(Boolean.class)), with(any(String.class)));
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }
        });

        try {
            response = cmep.deleteConfigMap("kappnav-test-cell1", "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test deleteConfigMapThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test deleteConfigMapThrowApiException_error failed with exception " + e.getMessage());
        }
    }
}
