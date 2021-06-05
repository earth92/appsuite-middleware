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

package com.openexchange.groupware.infostore.autodelete;

import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import org.slf4j.Logger;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.infostore.database.impl.InfostoreIterator;
import com.openexchange.groupware.infostore.facade.impl.InfostoreFacadeImpl;
import com.openexchange.groupware.infostore.utils.Metadata;
import com.openexchange.groupware.tools.iterator.FolderObjectIterator;
import com.openexchange.groupware.userconfiguration.CapabilityUserConfigurationStorage;
import com.openexchange.groupware.userconfiguration.UserConfigurationCodes;
import com.openexchange.java.util.TimeZones;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.tools.oxfolder.OXFolderIteratorSQL;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntProcedure;

/**
 * {@link InfostoreAutodeletePerformer}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.1
 */
public class InfostoreAutodeletePerformer {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(InfostoreAutodeletePerformer.class);
    }

    private final InfostoreFacadeImpl infostoreFacade;

    /**
     * Initializes a new {@link InfostoreAutodeletePerformer}.
     */
    public InfostoreAutodeletePerformer(InfostoreFacadeImpl infostoreFacade) {
        super();
        this.infostoreFacade = infostoreFacade;
    }

    /**
     * Removes versions of all documents, which are older than specified number of retention days.
     *
     * @param retentionDays The number of retention days
     * @param session The session
     * @throws OXException If remove operation fails
     */
    public void removeVersionsByRetentionDays(int retentionDays, ServerSession session) throws OXException {
        int userId = session.getUserId();
        TIntList deleteAll = null;
        TIntList deleteOwn = null;
        {
            User user = session.getUser();
            int[] accessibleModules;
            try {
                accessibleModules = CapabilityUserConfigurationStorage.loadUserConfiguration(userId, session.getContext()).getAccessibleModules();
            } catch (OXException e) {
                if (!UserConfigurationCodes.NOT_FOUND.equals(e)) {
                    throw e;
                }
                accessibleModules = null;
            }

            Queue<FolderObject> queue = ((FolderObjectIterator) OXFolderIteratorSQL.getAllVisibleFoldersIteratorOfModule(userId, user.getGroups(), accessibleModules, FolderObject.INFOSTORE, session.getContext())).asQueue();
            for (FolderObject fo : queue) {
                if (isNotVirtual(fo) && fo.getCreatedBy() == userId) {
                    // Folder owned by session-associated user
                    EffectivePermission permission = fo.getEffectiveUserPermission(userId, session.getUserPermissionBits());
                    if (permission.canDeleteAllObjects()) {
                        if (null == deleteAll) {
                            deleteAll = new TIntArrayList();
                        }
                        deleteAll.add(fo.getObjectID());
                    } else if (permission.canDeleteOwnObjects()) {
                        if (null == deleteOwn) {
                            deleteOwn = new TIntArrayList();
                        }
                        deleteOwn.add(fo.getObjectID());
                    }
                }
            }
        }

        Date maxLastModified = getMaxLastModified(retentionDays);

        if (null != deleteAll) {
            deleteAll.forEach(new TIntProcedure() {

                @Override
                public boolean execute(int folderId) {
                    try {
                        cleanupVersionsByMaxLastModified(folderId, 0, maxLastModified, session);
                    } catch (OXException e) {
                        if (!InfostoreExceptionCodes.DOCUMENT_NOT_EXIST.equals(e)) {
                            LoggerHolder.LOG.error("Failed to remove elapsed document versions from folder {}", Integer.valueOf(folderId), e);
                        }
                    } catch (Exception e) {
                        LoggerHolder.LOG.error("Failed to remove elapsed document versions from folder {}", Integer.valueOf(folderId), e);
                    }
                    return true;
                }
            });
        }

        if (null != deleteOwn) {
            deleteOwn.forEach(new TIntProcedure() {

                @Override
                public boolean execute(int folderId) {
                    try {
                        cleanupVersionsByMaxLastModified(folderId, userId, maxLastModified, session);
                    } catch (OXException e) {
                        if (!InfostoreExceptionCodes.DOCUMENT_NOT_EXIST.equals(e)) {
                            LoggerHolder.LOG.error("Failed to remove elapsed document versions from folder {}", Integer.valueOf(folderId), e);
                        }
                    } catch (Exception e) {
                        LoggerHolder.LOG.error("Failed to remove elapsed document versions from folder {}", Integer.valueOf(folderId), e);
                    }
                    return true;
                }
            });
        }
    }

    void cleanupVersionsByMaxLastModified(int folderId, int optOwner, Date maxLastModified, ServerSession session) throws OXException {
        /*
         * query elapsed versions in folder
         */
        List<DocumentMetadata> allVersions = SearchIterators.asList(InfostoreIterator.allVersionsWhere("infostore.folder_id = " + folderId + " AND infostore_document.last_modified < " + maxLastModified.getTime() + " AND infostore_document.file_store_location IS NOT NULL AND (infostore.version <> infostore_document.version_number)" + (optOwner > 0 ? " AND infostore.created_by="+optOwner : ""), Metadata.VALUES_ARRAY, infostoreFacade, session.getContext()));
        if (allVersions.isEmpty()) {
            return;
        }
        /*
         * group by document
         */
        TIntObjectMap<TIntList> groupedByDocument = new TIntObjectHashMap<TIntList>(allVersions.size());
        for (DocumentMetadata version : allVersions) {
            TIntList versions = groupedByDocument.get(version.getId());
            if (null == versions) {
                versions = new TIntArrayList();
                groupedByDocument.put(version.getId(), versions);
            }
            versions.add(version.getVersion());
        }
        /*
         * iterate by document & clean elapsed versions
         */
        for (TIntObjectIterator<TIntList> it = groupedByDocument.iterator(); it.hasNext();) {
            it.advance();
            infostoreFacade.removeVersion(it.key(), it.value().toArray(), false, true, session);
        }
    }

    /**
     * Removes all versions of a certain document, which exceed the given max. number of versions.
     *
     * @param id The document identifier
     * @param currentVersion The number of the current version
     * @param maxVersions The max. number of versions
     * @param session The session
     * @throws OXException If remove operation fails
     */
    public void removeVersionsByMaxCount(int id, int currentVersion, int maxVersions, ServerSession session) throws OXException {
        /*
         * query versions
         */
        List<DocumentMetadata> versionsOfDocument = SearchIterators.asList(InfostoreIterator.allVersionsWhere("infostore_document.infostore_id = " + id + " AND infostore_document.file_store_location IS NOT NULL", Metadata.VALUES_ARRAY, infostoreFacade, session.getContext()));
        /*
         * delete oldest version until max. number of versions is satisfied
         */
        int numberOfVersionsToDelete = versionsOfDocument.size() - maxVersions;
        if (numberOfVersionsToDelete > 0) {
            Collections.sort(versionsOfDocument, new VersionComparator(currentVersion));

            TIntList versionsToDelete = new TIntArrayList(numberOfVersionsToDelete);
            Iterator<DocumentMetadata> versionsOfDocumentIter = versionsOfDocument.iterator();
            for (int i = numberOfVersionsToDelete; i-- > 0;) {
                versionsToDelete.add(versionsOfDocumentIter.next().getVersion());
            }
            infostoreFacade.removeVersion(id, versionsToDelete.toArray(), false, false, session, false);
        }
    }

    private static Date getMaxLastModified(int retentionDays) {
        Calendar calendar = Calendar.getInstance(TimeZones.UTC);
        calendar.add(Calendar.DAY_OF_YEAR, -1 * retentionDays);
        return calendar.getTime();
    }

    private static boolean isNotVirtual(FolderObject fo) {
        final int id = fo.getObjectID();
        return id != FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID && id != FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID;
    }
    
    // -------------------------------------------------------------------------------------------------------------------------------------
    
    private static class VersionComparator implements Comparator<DocumentMetadata> {

        private final int currentVersion;

        VersionComparator(int currentVersion) {
            super();
            this.currentVersion = currentVersion;
        }

        @Override
        public int compare(DocumentMetadata d1, DocumentMetadata d2) {
            if (isCurrentVersion(d1)) {
                if (!isCurrentVersion(d2)) {
                    return 1;
                }
            } else if (isCurrentVersion(d2)) {
                if (!isCurrentVersion(d1)) {
                    return -1;
                }
            }

            int x = d1.getVersion();
            int y = d2.getVersion();
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }

        private boolean isCurrentVersion(DocumentMetadata d) {
            return currentVersion == d.getVersion();
        }
    }

}
