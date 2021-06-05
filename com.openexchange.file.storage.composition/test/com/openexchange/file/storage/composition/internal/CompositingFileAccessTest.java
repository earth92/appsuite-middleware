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

package com.openexchange.file.storage.composition.internal;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.openexchange.datatypes.genericonf.DynamicFormDescription;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.DefaultFile;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageAccountAccess;
import com.openexchange.file.storage.FileStorageAccountManager;
import com.openexchange.file.storage.FileStorageAccountManagerProvider;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FileStorageFileAccess.SortDirection;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageFolderAccess;
import com.openexchange.file.storage.FileStorageLockedFileAccess;
import com.openexchange.file.storage.FileStorageService;
import com.openexchange.file.storage.FileStorageVersionedFileAccess;
import com.openexchange.file.storage.composition.FileID;
import com.openexchange.file.storage.composition.FolderID;
import com.openexchange.file.storage.registry.FileStorageServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.session.SimSession;
import com.openexchange.sim.SimBuilder;
import com.openexchange.threadpool.SimThreadPoolService;
import com.openexchange.threadpool.ThreadPools;

/**
 * {@link CompositingFileAccessTest}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ ThreadPools.class })
public class CompositingFileAccessTest extends AbstractCompositingIDBasedFileAccess implements FileStorageService, FileStorageAccountAccess, FileStorageAccountManager {

    // private static final InputStream EMPTY_INPUT_STREAM = new ByteArrayInputStream(new byte[0]);

    String serviceId;

    private FileStorageFileAccess files;

    private final SimBuilder fileAccess = new SimBuilder();

    private String accountId;

    private final FileID fileId = new FileID("com.openexchange.test", "account 23", "folder", "id");

    private final FolderID folderId = new FolderID(fileId.getService(), fileId.getAccountId(), fileId.getFolderId());

    String serviceId2;

    String accountId2;

    final FileID fileId2 = new FileID("com.openexchange.test2", "account 12", "folder2", "id2");

    public CompositingFileAccessTest() {
        super(new SimSession());
    }

     @Test
     public void testExists() throws OXException {
        fileAccess.expectCall("exists", fileId.getFolderId(), fileId.getFileId(), "12").andReturn(Boolean.TRUE);

        assertTrue(exists(fileId.toUniqueID(), "12"));
        verifyAccount();

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetDeltaWithoutSort() throws OXException {

        fileAccess.expectCall("getDelta", folderId.getFolderId(), L(12L), Arrays.asList(
            File.Field.TITLE,
            File.Field.ID,
            File.Field.FOLDER_ID,
            File.Field.LAST_MODIFIED), Boolean.TRUE);

        getDelta(folderId.toUniqueID(), 12, Arrays.asList(File.Field.TITLE), true);

        verifyAccount();
        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetDeltaWithSort() throws OXException {

        fileAccess.expectCall("getDelta", folderId.getFolderId(), L(12L), Arrays.asList(
            File.Field.TITLE,
            File.Field.ID,
            File.Field.FOLDER_ID,
            File.Field.LAST_MODIFIED), File.Field.TITLE, SortDirection.DESC, Boolean.TRUE);

        getDelta(folderId.toUniqueID(), 12, Arrays.asList(File.Field.TITLE), File.Field.TITLE, SortDirection.DESC, true);

        verifyAccount();
        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetDocument() throws OXException {
        fileAccess.expectCall("getDocument", fileId.getFolderId(), fileId.getFileId(), "12");

        getDocument(fileId.toUniqueID(), "12");

        verifyAccount();
        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetDocuments1() throws OXException {
        fileAccess.expectCall("getDocuments", folderId.getFolderId());

        getDocuments(folderId.toUniqueID());

        verifyAccount();
        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetDocuments2() throws OXException {
        fileAccess.expectCall("getDocuments", folderId.getFolderId(), Arrays.asList(
            File.Field.TITLE,
            File.Field.ID,
            File.Field.FOLDER_ID,
            File.Field.LAST_MODIFIED));

        getDocuments(folderId.toUniqueID(), Arrays.asList(File.Field.TITLE));

        verifyAccount();
        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetDocuments3() throws OXException {
        fileAccess.expectCall("getDocuments", folderId.getFolderId(), Arrays.asList(
            File.Field.TITLE,
            File.Field.ID,
            File.Field.FOLDER_ID,
            File.Field.LAST_MODIFIED), File.Field.TITLE, SortDirection.DESC);

        getDocuments(folderId.toUniqueID(), Arrays.asList(File.Field.TITLE), File.Field.TITLE, SortDirection.DESC);

        verifyAccount();
        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetFileMetadata() throws OXException {
        final DefaultFile file = new DefaultFile();
        file.setId(fileId.getFileId());
        file.setFolderId(fileId.getFolderId());

        fileAccess.expectCall("getFileMetadata", fileId.getFolderId(), fileId.getFileId(), "12").andReturn(file);

        getFileMetadata(fileId.toUniqueID(), "12");

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetVersions1() throws OXException {
        fileAccess.expectCall("getVersions", fileId.getFolderId(), fileId.getFileId());

        getVersions(fileId.toUniqueID());

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetVersions2() throws OXException {
        fileAccess.expectCall("getVersions", fileId.getFolderId(), fileId.getFileId(), Arrays.asList(
            File.Field.TITLE,
            File.Field.ID,
            File.Field.FOLDER_ID,
            File.Field.LAST_MODIFIED));

        getVersions(fileId.toUniqueID(), Arrays.asList(File.Field.TITLE));

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testGetVersions3() throws OXException {
        fileAccess.expectCall("getVersions", fileId.getFolderId(), fileId.getFileId(), Arrays.asList(
            File.Field.TITLE,
            File.Field.ID,
            File.Field.FOLDER_ID,
            File.Field.LAST_MODIFIED), File.Field.TITLE, SortDirection.DESC);

        getVersions(fileId.toUniqueID(), Arrays.asList(File.Field.TITLE), File.Field.TITLE, SortDirection.DESC);

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testLock() throws OXException {
        fileAccess.expectCall("startTransaction");
        fileAccess.expectCall("lock", fileId.getFolderId(), fileId.getFileId(), L(1337L));
        fileAccess.expectCall("commit");
        fileAccess.expectCall("finish");

        lock(fileId.toUniqueID(), 1337);

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testUnlock() throws OXException {
        fileAccess.expectCall("startTransaction");
        fileAccess.expectCall("unlock", fileId.getFolderId(), fileId.getFileId());
        fileAccess.expectCall("commit");
        fileAccess.expectCall("finish");

        unlock(fileId.toUniqueID());

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testTouch() throws OXException {
        fileAccess.expectCall("startTransaction");
        fileAccess.expectCall("touch", fileId.getFolderId(), fileId.getFileId());
        fileAccess.expectCall("commit");
        fileAccess.expectCall("finish");

        touch(fileId.toUniqueID());

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testRemoveDocument() throws OXException {
        fileAccess.expectCall("startTransaction");
        fileAccess.expectCall("removeDocument", folderId.getFolderId(), L(12L));
        fileAccess.expectCall("commit");
        fileAccess.expectCall("finish");

        removeDocument(folderId.toUniqueID(), 12);

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testRemoveVersions() throws OXException {
        final String[] versions = new String[] { "1", "2", "3" };

        fileAccess.expectCall("startTransaction");
        fileAccess.expectCall("removeVersion", fileId.getFolderId(), fileId.getFileId(), versions).andReturn(new String[0]);
        fileAccess.expectCall("commit");
        fileAccess.expectCall("finish");
        removeVersion(fileId.toUniqueID(), versions);

        verifyAccount();
        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testSearch() throws OXException {
        fileAccess.expectCall("search", "query", Arrays.asList(
            File.Field.TITLE,
            File.Field.ID,
            File.Field.FOLDER_ID,
            File.Field.LAST_MODIFIED), folderId.getFolderId(), Boolean.FALSE, File.Field.TITLE, SortDirection.DESC, I(10), I(20));

        search("query", Arrays.asList(File.Field.TITLE), folderId.toUniqueID(), false, File.Field.TITLE, SortDirection.DESC, 10, 20);

        verifyAccount();

        fileAccess.assertAllWereCalled();
    }

     @Test
     public void testSearchInAllFolders() throws OXException {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(ThreadPools.class);

        SimThreadPoolService testThreadPool = new SimThreadPoolService();
        PowerMockito.when(ThreadPools.getThreadPool()).thenReturn(testThreadPool);

        fileAccess.expectCall("getAccountAccess").andReturn(this);
        fileAccess.expectCall("getAccountAccess").andReturn(this);

        search("query", Arrays.asList(File.Field.TITLE), FileStorageFileAccess.ALL_FOLDERS, File.Field.TITLE, SortDirection.DESC, 10, 20);

        fileAccess.assertAllWereCalled();
    }

    private void verifyAccount() {
        assertEquals(fileId.getAccountId(), accountId);
        assertEquals(fileId.getService(), serviceId);
    }

    @Override
    public FileStorageAccountAccess getAccountAccess(final String accountId, final Session session) throws OXException {
        if (this.accountId == null) {
            this.accountId = accountId;
        } else {
            this.accountId2 = accountId;
        }
        assertSame(this.session, session);
        return this;
    }

    @Override
    public FileStorageAccountManager getAccountManager() {
        return this;
    }

    @Override
    public String getDisplayName() {
        // Nothing to do
        return null;
    }

    @Override
    public DynamicFormDescription getFormDescription() {
        // Nothing to do
        return null;
    }

    @Override
    public String getId() {
        return "someId";
    }

    @Override
    public Set<String> getSecretProperties() {
        // Nothing to do
        return null;
    }

    @Override
    public String getAccountId() {
        return "someAccount";
    }

    @Override
    public FileStorageFileAccess getFileAccess() throws OXException {
        if (files != null) {
            return files;
        }
        return files = fileAccess.getSim(FileStorageFileAccess.class, FileStorageVersionedFileAccess.class, FileStorageLockedFileAccess.class);
    }

    @Override
    public FileStorageFolderAccess getFolderAccess() throws OXException {
        // Nothing to do
        return null;
    }

    @Override
    public FileStorageFolder getRootFolder() throws OXException {
        // Nothing to do
        return null;
    }

    @Override
    public boolean cacheable() {
        // Nothing to do
        return false;
    }

    @Override
    public void close() {
        // Nothing to do

    }

    @Override
    public void connect() throws OXException {
        // Nothing to do

    }

    @Override
    public boolean isConnected() {
        // Nothing to do
        return false;
    }

    @Override
    public boolean ping() throws OXException {
        // Nothing to do
        return false;
    }

    @Override
    public FileStorageService getService() {
        // Nothing to do
        return this;
    }

    @Override
    public String addAccount(final FileStorageAccount account, final Session session) throws OXException {
        // Nothing to do
        return null;
    }

    @Override
    public boolean hasEncryptedItems(final Session session) throws OXException {
        // Nothing to do
        return false;
    }

    @Override
    public void deleteAccount(final FileStorageAccount account, final Session session) throws OXException {
        // Nothing to do

    }

    @Override
    public FileStorageAccount getAccount(final String id, final Session session) throws OXException {
        // Nothing to do
        return null;
    }

    @Override
    public List<FileStorageAccount> getAccounts(final Session session) throws OXException {
        final FileStorageAccount account = new FileStorageAccount() {

            private static final long serialVersionUID = 1L;

            @Override
            public Map<String, Object> getConfiguration() {
                // Nothing to do
                return null;
            }

            @Override
            public String getDisplayName() {
                // Nothing to do
                return null;
            }

            @Override
            public FileStorageService getFileStorageService() {
                // Nothing to do
                return null;
            }

            @Override
            public String getId() {
                return "account 23";
            }

            @Override
            public JSONObject getMetadata() {
                return new JSONObject();
            }
        };
        return Arrays.asList(account);
    }

    @Override
    public void migrateToNewSecret(final String oldSecret, final String newSecret, final Session session) throws OXException {
        // Nothing to do

    }

    @Override
    public void cleanUp(String secret, Session session) throws OXException {
        // noop
    }

    @Override
    public void removeUnrecoverableItems(String secret, Session session) throws OXException {
        // noop
    }

    @Override
    public void updateAccount(final FileStorageAccount account, final Session session) throws OXException {
        // Nothing to do

    }

    public FileStorageAccountManagerProvider getProvider() {
        // Nothing to do
        return null;
    }

    @Override
    protected EventAdmin getEventAdmin() {
        return new EventAdmin() {

            @Override
            public void sendEvent(Event arg0) {
                // Nothing to do

            }

            @Override
            public void postEvent(Event arg0) {
                // Nothing to do

            }
        };
    }

    @Override
    protected FileStorageServiceRegistry getFileStorageServiceRegistry() {
        final FileStorageService thisService = this;
        return new FileStorageServiceRegistry() {

            @Override
            public FileStorageService getFileStorageService(String id) throws OXException {
                if (serviceId == null) {
                    serviceId = id;
                } else {
                    serviceId2 = id;
                }
                return thisService;
            }

            @Override
            public List<FileStorageService> getAllServices() throws OXException {
                return Arrays.asList(thisService, thisService);
            }

            @Override
            public boolean containsFileStorageService(String id) {
                return true;
            }
        };
    }

}
