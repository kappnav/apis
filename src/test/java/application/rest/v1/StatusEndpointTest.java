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

import application.rest.v1.StatusEndpoint;

import static org.junit.Assert.*;

import javax.ws.rs.core.Response;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.ApisApi;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;

/**
 * @author jasuryak
 *
 */
public class StatusEndpointTest {
	@SuppressWarnings("deprecation")	
	private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
   
	private final CustomObjectsApi coa = mock.mock(CustomObjectsApi.class);
	private final ApisApi apis = mock.mock(ApisApi.class);
	//private final CoreV1Api cv1a = mock.mock(CoreV1Api.class);
	
	private final StatusEndpoint sep = new StatusEndpoint();
	private Response response = null;
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	    ComponentInfoRegistry.setCustomObjectsApiForJunit(coa);
	    ComponentInfoRegistry.setApisApiForJunit(apis);
	    //ComponentInfoRegistry.setCoreV1ApiForJunit(cv1a);
	}

	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Ignore
	@Test
	public void computeStatus_succeeds() throws Exception {
		mock.checking(new Expectations() {
			{
			    allowing(coa).setApiClient(with(any(ApiClient.class)));
			    oneOf(coa).listClusterCustomObject("apiregistration.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("extensions", "v1beta1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("apps", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("events.k8s.io", "v1beta1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("authentication.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("authorization.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("autoscaling", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("batch", "v1", ".", "true", null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("certificates.k8s.io", "v1beta1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("networking.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("policy", "v1beta1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("rbac.authorization.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("storage.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("admissionregistration.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("apiextensions.k8s.io", "v1beta1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("apiextensions.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("scheduling.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("coordination.k8s.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("node.k8s.io", "v1beta1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("apps.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("authorization.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("build.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("image.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("oauth.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("project.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("quota.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("route.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("security.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("template.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("user.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("packages.operators.coreos.com", "v1", ".", null, null, null, null, 60, null, 60, false);
			    oneOf(coa).listClusterCustomObject("config.openshift.io", "v1", ".", null, null, null, null, 60, null, 60, false);
			    allowing(apis).setApiClient(with(any(ApiClient.class)));
			    allowing(apis).getAPIVersions();
			    //allowing(cv1a).setApiClient(with(any(ApiClient.class)));
			    //oneOf(cv1a).readNamespacedSecret("mysecret", "", null, null, null);
			}		
		});
		try {
		    response = sep.computeStatus("mysecret", "Secret", "/v1", "");
			int rc = response.getStatus();
			assertEquals("Test computeStatus_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test computeStatus_succeeds failed with exception " + e.getMessage());
		}
	}
	
	@Test
    public void computeStatusThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }       
        });
        try {
            response = sep.computeStatus("stock-trader", "Application", "/v1", "");
            int rc = response.getStatus();
            assertEquals("Test computeStatusThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test computeStatusThrowApiException_error failed with exception " + e.getMessage());
        }
    }
	
}
