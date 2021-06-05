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

package com.openexchange.folderstorage.internal.performers;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.composition.FilenameValidationUtils;
import com.openexchange.folderstorage.BasicPermission;
import com.openexchange.folderstorage.CalculatePermission;
import com.openexchange.folderstorage.Folder;
import com.openexchange.folderstorage.FolderExceptionErrorMessage;
import com.openexchange.folderstorage.FolderField;
import com.openexchange.folderstorage.FolderMoveWarningCollector;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.folderstorage.FolderProperty;
import com.openexchange.folderstorage.FolderServiceDecorator;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.FolderStorageDiscoverer;
import com.openexchange.folderstorage.LockCleaningFolderStorage;
import com.openexchange.folderstorage.ParameterizedFolder;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.PermissionTypeAwareFolder;
import com.openexchange.folderstorage.SetterAwareFolder;
import com.openexchange.folderstorage.SortableId;
import com.openexchange.folderstorage.StorageParametersUtility;
import com.openexchange.folderstorage.UpdateOperation;
import com.openexchange.folderstorage.database.contentType.InfostoreContentType;
import com.openexchange.folderstorage.filestorage.contentType.FileStorageContentType;
import com.openexchange.folderstorage.mail.contentType.MailContentType;
import com.openexchange.folderstorage.osgi.FolderStorageServices;
import com.openexchange.folderstorage.tx.TransactionManager;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.Strings;
import com.openexchange.objectusecount.IncrementArguments;
import com.openexchange.objectusecount.ObjectUseCountService;
import com.openexchange.principalusecount.PrincipalUseCountService;
import com.openexchange.share.GuestInfo;
import com.openexchange.share.recipient.RecipientType;
import com.openexchange.tools.oxfolder.OXFolderExceptionCode;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;

