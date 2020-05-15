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

import application.rest.v1.ComponentInfoRegistry;
import application.rest.v1.ComponentInfoRegistry.ComponentInfo;

import static org.junit.Assert.*;

import java.lang.String;


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
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.ApisApi;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;

/**
 * @author jasuryak
 *
 */
public class ComponentInfoRegistryTest {
    @SuppressWarnings("deprecation")
    private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    private final ApisApi apis = mock.mock(ApisApi.class);
    private final CustomObjectsApi coa = mock.mock(CustomObjectsApi.class);
    private final CoreV1Api cv1a = mock.mock(CoreV1Api.class);
    private final ApiClient ac = mock.mock(ApiClient.class);
    
    ComponentInfo ci = null;
    ComponentInfoRegistry cir = null;
   
    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        ComponentInfoRegistry.setApisApiForJunit(apis);
        ComponentInfoRegistry.setCustomObjectsApiForJunit(coa);
        ComponentInfoRegistry.setCoreV1ApiForJunit(cv1a);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void isNamespaced_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
            }

        });

        String kind = "ConfigMap";
        String apiVersion = "/v1";
        try {
            cir = new ComponentInfoRegistry();
            Boolean result = cir.isNamespaced(ac, kind, apiVersion);
            assertEquals("Test createApplication_succeeds FAILED", "true", result.toString());
        } catch (Exception e) {
            fail("Test createApplication_succeeds failed with exception " + e.getMessage());
        }
    }

    @Test
    public void isNamespacedWithNullApiVersion_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
            }

        });
 
        String kind = "ConfigMap";
        String apiVersion = null;
        try {
            cir = new ComponentInfoRegistry();
            Boolean result = cir.isNamespaced(ac, kind, apiVersion);
            assertEquals("Test isNamespacedWithNullApiVersion_succeeds FAILED", "true", result.toString());
        } catch (Exception e) {
            fail("Test isNamespacedWithNullApiVersion_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void isNamespacedWithWrongKind_error() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
            }

        });

        String kind = "Configmap";
        String apiVersion = "/v1";
        String expectedError = "resource kind " + kind + " or apiVersion " + apiVersion + " is Not Found";
        try {
            cir = new ComponentInfoRegistry();
            cir.isNamespaced(ac, kind, apiVersion);
            fail("Test isNamespacedWithWrongKind_error should failed with exception " + expectedError);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            assertEquals("Test isNamespacedWithWrongKind_error FAILED", expectedError, e.getMessage());
        }
    }
    
    @Test
    public void isNamespacedWithWrongApiVersion_error() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
            }

        });

        String kind = "ConfigMap";
        String apiVersion = "/v100";
        String expectedError = "resource kind " + kind + " or apiVersion " + apiVersion + " is Not Found";
        try {
            cir = new ComponentInfoRegistry();
            cir.isNamespaced(ac, kind, apiVersion);
            fail("Test isNamespacedWithWrongApiVersion_error should failed with exception " + expectedError);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            assertEquals("Test isNamespacedWithWrongApiVersion_error FAILED", expectedError, e.getMessage());
        }
    }
    
    @Test
    public void listClusterObject_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                oneOf(cv1a).setApiClient(ac);
                oneOf(cv1a).listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
            }

        });

        try {
            cir = new ComponentInfoRegistry();
            cir.listClusterObject(ac, "Pod", "/v1", null, null, null, null);
        } catch (Exception e) {
            fail("Test listClusterObject_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void listNamespacedObject_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                oneOf(cv1a).setApiClient(ac);
                oneOf(cv1a).listNamespacedSecret(null, null, null, null, null, null, null, null, null, null);
            }

        });

        try {
            cir = new ComponentInfoRegistry();
            cir.listNamespacedObject(ac, "Secret", "/v1", null, null, null, null, null);
        } catch (Exception e) {
            fail("Test listClusterObject_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void getNamespacedObject_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                oneOf(cv1a).setApiClient(ac);
                oneOf(cv1a).readNode(null, null, null, null);
            }

        });

        try {
            cir = new ComponentInfoRegistry();
            Object result = cir.getNamespacedObject(ac, "Node", "/v1", null, null);
            assertNotNull("Test listClusterObject_succeeds FAILED", result);
        } catch (Exception e) {
            fail("Test listClusterObject_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void getNamespacedObjectWithNoExistKind_error() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                oneOf(cv1a).setApiClient(ac);
            }

        });

        String expectedError = "resource kind Application is Not Found";
        try {
            cir = new ComponentInfoRegistry();
            // Specify apiVersion so it will use the BUILT_IN_KIND_TO_API_VERSION_MAP
            // and use kind that does not exist in the map
            cir.getNamespacedObject(ac, "Application", "/v1", null, null);
            fail("Test getNamespacedObjectWithNoExistKind_error should failed.");
        } catch (Exception e) {
            assertEquals("Test getNamespacedObjectWithNoExistKind_error FAILED", expectedError, e.getMessage());
        }
    }
    
    @Test
    public void getNamespacedObjectWithNoVersion_error() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                oneOf(cv1a).setApiClient(ac);
            }

        });

        String expectedError = "resource kind Namespace is Not Found";
        try {
            cir = new ComponentInfoRegistry();
            // Use null for apiVersion so it will use the CORE_KIND_TO_API_VERSION_MAP 
            // and use kind that does not exist in the map
            cir.getNamespacedObject(ac, "Namespace", null, null, null);
            fail("Test getNamespacedObjectWithNoVersion_error should failed.");
        } catch (Exception e) {
            assertEquals("Test getNamespacedObjectWithNoVersion_error FAILED", expectedError, e.getMessage());
        }
    }
    
    @Test
    public void getNamespacedObjectThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                oneOf(cv1a).setApiClient(ac);
                oneOf(cv1a).readNamespacedConfigMap(null, null, null, null, null);
                will(throwException(new ApiException("Injection to throw ApiException.")));
            }

        });

        String expectedError = "io.kubernetes.client.ApiException: Injection to throw ApiException.";
        try {
            cir = new ComponentInfoRegistry();
            cir.getNamespacedObject(ac, "ConfigMap", null, null, null);
            fail("Test getNamespacedObjectThrowApiException_error should failed.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            assertEquals("Test getNamespacedObjectThrowApiException_error FAILED", expectedError, e.getMessage());
        }
    }
    
    @Test
    public void getComponentGroupApiVersions_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                
            }
        });

        String expectedResult = "[apps/v1]";
        try {
            cir = new ComponentInfoRegistry();
            ComponentKind ck = new ComponentKind("core", "Deployment");
            Object result = cir.getComponentGroupApiVersions(ck);
            assertEquals("Test getComponentGroupApiVersions_succeeds FAILED", expectedResult, result.toString());
        } catch (Exception e) {
            fail("Test listClusterObject_succeeds failed with exception " + e.getMessage());
        }
    }
 
}
