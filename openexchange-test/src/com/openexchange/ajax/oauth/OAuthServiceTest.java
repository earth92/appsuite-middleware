/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.ajax.oauth;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.ProvisioningSetup;
import com.openexchange.ajax.framework.config.util.ChangePropertiesRequest;
import com.openexchange.ajax.oauth.actions.AllOAuthServicesRequest;
import com.openexchange.ajax.oauth.actions.GetOAuthServiceRequest;
import com.openexchange.ajax.oauth.actions.OAuthServicesResponse;
import com.openexchange.ajax.oauth.types.OAuthService;
import com.openexchange.exception.OXException;
import com.openexchange.test.pool.TestContext;
import com.openexchange.test.pool.TestContextPool;

/**
 * Instances of com.openexchange.oauth.OAuthServiceMetaData should be invisible if their according
 * enable-property is set to 'false'. This property is visible via ConfigCascade. We test the implementation
 * 'com.openexchange.oauth.testservice' here. The service is enabled server-wide but will be disabled for client2
 * at user-scope.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 */
public class OAuthServiceTest {

    private static final String TESTSERVICE = "com.openexchange.oauth.testservice";

    private TestContext testContext;
    private AJAXClient client1;
    private AJAXClient client2;

    @BeforeClass
    public static void before() throws Exception {
        ProvisioningSetup.init();
    }

    @Before
    public void setUp() throws Exception {
        testContext = TestContextPool.acquireContext(getClass().getCanonicalName());
        client1 = testContext.acquireUser().getAjaxClient();
        client2 = testContext.acquireUser().getAjaxClient();

        Map<String, String> properties = Collections.singletonMap("com.openechange.oauth.testservice.enabled", "false");
        ChangePropertiesRequest changePropertiesRequest = new ChangePropertiesRequest(properties, "user", null);
        client2.execute(changePropertiesRequest);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (null != client1) {
                client1.logout();
            }
            if (null != client2) {
                client2.logout();
            }
        } finally {
            TestContextPool.backContext(testContext);
        }
    }

    @Test
    public void testGetAllServices() throws OXException, IOException, JSONException {
        OAuthServicesResponse response = client1.execute(new AllOAuthServicesRequest());
        List<OAuthService> services = response.getServices();
        boolean found = false;
        for (OAuthService service : services) {
            if (TESTSERVICE.equals(service.getId())) {
                found = true;
                break;
            }
        }
        Assert.assertTrue("Service is missing: '" + TESTSERVICE + "'", found);
    }

    @Test
    public void testGetTestService() throws OXException, IOException, JSONException {
        OAuthServicesResponse response = client1.execute(new GetOAuthServiceRequest(TESTSERVICE));
        List<OAuthService> services = response.getServices();
        Assert.assertEquals("Get response should contain exactly one service", 1, services.size());
        OAuthService service = services.get(0);
        Assert.assertEquals("Service is missing: '" + TESTSERVICE + "'", TESTSERVICE, service.getId());
    }

    @Test
    public void testGetAllServicesWithoutPermission() throws Exception {
        OAuthServicesResponse response = client2.execute(new AllOAuthServicesRequest());
        List<OAuthService> services = response.getServices();
        boolean found = false;
        for (OAuthService service : services) {
            if (TESTSERVICE.equals(service.getId())) {
                found = true;
                break;
            }
        }
        Assert.assertFalse("Service is present without permission: '" + TESTSERVICE + "'", found);
    }
}
