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
	public void createApplication() throws Exception {
		mock.checking(new Expectations() {
            {
            	oneOf(coa).setApiClient(ac);
            	oneOf(coa).createNamespacedCustomObject(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(Object.class)), with(any(String.class)));
            	
            }
        });

		try {
			response = aep.createApplication(sampleApp, "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test createApplication FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test createApplication failed with exception " + e.getMessage());
		}
	}
	
	
	@Test
	public void getApplication() throws Exception {
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
			assertEquals("Test getApplication FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test getApplication failed with exception " + e.getMessage());
		}
	}
	
	
	@Test
	public void replaceApplication() throws Exception {
		mock.checking(new Expectations() {
            {
            	oneOf(coa).setApiClient(ac);
            	oneOf(coa).replaceNamespacedCustomObject(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(Object.class)));
            }
        });
		
		try {
			response = aep.replaceApplication(updatedSampleApp, "stock-trader", "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test replaceApplication FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test replaceApplication failed with exception " + e.getMessage());
		}
	}
	
	
	@Test
	public void deleteApplication() throws Exception {
		mock.checking(new Expectations() {
            {
            	oneOf(coa).setApiClient(ac);
            	oneOf(coa).deleteNamespacedCustomObject(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(String.class)), (V1DeleteOptions) with(any(Object.class)), with(any(Integer.class)), with(any(Boolean.class)), with(any(String.class)));
            }
        });
		
		try {
			response = aep.deleteApplication(updatedSampleApp, "stock-trader");
			int rc = response.getStatus();
			assertEquals("Test deleteApplication FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test deleteApplication failed with exception " + e.getMessage());
		}
	}
	
}
