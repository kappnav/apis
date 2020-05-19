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

import application.rest.v1.StatusProcessor;

import static org.junit.Assert.*;

import javax.ws.rs.core.Response;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.ApisApi;
import io.kubernetes.client.apis.CoreV1Api;

/**
 * @author jasuryak
 *
 */
public class StatusProcessorTest {
	@SuppressWarnings("deprecation")	
	private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    
	private final CoreV1Api cv1a = mock.mock(CoreV1Api.class);   
	private final ApiClient ac = mock.mock(ApiClient.class);
	private final ApisApi apis = mock.mock(ApisApi.class);
	
	private StatusProcessor sp = null;
	private KAppNavConfig kanc = null;
	private ComponentInfoRegistry cir = null;
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	    kanc = new KAppNavConfig();
	    sp = new StatusProcessor(kanc);
	    ComponentInfoRegistry.setApisApiForJunit(apis);
	}

	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

    String test1 = "{" + 
            "    \"apiVersion\": \"v1\"," + 
            "    \"items\": [" + 
            "        {" + 
            "            \"apiVersion\": \"app.k8s.io/v1beta1\"," + 
            "            \"kind\": \"Application\"," + 
            "            \"metadata\": {" + 
            "                \"annotations\": {" + 
            "                    \"kappnav.component.namespaces\": \"twas,liberty,localliberty,appmetrics-dash\"" + 
            "                }," + 
            "                \"labels\": {" + 
            "                    \"app.kubernetes.io/name\": \"stock-trader-app\"" + 
            "                }," + 
            "                \"name\": \"stock-trader\"," + 
            "                \"namespace\": \"stock-trader\"" + 
            "            }," + 
            "            \"spec\": {" + 
            "                \"componentKinds\": [" + 
            "                    {" + 
            "                        \"group\": \"core\"," + 
            "                        \"kind\": \"Deployment\"" + 
            "                    }," + 
            "                    {" + 
            "                        \"group\": \"core\"," + 
            "                        \"kind\": \"Service\"" + 
            "                    }," + 
            "                    {" + 
            "                        \"group\": \"kappnav.io\"," + 
            "                        \"kind\": \"WAS-Traditional-App\"" + 
            "                    }," + 
            "                    {" + 
            "                        \"group\": \"kappnav.io\"," + 
            "                        \"kind\": \"Liberty-App\"" + 
            "                    }," + 
            "                    {" + 
            "                        \"group\": \"apps.openshift.io\"," + 
            "                        \"kind\": \"DeploymentConfig\"" + 
            "                    }" + 
            "                ]," + 
            "                \"selector\": {" + 
            "                    \"matchLabels\": {" + 
            "                        \"solution\": \"stock-trader\"" + 
            "                    }" + 
            "                }" + 
            "            }" + 
            "        }" + 
            "    ]" + 
            "}";
    
    String test2 = "{" + 
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
    
    JsonObject jsonObject1 = new JsonParser().parse(test1).getAsJsonObject();
    JsonObject jsonObject2 = new JsonParser().parse(test2).getAsJsonObject();
    
	@Test
	public void getComponentStatus_succeeds() throws Exception {
		mock.checking(new Expectations() {
			{
			    allowing(apis).setApiClient(with(any(ApiClient.class)));
			    allowing(apis).getAPIVersions();
			}		
		});
		try {
		    JsonObject expectedResult = new JsonParser().parse("{value:Unknown,flyover:Unknown}").getAsJsonObject();
		    cir = new ComponentInfoRegistry();
		    JsonObject result = sp.getComponentStatus(ac, cir, jsonObject1, jsonObject2);
		    assertEquals("Test getComponentStatus_succeeds FAILED", expectedResult, result);
		} catch (Exception e) {
			fail("Test getComponentStatus_succeeds failed with exception " + e.getMessage());
		}
	}
	
}
