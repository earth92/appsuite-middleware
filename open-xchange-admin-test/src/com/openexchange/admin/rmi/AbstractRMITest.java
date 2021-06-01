/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 * 
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.admin.rmi;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.Timeout;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Credentials;
import com.openexchange.admin.rmi.dataobjects.Group;
import com.openexchange.admin.rmi.dataobjects.Resource;
import com.openexchange.admin.rmi.dataobjects.User;
import com.openexchange.admin.rmi.factory.UserFactory;
import com.openexchange.admin.rmi.manager.ContextManager;
import com.openexchange.admin.rmi.manager.DatabaseManager;
import com.openexchange.admin.rmi.manager.FilestoreManager;
import com.openexchange.admin.rmi.manager.GroupManager;
import com.openexchange.admin.rmi.manager.MaintenanceReasonManager;
import com.openexchange.admin.rmi.manager.ResellerManager;
import com.openexchange.admin.rmi.manager.ResourceManager;
import com.openexchange.admin.rmi.manager.ServerManager;
import com.openexchange.admin.rmi.manager.TaskManagementManager;
import com.openexchange.admin.rmi.manager.UserManager;
import com.openexchange.admin.rmi.util.AssertUtil;
import com.openexchange.exception.OXException;
import com.openexchange.test.common.configuration.AJAXConfig;
import com.openexchange.test.common.configuration.AJAXConfig.Property;

