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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Credentials;
import com.openexchange.admin.rmi.dataobjects.Group;
import com.openexchange.admin.rmi.dataobjects.Resource;
import com.openexchange.admin.rmi.dataobjects.User;
import com.openexchange.admin.rmi.dataobjects.UserModuleAccess;
import com.openexchange.admin.rmi.exceptions.ContextExistsException;
import com.openexchange.admin.rmi.exceptions.NoSuchContextException;
import com.openexchange.admin.rmi.exceptions.NoSuchGroupException;
import com.openexchange.admin.rmi.exceptions.NoSuchResourceException;
import com.openexchange.admin.rmi.exceptions.NoSuchUserException;
import com.openexchange.admin.rmi.factory.ContextFactory;
import com.openexchange.admin.rmi.factory.GroupFactory;
import com.openexchange.admin.rmi.factory.ResourceFactory;
import com.openexchange.admin.rmi.factory.UserFactory;
import com.openexchange.admin.rmi.util.AssertUtil;

/**
 * {@link AdditionalRMITests}
 *
 * @author <a href="mailto:tobias.prinz@open-xchange.com">Tobias Prinz</a>
 * @author <a href="mailto:karsten.will@open-xchange.com">Karsten Will</a>
 */
public class AdditionalRMITests extends AbstractRMITest {

    public String myUserName = "thorben.betten";
    public String myDisplayName = "Thorben Betten";

