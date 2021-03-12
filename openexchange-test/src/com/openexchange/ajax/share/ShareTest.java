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

package com.openexchange.ajax.share;

import static com.openexchange.java.Autoboxing.B;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import org.json.JSONException;
import org.junit.Assert;
import com.openexchange.ajax.folder.Create;
import com.openexchange.ajax.folder.actions.DeleteRequest;
import com.openexchange.ajax.folder.actions.EnumAPI;
import com.openexchange.ajax.folder.actions.GetRequest;
import com.openexchange.ajax.folder.actions.GetResponse;
import com.openexchange.ajax.folder.actions.InsertRequest;
import com.openexchange.ajax.folder.actions.InsertResponse;
import com.openexchange.ajax.folder.actions.OCLGuestPermission;
import com.openexchange.ajax.folder.actions.UpdateRequest;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AJAXRequest;
import com.openexchange.ajax.framework.AbstractSmtpAJAXSession;
import com.openexchange.ajax.infostore.actions.DeleteInfostoreRequest;
import com.openexchange.ajax.infostore.actions.GetInfostoreRequest;
import com.openexchange.ajax.infostore.actions.GetInfostoreResponse;
import com.openexchange.ajax.infostore.actions.InfostoreTestManager;
import com.openexchange.ajax.infostore.actions.NewInfostoreRequest;
import com.openexchange.ajax.infostore.actions.NewInfostoreResponse;
import com.openexchange.ajax.infostore.actions.UpdateInfostoreRequest;
import com.openexchange.ajax.infostore.actions.UpdateInfostoreResponse;
import com.openexchange.ajax.share.GuestClient.ClientConfig;
import com.openexchange.ajax.share.actions.ExtendedPermissionEntity;
import com.openexchange.ajax.share.actions.FileShare;
import com.openexchange.ajax.share.actions.FileSharesRequest;
import com.openexchange.ajax.share.actions.FolderShare;
import com.openexchange.ajax.share.actions.FolderSharesRequest;
import com.openexchange.ajax.share.actions.GetLinkRequest;
import com.openexchange.ajax.share.actions.GetLinkResponse;
import com.openexchange.ajax.share.actions.ShareLink;
import com.openexchange.ajax.smtptest.MailManager;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.DefaultFile;
import com.openexchange.file.storage.DefaultFileStorageGuestObjectPermission;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.File.Field;
import com.openexchange.file.storage.FileStorageGuestObjectPermission;
import com.openexchange.file.storage.FileStorageObjectPermission;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.modules.Module;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.Strings;
import com.openexchange.java.util.TimeZones;
import com.openexchange.java.util.UUIDs;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.share.ShareTarget;
import com.openexchange.share.notification.ShareNotificationService.Transport;
import com.openexchange.share.recipient.AnonymousRecipient;
import com.openexchange.share.recipient.GuestRecipient;
import com.openexchange.share.recipient.RecipientType;
import com.openexchange.share.recipient.ShareRecipient;
import com.openexchange.test.common.test.TestClassConfig;
import com.openexchange.test.common.test.pool.TestContext;
import com.openexchange.test.common.test.pool.TestUser;
import com.openexchange.testing.httpclient.invoker.ApiClient;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.MailData;

