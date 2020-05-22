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

import application.rest.v1.ComponentsEndpoint;

import static org.junit.Assert.*;

import javax.ws.rs.core.Response;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonSyntaxException;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.ApisApi;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;

/**
 * @author jasuryak
 *
 */
public class ComponentsEndpointTest {
    @SuppressWarnings("deprecation")
    private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    private final ApisApi apis = mock.mock(ApisApi.class);
    private final CustomObjectsApi coa = mock.mock(CustomObjectsApi.class);

    private final ComponentsEndpoint cep = new ComponentsEndpoint();
    private Response response = null;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        ComponentInfoRegistry.setApisApiForJunit(apis);
        ApplicationCache.setCustomObjectsApiForJunit(coa);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getComponents_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                allowing(coa).setApiClient(with(any(ApiClient.class)));
                oneOf(coa).getNamespacedCustomObject("app.k8s.io", "v1beta1", "stock-trader", "applications",
                        "stock-trader");
            }
        });
        try {
            response = cep.getComponents("stock-trader", "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test getComponents_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test getComponents_succeeds failed with exception " + e.getMessage());
        }
    }

    @Test
    public void getComponentsThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                allowing(apis).setApiClient(with(any(ApiClient.class)));
                allowing(apis).getAPIVersions();
                allowing(coa).setApiClient(with(any(ApiClient.class)));
                oneOf(coa).getNamespacedCustomObject("app.k8s.io", "v1beta1", "stock-trader", "applications",
                        "stock-trader");
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }
        });
        try {
            response = cep.getComponents("stock-trader", "stock-trader");
            int rc = response.getStatus();
            assertEquals("Test getComponentsThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test getComponentsThrowApiException_error failed with exception " + e.getMessage());
        }
    }

}
