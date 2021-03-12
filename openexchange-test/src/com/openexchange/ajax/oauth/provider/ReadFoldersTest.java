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

package com.openexchange.ajax.oauth.provider;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import com.openexchange.ajax.folder.actions.EnumAPI;
import com.openexchange.ajax.folder.actions.FolderUpdatesResponse;
import com.openexchange.ajax.folder.actions.ListRequest;
import com.openexchange.ajax.folder.actions.ListResponse;
import com.openexchange.ajax.folder.actions.PathRequest;
import com.openexchange.ajax.folder.actions.PathResponse;
import com.openexchange.ajax.folder.actions.RootRequest;
import com.openexchange.ajax.folder.actions.UpdatesRequest;
import com.openexchange.ajax.folder.actions.VisibleFoldersRequest;
import com.openexchange.ajax.folder.actions.VisibleFoldersResponse;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AJAXRequest;
import com.openexchange.ajax.framework.AbstractAJAXResponse;
import com.openexchange.ajax.framework.AbstractUpdatesRequest.Ignore;
import com.openexchange.ajax.framework.UserValues;
import com.openexchange.ajax.user.actions.GetRequest;
import com.openexchange.ajax.user.actions.GetResponse;
import com.openexchange.calendar.json.AppointmentActionFactory;
import com.openexchange.contacts.json.ContactActionFactory;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.ContentType;
import com.openexchange.folderstorage.FolderExceptionErrorMessage;
import com.openexchange.folderstorage.database.contentType.CalendarContentType;
import com.openexchange.folderstorage.database.contentType.ContactsContentType;
import com.openexchange.folderstorage.database.contentType.TaskContentType;
import com.openexchange.group.GroupStorage;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.java.util.TimeZones;
import com.openexchange.oauth.provider.exceptions.OAuthInsufficientScopeException;
import com.openexchange.oauth.provider.resourceserver.scope.Scope;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.tasks.json.TaskActionFactory;
import com.openexchange.test.FolderTestManager;
import com.openexchange.test.TestClassConfig;
import com.openexchange.test.concurrent.ParallelParameterized;
import com.openexchange.test.tryagain.TryAgain;

/**
 * {@link ReadFoldersTest}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.8.0
 */
@SuppressWarnings("deprecation")
@RunWith(ParallelParameterized.class)
public class ReadFoldersTest extends AbstractOAuthTest {

    private UserValues values;

    private FolderTestManager ftm;

    private FolderTestManager ftm2;

    private final ContentType contentType;

    private FolderObject privateSubfolder;

    private FolderObject publicSubfolder;

    private FolderObject sharedSubfolder;

    private int userId;

    private Set<Integer> groups;

    private final EnumAPI api;

    private final boolean altNames;

    private static final Map<Scope, ContentType> S2CT = new HashMap<>();
    static {
        S2CT.put(Scope.newInstance(ContactActionFactory.OAUTH_READ_SCOPE), ContactsContentType.getInstance());
        S2CT.put(Scope.newInstance(AppointmentActionFactory.OAUTH_READ_SCOPE), CalendarContentType.getInstance());
        S2CT.put(Scope.newInstance(TaskActionFactory.OAUTH_READ_SCOPE), TaskContentType.getInstance());

    }

    private static final Set<EnumAPI> APIS = EnumSet.allOf(EnumAPI.class);


    static {
        APIS.remove(EnumAPI.EAS_FOLDERS);
    }

    @Parameters(name = "{1}_{2}_altNames={3}")
    public static Collection<Object[]> generateData() {
        List<Object[]> params = new ArrayList<>(S2CT.size());
        for (Scope scope : S2CT.keySet()) {
            for (EnumAPI api : APIS) {
                for (Boolean altNames : new Boolean[] { Boolean.TRUE, Boolean.FALSE }) {
                    params.add(new Object[] { scope, S2CT.get(scope), api, altNames });
                }
            }
        }
        return params;
    }

    public ReadFoldersTest(Scope scope, ContentType contentType, EnumAPI api, boolean altNames) {
        super(scope);
        this.contentType = contentType;
        this.api = api;
        this.altNames = altNames;
    }

    private int moduleId() {
        if (contentType == ContactsContentType.getInstance()) {
            return FolderObject.CONTACT;
        } else if (contentType == CalendarContentType.getInstance()) {
            return FolderObject.CALENDAR;
        } else if (contentType == TaskContentType.getInstance()) {
            return FolderObject.TASK;
        }
        return -1;
    }