/**
 * {@link UpdatePerformer} - Serves the <code>UPDATE</code> request.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class UpdatePerformer extends AbstractUserizedFolderPerformer {

    private static final String CONTENT_TYPE_MAIL = MailContentType.getInstance().toString();

    private boolean increaseObjectUseCount;
    private boolean collectFolderMoveWarnings;

    /**
     * Initializes a new {@link UpdatePerformer} from given session.
     *
     * @param session The session
     * @throws OXException If passed session is invalid
     */
    public UpdatePerformer(final ServerSession session, final FolderServiceDecorator decorator) throws OXException {
        super(session, decorator);
        increaseObjectUseCount = true;
        collectFolderMoveWarnings = true;
    }

    /**
     * Initializes a new {@link UpdatePerformer} from given user-context-pair.
     *
     * @param user The user
     * @param context The context
     */
    public UpdatePerformer(final User user, final Context context, final FolderServiceDecorator decorator) {
        super(user, context, decorator);
        increaseObjectUseCount = true;
    }

    /**
     * Initializes a new {@link UpdatePerformer}.
     *
     * @param session The session
     * @param folderStorageDiscoverer The folder storage discoverer
     * @throws OXException If passed session is invalid
     */
    public UpdatePerformer(final ServerSession session, final FolderServiceDecorator decorator, final FolderStorageDiscoverer folderStorageDiscoverer) throws OXException {
        super(session, decorator, folderStorageDiscoverer);
        increaseObjectUseCount = true;
    }

    /**
     * Initializes a new {@link UpdatePerformer}.
     *
     * @param user The user
     * @param context The context
     * @param folderStorageDiscoverer The folder storage discoverer
     */
    public UpdatePerformer(final User user, final Context context, final FolderServiceDecorator decorator, final FolderStorageDiscoverer folderStorageDiscoverer) {
        super(user, context, decorator, folderStorageDiscoverer);
        increaseObjectUseCount = true;
    }

    /**
     * Sets the increaseObjectUseCount flag
     *
     * @param increaseObjectUseCount The increaseObjectUseCount to set
     */
    public void setIncreaseObjectUseCount(boolean increaseObjectUseCount) {
        this.increaseObjectUseCount = increaseObjectUseCount;
    }

    /**
     * Sets the collectFolderMoveWarnings flag
     *
     * @param collectFolderMoveWarnings The collectFolderMoveWarnings to set
     */
    public void setCollectFolderMoveWarnings(boolean collectFolderMoveWarnings) {
        this.collectFolderMoveWarnings = collectFolderMoveWarnings;
    }

    /**
     * Performs the <code>UPDATE</code> request.
     *
     * @param folder The object which denotes the folder to update and provides the changes to perform
     * @param timeStamp The requestor's last-modified time stamp
     * @throws OXException If update fails
     */
    public void doUpdate(final Folder folder, final Date timeStamp) throws OXException {
        final String folderId = folder.getID();
        if (null == folderId) {
            throw FolderExceptionErrorMessage.MISSING_FOLDER_ID.create(new Object[0]);
        }
        final String treeId = folder.getTreeID();
        if (null == treeId) {
            throw FolderExceptionErrorMessage.MISSING_TREE_ID.create(new Object[0]);
        }
        final FolderStorage storage = folderStorageDiscoverer.getFolderStorage(treeId, folderId);
        if (null == storage) {
            throw FolderExceptionErrorMessage.NO_STORAGE_FOR_ID.create(treeId, folderId);
        }
        if (null != timeStamp) {
            storageParameters.setTimeStamp(timeStamp);
        }

        TransactionManager transactionManager = TransactionManager.initTransaction(storageParameters);
        boolean rollbackTransaction = true;
        /*
         * Throws an exception if someone tries to add an element. If this happens, you found a bug.
         * As long as a TransactionManager is present, every storage has to add itself to the
         * TransactionManager in FolderStorage.startTransaction() and must return false.
         */
        final List<FolderStorage> openedStorages = Collections.emptyList();
        checkOpenedStorage(storage, openedStorages);
        try {
            /*
             * Load storage folder
             */
            final Folder storageFolder = storage.getFolder(treeId, folderId, storageParameters);
            final String oldParentId = storageFolder.getParentID();
            final String newParentId = folder.getParentID();
            final boolean move = (null != newParentId && !newParentId.equals(oldParentId));
            final Folder destinationFolder;
            if (move){
                destinationFolder = storage.getFolder(treeId, newParentId, storageParameters);
            } else {
                destinationFolder = storageFolder;
            }
            boolean ignoreCase = supportsCaseInsensitive(destinationFolder);
            boolean supportsAutoRename = supportsAutoRename(destinationFolder);

            {
                if (move) {
                    boolean checkForReservedName = true;
                    if (null == folder.getName()) {
                        folder.setName(storageFolder.getName());
                        checkForReservedName = false;
                    }
                    if (false == supportsAutoRename && null != checkForEqualName(treeId, newParentId, folder, storageFolder.getContentType(), CheckOptions.builder().allowAutorename(true).ignoreCase(ignoreCase).build())) {
                        throw FolderExceptionErrorMessage.EQUAL_NAME.create(folder.getName(), getFolderNameSave(storage, newParentId), treeId);
                    }
                    if (checkForReservedName && !folder.getName().equals(storageFolder.getName()) && null != checkForReservedName(treeId, newParentId, folder, storageFolder.getContentType(), CheckOptions.builder().allowAutorename(true).ignoreCase(ignoreCase).build())) {
                        throw FolderExceptionErrorMessage.RESERVED_NAME.create(folder.getName());
                    }
                    UpdateOperation.markAsMove(storageParameters);
                }
            }

            final boolean rename;
            {
                final String newName = folder.getName();
                rename = (null != newName && !newName.equals(storageFolder.getName()));
                if (rename && false == move) {
                    if (null != checkForReservedName(treeId, storageFolder.getParentID(), folder, storageFolder.getContentType(), CheckOptions.builder().allowAutorename(false).ignoreCase(ignoreCase).build())) {
                        throw FolderExceptionErrorMessage.RESERVED_NAME.create(folder.getName());
                    }
                    if (InfostoreContentType.getInstance().equals(storageFolder.getContentType()) && Strings.isNotEmpty(newName)) {
                        FilenameValidationUtils.checkCharacters(newName);
                        FilenameValidationUtils.checkName(newName);
                    }
                    UpdateOperation.markAsRename(storageParameters);
                }
            }
            boolean changeSubscription = false;
            {
                if (folder instanceof SetterAwareFolder) {
                    if (((SetterAwareFolder) folder).containsSubscribed()) {
                        changeSubscription = (storageFolder.isSubscribed() != folder.isSubscribed());
                    }
                } else {
                    changeSubscription = (storageFolder.isSubscribed() != folder.isSubscribed());
                }
            }
            boolean changeUsedForSync = false;
            {
                if (folder.getUsedForSync() != null) {
                    if (folder instanceof SetterAwareFolder) {
                        if (((SetterAwareFolder) folder).containsUsedForSync() && folder.getUsedForSync().isUsedForSync() != storageFolder.getUsedForSync().isUsedForSync()) {
                            changeUsedForSync = true;
                        }
                    } else {
                        if (folder.getUsedForSync().isUsedForSync() != storageFolder.getUsedForSync().isUsedForSync()) {
                            changeUsedForSync = true;
                        }
                    }
                }
            }
            final boolean changedMetaInfo;
            {
                Map<String, Object> meta = folder.getMeta();
                if (null == meta) {
                    changedMetaInfo = false;
                } else {
                    Map<String, Object> storageMeta = storageFolder.getMeta();
                    if (null == storageMeta) {
                        changedMetaInfo = true;
                    } else {
                        changedMetaInfo = false == meta.equals(storageMeta);
                    }
                }
            }
            final boolean changedProperties;
            {
                if (ParameterizedFolder.class.isInstance(folder)) {
                    Map<FolderField, FolderProperty> properties = ((ParameterizedFolder) folder).getProperties();
                    if (null == properties) {
                        changedProperties = false;
                    } else {
                        Map<FolderField, FolderProperty> storageProperties = ParameterizedFolder.class.isInstance(storageFolder) ?
                            ((ParameterizedFolder) storageFolder).getProperties() : null;
                        if (properties.isEmpty() && (null == storageProperties || storageProperties.isEmpty())) {
                            changedProperties = false;
                        } else {
                            changedProperties = false == properties.equals(storageProperties);
                        }
                    }
                } else {
                    changedProperties = false;
                }
            }

            /*
             * restore inherited/legator permission type from original folder's permissions as needed
             */
            if ((false == folder instanceof PermissionTypeAwareFolder) &&
                (storageFolder.getContentType().getModule() == FolderObject.INFOSTORE && folder.getContentType() == null) ||
                (folder.getContentType() != null && folder.getContentType().getModule() == FolderObject.INFOSTORE)) {
                restorePermissionType(folder, storageFolder);
            }

            ComparedFolderPermissions comparedPermissions = new ComparedFolderPermissions(session, folder, storageFolder);
            boolean addedDecorator = false;
            FolderServiceDecorator decorator = storageParameters.getDecorator();
            if (decorator == null) {
                decorator = new FolderServiceDecorator();
                storageParameters.setDecorator(decorator);
                addedDecorator = true;
            }
            final boolean cascadePermissions = decorator.getBoolProperty("cascadePermissions");

            boolean isRecursion = decorator.containsProperty(RECURSION_MARKER);
            if (!isRecursion) {
                decorator.put(RECURSION_MARKER, Boolean.TRUE);
            }
            try {
                /*
                 * Do move?
                 */
                if (move) {
                    doMove(folder, oldParentId, storageFolder, storage, openedStorages);
                } else if (rename) {
                    folder.setParentID(oldParentId);
                    /*
                     * Perform rename either in real or in virtual storage
                     */
                    if (FolderStorage.REAL_TREE_ID.equals(folder.getTreeID())) {
                        doRenameReal(folder, storage);
                    } else {
                        doRenameVirtual(folder, storage, openedStorages);
                    }
                } else if (comparedPermissions.hasChanges() || cascadePermissions) {
                    try {
                        doPermissionChange(treeId, folderId, folder, comparedPermissions, storageFolder, storage, isRecursion ? Boolean.TRUE : Boolean.FALSE, cascadePermissions, decorator, transactionManager, openedStorages);
                    } catch (OXException e) {
                        if (OXFolderExceptionCode.NO_ADMIN_ACCESS.equals(e)) {
                            addWarning(e);
                            return;
                        }
                        throw e;
                    }
                } else if (changeSubscription || changeUsedForSync || changedMetaInfo || changedProperties) {
                    /*
                     * Change subscription, meta, properties either in real or in virtual storage
                     */
                    if (FolderStorage.REAL_TREE_ID.equals(folder.getTreeID())) {
                        storage.updateFolder(folder, storageParameters);
                    } else {
                        final FolderStorage realStorage = folderStorageDiscoverer.getFolderStorage(FolderStorage.REAL_TREE_ID, folder.getID());
                        if (null == realStorage) {
                            throw FolderExceptionErrorMessage.NO_STORAGE_FOR_ID.create(FolderStorage.REAL_TREE_ID, folder.getID());
                        }
                        if (storage.equals(realStorage)) {
                            storage.updateFolder(folder, storageParameters);
                        } else {
                            checkOpenedStorage(realStorage, openedStorages);
                            realStorage.updateFolder(folder, storageParameters);
                            storage.updateFolder(folder, storageParameters);
                        }
                    }
                }
            } finally {
                if (!isRecursion) {
                    decorator.remove(RECURSION_MARKER);
                }

                if (addedDecorator) {
                    storageParameters.setDecorator(null);
                }
            }
            /*
             * Commit
             */
            transactionManager.commit();
            rollbackTransaction = false;

            final Set<OXException> warnings = storageParameters.getWarnings();
            if (null != warnings) {
                for (final OXException warning : warnings) {
                    addWarning(warning);
                }
            }

        } catch (OXException e) {
            throw e;
        } catch (Exception e) {
            throw FolderExceptionErrorMessage.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            if (rollbackTransaction) {
                transactionManager.rollback();
            }
        }
    } // End of doUpdate()

    private void doMove(Folder folder, String oldParentId, Folder storageFolder, FolderStorage storage, Collection<FolderStorage> openedStorages) throws OXException {
        /*
         * Move folder dependent on folder is virtual or not
         */
        final String newParentId = folder.getParentID();
        FolderStorage newRealParentStorage = folderStorageDiscoverer.getFolderStorage(FolderStorage.REAL_TREE_ID, newParentId);
        if (null == newRealParentStorage) {
            throw FolderExceptionErrorMessage.NO_STORAGE_FOR_ID.create(FolderStorage.REAL_TREE_ID, newParentId);
        }
        FolderStorage realParentStorage = folderStorageDiscoverer.getFolderStorage(FolderStorage.REAL_TREE_ID, oldParentId);
        if (null == realParentStorage) {
            throw FolderExceptionErrorMessage.NO_STORAGE_FOR_ID.create(FolderStorage.REAL_TREE_ID, oldParentId);
        }
        FolderStorage realStorage = folderStorageDiscoverer.getFolderStorage(FolderStorage.REAL_TREE_ID, folder.getID());
        if (null == realStorage) {
            throw FolderExceptionErrorMessage.NO_STORAGE_FOR_ID.create(FolderStorage.REAL_TREE_ID, folder.getID());
        }
        /*
         * ensure FileStorageFolderStorage is used for move operations to/from a file storage
         */
        if (FileStorageContentType.getInstance().equals(realParentStorage.getDefaultContentType())) {
            newRealParentStorage = realParentStorage;
            realStorage = realParentStorage;
        } else if (FileStorageContentType.getInstance().equals(newRealParentStorage.getDefaultContentType())) {
            realParentStorage = newRealParentStorage;
            realStorage = newRealParentStorage;
        }
        /*
         * Check for forbidden public mail folder
         */
        if (CONTENT_TYPE_MAIL.equals(storageFolder.getContentType().toString())) {
            boolean started = newRealParentStorage.startTransaction(storageParameters, true);
            boolean rollback = true;
            try {
                Folder newParent = newRealParentStorage.getFolder(FolderStorage.REAL_TREE_ID, newParentId, storageParameters);
                if (isPublicPimFolder(newParent)) {
                    throw FolderExceptionErrorMessage.NO_PUBLIC_MAIL_FOLDER.create();
                }
                if (started) {
                    newRealParentStorage.commitTransaction(storageParameters);
                    rollback = false;
                }
            } catch (RuntimeException e) {
                throw FolderExceptionErrorMessage.UNEXPECTED_ERROR.create(e, e.getMessage());
            } finally {
                if (started && rollback) {
                    newRealParentStorage.rollback(storageParameters);
                }
            }
        }
        /*
         * Checks if a warning is needed for the folder move
         */
        Optional<OXException> warnings = Optional.empty();
        if (collectFolderMoveWarnings) {
            warnings = new FolderMoveWarningCollector(session, storageParameters, storageFolder, realStorage, newParentId, newRealParentStorage).collectWarnings();
            warnings.ifPresent(warning -> addWarning(warning));
        }
        boolean ignoreWarnings = StorageParametersUtility.getBoolParameter("ignoreWarnings", storageParameters);
        if (collectFolderMoveWarnings && warnings.isPresent() && ignoreWarnings == false) {
            folder.setID(null);
        } else {
            /*
             * Perform move either in real or in virtual storage
             */
            MovePerformer movePerformer = newMovePerformer();
            movePerformer.setStorageParameters(storageParameters);
            if (FolderStorage.REAL_TREE_ID.equals(folder.getTreeID())) {
                movePerformer.doMoveReal(folder, storage);
            } else {
                movePerformer.doMoveVirtual(folder, storage, realStorage, realParentStorage, newRealParentStorage, storageFolder, openedStorages);
            }
        }
    }

    private void doPermissionChange(String treeId, String folderId, Folder folder, ComparedFolderPermissions comparedPermissions, Folder storageFolder, FolderStorage storage, Boolean isRecursion, boolean cascadePermissions, FolderServiceDecorator decorator,TransactionManager transactionManager, Collection<FolderStorage> openedStorages) throws OXException {
        if (this.increaseObjectUseCount) {
            ObjectUseCountService useCountService = FolderStorageServices.getService(ObjectUseCountService.class);
            List<Integer> addedUsers = comparedPermissions.getAddedUsers();
            if (null != useCountService && null != addedUsers && !addedUsers.isEmpty()) {
                for (Integer i : addedUsers) {
                    IncrementArguments arguments = new IncrementArguments.Builder(i.intValue()).build();
                    useCountService.incrementObjectUseCount(session, arguments);
                }
            }

            PrincipalUseCountService principalUseCountService = FolderStorageServices.getService(PrincipalUseCountService.class);
            if (null != principalUseCountService) {
                List<Permission> groupPermissions = comparedPermissions.getAddedGroupPermissions();
                if (groupPermissions != null && !groupPermissions.isEmpty()) {
                    for (Permission perm : groupPermissions) {
                        principalUseCountService.increment(session, perm.getEntity());
                    }
                }
            }
        }

        /*
         * Check permissions of anonymous guest users
         */
        checkGuestPermissions(storageFolder, comparedPermissions, transactionManager);
        /*
         * prepare new shares for added guest permissions
         */
        if (!isRecursion.booleanValue() && comparedPermissions.hasNewGuests()) {
            processAddedGuestPermissions(folderId, storageFolder.getContentType(), comparedPermissions, transactionManager.getConnection());
        }
        if (cascadePermissions) {
            /*
             * Switch back to false before update due to the recursive nature of FolderStorage.updateFolder in some implementations
             */
            decorator.put("cascadePermissions", Boolean.FALSE);
        }

        /*
         * Change permissions either in real or in virtual storage
         */
        if (FolderStorage.REAL_TREE_ID.equals(folder.getTreeID())) {
            storage.updateFolder(folder, storageParameters);
        } else {
            final FolderStorage realStorage = folderStorageDiscoverer.getFolderStorage(FolderStorage.REAL_TREE_ID, folder.getID());
            if (null == realStorage) {
                throw FolderExceptionErrorMessage.NO_STORAGE_FOR_ID.create(FolderStorage.REAL_TREE_ID, folder.getID());
            }

            if (storage.equals(realStorage)) {
                storage.updateFolder(folder, storageParameters);
            } else {
                checkOpenedStorage(realStorage, openedStorages);
                realStorage.updateFolder(folder, storageParameters);
                storage.updateFolder(folder, storageParameters);

                if (comparedPermissions.hasRemovedUsers() || comparedPermissions.hasModifiedUsers()) {
                    if (realStorage instanceof LockCleaningFolderStorage) {
                        List<Permission> removedPermissions = comparedPermissions.getRemovedUserPermissions();
                        int[] userIdRemoved = new int[removedPermissions.size()];
                        int x = 0;
                        for (Permission perm : removedPermissions) {
                            userIdRemoved[x++] = perm.getEntity();
                        }

                        List<Permission> modifiedPermissions = comparedPermissions.getModifiedUserPermissions();
                        int[] userIdModified = new int[modifiedPermissions.size()];
                        x = 0;
                        for (Permission perm : modifiedPermissions) {
                            if (perm.getWritePermission() == Permission.NO_PERMISSIONS || perm.getWritePermission() == Permission.WRITE_OWN_OBJECTS) {
                                userIdModified[x++] = perm.getEntity();
                            }
                        }

                        int[] merged = com.openexchange.tools.arrays.Arrays.concatenate(userIdRemoved, userIdModified);
                        ((LockCleaningFolderStorage) realStorage).cleanLocksFor(folder, merged, storageParameters);
                    }
                }
            }
        }
        /*
         * Cascade folder permissions
         */
        if (cascadePermissions) {
            boolean ignoreWarnings = StorageParametersUtility.getBoolParameter("ignoreWarnings", storageParameters);
            checkOpenedStorage(storage, openedStorages);
            List<String> subfolderIDs = new ArrayList<String>();
                gatherSubfolders(folder, storage, treeId, subfolderIDs, ignoreWarnings);
                if (0 < subfolderIDs.size()) {
                    /*
                     * prepare target permissions: remove any anonymous link permission entities
                     */
                    List<Permission> permissions = new ArrayList<Permission>(folder.getPermissions().length);
                    for (Permission permission : folder.getPermissions()) {
                        if (false == permission.isGroup()) {
                            GuestInfo guest = comparedPermissions.getGuestInfo(permission.getEntity());
                            if (null != guest && RecipientType.ANONYMOUS.equals(guest.getRecipientType())) {
                                continue;
                            }
                        }
                        permissions.add(permission);
                    }
                    updatePermissions(storage, treeId, subfolderIDs, permissions.toArray(new Permission[permissions.size()]));
                }
        }
        /*
         * delete existing shares for removed guest permissions
         */
        if (!isRecursion.booleanValue() && comparedPermissions.hasRemovedGuests()) {
            processRemovedGuestPermissions(comparedPermissions.getRemovedGuestPermissions());
        }
    }

    /**
     * Makes sure that permissions types didn't get lost.
     *
     * @param updated The updated folder
     * @param original The original folder
     */
    private void restorePermissionType(Folder updated, Folder original) {
        if (null == updated.getPermissions()) {
            return;
        }
        // @formatter:off
        List<Permission> updatedPerms = Arrays.asList(updated.getPermissions());
        // Create a mapping from the old to the new permissions
        Map<Permission, Permission> updated2originalMap = updatedPerms.stream()
                                                .filter((p) -> p != null)
                                                .collect(HashMap::new,
                                                    (m,p)-> m.put(p, Arrays.asList(original.getPermissions())
                                                                    .parallelStream()
                                                                    .filter((ori) -> ori != null && ori.getEntity() == p.getEntity() && ori.isGroup() == p.isGroup() && ori.getSystem() == p.getSystem())
                                                                    .findAny().orElse(null)),
                                                    HashMap::putAll);
        // Replace all new permissions with a permission with the correct type
        updatedPerms.replaceAll((updatedPerm) -> {
            Permission orig = updated2originalMap.get(updatedPerm);
            if(orig != null && (FolderPermissionType.LEGATOR.equals(orig.getType()) || FolderPermissionType.INHERITED.equals(orig.getType()))) {
                BasicPermission result = new BasicPermission(updatedPerm);
                result.setType(orig.getType());
                return result;
            }
            return updatedPerm;
        });
        // @formatter:on

        updated.setPermissions(updatedPerms.toArray(new Permission[updatedPerms.size()]));
    }

    /**
     * Gather all sub-folders that the current user has administrative rights.
     *
     * @param folder The folder
     * @param storage The folder storage
     * @param treeId The tree identifier
     * @param ids The already gathered sub-folders
     * @param ignoreWarnings Whether or not the warnings are going to be ignored
     * @throws OXException if the current user does not have administrative rights.
     */
    private void gatherSubfolders(Folder folder, FolderStorage storage, String treeId, List<String> ids, final boolean ignoreWarnings) throws OXException {
        SortableId[] sortableIds = storage.getSubfolders(treeId, folder.getID(), storageParameters);
        for (SortableId id : sortableIds) {
            Folder f = storage.getFolder(treeId, id.getId(), storageParameters);
            Permission permission = CalculatePermission.calculate(f, this, ALL_ALLOWED);
            if (!permission.isAdmin()) {
                if (!ignoreWarnings) {
                    int contextId;
                    int userId;
                    if (session == null) {
                        contextId = context.getContextId();
                        userId = user.getId();
                    } else {
                        contextId = session.getContextId();
                        userId = session.getUserId();
                    }
                    throw OXFolderExceptionCode.NO_ADMIN_ACCESS.create(I(userId), f.getName(), Integer.valueOf(contextId));
                }
            } else {
                ids.add(f.getID());
                gatherSubfolders(f, storage, treeId, ids, ignoreWarnings);
            }
        }
    }

    /**
     * Updates the permissions for multiple folders.
     *
     * @param storage The folder storage
     * @param treeId The tree identifier
     * @param folderIDs The identifiers of the folders to update the permissions
     * @param permissions The target permissions to apply for all folders
     * @throws OXException If applying the permissions fails.
     */
    private void updatePermissions(FolderStorage storage, String treeId, List<String> folderIDs, Permission[] permissions) throws OXException {
        for (String id : folderIDs) {
            UpdateFolder toUpdate = new UpdateFolder();
            toUpdate.setTreeID(treeId);
            toUpdate.setID(id);
            toUpdate.setPermissions(permissions);
            storage.updateFolder(toUpdate, storageParameters);
        }
    }

    private MovePerformer newMovePerformer() throws OXException {
        if (null == session) {
            return new MovePerformer(user, context, folderStorageDiscoverer);
        }
        return new MovePerformer(session, folderStorageDiscoverer);
    }

    private void doRenameReal(final Folder folder, final FolderStorage realStorage) throws OXException {
        realStorage.updateFolder(folder, storageParameters);
    }

    private void doRenameVirtual(final Folder folder, final FolderStorage virtualStorage, final List<FolderStorage> openedStorages) throws OXException {
        // Update name in real tree
        final FolderStorage realStorage = folderStorageDiscoverer.getFolderStorage(FolderStorage.REAL_TREE_ID, folder.getID());
        if (null == realStorage) {
            throw FolderExceptionErrorMessage.NO_STORAGE_FOR_ID.create(FolderStorage.REAL_TREE_ID, folder.getID());
        }
        if (virtualStorage.equals(realStorage)) {
            virtualStorage.updateFolder(folder, storageParameters);
        } else {
            checkOpenedStorage(realStorage, openedStorages);
            Folder clone4Real = (Folder) folder.clone();
            clone4Real.setParentID(null);
            realStorage.updateFolder(clone4Real, storageParameters);
            // Update name in virtual tree
            folder.setNewID(clone4Real.getID());
            virtualStorage.updateFolder(folder, storageParameters);
            folder.setID(clone4Real.getID());
        }
    }

    private void checkOpenedStorage(final FolderStorage storage, final List<FolderStorage> openedStorages) throws OXException {
        for (final FolderStorage openedStorage : openedStorages) {
            if (openedStorage.equals(storage)) {
                return;
            }
        }
        if (storage.startTransaction(storageParameters, true)) {
            openedStorages.add(storage);
        }
    }

    /**
     * Tries to get a folder's name, ignoring any exceptions that might occur. Useful for exception messages.
     *
     * @param storage The storage
     * @param folderId The folder identifier
     * @return The folder name, falling back to the identifier
     */
    private String getFolderNameSave(FolderStorage storage, String folderId) {
        try {
            Folder folder = storage.getFolder(FolderStorage.REAL_TREE_ID, folderId, storageParameters);
            return folder.getName();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(UpdatePerformer.class).debug("Error getting name for folder '{}'", folderId, e);
            return folderId;
        }
    }

}
