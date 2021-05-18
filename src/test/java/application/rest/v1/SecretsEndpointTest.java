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

import application.rest.v1.SecretsEndpoint;

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

import com.google.gson.JsonSyntaxException;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;

/**
 * @author jasuryak
 *
 */
public class SecretsEndpointTest {
	@SuppressWarnings("deprecation")	
	private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    
	//private final CoreV1Api cv1a = mock.mock(CoreV1Api.class);   
	
	private final SecretsEndpoint sep = new SecretsEndpoint();
	private Response response = null;
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		//sep.setCoreV1ApiForInternal(cv1a);
	}

	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Ignore
	@Test
	public void getSecrets_succeeds() throws Exception {
		mock.checking(new Expectations() {
			{
				//oneOf(cv1a).setApiClient(with(any(ApiClient.class))); 
				//oneOf(cv1a).listSecretForAllNamespaces(false, null, null, "secret-label=mysecret", 60, null, null, null, 60, false);
			}		
		});
		try {
			response = sep.getSecrets("secret-label", "mysecret");
			int rc = response.getStatus();
			assertEquals("Test getSecrets_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test getSecrets_succeeds failed with exception " + e.getMessage());
		}
	}
	
	@Ignore
	@Test
    public void getSecretsThrowApiException_error() throws Exception {
        mock.checking(new Expectations() {
            {
                //oneOf(cv1a).setApiClient(with(any(ApiClient.class))); 
                //oneOf(cv1a).listSecretForAllNamespaces(null, null, null, "secret-label=mysecret", 60, null, null, null, 60, false);
                will(throwException(new ApiException(207, "Injection to throw ApiException.")));
            }       
        });
        try {
            response = sep.getSecrets("secret-label", "mysecret");
            int rc = response.getStatus();
            assertEquals("Test getSecretsThrowApiException_error FAILED", 207, rc);
        } catch (Exception e) {
            fail("Test getSecretsThrowApiException_error failed with exception " + e.getMessage());
        }
    }
	
}