    private int privateFolderId() throws OXException, IOException, JSONException {
        return privateFolderId(getClient());
    }

    private int privateFolderId(AJAXClient client) throws OXException, IOException, JSONException {
        if (contentType == ContactsContentType.getInstance()) {
            return client.getValues().getPrivateContactFolder();
        } else if (contentType == CalendarContentType.getInstance()) {
            return client.getValues().getPrivateAppointmentFolder();
        } else if (contentType == TaskContentType.getInstance()) {
            return client.getValues().getPrivateTaskFolder();
        }

        return -1;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        values = getClient().getValues();
        userId = values.getUserId();
        GetResponse getResponse = getClient().execute(new GetRequest(userId, TimeZones.UTC));
        int[] userGroups = getResponse.getUser().getGroups();
        groups = new HashSet<>();
        groups.add(I(GroupStorage.GROUP_ZERO_IDENTIFIER));
        if (userGroups != null) {
            for (int g : userGroups) {
                groups.add(I(g));
            }
        }

        ftm = new FolderTestManager(getClient());
        privateSubfolder = ftm.generatePrivateFolder("oauth provider folder tree test - private " + contentType.toString() + " " + UUID.randomUUID().toString(), moduleId(), privateFolderId(), userId);
        publicSubfolder = ftm.generatePublicFolder("oauth provider folder tree test - public " + contentType.toString() + " " + UUID.randomUUID().toString(), moduleId(), FolderObject.SYSTEM_PUBLIC_FOLDER_ID, userId);
        ftm.insertFoldersOnServer(new FolderObject[] { privateSubfolder, publicSubfolder });

        // prepare shared folders
        ftm2 = new FolderTestManager(testUser2.getAjaxClient());
        // remove any non-private permissions from client2s private folder
        OCLPermission adminPermission = new OCLPermission();
        adminPermission.setEntity(testUser2.getAjaxClient().getValues().getUserId());
        adminPermission.setGroupPermission(false);
        adminPermission.setFolderAdmin(true);
        adminPermission.setAllPermission(OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION);
        FolderObject client2PrivateFolder = ftm2.getFolderFromServer(privateFolderId(testUser2.getAjaxClient()));
        client2PrivateFolder.setPermissionsAsArray(new OCLPermission[] { adminPermission });
        client2PrivateFolder.setLastModified(new Date());
        ftm2.updateFolderOnServer(client2PrivateFolder);
        sharedSubfolder = ftm2.generateSharedFolder("oauth provider folder tree test - shared " + contentType.toString() + " " + UUID.randomUUID().toString(), moduleId(), privateFolderId(testUser2.getAjaxClient()), testUser2.getAjaxClient().getValues().getUserId(), userId);
        ftm2.insertFoldersOnServer(new FolderObject[] { sharedSubfolder });

        oAuthClient.logout();
        oAuthClient = new OAuthClient(testUser, clientApp.getId(), clientApp.getSecret(), clientApp.getRedirectURIs().get(0), scope);
    }

    @Override
    public TestClassConfig getTestConfig() {
        return TestClassConfig.builder().createAjaxClient().withUserPerContext(2).build();
    }

