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
   
	private final ApiClient ac = mock.mock(ApiClient.class);
	private final CoreV1Api cv1a = mock.mock(CoreV1Api.class);
	private final JSON json = new JSON();
	private final Gson gson = new Gson();
	 
	private final ConfigMapEndpoint cmep = new ConfigMapEndpoint();
	private Response response = null;
	
	String testConfigMap = "{\n" + 
    		"    \"apiVersion\": \"v1\",\n" + 
    		"    \"items\": [\n" + 
    		"        {\n" + 
    		"            \"apiVersion\": \"v1\",\n" + 
    		"            \"data\": {\n" + 
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
    		"            },\n" + 
    		"            \"kind\": \"ConfigMap\",\n" + 
    		"            \"metadata\": {\n" + 
    		"                \"creationTimestamp\": \"2020-04-12T02:24:37Z\",\n" + 
    		"                \"labels\": {\n" + 
    		"                    \"kappnav.io/map-type\": \"action\"\n" + 
    		"                },\n" + 
    		"                \"name\": \"stock-trader.actions.deployment-liberty.loyalty-level\",\n" + 
    		"                \"namespace\": \"stock-trader\",\n" + 
    		"                \"resourceVersion\": \"247487\",\n" + 
    		"                \"selfLink\": \"/api/v1/namespaces/stock-trader/configmaps/stock-trader.actions.deployment-liberty.loyalty-level\",\n" + 
    		"                \"uid\": \"b5398c7b-a2ff-4f21-bc39-36fd52785ee1\"\n" + 
    		"            }\n" + 
    		"        }\n" + 
    		"    ],\n" + 
    		"    \"kind\": \"List\",\n" + 
    		"    \"metadata\": {\n" + 
    		"        \"resourceVersion\": \"\",\n" + 
    		"        \"selfLink\": \"\"\n" + 
    		"    }\n" + 
    		"}\n" + 
    		"";
    
    String testConfigMapNew = "{\n" + 
    		"    \"apiVersion\": \"v1\",\n" + 
    		"    \"items\": [\n" + 
    		"        {\n" + 
    		"            \"apiVersion\": \"v1\",\n" + 
    		"            \"data\": {\n" + 
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
    		"            },\n" + 
    		"            \"kind\": \"ConfigMap\",\n" + 
    		"            \"metadata\": {\n" + 
    		"                \"creationTimestamp\": \"2020-04-12T02:24:37Z\",\n" + 
    		"                \"labels\": {\n" + 
    		"                    \"kappnav.io/map-type\": \"action\"\n" + 
    		"                },\n" + 
    		"                \"name\": \"stock-trader.actions.deployment-liberty.loyalty-level\",\n" + 
    		"                \"namespace\": \"stocktrader\",\n" + 
    		"                \"resourceVersion\": \"247487\",\n" + 
    		"                \"selfLink\": \"/api/v1/namespaces/stocktrader/configmaps/stock-trader.actions.deployment-liberty.loyalty-level\",\n" + 
    		"                \"uid\": \"b5398c7b-a2ff-4f21-bc39-36fd52785ee1\"\n" + 
    		"            }\n" + 
    		"        }\n" + 
    		"    ],\n" + 
    		"    \"kind\": \"List\",\n" + 
    		"    \"metadata\": {\n" + 
    		"        \"resourceVersion\": \"\",\n" + 
    		"        \"selfLink\": \"\"\n" + 
    		"    }\n" + 
    		"}\n" + 
    		"";
    
    JsonObject jsonObject = new JsonParser().parse(testConfigMap).getAsJsonObject();
    
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		cmep.setCoreV1ApiForInternal(cv1a);
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
				allowing(ac).getJSON();
				will(returnValue(json));
				oneOf(cv1a).createNamespacedConfigMap(with(any(String.class)), with(any(V1ConfigMap.class)), with(aNull(String.class)));
			} 
		});
			
		try {
			response = cmep.createConfigMap(testConfigMap, "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test createConfigMap_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test createConfigMap_succeeds failed with exception " + e.getMessage());
		}
	}
		
		
	@Test
	public void getConfigMap_succeeds() throws Exception {
		mock.checking(new Expectations() {
            {
            	oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
            	oneOf(cv1a).readNamespacedConfigMap("stock-trader.actions.deployment-liberty.loyalty-level", "stock-trader", null, null, null);
            	allowing(ac).getJSON();
            	will(returnValue(json));
            
            }
        });
		
		try {
			response = cmep.getConfigMap("stock-trader.actions.deployment-liberty.loyalty-level", "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test getConfigMap_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test getConfigMap_succeeds failed with exception " + e.getMessage());
		}
	}	
	
	
	@Test
	public void replaceConfigMap_succeeds() throws Exception {
		mock.checking(new Expectations() {
            {
            	oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
            	allowing(ac).getJSON();
				will(returnValue(json));
				oneOf(cv1a).replaceNamespacedConfigMap(with(any(String.class)), with(any(String.class)), with(any(V1ConfigMap.class)), with(aNull(String.class)));
            }
        });
		
		try {
			response = cmep.replaceConfigMap(testConfigMapNew, "stock-trader.actions.deployment-liberty.loyalty-level", "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test replaceConfigMap_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test replaceConfigMap_succeeds failed with exception " + e.getMessage());
		}
	}
	
	
	@Test
	public void deleteConfigMap_succeeds() throws Exception {
		mock.checking(new Expectations() {
            {
            	oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
            	oneOf(cv1a).deleteNamespacedConfigMap(with(any(String.class)), with(any(String.class)), (V1DeleteOptions) with(any(Object.class)), with(aNull(String.class)), with(any(Integer.class)), with(any(Boolean.class)), with(any(String.class)));
            }
        });
		
		try {
			response = cmep.deleteConfigMap("stock-trader.actions.deployment-liberty.loyalty-level", "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test deleteConfigMap_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test deleteConfigMap_succeeds failed with exception " + e.getMessage());
		}
	}	
	
}
