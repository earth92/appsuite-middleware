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

import java.util.LinkedList;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import com.google.code.tempusfugit.concurrency.ConcurrentTestRunner;
import com.google.code.tempusfugit.concurrency.annotations.Concurrent;
import com.openexchange.exception.OXException;
import com.openexchange.test.pool.TestContext;
import com.openexchange.test.pool.TestContextPool;
import com.openexchange.test.pool.TestUser;
import com.openexchange.test.tryagain.TryAgain;
import com.openexchange.test.tryagain.TryAgainTestRule;

/**
 * {@link AbstractClientSession}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since v7.10.0
 */
@RunWith(ConcurrentTestRunner.class)
@Concurrent(count = 5)
public class AbstractClientSession {

    @Rule public TestName name = new TestName();

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {

        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractClientSession.class);
    }

    /** Declare 'try again' rule as public field to allow {@link TryAgain}-annotation for tests */
    @org.junit.Rule
    public final TryAgainTestRule tryAgainRule = new TryAgainTestRule();

    private List<TearDownOperation> operations;

    private AJAXClient client;
    private AJAXClient client2;
    protected TestContext testContext;
    protected List<TestContext> testContextList;
    protected TestUser admin;
    protected TestUser testUser;
    protected TestUser testUser2;

    @Before
    public void setUp() throws Exception {
        ProvisioningSetup.init();

        operations = new LinkedList<>();
        testContextList = TestContextPool.acquireContext(this.getClass().getCanonicalName() + "." + name.getMethodName(), getNumerOfContexts());
        testContext = testContextList.get(0);
        Assert.assertNotNull("Unable to retrieve a context!", testContext);
        testUser = testContext.acquireUser();
        testUser2 = testContext.acquireUser();
        client = generateClient(testUser);
        client2 = generateClient(testUser2);
        admin = testContext.getAdmin();
    }

    /**
     * Allows to override the number of contexts aquired from this test
     *
     * @return The number of context to aquire. Defaults to 1
     */
    protected int getNumerOfContexts() {
        return 1;
    }

    @SuppressWarnings("unused")
    @After
    public void tearDown() throws Exception {
        try {
            /*
             * Call operations from last added item to first added item (LIFO)
             * to avoid premature closing of e.g. API clients before all relevant
             * operations for this client has been called
             */
            for (int i = operations.size() - 1; i >= 0; i--) {
                operations.get(i).safeTearDown();
            }
            client = logoutClient(client, true);
            client2 = logoutClient(client2, true);

        } finally {
            TestContextPool.backContext(testContextList);
        }
    }

    protected final AJAXClient getClient() {
        return client;
    }

    protected final AJAXClient getClient2() {
        return client2;
    }

    public final AJAXSession getSession() {
        return client.getSession();
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
    protected final AJAXClient logoutClient(AJAXClient client) {
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
     * @param loggin Whether to log an error or not
     * @return <code>null</code> to prepare client for garbage collection
     */
    protected final AJAXClient logoutClient(AJAXClient client, boolean loggin) {
        try {
            if (client != null) {
                client.logout();
            }
        } catch (Exception e) {
            if (loggin) {
                LoggerHolder.LOG.error("Unable to correctly tear down test setup.", e);
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
    protected final AJAXClient generateDefaultClient() throws OXException {
        return generateClient(getClientId());
    }

    /**
     * Generates a new {@link AJAXClient}.
     * Generated client needs a <b>logout in tearDown()</b>
     *
     * @param client The client identifier to use when performing a login
     * @return The new {@link AJAXClient}
     * @throws OXException In case no client could be created
     */
    protected final AJAXClient generateClient(String client) throws OXException {
        return generateClient(client, testContext.acquireUser());
    }

    /**
     * Generates a new {@link AJAXClient} for the {@link TestUser}.
     * Generated client needs a <b>logout in tearDown()</b>
     *
     * @param user The {@link TestUser} to create a client for
     * @return The new {@link AJAXClient}
     * @throws OXException In case no client could be created
     */
    protected final AJAXClient generateClient(TestUser user) throws OXException {
        return generateClient(getClientId(), user);
    }

    /**
     * Gets the client identifier to use when performing a login
     *
     * @return The client identifier or <code>null</code> to use default one (<code>"com.openexchange.ajax.framework.AJAXClient"</code>)
     */
    protected String getClientId() {
        return null;
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
    protected final AJAXClient generateClient(String client, TestUser user) throws OXException {
        if (null == user) {
            LoggerHolder.LOG.error("Can only create a client for an valid user");
            throw new OXException();
        }
        AJAXClient newClient;
        try {
            if (null == client || client.isEmpty()) {
                newClient = new AJAXClient(user);
            } else {
                newClient = new AJAXClient(user, client);
            }
        } catch (Exception e) {
            LoggerHolder.LOG.error("Could not generate new client for user {} in context {}.", user.getUser(), user.getContext(), e);
            throw new OXException(e);
        }
        return newClient;
    }

    /**
     * Adds a new {@link TearDownOperation} to call in this classes {@link #tearDown()} method
     * <p>
     * Note: Operations will be remembered in order and will be executed with the last-in first-out (LIFO)
     * principal. Therefore e.g. first add the logout of the test client afterwards the removal of a the calendar event
     * that uses the client from before.
     *
     * @param operation A {@link TearDownOperation} to execute with {@link TearDownOperation#safeTearDown()}
     */
    protected void addTearDownOperation(TearDownOperation operation) {
        if (null != operation) {
            operations.add(operation);
        }
    }

    /**
     *
     * {@link TearDownOperation} - A tear down operation
     *
     * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
     * @since v7.10.4
     */
    @FunctionalInterface
    public interface TearDownOperation {

        /**
         * A tear down operation
         *
         * @throws Exception
         */
        void tearDown() throws Exception;

        /**
         * Executes the tear down operation via {@link #tearDown()}
         * with logging the error
         *
         */
        default void safeTearDown() {
            try {
                tearDown();
            } catch (Throwable t) {
                LoggerHolder.LOG.debug("Unable to execute tear down operation", t);
            }
        }
    }
}
