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

import application.rest.v1.ApplicationCache;

import static org.junit.Assert.*;

import java.lang.String;
import java.util.ArrayList;
import java.util.List;

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
import com.google.gson.JsonSyntaxException;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1DeleteOptions;

/**
 * @author jasuryak
 *
 */
public class ApplicationCacheTest {
    @SuppressWarnings("deprecation")
    private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    private final CustomObjectsApi coa = mock.mock(CustomObjectsApi.class);
    private final KAppNavEndpoint kanep = mock.mock(KAppNavEndpoint.class);
   
    List<JsonObject> jsonList = new ArrayList<JsonObject>();
    
    private ApiClient ac = null;

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
            "}";
    
    JsonObject jsonObject1 = new JsonParser().parse(test1).getAsJsonObject();
    JsonObject jsonObject2 = new JsonParser().parse(test2).getAsJsonObject();

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        ac = KAppNavEndpoint.getApiClient();
        ApplicationCache.setCustomObjectsApiForJunit(coa);
        jsonList.add(jsonObject2);
    }

    /**
     * @throws java.lang.Exception
     */
    @SuppressWarnings("static-access")
    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getNamespacedApplicationObject_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(coa).setApiClient(with(any(ApiClient.class)));
                oneOf(coa).getNamespacedCustomObject("app.k8s.io", "v1beta1", "book-info", "applications", "book-info");
                will(returnValue(jsonObject1));
            }
        });

        try {
            JsonObject result = ApplicationCache.getNamespacedApplicationObject(ac, "book-info", "book-info");
            assertEquals("Test getNamespacedApplicationObject_succeeds FAILED", jsonObject1, result);
        } catch (Exception e) {
            fail("Test getNamespacedApplicationObject_succeeds failed with exception " + e.getMessage());
        }
    }

    @Test
    public void listApplicationObject_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(coa).setApiClient(with(any(ApiClient.class)));
                oneOf(coa).listClusterCustomObject("app.k8s.io", "v1beta1", "applications", null, null, null, null);
                will(returnValue(jsonObject1));
            }

        });

        try {
            List<JsonObject> result = ApplicationCache.listApplicationObject(ac);
            assertEquals("Test listApplicationObject_succeeds FAILED", jsonList, result);
        } catch (Exception e) {
            fail("Test listApplicationObject_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void listNamespacedApplicationObject_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(coa).setApiClient(with(any(ApiClient.class)));
                oneOf(coa).listNamespacedCustomObject("app.k8s.io", "v1beta1", "stock-trader", "applications", null, null, null, null);
                will(returnValue(jsonObject1));
            }
        });

        try {
            List<JsonObject> result = ApplicationCache.listNamespacedApplicationObject(ac, "stock-trader");
            assertEquals("Test listApplicationObject_succeeds FAILED", jsonList, result);
        } catch (Exception e) {
            fail("Test createApplicationThrowApiException_error failed with exception " + e.getMessage());
        }
    }
    
}