    @Test
    @TryAgain
    public void testFolderTreeNavigation() throws Exception {
        // expect root folders
        Set<Integer> expectedFolderIds = new HashSet<>();
        if (api == EnumAPI.OUTLOOK) {
            expectedFolderIds.add(I(FolderObject.SYSTEM_PRIVATE_FOLDER_ID));
        } else {
            expectedFolderIds.add(I(FolderObject.SYSTEM_PRIVATE_FOLDER_ID));
            expectedFolderIds.add(I(FolderObject.SYSTEM_PUBLIC_FOLDER_ID));
            expectedFolderIds.add(I(FolderObject.SYSTEM_SHARED_FOLDER_ID));
        }

        RootRequest rootRequest = new RootRequest(api);
        rootRequest.setAltNames(altNames);
        Set<Integer> rootFolderIds = collectFolderIds(rootRequest);
        Assert.assertTrue("Missing expected root folder(s). Expected " + expectedFolderIds + " but got " + rootFolderIds, rootFolderIds.containsAll(expectedFolderIds));
        Assert.assertFalse("Infostore root folder was contained in response but must not", rootFolderIds.contains(I(FolderObject.SYSTEM_INFOSTORE_FOLDER_ID)));

        VisibleFoldersRequest request = new VisibleFoldersRequest(api, contentType.toString());
        request.setAltNames(altNames);
        VisibleFoldersResponse response = oAuthClient.execute(request);
        assertNoErrorsAndWarnings(response);
        Iterator<FolderObject> privateFoldersIter = response.getPrivateFolders();
        List<FolderObject> privateFolders = new ArrayList<FolderObject>(3);
        while (privateFoldersIter.hasNext()) {
            privateFolders.add(privateFoldersIter.next());

        }
        assertContentTypeAndPermissions(privateFolders);
        Set<Integer> privateFolderIds = collectFolderIds(privateFolders);
        Assert.assertTrue("Missing expected private folder " + privateFolderId() + " in " + privateFolderIds, privateFolderIds.contains(I(privateFolderId())));

        ListRequest listPrivateSubfoldersRequest = new ListRequest(api, privateFolderId());
        listPrivateSubfoldersRequest.setAltNames(altNames);
        List<FolderObject> privateSubFolders = listFolders(listPrivateSubfoldersRequest);
        assertContentTypeAndPermissions(privateSubFolders);
        Set<Integer> privateSubFolderIds = collectFolderIds(privateSubFolders);
        Assert.assertTrue("Missing expected private subfolder " + privateSubfolder.getObjectID() + " in " + privateSubFolderIds, privateSubFolderIds.contains(I(privateSubfolder.getObjectID())));

        // expect public folders
        ListRequest listPublicRequest = new ListRequest(api, FolderObject.SYSTEM_PUBLIC_FOLDER_ID);
        listPublicRequest.setAltNames(altNames);
        List<FolderObject> publicSubFolders = listFolders(listPublicRequest);
        assertContentTypeAndPermissions(publicSubFolders);
        Set<Integer> publicSubFolderIds = collectFolderIds(publicSubFolders);
        Assert.assertTrue("Missing expected public subfolder " + publicSubfolder.getObjectID() + " in " + publicSubFolderIds, publicSubFolderIds.contains(I(publicSubfolder.getObjectID())));

        // expect shared folders
        ListRequest listSharedFolders = new ListRequest(api, FolderObject.SYSTEM_SHARED_FOLDER_ID);
        listSharedFolders.setAltNames(altNames);
        List<FolderObject> sharedFolders = listFolders(listSharedFolders);
        assertContentTypeAndPermissions(sharedFolders);
        FolderObject client2Folder = null;
        String sharedFolderId = "u:" + testUser2.getAjaxClient().getValues().getUserId();
        for (FolderObject folder : sharedFolders) {
            if (sharedFolderId.equals(folder.getFullName())) {
                client2Folder = folder;
                break;
            }
        }
        Assert.assertNotNull("Missing expected folder " + sharedFolderId + " below system shared folder", client2Folder);
        ListRequest listSharedSubFolders = new ListRequest(api, sharedFolderId);
        listSharedSubFolders.setAltNames(altNames);
        Set<Integer> sharedSubFolderIds = collectFolderIds(listSharedSubFolders);
        Assert.assertTrue("Missing expected shared subfolder " + sharedSubfolder.getObjectID() + " in " + sharedSubFolderIds, sharedSubFolderIds.contains(I(sharedSubfolder.getObjectID())));
    }

    @Test
    @TryAgain
    public void testAllVisibleFolders() throws Exception {
        Set<Integer> expectedFolderIds = new HashSet<>();
        VisibleFoldersRequest request = new VisibleFoldersRequest(api, contentType.toString());
        request.setAltNames(altNames);
        VisibleFoldersResponse response = oAuthClient.execute(request);
        assertNoErrorsAndWarnings(response);

        // private
        List<FolderObject> privateFolders = toList(response.getPrivateFolders());
        assertContentTypeAndPermissions(privateFolders);
        expectedFolderIds.add(I(privateFolderId()));
        expectedFolderIds.add(I(privateSubfolder.getObjectID()));
        Assert.assertTrue(collectFolderIds(privateFolders).containsAll(expectedFolderIds));

        // public
        List<FolderObject> publicFolders = toList(response.getPublicFolders());
        assertContentTypeAndPermissions(publicFolders);
        expectedFolderIds.clear();
        expectedFolderIds.add(I(publicSubfolder.getObjectID()));
        Assert.assertTrue(collectFolderIds(publicFolders).containsAll(expectedFolderIds));

        // shared
        List<FolderObject> sharedFolders = toList(response.getSharedFolders());
        assertContentTypeAndPermissions(sharedFolders);
        expectedFolderIds.clear();
        expectedFolderIds.add(I(sharedSubfolder.getObjectID()));
        Set<Integer> sharedFoldersIds = collectFolderIds(sharedFolders);
		Assert.assertTrue("Updated folders do not contain expected ones. Expected: " + expectedFolderIds + " but was: " + sharedFoldersIds, sharedFoldersIds.containsAll(expectedFolderIds));
    }

