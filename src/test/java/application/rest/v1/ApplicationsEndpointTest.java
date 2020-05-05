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

import application.rest.v1.ApplicationsEndpoint;
import application.rest.v1.configmaps.KindActionMappingProcessor;

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
import io.kubernetes.client.apis.CustomObjectsApi;

/**
 * @author jasuryak
 *
 */
public class ApplicationsEndpointTest {
	@SuppressWarnings("deprecation")
	private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    
	private final ApiClient ac = mock.mock(ApiClient.class);
	private final CustomObjectsApi coa = mock.mock(CustomObjectsApi.class);
	private final CoreV1Api cv1a = mock.mock(CoreV1Api.class);
	private final JSON json = mock.mock(JSON.class);
	
	private final ApplicationsEndpoint aep = new ApplicationsEndpoint();
	private final ApplicationCache appc = new ApplicationCache();
	private final KindActionMappingProcessor kamp = new KindActionMappingProcessor();
	private final Gson gson = new Gson();
	private Response response = null;
	
	String sampleApp = "{" + 
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
	
	JsonObject jsonObject = new JsonParser().parse(sampleApp).getAsJsonObject();

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		//ApplicationsEndpoint.setApiClientForJunit(ac);
		appc.setCustomObjectsApiForJunit(coa);
		kamp.setCustomObjectsApiForJunit(coa);
		KAppNavConfig.setCoreV1ApiForInternal(cv1a);
	}

	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void getApplications_with_namespace_succeeds() throws Exception {
		mock.checking(new Expectations() {
            {
            	oneOf(coa).setApiClient(with(any(ApiClient.class)));
            	oneOf(coa).listNamespacedCustomObject("app.k8s.io", "v1beta1", "stock-trader", "applications", null, null, null, null);
            	oneOf(ac).getJSON();
            	will(returnValue(json)); 
            	oneOf(json).getGson();
				will(returnValue(gson));
            }
        });
		
		try {
			response = aep.getApplications("stock-trader");
			int rc = response.getStatus();
			assertEquals("Test getApplications_with_namespace_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test getApplications_with_namespace_succeeds failed with exception " + e.getMessage());
		}
	}
	
	
	@Test
	public void getApplications_succeeds_with_empty_namespace_succeeds() throws Exception {
		mock.checking(new Expectations() {
            {
            	oneOf(coa).setApiClient(with(any(ApiClient.class)));
            	oneOf(coa).listClusterCustomObject("app.k8s.io", "v1beta1", "applications", null, null, null, null);
            	oneOf(ac).getJSON();
            	will(returnValue(json));
            	oneOf(json).getGson();
				will(returnValue(gson));
            }
        });
		
		try {
			response = aep.getApplications("");
			int rc = response.getStatus();
			assertEquals("Test getApplications_succeeds_with_empty_namespace_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test getApplications_succeeds_with_empty_namespace_succeeds failed with exception " + e.getMessage());
		}
	}
	
	@Test
	public void getApplicationAndMap_succeeds() throws Exception {
		mock.checking(new Expectations() {
            {
            	allowing(coa).setApiClient(with(any(ApiClient.class)));
            	oneOf(coa).getNamespacedCustomObject("app.k8s.io", "v1beta1", "stock-trader", "applications", "stock-trader");
            	will(returnValue(jsonObject));
            	oneOf(ac).getJSON();
            	will(returnValue(json));
            	oneOf(json).getGson();
            	will(returnValue(gson));
            	oneOf(coa).listClusterCustomObject("actions.kappnav.io", "v1", "kindactionmappings", null, null, null, null);
            }
        });
		
		try {
			response = aep.getApplicationAndMap("stock-trader", "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test getApplicationAndMap_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test getApplicationAndMap_succeeds failed with exception " + e.getMessage());
		}
	}

}