/**
 * {@link AbstractRMITest}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public abstract class AbstractRMITest {

    protected static String TEST_DOMAIN = "example.org";
    protected static String change_suffix = "-changed";

    protected Credentials contextAdminCredentials;
    protected Credentials superAdminCredentials;
    protected User superAdmin;
    protected User contextAdmin;

    @Rule
    public Timeout globalTimeout = new Timeout(3000, TimeUnit.SECONDS); // 30 seconds max per method tested

    /**
     * Initialises the test configuration and creates one context for the tests
     * 
     * @throws Exception if an error occurs during initialisation of the configuration
     */
    @BeforeClass
    public static void setUpEnvironment() throws OXException {
        AJAXConfig.init();
    }

    /**
     * Clean up managers
     */
    @SuppressWarnings("unused")
    @After
    public void tearDown() throws Exception {
        // perform any clean-ups here
        getContextManager().cleanUp();
        getUserManager().cleanUp();
        getGroupManager().cleanUp();
        getResourceManager().cleanUp();
        getResellerManager().cleanUp();
        getDatabaseManager().cleanUp();
        getServerManager().cleanUp();
        getFilestoreManager().cleanUp();
        getMaintenanceReasonManager().cleanUp();
    }

    @SuppressWarnings("unused")
    @Before
    public void setUp() throws Exception {
        contextAdminCredentials = getContextAdminCredentials();
        superAdminCredentials = getMasterAdminCredentials();

        superAdmin = UserFactory.createUser(superAdminCredentials.getLogin(), superAdminCredentials.getPassword(), "ContextCreatingAdmin", "Ad", "Min", "adminmaster@ox.invalid");
        contextAdmin = UserFactory.createUser(contextAdminCredentials.getLogin(), contextAdminCredentials.getPassword(), "ContextAdmin", "Context", "Admin", "contextAdmin@ox.invalid");
    }

    /**
     * Returns the RMI host URL
     * 
     * @return the RMI host URL
     */
    protected static String getRMIHostUrl() {
        String host = getRMIHost();

        if (!host.startsWith("rmi://")) {
            host = "rmi://" + host;
        }
        if (!host.endsWith("/")) {
            host += "/";
        }
        return host;
    }

    /**
     * Returns the RMI host name. It first looks up the <code>rmi_test_host</code>
     * system property and then the {@link Property#RMIHOST} via the {@link AJAXConfig}
     * 
     * @return The RMI host name.
     */
    protected static String getRMIHost() {
        String host = "localhost";

        if (System.getProperty("rmi_test_host") != null) {
            host = System.getProperty("rmi_test_host");
        } else if (AJAXConfig.getProperty(Property.RMIHOST) != null) {
            host = AJAXConfig.getProperty(Property.RMIHOST);
        }

        return host;
    }

    /**
     * Returns the master <code>oxadminmaster</code> {@link Credentials}.
     * Looks up the password through the system property <code>rmi_test_masterpw</code>
     * i
     * 
     * @return The <code>oxadminmaster</code> {@link Credentials}
     */
    private static Credentials getMasterAdminCredentials() {
        String mpw = "secret";
        if (System.getProperty("rmi_test_masterpw") != null) {
            mpw = System.getProperty("rmi_test_masterpw");
        }
        return new Credentials("oxadminmaster", mpw);
    }

    /**
     * Returns the context admin's {@link Credentials}
     * 
     * @return the context admin's {@link Credentials}
     */
    private static Credentials getContextAdminCredentials() {
        User oxadmin = UserFactory.createContextAdmin();
        return new Credentials(oxadmin.getName(), oxadmin.getPassword());
    }

    /**
     * Asserts that the resource was created properly by fetching it and comparing it with the expected one.
     * 
     * @param expected The expected {@link Resource}
     * @param context The {@link Context}
     * @param credentials The admin credentials
     * @throws Exception if an error is occurred
     */
    public void assertResourceWasCreatedProperly(Resource expected, Context context, Credentials credentials) throws Exception {
        Resource lookup = new Resource();
        lookup.setId(expected.getId());
        lookup = getResourceManager().getData(lookup, context, credentials);
        AssertUtil.assertResourceEquals(expected, lookup);
    }

    /**
     * Asserts that the group was created properly by fetching it and comparing it with the expected one.
     * 
     * @param expected The expected {@link Group}
     * @param context The {@link Context}
     * @param credentials The admin credentials
     * @throws Exception if an error is occurred
     */
    public void assertGroupWasCreatedProperly(Group expected, Context context, Credentials credentials) throws Exception {
        Group lookup = new Group();
        lookup.setId(expected.getId());
        lookup = getGroupManager().getData(lookup, context, credentials);
        AssertUtil.assertGroupEquals(expected, lookup);
    }

    /**
     * Asserts that the user was created properly by fetching it and comparing it with the expected one.
     * 
     * @param expected The expected {@link User}
     * @param context The {@link Context}
     * @param credentials The admin credentials
     * @throws Exception if an error is occurred
     */
    public void assertUserWasCreatedProperly(User expected, Context context, Credentials credentials) throws Exception {
        User lookupUser = new User();
        lookupUser.setId(expected.getId());
        lookupUser = getUserManager().getData(context, lookupUser, credentials);
        AssertUtil.assertUserEquals(expected, lookupUser);
    }

    public static String getChangedEmailAddress(String address, String changed) {
        return address.replaceFirst("@", changed + "@");
    }

    /*** Interfaces ***/

    public OXTaskMgmtInterface getTaskInterface() throws MalformedURLException, RemoteException, NotBoundException {
        return (OXTaskMgmtInterface) Naming.lookup(getRMIHostUrl() + OXTaskMgmtInterface.RMI_NAME);
    }

    /*** ANY & friends ***/
    protected interface Verifier<T, S> {

        public boolean verify(T obj1, S obj2);
    }

    public <T, S> boolean any(Collection<T> collection, S searched, Verifier<T, S> verifier) {
        for (T elem : collection) {
            if (verifier.verify(elem, searched)) {
                return true;
            }
        }
        return false;
    }

    public <T, S> boolean any(T[] collection, S searched, Verifier<T, S> verifier) {
        return any(Arrays.asList(collection), searched, verifier);
    }

    /////////////////////////// MANAGERS ///////////////////////////////

    /**
     * Gets the {@link ContextManager}
     * 
     * @return The {@link ContextManager}
     */
    protected static ContextManager getContextManager() {
        return ContextManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Returns the {@link UserManager} instance
     * 
     * @return the {@link UserManager} instance
     */
    protected static UserManager getUserManager() {
        return UserManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Gets the {@link DatabaseManager}
     * 
     * @return the {@link DatabaseManager}
     */
    protected static DatabaseManager getDatabaseManager() {
        return DatabaseManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Gets the {@link ServerManager}
     * 
     * @return the {@link ServerManager}
     */
    protected static ServerManager getServerManager() {
        return ServerManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Gets the {@link MaintenanceReasonManager}
     * 
     * @return the {@link MaintenanceReasonManager}
     */
    protected static MaintenanceReasonManager getMaintenanceReasonManager() {
        return MaintenanceReasonManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Gets the {@link GroupManager}
     * 
     * @return the {@link GroupManager}
     */
    protected static GroupManager getGroupManager() {
        return GroupManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Gets the {@link ResourceManager}
     * 
     * @return the {@link ResourceManager}
     */
    protected static ResourceManager getResourceManager() {
        return ResourceManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Gets the {@link FilestoreManager}
     * 
     * @return the {@link FilestoreManager}
     */
    protected static FilestoreManager getFilestoreManager() {
        return FilestoreManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Gets the {@link ResellerManager}
     * 
     * @return the {@link ResellerManager}
     */
    protected static ResellerManager getResellerManager() {
        return ResellerManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    /**
     * Gets the {@link TaskManagementManager}
     * 
     * @return the {@link TaskManagementManager}
     */
    protected static TaskManagementManager getTaskManagementManager() {
        return TaskManagementManager.getInstance(getRMIHostUrl(), getMasterAdminCredentials());
    }

    private Pattern exceptionIdPattern = Pattern.compile("; exceptionId -?\\d.*-\\d.*");

    /**
     * Checks if exception message contains an UUID as identifier
     */
    protected void checkException(Exception e) {
        assertNotNull(e);
        String message = e.getMessage();
        assertTrue(exceptionIdPattern.matcher(message).find());
    }
}