    @Test
    @TryAgain
    public void testUpdates() throws Exception {
        UpdatesRequest request = new UpdatesRequest(api, ListRequest.DEFAULT_COLUMNS, -1, null, new Date(privateSubfolder.getLastModified().getTime() - 1000), Ignore.NONE);
        request.setAltNames(altNames);
        FolderUpdatesResponse updatesResponse = oAuthClient.execute(request);
        assertNoErrorsAndWarnings(updatesResponse);
        List<FolderObject> folders = updatesResponse.getFolders();
        assertContentTypeAndPermissions(folders);
        Set<Integer> expectedFolderIds = new HashSet<>();
        expectedFolderIds.add(I(privateSubfolder.getObjectID()));
        expectedFolderIds.add(I(publicSubfolder.getObjectID()));
        expectedFolderIds.add(I(sharedSubfolder.getObjectID()));
        Set<Integer> updatedFolderIds = collectFolderIds(folders);
		Assert.assertTrue("Updated folders do not contain expected ones. Expected: " + expectedFolderIds + " but was: " + updatedFolderIds, updatedFolderIds.containsAll(expectedFolderIds));
    }

    @Test
    @TryAgain
    public void testPath() throws Exception {
        PathRequest request = new PathRequest(api, Integer.toString(privateSubfolder.getObjectID()));
        request.setAltNames(altNames);
        PathResponse pathResponse = oAuthClient.execute(request);
        assertNoErrorsAndWarnings(pathResponse);
        List<FolderObject> folders = toList(pathResponse.getFolder());
        assertContentTypeAndPermissions(folders);
        Set<Integer> expectedFolderIds = new HashSet<>();
        expectedFolderIds.add(I(privateSubfolder.getObjectID()));
        expectedFolderIds.add(I(privateFolderId()));
        expectedFolderIds.add(I(FolderObject.SYSTEM_PRIVATE_FOLDER_ID));
        Set<Integer> collectFolderIds = collectFolderIds(folders);
        Assert.assertTrue(collectFolderIds.containsAll(expectedFolderIds));
        collectFolderIds.removeAll(expectedFolderIds);
        Assert.assertTrue(collectFolderIds.isEmpty());
    }

    @Test
    @TryAgain
    public void testGet() throws Exception {
        Set<Integer> folderIds = new HashSet<>();
        folderIds.add(I(FolderObject.SYSTEM_PRIVATE_FOLDER_ID));
        folderIds.add(I(FolderObject.SYSTEM_PUBLIC_FOLDER_ID));
        folderIds.add(I(FolderObject.SYSTEM_SHARED_FOLDER_ID));
        folderIds.add(I(privateFolderId()));
        folderIds.add(I(privateSubfolder.getObjectID()));
        folderIds.add(I(publicSubfolder.getObjectID()));
        folderIds.add(I(sharedSubfolder.getObjectID()));

        for (int folderId : folderIds) {
            com.openexchange.ajax.folder.actions.GetRequest request = new com.openexchange.ajax.folder.actions.GetRequest(api, folderId);
            request.setAltNames(altNames);
            com.openexchange.ajax.folder.actions.GetResponse response = oAuthClient.execute(request);
            assertNoErrorsAndWarnings(response);
            assertContentTypeAndPermissions(response.getFolder());
        }
    }

    @Test
    @TryAgain
    public void testInsufficientScopeOnAllVisibleFolders() throws Exception {
        HashSet<Scope> invalidScopes = new HashSet<>(S2CT.keySet());
        invalidScopes.remove(scope);
        for (Scope invalidScope : invalidScopes) {
            ContentType invalidContentType = S2CT.get(invalidScope);
            VisibleFoldersRequest request = new VisibleFoldersRequest(api, invalidContentType.toString(), VisibleFoldersRequest.DEFAULT_COLUMNS, false);
            request.setAltNames(altNames);
            VisibleFoldersResponse response = oAuthClient.execute(request);
            assertFolderNotVisibleError(response, invalidScope);
        }
    }

