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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.Naming;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Credentials;
import com.openexchange.admin.rmi.dataobjects.Filestore;
import com.openexchange.admin.rmi.dataobjects.User;
import com.openexchange.admin.rmi.dataobjects.UserModuleAccess;
import com.openexchange.admin.rmi.exceptions.InvalidDataException;
import com.openexchange.admin.rmi.exceptions.NoSuchUserException;
import com.openexchange.admin.rmi.exceptions.StorageException;
import com.openexchange.admin.rmi.factory.ContextFactory;
import com.openexchange.admin.rmi.factory.UserFactory;
import com.openexchange.admin.rmi.util.AssertUtil;
import com.openexchange.java.Autoboxing;
import com.openexchange.test.common.data.conversion.ical.Assert;

/**
 * {@link UserTest}
 *
 * @author cutmasta
 * @author d7
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class UserTest extends AbstractRMITest {

    public final String NAMED_ACCESS_COMBINATION_BASIC = "all";

    // list of chars that must be valid
    protected final String VALID_CHAR_TESTUSER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // global setting for stored password
    protected final String pass = "foo-user-pass";

    protected Context context;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        context = getContextManager().create(ContextFactory.createContext(5000L), contextAdminCredentials);
    }

    /**
     * Tests whether the default module access is set
     */
    @Test
    public void testDefaultModuleAccess() throws Exception {
        // Check whether all new options have been cleared in disableAll()
        UserModuleAccess ret = new UserModuleAccess();
        ret.disableAll();
        ret.setWebmail(true);
        ret.setContacts(true);

        Class<?> clazz = ret.getClass();
        for (Method m : clazz.getMethods()) {
            String name = m.getName();
            if (!name.equals("getClass") && !name.equals("getPermissionBits") && !name.equals("getProperties") && !name.equals("getProperty") && (name.startsWith("is") || name.startsWith("get"))) {
                //System.out.println("*******" + name);
                boolean res = Autoboxing.b((Boolean) m.invoke(ret, (Object[]) null));
                if (name.endsWith("Webmail") || name.endsWith("Contacts") || name.endsWith("GlobalAddressBookDisabled")) {
                    assertTrue(name + " must return true", res);
                } else {
                    assertFalse(name + " must return false", res);
                }
            }
        }
    }

    /**
     * Tests the user creation
     */
    @Test
    public void testCreate() throws Exception {

        // create new user
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }
    }

    @Test
    public void testCreateUserLoadRemoteMailContentByDefault() throws Exception {
        // create new user
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        usr.setLoadRemoteMailContentByDefault(Boolean.TRUE);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // See if value was set
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        Assert.assertThat("Loading remote content should be allowed!", srv_loaded.isLoadRemoteMailContentByDefault(), is(Boolean.TRUE));
    }

    @Test
    public void testChangeUserLoadRemoteMailContentByDefault() throws Exception {
        // create new user
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        usr.setLoadRemoteMailContentByDefault(Boolean.TRUE);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // Change value
        createduser.setLoadRemoteMailContentByDefault(Boolean.FALSE);
        getUserManager().change(context, createduser, contextAdminCredentials);

        // Check value
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        Assert.assertThat("Loading remote content should be disabled!", srv_loaded.isLoadRemoteMailContentByDefault(), is(Boolean.FALSE));
    }

    @Test
    public void testChangeUserWithoutLoadRemoteMailContentByDefault() throws Exception {
        // create new user
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        usr.setLoadRemoteMailContentByDefault(Boolean.TRUE);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // Don't set value
        createduser.setLoadRemoteMailContentByDefault(null);
        getUserManager().change(context, createduser, contextAdminCredentials);

        // Value should be unchanged
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        Assert.assertThat("Loading remote content should still be enabled!", srv_loaded.isLoadRemoteMailContentByDefault(), is(Boolean.TRUE));
    }

    /**
     * Tests the user creation with context module access rights
     */
    @Test
    public void testCreateWithContextModuleAccessRights() throws Exception {

        // create new user
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }
    }

    /**
     * Tests the user creation with a named module access
     */
    @Test
    public void testCreateWithNamedModuleAccessRights() throws Exception {

        // create new user
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, NAMED_ACCESS_COMBINATION_BASIC, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }
    }

    /**
     * Test the user creation with only the mandatory fields set
     */
    @Test
    public void testCreateMandatory() throws Exception {

        // create new user
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            compareUserMandatory(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }
    }

    /**
     * Test user creation with erroneous drive folder mode
     */
    @Test
    public void testCreateUserWithWrongDriveFoldersMode() throws Exception {

        UserModuleAccess access = new UserModuleAccess();
        User user = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN);
        user.setDriveFolderMode("wrong");
        User createdUser = null;
        try {
            createdUser = getUserManager().create(context, user, access, contextAdminCredentials);
            fail("No exception was thrown");
        } catch (InvalidDataException e) {
            // Do nothing, we expect that
        }
        assertNull("User was created although an unsupported folder mode was set", createdUser);
    }

    /**
     * Tests user deletion
     */
    @Test(expected = NoSuchUserException.class)
    public void testDelete() throws Exception {
        // create new user
        UserModuleAccess access = new UserModuleAccess();

        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context, false);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // delete user
        getUserManager().delete(context, id(createduser), contextAdminCredentials);

        // try to load user, this MUST fail
        getUserManager().getData(context, createduser, contextAdminCredentials);
        fail("user not exists expected");
    }

    //@Test
    public void _disabledtestBug9027() throws Exception {

        // The same user cannot be created after if
        // was deleted due to infostore problems
        // Details: http://bugs.open-xchange.com/cgi-bin/bugzilla/show_bug.cgi?id=9027

        // get context to create an user

        // create new user

        UserModuleAccess access = new UserModuleAccess();

        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // delete user
        getUserManager().delete(context, createduser, contextAdminCredentials);

        // create same user again, this failes as described in the bug
        createduser = getUserManager().create(context, usr, access, contextAdminCredentials);
    }

    /**
     * Tests providing an empty array to the remote interface when deleting users
     */
    @Test(expected = InvalidDataException.class)
    public void testDeleteEmptyUserList() throws Exception {

        UserModuleAccess access = new UserModuleAccess();

        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // delete user
        getUserManager().delete(context, new User[0], contextAdminCredentials);

        // try to load user, this MUST fail
        getUserManager().getData(context, createduser, contextAdminCredentials);
        fail("user not exists expected");
    }

    /**
     * Tests getting all data for a user
     */
    @Test
    public void testGetData() throws Exception {

        UserModuleAccess access = new UserModuleAccess();

        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, createduser, contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }
    }

    /**
     * Tests getting all user data for a user by searching by the user's name
     */
    @Test
    public void testGetDataByName() throws Exception {
        UserModuleAccess access = new UserModuleAccess();

        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        User usernameuser = new User();
        usernameuser.setName(createduser.getName());

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, usernameuser, contextAdminCredentials);
        if (!createduser.getId().equals(srv_loaded.getId())) {
            fail("Expected to get user data");
        }
        //verify data
        AssertUtil.assertUser(createduser, srv_loaded);
    }

    /**
     * Tests if fix for bug 18866 still works.
     */
    @Test
    public void testPublicFolderEditableForUser() throws Exception {

        UserModuleAccess access = new UserModuleAccess();
        access.setPublicFolderEditable(true);
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser;
        try {
            createduser = getUserManager().create(context, usr, access, contextAdminCredentials);
            fail("Creating a user with permission to edit public folder permissions should be denied.");
        } catch (StorageException e) {
            // Everything is fine. Setting publicFolderEditable should be denied. See bugs 18866, 20369, 20635.
            access.setPublicFolderEditable(false);
            createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        }

        // now load user from server and check if data is correct, else fail
        UserModuleAccess moduleAccess = getUserManager().getModuleAccess(context, createduser, contextAdminCredentials);
        assertFalse("Editing public folder was allowed for a normal user.", moduleAccess.isPublicFolderEditable());

        moduleAccess.setPublicFolderEditable(true);
        try {
            getUserManager().changeModuleAccess(context, usr, moduleAccess, contextAdminCredentials);
            fail("Setting publicfoldereditable to true was not denied by admin.");
        } catch (StorageException e) {
            // This is expected.
        }
    }

    /**
     * Tests if fix for bug 18866 still works.
     */
    @Test
    public void testPublicFolderEditableForAdmin() throws Exception {

        User usr = new User();
        // Administrator gets always principal identifier 2. The group users gets principal identifier 1.
        usr.setId(Integer.valueOf(2));

        // enable and test it.
        UserModuleAccess access = getUserManager().getModuleAccess(context, usr, contextAdminCredentials);
        access.setPublicFolderEditable(true);
        getUserManager().changeModuleAccess(context, usr, access, contextAdminCredentials);
        access = getUserManager().getModuleAccess(context, usr, contextAdminCredentials);
        assertTrue("Flag publicfoldereditable does not survice roundtrip for context administrator.", access.isPublicFolderEditable());

        access.setPublicFolderEditable(false);
        getUserManager().changeModuleAccess(context, usr, access, contextAdminCredentials);
        access = getUserManager().getModuleAccess(context, usr, contextAdminCredentials);
        assertFalse("Flag publicfoldereditable does not survice roundtrip for context administrator.", access.isPublicFolderEditable());
    }

    /**
     * Tests getting the user data via the user name and user credentials
     */
    @Test
    public void testGetDataByNameWithUserAuth() throws Exception {

        UserModuleAccess access = new UserModuleAccess();

        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        User usernameuser = new User();
        usernameuser.setName(createduser.getName());

        Credentials usercred = new Credentials(usr.getName(), usr.getPassword());
        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, usernameuser, usercred);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }
    }

    /**
     * Tests getting the data by user id
     */
    @Test
    public void testGetDataByID() throws Exception {
        UserModuleAccess access = new UserModuleAccess();

        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        User iduser = new User();
        iduser.setId(createduser.getId());

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, iduser, contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }
    }

    /**
     * Tests getting the module access of a user
     */
    @Test
    public void testGetModuleAccess() throws Exception {
        UserModuleAccess client_access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, client_access, contextAdminCredentials);

        // get module access
        UserModuleAccess srv_response = getUserManager().getModuleAccess(context, createduser, contextAdminCredentials);

        // test if module access was set correctly
        AssertUtil.compareUserAccess(client_access, srv_response);
    }

    /**
     * Tests changing the user module access
     */
    @Test
    public void testChangeModuleAccess() throws Exception {
        UserModuleAccess client_access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, client_access, contextAdminCredentials);

        // get module access
        UserModuleAccess srv_response = getUserManager().getModuleAccess(context, createduser, contextAdminCredentials);

        // test if module access was set correctly
        AssertUtil.compareUserAccess(client_access, srv_response);

        // now change server loaded module access and submit changes to the server
        srv_response.setCalendar(!srv_response.getCalendar());
        srv_response.setContacts(!srv_response.getContacts());
        srv_response.setDelegateTask(!srv_response.getDelegateTask());
        srv_response.setEditPublicFolders(!srv_response.getEditPublicFolders());
        srv_response.setIcal(!srv_response.getIcal());
        srv_response.setInfostore(!srv_response.getInfostore());
        srv_response.setReadCreateSharedFolders(!srv_response.getReadCreateSharedFolders());
        srv_response.setSyncml(!srv_response.getSyncml());
        srv_response.setTasks(!srv_response.getTasks());
        srv_response.setVcard(!srv_response.getVcard());
        srv_response.setWebdav(!srv_response.getWebdav());
        srv_response.setWebdavXml(!srv_response.getWebdavXml());
        srv_response.setWebmail(!srv_response.getWebmail());

        // submit changes
        getUserManager().changeModuleAccess(context, createduser, srv_response, contextAdminCredentials);

        // load again and verify
        UserModuleAccess srv_response_changed = getUserManager().getModuleAccess(context, createduser, contextAdminCredentials);

        // test if module access was set correctly
        AssertUtil.compareUserAccess(srv_response, srv_response_changed);

    }

    /**
     * Tests getting a list of users in a context
     */
    @Test
    public void testList() throws Exception {
        UserModuleAccess client_access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, client_access, contextAdminCredentials);

        User[] srv_response = getUserManager().search(context, "*", contextAdminCredentials);

        assertTrue("Expected list size > 0 ", srv_response.length > 0);

        boolean founduser = false;
        for (User element : srv_response) {
            if (element.getId().intValue() == createduser.getId().intValue()) {
                founduser = true;
            }
        }

        assertTrue("Expected to find added user in user list", founduser);
    }

    /**
     * Tests listing users that have a dedicated filestore
     */
    @Test
    public void testListUsersWithOwnFilestore() throws Exception {
        Filestore fs = null;

        UserModuleAccess client_access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context, false);
        User createduser = getUserManager().create(context, usr, client_access, contextAdminCredentials);
        //test if filestore already exists
        Filestore[] filestores = getFilestoreManager().search("file:///", true);
        if (filestores != null && filestores.length != 0) {
            if (filestores.length != 1) {
                fail("Unexpected failure. Multiple filestores already exists.");
            } else {
                fs = filestores[0];
            }
        }

        if (fs == null) {
            //create new filestore
            fs = new Filestore();
            fs.setMaxContexts(new Integer(1000));
            fs.setSize(new Long(1024));
            fs.setUrl("file:///");

            fs = getFilestoreManager().register(fs);
        }
        //move user to new filestore
        getUserManager().moveFromContextToUserFilestore(context, usr, fs, 10, contextAdminCredentials);
        Thread.sleep(500); //wait for move

        User[] srv_response = getUserManager().listUsersWithOwnFilestore(context, fs.getId(), contextAdminCredentials);

        assertTrue("Expected list size > 0 ", srv_response.length > 0);

        boolean founduser = false;
        for (User element : srv_response) {
            if (element.getId().intValue() == createduser.getId().intValue()) {
                founduser = true;
            }
        }

        assertTrue("Expected to find added user in user list", founduser);
    }

    /**
     * Tests listing all users in a context
     */
    @Test
    public void testListAll() throws Exception {
        UserModuleAccess client_access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, client_access, contextAdminCredentials);

        User[] srv_response = getUserManager().listAll(context, contextAdminCredentials);

        assertTrue("Expected list size > 0 ", srv_response.length > 0);

        boolean founduser = false;
        for (User element : srv_response) {
            if (element.getId().intValue() == createduser.getId().intValue()) {
                founduser = true;
            }
        }

        assertTrue("Expected to find added user in user list", founduser);
    }

    /**
     * Tests changing/updating a user
     */
    @Test
    public void testChange() throws Exception {
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }

        // now change data
        srv_loaded = createChangeUserData(srv_loaded);
        // submit changes
        getUserManager().change(context, srv_loaded, contextAdminCredentials);

        // load again
        User user_changed_loaded = getUserManager().getData(context, id(srv_loaded), contextAdminCredentials);
        // set Username to old value for verification
        srv_loaded.setName(createduser.getName());
        // remove deleted dynamic attribute for verification
        srv_loaded.getUserAttributes().get("com.openexchange.test").remove("deleteMe");
        if (srv_loaded.getId().equals(user_changed_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(srv_loaded, user_changed_loaded);
        } else {
            fail("Expected to get correct changed user data");
        }
    }

    /**
     * Tests changing the alias of a user
     */
    @Test
    public void testChangeAlias() throws Exception {
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }

        // now change data
        String alias = generateRandomAlias(44);
        srv_loaded = changeUserAlias(srv_loaded, alias);

        // submit changes
        getUserManager().change(context, srv_loaded, contextAdminCredentials);

        // load again
        User user_changed_loaded = getUserManager().getData(context, id(srv_loaded), contextAdminCredentials);

        // remove deleted dynamic attribute for verification
        srv_loaded.getUserAttributes().get("com.openexchange.test").remove("deleteMe");
        if (srv_loaded.getId().equals(user_changed_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(srv_loaded, user_changed_loaded);
        } else {
            fail("Expected to get correct changed user data");
        }
    }

    /**
     * Tests to create a user with an image and to change it
     *
     * @throws Exception
     */
    @Test
    public void testCreateChangeImage() throws Exception {
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        final byte[] originalImage = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABAQMAAAAl21bKAAAAA1BMVEUVAPQHR2pEAAAAAXRSTlPM0jRW/QAAAApJREFUeJxjYgAAAAYAAzY3fKgAAAAASUVORK5CYII=");
        usr.setImage1(originalImage);
        usr.setImage1ContentType("application/png");
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);
        assertArrayEquals(originalImage, createduser.getImage1());
        assertNotNull(createduser.getImage1());

        //set the user's image
        final byte[] testImage = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABAQMAAAAl21bKAAAAA1BMVEX/GBiIlQk4AAAAAXRSTlPM0jRW/QAAAApJREFUeJxjYgAAAAYAAzY3fKgAAAAASUVORK5CYII=");
        createduser.setImage1(testImage);
        createduser.setImage1ContentType("application/png");
        getUserManager().change(context, createduser, contextAdminCredentials);

        //Check if it was set
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        assertArrayEquals(testImage, srv_loaded.getImage1());
        assertNotNull(srv_loaded.getImage1());
    }


    /**
     * Tests changing the alias of a user to one with a very long name
     */
    @Test
    public void testChangeAliasTooLong() throws Exception {
        // Try to change alias with too long name (Bug 52763)
        // get context to create an user
        // create new user
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }

        // now change data
        String alias = generateRandomAlias(4096);
        srv_loaded = changeUserAlias(srv_loaded, alias);

        // submit changes, should throw StorageException
        boolean canary = true;
        try {
            getUserManager().change(context, srv_loaded, contextAdminCredentials);
        } catch (StorageException e) {
            canary = false;
        }

        // Check if exception was thrown
        if (canary) {
            fail("Expected an Storage Exception");
        }

        // remove flawed alias
        srv_loaded.removeAlias(alias);

        // no assertion needed since no data changed on server
        // check if you can still change alias (Bug 52763)
        alias = generateRandomAlias(44);
        srv_loaded = changeUserAlias(srv_loaded, alias);

        // submit changes
        getUserManager().change(context, srv_loaded, contextAdminCredentials);

        // load again
        User user_changed_loaded = getUserManager().getData(context, id(srv_loaded), contextAdminCredentials);

        // remove deleted dynamic attribute for verification
        srv_loaded.getUserAttributes().get("com.openexchange.test").remove("deleteMe");
        if (srv_loaded.getId().equals(user_changed_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(srv_loaded, user_changed_loaded);
        } else {
            fail("Expected to get correct changed user data");
        }
    }

    /**
     * Tests setting a single user attribute to <code>null</code>
     */
    @Test
    public void testChangeSingleAttributeNull() throws Exception {
        // set single values to null in the user object and then call change, what happens?
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }

        HashSet<String> notallowed = getNotNullableFields();

        // loop through methods and change each attribute per single call and load and compare
        MethodMapObject[] meth_objects = getSetableAttributeMethods(usr.getClass());

        for (MethodMapObject map_obj : meth_objects) {
            if (notallowed.contains(map_obj.getMethodName())) {
                continue;
            }
            User tmp_usr = new User();
            if (!map_obj.getMethodName().equals("setId")) {
                // resolv by name
                tmp_usr.setId(srv_loaded.getId());
            } else {
                // server must resolv by name
                tmp_usr.setName(srv_loaded.getName());
            }

            if (map_obj.getMethodParameterType().equals("java.lang.Integer")) {
                map_obj.getSetter().invoke(tmp_usr, new Object[] { Integer.valueOf(-1) });

                System.out.println("Setting -1 via " + map_obj.getMethodName() + " -> " + map_obj.getGetter().invoke(tmp_usr));
            } else if (map_obj.getMethodParameterType().equals("java.lang.Boolean")) {
                map_obj.getSetter().invoke(tmp_usr, new Object[] { Boolean.FALSE });

                System.out.println("Setting false via " + map_obj.getMethodName() + " -> " + map_obj.getGetter().invoke(tmp_usr));
            } else {
                map_obj.getSetter().invoke(tmp_usr, new Object[] { null });

                System.out.println("Setting null via " + map_obj.getMethodName() + " -> " + map_obj.getGetter().invoke(tmp_usr));
            }

            // submit changes
            getUserManager().change(context, tmp_usr, contextAdminCredentials);

            // load from server and compare the single changed value
            User user_single_change_loaded = getUserManager().getData(context, id(srv_loaded), contextAdminCredentials);

            if (!notallowed.contains(map_obj.getMethodName())) {
                // local and remote must be null
                assertEquals(map_obj.getGetter().getName().substring(3) + " not equal", map_obj.getGetter().invoke(tmp_usr), map_obj.getGetter().invoke(user_single_change_loaded));
            } else {
                // we wanted to change a attribute which cannot be changed by
                // server, so we check for not null
                assertNotNull(map_obj.getMethodName() + " cannot be null", map_obj.getGetter().invoke(user_single_change_loaded));
            }
        }
    }

    /**
     * Tests changing all user attributes to <code>null</code>
     */
    @Test(expected = InvalidDataException.class)
    public void testChangeAllAttributesNull() throws Exception {
        // set all values to null in the user object and then call change, what
        // happens?
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }

        // loop through methods and change each attribute per single call and load and compare
        MethodMapObject[] meth_objects = getSetableAttributeMethods(usr.getClass());
        User tmp_usr = new User();
        for (MethodMapObject map_obj : meth_objects) {
            if (!map_obj.getMethodName().equals("setId")) {
                // resolv by name
                tmp_usr.setId(srv_loaded.getId());
            } else {
                // server must resolv by name
                tmp_usr.setName(srv_loaded.getName());
            }
            if (!map_obj.getMethodName().equals("setUserAttribute")) {
                map_obj.getSetter().invoke(tmp_usr, new Object[] { null });
                System.out.println("Setting null via " + map_obj.getMethodName() + " -> " + map_obj.getGetter().invoke(tmp_usr));
            }

        }

        // submit changes
        getUserManager().change(context, tmp_usr, contextAdminCredentials);

        // load from server and compare the single changed value
        User user_single_change_loaded = getUserManager().getData(context, id(srv_loaded), contextAdminCredentials);

        // TODO
        // special compare must be written that checks for special attributes like username etc which cannot be null
        AssertUtil.compareUserSpecialForNulledAttributes(tmp_usr, user_single_change_loaded);
    }

    /**
     * Tests changing all allowed user attributes to <code>null</code>
     */
    @Test
    public void testChangeAllAllowedAttributesNull() throws Exception {
        // set all values to null in the user object and then call change, what
        // happens?
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }

        HashSet<String> notallowed = getNotNullableFields();

        // loop through methods and change each attribute per single call and load and compare
        MethodMapObject[] meth_objects = getSetableAttributeMethods(usr.getClass());
        User tmp_usr = (User) createduser.clone();
        for (MethodMapObject map_obj : meth_objects) {
            if (!map_obj.getMethodName().equals("setId")) {
                // resolv by name
                tmp_usr.setId(srv_loaded.getId());
            } else {
                // server must resolv by name
                tmp_usr.setName(srv_loaded.getName());
            }

            if (notallowed.contains(map_obj.methodName)) {
                continue;
            }
            map_obj.getSetter().invoke(tmp_usr, new Object[] { null });

            System.out.println("Setting null via " + map_obj.getMethodName() + " -> " + map_obj.getGetter().invoke(tmp_usr));
        }

        // submit changes
        getUserManager().change(context, tmp_usr, contextAdminCredentials);

        // load from server and compare the single changed value
        User user_single_change_loaded = getUserManager().getData(context, id(srv_loaded), contextAdminCredentials);

        // TODO
        // special compare must be written that checks for special attributes like username etc which cannot be null
        AssertUtil.compareUserSpecialForNulledAttributes(tmp_usr, user_single_change_loaded);
    }

    /**
     * Tests changing one user attribute per call
     */
    @Test
    public void testChangeSingleAttribute() throws Exception {
        // change only 1 attribute of user object per call
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }

        // which attributes should not be edited in a single change call
        // because of trouble when server needs combined attribute changed like mail attributes
        // or server does not support it
        HashSet<String> notallowed = new HashSet<String>();

        // # mail attribs must be combined in a change #
        notallowed.add("setEmail1");
        notallowed.add("setFolderTree");
        notallowed.add("setPrimaryEmail");
        notallowed.add("setDefaultSenderAddress");
        notallowed.add("setMail_folder_drafts_name");
        notallowed.add("setMail_folder_sent_name");
        notallowed.add("setMail_folder_spam_name");
        notallowed.add("setMail_folder_trash_name");
        notallowed.add("setMail_folder_confirmed_ham_name");
        notallowed.add("setMail_folder_confirmed_spam_name");
        // #                                                                     #

        notallowed.add("setId");// we cannot change the id of a user, is a mandatory field for a change
        notallowed.add("setPassword");// server password is always different(crypted)
        notallowed.add("setPasswordMech");// server password is always different(crypted)
        notallowed.add("setName");// server does not support username change
        notallowed.add("setSalt");// salt will be generated server side

        notallowed.add("setFilestoreId");
        notallowed.add("setFilestoreOwner");
        notallowed.add("setFilestore_name");

        notallowed.add("setPrimaryAccountName");
        notallowed.add("setDriveFolderMode");
        notallowed.add("setImage1ContentType");

        // loop through methods and change each attribute per single call and load and compare
        MethodMapObject[] meth_objects = getSetableAttributeMethods(usr.getClass());

        for (MethodMapObject map_obj : meth_objects) {
            if (!notallowed.contains(map_obj.getMethodName())) {
                User tmp_usr = new User(Autoboxing.i(srv_loaded.getId()));
                if (map_obj.getMethodParameterType().equalsIgnoreCase("java.lang.String") && map_obj.getGetter().getParameterTypes().length == 0) {
                    String oldvalue = (String) map_obj.getGetter().invoke(srv_loaded);
                    if (map_obj.getMethodName().equals("setLanguage")) {
                        map_obj.getSetter().invoke(tmp_usr, "fr_FR");
                    } else if (map_obj.getMethodName().equals("setTimezone")) {
                        map_obj.getSetter().invoke(tmp_usr, "Asia/Taipei");
                    } else if (map_obj.getMethodName().toLowerCase().contains("mail")) {
                        map_obj.getSetter().invoke(tmp_usr, getChangedEmailAddress(oldvalue, "_singlechange"));
                    } else {
                        map_obj.getSetter().invoke(tmp_usr, oldvalue == null ? "singlechanged" : oldvalue + "-singlechange");
                    }
                    //System.out.println("Setting String via "+map_obj.getMethodName() +" -> "+map_obj.getGetter().invoke(tmp_usr));
                }
                if (map_obj.getMethodParameterType().equalsIgnoreCase("java.lang.Integer")) {
                    Integer oldvalue = (Integer) map_obj.getGetter().invoke(srv_loaded);
                    map_obj.getSetter().invoke(tmp_usr, Autoboxing.I(oldvalue.intValue() + 1));
                    //System.out.println("Setting Integer via "+map_obj.getMethodName() +" -> "+map_obj.getGetter().invoke(tmp_usr));
                }
                if (map_obj.getMethodParameterType().equalsIgnoreCase("java.lang.Boolean")) {
                    Boolean oldvalue = (Boolean) map_obj.getGetter().invoke(srv_loaded);
                    map_obj.getSetter().invoke(tmp_usr, Autoboxing.B(!oldvalue.booleanValue()));
                    //System.out.println("Setting Boolean via "+map_obj.getMethodName() +" -> "+map_obj.getGetter().invoke(tmp_usr));
                }
                if (map_obj.getMethodParameterType().equalsIgnoreCase("java.util.Date")) {
                    Date oldvalue = (Date) map_obj.getGetter().invoke(srv_loaded);
                    // set date to current +1 day
                    map_obj.getSetter().invoke(tmp_usr, new Date(oldvalue.getTime() + (24 * 60 * 60 * 1000)));
                    //System.out.println("Setting Date via "+map_obj.getMethodName() +" -> "+map_obj.getGetter().invoke(tmp_usr));
                }

                //  submit changes
                getUserManager().change(context, tmp_usr, contextAdminCredentials);
                // load from server and compare the single changed value
                User user_single_change_loaded = getUserManager().getData(context, id(srv_loaded), contextAdminCredentials);

                // compare both string values , server and local copy must be same, else, the change was unsuccessful
                if (map_obj.getGetter().getParameterTypes().length == 0) {
                    Object expected = map_obj.getGetter().invoke(tmp_usr);
                    Object actual = map_obj.getGetter().invoke(user_single_change_loaded);
                    assertEquals(map_obj.getGetter().getName().substring(3) + " not equal " + expected.getClass().getName() + " " + actual.getClass().getName(), expected, actual);
                }
            }
        }
    }

    /**
     * Tests the following scenario:
     * <ol>
     * <li>create user</li>
     * <li>check if user was created correctly</li>
     * <li>change user data but send NO data</li>
     * <li>load user again from server and compare with 1st created user</li>
     * </ol>
     * Then tests a change with no data set only id set and compare the data afterwards.
     */
    @Test
    public void testChangeWithEmptyUserIdentifiedByID() throws Exception {
        // STEP 1
        // create new user
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // STEP 2
        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }

        // STEP 3
        User emptyusr = new User(Autoboxing.i(srv_loaded.getId()));
        getUserManager().change(context, emptyusr, contextAdminCredentials);

        // STEP 4
        // now load user from server and check if data is correct, else fail
        User srv_loaded2 = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded2.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }

    }

    /**
     * Tests a change with no data set ONLY username set and compare the data afterwards
     */
    @Test
    public void testChangeWithEmptyUserIdentifiedByName() throws Exception {
        // STEP 1
        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // STEP 2
        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }

        // STEP 3
        User emptyusr = new User();
        emptyusr.setName(srv_loaded.getName());
        getUserManager().change(context, emptyusr, contextAdminCredentials);

        // STEP 4
        // now load user from server and check if data is correct, else fail
        User srv_loaded2 = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded2.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }

    }

    /**
     * Tests a change with data set but identified by username and compare the data afterwards
     */
    @Test
    public void testChangeIdentifiedByName() throws Exception {
        // STEP 1

        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // STEP 2
        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }

        // STEP 3
        User emptyusr = createChangeUserData(srv_loaded);
        emptyusr.setId(null);// reset id, server must ident the user by username
        getUserManager().change(context, emptyusr, contextAdminCredentials);

        // STEP 4
        // now load user from server and check if data is correct, else fail
        User srv_loaded2 = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded2.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }

    }

    /**
     * Tests a change with data set but identified by id and compare the data afterwards
     */
    @Test
    public void testChangeIdentifiedByID() throws Exception {
        // STEP 1

        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // STEP 2
        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }

        // STEP 3
        User emptyusr = createChangeUserData(srv_loaded);
        // reset username, server must ident the user by id
        // This is a dirty trick to circumvent the setter method. Don't do this at home ;-)
        Field field = emptyusr.getClass().getDeclaredField("name");
        field.setAccessible(true);
        field.set(emptyusr, null);
        getUserManager().change(context, emptyusr, contextAdminCredentials);

        // STEP 4
        // now load user from server and check if data is correct, else fail
        User srv_loaded2 = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded2.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data for added user");
        }

    }

    /**
     * Tests a change without identifier and name
     */
    @Test(expected = InvalidDataException.class)
    public void testChangeWithoutIdAndName() throws Exception {

        UserModuleAccess access = new UserModuleAccess();
        User usr = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context);
        User createduser = getUserManager().create(context, usr, access, contextAdminCredentials);

        // now load user from server and check if data is correct, else fail
        User srv_loaded = getUserManager().getData(context, id(createduser), contextAdminCredentials);
        if (createduser.getId().equals(srv_loaded.getId())) {
            //verify data
            AssertUtil.assertUser(createduser, srv_loaded);
        } else {
            fail("Expected to get user data");
        }

        // now change data
        srv_loaded = createChangeUserData(srv_loaded);
        srv_loaded.setId(null);
        srv_loaded.setName(null);
        // submit changes
        getUserManager().change(context, srv_loaded, contextAdminCredentials);
    }

    /**
     * This test is used to check how the change method deals with changing values which are null before changing
     */
    @Test
    public void testChangeNullFields() throws Exception {

        OXLoginInterface oxl = (OXLoginInterface) Naming.lookup(getRMIHostUrl() + OXLoginInterface.RMI_NAME);
        // Here we get the user object of the admin from the database
        // The admin has no company set by default, so we can test here, how a change work on field's which
        // aren't set by default
        User usr = oxl.login2User(context, contextAdminCredentials);
        // passwordmech is set by login2user so we need to null it here for the change test
        // not to fail
        usr.setPasswordMech(null);
        OXUserInterface user = (OXUserInterface) Naming.lookup(getRMIHostUrl() + OXUserInterface.RMI_NAME);
        usr.setNickname("test");

        usr.setCompany("test");
        usr.setSur_name("test");
        usr.setEmail1(usr.getPrimaryEmail());
        // Store username to be able to restore it after change
        String username = usr.getName();
        // This is a dirty trick to circumvent the setter method. Don't do this at home ;-)
        Field field = usr.getClass().getDeclaredField("name");
        field.setAccessible(true);
        field.set(usr, null);
        System.out.println(usr.isCompanyset());
        usr.setFilestoreId(null);
        usr.setMaxQuota(null);
        user.change(context, usr, contextAdminCredentials);
        usr.setName(username);
        User usr2 = oxl.login2User(context, contextAdminCredentials);
        AssertUtil.assertUser(usr, usr2);
    }

    /**
     * Tests whether a user exists
     */
    @Test
    public void testExists() throws Exception {
        User exists = UserFactory.createUser(VALID_CHAR_TESTUSER + System.currentTimeMillis(), pass, TEST_DOMAIN, context, false);
        User notexists = new User();
        notexists.setName("Rumpelstilz");
        User createduser = getUserManager().create(context, exists, contextAdminCredentials);

        boolean existingexists = false;
        try {
            existingexists = getUserManager().exists(context, exists, contextAdminCredentials);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // delete user
        getUserManager().delete(context, createduser, contextAdminCredentials);

        try {
            assertFalse("nonexisting user must not exist", getUserManager().exists(context, notexists, contextAdminCredentials));
        } catch (Exception e) {
            e.printStackTrace();
        }

        assertTrue("created user does not exist", existingexists);
    }

    ///////////////////////////// HELPERS TO MOVE ////////////////////////////

    public User addUser(Context ctx, User usr, UserModuleAccess access) throws Exception {
        // create new user
        return getUserManager().create(ctx, usr, access, contextAdminCredentials);
    }

    private String generateRandomAlias(long length) {
        String mail = "@test.org";
        StringBuffer buf = new StringBuffer();
        Random r = new Random();
        for (long l = 0; l <= (length - mail.length()); l++) {
            buf.append((char) (r.nextInt(26) + 'a'));
        }
        buf.append(mail);
        return buf.toString();
    }

    private User changeUserAlias(User usr, String alias) throws CloneNotSupportedException {
        User retval = (User) usr.clone();

        // Change alias and remove attributes to meet later compare action
        retval.addAlias(alias);
        retval.setFilestoreId(null);
        retval.setPasswordMech(null);
        retval.setUserAttribute("com.openexchange.test", "simpleValue", usr.getUserAttribute("com.openexchange.test", "simpleValue") + change_suffix);
        retval.setUserAttribute("com.openexchange.test", "newValue", change_suffix);
        retval.setUserAttribute("com.openexchange.test", "deleteMe", null);

        return retval;
    }

    private User createChangeUserData(User usr) throws CloneNotSupportedException, URISyntaxException {

        // change all fields of the user

        User retval = (User) usr.clone();
        retval.setFilestoreId(null);
        //retval.setName(null); // INFO: Commented because the server does not throw any exception if username is sent!
        retval.setPasswordMech(null);
        retval.setMailenabled(Autoboxing.B(!usr.getMailenabled().booleanValue()));

        // do not change primary mail, that's forbidden per default, see
        //PRIMARY_MAIL_UNCHANGEABLE in User.properties
        // retval.setPrimaryEmail(usr.getPrimaryEmail()+change_suffix);
        //retval.setEmail1(usr.getEmail1()+change_suffix);
        //retval.setDefaultSenderAddress(usr.getPrimaryEmail()+change_suffix);
        retval.setEmail2(getChangedEmailAddress(usr.getEmail2(), change_suffix));
        retval.setEmail3(getChangedEmailAddress(usr.getEmail3(), change_suffix));

        retval.setDisplay_name(usr.getDisplay_name() + change_suffix);
        retval.setGiven_name(usr.getGiven_name() + change_suffix);
        retval.setSur_name(usr.getSur_name() + change_suffix);
        retval.setLanguage("en_US");
        // new for testing

        HashSet<String> aliases = usr.getAliases();
        HashSet<String> lAliases = new HashSet<String>();
        for (String element : aliases) {
            lAliases.add(getChangedEmailAddress(element, change_suffix));
        }
        lAliases.add(usr.getPrimaryEmail());

        retval.setAliases(lAliases);

        // set the dates to the actual + 1 day
        retval.setBirthday(new Date(usr.getBirthday().getTime() + (24 * 60 * 60 * 1000)));
        retval.setAnniversary(new Date(usr.getAnniversary().getTime() + (24 * 60 * 60 * 1000)));
        retval.setAssistant_name(usr.getAssistant_name() + change_suffix);
        retval.setBranches(usr.getBranches() + change_suffix);
        retval.setBusiness_category(usr.getBusiness_category() + change_suffix);
        retval.setCity_business(usr.getCity_business() + change_suffix);
        retval.setCountry_business(usr.getCountry_business() + change_suffix);
        retval.setPostal_code_business(usr.getPostal_code_business() + change_suffix);
        retval.setState_business(usr.getState_business() + change_suffix);
        retval.setStreet_business(usr.getStreet_business() + change_suffix);
        retval.setTelephone_callback(usr.getTelephone_callback() + change_suffix);
        retval.setCity_home(usr.getCity_home() + change_suffix);
        retval.setCommercial_register(usr.getCommercial_register() + change_suffix);
        retval.setCompany(usr.getCompany() + change_suffix);
        retval.setCountry_home(usr.getCountry_home() + change_suffix);
        retval.setDepartment(usr.getDepartment() + change_suffix);
        retval.setEmployeeType(usr.getEmployeeType() + change_suffix);
        retval.setFax_business(usr.getFax_business() + change_suffix);
        retval.setFax_home(usr.getFax_home() + change_suffix);
        retval.setFax_other(usr.getFax_other() + change_suffix);
        URI uri = new URI(usr.getImapServerString());
        retval.setImapServer(new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost() + change_suffix, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString());
        retval.setInstant_messenger1(usr.getInstant_messenger1() + change_suffix);
        retval.setInstant_messenger2(usr.getInstant_messenger2() + change_suffix);
        retval.setTelephone_ip(usr.getTelephone_ip() + change_suffix);
        retval.setTelephone_isdn(usr.getTelephone_isdn() + change_suffix);
        retval.setMail_folder_drafts_name(usr.getMail_folder_drafts_name() + change_suffix);
        retval.setMail_folder_sent_name(usr.getMail_folder_sent_name() + change_suffix);
        retval.setMail_folder_spam_name(usr.getMail_folder_spam_name() + change_suffix);
        retval.setMail_folder_trash_name(usr.getMail_folder_trash_name() + change_suffix);
        retval.setMail_folder_archive_full_name(usr.getMail_folder_archive_full_name() + change_suffix);
        retval.setManager_name(usr.getManager_name() + change_suffix);
        retval.setMarital_status(usr.getMarital_status() + change_suffix);
        retval.setCellular_telephone1(usr.getCellular_telephone1() + change_suffix);
        retval.setCellular_telephone2(usr.getCellular_telephone2() + change_suffix);
        retval.setInfo(usr.getInfo() + change_suffix);
        retval.setNickname(usr.getNickname() + change_suffix);
        retval.setNote(usr.getNote() + change_suffix);
        retval.setNumber_of_children(usr.getNumber_of_children() + change_suffix);
        retval.setNumber_of_employee(usr.getNumber_of_employee() + change_suffix);
        retval.setTelephone_pager(usr.getTelephone_pager() + change_suffix);
        retval.setPassword_expired(Autoboxing.B(!usr.getPassword_expired().booleanValue()));
        retval.setTelephone_assistant(usr.getTelephone_assistant() + change_suffix);
        retval.setTelephone_business1(usr.getTelephone_business1() + change_suffix);
        retval.setTelephone_business2(usr.getTelephone_business2() + change_suffix);
        retval.setTelephone_car(usr.getTelephone_car() + change_suffix);
        retval.setTelephone_company(usr.getTelephone_company() + change_suffix);
        retval.setTelephone_home1(usr.getTelephone_home1() + change_suffix);
        retval.setTelephone_home2(usr.getTelephone_home2() + change_suffix);
        retval.setTelephone_other(usr.getTelephone_other() + change_suffix);
        retval.setPosition(usr.getPosition() + change_suffix);
        retval.setPostal_code_home(usr.getPostal_code_home() + change_suffix);
        retval.setProfession(usr.getProfession() + change_suffix);
        retval.setTelephone_radio(usr.getTelephone_radio() + change_suffix);
        retval.setRoom_number(usr.getRoom_number() + change_suffix);
        retval.setSales_volume(usr.getSales_volume() + change_suffix);
        retval.setCity_other(usr.getCity_other() + change_suffix);
        retval.setCountry_other(usr.getCountry_other() + change_suffix);
        retval.setMiddle_name(usr.getMiddle_name() + change_suffix);
        retval.setPostal_code_other(usr.getPostal_code_other() + change_suffix);
        retval.setState_other(usr.getState_other() + change_suffix);
        retval.setStreet_other(usr.getStreet_other() + change_suffix);
        uri = new URI(usr.getSmtpServerString());
        retval.setSmtpServer(new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost() + change_suffix, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString());
        retval.setSpouse_name(usr.getSpouse_name() + change_suffix);
        retval.setState_home(usr.getState_home() + change_suffix);
        retval.setStreet_home(usr.getStreet_home() + change_suffix);
        retval.setSuffix(usr.getSuffix() + change_suffix);
        retval.setTax_id(usr.getTax_id() + change_suffix);
        retval.setTelephone_telex(usr.getTelephone_telex() + change_suffix);
        retval.setTimezone(usr.getTimezone());
        retval.setTitle(usr.getTitle() + change_suffix);
        retval.setTelephone_ttytdd(usr.getTelephone_ttytdd() + change_suffix);
        retval.setUrl(usr.getUrl() + change_suffix);
        retval.setUserfield01(usr.getUserfield01() + change_suffix);
        retval.setUserfield02(usr.getUserfield02() + change_suffix);
        retval.setUserfield03(usr.getUserfield03() + change_suffix);
        retval.setUserfield04(usr.getUserfield04() + change_suffix);
        retval.setUserfield05(usr.getUserfield05() + change_suffix);
        retval.setUserfield06(usr.getUserfield06() + change_suffix);
        retval.setUserfield07(usr.getUserfield07() + change_suffix);
        retval.setUserfield08(usr.getUserfield08() + change_suffix);
        retval.setUserfield09(usr.getUserfield09() + change_suffix);
        retval.setUserfield10(usr.getUserfield10() + change_suffix);
        retval.setUserfield11(usr.getUserfield11() + change_suffix);
        retval.setUserfield12(usr.getUserfield12() + change_suffix);
        retval.setUserfield13(usr.getUserfield13() + change_suffix);
        retval.setUserfield14(usr.getUserfield14() + change_suffix);
        retval.setUserfield15(usr.getUserfield15() + change_suffix);
        retval.setUserfield16(usr.getUserfield16() + change_suffix);
        retval.setUserfield17(usr.getUserfield17() + change_suffix);
        retval.setUserfield18(usr.getUserfield18() + change_suffix);
        retval.setUserfield19(usr.getUserfield19() + change_suffix);
        retval.setUserfield20(usr.getUserfield20() + change_suffix);

        retval.setUserAttribute("com.openexchange.test", "simpleValue", usr.getUserAttribute("com.openexchange.test", "simpleValue") + change_suffix);
        retval.setUserAttribute("com.openexchange.test", "newValue", change_suffix);
        retval.setUserAttribute("com.openexchange.test", "deleteMe", null);
        // Remove value
        return retval;
    }

    ////////////////////////////// HELPERS //////////////////////////////////

    /**
     * Helper method to create a new {@link User} and set the id
     *
     * @param createdUser The created user from which to copy the id
     * @return The newly created user
     */
    private User id(User createdUser) {
        User user = new User();
        user.setId(createdUser.getId());
        return user;
    }

    /**
     * Compares mandatory fields of {@link User} A with {@link User} B
     *
     * @param a {@link User} A
     * @param b {@link User} B
     */
    private void compareUserMandatory(User a, User b) {
        System.out.println("USERA" + a.toString());
        System.out.println("USERB" + b.toString());

        assertEquals("username not equal", a.getName(), b.getName());
        assertEquals("enabled not equal", a.getMailenabled(), b.getMailenabled());
        assertEquals("primaryemail not equal", a.getPrimaryEmail(), b.getPrimaryEmail());
        assertEquals("display name not equal", a.getDisplay_name(), b.getDisplay_name());
        assertEquals("firtname not equal", a.getGiven_name(), b.getGiven_name());
    }

    /**
     * Returns a {@link HashSet} with all non-nullable user fields
     *
     * @return A {@link HashSet} with the fields
     */
    private HashSet<String> getNotNullableFields() {
        // TODO: Convert to enum
        HashSet<String> notallowed = new HashSet<String>();
        notallowed.add("setEmail1");
        notallowed.add("setFolderTree");
        notallowed.add("setDefaultSenderAddress");
        notallowed.add("setId");
        String[] mandatoryMembersCreate = new User().getMandatoryMembersCreate();
        for (String name : mandatoryMembersCreate) {
            StringBuilder sb = new StringBuilder("set");
            sb.append(name.substring(0, 1).toUpperCase());
            sb.append(name.substring(1));
            notallowed.add(sb.toString());
        }
        notallowed.add("setMail_folder_drafts_name");
        notallowed.add("setMail_folder_sent_name");
        notallowed.add("setMail_folder_spam_name");
        notallowed.add("setMail_folder_trash_name");
        notallowed.add("setMail_folder_confirmed_ham_name");
        notallowed.add("setMail_folder_confirmed_spam_name");
        notallowed.add("setMail_folder_archive_full_name");
        notallowed.add("setGUI_Spam_filter_capabilities_enabled");
        notallowed.add("setPassword_expired");
        notallowed.add("setMailenabled");
        notallowed.add("setLanguage");
        notallowed.add("setTimezone");
        notallowed.add("setPasswordMech");
        notallowed.add("setUserAttribute");
        notallowed.add("setFilestoreId");
        notallowed.add("setFilestore_name");
        notallowed.add("setFilestoreOwner");
        notallowed.add("setPrimaryAccountName");
        return notallowed;
    }

    /**
     * Returns all setters of the specified {@link Class}
     *
     * @param clazz The {@link Class}
     * @return A {@link MethodMapObject} with all setters
     */
    private MethodMapObject[] getSetableAttributeMethods(Class<?> clazz) {
        Method[] theMethods = clazz.getMethods();
        List<MethodMapObject> tmplist = new ArrayList<MethodMapObject>();

        MethodMapObject map_obj = null;

        // first fill setter and other infos in map object
        for (Method method : theMethods) {
            String method_name = method.getName();
            if (method_name.startsWith("set")) {
                // check if it is a type we support
                if (method.getParameterTypes()[0].getName().equalsIgnoreCase("java.lang.String") || method.getParameterTypes()[0].getName().equalsIgnoreCase("java.lang.Integer") || method.getParameterTypes()[0].getName().equalsIgnoreCase("java.util.Date") || method.getParameterTypes()[0].getName().equalsIgnoreCase("java.lang.Boolean")) {

                    map_obj = new MethodMapObject();
                    map_obj.setMethodName(method_name);
                    map_obj.setMethodParameterType(method.getParameterTypes()[0].getName());
                    map_obj.setSetter(method);

                    tmplist.add(map_obj);
                }
            }
        }

        for (MethodMapObject obj_map : tmplist) {
            String obj_method_name = obj_map.getMethodName();
            for (Method method : theMethods) {
                String meth_name = method.getName();
                if (isGetter(obj_method_name, meth_name)) {
                    obj_map.setGetter(method);
                    break;
                } else if (isBooleanGetter(obj_method_name, method, meth_name)) {
                    obj_map.setGetter(method);
                    break;
                }
            }
        }

        // now fill the getter in the map obj

        return tmplist.toArray(new MethodMapObject[tmplist.size()]);
    }

    private boolean isGetter(String obj_method_name, String meth_name) {
        return meth_name.startsWith("get") && meth_name.substring(3).equalsIgnoreCase(obj_method_name.substring(3));
    }

    private boolean isBooleanGetter(String obj_method_name, Method method, String meth_name) {
        return method.getReturnType().isAssignableFrom(Boolean.class) && meth_name.startsWith("is") && meth_name.substring(2).equalsIgnoreCase(obj_method_name.substring(3));
    }

    /////////////////////// NESTED CLASSES ////////////////////////

    /**
     * {@link MethodMapObject}
     */
    private class MethodMapObject {

        private Method getter = null;
        private Method setter = null;
        private String methodParameterType = null;
        String methodName = null;

        /**
         * @return the getter
         */
        public Method getGetter() {
            return getter;
        }

        /**
         * @param getter the getter to set
         */
        public void setGetter(Method getter) {
            this.getter = getter;
        }

        /**
         * @return the methodName
         */
        public String getMethodName() {
            return methodName;
        }

        /**
         * @param methodName the methodName to set
         */
        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        /**
         * @return the methodType
         */
        public String getMethodParameterType() {
            return methodParameterType;
        }

        /**
         * @param methodType the methodType to set
         */
        public void setMethodParameterType(String methodType) {
            methodParameterType = methodType;
        }

        /**
         * @return the setter
         */
        public Method getSetter() {
            return setter;
        }

        /**
         * @param setter the setter to set
         */
        public void setSetter(Method setter) {
            this.setter = setter;
        }
    }
}