    private Context context;
    private Resource testResource;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupContexts();
    }

    private void setupContexts() throws Exception {
        context = getContextManager().create(ContextFactory.createContext(1000L), contextAdminCredentials);

        User user = UserFactory.createUser("thorben.betten", "secret", myDisplayName, "Thorben", "Betten", "oxuser@example.com");
        user.setImapServer("example.com");
        user.setImapLogin("oxuser");
        user.setSmtpServer("example.com");

        getUserManager().create(context, user, contextAdminCredentials);
    }

    /**
     * Test the #any method. This explains how it is used, too, in case you either have never seen first-order-functions or seen the
     * monstrosity that is necessary to model them in Java.
     */
    @Test
    public void testAnyHelper() {
        Integer[] myArray = new Integer[] { Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), };
        Integer inThere = Integer.valueOf(1);
        Integer notInThereInteger = Integer.valueOf(0);

        assertFalse(any(myArray, notInThereInteger, new Verifier<Integer, Integer>() {

            @Override
            public boolean verify(Integer obj1, Integer obj2) {
                return obj1.equals(obj2);
            }
        }));

        assertTrue(any(myArray, inThere, new Verifier<Integer, Integer>() {

            @Override
            public boolean verify(Integer obj1, Integer obj2) {
                return obj1.equals(obj2);
            }
        }));
    }

    /**
     * Looking up users by User#name by checking whether display_name is updated
     */
    @Test
    public void testGetOxAccount() throws Exception {

        User knownUser = new User();
        knownUser.setName(myUserName);
        User[] mailboxNames = new User[] { knownUser };// users with only their mailbox name (User#name) - the rest is going to be looked
        // up
        User[] queriedUsers = getUserManager().getData(context, mailboxNames, contextAdminCredentials);// required line for test

        assertEquals("Query should return only one user", new Integer(1), Integer.valueOf(queriedUsers.length));
        User queriedUser = queriedUsers[0];
        assertEquals("Should have looked up display name", myDisplayName, queriedUser.getDisplay_name());
    }

    /**
     * Tests #listAll by comparing it with the result of #getData for all users
     */
    @Test
    public void testGetAllUsers() throws Exception {
        final Credentials credentials = contextAdminCredentials;
        User[] allUsers = getUserManager().listAll(context, credentials);// required line for test
        User[] queriedUsers = getUserManager().getData(context, allUsers, credentials);// required line for test
        AssertUtil.assertIDsAreEqual(allUsers, queriedUsers);
    }

    /*
     * Gets all groups and checks whether our test user is in one ore more
     */
    @Test
    public void testGetOxGroups() throws Exception {
        Context updatedContext = getContextManager().getData(context);

        User myUser = new User();
        myUser.setName(myUserName);
        User[] returnedUsers = getUserManager().getData(updatedContext, new User[] { myUser }, contextAdminCredentials);
        assertEquals(Integer.valueOf(1), Integer.valueOf(returnedUsers.length));
        User myUpdatedUser = returnedUsers[0];
        Group[] allGroups = getGroupManager().listAll(context, contextAdminCredentials);

        assertTrue("User's ID group should be found in a group", any(allGroups, myUpdatedUser.getId(), new Verifier<Group, Integer>() {

            @Override
            public boolean verify(Group group, Integer userid) {
                return (Arrays.asList(group.getMembers())).contains(userid);
            }
        }));
    }

    /**
     * Creates a resource and checks whether it is found
     */
    @Test
    public void testGetOxResources() throws Exception {
        Resource res = getTestResource();

        testResource = getResourceManager().create(res, context, contextAdminCredentials);

        try {
            List<Resource> allResources = Arrays.asList(getResourceManager().listAll(context, contextAdminCredentials));
            assertTrue("Should contain our trusty test resource", any(allResources, res, new Verifier<Resource, Resource>() {

                @Override
                public boolean verify(Resource fromCollection, Resource myResource) {
                    return myResource.getDisplayname().equals(fromCollection.getDisplayname()) && myResource.getEmail().equals(fromCollection.getEmail()) && myResource.getName().equals(fromCollection.getName());
                }
            }));
        } finally {
            try {
                getResourceManager().delete(testResource, context, contextAdminCredentials);
            } catch (NoSuchResourceException e) {
                // don't do anything, has been removed already, right?
                System.out.println("Resource was removed already");
            }
        }
    }

    /**
     * Tests creating a context, setting the access level and creating a first user for that context. The first user in a context is usually
     * the admin. Do not test creation of the first normal user, #testCreateOxUser() does that already
     */
    @Test
    public void testCreateFirstUser() throws Exception {
        Context newContext = ContextFactory.createContext("newContext");

        User newAdmin = UserFactory.createUser("new_admin", "secret", "New Admin", "New", "Admin", "newadmin@ox.invalid");
        newContext = getContextManager().create(newContext, newAdmin);// required line for test
        Credentials newAdminCredentials = new Credentials();
        newAdmin.setId(Integer.valueOf(2));// has to be hardcoded, because it cannot be looked up easily.
        newAdminCredentials.setLogin(newAdmin.getName());
        newAdminCredentials.setPassword("secret");
        assertUserWasCreatedProperly(newAdmin, newContext, newAdminCredentials);
    }

    @Test
    public void testCreateOxUser() throws Exception {
        User myNewUser = UserFactory.createUser("new_user", "secret", "New User", "New", "User", "newuser@ox.invalid");
        UserModuleAccess access = new UserModuleAccess();

        boolean userCreated = false;
        try {
            myNewUser = getUserManager().create(context, myNewUser, access, contextAdminCredentials);// required line for test
            userCreated = true;
            assertUserWasCreatedProperly(myNewUser, context, contextAdminCredentials);
        } finally {
            if (userCreated) {
                getUserManager().delete(context, myNewUser, contextAdminCredentials);
            }
        }
    }

    /**
     * Test the creation of a group
     */
    @Test
    public void testCreateOxGroup() throws Exception {
        boolean groupCreated = false;
        Group group = GroupFactory.createGroup("groupdisplayname", "groupname");
        try {
            group = getGroupManager().create(group, context, contextAdminCredentials);// required line for test
            groupCreated = true;
            assertGroupWasCreatedProperly(group, context, contextAdminCredentials);
        } finally {
            if (groupCreated) {
                getGroupManager().delete(group, context, contextAdminCredentials);
            }
        }
    }

    @Test
    public void testCreateOxResource() throws Exception {
        boolean resourceCreated = false;
        Resource res = ResourceFactory.createResource("resourceName", "resourceDisplayname", "resource@email.invalid");
        try {
            res = getResourceManager().create(res, context, contextAdminCredentials);// required line for test
            resourceCreated = true;
            assertResourceWasCreatedProperly(res, context, contextAdminCredentials);
        } finally {
            if (resourceCreated) {
                getResourceManager().delete(res, context, contextAdminCredentials);
            }
        }
    }

    @Test
    public void testUpdateOxAdmin_updateOxUser() throws Exception {
        boolean valueChanged = false;
        contextAdmin = getUserManager().getData(context, contextAdmin, contextAdminCredentials);
        String originalValue = contextAdmin.getAssistant_name();
        User changesToAdmin = new User();
        changesToAdmin.setId(contextAdmin.getId());
        String newAssistantName = "Herbert Feuerstein";
        changesToAdmin.setAssistant_name(newAssistantName);
        assertFalse("Precondition: Old assistant name should differ from new assistant name", newAssistantName.equals(originalValue));
        try {
            getUserManager().change(context, changesToAdmin, contextAdminCredentials);// required line for test
            valueChanged = true;
            contextAdmin = getUserManager().getData(context, contextAdmin, contextAdminCredentials);
            ;// refresh data
            assertEquals(changesToAdmin.getAssistant_name(), contextAdmin.getAssistant_name());
        } finally {
            if (valueChanged) {
                changesToAdmin.setAssistant_name(originalValue);
                getUserManager().change(context, changesToAdmin, contextAdminCredentials);
            }
        }
    }

    @Test
    public void testUpdateOxGroup() throws Exception {
        boolean groupCreated = false;
        Group group = GroupFactory.createGroup("groupdisplayname", "groupname");
        try {
            group = getGroupManager().create(group, context, contextAdminCredentials);
            groupCreated = true;
            Group groupChange = new Group();
            groupChange.setId(group.getId());
            groupChange.setName("changed groupname");
            getGroupManager().change(groupChange, context, contextAdminCredentials);// required line for test
            group = getGroupManager().getData(group, context, contextAdminCredentials);// update

            assertEquals("Name should have been changed", group.getName(), groupChange.getName());
        } finally {
            if (groupCreated) {
                getGroupManager().delete(group, context, contextAdminCredentials);
            }
        }
    }

    @Test
    public void testUpdateOxResource() throws Exception {
        boolean resourceCreated = false;
        Resource res = ResourceFactory.createResource("resourceName", "resourceDisplayname", "resource@email.invalid");
        try {
            res = getResourceManager().create(res, context, contextAdminCredentials);
            resourceCreated = true;
            Resource resChange = new Resource();
            resChange.setId(res.getId());
            resChange.setDisplayname("changed display name");
            getResourceManager().change(resChange, context, contextAdminCredentials);// required line for test
            res = getResourceManager().getData(res, context, contextAdminCredentials);// update
            assertEquals("Display name should have changed", resChange.getDisplayname(), res.getDisplayname());
        } finally {
            if (resourceCreated) {
                getResourceManager().delete(res, context, contextAdminCredentials);
            }
        }
    }

    @Test
    public void testDeleteOxUsers() throws Exception {
        boolean resourceDeleted = false;
        Resource res = ResourceFactory.createResource("resourceName", "resourceDisplayname", "resource@email.invalid");
        try {
            res = getResourceManager().create(res, context, contextAdminCredentials);

            Assert.assertNotNull("Resource id cannot be null", res.getId());
            getResourceManager().delete(res, context, contextAdminCredentials);
            resourceDeleted = true;
        } catch (Exception exception) {
            Assert.assertTrue("Resource could not be deleted!", resourceDeleted);
        }
    }

    @Test
    public void testGetUserAccessModules() throws Exception {
        User knownUser = new User();
        knownUser.setName(this.myUserName);
        User[] mailboxNames = new User[] { knownUser };// users with only their mailbox name (User#name) - the rest is going to be looked
        // up
        User[] queriedUsers = getUserManager().getData(context, mailboxNames, contextAdminCredentials);// query by mailboxNames (User.name)

        assertEquals("Query should return only one user", new Integer(1), Integer.valueOf(queriedUsers.length));
        User user = queriedUsers[0];

        UserModuleAccess access = getUserManager().getModuleAccess(context, user, contextAdminCredentials);
        assertTrue("Information for module access should be available", access != null);
    }

    @Test
    public void testUpdateMaxCollapQuota() throws Exception {
        Context contextTmp = getContextManager().getData(context);
        Long updatedMaxQuota = new Long(1024);
        contextTmp.setMaxQuota(updatedMaxQuota);
        getContextManager().change(contextTmp);
        Context newContext = getContextManager().getData(context);
        assertEquals("MaxCollapQuota should have the new value", newContext.getMaxQuota(), updatedMaxQuota);
    }

    @Test
    public void testGetUser() throws Exception {
        User knownUser = new User();
        knownUser.setName(this.myUserName);
        User[] mailboxNames = new User[] { knownUser };// users with only their mailbox name (User#name) - the rest is going to be
                                                       // looked
                                                       // up
        User[] queriedUsers = getUserManager().getData(context, mailboxNames, contextAdminCredentials);// query by mailboxNames (User.name)

        assertEquals("Query should return only one user", new Integer(1), Integer.valueOf(queriedUsers.length));
        User receivedUser = queriedUsers[0];
        User queriedUser = getUserManager().getData(context, receivedUser, contextAdminCredentials);
        assertEquals("Should have looked up display name", myDisplayName, queriedUser.getDisplay_name());
    }

    @Test
    public void testUpdateModuleAccess() throws Exception {
        User knownUser = new User();
        knownUser.setName("oxadmin");
        User[] mailboxNames = new User[] { knownUser };// users with only their mailbox name (User#name) - the rest is going to be looked
        // up
        User[] queriedUsers = getUserManager().getData(context, mailboxNames, contextAdminCredentials);// query by mailboxNames (User.name)

        assertEquals("Query should return only one user", new Integer(1), Integer.valueOf(queriedUsers.length));
        User user = queriedUsers[0];

        /**
         * Besides contacts and webmail, all other accesses should be disabled, see com.openexchange.admin.tools.AdminCache.getDefaultUserModuleAccess()
         * or ModuleAccessDefinitions.properties for <code>webmail_plus</cdoe> and <code>NEW_CONTEXT_DEFAULT_ACCESS_COMBINATION_NAME</code> in
         * <code>hosting.properties</code>
         */
        UserModuleAccess access = getUserManager().getModuleAccess(context, user, contextAdminCredentials);
        assertFalse("Calendar access should be disabled by default", access.getCalendar());
        access.setCalendar(true);
        getUserManager().changeModuleAccess(context, user, access, contextAdminCredentials);
        access = getUserManager().getModuleAccess(context, user, contextAdminCredentials);
        assertTrue("Calendar access should be enabled now", access.getCalendar());
        // reset access and check again
        access.setCalendar(false);
        getUserManager().changeModuleAccess(context, user, access, contextAdminCredentials);
        access = getUserManager().getModuleAccess(context, user, contextAdminCredentials);
        assertFalse("Calendar access should be disabled again", access.getCalendar());
    }

    @Test
    public void testContextExistsException() throws Exception {
        boolean contextCreated = false;
        Context newContext = ContextFactory.createContext("newContext");
        User newAdmin = UserFactory.createUser("oxadmin", "secret", "New Admin", "New", "Admin", "newadmin@ox.invalid");
        try {
            newContext = getContextManager().create(newContext, newAdmin);
            contextCreated = true;
            try {
                getContextManager().create(newContext, newAdmin);
                fail("Should throw ContextExistsException");
            } catch (ContextExistsException e) {
                assertTrue("Caught exception", true);
            }
        } finally {
            if (contextCreated) {
                getContextManager().delete(newContext);
            }
        }
    }

    @Test
    public void testNoSuchContextException() throws Exception {
        Context missingContext = ContextFactory.createContext(Integer.MAX_VALUE, "missing");
        try {
            getContextManager().delete(missingContext);
            fail("Expected NoSuchContextException");
        } catch (NoSuchContextException e) {
            assertTrue("Caught exception", true);
        }
    }

    @Test
    public void testNoSuchGroupException() throws Exception {
        Group missingGroup = new Group();
        missingGroup.setId(Integer.valueOf(Integer.MAX_VALUE));
        try {
            getGroupManager().delete(missingGroup, context, contextAdminCredentials);
            fail("Expected NoSuchGroupException");
        } catch (NoSuchGroupException e) {
            assertTrue("Caught exception", true);
        }
    }

    @Test
    public void testNoSuchResourceException() throws Exception {
        Resource missingResource = new Resource();
        missingResource.setId(Integer.valueOf(Integer.MAX_VALUE));
        try {
            getResourceManager().delete(missingResource, context, contextAdminCredentials);
            fail("Expected NoSuchResourceException");
        } catch (NoSuchResourceException e) {
            assertTrue("Caught exception", true);
        }
    }

    @Test
    public void testNoSuchUserException() throws Exception {
        User missingUser = new User();
        missingUser.setId(Integer.valueOf(Integer.MAX_VALUE));
        try {
            getUserManager().delete(context, missingUser, contextAdminCredentials);
            fail("Expected NoSuchUserException");
        } catch (NoSuchUserException e) {
            assertTrue("Caught exception", true);
        }
    }

    /**
     * Get the test resource
     */
    private Resource getTestResource() {
        if (testResource != null && testResource.getId() != null) {
            return testResource;
        }
        Resource res = new Resource();
        res.setName("Testresource");
        res.setEmail("test-resource@testsystem.invalid");
        res.setDisplayname("The test resource");
        return res;
    }
}
