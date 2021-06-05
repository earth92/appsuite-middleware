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

import static com.openexchange.file.storage.composition.internal.FileStorageTools.supports;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.DefaultFileStorageObjectPermission;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.File.Field;
import com.openexchange.file.storage.FileStorageAccountAccess;
import com.openexchange.file.storage.FileStorageCapability;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FileStorageFileAccess.IDTuple;
import com.openexchange.file.storage.FileStorageGuestObjectPermission;
import com.openexchange.file.storage.FileStorageObjectPermission;
import com.openexchange.file.storage.UserizedFile;
import com.openexchange.file.storage.composition.FileID;
import com.openexchange.file.storage.composition.FolderID;
import com.openexchange.file.storage.composition.crypto.CryptoAwareSharingService;
import com.openexchange.java.Autoboxing;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.session.Session;
import com.openexchange.share.CreatedShares;
import com.openexchange.share.GuestInfo;
import com.openexchange.share.ShareInfo;
import com.openexchange.share.ShareService;
import com.openexchange.share.ShareTarget;
import com.openexchange.share.recipient.RecipientType;
import com.openexchange.share.recipient.ShareRecipient;
import com.openexchange.tx.ConnectionHolder;

/**
 * {@link ShareHelper}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class ShareHelper {

    /**
     * Pre-processes the supplied document to extract added, modified or removed guest object permissions required for sharing support. Guest object
     * permissions that are considered as "new", i.e. guest object permissions from the document metadata that are not yet resolved to a
     * guest user entity, are removed implicitly from the document in order to re-add them afterwards (usually by calling
     * {@link ShareHelper#applyGuestPermissions}). Additionally some validity checks are performed to fail fast in case of invalid requests.
     *
     * @param session The session
     * @param fileAccess The file access hosting the document
     * @param document The document being saved
     * @param modifiedColumns The modified fields as supplied by the client, or <code>null</code> if not set
     * @return The compared object permissions yielding new and removed guest object permissions
     */
    public static ComparedObjectPermissions processGuestPermissions(Session session, FileStorageFileAccess fileAccess, File document, List<Field> modifiedColumns) throws OXException {
        if ((null == modifiedColumns || modifiedColumns.contains(Field.OBJECT_PERMISSIONS))) {
            ComparedObjectPermissions comparedPermissions;
            if (FileStorageFileAccess.NEW == document.getId()) {
                comparedPermissions = new ComparedObjectPermissions(session, null, document);
            } else {
                File oldDocument = fileAccess.getFileMetadata(document.getFolderId(), document.getId(), FileStorageFileAccess.CURRENT_VERSION);
                comparedPermissions = new ComparedObjectPermissions(session, oldDocument, document);
            }
            /*
             * check for general support if changes should be applied
             */
            if (comparedPermissions.hasChanges() && false == supports(fileAccess, FileStorageCapability.OBJECT_PERMISSIONS)) {
                throw FileStorageExceptionCodes.NO_PERMISSION_SUPPORT.create(
                    fileAccess.getAccountAccess().getService().getDisplayName(), document.getFolderId(), I(session.getContextId()));
            }

            /*
             * Remove new guests from the document and check them in terms of permission bits
             */
            if (comparedPermissions.hasNewGuests()) {
                List<FileStorageGuestObjectPermission> newGuestPermissions = comparedPermissions.getNewGuestPermissions();
                document.getObjectPermissions().removeAll(newGuestPermissions);
                FileStorageGuestObjectPermission newAnonymousPermission = null;
                for (FileStorageGuestObjectPermission p : newGuestPermissions) {
                    if (isInvalidGuestPermission(p)) {
                        throw FileStorageExceptionCodes.INVALID_OBJECT_PERMISSIONS.create(I(p.getPermissions()), I(p.getEntity()), document.getId());
                    }
                    if (RecipientType.ANONYMOUS.equals(p.getRecipient().getType())) {
                        if (null == newAnonymousPermission) {
                            newAnonymousPermission = p;
                        } else {
                            throw FileStorageExceptionCodes.INVALID_OBJECT_PERMISSIONS.create(I(p.getPermissions()), I(p.getEntity()), document.getId());
                        }
                    }
                }
                /*
                 * check for an already existing anonymous permission if a new one should be added
                 */
                if (null != newAnonymousPermission && containsOriginalAnonymousPermission(comparedPermissions)) {
                    throw FileStorageExceptionCodes.INVALID_OBJECT_PERMISSIONS.create(
                        I(newAnonymousPermission.getPermissions()), I(newAnonymousPermission.getEntity()), document.getId());
                }
            }
            /*
             * Check permission bits of added and modified guests that already exist as users.
             * Especially existing anonymous guests must not be added as permission entities.
             */
             if (comparedPermissions.hasAddedGuests()) {
                 FileStorageObjectPermission addedAnonymousPermission = null;
                 for (Integer guest : comparedPermissions.getAddedGuests()) {
                     FileStorageObjectPermission p = comparedPermissions.getAddedGuestPermission(guest);
                     GuestInfo guestInfo = comparedPermissions.getGuestInfo(guest.intValue());
                     if (isInvalidGuestPermission(p, guestInfo) || (isAnonymous(guestInfo) && isNotEqualsTarget(document, fileAccess.getAccountAccess(), guestInfo.getLinkTarget()))) {
                         throw FileStorageExceptionCodes.INVALID_OBJECT_PERMISSIONS.create(I(p.getPermissions()), I(p.getEntity()), document.getId());
                     }
                     if (isAnonymous(guestInfo)) {
                         if (null == addedAnonymousPermission) {
                             addedAnonymousPermission = p;
                         } else {
                             throw FileStorageExceptionCodes.INVALID_OBJECT_PERMISSIONS.create(I(p.getPermissions()), I(p.getEntity()), document.getId());
                         }
                     }
                 }
                 /*
                  * check for an already existing anonymous permission if another one should be added
                  */
                 if (null != addedAnonymousPermission && containsOriginalAnonymousPermission(comparedPermissions)) {
                     throw FileStorageExceptionCodes.INVALID_OBJECT_PERMISSIONS.create(
                         I(addedAnonymousPermission.getPermissions()), I(addedAnonymousPermission.getEntity()), document.getId());
                 }
             }
             if (comparedPermissions.hasModifiedGuests()) {
                 for (Integer guest : comparedPermissions.getModifiedGuests()) {
                     FileStorageObjectPermission p = comparedPermissions.getModifiedGuestPermission(guest);
                     if (isInvalidGuestPermission(p, comparedPermissions.getGuestInfo(guest.intValue()))) {
                         throw FileStorageExceptionCodes.INVALID_OBJECT_PERMISSIONS.create(I(p.getPermissions()), I(p.getEntity()), document.getId());
                     }
                 }
             }

            return comparedPermissions;
        }
        return new ComparedObjectPermissions(session, (File)null, (File)null);
    }

    public static List<FileStorageObjectPermission> collectAddedObjectPermissions(ComparedObjectPermissions comparedPermissions, Session session) {
        Collection<FileStorageObjectPermission> newPermissions = comparedPermissions.getNewPermissions();
        if (newPermissions == null || newPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        List<FileStorageObjectPermission> addedPermissions = new ArrayList<>(newPermissions.size());
        List<Integer> modifiedGuests = comparedPermissions.getModifiedGuests();
        for (FileStorageObjectPermission p : newPermissions) {
            // gather all new user entities except the one executing this operation
            if (!p.isGroup() && p.getEntity() != session.getUserId() && !modifiedGuests.contains(I(p.getEntity()))) {
                addedPermissions.add(p);
            }
        }

        for (FileStorageObjectPermission p : comparedPermissions.getAddedGroupPermissions()) {
            addedPermissions.add(p);
        }

        return addedPermissions;
    }

    /**
     * Applies any added or removed guest object permissions for a document, based on the previously extracted object permission
     * comparison (via {@link ShareHelper#processGuestPermissions}). This includes removing shares for removed object permissions, adding
     * shares for new guest permissions, as well as writing back resolved object permissions containing the guest user entities to the
     * document.
     *
     * @param session The session
     * @param access The file access hosting the document
     * @param document The saved document
     * @param comparedPermissions The previously extracted object permission comparison
     * @return The ID tuple referencing the document
     * @throws OXException
     */
    public static IDTuple applyGuestPermissions(Session session, FileStorageFileAccess fileAccess, File document, ComparedObjectPermissions comparedPermissions) throws OXException {
        List<FileStorageObjectPermission> updatedPermissions = handleGuestPermissions(session, fileAccess, document, comparedPermissions);
        updateEncryptionForGuests(session, fileAccess, document, comparedPermissions, updatedPermissions);
        if (null == updatedPermissions) {
            return new IDTuple(document.getFolderId(), document.getId());
        }
        document.setObjectPermissions(updatedPermissions);
        return fileAccess.saveFileMetadata(document, document.getSequenceNumber(), Collections.singletonList(Field.OBJECT_PERMISSIONS));
    }

    /**
     * Updates any encryption settings for Guests if applicable.  Should be called after Guests are created.
     * @param session
     * @param fileAccess
     * @param document
     * @param comparedPermissions
     * @throws OXException
     */
    private static void updateEncryptionForGuests(Session session, FileStorageFileAccess fileAccess, File document, ComparedObjectPermissions comparedPermissions, List<FileStorageObjectPermission> updatedPermissions) throws OXException {
        // Check if Encrypted/Guard file, and we need to change encryption in the file
        CryptoAwareSharingService cryptoSharingService = Services.getServiceLookup().getOptionalService(CryptoAwareSharingService.class);
        if (cryptoSharingService != null && comparedPermissions.hasChanges()) {
            File oldDocument = fileAccess.getFileMetadata(document.getFolderId(), document.getId(), FileStorageFileAccess.CURRENT_VERSION);
            if (cryptoSharingService.isEncrypted(oldDocument)) {
                cryptoSharingService.updateSharing(session, document, fileAccess, comparedPermissions, updatedPermissions);
            }
        }
    }

    private static List<FileStorageObjectPermission> handleGuestPermissions(Session session, FileStorageFileAccess fileAccess, File document, ComparedObjectPermissions comparedPermissions) throws OXException {
        List<FileStorageObjectPermission> updatedPermissions = null;
        if (null != comparedPermissions) {
            if (comparedPermissions.hasNewGuests()) {
                updatedPermissions = ShareHelper.handleNewGuestPermissions(session, fileAccess, document, comparedPermissions);
            }
            if (comparedPermissions.hasRemovedGuests()) {
                /*
                 * extract affected guest entities & schedule cleanup tasks
                 */
                List<Integer> affectedUserIDs = getAffectedUserIDs(comparedPermissions.getRemovedGuestPermissions());
                if (0 < affectedUserIDs.size()) {
                    Services.getService(ShareService.class).scheduleGuestCleanup(session.getContextId(), Autoboxing.I2i(affectedUserIDs));
                }
            }
        }
        return updatedPermissions;
    }

    private static List<FileStorageObjectPermission> handleNewGuestPermissions(Session session, FileStorageFileAccess access, File document, ComparedObjectPermissions comparedPermissions) throws OXException {
        Connection connection = ConnectionHolder.CONNECTION.get();
        session.setParameter(Connection.class.getName(), connection);
        try {
            if (comparedPermissions.hasNewGuests()) {
                List<FileStorageGuestObjectPermission> newGuestPermissions = comparedPermissions.getNewGuestPermissions();
                List<ShareRecipient> shareRecipients = new ArrayList<>(newGuestPermissions.size());
                for (FileStorageGuestObjectPermission guestPermission : newGuestPermissions) {
                    shareRecipients.add(guestPermission.getRecipient());
                }

                List<FileStorageObjectPermission> allPermissions = new ArrayList<>(shareRecipients.size());
                ShareService shareService = Services.getService(ShareService.class);
                if (null == shareService) {
                    throw ServiceExceptionCode.absentService(ShareService.class);
                }
                String service = access.getAccountAccess().getService().getId();
                String account = access.getAccountAccess().getAccountId();
                String folderID = new FolderID(service, account, document.getFolderId()).toUniqueID();
                String fileID = new FileID(service, account, document.getFolderId(), document.getId()).toUniqueID();
                ShareTarget shareTarget = new ShareTarget(8, folderID, fileID);
                CreatedShares shares = shareService.addTarget(session, shareTarget, shareRecipients);
                for (FileStorageGuestObjectPermission permission : newGuestPermissions) {
                    ShareInfo share = shares.getShare(permission.getRecipient());
                    GuestInfo guestInfo = share.getGuest();
                    allPermissions.add(new DefaultFileStorageObjectPermission(guestInfo.getGuestID(), false, permission.getPermissions()));
                    comparedPermissions.rememberGuestInfo(guestInfo);
                }
                List<FileStorageObjectPermission> objectPermissions = document.getObjectPermissions();
                if (objectPermissions != null) {
                    for (FileStorageObjectPermission objectPermission : objectPermissions) {
                        allPermissions.add(objectPermission);
                    }
                }

                return allPermissions;
            }
        } finally {
            session.setParameter(Connection.class.getName(), null);
        }

        return null;
    }

    private static List<Integer> getAffectedUserIDs(List<FileStorageObjectPermission> permissions) {
        if (null == permissions || 0 == permissions.size()) {
            return Collections.emptyList();
        }
        List<Integer> affectedUserIDs = new ArrayList<>(permissions.size());
        for (FileStorageObjectPermission removedPermission : permissions) {
            if (false == removedPermission.isGroup()) {
                affectedUserIDs.add(Integer.valueOf(removedPermission.getEntity()));
            }
        }
        return affectedUserIDs;
    }

    private static boolean isAnonymous(GuestInfo guestInfo) {
        return guestInfo.getRecipientType() == RecipientType.ANONYMOUS;
    }

    private static boolean isNotEqualsTarget(File document, FileStorageAccountAccess accountAccess, ShareTarget target) {
        FileID fileId;
        FolderID folderId;
        if (document instanceof UserizedFile) {
            UserizedFile uFile = (UserizedFile) document;
            folderId = new FolderID(uFile.getOriginalFolderId());
            fileId = new FileID(uFile.getOriginalId());
        } else {
            folderId = new FolderID(document.getFolderId());
            fileId = new FileID(document.getId());
        }

        String service = accountAccess.getService().getId();
        String account = accountAccess.getAccountId();
        folderId.setService(service);
        folderId.setAccountId(account);
        fileId.setService(service);
        fileId.setAccountId(account);
        fileId.setFolderId(folderId.getFolderId());
        return !(new ShareTarget(8, folderId.toUniqueID(), fileId.toUniqueID()).equals(target));
    }

    private static boolean isInvalidGuestPermission(FileStorageGuestObjectPermission p) {
        return p.getRecipient().getType() == RecipientType.ANONYMOUS && (p.canWrite() || p.canDelete());
    }

    private static boolean isInvalidGuestPermission(FileStorageObjectPermission p, GuestInfo guestInfo) {
        if (guestInfo != null && guestInfo.getRecipientType() == RecipientType.ANONYMOUS) {
            return (p.canWrite() || p.canDelete());
        }

        return false;
    }

    /**
     * Gets a value indicating whether the original permissions in the supplied compared permissions instance already contain an
     * "anonymous" entity one or not.
     *
     * @param comparedPermissions The compared permissions to check
     * @return <code>true</code> if there's an "anonymous" entity in the original permissions, <code>false</code>, otherwise
     */
    private static boolean containsOriginalAnonymousPermission(ComparedObjectPermissions comparedPermissions) throws OXException {
        Collection<FileStorageObjectPermission> originalPermissions = comparedPermissions.getOriginalPermissions();
        if (null != originalPermissions && 0 < originalPermissions.size()) {
            for (FileStorageObjectPermission originalPermission : originalPermissions) {
                if (false == originalPermission.isGroup()) {
                    GuestInfo guestInfo = comparedPermissions.getGuestInfo(originalPermission.getEntity());
                    if (null != guestInfo && isAnonymous(guestInfo)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