    @Test
    @TryAgain
    public void testInsufficientScopeOnGet() throws Exception {
        HashSet<Scope> invalidScopes = new HashSet<>(S2CT.keySet());
        invalidScopes.remove(scope);
        for (Scope invalidScope : invalidScopes) {
            ContentType invalidContentType = S2CT.get(invalidScope);
            // get folders via ajax client and verify that every single get-request for those folders fails
            // for the according OAuth client because of insufficient scope
            VisibleFoldersRequest allRequest = new VisibleFoldersRequest(api, invalidContentType.toString(), VisibleFoldersRequest.DEFAULT_COLUMNS, false);
            allRequest.setAltNames(altNames);
            VisibleFoldersResponse allResponse = getClient().execute(allRequest);
            assertNoErrorsAndWarnings(allResponse);
            List<FolderObject> allFolders = new LinkedList<>();
            allFolders.addAll(toList(allResponse.getPrivateFolders()));
            allFolders.addAll(toList(allResponse.getPublicFolders()));
            allFolders.addAll(toList(allResponse.getSharedFolders()));
            for (FolderObject folder : allFolders) {
                com.openexchange.ajax.folder.actions.GetResponse response;
                if (folder.getObjectID() < 0) {
                    com.openexchange.ajax.folder.actions.GetRequest getRequest = new com.openexchange.ajax.folder.actions.GetRequest(api, folder.getFullName(), false);
                    getRequest.setAltNames(altNames);
                    response = oAuthClient.execute(getRequest);
                } else {
                    com.openexchange.ajax.folder.actions.GetRequest getRequest = new com.openexchange.ajax.folder.actions.GetRequest(api, folder.getObjectID(), false);
                    getRequest.setAltNames(altNames);
                    response = oAuthClient.execute(getRequest);
                }

                assertFolderNotVisibleError(response, invalidScope);
            }
        }
    }

    private void assertFolderNotVisibleError(AbstractAJAXResponse response, Scope requiredScope) {
        OXException e = response.getException();
        Assert.assertTrue(e instanceof OAuthInsufficientScopeException || FolderExceptionErrorMessage.FOLDER_NOT_VISIBLE.equals(e));
        if (e instanceof OAuthInsufficientScopeException) {
            Assert.assertEquals(requiredScope.toString(), ((OAuthInsufficientScopeException) e).getScope());
        }
    }

    private void assertContentTypeAndPermissions(List<FolderObject> folders) {
        Iterator<FolderObject> it = folders.iterator();
        while (it.hasNext()) {
            FolderObject folder = it.next();
            assertContentTypeAndPermissions(folder);
        }
    }

    private void assertContentTypeAndPermissions(FolderObject folder) {
        Assert.assertTrue("Unexpected module " + folder.getModule() + " for folder " + folder.getFolderName(), moduleId() == folder.getModule() || FolderObject.SYSTEM_MODULE == folder.getModule());
        boolean canRead = false;
        for (OCLPermission p : folder.getPermissions()) {
            if (p.getEntity() == userId || (p.isGroupPermission() && groups.contains(I(p.getEntity())))) {
                canRead = p.isFolderVisible();
                break;
            }
        }
        Assert.assertTrue("Request returned folder " + folder.toString() + " but folder must not be visible", canRead);
    }

    private void assertNoErrorsAndWarnings(AbstractAJAXResponse response) {
        Assert.assertFalse(response.hasError());
        Assert.assertFalse(response.hasWarnings());
    }

    private List<FolderObject> listFolders(AJAXRequest<ListResponse> request) throws OXException, IOException, JSONException {
        ListResponse response = oAuthClient.execute(request);
        assertNoErrorsAndWarnings(response);
        List<FolderObject> folders = new ArrayList<>();
        Iterator<FolderObject> it = response.getFolder();
        while (it.hasNext()) {
            folders.add(it.next());
        }

        return folders;
    }

    private static <T> List<T> toList(Iterator<T> iterator) {
        List<T> list = new LinkedList<>();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }

        return list;
    }

    private static Set<Integer> collectFolderIds(List<FolderObject> folders) {
        Iterator<FolderObject> it = folders.iterator();
        return collectFolderIds(it);
    }

    private static Set<Integer> collectFolderIds(Iterator<FolderObject> it) {
        Set<Integer> folderIds = new HashSet<>();
        while (it.hasNext()) {
            folderIds.add(I(it.next().getObjectID()));
        }
        return folderIds;
    }

    private Set<Integer> collectFolderIds(AJAXRequest<ListResponse> request) throws OXException, IOException, JSONException {
        List<FolderObject> folders = listFolders(request);
        return collectFolderIds(folders);
    }

}