/**
 * {@link ShareTest}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public abstract class ShareTest extends AbstractSmtpAJAXSession {

    // @formatter:off
    protected static final EnumAPI[] TESTED_FOLDER_APIS = new EnumAPI[] { EnumAPI.OX_OLD, EnumAPI.OX_NEW, EnumAPI.OUTLOOK };

    protected static final int[] TESTED_MODULES = new int[] {
        FolderObject.CONTACT, FolderObject.INFOSTORE, FolderObject.TASK, FolderObject.CALENDAR
    };
    // @formatter:on

    protected static final Random random = new Random();
    protected static final int CLEANUP_DELAY = 30000;

    private Map<Integer, FolderObject> foldersToDelete;
    private Map<String, File> filesToDelete;
    protected InfostoreTestManager itm;

    private TestContext guestContext;

    public enum GuestPermissionType {
        NAMED_AUTHOR_PERMISSION,
        NAMED_GUEST_PERMISSION,
        ANONYMOUS_GUEST_PERMISSION_WITH_PASSWORD,
        ANONYMOUS_GUEST_PERMSSION
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        itm = new InfostoreTestManager(getClient());
        foldersToDelete = new HashMap<Integer, FolderObject>();
        filesToDelete = new HashMap<String, File>();
        guestContext = testContextList.get(1);
    }

    @Override
    public TestClassConfig getTestConfig() {
        return TestClassConfig.builder().createAjaxClient().withContexts(2).withUserPerContext(1).build();
    }

    /**
     * Gets the client timezone
     *
     * @return The client timezone
     */
    protected TimeZone getTimeZone() throws OXException, IOException, JSONException {
        return getClient().getValues().getTimeZone();
    }

    /**
     * Gets a share link for a specific target by issuing an appropriate "get link" request.
     *
     * @param target The share target to get the share link for
     * @return The share link
     */
    protected ShareLink getLink(ShareTarget target) throws OXException, IOException, JSONException {
        GetLinkResponse response = getClient().execute(new GetLinkRequest(target, getTimeZone()));
        assertFalse(response.getErrorMessage(), response.hasError());
        return response.getShareLink();
    }

    /**
     * Inserts and remembers a new shared folder containing the supplied guest permissions.
     *
     * @param client The client to use
     * @param api The folder tree to use
     * @param module The module identifier
     * @param parent The ID of the parent folder
     * @param permission The permission to add
     * @return The inserted folder
     * @throws Exception
     */
    protected FolderObject insertSharedFolder(AJAXClient client, EnumAPI api, int module, int parent, OCLPermission permission) throws Exception {
        return insertSharedFolder(client, api, module, parent, randomUID(), permission);
    }

    /**
     * Inserts and remembers a new shared folder containing the supplied guest permissions.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @param parent The ID of the parent folder
     * @param permission The permission to add
     * @return The inserted folder
     * @throws Exception
     */
    protected FolderObject insertSharedFolder(EnumAPI api, int module, int parent, OCLPermission permission) throws Exception {
        return insertSharedFolder(api, module, parent, randomUID(), permission);
    }

    /**
     * Inserts and remembers a new shared folder containing the supplied guest permissions.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @param parent The ID of the parent folder
     * @param name The folders name
     * @param permission The permission to add
     * @return The inserted folder
     * @throws Exception
     */
    protected FolderObject insertSharedFolder(EnumAPI api, int module, int parent, String name, OCLPermission permission) throws Exception {
        return insertSharedFolder(getClient(), api, module, parent, name, permission);
    }

    /**
     * Inserts and remembers a new shared folder containing the supplied guest permissions.
     *
     * @param client The client to use
     * @param api The folder tree to use
     * @param module The module identifier
     * @param parent The ID of the parent folder
     * @param name The folders name
     * @param permission The permission to add
     * @return The inserted folder
     * @throws Exception
     */
    protected FolderObject insertSharedFolder(AJAXClient client, EnumAPI api, int module, int parent, String name, OCLPermission permission) throws Exception {
        FolderObject sharedFolder = Create.createPrivateFolder(name, module, client.getValues().getUserId(), permission);
        sharedFolder.setParentFolderID(parent);
        return insertFolder(client, api, sharedFolder);
    }

    /**
     * Inserts and remembers a new shared folder containing the supplied guest permissions.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @param parent The ID of the parent folder
     * @param name The folders name
     * @param permissions The permissions to add
     * @return The inserted folder
     * @throws Exception
     */
    protected FolderObject insertSharedFolder(EnumAPI api, int module, int parent, String name, OCLPermission[] permissions) throws Exception {
        FolderObject sharedFolder = Create.createPrivateFolder(name, module, getClient().getValues().getUserId(), permissions);
        sharedFolder.setParentFolderID(parent);
        return insertFolder(api, sharedFolder);
    }

    /**
     * Inserts and remembers a new private folder.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @param parent The ID of the parent folder
     * @param name The folder's name
     * @return The inserted folder
     * @throws Exception
     */
    protected FolderObject insertPrivateFolder(EnumAPI api, int module, int parent, String name) throws Exception {
        FolderObject createdFolder = insertPrivateFolder(getClient(), api, module, parent, name);
        assertNotNull(createdFolder);
        remember(createdFolder);
        assertEquals("Folder name wrong", name, createdFolder.getFolderName());
        return createdFolder;
    }

    protected static FolderObject insertPrivateFolder(AJAXClient client, EnumAPI api, int module, int parent, String name) throws Exception {
        FolderObject privateFolder = Create.createPrivateFolder(name, module, client.getValues().getUserId());
        privateFolder.setParentFolderID(parent);
        return insertFolder(client, api, privateFolder);
    }

    /**
     * Inserts and remembers a new private folder.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @param parent The ID of the parent folder
     * @return The inserted folder
     * @throws Exception
     */
    protected FolderObject insertPrivateFolder(EnumAPI api, int module, int parent) throws Exception {
        return insertPrivateFolder(api, module, parent, randomUID());
    }

    protected static FolderObject insertPrivateFolder(AJAXClient client, EnumAPI api, int module, int parent) throws Exception {
        return insertPrivateFolder(client, api, module, parent, randomUID());
    }

    /**
     * Gets the public root folder based on the folder module, i.e. either {@link FolderObject#SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID} for the
     * infostore module, or {@link FolderObject#SYSTEM_PUBLIC_FOLDER_ID} for other folder.
     *
     * @param module The module to get the public root folder identifier for
     * @return The public root folder identifier
     */
    protected static int getPublicRoot(int module) {
        return FolderObject.INFOSTORE == module ? FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID : FolderObject.SYSTEM_PUBLIC_FOLDER_ID;
    }

    /**
     * Inserts a public folder below folder 2 or folder 15 if its a drive folder.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @return The inserted folder
     * @throws Exception
     */
    protected FolderObject insertPublicFolder(int module) throws Exception {
        FolderObject folder = new FolderObject();
        folder.setFolderName(randomUID());
        folder.setModule(module);
        folder.setType(FolderObject.PUBLIC);
        OCLPermission perm1 = new OCLPermission();
        perm1.setEntity(getClient().getValues().getUserId());
        perm1.setGroupPermission(false);
        perm1.setFolderAdmin(true);
        perm1.setAllPermission(OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION);
        folder.setPermissionsAsArray(new OCLPermission[] { perm1 });
        folder.setParentFolderID(getPublicRoot(module));

        InsertRequest request = new InsertRequest(EnumAPI.OX_OLD, folder, true);
        request.setNotifyPermissionEntities(Transport.MAIL);
        InsertResponse response = getClient().execute(request);
        response.fillObject(folder);
        return folder;
    }

    protected static FolderObject insertPublicFolder(AJAXClient client, EnumAPI api, int module) throws Exception {
        return insertPublicFolder(client, api, module, getPublicRoot(module), randomUID());
    }

    protected static FolderObject insertPublicFolder(AJAXClient client, EnumAPI api, int module, int parent, String name) throws Exception {
        FolderObject folder = new FolderObject();
        folder.setFolderName(name);
        folder.setParentFolderID(parent);
        folder.setModule(module);
        folder.setType(FolderObject.PUBLIC);
        OCLPermission perm1 = new OCLPermission();
        perm1.setEntity(client.getValues().getUserId());
        perm1.setGroupPermission(false);
        perm1.setFolderAdmin(true);
        perm1.setAllPermission(OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION);
        folder.setPermissionsAsArray(new OCLPermission[] { perm1 });
        return insertFolder(client, api, folder);
    }

    /**
     * Inserts and remembers a new file with random content and a random name.
     *
     * @param folderID The parent folder identifier
     * @return The inserted file
     * @throws Exception
     */
    protected File insertFile(int folderID) throws Exception {
        return insertFile(folderID, randomUID());
    }

    /**
     * Inserts and remembers a new file with random content and a random name.
     *
     * @param folderID The parent folder identifier
     * @param guestPermission The guest permission to assign
     * @return The inserted file
     * @throws Exception
     */
    protected File insertSharedFile(int folderID, FileStorageObjectPermission guestPermission) throws Exception {
        return insertSharedFile(folderID, randomUID(), guestPermission);
    }

    /**
     * Inserts and remembers a new file with random content.
     *
     * @param folderID The parent folder identifier
     * @param filename The filename to use
     * @return The inserted file
     * @throws Exception
     */
    protected File insertFile(int folderID, String filename) throws Exception {
        return insertSharedFile(folderID, filename, null);
    }

    /**
     * Inserts a new file with random content.
     *
     * @param client The client to use
     * @param folderID The parent folder identifier
     * @param filename The filename to use
     * @return The inserted file
     * @throws Exception
     */
    protected static File insertFile(AJAXClient client, int folderID, String filename) throws Exception {
        return insertSharedFile(client, folderID, filename, null);
    }

    /**
     * Inserts and remembers a new shared file with random content.
     *
     * @param folderID The parent folder identifier
     * @param filename The filename to use
     * @param permission The permission to assign
     * @return The inserted file
     * @throws Exception
     */
    protected File insertSharedFile(int folderID, String filename, FileStorageObjectPermission permission) throws Exception {
        byte[] contents = new byte[64 + random.nextInt(256)];
        random.nextBytes(contents);
        return insertSharedFile(folderID, filename, permission, contents);
    }

    /**
     * Inserts a new shared file with random content.
     *
     * @param client The client to use
     * @param folderID The parent folder identifier
     * @param filename The filename to use
     * @param permission The permission to assign
     * @return The inserted file
     * @throws Exception
     */
    protected static File insertSharedFile(AJAXClient client, int folderID, String filename, FileStorageObjectPermission permission) throws Exception {
        byte[] contents = new byte[64 + random.nextInt(256)];
        random.nextBytes(contents);
        return insertSharedFile(client, folderID, filename, permission, contents);
    }

    /**
     * Inserts and remembers a new shared file with random content.
     *
     * @param folderID The parent folder identifier
     * @param filename The filename to use
     * @param permission The permission to assign
     * @param data The file contents
     * @return The inserted file
     */
    protected File insertSharedFile(int folderID, String filename, FileStorageObjectPermission permission, byte[] data) throws Exception {
        return insertSharedFile(folderID, filename, null == permission ? null : Collections.singletonList(permission), data);
    }

    /**
     * Inserts a new shared file with random content.
     *
     * @param client The client to use
     * @param folderID The parent folder identifier
     * @param filename The filename to use
     * @param permission The permission to assign
     * @param data The file contents
     * @return The inserted file
     */
    protected static File insertSharedFile(AJAXClient client, int folderID, String filename, FileStorageObjectPermission permission, byte[] data) throws Exception {
        return insertSharedFile(client, folderID, filename, null == permission ? null : Collections.singletonList(permission), data);
    }

    /**
     * Inserts and remembers a new shared file with random content.
     *
     * @param folderID The parent folder identifier
     * @param filename The filename to use
     * @param permission The permission to assign
     * @param data The file contents
     * @return The inserted file
     * @throws Exception
     */
    protected File insertSharedFile(int folderID, String filename, List<FileStorageObjectPermission> permissions, byte[] data) throws Exception {
        File createdFile = insertSharedFile(getClient(), folderID, filename, permissions, data);
        remember(createdFile);
        return createdFile;
    }

    /**
     * Inserts a new shared file with random content.
     *
     * @param client The client to use
     * @param folderID The parent folder identifier
     * @param filename The filename to use
     * @param permission The permission to assign
     * @param data The file contents
     * @return The inserted file
     * @throws Exception
     */
    protected static File insertSharedFile(AJAXClient client, int folderID, String filename, List<FileStorageObjectPermission> permissions, byte[] data) throws Exception {
        DefaultFile metadata = new DefaultFile();
        metadata.setFolderId(String.valueOf(folderID));
        metadata.setFileName(filename);
        if (null != permissions) {
            metadata.setObjectPermissions(permissions);
        }
        NewInfostoreRequest newRequest = new NewInfostoreRequest(metadata, new ByteArrayInputStream(data));
        newRequest.setNotifyPermissionEntities(Transport.MAIL);
        NewInfostoreResponse newResponse = client.execute(newRequest);
        assertFalse(newResponse.getErrorMessage(), newResponse.hasError());
        String id = newResponse.getID();
        metadata.setId(id);
        GetInfostoreRequest getRequest = new GetInfostoreRequest(id);
        GetInfostoreResponse getResponse = client.execute(getRequest);
        File createdFile = getResponse.getDocumentMetadata();
        assertNotNull(createdFile);
        createdFile.setMeta(Collections.singletonMap("timestamp", getResponse.getTimestamp()));
        return createdFile;
    }

    protected interface RequestCustomizer<R extends AJAXRequest<?>> {

        void customize(R request);
    }

    /**
     * Updates and remembers a folder without cascading permission.
     *
     * @param api The folder tree to use
     * @param folder The folder to update
     * @return The updated folder
     * @throws Exception
     */
    protected FolderObject updateFolder(EnumAPI api, FolderObject folder) throws Exception {
        return updateFolder(getClient(), api, folder, false);
    }

    /**
     * Updates and remembers a folder without cascading permission.
     *
     * @param client The client to use
     * @param api The folder tree to use
     * @param folder The folder to update
     * @return The updated folder
     * @throws Exception
     */
    protected FolderObject updateFolder(AJAXClient client, EnumAPI api, FolderObject folder) throws Exception {
        return updateFolder(client, api, folder, false);
    }

    protected FolderObject updateFolder(EnumAPI api, FolderObject folder, final Transport transport) throws Exception {
        return updateFolder(api, folder, new RequestCustomizer<UpdateRequest>() {

            @Override
            public void customize(UpdateRequest request) {
                request.setNotifyPermissionEntities(transport);
            }
        });
    }

    /**
     * Updates and remembers a folder.
     *
     * @param api The folder tree to use
     * @param folder The folder to update
     * @param cascadePermissions If changed permissions shall be also applied to subfolders
     * @return The updated folder
     * @throws Exception
     */
    protected FolderObject updateFolder(EnumAPI api, FolderObject folder, final boolean cascadePermissions) throws Exception {
        return updateFolder(getClient(), api, folder, cascadePermissions);
    }

    /**
     * Updates and remembers a folder.
     *
     * @param client The client to use
     * @param api The folder tree to use
     * @param folder The folder to update
     * @param cascadePermissions If changed permissions shall be also applied to subfolders
     * @return The updated folder
     * @throws Exception
     */
    protected FolderObject updateFolder(AJAXClient client, EnumAPI api, FolderObject folder, final boolean cascadePermissions) throws Exception {
        return updateFolder(client, api, folder, new RequestCustomizer<UpdateRequest>() {

            @Override
            public void customize(UpdateRequest request) {
                request.setCascadePermissions(cascadePermissions);
            }
        });
    }

    /**
     * Updates and remembers a folder without cascading permission.
     *
     * @param api The folder tree to use
     * @param folder The folder to update
     * @param customizer The request customizer or <code>null</code>
     * @return The updated folder
     * @throws Exception
     */
    protected FolderObject updateFolder(EnumAPI api, FolderObject folder, RequestCustomizer<UpdateRequest> customizer) throws Exception {
        return updateFolder(getClient(), api, folder, customizer);
    }

    /**
     * Updates and remembers a folder without cascading permission.
     *
     * @param client The client to use
     * @param api The folder tree to use
     * @param folder The folder to update
     * @param customizer The request customizer or <code>null</code>
     * @return The updated folder
     * @throws Exception
     */
    protected FolderObject updateFolder(AJAXClient client, EnumAPI api, FolderObject folder, RequestCustomizer<UpdateRequest> customizer) throws Exception {
        UpdateRequest request = new UpdateRequest(api, folder);
        request.setNotifyPermissionEntities(Transport.MAIL);
        if (customizer != null) {
            customizer.customize(request);
        }
        InsertResponse insertResponse = client.execute(request);
        insertResponse.fillObject(folder);
        remember(folder);
        FolderObject updatedFolder = getFolder(api, folder.getObjectID(), client);
        assertNotNull(updatedFolder);
        assertEquals("Folder name wrong", folder.getFolderName(), updatedFolder.getFolderName());
        return updatedFolder;
    }

    /**
     * Updates and remembers a file.
     *
     * @param file The file to update
     * @param modifiedColumns The modified columns
     * @return The updated file, re-fetched from the server
     * @throws Exception
     */
    protected File updateFile(File file, Field[] modifiedColumns) throws Exception {
        return updateFile(file, modifiedColumns, null);
    }

    private static final Date FAR_FUTURE = new Date(Long.MAX_VALUE);

    /**
     * Updates and remembers a file.
     *
     * @param file The file to update
     * @param modifiedColumns The modified columns
     * @param customizer The request customizer or <code>null</code>
     * @return The updated file, re-fetched from the server
     * @throws Exception
     */
    protected File updateFile(File file, Field[] modifiedColumns, RequestCustomizer<UpdateInfostoreRequest> customizer) throws Exception {
        Date timestamp = file.getMeta() == null ? FAR_FUTURE : (Date) file.getMeta().getOrDefault("timestamp", FAR_FUTURE);
        UpdateInfostoreRequest updateInfostoreRequest = new UpdateInfostoreRequest(file, modifiedColumns, timestamp);
        updateInfostoreRequest.setNotifyPermissionEntities(Transport.MAIL);
        updateInfostoreRequest.setFailOnError(true);
        if (customizer != null) {
            customizer.customize(updateInfostoreRequest);
        }
        UpdateInfostoreResponse updateInfostoreResponse = getClient().execute(updateInfostoreRequest);
        assertFalse(updateInfostoreResponse.hasError());
        GetInfostoreRequest getInfostoreRequest = new GetInfostoreRequest(updateInfostoreResponse.getID());
        getInfostoreRequest.setFailOnError(true);
        GetInfostoreResponse getInfostoreResponse = getClient().execute(getInfostoreRequest);
        File result = getInfostoreResponse.getDocumentMetadata();
        result.setMeta(Collections.singletonMap("timestamp", getInfostoreResponse.getTimestamp()));
        return result;
    }

    /**
     * Gets a file by ID.
     *
     * @param id The identifier of the file to get
     * @return The file
     * @throws Exception
     */
    protected File getFile(String id) throws Exception {
        GetInfostoreRequest getInfostoreRequest = new GetInfostoreRequest(id);
        getInfostoreRequest.setFailOnError(true);
        GetInfostoreResponse getInfostoreResponse = getClient().execute(getInfostoreRequest);
        return getInfostoreResponse.getDocumentMetadata();
    }

    /**
     * Gets a folder by ID.
     *
     * @param api The folder API to use
     * @param objectID The ID of the folder to get
     * @return The folder
     * @throws Exception
     */
    protected FolderObject getFolder(EnumAPI api, int objectID) throws Exception {
        return getFolder(api, objectID, getClient());
    }

    /**
     * Gets a folder by ID with the given getClient().
     *
     * @param api The folder API to use
     * @param objectID The ID of the folder to get
     * @return The folder
     * @throws Exception
     */
    protected static FolderObject getFolder(EnumAPI api, int objectID, AJAXClient client) throws Exception {
        GetResponse getResponse = client.execute(new GetRequest(api, objectID));
        FolderObject folder = getResponse.getFolder();
        folder.setLastModified(getResponse.getTimestamp());
        return folder;
    }

    protected FolderObject insertFolder(EnumAPI api, FolderObject folder) throws Exception {
        FolderObject createdFolder = insertFolder(getClient(), api, folder);
        assertNotNull(createdFolder);
        remember(createdFolder);
        assertEquals("Folder name wrong", folder.getFolderName(), createdFolder.getFolderName());
        return createdFolder;
    }

    protected static FolderObject insertFolder(AJAXClient client, EnumAPI api, FolderObject folder) throws Exception {
        InsertRequest insertRequest = new InsertRequest(api, folder, client.getValues().getTimeZone());
        insertRequest.setNotifyPermissionEntities(Transport.MAIL);
        InsertResponse insertResponse = client.execute(insertRequest);
        insertResponse.fillObject(folder);
        return getFolder(api, folder.getObjectID(), client);
    }

    /**
     * Remembers a folder for cleanup.
     *
     * @param folder The folder to remember
     */
    protected void remember(FolderObject folder) {
        if (null != folder) {
            foldersToDelete.put(Integer.valueOf(folder.getObjectID()), folder);
        }
    }

    /**
     * Remembers a file for cleanup.
     *
     * @param file The file to remember
     */
    protected void remember(File file) {
        if (null != file) {
            filesToDelete.put(file.getId(), file);
        }
    }

    /**
     * Gets all folder shares of a specific module.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @return The folder shares
     */
    protected List<FolderShare> getFolderShares(EnumAPI api, int module) throws Exception {
        return getFolderShares(getClient(), api, module);
    }

    /**
     * Gets all folder shares of a specific module.
     *
     * @param client The ajax client to use
     * @param api The folder tree to use
     * @param module The module identifier
     * @return The folder shares
     */
    protected static List<FolderShare> getFolderShares(AJAXClient client, EnumAPI api, int module) throws Exception {
        return client.execute(new FolderSharesRequest(api, Module.getModuleString(module, -1))).getShares(client.getValues().getTimeZone());
    }

    /**
     * Gets all folder shares of a specific module.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @return The folder shares
     */
    protected List<FileShare> getFileShares() throws Exception {
        return getFileShares(getClient());
    }

    /**
     * Gets all folder shares of a specific module.
     *
     * @param client The ajax client to use
     * @param api The folder tree to use
     * @param module The module identifier
     * @return The folder shares
     */
    protected static List<FileShare> getFileShares(AJAXClient client) throws Exception {
        return client.execute(new FileSharesRequest()).getShares(client.getValues().getTimeZone());
    }

    /**
     * Finds the first guest permission for the given folder based on the contained OCL permissions.
     *
     * @param folder The folder
     * @return The permission or <code>null</code> if none exists
     */
    protected OCLPermission findFirstGuestPermission(FolderObject folder) throws Exception {
        OCLPermission matchingPermission = null;
        for (OCLPermission permission : folder.getPermissions()) {
            if (!permission.isGroupPermission() && permission.getEntity() != getClient().getValues().getUserId()) {
                com.openexchange.ajax.user.actions.GetResponse getResponse = getClient().execute(new com.openexchange.ajax.user.actions.GetRequest(permission.getEntity(), getClient().getValues().getTimeZone(), true));
                if (getResponse.getUser().isGuest()) {
                    matchingPermission = permission;
                    break;
                }
            }
        }

        return matchingPermission;
    }

    /**
     * Discovers the share URL based on the supplied guest permission entity by either reading the share URL property directly in case
     * the guest entity points to an anonymous share, or by fetching and parsing the notification message for the recipient in case he is
     * an invited guest.
     *
     * @param client The api client to use
     * @param guestEntity The guest entity
     * @return The share URL, or <code>null</code> if not found
     */
    protected String discoverShareURL(ApiClient client, ExtendedPermissionEntity guestEntity) throws Exception {
        switch (guestEntity.getType()) {
            case ANONYMOUS:
                return guestEntity.getShareURL();
            case GUEST:
                assertNotNull("No contact in guest entity", guestEntity.getContact());
                String email = guestEntity.getContact().getEmail1();
                assertNotNull("No mail address in guest entity", email);
                return discoverInvitationLink(client, email);
            default:
                fail("unexpected recipient type: " + guestEntity.getType());
                break;
        }
        return null;
    }

    /**
     * Fetches the currently stored e-mail messages on the server and discovers an invitation message sent to a specific recipient.
     *
     * @param client The ajax client to use
     * @param emailAddress The guest's e-mail address to search for
     * @return The message, or <code>null</code> if not found
     */
    protected MailData discoverInvitationMessage(ApiClient client, String emailAddress) throws Exception {
        return discoverInvitationMessage(client, emailAddress, 10000L);
    }

    /**
     * Fetches the currently stored e-mail messages on the server and discovers an invitation message sent to a specific recipient.
     *
     * @param client The ajax client to use
     * @param emailAddress The guest's e-mail address to search for
     * @param timeout The maximum timeout to wait for the message to arrive, or <code>0</code> to only check once
     * @return The message, or <code>null</code> if not found
     */
    protected MailData discoverInvitationMessage(ApiClient client, String emailAddress, long timeout) throws Exception {
        MailManager mailManager = new MailManager(client);
        if (0 >= timeout) {
            return discoverInvitationMessage(mailManager.getMails(), emailAddress);
        }
        long until = System.currentTimeMillis() + timeout;
        do {
            MailData message = discoverInvitationMessage(mailManager.getMails(), emailAddress);
            if (null != message) {
                return message;
            }
            Thread.sleep(500);
        } while (System.currentTimeMillis() < until);
        return null;
    }

    protected boolean awaitGuest8Cleanup(int guestID, long timeout) throws Exception {
        long until = System.currentTimeMillis() + timeout;
        do {
            com.openexchange.ajax.user.actions.GetResponse response = getClient().execute(new com.openexchange.ajax.user.actions.GetRequest(guestID, TimeZones.UTC, false));
            if (response.hasError() && null != response.getException()) {
                OXException e = response.getException();
                if ("USR-0010".equals(e.getErrorCode())) {
                    return true;
                } else if ("CON-0125".equals(e.getErrorCode())) {
                    // partly okay
                } else {
                    throw e;
                }
            }
            Thread.sleep(500);
        } while (System.currentTimeMillis() < until);
        return false;
    }

    /**
     * Discovers the invitation message sent to a specific recipient.
     *
     * @param messages The e-mail messages to check
     * @param emailAddress The guest's e-mail address to search for
     * @return The message, or <code>null</code> if not found
     */
    protected MailData discoverInvitationMessage(List<MailData> messages, String emailAddress) {
        for (MailData message : messages) {
            String toHeader = ((Map<String, String>) message.getHeaders()).get("Delivered-To");
            if (Strings.isNotEmpty(toHeader)) {
                InternetAddress[] addresses = null;
                try {
                    addresses = InternetAddress.parseHeader(toHeader, false);
                } catch (AddressException e) {
                    fail(e.getMessage());
                }
                if (null != addresses && 0 < addresses.length) {
                    for (InternetAddress address : addresses) {
                        if (emailAddress.equals(address.getAddress())) {
                            return message;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Fetches the currently stored e-mail messages on the server and discovers an invitation message sent to a specific recipient. If
     * found, the share URL link is extracted and returned.
     *
     * @param client The ajax client to use
     * @param emailAddress The guest's e-mail address to search for
     * @return The share URL, or <code>null</code> if not found
     */
    protected String discoverInvitationLink(ApiClient client, String emailAddress) throws Exception {
        MailData message = discoverInvitationMessage(client, emailAddress);
        if (null != message) {
            return ((Map<String, String>) message.getHeaders()).get("X-Open-Xchange-Share-URL");
        }
        // Wait another 5 seconds for the mail to arrive
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        message = discoverInvitationMessage(client, emailAddress);
        if (null != message) {
            return ((Map<String, String>) message.getHeaders()).get("X-Open-Xchange-Share-URL");
        }
        return null;
    }

    /**
     * Discovers a specific guest permission entity amongst all available shares of the current user, based on the folder- and guest identifiers.
     *
     * @param api The folder tree to use
     * @param module The module identifier
     * @param folderID The folder ID to discover the share for
     * @param guest The ID of the guest associated to the share
     * @return The guest permission entity, or <code>null</code> if not found
     */
    protected ExtendedPermissionEntity discoverGuestEntity(EnumAPI api, int module, int folderID, int guest) throws Exception {
        return discoverGuestEntity(getClient(), api, module, folderID, guest);
    }

    /**
     * Discovers a specific guest permission entity amongst all available shares of the current user, based on the folder- and guest identifiers.
     *
     * @param client The ajax client to use
     * @param folderID The folder ID to discover the share for
     * @param guest The ID of the guest associated to the share
     * @return The share, or <code>null</code> if not found
     */
    protected static ExtendedPermissionEntity discoverGuestEntity(AJAXClient client, EnumAPI api, int module, int folderID, int guest) throws Exception {
        List<FolderShare> shares = getFolderShares(client, api, module);
        for (FolderShare share : shares) {
            if (share.getObjectID() == folderID) {
                return discoverGuestEntity(share.getExtendedPermissions(), guest);
            }
        }
        return null;
    }

    /**
     * Discovers a specific guest permission entity amongst all available shares of the current user, based on the file- and guest identifiers.
     *
     * @param item The item ID to discover the share for
     * @param guest The ID of the guest associated to the share
     * @return The guest permission entity, or <code>null</code> if not found
     */
    protected ExtendedPermissionEntity discoverGuestEntity(String item, int guest) throws Exception {
        return discoverGuestEntity(getClient(), item, guest);
    }

    /**
     * Discovers a specific guest permission entity amongst all available shares of the current user, based on the file- and guest identifiers.
     *
     * @param client The ajax client to use
     * @param item The item ID to discover the share for
     * @param guest The ID of the guest associated to the share
     * @return The share, or <code>null</code> if not found
     */
    protected static ExtendedPermissionEntity discoverGuestEntity(AJAXClient client, String item, int guest) throws Exception {
        List<FileShare> shares = getFileShares(client);
        for (FileShare share : shares) {
            if (share.getId().equals(item)) {
                return discoverGuestEntity(share.getExtendedPermissions(), guest);
            }
        }
        return null;
    }

    /**
     * Discovers a specific share amongst all available shares of the current user, based on the folder- and guest identifiers.
     *
     * @param client The ajax client to use
     * @param folderID The folder ID to discover the share for
     * @param guest The ID of the guest associated to the share
     * @return The share, or <code>null</code> if not found
     */
    protected static ExtendedPermissionEntity discoverGuestEntity(List<ExtendedPermissionEntity> entities, int guest) {
        if (null != entities) {
            for (ExtendedPermissionEntity entity : entities) {
                if (entity.getEntity() == guest) {
                    return entity;
                }
            }
        }
        return null;
    }

    protected static Date futureTimestamp() {
        return new Date(System.currentTimeMillis() + 1000000);
    }

    protected static void deleteFoldersSilently(AJAXClient client, Map<Integer, FolderObject> foldersToDelete) throws Exception {
        deleteFoldersSilently(client, foldersToDelete.keySet());
    }

    protected static void deleteFoldersSilently(AJAXClient client, Collection<Integer> foldersIDs) throws Exception {
        if (null != client && null != foldersIDs && 0 < foldersIDs.size()) {
            DeleteRequest deleteRequest = new DeleteRequest(EnumAPI.OX_NEW, Autoboxing.I2i(foldersIDs), futureTimestamp());
            deleteRequest.setHardDelete(Boolean.TRUE);
            client.execute(deleteRequest);
        }
    }

    protected static void deleteFilesSilently(AJAXClient client, Collection<File> files) throws Exception {
        if (null != client && null != files && 0 < files.size()) {
            List<String> folderIDs = new ArrayList<String>();
            List<String> fileIDs = new ArrayList<String>();
            for (File file : files) {
                if (file != null) {
                    folderIDs.add(file.getFolderId());
                    fileIDs.add(file.getId());
                }
            }
            DeleteInfostoreRequest deleteInfostoreRequest = new DeleteInfostoreRequest(fileIDs, folderIDs, futureTimestamp());
            deleteInfostoreRequest.setHardDelete(Boolean.TRUE);
            client.execute(deleteInfostoreRequest);
        }
    }

    /**
     * Resolves the share behind a guest permission, i.e. accesses the share link and authenticates using the guest's credentials.
     *
     * @param guestPermission The guest permission entity
     * @param recipient The recipient
     * @return An authenticated guest client being able to access the share
     */
    protected GuestClient resolveShare(ExtendedPermissionEntity guestPermission, ShareRecipient recipient, ApiClient apiClient) throws Exception {
        String shareURL = discoverShareURL(apiClient, guestPermission);
        assertNotNull("Got no share URL for " + recipient, shareURL);
        return new GuestClient(shareURL, recipient);
    }

    /**
     * Resolves the share, i.e. accesses the share link and authenticates using the guest's credentials.
     *
     * @param shareURL The share URL
     * @param recipient The recipient
     * @return An authenticated guest client being able to access the share
     */
    protected GuestClient resolveShare(String shareURL, ShareRecipient recipient) throws Exception {
        return new GuestClient(shareURL, recipient);
    }

    /**
     * Resolves the share behind a guest permission, i.e. accesses the share link and authenticates using the supplied credentials.
     *
     * @param guestPermission The guest permission entity
     * @param username The username, or <code>null</code> if not needed
     * @param password The password, or <code>null</code> if not needed
     * @return An authenticated guest client being able to access the share
     */
    protected GuestClient resolveShare(ExtendedPermissionEntity guestPermission, String username, String password) throws Exception {
        return resolveShare(guestPermission.getShareURL(), username, password);
    }

    /**
     * Resolves the supplied share url, i.e. accesses the share link and authenticates using the supplied credentials.
     *
     * @param url The share URL
     * @param username The username, or <code>null</code> if not needed
     * @param password The password, or <code>null</code> if not needed
     * @return An authenticated guest client being able to access the share
     */
    protected GuestClient resolveShare(String url, String username, String password) throws Exception {
        return new GuestClient(url, username, password);
    }

    /**
     * Resolves the supplied share url without authentication, i.e. expects that no password
     * exists for the guest user and resolving the share results in a direct redirect into
     * the App Suite UI.
     *
     * @param url The share URL
     * @return An authenticated guest client being able to access the share
     */
    protected GuestClient resolveShare(String url) throws Exception {
        return new GuestClient(new ClientConfig(url));
    }

    protected boolean awaitGuestCleanup(int guestID, long timeout) throws Exception {
        long until = System.currentTimeMillis() + timeout;
        do {
            com.openexchange.ajax.user.actions.GetResponse response = getClient().execute(new com.openexchange.ajax.user.actions.GetRequest(guestID, TimeZones.UTC, false));
            if (response.hasError() && null != response.getException()) {
                OXException e = response.getException();
                if ("USR-0010".equals(e.getErrorCode())) {
                    return true;
                } else if ("CON-0125".equals(e.getErrorCode())) {
                    // partly okay
                } else {
                    throw e;
                }
            }
            Thread.sleep(500);
        } while (System.currentTimeMillis() < until);
        return false;
    }

    protected void checkGuestUserDeleted(int guestID) throws Exception {
        assertTrue("Guest user " + guestID + " not deleted after " + CLEANUP_DELAY + "ms", awaitGuestCleanup(guestID, CLEANUP_DELAY));
    }

    /**
     * Checks the supplied OCL permissions against the expected permissions.
     *
     * @param expected The expected permissions
     * @param actual The actual permissions
     */
    protected static void checkPermissions(OCLPermission expected, OCLPermission actual) {
        assertEquals("Permission wrong", expected.getDeletePermission(), actual.getDeletePermission());
        assertEquals("Permission wrong", expected.getFolderPermission(), actual.getFolderPermission());
        assertEquals("Permission wrong", expected.getReadPermission(), actual.getReadPermission());
        assertEquals("Permission wrong", expected.getWritePermission(), actual.getWritePermission());
    }

    /**
     * Checks the supplied object permissions against the expected permissions.
     *
     * @param expected The expected permissions
     * @param actual The actual permissions
     */
    protected static void checkPermissions(FileStorageObjectPermission expected, FileStorageObjectPermission actual) {
        assertEquals("Permission wrong", B(expected.canDelete()), B(actual.canDelete()));
        assertEquals("Permission wrong", B(expected.canWrite()), B(actual.canWrite()));
        assertEquals("Permission wrong", B(expected.canRead()), B(actual.canRead()));
    }

    /**
     * Checks the supplied extended permission entity against the expected object permissions.
     *
     * @param expectedPermission The expected permissions
     * @param actual The actual extended permission
     */
    protected static void checkGuestPermission(FileStorageObjectPermission expectedPermission, ExtendedPermissionEntity actual) {
        assertNotNull("No guest permission entitiy", actual);
        checkPermissions(expectedPermission, actual.toObjectPermission());
        if (FileStorageGuestObjectPermission.class.isInstance(expectedPermission)) {
            checkRecipient(((FileStorageGuestObjectPermission) expectedPermission).getRecipient(), actual);
        } else {
            assertEquals("Entity ID wrong", actual.getEntity(), expectedPermission.getEntity());
            assertEquals("Recipient type wrong", actual.getType(), expectedPermission.isGroup() ? RecipientType.GROUP : RecipientType.USER);
        }
    }

    /**
     * Checks the supplied extended guest permission against the expected guest permissions.
     *
     * @param expectedPermission The expected permissions
     * @param actual The actual extended permission
     */
    protected static void checkGuestPermission(OCLPermission expectedPermission, ExtendedPermissionEntity actual) {
        assertNotNull("No guest permission entitiy for entity " + expectedPermission.getEntity(), actual);
        checkPermissions(expectedPermission, actual.toFolderPermission(expectedPermission.getFuid()));
        if (OCLGuestPermission.class.isInstance(expectedPermission)) {
            checkRecipient(((OCLGuestPermission) expectedPermission).getRecipient(), actual);
        } else {
            assertEquals("Entity ID wrong", actual.getEntity(), expectedPermission.getEntity());
            assertEquals("Recipient type wrong", actual.getType(), expectedPermission.isGroupPermission() ? RecipientType.GROUP : RecipientType.USER);
        }
    }

    /**
     * Checks the supplied extended guest permission against the expected guest permissions.
     *
     * @param expectedPermission The expected permissions
     * @param actual The actual extended permission
     */
    protected static void checkGuestPermission(OCLGuestPermission expectedPermission, ExtendedPermissionEntity actual) {
        assertNotNull("No guest permission entity for " + expectedPermission.getName(), actual);
        checkGuestPermission((OCLPermission) expectedPermission, actual);
    }

    private static void checkRecipient(ShareRecipient expected, ExtendedPermissionEntity actual) {
        assertEquals("Wrong recipient type", expected.getType(), actual.getType());
        if (RecipientType.ANONYMOUS.equals(expected.getType())) {
            AnonymousRecipient anonymousRecipient = (AnonymousRecipient) expected;
            assertEquals("Wrong password", anonymousRecipient.getPassword(), actual.getPassword());
            assertEquals("Expiry date wrong", anonymousRecipient.getExpiryDate(), actual.getExpiry());
        } else if (RecipientType.GUEST.equals(expected.getType())) {
            GuestRecipient guestRecipient = (GuestRecipient) expected;
            assertEquals("Wrong display name", guestRecipient.getDisplayName(), actual.getDisplayName());
            assertNotNull("No contact", actual.getContact());
            assertEquals("Wrong e-mail address", guestRecipient.getEmailAddress(), actual.getContact().getEmail1());
        }
    }

    private static OCLGuestPermission createNamedGuestPermission(String emailAddress, String displayName, String password) {
        OCLGuestPermission guestPermission = createNamedPermission(emailAddress, displayName, password);
        guestPermission.setAllPermission(OCLPermission.READ_FOLDER, OCLPermission.READ_ALL_OBJECTS, OCLPermission.NO_PERMISSIONS, OCLPermission.NO_PERMISSIONS);
        guestPermission.getRecipient().setBits(guestPermission.getPermissionBits());
        return guestPermission;
    }

    private static OCLGuestPermission createNamedAuthorPermission(String emailAddress, String displayName, String password) {
        return createNamedAuthorPermission(-1, emailAddress, displayName, password);
    }

    private static OCLGuestPermission createNamedAuthorPermission(int userId, String emailAddress, String displayName, String password) {
        OCLGuestPermission guestPermission = createNamedPermission(emailAddress, displayName, password);
        guestPermission.setAllPermission(OCLPermission.CREATE_OBJECTS_IN_FOLDER, OCLPermission.READ_ALL_OBJECTS, OCLPermission.WRITE_ALL_OBJECTS, OCLPermission.NO_PERMISSIONS);
        guestPermission.getRecipient().setBits(guestPermission.getPermissionBits());
        guestPermission.setEntity(userId);
        return guestPermission;
    }

    protected static OCLGuestPermission createNamedPermission(String emailAddress, String displayName, String password) {
        OCLGuestPermission guestPermission = new OCLGuestPermission();
        GuestRecipient guestRecipient = new GuestRecipient();
        guestRecipient.setEmailAddress(emailAddress);
        guestRecipient.setDisplayName(displayName);
        guestRecipient.setPassword(password);
        guestPermission.setRecipient(guestRecipient);
        guestPermission.setGroupPermission(false);
        guestPermission.setFolderAdmin(false);
        guestPermission.getRecipient().setBits(guestPermission.getPermissionBits());
        return guestPermission;
    }

    protected static OCLGuestPermission createNamedPermission(String emailAddress, String displayName) {
        OCLGuestPermission guestPermission = new OCLGuestPermission();
        GuestRecipient guestRecipient = new GuestRecipient();
        guestRecipient.setEmailAddress(emailAddress);
        guestRecipient.setDisplayName(displayName);
        guestPermission.setRecipient(guestRecipient);
        guestPermission.setGroupPermission(false);
        guestPermission.setFolderAdmin(false);
        guestPermission.getRecipient().setBits(guestPermission.getPermissionBits());
        return guestPermission;
    }

    protected OCLGuestPermission createGuestPermission(GuestPermissionType permissionType) throws ApiException {
        OCLGuestPermission permission = null;
        switch (permissionType) {
            case ANONYMOUS_GUEST_PERMISSION_WITH_PASSWORD:
                permission = createAnonymousGuestPermission("secret");
                break;
            case ANONYMOUS_GUEST_PERMSSION:
                permission = createAnonymousGuestPermission();
                break;
            case NAMED_AUTHOR_PERMISSION:
                permission = createNamedAuthorPermission();
                break;
            case NAMED_GUEST_PERMISSION:
                permission = createNamedGuestPermission();
                break;
            default:
                break;
        }
        return permission;
    }

    protected OCLGuestPermission createNamedGuestPermission() throws ApiException {
        return createNamedGuestPermission(true);
    }

    /**
     *
     * Creates a {@link OCLGuestPermission} from a new user in the guest context
     *
     * @param usePassword Whether to use the users password or not
     * @return The permissions
     * @throws ApiException If client can't be created
     */
    protected OCLGuestPermission createNamedGuestPermission(boolean usePassword) throws ApiException {
        TestUser namedGuest = guestContext.acquireUser();
        return createNamedGuestPermission(namedGuest, usePassword);
    }

    /**
     *
     * Creates a named guest permission
     *
     * @param testUser The test user to create the permission for
     * @param usePassword Whether to use the testUser`s password or not
     * @return The {@link OCLGuestPermission}
     * @throws ApiException If client can't be created
     */
    protected OCLGuestPermission createNamedGuestPermission(TestUser testUser, boolean usePassword) throws ApiException {
        OCLGuestPermission permission;
        permission = createNamedGuestPermission(testUser.getLogin(), testUser.getUser(), usePassword ? testUser.getPassword() : null);
        permission.setApiClient(testUser.getApiClient());
        return permission;
    }

    protected OCLGuestPermission createNamedAuthorPermission() throws ApiException {
        return createNamedAuthorPermission(true);
    }

    /**
     *
     * Creates a name author permission for a new user from the guest context
     *
     * @param usePassword Whether to use the users password or not
     * @return The permissions
     * @throws ApiException If client can't be created
     */
    protected OCLGuestPermission createNamedAuthorPermission(boolean usePassword) throws ApiException {
        OCLGuestPermission permission;
        TestUser namedAuthor = guestContext.acquireUser();
        permission = createNamedAuthorPermission(namedAuthor.getLogin(), namedAuthor.getUser(), usePassword ? namedAuthor.getPassword() : null);
        permission.setApiClient(namedAuthor.getApiClient());
        return permission;
    }

    protected static OCLGuestPermission createAnonymousGuestPermission(String password) {
        OCLGuestPermission guestPermission = createAnonymousPermission(password);
        guestPermission.setAllPermission(OCLPermission.READ_FOLDER, OCLPermission.READ_ALL_OBJECTS, OCLPermission.NO_PERMISSIONS, OCLPermission.NO_PERMISSIONS);
        guestPermission.getRecipient().setBits(guestPermission.getPermissionBits());
        return guestPermission;
    }

    protected static OCLGuestPermission createAnonymousGuestPermission() {
        return createAnonymousGuestPermission(null);
    }

    protected static OCLGuestPermission createAnonymousPermission(String password) {
        AnonymousRecipient recipient = new AnonymousRecipient();
        recipient.setPassword(password);
        OCLGuestPermission guestPermission = new OCLGuestPermission(recipient);
        AnonymousRecipient anonymousRecipient = new AnonymousRecipient();
        anonymousRecipient.setPassword(password);
        guestPermission.setRecipient(anonymousRecipient);
        guestPermission.setGroupPermission(false);
        guestPermission.setFolderAdmin(false);
        guestPermission.getRecipient().setBits(guestPermission.getPermissionBits());
        return guestPermission;
    }

    protected static FileStorageGuestObjectPermission asObjectPermission(OCLPermission guestPermission) {
        DefaultFileStorageGuestObjectPermission objectPermission = new DefaultFileStorageGuestObjectPermission();
        objectPermission.setEntity(guestPermission.getEntity());
        objectPermission.setGroup(guestPermission.isGroupPermission());
        if (guestPermission instanceof OCLGuestPermission) {
            objectPermission.setRecipient(((OCLGuestPermission) guestPermission).getRecipient());
        }
        if (guestPermission.canWriteAllObjects()) {
            objectPermission.setPermissions(FileStorageObjectPermission.WRITE);
        } else if (guestPermission.canReadAllObjects()) {
            objectPermission.setPermissions(FileStorageObjectPermission.READ);
        }
        objectPermission.getRecipient().setBits(objectPermission.getPermissions());
        return objectPermission;
    }

    protected int getDefaultFolder(int module) throws Exception {
        return getDefaultFolder(getClient(), module);
    }

    protected static int getDefaultFolder(AJAXClient client, int module) throws Exception {
        switch (module) {
            case FolderObject.CONTACT:
                return client.getValues().getPrivateContactFolder();
            case FolderObject.CALENDAR:
                return client.getValues().getPrivateAppointmentFolder();
            case FolderObject.INFOSTORE:
                return client.getValues().getPrivateInfostoreFolder();
            case FolderObject.TASK:
                return client.getValues().getPrivateTaskFolder();
            default:
                Assert.fail("No default folder for moduel: " + module);
                return 0;
        }
    }

    protected static String randomUID() {
        return UUIDs.getUnformattedString(UUID.randomUUID());
    }

    protected static int randomModule() {
        return TESTED_MODULES[random.nextInt(TESTED_MODULES.length)];
    }

    protected static EnumAPI randomFolderAPI() {
        return TESTED_FOLDER_APIS[random.nextInt(TESTED_FOLDER_APIS.length)];
    }

    protected OCLGuestPermission randomGuestPermission(int module) throws ApiException {
        OCLGuestPermission permission = null;
        do {
            permission = randomGuestPermission();
        } while (false == isReadOnly(permission) && isReadOnlySharing(module));
        return permission;
    }

    protected OCLGuestPermission randomGuestPermission() throws ApiException {

        GuestPermissionType[] permissionTypes = GuestPermissionType.values();
        GuestPermissionType permissionType = permissionTypes[random.nextInt(permissionTypes.length)];
        return createGuestPermission(permissionType);
    }

    protected OCLGuestPermission randomGuestPermission(RecipientType type, int module) throws ApiException {
        OCLGuestPermission permission;
        do {
            permission = randomGuestPermission(module);
        } while (false == type.equals(permission.getRecipient().getType()));
        return permission;
    }

    protected OCLGuestPermission randomGuestPermission(RecipientType type) throws ApiException {
        OCLGuestPermission permission;
        do {
            permission = randomGuestPermission();
        } while (false == type.equals(permission.getRecipient().getType()));
        return permission;
    }

    protected FileStorageGuestObjectPermission randomGuestObjectPermission() throws ApiException {
        return asObjectPermission(randomGuestPermission());
    }

    protected FileStorageGuestObjectPermission randomGuestObjectPermission(RecipientType type) throws ApiException {
        FileStorageGuestObjectPermission permission;
        do {
            permission = asObjectPermission(randomGuestPermission());
        } while (false == type.equals(permission.getRecipient().getType()));
        return permission;
    }

    /**
     * Gets a value indicating whether a specific module enforces "read-only" sharing for guests or not.
     *
     * @param module The folder module to check
     * @return <code>true</code> if the module may be used for "read-only" sharing to guest users only, <code>false</code>, otherwise
     */
    protected static boolean isReadOnlySharing(int module) {
        return FolderObject.CALENDAR == module || FolderObject.TASK == module || FolderObject.CONTACT == module;
    }

    /**
     * Gets a value indicating whether a specific permission is "read-only" or not.
     *
     * @param permission The permission to check
     * @return <code>true</code> if the permission is considered "read-only", false, otherwise
     */
    protected static boolean isReadOnly(OCLPermission permission) {
        return false == (permission.canWriteOwnObjects() || permission.canCreateObjects() || permission.canDeleteOwnObjects());
    }

    /**
     * Gets a value indicating whether a specific permission is "read-only" or not.
     *
     * @param permission The permission to check
     * @return <code>true</code> if the permission is considered "read-only", false, otherwise
     */
    protected static boolean isReadOnly(FileStorageObjectPermission permission) {
        return false == (permission.canDelete() || permission.canWrite());
    }

    /**
     * Gets the username to use for login from the supplied share recipient.
     *
     * @param recipient The recipient
     * @return The username
     */
    public static String getUsername(ShareRecipient recipient) {
        switch (recipient.getType()) {
            case ANONYMOUS:
                return recipient.getType().toString().toLowerCase();
            case GUEST:
                return ((GuestRecipient) recipient).getEmailAddress();
            default:
                Assert.fail("Unknown recipient: " + recipient.getType());
                return null;
        }
    }

    /**
     * Gets the password to use for login from the supplied share recipient.
     *
     * @param recipient The recipient
     * @return The password, or <code>null</code> if not needed
     */
    public static String getPassword(ShareRecipient recipient) {
        switch (recipient.getType()) {
            case ANONYMOUS:
                return ((AnonymousRecipient) recipient).getPassword();
            case GUEST:
                return ((GuestRecipient) recipient).getPassword();
            default:
                Assert.fail("Unknown recipient: " + recipient.getType());
                return null;
        }
    }

}
