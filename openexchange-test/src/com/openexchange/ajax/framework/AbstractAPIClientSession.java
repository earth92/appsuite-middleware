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

package com.openexchange.ajax.framework;

import static com.openexchange.java.Autoboxing.I;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Assert;
import com.openexchange.configuration.AJAXConfig;
import com.openexchange.exception.OXException;
import com.openexchange.test.pool.TestContext;
import com.openexchange.test.pool.TestUser;
import com.openexchange.testing.httpclient.invoker.ApiClient;
import com.openexchange.testing.httpclient.models.CommonResponse;
import com.openexchange.testing.httpclient.modules.LoginApi;

/**
 *
 * {@link AbstractAPIClientSession}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.0
 */
public abstract class AbstractAPIClientSession extends AbstractClientSession {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractAPIClientSession.class);

    protected LoginApi loginApi;
    protected ApiClient apiClient;
    protected Map<TestUser, ApiClient> apiClients = new HashMap<>();

    /**
     * Default constructor.
     *
     * @param name name of the test.
     */
    protected AbstractAPIClientSession() {
        super();
    }

    @Override
    public TestConfig getTestConfig() {
        return TestConfig.builder().createApiClient().build();
    }

    protected ApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Gets the api client with the given number from the first context
     *
     * @param x
     * @return The api client
     */
    protected ApiClient getApiClient(int x) {
        return getApiClient(testContext, x);
    }

    /**
     * Gets the api client with the given number from the first context
     *
     * @param ctx The test context
     * @param x
     * @return The api client
     */
    protected ApiClient getApiClient(TestContext ctx, int x) {
        Assert.assertThat(I(x), allOf(is(greaterThanOrEqualTo(I(0))), is(lessThan(I(users.get(ctx).size())))));
        assertFalse(apiClients.isEmpty());
        return apiClients.get(users.get(ctx).get(x));
    }

    /**
     * Gets the api client for the given user
     *
     * @param user The user
     * @return The api client
     */
    protected ApiClient getApiClient(TestUser user) {
        ApiClient result = apiClients.get(user);
        Assert.assertNotNull("No client for the given user", result);
        return result;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (getTestConfig().createApiClients) {
            for (Entry<TestContext, List<TestUser>> entry : users.entrySet()) {
                for (TestUser user : entry.getValue()) {
                    ApiClient tmpClient = generateApiClient(user);
                    rememberClient(user, tmpClient);
                    if (apiClient == null && user.equals(testUser)) {
                        apiClient = tmpClient;
                    }
                }
            }
        }
    }

    /**
     * Remembers a generated {@link ApiClient} so that it can be loged out after the test run
     *
     * @param client The {@link ApiClient} to remember
     */
    protected void rememberClient(TestUser user, ApiClient client) {
        apiClients.put(user, client);
    }

    @Override
    public void tearDown() throws Exception {
        try {
            for (ApiClient client : apiClients.values()) {
                if (client.getSession() != null) {
                    logoutClient(client, true);
                }
            }
        } finally {
            super.tearDown();
        }
    }

    /**
     * Does a logout for the client. Errors won't be logged.
     * Example:
     * <p>
     * <code>
     * client = logoutClient(client);
     * </code>
     * </p>
     *
     * @param client to logout
     * @return <code>null</code> to prepare client for garbage collection
     */
    protected final ApiClient logoutClient(ApiClient client) {
        return logoutClient(client, false);
    }

    /**
     * Does a logout for the client.
     * Example:
     * <p>
     * <code>
     * client = logoutClient(client, true);
     * </code>
     * </p>
     *
     * @param client to logout
     * @param logging Whether to log an error or not
     * @return <code>null</code> to prepare client for garbage collection
     */
    protected final ApiClient logoutClient(ApiClient client, boolean logging) {
        try {
            if (client != null) {
                client.logout();
                LOG.info("Logout succesfull for user " + client.getUser());
            }
        } catch (Exception e) {
            if (logging) {
                LOG.error("Unable to correctly tear down test setup.", e);
            }
        }
        return null;
    }

    /**
     * Generates a new {@link AJAXClient}. Uses standard client identifier.
     * Generated client needs a <b>logout in tearDown()</b>
     *
     * @return The new {@link AJAXClient}
     * @throws OXException In case no client could be created
     */
    protected final ApiClient generateDefaultApiClient() throws OXException {
        return generateApiClient(getClientId());
    }

    /**
     * Generates a new {@link AJAXClient}.
     * Generated client needs a <b>logout in tearDown()</b>
     *
     * @param client The client identifier to use when performing a login
     * @return The new {@link AJAXClient}
     * @throws OXException In case no client could be created
     */
    protected final ApiClient generateApiClient(String client) throws OXException {
        return generateApiClient(client, testContext.acquireUser());
    }

    /**
     * Generates a new {@link AJAXClient} for the {@link TestUser}.
     * Generated client needs a <b>logout in tearDown()</b>
     *
     * @param user The {@link TestUser} to create a client for
     * @return The new {@link AJAXClient}
     * @throws OXException In case no client could be created
     */
    protected final ApiClient generateApiClient(TestUser user) throws OXException {
        return generateApiClient(getClientId(), user);
    }

    /**
     * Generates a new {@link AJAXClient} for the {@link TestUser}.
     * Generated client needs a <b>logout in tearDown()</b>
     *
     * @param client The client identifier to use when performing a login
     * @param user The {@link TestUser} to create a client for
     * @return The new {@link AJAXClient}
     * @throws OXException In case no client could be created
     */
    protected final ApiClient generateApiClient(String client, TestUser user) throws OXException {
        if (null == user) {
            LOG.error("Can only create a client for an valid user");
            throw new OXException();
        }
        ApiClient newClient;
        try {
            newClient = generateApiClient();
            setBasePath(newClient);
            newClient.setUserAgent("HTTP API Testing Agent");
            newClient.login(user.getLogin(), user.getPassword());
        } catch (Exception e) {
            LOG.error("Could not generate new client for user {} in context {} ", user.getUser(), user.getContext(), e);
            throw new OXException(e);
        }
        return newClient;
    }

    public ApiClient generateApiClient() {
        return new ApiClient();
    }

    protected void setBasePath(ApiClient newClient) {
        String hostname = AJAXConfig.getProperty(AJAXConfig.Property.HOSTNAME);
        if (hostname == null) {
            hostname = "localhost";
        }
        String protocol = AJAXConfig.getProperty(AJAXConfig.Property.PROTOCOL);
        if (protocol == null) {
            protocol = "http";
        }
        newClient.setBasePath(protocol + "://" + hostname + "/ajax");
    }

    /**
     * Checks if a response doesn't contain any errors
     *
     * @param error The error element of the response
     * @param errorDesc The error description element of the response
     */
    protected static void checkResponse(CommonResponse response) {
        assertNull(response.getError(), response.getErrorDesc());
    }

    /**
     * Checks if a response doesn't contain any errors
     *
     * @param error The error element of the response
     * @param errorDesc The error description element of the response
     */
    protected static void checkResponse(String error, String errorDesc) {
        assertNull(errorDesc, error);
    }

    /**
     * Checks if a response doesn't contain any errors
     *
     * @param error The error element of the response
     * @param errorDesc The error description element of the response
     * @param data The data element of the response
     * @return The data
     */
    protected static <T> T checkResponse(String error, String errorDesc, T data) {
        assertNull(errorDesc, error);
        assertNotNull(data);
        return data;
    }

    /**
     * Returns the session id of the default session
     *
     * @return The session id
     */
    protected String getSessionId() {
        return apiClient.getSession();
    }
}
