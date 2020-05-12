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

import application.rest.v1.SecretEndpoint;

import static org.junit.Assert.*;
import java.lang.String;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Secret;

/**
 * @author jasuryak
 *
 */
public class SecretEndpointTest {
    @SuppressWarnings("deprecation")
    private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    private final ApiClient ac = mock.mock(ApiClient.class);
    private final CoreV1Api cv1a = mock.mock(CoreV1Api.class);
    private final JSON json = mock.mock(JSON.class);

    private final Gson gson = new Gson();
    private final V1Secret secret = new V1Secret();
    private final SecretEndpoint sep = new SecretEndpoint();
    private final V1DeleteOptions v1do = new V1DeleteOptions();

    private Response response = null;

    String test1 = "{" + 
            "    \"apiVersion\": \"v1\"," + 
            "    \"data\": {" + 
            "        \"password\": \"security\"," + 
            "        \"username\": \"user1\"" + 
            "    }," + 
            "    \"kind\": \"Secret\"," + 
            "    \"metadata\": {" + 
            "        \"name\": \"kappnav-test-cell1-secret\"," + 
            "        \"namespace\": \"twas\"" + 
            "    }" + 
            "}"; 
    
    // Update the user and password values
    String test2 = "{" + 
            "    \"apiVersion\": \"v1\"," + 
            "    \"data\": {" + 
            "        \"password\": \"c2VjdXJpdHk=\"," + 
            "        \"username\": \"dXNlcjE=\"" + 
            "    }," + 
            "    \"kind\": \"Secret\"," + 
            "    \"metadata\": {" + 
            "        \"name\": \"kappnav-test-cell1-secret\"," + 
            "        \"namespace\": \"twas\"" + 
            "    }" + 
            "}";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        sep.setApiClientForJunit(ac);
        sep.setCoreV1ApiForJunit(cv1a);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void createSecret_succeeds() throws Exception {
        V1ObjectMeta v1om = new V1ObjectMeta();
        v1om.setName("kappnav-test-cell1-secret");
        v1om.setNamespace("twas");

        byte[] un = "user1".getBytes();
        byte[] pwd = "security=".getBytes();
        Map<String, byte[]> data = new HashMap<String, byte[]>();
        data.put("username", un);
        data.put("password", pwd);

        secret.setApiVersion("v1");
        secret.setKind("Secret");
        secret.setMetadata(v1om);
        secret.setData(data);

        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(ac);
                oneOf(ac).getJSON();
                will(returnValue(json));
                oneOf(json).deserialize(test1, V1Secret.class);
                will(returnValue(secret));
                oneOf(cv1a).createNamespacedSecret(KAppNavEndpoint.encodeURLParameter("twas"), secret, null);
            }
        });

        try {
            response = sep.createSecret(test1, "twas");
            int rc = response.getStatus();
            assertEquals("Test createSecret_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test createSecret_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void createSecretThrowApiException_Error() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(ac);
                oneOf(ac).getJSON();
                will(returnValue(json));
                oneOf(json).deserialize(test1, V1Secret.class);
                will(returnValue(secret));
                oneOf(cv1a).createNamespacedSecret(KAppNavEndpoint.encodeURLParameter("twas"), secret, null);
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }
        });

        try {
            response = sep.createSecret(test1, "twas");
            int rc = response.getStatus();
            assertEquals("Test createSecretThrowApiException_Error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test createSecretThrowApiException_Error failed with exception " + e.getMessage());
        }
    }

    @Test
    public void getSecret_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).readNamespacedSecret("kappnav-test-cell1-secret", "twas", null, null, null);
                will(returnValue(secret));
                oneOf(ac).getJSON();
                will(returnValue(json));
                oneOf(json).getGson();
                will(returnValue(gson));
            }
        });

        try {
            response = sep.getSecret("kappnav-test-cell1-secret", "twas");
            int rc = response.getStatus();
            assertEquals("Test getSecret_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test getSecret_succeeds failed with exception " + e.getMessage());
        }
    }
    
    public void getSecretThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).readNamespacedSecret("kappnav-test-cell1-secret", "twas", null, null, null);
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }
        });

        try {
            response = sep.getSecret("kappnav-test-cell1-secret", "twas");
            int rc = response.getStatus();
            assertEquals("Test getSecretThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test getSecretThrowApiException_error failed with exception " + e.getMessage());
        }
    }

    @Test
    public void replaceSecret_succeeds() throws Exception {
        V1ObjectMeta v1om = new V1ObjectMeta();
        v1om.setName("kappnav-test-cell1-secret");
        v1om.setNamespace("twas");

        byte[] un = "dXNlcjE=".getBytes();
        byte[] pwd = "c2VjdXJpdHk=".getBytes();
        Map<String, byte[]> data = new HashMap<String, byte[]>();
        data.put("username", un);
        data.put("password", pwd);

        secret.setApiVersion("v1");
        secret.setKind("Secret");
        secret.setMetadata(v1om);
        secret.setData(data);

        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(ac);
                oneOf(ac).getJSON();
                will(returnValue(json));
                oneOf(json).deserialize(test2, V1Secret.class);
                will(returnValue(secret));
                oneOf(cv1a).replaceNamespacedSecret(KAppNavEndpoint.encodeURLParameter("kappnav-test-cell1-secret"),
                        "twas", secret, null);
            }
        });

        try {
            response = sep.replaceSecret(test2, "kappnav-test-cell1-secret", "twas");
            int rc = response.getStatus();
            assertEquals("Test replaceSecret_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test replaceSecret_succeeds failed with exception " + e.getMessage());
        }
    }
    
    @Test
    public void replaceSecretThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(ac);
                oneOf(ac).getJSON();
                will(returnValue(json));
                oneOf(json).deserialize(test2, V1Secret.class);
                will(returnValue(secret));
                oneOf(cv1a).replaceNamespacedSecret(KAppNavEndpoint.encodeURLParameter("kappnav-test-cell1-secret"),
                        "twas", secret, null);
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }
        });

        try {
            response = sep.replaceSecret(test2, "kappnav-test-cell1-secret", "twas");
            int rc = response.getStatus();
            assertEquals("Test replaceSecretThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test replaceSecretThrowApiException_error failed with exception " + e.getMessage());
        }
    }

    @Test
    public void deleteSecret_succeeds() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).deleteNamespacedSecret(KAppNavEndpoint.encodeURLParameter("kappnav-test-cell1-secret"),
                        KAppNavEndpoint.encodeURLParameter("twas"), v1do, null, 0, true, "");
            }
        });

        try {
            response = sep.deleteSecret("kappnav-test-cell1-secret", "twas");
            int rc = response.getStatus();
            assertEquals("Test deleteSecret_succeeds FAILED", 200, rc);
        } catch (Exception e) {
            fail("Test deleteSecret_succeeds failed with exception " + e.getMessage());
        }
    }

    @Test
    public void deleteSecretThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                oneOf(cv1a).setApiClient(with(any(ApiClient.class)));
                oneOf(cv1a).deleteNamespacedSecret(KAppNavEndpoint.encodeURLParameter("kappnav-test-cell1-secret"),
                        KAppNavEndpoint.encodeURLParameter("twas"), v1do, null, 0, true, "");
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }
        });

        try {
            response = sep.deleteSecret("kappnav-test-cell1-secret", "twas");
            int rc = response.getStatus();
            assertEquals("Test deleteSecretThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test deleteSecretThrowApiException_error failed with exception " + e.getMessage());
        }
    }

}
