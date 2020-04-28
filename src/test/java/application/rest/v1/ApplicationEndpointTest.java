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

import application.rest.v1.ApplicationEndpoint;

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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1DeleteOptions;

/**
 * @author jasuryak
 *
 */
public class ApplicationEndpointTest {
	@SuppressWarnings("deprecation")	
	private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    
	private final ApiClient ac = mock.mock(ApiClient.class);
	private final CustomObjectsApi coa = mock.mock(CustomObjectsApi.class);
	private final ApplicationEndpoint aep = new ApplicationEndpoint();
	private final ApplicationCache appc = new ApplicationCache();
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
	
	// change the name and solution to stock-trader-new
	String updatedSampleApp = "{" + 
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
			"                \"name\": \"stock-trader-new\"," + 
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
			"                        \"solution\": \"stock-trader-new\"" + 
			"                    }" + 
			"                }" + 
			"            }" + 
			"        }" + 
			"    ]" + 
			"}";

	JsonObject jsonObject1 = new JsonParser().parse(sampleApp).getAsJsonObject();
	JsonObject jsonObject2 = new JsonParser().parse(updatedSampleApp).getAsJsonObject();
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		aep.setApiClientForJunit(ac);
		KAppNavEndpoint.setCustomObjectsApiForJunit(coa);
		appc.setCustomObjectsApiForJunit(coa);
	}

	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	
	@Test
	public void createApplication_succeeds() throws Exception {
		mock.checking(new Expectations() {
			{
				oneOf(coa).setApiClient(ac);
				oneOf(coa).createNamespacedCustomObject(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(Object.class)), with(any(String.class)));
			}
			
		});
		
		try {
			response = aep.createApplication(sampleApp, "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test createApplication_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test createApplication_succeeds failed with exception " + e.getMessage());
		}
	}
	
	
	@Test
	public void getApplication_succeeds() throws Exception {
		mock.checking(new Expectations() {
			{
				allowing(coa).setApiClient(ac);
				oneOf(coa).getNamespacedCustomObject(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)));
            	will(returnValue(jsonObject1));
			}		
		});
		
		try {
			response = aep.getApplication("stock-trader", "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test getApplication_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test getApplication_succeeds failed with exception " + e.getMessage());
		}
	}
	
	
	@Test
	public void replaceApplication_succeeds() throws Exception {
		mock.checking(new Expectations() {
			{
				oneOf(coa).setApiClient(ac);
				oneOf(coa).replaceNamespacedCustomObject(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(Object.class)));				
			}		
		});
		
		try {
			response = aep.replaceApplication(updatedSampleApp, "stock-trader", "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test replaceApplication_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test replaceApplication_succeeds failed with exception " + e.getMessage());
		}
	}
	
	
	@Test
	public void deleteApplication_succeeds() throws Exception {
		mock.checking(new Expectations() {
			{
				oneOf(coa).setApiClient(ac);
				oneOf(coa).deleteNamespacedCustomObject(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), (V1DeleteOptions) with(any(Object.class)), with(any(Integer.class)), with(any(Boolean.class)), with(any(String.class)));		
			}
		});
		
		try {
			response = aep.deleteApplication(updatedSampleApp, "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test deleteApplication_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test deleteApplication_succeeds failed with exception " + e.getMessage());
		}
	}
	
}
