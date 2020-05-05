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

import application.rest.v1.NamespacesEndpoint;

import static org.junit.Assert.*;

import javax.ws.rs.core.Response;


import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author jasuryak
 *
 */
public class NamespacesEndpointTest {
	@SuppressWarnings("deprecation")	
	private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
	
	private final NamespacesEndpoint nep = new NamespacesEndpoint();
	private Response response = null;
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	
	@Test
	public void getNamespaceList_succeeds() throws Exception {
		try {
			response = nep.getNamespaceList();
			int rc = response.getStatus();
			assertEquals("Test getNamespaceList_succeeds FAILED", 200, rc);
		} catch (Exception e) {
			fail("Test getNamespaceList_succeeds failed with exception " + e.getMessage());
		}
	}
	
}
