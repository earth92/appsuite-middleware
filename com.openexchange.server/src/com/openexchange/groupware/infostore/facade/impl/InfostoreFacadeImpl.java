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

package com.openexchange.groupware.infostore.facade.impl;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.java.Autoboxing.i;
import static com.openexchange.java.util.Tools.getUnsignedInteger;
import static com.openexchange.java.util.Tools.getUnsignedLong;
import static com.openexchange.tools.arrays.Arrays.contains;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import com.google.common.collect.Lists;
import com.openexchange.ajax.fileholder.IFileHolder.InputStreamClosure;
import com.openexchange.annotation.NonNull;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.database.tx.DBService;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.FileStorageFileAccess.IDTuple;
import com.openexchange.file.storage.FileStorageUtility;
import com.openexchange.file.storage.MediaStatus;
import com.openexchange.file.storage.Quota;
import com.openexchange.file.storage.UserizedIDTuple;
import com.openexchange.file.storage.composition.FileID;
import com.openexchange.filestore.FileStorage;
import com.openexchange.filestore.FileStorageCodes;
import com.openexchange.filestore.Info;
import com.openexchange.filestore.QuotaFileStorage;
import com.openexchange.filestore.QuotaFileStorageService;
import com.openexchange.groupware.EntityInfo;
import com.openexchange.groupware.EnumComponent;
import com.openexchange.groupware.Types;
import com.openexchange.groupware.container.EffectiveObjectPermission;
import com.openexchange.groupware.container.EffectiveObjectPermissions;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.container.ObjectPermission;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.impl.IDGenerator;
import com.openexchange.groupware.infostore.DocumentAndMetadata;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.EffectiveInfostoreFolderPermission;
import com.openexchange.groupware.infostore.EffectiveInfostorePermission;
import com.openexchange.groupware.infostore.EntityInfoLoader;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.infostore.InfostoreFacade;
import com.openexchange.groupware.infostore.InfostoreFolderPath;
import com.openexchange.groupware.infostore.InfostoreSearchEngine;
import com.openexchange.groupware.infostore.InfostoreTimedResult;
import com.openexchange.groupware.infostore.autodelete.InfostoreAutodeletePerformer;
import com.openexchange.groupware.infostore.autodelete.InfostoreAutodeleteSettings;
import com.openexchange.groupware.infostore.database.FilenameReservation;
import com.openexchange.groupware.infostore.database.FilenameReserver;
import com.openexchange.groupware.infostore.database.impl.CheckSizeSwitch;
import com.openexchange.groupware.infostore.database.impl.CreateDocumentAction;
import com.openexchange.groupware.infostore.database.impl.CreateObjectPermissionAction;
import com.openexchange.groupware.infostore.database.impl.CreateVersionAction;
import com.openexchange.groupware.infostore.database.impl.DatabaseImpl;
import com.openexchange.groupware.infostore.database.impl.DeleteDocumentAction;
import com.openexchange.groupware.infostore.database.impl.DeleteObjectPermissionAction;
import com.openexchange.groupware.infostore.database.impl.DeleteVersionAction;
import com.openexchange.groupware.infostore.database.impl.DocumentCustomizer;
import com.openexchange.groupware.infostore.database.impl.DocumentMetadataImpl;
import com.openexchange.groupware.infostore.database.impl.FilenameReserverImpl;
import com.openexchange.groupware.infostore.database.impl.InfostoreIterator;
import com.openexchange.groupware.infostore.database.impl.InfostoreQueryCatalog;
import com.openexchange.groupware.infostore.database.impl.InfostoreSecurity;
import com.openexchange.groupware.infostore.database.impl.InfostoreSecurityImpl;
import com.openexchange.groupware.infostore.database.impl.ReplaceDocumentIntoDelTableAction;
import com.openexchange.groupware.infostore.database.impl.Tools;
import com.openexchange.groupware.infostore.database.impl.UpdateDocumentAction;
import com.openexchange.groupware.infostore.database.impl.UpdateObjectPermissionAction;
import com.openexchange.groupware.infostore.database.impl.UpdateVersionAction;
import com.openexchange.groupware.infostore.database.impl.versioncontrol.VersionControlUtil;
import com.openexchange.groupware.infostore.media.EstimationResult;
import com.openexchange.groupware.infostore.media.ExtractorResult;
import com.openexchange.groupware.infostore.media.FileStorageInputStreamProvider;
import com.openexchange.groupware.infostore.media.MediaMetadataExtractor;
import com.openexchange.groupware.infostore.media.MediaMetadataExtractorService;
import com.openexchange.groupware.infostore.media.MediaMetadataExtractors;
import com.openexchange.groupware.infostore.search.SearchTerm;
import com.openexchange.groupware.infostore.search.impl.SearchEngineImpl;
import com.openexchange.groupware.infostore.utils.FileDelta;
import com.openexchange.groupware.infostore.utils.GetSwitch;
import com.openexchange.groupware.infostore.utils.Metadata;
import com.openexchange.groupware.infostore.utils.SetSwitch;
import com.openexchange.groupware.infostore.validation.FilenamesMayNotContainSlashesValidator;
import com.openexchange.groupware.infostore.validation.InvalidCharactersValidator;
import com.openexchange.groupware.infostore.validation.ObjectPermissionValidator;
import com.openexchange.groupware.infostore.validation.PermissionSizeValidator;
import com.openexchange.groupware.infostore.validation.ValidationChain;
import com.openexchange.groupware.infostore.webdav.EntityLockManager;
import com.openexchange.groupware.infostore.webdav.EntityLockManagerImpl;
import com.openexchange.groupware.infostore.webdav.Lock;
import com.openexchange.groupware.infostore.webdav.LockManager.Scope;
import com.openexchange.groupware.infostore.webdav.LockManager.Type;
import com.openexchange.groupware.infostore.webdav.TouchInfoitemsWithExpiredLocksListener;
import com.openexchange.groupware.results.CustomizableTimedResult;
import com.openexchange.groupware.results.Delta;
import com.openexchange.groupware.results.TimedResult;
import com.openexchange.groupware.userconfiguration.Permission;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.SizeKnowingInputStream;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.quota.QuotaExceptionCodes;
import com.openexchange.quota.groupware.AmountQuotas;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Origin;
import com.openexchange.session.Session;
import com.openexchange.session.SessionHolder;
import com.openexchange.session.Sessions;
import com.openexchange.session.UserAndContext;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.share.ShareService;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.Task;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.tools.file.AppendFileAction;
import com.openexchange.tools.file.SaveFileAction;
import com.openexchange.tools.iterator.Customizer;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIteratorAdapter;
import com.openexchange.tools.iterator.SearchIteratorDelegator;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.tools.oxfolder.OXFolderManager;
import com.openexchange.tools.oxfolder.OXFolderSQL;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.tx.UndoableAction;
import com.openexchange.user.User;
import com.openexchange.user.UserExceptionCode;
import com.openexchange.user.UserService;
import com.openexchange.userconf.UserPermissionService;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.linked.TIntLinkedList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * {@link InfostoreFacadeImpl}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class InfostoreFacadeImpl extends DBService implements InfostoreFacade, InfostoreSearchEngine {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(InfostoreFacadeImpl.class);
    }

    private static final InfostoreQueryCatalog QUERIES = InfostoreQueryCatalog.getInstance();
    private static final AtomicReference<QuotaFileStorageService> QFS_REF = new AtomicReference<>();

    /**
     * Applies the given <code>QuotaFileStorageService</code> instance
     *
     * @param service The instance or <code>null</code>
     */
    public static void setQuotaFileStorageService(QuotaFileStorageService service) {
        QFS_REF.set(service);
    }

    static int getMaxCallerRunsCountForMediaDataExtraction(ServerSession session) {
        int defaultValue = 100;

        ConfigViewFactory viewFactory = ServerServiceRegistry.getInstance().getService(ConfigViewFactory.class);
        if (null != viewFactory) {
            try {
                ConfigView view = viewFactory.getView(session.getUserId(), session.getContextId());
                int maxCallerCount = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.groupware.infostore.media.maxCallerRunsCount", defaultValue, view);
                return maxCallerCount <= 0 ? defaultValue : maxCallerCount;
            } catch (@SuppressWarnings("unused") Exception e) {
                // Ignore
            }
        }
        return defaultValue;
    }

    /** A simple task to schedule a new media metadata extraction */
    private static class ScheduledExtractionTask implements Runnable {

        private final DocumentMetadata document;
        private final QuotaFileStorage fileStorage;
        private final MediaMetadataExtractorService extractorService;
        private final Map<String, Object> optArguments;
        private final ServerSession session;

        ScheduledExtractionTask(DocumentMetadata document, QuotaFileStorage fileStorage, MediaMetadataExtractorService extractorService, Map<String, Object> optArguments, ServerSession session) {
            super();
            this.document = document;
            this.fileStorage = fileStorage;
            this.extractorService = extractorService;
            this.optArguments = optArguments;
            this.session = session;
        }

        @Override
        public void run() {
            try {
                extractorService.scheduleMediaMetadataExtraction(document, fileStorage, optArguments, session);
            } catch (Exception e) {
                LoggerHolder.LOG.warn("Failed scheduling of media metadata extraction for document {} ({}) with version {} in context {}", I(document.getId()), document.getFileName(), I(document.getVersion()), I(session.getContextId()), e);
            }
        }
    }

    /** Abstract class allowing to retrieve the identifier of the owner for the folder, in which a given document resides */
    private static class AbstractFolderOwnerProvider {

        protected final InfostoreFacadeImpl infostore;
        protected final ServerSession session;
        private TIntIntMap folderOwners;

        /**
         * Initializes a new {@link AbstractFolderOwnerProvider}.
         *
         * @param infostore The infostore instance
         * @param session The session
         */
        protected AbstractFolderOwnerProvider(InfostoreFacadeImpl infostore, ServerSession session) {
            super();
            this.infostore = infostore;
            this.session = session;
        }

        /**
         * Gets the real folder owner for given document; provided that document's original folder is set.
         *
         * @param document The document
         * @param dbService The database service
         * @param context The associated context
         * @return The real folder owner
         * @throws OXException If real folder owner cannot be returned
         */
        public static int getFolderOwnerFor(DocumentMetadata document, DBService dbService, Context context) throws OXException {
            Connection readCon = null;
            try {
                readCon = dbService.getReadConnection(context);
                OXFolderAccess folderAccess = new OXFolderAccess(readCon, context);
                FolderObject folder = folderAccess.getFolderObject((int) document.getOriginalFolderId());
                return InfostoreSecurityImpl.getFolderOwner(folder);
            } finally {
                dbService.releaseReadConnection(context, readCon);
            }
        }

        /**
         * Gets the identifier of the owner for the folder, in which given document resides.
         *
         * @param document The document providing the folder identifier
         * @return The folder owner or <code>-1</code>
         * @throws OXException If folder owner cannot be returned
         */
        protected int getFolderOwner(DocumentMetadata document) throws OXException {
            TIntIntMap folderOwners = this.folderOwners;
            if (null == folderOwners) {
                int folderOwner = getFolderOwnerFor(document, infostore, session.getContext());
                folderOwners = new TIntIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, -1);
                this.folderOwners = folderOwners;
                folderOwners.put((int) document.getFolderId(), folderOwner);
                return folderOwner;
            }

            int folderId = (int) document.getFolderId();
            int folderOwner = folderOwners.get(folderId);
            if (folderOwner < 0) {
                folderOwner = getFolderOwnerFor(document, infostore, session.getContext());
                folderOwners.put(folderId, folderOwner);
            }
            return folderOwner;

        }
    }

    /**
     * {@link ObjectPermissionCustomizer} - Loads object permissions
     *
     * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
     * @since v7.10.5
     */
    private static class ObjectPermissionCustomizer implements DocumentCustomizer {

        private final ObjectPermissionLoader objectPermissionLoader;
        private final Context context;
        private final DocumentCustomizer optSuccessor;

        /**
         * Initializes a new {@link InfostoreFacadeImpl.ObjectPermissionCustomizer}.
         * 
         * @param objectPermissionLoader The loader for permissions
         * @param context The context
         * @param optSuccessor The optional {@link DocumentCustomizer} to call afterwards
         */
        public ObjectPermissionCustomizer(ObjectPermissionLoader objectPermissionLoader, Context context, DocumentCustomizer optSuccessor) {
            super();
            this.objectPermissionLoader = objectPermissionLoader;
            this.context = context;
            this.optSuccessor = optSuccessor;
        }

        @Override
        public DocumentMetadata handle(DocumentMetadata document) throws OXException {
            objectPermissionLoader.add(document, context, null);
            return null == optSuccessor ? document : optSuccessor.handle(document);
        }
    }

    private static class LoadEntityInfoCustomizer implements DocumentCustomizer {

        private final ServerSession session;
        private final boolean addCreatedFrom;
        private final boolean addModifiedFrom;
        private final EntityInfoLoader loader;
        private final DocumentCustomizer optSuccessor;

        public LoadEntityInfoCustomizer(EntityInfoLoader loader, boolean addCreatedFrom, boolean addModifiedFrom, ServerSession session, DocumentCustomizer optSuccessor) {
            super();
            this.loader = loader;
            this.addCreatedFrom = addCreatedFrom;
            this.addModifiedFrom = addModifiedFrom;
            this.session = session;
            this.optSuccessor = optSuccessor;
        }

        @Override
        public DocumentMetadata handle(DocumentMetadata document) throws OXException {
            if (null != loader) {
                if (addCreatedFrom) {
                    CreatedFromLoader createdFromLoader = new CreatedFromLoader(Collections.singletonMap(I(document.getId()), document), loader, session);
                    document = createdFromLoader.add(document, session.getContext(), null);
                }
                if (addModifiedFrom) {
                    ModifiedFromLoader modifiedFromLoader = new ModifiedFromLoader(Collections.singletonMap(I(document.getId()), document), loader, session);
                    document = modifiedFromLoader.add(document, session.getContext(), null);
                }
            }
            return null == optSuccessor ? document : optSuccessor.handle(document);
        }

    }

    /** The document customizer caring about triggering media metadata extraction dependent on the media status */
    private static class TriggerMediaMetaDataExtractionDocumentCustomizer extends AbstractFolderOwnerProvider implements DocumentCustomizer {

        private final QuotaFileStorage optFileStorage;
        private final DocumentCustomizer optSuccessor;
        private int maxTriggerCount;

        /**
         * Initializes a new {@link TriggerMediaMetaDataExtractionDocumentCustomizer}.
         */
        TriggerMediaMetaDataExtractionDocumentCustomizer(InfostoreFacadeImpl infostore, QuotaFileStorage optFileStorage, ServerSession session) {
            this(infostore, optFileStorage, session, null);
        }

        /**
         * Initializes a new {@link TriggerMediaMetaDataExtractionDocumentCustomizer}.
         */
        TriggerMediaMetaDataExtractionDocumentCustomizer(InfostoreFacadeImpl infostore, QuotaFileStorage optFileStorage, ServerSession session, DocumentCustomizer optSuccessor) {
            super(infostore, session);
            this.optFileStorage = optFileStorage;
            this.optSuccessor = optSuccessor;
            maxTriggerCount = getMaxCallerRunsCountForMediaDataExtraction(session);
        }

        @Override
        public DocumentMetadata handle(DocumentMetadata document) throws OXException {
            if (infostore.considerMediaDataExtraction(document)) {
                QuotaFileStorage fileStorage = this.optFileStorage;
                if (null == fileStorage) {
                    int folderOwner = getFolderOwner(document);
                    fileStorage = infostore.getFileStorage(folderOwner, session.getContextId());
                }

                boolean forceBackground = maxTriggerCount == 0; // If expired, background extraction is supposed to be performed
                TriggerMediaMetaDataExtractionResult result = infostore.triggerMediaDataExtraction(document, null, true, forceBackground, fileStorage, session);
                if (result.performedByCaller) {
                    maxTriggerCount--;
                }
            }
            return optSuccessor == null ? document : optSuccessor.handle(document);
        }
    }

    /** The <code>SearchIterator</code> caring about triggering media metadata extraction dependent on the media status */
    private static class TriggerMediaMetaDataExtractionSearchIterator extends AbstractFolderOwnerProvider implements SearchIterator<DocumentMetadata> {

        private final SearchIterator<DocumentMetadata> searchIterator;
        private int maxTriggerCount;

        /**
         * Initializes a new {@link SearchIteratorImplementation}.
         */
        TriggerMediaMetaDataExtractionSearchIterator(SearchIterator<DocumentMetadata> searchIterator, InfostoreFacadeImpl infostore, ServerSession session) {
            super(infostore, session);
            this.searchIterator = searchIterator;
            maxTriggerCount = getMaxCallerRunsCountForMediaDataExtraction(session);
        }

        @Override
        public boolean hasNext() throws OXException {
            return searchIterator.hasNext();
        }

        @Override
        public DocumentMetadata next() throws OXException {
            DocumentMetadata document = searchIterator.next();
            if (document != null && infostore.considerMediaDataExtraction(document)) {
                int folderOwner = getFolderOwner(document);
                if (folderOwner > 0) {
                    QuotaFileStorage fileStorage = infostore.getFileStorage(folderOwner, session.getContextId());

                    boolean forceBackground = maxTriggerCount == 0; // If expired, background extraction is supposed to be performed
                    TriggerMediaMetaDataExtractionResult result = infostore.triggerMediaDataExtraction(document, null, true, forceBackground, fileStorage, session);
                    if (result.performedByCaller) {
                        maxTriggerCount--;
                    }
                }
            }
            return document;
        }

        @Override
        public void close() {
            searchIterator.close();
        }

        @Override
        public int size() {
            return searchIterator.size();
        }

        @Override
        public boolean hasWarnings() {
            return searchIterator.hasWarnings();
        }

        @Override
        public void addWarning(OXException warning) {
            searchIterator.addWarning(warning);
        }

        @Override
        public OXException[] getWarnings() {
            return searchIterator.getWarnings();
        }
    }

    private static class TriggerMediaMetaDataExtractionByCollection extends AbstractFolderOwnerProvider {

        private final Collection<DocumentMetadata> documents;
        private int maxTriggerCount;

        /**
         * Initializes a new {@link SearchIteratorImplementation}.
         */
        TriggerMediaMetaDataExtractionByCollection(Collection<DocumentMetadata> documents, InfostoreFacadeImpl infostore, ServerSession session) {
            super(infostore, session);
            this.documents = documents;
            maxTriggerCount = getMaxCallerRunsCountForMediaDataExtraction(session);
        }

        /**
         * Consider this instance's documents for possible triggering of media meta-data extraction.
         *
         * @throws OXException If consideration fails
         */
        void considerDocuments() throws OXException {
            for (DocumentMetadata document : documents) {
                if (infostore.considerMediaDataExtraction(document)) {
                    int folderOwner = getFolderOwner(document);
                    if (folderOwner > 0) {
                        QuotaFileStorage fileStorage = infostore.getFileStorage(folderOwner, session.getContextId());

                        boolean forceBackground = maxTriggerCount == 0; // If expired, background extraction is supposed to be performed
                        TriggerMediaMetaDataExtractionResult result = infostore.triggerMediaDataExtraction(document, null, true, forceBackground, fileStorage, session);
                        if (result.performedByCaller) {
                            maxTriggerCount--;
                        }
                    }
                }
            }
        }
    }

    private static class TriggerMediaMetaDataExtractionResult {

        @NonNull
        static final TriggerMediaMetaDataExtractionResult NEITHER_NOR = new TriggerMediaMetaDataExtractionResult(null, false);

        @NonNull
        static final TriggerMediaMetaDataExtractionResult CALLER_RAN = new TriggerMediaMetaDataExtractionResult(null, true);

        // ---------------------------------------------------------------------------------------------------------------------------------

        /** The job, which is supposed to save the media metadata, or <code>null</code> (if not applicable or performed by caller) */
        final Runnable extraction;

        /** <code>true</code> if extraction has been performed by caller; otherwise <code>false</code> */
        final boolean performedByCaller;

        /**
         * Initializes a new {@link TriggerMediaMetaDataExtractionResult}.
         *
         * @param scheduleExtraction The job, which is supposed to save the media metadata, or <code>null</code> (if not applicable or performed by caller)
         */
        TriggerMediaMetaDataExtractionResult(Runnable scheduleExtraction) {
            this(scheduleExtraction, false);
        }

        /**
         * Initializes a new {@link TriggerMediaMetaDataExtractionResult}.
         *
         * @param extraction The job, which is supposed to save the media metadata, or <code>null</code> (if not applicable or performed by caller)
         * @param performedByCaller <code>true</code> if extraction has been performed by caller; otherwise <code>false</code>
         */
        private TriggerMediaMetaDataExtractionResult(Runnable extraction, boolean performedByCaller) {
            super();
            this.extraction = extraction;
            this.performedByCaller = performedByCaller;
        }
    }

    private static class FileRemoveInfo {

        final String fileId;
        final int folderAdmin;
        final int contextId;

        FileRemoveInfo(String fileId, int folderAdmin, int contextId) {
            super();
            this.fileId = fileId;
            this.folderAdmin = folderAdmin;
            this.contextId = contextId;
        }
    }

    // -------------------------------------------------------------------------------------------------------

    /** The infostore security instance */
    protected final InfostoreSecurity security = new InfostoreSecurityImpl();

    private final DatabaseImpl db = new DatabaseImpl();

    private final EntityLockManager lockManager = new EntityLockManagerImpl("infostore_lock");

    private final ThreadLocal<List<FileRemoveInfo>> fileIdRemoveList = new ThreadLocal<>();
    private final ThreadLocal<TIntObjectMap<TIntSet>> guestCleanupList = new ThreadLocal<>();
    private final ThreadLocal<List<Runnable>> pendingInvocations = new ThreadLocal<>();

    private final TouchInfoitemsWithExpiredLocksListener expiredLocksListener;

    final ObjectPermissionLoader objectPermissionLoader;
    private final NumberOfVersionsLoader numberOfVersionsLoader;
    private final LockedUntilLoader lockedUntilLoader;
    private final EntityInfoLoader entityInfoLoader;
    private final SearchEngineImpl searchEngine;


    /**
     * Initializes a new {@link InfostoreFacadeImpl}.
     */
    public InfostoreFacadeImpl() {
        super();
        expiredLocksListener = new TouchInfoitemsWithExpiredLocksListener(null, this);
        lockManager.addExpiryListener(expiredLocksListener);
        this.searchEngine = new SearchEngineImpl(this);
        this.objectPermissionLoader = new ObjectPermissionLoader(this);
        this.numberOfVersionsLoader = new NumberOfVersionsLoader(this);
        this.lockedUntilLoader = new LockedUntilLoader(lockManager);
        this.entityInfoLoader = new EntityInfoLoader();
    }

    /**
     * Initializes a new {@link InfostoreFacadeImpl}.
     *
     * @param provider The database provider to use
     */
    public InfostoreFacadeImpl(final DBProvider provider) {
        this();
        setProvider(provider);
    }

    @Override
    public boolean exists(int id, int version, ServerSession session) throws OXException {
        try {
            return security.getInfostorePermission(session, id).canReadObject();
        } catch (OXException e) {
            if (InfostoreExceptionCodes.NOT_EXIST.equals(e)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public boolean exists(int id, int version, Context context) throws OXException {
        Metadata[] metadata = new Metadata[] { Metadata.FOLDER_ID_LITERAL, Metadata.ID_LITERAL, Metadata.VERSION_LITERAL };
        InfostoreIterator searchIterator = null;
        try {
            searchIterator = InfostoreIterator.versions(id, metadata, Metadata.VERSION_LITERAL, InfostoreFacade.ASC, this, context);
            if (version == InfostoreFacade.CURRENT_VERSION) {
                return searchIterator.hasNext();
            }
            boolean found = false;
            while (searchIterator.hasNext()) {
                DocumentMetadata document = searchIterator.next();
                if (version == document.getVersion()) {
                    found = true;
                    break;
                }
            }
            return found;
        } finally {
            SearchIterators.close(searchIterator);
        }
    }

    @Override
    public boolean hasDocumentAccess(int id, AccessPermission permission, User user, Context context) throws OXException {
        UserPermissionService userPermissionService = ServerServiceRegistry.getServize(UserPermissionService.class, true);
        UserPermissionBits permissionBits = userPermissionService.getUserPermissionBits(user.getId(), context);
        EffectiveInfostorePermission effectivePermission = security.getInfostorePermission(context, user, permissionBits, id);
        return permission.appliesTo(effectivePermission);
    }

    @Override
    public DocumentMetadata getDocumentMetadata(int id, int version, ServerSession session) throws OXException {
        return getDocumentMetadata(-1, id, version, session);
    }

    @Override
    public DocumentMetadata getDocumentMetadata(long folderId, int id, int version, ServerSession session) throws OXException {
        Context context = session.getContext();
        /*
         * load document metadata (including object permissions)
         */
        DocumentMetadata document = objectPermissionLoader.add(load(id, version, context), context, null);
        /*
         * check permissions
         */
        EffectiveInfostorePermission permission = security.getInfostorePermission(session, document);
        if (false == permission.canReadObject()) {
            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
        }
        /*
         * adjust parent folder if required
         */
        if (getSharedFilesFolderID() == folderId || false == permission.canReadObjectInFolder()) {
            document.setOriginalFolderId(document.getFolderId());
            document.setFolderId(getSharedFilesFolderID());
            /*
             * Re-sharing of files is not allowed.
             */
            document.setShareable(false);
        } else {
            document.setShareable(permission.canShareObject());
        }
        /*
         * trigger media meta-data extraction
         */
        if (considerMediaDataExtraction(document)) {
            int folderOwner = AbstractFolderOwnerProvider.getFolderOwnerFor(document, this, context);
            QuotaFileStorage fileStorage = getFileStorage(folderOwner, context.getContextId());
            triggerMediaDataExtraction(document, null, true, false, fileStorage, session);
        }
        /*
         * Load created/modifiedFrom data
         */

        CreatedFromLoader createdFromLoader = new CreatedFromLoader(Collections.singletonMap(I(document.getId()), document), entityInfoLoader, session);
        ModifiedFromLoader modifiedFromLoader = new ModifiedFromLoader(Collections.singletonMap(I(document.getId()), document), entityInfoLoader, session);
        /*
         * add further metadata and return
         */
        return modifiedFromLoader.add(createdFromLoader.add(numberOfVersionsLoader.add(lockedUntilLoader.add(document, context, null), context, null), context, null), context, null);
    }

    @Override
    public DocumentMetadata getDocumentMetadata(int id, int version, Context context) throws OXException {
        /*
         * load document metadata (including object permissions)
         */
        DocumentMetadata document = objectPermissionLoader.add(load(id, version, context), context, null);

        ServerSession session = getSession(null);
        if (null != session) {
            /*
             * trigger media meta-data extraction
             */
            if (considerMediaDataExtraction(document)) {

                int folderOwner = AbstractFolderOwnerProvider.getFolderOwnerFor(document, this, context);
                if (folderOwner > 0) {
                    QuotaFileStorage fileStorage = getFileStorage(folderOwner, context.getContextId());
                    triggerMediaDataExtraction(document, null, true, false, fileStorage, session);
                }
            }
            /*
             * Load created/modifiedFrom data
             */
            CreatedFromLoader createdFromLoader = new CreatedFromLoader(Collections.singletonMap(I(document.getId()), document), entityInfoLoader, session);
            ModifiedFromLoader modifiedFromLoader = new ModifiedFromLoader(Collections.singletonMap(I(document.getId()), document), entityInfoLoader, session);
            return modifiedFromLoader.add(createdFromLoader.add(numberOfVersionsLoader.add(lockedUntilLoader.add(document, context, null), context, null), context, null), context, null);
        }

        /*
         * add further metadata and return
         */
        return numberOfVersionsLoader.add(lockedUntilLoader.add(document, context, null), context, null);
    }

    @Override
    public IDTuple saveDocumentMetadata(final DocumentMetadata document, final long sequenceNumber, final ServerSession session) throws OXException {
        return saveDocument(document, null, sequenceNumber, session);
    }

    @Override
    public IDTuple saveDocumentMetadata(final DocumentMetadata document, final long sequenceNumber, final Metadata[] modifiedColumns, final ServerSession session) throws OXException {
        return saveDocument(document, null, sequenceNumber, modifiedColumns, session);
    }

    @Override
    public InputStream getDocument(final int id, final int version, final ServerSession session) throws OXException {
        return getDocument(id, version, 0L, -1L, session);
    }

    @Override
    public InputStream getDocument(int id, int version, long offset, long length, final ServerSession session) throws OXException {
        /*
         * get needed metadata & check read permissions
         */
        DocumentMetadata metadata = load(id, version, session.getContext());
        EffectiveInfostorePermission permission = security.getInfostorePermission(session, metadata);
        if (false == permission.canReadObject()) {
            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
        }
        return getDocument(session, permission.getFolderOwner(), metadata, offset, length);
    }

    /**
     * Generates an E-Tag based on the supplied document metadata.
     *
     * @param metadata The metadata
     * @return The E-Tag
     */
    private static String getETag(DocumentMetadata metadata) {
        FileID fileID = new FileID(String.valueOf(metadata.getId()));
        fileID.setFolderId(String.valueOf(metadata.getFolderId()));
        return FileStorageUtility.getETagFor(fileID.toUniqueID(), String.valueOf(metadata.getVersion()), metadata.getLastModified());
    }

    @Override
    public DocumentAndMetadata getDocumentAndMetadata(int id, int version, String clientETag, ServerSession session) throws OXException {
        return getDocumentAndMetadata(-1, id, version, clientETag, session);
    }

    @Override
    public DocumentAndMetadata getDocumentAndMetadata(long folderId, int id, int version, String clientETag, ServerSession session) throws OXException {
        Context context = session.getContext();
        /*
         * get needed metadata (including object permissions) & check read permissions
         */
        DocumentMetadata metadata = objectPermissionLoader.add(load(id, version, context), context, null);
        EffectiveInfostorePermission permission = security.getInfostorePermission(session, metadata);
        if (false == permission.canReadObject()) {
            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
        }
        /*
         * adjust parent folder if required, add further metadata
         */
        if (getSharedFilesFolderID() == folderId || false == permission.canReadObjectInFolder()) {
            metadata.setOriginalFolderId(metadata.getFolderId());
            metadata.setFolderId(getSharedFilesFolderID());
            /*
             * Re-sharing of files is not allowed.
             */
            metadata.setShareable(false);
        } else {
            metadata.setShareable(permission.canShareObject());
        }
        /*
         * trigger media meta-data extraction
         */
        QuotaFileStorage fileStorage = null;
        if (considerMediaDataExtraction(metadata)) {
            int folderOwner = AbstractFolderOwnerProvider.getFolderOwnerFor(metadata, this, context);
            fileStorage = getFileStorage(folderOwner, context.getContextId());
            triggerMediaDataExtraction(metadata, null, true, false, fileStorage, session);
        }
        metadata = numberOfVersionsLoader.add(lockedUntilLoader.add(metadata, context, null), context, null);
        /*
         * Load created/modifiedFrom data
         */
        CreatedFromLoader createdFromLoader = new CreatedFromLoader(Collections.singletonMap(I(metadata.getId()), metadata), entityInfoLoader, session);
        metadata = createdFromLoader.add(metadata, context, null);
        ModifiedFromLoader modifiedFromLoader = new ModifiedFromLoader(Collections.singletonMap(I(metadata.getId()), metadata), entityInfoLoader, session);
        metadata = modifiedFromLoader.add(metadata, context, null);
        /*
         * check client E-Tag if supplied
         */
        String eTag = getETag(metadata);
        if (Strings.isNotEmpty(clientETag) && clientETag.equals(eTag)) {
            return new DocumentAndMetadataImpl(metadata, null, eTag);
        }
        /*
         * add file to result, otherwise
         */
        if (null == fileStorage) {
            int folderOwner = AbstractFolderOwnerProvider.getFolderOwnerFor(metadata, this, context);
            fileStorage = getFileStorage(folderOwner, context.getContextId());
        }
        final FileStorage fs = fileStorage;
        final String filestoreLocation = metadata.getFilestoreLocation();
        InputStreamClosure isClosure = new InputStreamClosure() {

            @Override
            public InputStream newStream() throws OXException, IOException {
                return null == filestoreLocation ? Streams.EMPTY_INPUT_STREAM : fs.getFile(filestoreLocation);
            }
        };
        return new DocumentAndMetadataImpl(metadata, isClosure, eTag);
    }

    @Override
    public void lock(final int id, final long diff, final ServerSession session) throws OXException {
        Context context = session.getContext();
        User user = session.getUser();
        final EffectiveInfostorePermission infoPerm = security.getInfostorePermission(session, id);
        if (!infoPerm.canWriteObject()) {
            throw InfostoreExceptionCodes.WRITE_PERMS_FOR_LOCK_MISSING.create();
        }
        final DocumentMetadata document = checkWriteLock(id, session);
        if (lockManager.isLocked(document.getId(), session.getContext(), user)) {
            // Already locked by this user
            return;
        }

        long timeout = diff;
        lockManager.lock(id, timeout, Scope.EXCLUSIVE, Type.WRITE, session.getUserlogin(), context, user);
        touch(id, session);
    }

    @Override
    public void unlock(int id, ServerSession session) throws OXException {
        EffectiveInfostorePermission infoPerm = security.getInfostorePermission(session, id);
        if (false == infoPerm.canWriteObject()) {
            throw InfostoreExceptionCodes.WRITE_PERMS_FOR_UNLOCK_MISSING.create();
        }
        checkMayUnlock(id, session);
        lockManager.removeAll(id, session);
        touch(id, session);
    }

    @Override
    public void touch(final int id, final ServerSession session) throws OXException {
        final Context context = session.getContext();
        final DocumentMetadata oldDocument = load(id, CURRENT_VERSION, context);
        final DocumentMetadata document = new DocumentMetadataImpl(oldDocument);
        Metadata[] modifiedColums = new Metadata[] { Metadata.MODIFIED_BY_LITERAL };
        long sequenceNumber = oldDocument.getSequenceNumber();

        document.setModifiedBy(session.getUserId());
        perform(new UpdateDocumentAction(this, QUERIES, context, document, oldDocument, modifiedColums, sequenceNumber, session), true);
        perform(new UpdateVersionAction(this, QUERIES, context, document, oldDocument, modifiedColums, sequenceNumber, session), true);
    }

    @Override
    public void touch(int id, Context context) throws OXException {
        DocumentMetadata oldDocument = load(id, CURRENT_VERSION, context);
        DocumentMetadata document = new DocumentMetadataImpl(oldDocument);
        document.setLastModified(new Date());
        document.setModifiedBy(context.getMailadmin());
        HashSet<Metadata> modifiedColums = new HashSet<>(Arrays.asList(Metadata.LAST_MODIFIED_LITERAL, Metadata.MODIFIED_BY_LITERAL));
        long sequenceNumber = oldDocument.getSequenceNumber();
        int folderOwner = security.getFolderOwner(oldDocument, context);
        saveModifiedDocument(new SaveParameters(context, null, document, oldDocument, sequenceNumber, modifiedColums, folderOwner));
    }

    @Override
    public com.openexchange.file.storage.Quota getFileQuota(ServerSession session) throws OXException {
        return getFileQuotaFor(session);
    }

    @Override
    public Quota getFileQuota(long folderId, ServerSession session) throws OXException {
        EffectiveInfostoreFolderPermission folderPermission = security.getFolderPermission(session, folderId);
        if (FolderObject.INFOSTORE != folderPermission.getPermission().getFolderModule()) {
            throw InfostoreExceptionCodes.NOT_INFOSTORE_FOLDER.create(L(folderId));
        }
        if (false == folderPermission.isFolderVisible()) {
            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
        }
        return getFileQuotaFor(session);
    }

    private com.openexchange.file.storage.Quota getFileQuotaFor(ServerSession session) throws OXException {
        long limit = com.openexchange.file.storage.Quota.UNLIMITED;
        long usage = com.openexchange.file.storage.Quota.UNLIMITED;
        limit = AmountQuotas.getLimit(session, "infostore", ServerServiceRegistry.getServize(ConfigViewFactory.class, true), ServerServiceRegistry.getServize(DatabaseService.class, true));
        if (com.openexchange.file.storage.Quota.UNLIMITED != limit) {
            usage = getUsedQuota(session.getContext());
        }
        return new com.openexchange.file.storage.Quota(limit, usage, com.openexchange.file.storage.Quota.Type.FILE);
    }

    @Override
    public com.openexchange.file.storage.Quota getStorageQuota(ServerSession session) throws OXException {
        return getStorageQuotaFor(session.getUserId(), session.getContextId());
    }

    @Override
    public Quota getStorageQuota(long folderId, ServerSession session) throws OXException {
        EffectiveInfostoreFolderPermission folderPermission = security.getFolderPermission(session, folderId);
        if (FolderObject.INFOSTORE != folderPermission.getPermission().getFolderModule()) {
            throw InfostoreExceptionCodes.NOT_INFOSTORE_FOLDER.create(L(folderId));
        }
        if (false == folderPermission.isFolderVisible()) {
            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
        }

        int folderOwner = folderPermission.getFolderOwner();
        return getStorageQuotaFor(folderOwner, session.getContextId());
    }

    private com.openexchange.file.storage.Quota getStorageQuotaFor(int folderOwner, int contextId) {
        long limit = com.openexchange.file.storage.Quota.UNLIMITED;
        long usage = com.openexchange.file.storage.Quota.UNLIMITED;

        try {
            QuotaFileStorage fileStorage = getFileStorage(folderOwner, contextId);
            limit = fileStorage.getQuota();
            if (com.openexchange.file.storage.Quota.UNLIMITED != limit) {
                usage = fileStorage.getUsage();
            }
        } catch (OXException e) {
            LoggerHolder.LOG.warn("Error getting file storage quota for user {} in context {}", I(folderOwner), I(contextId), e);
        }

        return new com.openexchange.file.storage.Quota(limit, usage, com.openexchange.file.storage.Quota.Type.STORAGE);
    }

    /**
     * Loads the current version of a document with all available metadata from the database.
     *
     * @param id The ID of the document to load
     * @param ctx The context
     * @return The loaded document
     * @throws OXException
     */
    protected DocumentMetadata load(int id, Context ctx) throws OXException {
        return load(id, CURRENT_VERSION, ctx);
    }

    /**
     * Loads a document in a specific version with all available metadata from the database.
     *
     * @param id The ID of the document to load
     * @param version The version to load
     * @param ctx The context
     * @return The loaded document
     * @throws OXException
     */
    protected DocumentMetadata load(final int id, final int version, final Context ctx) throws OXException {
        InfostoreIterator iterator = null;
        try {
            iterator = InfostoreIterator.loadDocumentIterator(id, version, this, ctx);
            if (false == iterator.hasNext()) {
                throw InfostoreExceptionCodes.DOCUMENT_NOT_EXIST.create();
            }
            return iterator.next();
        } finally {
            SearchIterators.close(iterator);
        }
    }

    private DocumentMetadata checkWriteLock(final int id, final ServerSession session) throws OXException {
        final DocumentMetadata document = load(id, CURRENT_VERSION, session.getContext());
        checkWriteLock(document, session);
        return document;
    }

    private void checkWriteLock(final DocumentMetadata document, final ServerSession session) throws OXException {
        if (document.getModifiedBy() == session.getUserId()) {
            return;
        }

        if (lockManager.isLocked(document.getId(), session.getContext(), session.getUser())) {
            throw InfostoreExceptionCodes.CURRENTLY_LOCKED.create();
        }
    }

    private void checkMayUnlock(final int id, final ServerSession session) throws OXException {
        final DocumentMetadata document = load(id, CURRENT_VERSION, session.getContext());
        if (document.getCreatedBy() == session.getUserId() || document.getModifiedBy() == session.getUserId()) {
            return;
        }
        final List<Lock> locks = lockManager.findLocks(id, session);
        if (locks.size() > 0) {
            throw InfostoreExceptionCodes.LOCKED_BY_ANOTHER.create();
        }
    }

    @Override
    public IDTuple saveDocumentTryAddVersion(DocumentMetadata document, InputStream data, long sequenceNumber, Metadata[] modifiedColumns, ServerSession session) throws OXException {
        try {
            return saveDocument(document, data, sequenceNumber, true, session);
        } catch (OXException e) {
            if (e.getCode() == InfostoreExceptionCodes.FILENAME_NOT_UNIQUE.getNumber() && e.getPrefix().equals(EnumComponent.INFOSTORE.getAbbreviation())) {
                long folderId = ((Long) e.getDisplayArgs()[1]).longValue();
                int id = ((Integer) e.getDisplayArgs()[2]).intValue();
                if (id == 0) {
                    throw InfostoreExceptionCodes.MODIFIED_CONCURRENTLY.create();
                }
                try {
                    DocumentMetadata existing = load(id, CURRENT_VERSION, session.getContext());
                    DocumentMetadata update = new DocumentMetadataImpl(document);
                    update.setFolderId(folderId);
                    update.setId(id);
                    update.setLastModified(new Date());
                    String existingFilename = existing.getFileName();
                    String updateFilename = update.getFileName();
                    Metadata[] columns = null == modifiedColumns ? new Metadata[] { Metadata.LAST_MODIFIED_LITERAL, Metadata.FILENAME_LITERAL } : addSequenceNumberIfNeeded(modifiedColumns);
                    if (Strings.isNotEmpty(updateFilename) && Strings.isNotEmpty(existingFilename) && existingFilename.equalsIgnoreCase(updateFilename)) {
                        columns = addFilenameIfNeeded(columns);
                    }
                    return saveDocument(update, data, existing.getSequenceNumber(), columns, session);
                } catch (OXException x) {
                    if (x.getCode() == InfostoreExceptionCodes.DOCUMENT_NOT_EXIST.getNumber() && x.getPrefix().equals(EnumComponent.INFOSTORE.getAbbreviation())) {
                        return saveDocument(document, data, sequenceNumber, false, session);
                    }
                    throw x;
                }
            }
            throw e;
        }
    }

    @Override
    public IDTuple saveDocument(final DocumentMetadata document, final InputStream data, final long sequenceNumber, final ServerSession session) throws OXException {
        return saveDocument(document, data, sequenceNumber, false, session);
    }

    private IDTuple saveDocument(final DocumentMetadata document, final InputStream data, final long sequenceNumber, final boolean tryAddVersion, final ServerSession session) throws OXException {

        if (document.getId() != InfostoreFacade.NEW) {
            return saveDocument(document, data, sequenceNumber, nonNull(document), session);
        }

        // Insert NEW document
        final Context context = session.getContext();
        EffectiveInfostoreFolderPermission targetFolderPermission = security.getFolderPermission(session, document.getFolderId());
        if (FolderObject.INFOSTORE != targetFolderPermission.getPermission().getFolderModule()) {
            throw InfostoreExceptionCodes.NOT_INFOSTORE_FOLDER.create(L(document.getFolderId()));
        }
        if (false == targetFolderPermission.canCreateObjects()) {
            throw InfostoreExceptionCodes.NO_CREATE_PERMISSION.create();
        }
        if (null != document.getObjectPermissions() && false == targetFolderPermission.canShareOwnObjects()) {
            throw InfostoreExceptionCodes.NO_WRITE_PERMISSION.create();
        }

        {
            com.openexchange.file.storage.Quota storageQuota = getFileQuota(session);
            long limit = storageQuota.getLimit();
            if (limit > 0) {
                long usage = storageQuota.getUsage();
                if (usage >= limit) {
                    throw QuotaExceptionCodes.QUOTA_EXCEEDED_FILES.create(Long.valueOf(usage), Long.valueOf(limit));
                }
            }
        }

        setDefaults(document);
        getValidationChain().validate(session, document, null, null);
        CheckSizeSwitch.checkSizes(document, this, context);

        Runnable pending = null;
        FilenameReserver filenameReserver = null;
        try {
            filenameReserver = new FilenameReserverImpl(context, this);
            FilenameReservation reservation = filenameReserver.reserve(document, !tryAddVersion);
            if (reservation.wasAdjusted()) {
                document.setFileName(reservation.getFilename());
                if (reservation.wasSameTitle()) {
                    document.setTitle(reservation.getFilename());
                }
            }
            Connection writeCon = null;
            try {
                startDBTransaction();
                writeCon = getWriteConnection(context);
                document.setId(getId(context, writeCon));
                commitDBTransaction();
            } catch (SQLException e) {
                throw InfostoreExceptionCodes.NEW_ID_FAILED.create(e);
            } finally {
                releaseWriteConnection(context, writeCon);
                finishDBTransaction();
            }

            Date now = new Date();
            if (null == document.getLastModified()) {
                document.setLastModified(now);
            }
            if (null == document.getCreationDate()) {
                document.setCreationDate(now);
            }
            document.setCreatedBy(session.getUserId());
            document.setModifiedBy(session.getUserId());

            if (null != data) {
                document.setVersion(1);
            } else {
                document.setVersion(0);
            }

            perform(new CreateDocumentAction(this, QUERIES, context, Collections.singletonList(document), session), true);
            perform(new CreateObjectPermissionAction(this, context, document), true);

            final DocumentMetadata version0 = new DocumentMetadataImpl(document);
            version0.setFileName(null);
            version0.setFileSize(0);
            version0.setFileMD5Sum(null);
            version0.setFileMIMEType(null);
            version0.setVersion(0);
            version0.setFilestoreLocation(null);

            perform(new CreateVersionAction(this, QUERIES, context, Collections.singletonList(version0), session), true);

            if (data != null) {
                QuotaFileStorage fileStorage = getFileStorage(targetFolderPermission.getFolderOwner(), session.getContextId());
                SaveFileAction saveFile = new SaveFileAction(fileStorage, data, document.getFileSize());
                perform(saveFile, false);
                document.setVersion(1);
                document.setFilestoreLocation(saveFile.getFileStorageID());
                document.setFileMD5Sum(saveFile.getChecksum());
                document.setFileSize(saveFile.getByteCount());

                Runnable extraction = triggerMediaDataExtraction(document, null, false, false, fileStorage, session).extraction;
                if (null != extraction) {
                    List<Runnable> tasks = pendingInvocations.get();
                    if (null == tasks) {
                        pending = extraction;
                    } else {
                        tasks.add(extraction);
                    }
                }

                perform(new CreateVersionAction(this, QUERIES, context, Collections.singletonList(document), session), true);
            }

            return new IDTuple(String.valueOf(document.getFolderId()), String.valueOf(document.getId()));
        } finally {
            if (null != filenameReserver) {
                filenameReserver.cleanUp();
            }
            if (null != pending) {
                pending.run();
            }
        }
    }

    /**
     * Check if client requested to schedule/perform media metadata extraction
     *
     * @return <code>true</code> to schedule/perform media metadata extraction; otherwise <code>false</code>
     */
    private boolean shouldTriggerMediaDataExtraction() {
        // It's true if the log property is not null and is equal, ignoring case, to the string "true"
        return Boolean.parseBoolean(LogProperties.get(LogProperties.Name.FILE_STORAGE_PREGENERATE_PREVIEWS));
    }

    /**
     * Checks whether media metadata extraction should be triggered for given document
     *
     * @param document The document to examine
     * @return <code>true</code> to trigger media metadata extraction; otherwise <code>false</code>
     */
    boolean considerMediaDataExtraction(DocumentMetadata document) {
        // Either not yet handled (no media status available), a severe error occurred in previous attempt or media status was generated with an older version
        MediaStatus mediaStatus = document.getMediaStatus();
        return mediaStatus == null || MediaStatus.Status.ERROR == mediaStatus.getStatus() || mediaStatus.getVersion() < MediaStatus.getApplicationVersionNumber();
    }

    /**
     * Triggers the extraction of possible media information for specified document
     *
     * @param document The document to extract from
     * @param updatedColumns The optional updated columns
     * @param save Whether to immediately save current media metadata
     * @param forceBackground Whether extraction is forced to be performed as background task
     * @param fileStorage The storage holding file content
     * @param session The session
     * @return The result
     */
    @NonNull
    TriggerMediaMetaDataExtractionResult triggerMediaDataExtraction(final DocumentMetadata document, Collection<Metadata> updatedColumns, boolean save, boolean forceBackground, final QuotaFileStorage fileStorage, final ServerSession session) {
        InputStream documentData = null;
        try {
            // Acquire needed service
            final MediaMetadataExtractorService extractorService = ServerServiceRegistry.getInstance().getService(MediaMetadataExtractorService.class);
            if (null == extractorService) {
                // No extractor service available
                return TriggerMediaMetaDataExtractionResult.NEITHER_NOR;
            }

            // Obtain binary data from file storage
            FileStorageInputStreamProvider streamProvider = new FileStorageInputStreamProvider(document.getFilestoreLocation(), fileStorage);

            // Check...
            EstimationResult result = extractorService.estimateEffort(session, streamProvider, document);
            if (result.isNotApplicable()) {
                // No extractors or not applicable
                document.setMediaStatus(MediaStatus.none());
                if (null != updatedColumns) {
                    Metadata.addIfAbsent(updatedColumns, Metadata.MEDIA_STATUS_LITERAL);
                }
                if (save) {
                    MediaMetadataExtractors.saveMediaStatusForDocument(MediaStatus.none(), document, session);
                }
                return TriggerMediaMetaDataExtractionResult.NEITHER_NOR;
            }

            // Check for low effort
            if (result.isLowEffort() && !forceBackground) {
                // Low effort... Perform with current thread
                try {
                    ExtractorResult extractorResult;
                    {
                        MediaMetadataExtractor optExtractor = result.getExtractor();
                        if (null != optExtractor) {
                            documentData = result.getDocumentData();
                            extractorResult = extractorService.extractAndApplyMediaMetadataUsing(optExtractor, documentData, streamProvider, document, result.getOptionalArguments());
                        } else {
                            extractorResult = extractorService.extractAndApplyMediaMetadata(streamProvider, document, result.getOptionalArguments());
                        }
                    }

                    switch (extractorResult) {
                        case INTERRUPTED:
                            Thread.interrupted();
                            //$FALL-THROUGH$
                        case ACCEPTED_BUT_FAILED:
                            document.setMediaStatus(MediaStatus.failure());
                            if (null != updatedColumns) {
                                Metadata.addIfAbsent(updatedColumns, Metadata.MEDIA_STATUS_LITERAL);
                            }
                            if (save) {
                                MediaMetadataExtractors.saveMediaStatusForDocument(MediaStatus.failure(), document, session);
                            }
                            return TriggerMediaMetaDataExtractionResult.CALLER_RAN;
                        case SUCCESSFUL:
                            {
                                document.setMediaStatus(MediaStatus.success());
                                if (null != updatedColumns) {
                                    List<Metadata> modifiedColumns = new ArrayList<>(8);
                                    modifiedColumns.add(Metadata.MEDIA_STATUS_LITERAL);
                                    if (document.getCaptureDate() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.CAPTURE_DATE_LITERAL);
                                    }
                                    if (document.getGeoLocation() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.GEOLOCATION_LITERAL);
                                    }
                                    if (document.getWidth() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.WIDTH_LITERAL);
                                    }
                                    if (document.getHeight() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.HEIGHT_LITERAL);
                                    }
                                    if (document.getCameraIsoSpeed() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.CAMERA_ISO_SPEED_LITERAL);
                                    }
                                    if (document.getCameraAperture() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.CAMERA_APERTURE_LITERAL);
                                    }
                                    if (document.getCameraExposureTime() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.CAMERA_EXPOSURE_TIME_LITERAL);
                                    }
                                    if (document.getCameraFocalLength() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.CAMERA_FOCAL_LENGTH_LITERAL);
                                    }
                                    if (document.getCameraMake() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.CAMERA_MAKE_LITERAL);
                                    }
                                    if (document.getCameraModel() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.CAMERA_MODEL_LITERAL);
                                    }
                                    if (document.getMediaMeta() != null) {
                                        Metadata.addIfAbsent(updatedColumns, Metadata.MEDIA_META_LITERAL);
                                    }
                                }
                                if (save) {
                                    DocumentMetadataImpl documentToPass = new DocumentMetadataImpl(document);
                                    documentToPass.setSequenceNumber(document.getSequenceNumber());
                                MediaMetadataExtractors.saveMediaMetaDataFromDocument(documentToPass, document, session);
                                }
                                return TriggerMediaMetaDataExtractionResult.CALLER_RAN;
                            }
                        case NONE:
                            // fall-through
                        default:
                            document.setMediaStatus(MediaStatus.none());
                            if (null != updatedColumns) {
                                Metadata.addIfAbsent(updatedColumns, Metadata.MEDIA_STATUS_LITERAL);
                            }
                            if (save) {
                                MediaMetadataExtractors.saveMediaStatusForDocument(MediaStatus.none(), document, session);
                            }
                            return TriggerMediaMetaDataExtractionResult.CALLER_RAN;
                    }
                } catch (Exception e) {
                    LoggerHolder.LOG.debug("Failed to extract media metadata from document {} ({}) with version {} in context {}", I(document.getId()), document.getFileName(), I(document.getVersion()), I(session.getContextId()), e);
                    document.setMediaStatus(MediaStatus.error());
                    if (null != updatedColumns) {
                        Metadata.addIfAbsent(updatedColumns, Metadata.MEDIA_STATUS_LITERAL);
                    }
                    if (save) {
                        MediaMetadataExtractors.saveMediaStatusForDocument(MediaStatus.error(), document, session);
                    }
                    return TriggerMediaMetaDataExtractionResult.CALLER_RAN;
                }
            }

            // Schedule...
            if (save) {
                try {
                    extractorService.scheduleMediaMetadataExtraction(document, fileStorage, result.getOptionalArguments(), session);
                } catch (Exception e) {
                    LoggerHolder.LOG.warn("Failed scheduling of media metadata extraction for document {} with version {} in context {}", I(document.getId()), I(document.getVersion()), I(session.getContextId()), e);
                    try {
                        MediaMetadataExtractors.saveMediaStatusForDocument(MediaStatus.none(), document, session);
                    } catch (Exception x) {
                        LoggerHolder.LOG.warn("Failed restoring '{}' media status for document {} with version {} in context {}", MediaStatus.Status.NONE.getIdentifier(), I(document.getId()), I(document.getVersion()), I(session.getContextId()), x);
                    }
                }
                return TriggerMediaMetaDataExtractionResult.NEITHER_NOR;
            }
            Runnable scheduleTask = new ScheduledExtractionTask(document, fileStorage, extractorService, result.getOptionalArguments(), session);
            return new TriggerMediaMetaDataExtractionResult(scheduleTask);
        } catch (OXException e) {
            LoggerHolder.LOG.warn("Failed extraction of media metadata for document {} with version {} in context {}", I(document.getId()), I(document.getVersion()), I(session.getContextId()), e);
        } finally {
            Streams.close(documentData);
        }

        document.setMediaStatus(MediaStatus.none());
        if (null != updatedColumns) {
            updatedColumns.add(Metadata.MEDIA_STATUS_LITERAL);
        }
        if (save) {
            try {
                MediaMetadataExtractors.saveMediaStatusForDocument(MediaStatus.none(), document, session);
            } catch (OXException e) {
                LoggerHolder.LOG.warn("Failed setting '{}' media status for document {} with version {} in context {}", MediaStatus.Status.NONE.getIdentifier(), I(document.getId()), I(document.getVersion()), I(session.getContextId()), e);
            }
        }
        return TriggerMediaMetaDataExtractionResult.NEITHER_NOR;
    }

    private long getUsedQuota(final Context context) throws OXException {
        Connection readCon = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            readCon = getReadConnection(context);
            stmt = readCon.prepareStatement("SELECT COUNT(id) from infostore where cid=?");
            stmt.setLong(1, context.getContextId());
            rs = stmt.executeQuery();
            return rs.next() ? rs.getLong(1) : -1L;
        } catch (SQLException e) {
            throw InfostoreExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
            if (null != readCon) {
                releaseReadConnection(context, readCon);
            }
        }
    }

    private void setDefaults(final DocumentMetadata document) {
        if (document.getTitle() == null || "".equals(document.getTitle())) {
            document.setTitle(document.getFileName());
        }
    }

    protected <T> T performQuery(final Context ctx, final String query, final ResultProcessor<T> rsp, final Object... args) throws SQLException, OXException {
        Connection readCon = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            readCon = getReadConnection(ctx);
            stmt = readCon.prepareStatement(query);
            for (int i = 0; i < args.length; i++) {
                stmt.setObject(i + 1, args[i]);
            }

            rs = stmt.executeQuery();

            return rsp.process(rs);
        } finally {
            close(stmt, rs);
            if (readCon != null) {
                releaseReadConnection(ctx, readCon);
            }
        }
    }

    private int getNextVersionNumberForInfostoreObject(final int cid, final int infostore_id, final Connection con) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            int retval = 0;
            stmt = con.prepareStatement("SELECT MAX(version_number) FROM infostore_document WHERE cid=? AND infostore_id=?");
            stmt.setInt(1, cid);
            stmt.setInt(2, infostore_id);
            result = stmt.executeQuery();
            if (result.next()) {
                retval = result.getInt(1);
            }
            result.close();
            stmt.close();

            stmt = con.prepareStatement("SELECT MAX(version_number) FROM del_infostore_document WHERE cid=? AND infostore_id=?");
            stmt.setInt(1, cid);
            stmt.setInt(2, infostore_id);
            result = stmt.executeQuery();
            if (result.next()) {
                final int delVersion = result.getInt(1);
                if (delVersion > retval) {
                    retval = delVersion;
                }
            }
            return retval + 1;
        } finally {
            Databases.closeSQLStuff(result, stmt);
        }
    }

    protected QuotaFileStorage getFileStorage(int folderOwner, int contextId) throws OXException {
        QuotaFileStorageService storageService = QFS_REF.get();
        if (null == storageService) {
            throw ServiceExceptionCode.absentService(QuotaFileStorageService.class);
        }

        return storageService.getQuotaFileStorage(folderOwner, contextId, Info.drive());
    }

    /**
     * Loads a specific file from the storage.
     *
     * @param session The session
     * @param folderOwner The actual folder owner to choose the target filestore
     * @param filestoreLocation The filestore location of the document to load
     * @param offset The start offset in bytes to read from the document, or <code>0</code> to start from the beginning
     * @param length The number of bytes to read from the document, or <code>-1</code> to read the stream until the end
     * @return An input stream for the content
     */
    protected InputStream getDocument(ServerSession session, int folderOwner, DocumentMetadata metadata, long offset, long length) throws OXException {
        /*
         * get & return file from storage
         */
        if (null == metadata.getFilestoreLocation()) {
            return Streams.EMPTY_INPUT_STREAM;
        }
        FileStorage fileStorage = getFileStorage(folderOwner, session.getContextId());
        if (0 == offset && -1 == length) {
            return new SizeKnowingInputStream(fileStorage.getFile(metadata.getFilestoreLocation()), metadata.getFileSize());
        }
        try {
            return new SizeKnowingInputStream(fileStorage.getFile(metadata.getFilestoreLocation(), offset, length), length);
        } catch (OXException e) {
            if (FileStorageCodes.INVALID_RANGE.equals(e)) {
                Object[] args = e.getLogArgs();
                if (null != args && 3 < args.length && Long.class.isInstance(args[3])) {
                    long actualSize = ((Long) args[3]).longValue();
                    if (actualSize != metadata.getFileSize()) {
                        LoggerHolder.LOG.debug("Detected invalid file size {} in stored metadata for file {}", L(length), I(metadata.getId()), e);
                        try {
                            DocumentMetadataImpl updatedMetadata = new DocumentMetadataImpl(metadata);
                            updatedMetadata.setFileSize(actualSize);
                            perform(new UpdateVersionAction(this, QUERIES, session.getContext(), updatedMetadata, metadata, new Metadata[] { Metadata.FILE_SIZE_LITERAL }, metadata.getSequenceNumber(), session), true);
                            LoggerHolder.LOG.info("Auto-corrected invalid file size in stored metadata for file {}", I(metadata.getId()));
                        } catch (Exception x) {
                            LoggerHolder.LOG.warn("Error auto-correcting invalid file size in stored metadata for file {}", I(metadata.getId()), x);
                        }
                    }
                }
            }
            throw e;
        }
    }

    private Metadata[] nonNull(final DocumentMetadata document) {
        final List<Metadata> nonNull = new ArrayList<>();
        final GetSwitch get = new GetSwitch(document);
        for (final Metadata metadata : Metadata.HTTPAPI_VALUES) {
            if (null != metadata.doSwitch(get)) {
                nonNull.add(metadata);
            }
        }
        return nonNull.toArray(new Metadata[nonNull.size()]);
    }

    @Override
    public IDTuple saveDocument(final DocumentMetadata document, final InputStream data, final long sequenceNumber, final Metadata[] modifiedColumns, final ServerSession session) throws OXException {
        return saveDocument(document, data, sequenceNumber, modifiedColumns, false, session);
    }

    @Override
    public IDTuple saveDocument(final DocumentMetadata document, final InputStream data, final long sequenceNumber, final Metadata[] modifiedColumns, final boolean ignoreVersion, final ServerSession session) throws OXException {
        return saveDocument(document, data, sequenceNumber, modifiedColumns, ignoreVersion, -1L, session);
    }

    @Override
    public IDTuple saveDocument(DocumentMetadata document, InputStream data, long sequenceNumber, Metadata[] modifiedColumns, long offset, final ServerSession session) throws OXException {
        return saveDocument(document, data, sequenceNumber, modifiedColumns, true, offset, session);
    }

    protected IDTuple saveDocument(DocumentMetadata document, InputStream data, long sequenceNumber, Metadata[] modifiedColumns, boolean ignoreVersion, long offset, final ServerSession session) throws OXException {
        if (0 < offset && (NEW == document.getId() || false == ignoreVersion)) {
            throw InfostoreExceptionCodes.NO_OFFSET_FOR_NEW_VERSIONS.create();
        }

        if (document.getId() == NEW) {
            return saveDocument(document, data, sequenceNumber, session);
        }

        //Adding original path information if necessary
        final OXFolderAccess folderAccess = new OXFolderAccess(session.getContext());
        modifiedColumns = addOriginPathIfNecessary(document, modifiedColumns, session, folderAccess);

        // Check permissions
        Context context = session.getContext();
        int sharedFilesFolderID = getSharedFilesFolderID();
        EffectiveInfostorePermission infoPerm = security.getInfostorePermission(session, document.getId());
        if (false == infoPerm.canWriteObject() || contains(modifiedColumns, Metadata.OBJECT_PERMISSIONS_LITERAL) && (document.getFolderId() == sharedFilesFolderID || false == infoPerm.canShareObject())) {
            throw InfostoreExceptionCodes.NO_WRITE_PERMISSION.create();
        }

        // Check and adjust folder id
        Metadata[] modifiedCols = modifiedColumns;
        List<Metadata> sanitizedColumns = new ArrayList<>(modifiedCols.length);
        Collections.addAll(sanitizedColumns, modifiedCols);
        if (sanitizedColumns.contains(Metadata.FOLDER_ID_LITERAL)) {
            long folderId = document.getFolderId();
            if (folderId == sharedFilesFolderID) {
                document.setFolderId(infoPerm.getObject().getFolderId());
                sanitizedColumns.remove(Metadata.FOLDER_ID_LITERAL);
                modifiedCols = sanitizedColumns.toArray(new Metadata[sanitizedColumns.size()]);
            } else if (document.getFolderId() != -1 && infoPerm.getObject().getFolderId() != document.getFolderId()) {
                EffectiveInfostoreFolderPermission targetFolderPermission = security.getFolderPermission(session, document.getFolderId());
                if (FolderObject.INFOSTORE != targetFolderPermission.getPermission().getFolderModule()) {
                    throw InfostoreExceptionCodes.NOT_INFOSTORE_FOLDER.create(L(folderId));
                }
                if (false == targetFolderPermission.canCreateObjects()) {
                    throw InfostoreExceptionCodes.NO_CREATE_PERMISSION.create();
                }
                if (false == infoPerm.canDeleteObject()) {
                    throw InfostoreExceptionCodes.NO_SOURCE_DELETE_PERMISSION.create();
                }
            }
        }

        // Set modified information
        Set<Metadata> updatedCols = new HashSet<>(Arrays.asList(modifiedCols));
        updatedCols.removeAll(Arrays.asList(Metadata.CREATED_BY_LITERAL, Metadata.CREATION_DATE_LITERAL, Metadata.ID_LITERAL));
        boolean checkWriteLock = true;
        if (updatedCols.size() == 1 && updatedCols.contains(Metadata.OBJECT_PERMISSIONS_LITERAL)) {
            // Skip lock check in case only permissions are changed
            checkWriteLock = false;
        }

        document.setModifiedBy(session.getUserId());
        updatedCols.add(Metadata.MODIFIED_BY_LITERAL);

        if (null == document.getLastModified() || false == updatedCols.contains(Metadata.LAST_MODIFIED_LITERAL)) {
            document.setLastModified(new Date());
            updatedCols.add(Metadata.LAST_MODIFIED_LITERAL);
        }

        CheckSizeSwitch.checkSizes(document, this, context);
        DocumentMetadata oldDocument;
        if (checkWriteLock) {
            oldDocument = objectPermissionLoader.add(checkWriteLock(document.getId(), session), session.getContext(), null);
        } else {
            oldDocument = objectPermissionLoader.add(load(document.getId(), CURRENT_VERSION, session.getContext()), session.getContext(), null);
        }
        getValidationChain().validate(session, document, oldDocument, updatedCols);

        SaveParameters saveParameters = new SaveParameters(context, session, document, oldDocument, sequenceNumber, updatedCols, infoPerm.getFolderOwner());
        saveParameters.setData(data, offset, session.getUserId(), ignoreVersion);
        saveModifiedDocument(saveParameters);

        return 10 == document.getFolderId()
            ? new UserizedIDTuple(String.valueOf(document.getFolderId()), String.valueOf(document.getId()), String.valueOf(oldDocument.getOriginalFolderId()))
            : new IDTuple(String.valueOf(document.getFolderId()), String.valueOf(document.getId()));
    }

    @Override
    public IDTuple saveDocumentMetadata(DocumentMetadata document, long sequenceNumber, Metadata[] modifiedColumns, Context context) throws OXException {
        if (document.getId() == NEW) {
            throw InfostoreExceptionCodes.DOCUMENT_NOT_EXIST.create();
        }

        long folderId = document.getFolderId();
        if (folderId < 0) {
            throw InfostoreExceptionCodes.NOT_INFOSTORE_FOLDER.create(Long.valueOf(folderId));
        }
        if (folderId < FolderObject.MIN_FOLDER_ID) {
            throw InfostoreExceptionCodes.NO_DOCUMENTS_IN_VIRTUAL_FOLDER.create();
        }

        Set<Metadata> updatedCols = new HashSet<>();
        Collections.addAll(updatedCols, modifiedColumns == null ? Metadata.VALUES_ARRAY : modifiedColumns);
        if (!updatedCols.contains(Metadata.LAST_MODIFIED_LITERAL)) {
            document.setLastModified(new Date());
            updatedCols.add(Metadata.LAST_MODIFIED_LITERAL);
        }

        if (!updatedCols.contains(Metadata.MODIFIED_BY_LITERAL)) {
            document.setModifiedBy(context.getMailadmin());
            updatedCols.add(Metadata.MODIFIED_BY_LITERAL);
        }

        CheckSizeSwitch.checkSizes(document, this, context);

        DocumentMetadata oldDocument = objectPermissionLoader.add(load(document.getId(), context), context, null);
        return saveModifiedDocument(new SaveParameters(context, null, document, oldDocument, sequenceNumber, updatedCols, security.getFolderOwner(folderId, context)));
    }

    private IDTuple saveModifiedDocument(SaveParameters parameters) throws OXException {
        FilenameReserver filenameReserver = null;
        try {
            Set<Metadata> updatedCols = parameters.getUpdatedCols();
            DocumentMetadata document = parameters.getDocument();
            DocumentMetadata oldDocument = parameters.getOldDocument();
            Context context = parameters.getContext();
            ServerSession session = parameters.getSession();
            int checkedVersion = -1;

            if (updatedCols.contains(Metadata.VERSION_LITERAL)) {
                String fname = load(document.getId(), document.getVersion(), context).getFileName();
                checkedVersion = document.getVersion();
                if (!updatedCols.contains(Metadata.FILENAME_LITERAL)) {
                    updatedCols.add(Metadata.FILENAME_LITERAL);
                    document.setFileName(fname);
                }
            }

            boolean isMove = updatedCols.contains(Metadata.FOLDER_ID_LITERAL) && oldDocument.getFolderId() != document.getFolderId();
            boolean isRename = updatedCols.contains(Metadata.FILENAME_LITERAL) && null != document.getFileName() && false == document.getFileName().equalsIgnoreCase(oldDocument.getFileName());
            if (isMove) {
                // this is a move - reserve in target folder
                String newFileName = null != document.getFileName() ? document.getFileName() : oldDocument.getFileName();
                DocumentMetadata placeHolder = new DocumentMetadataImpl(oldDocument.getId());
                placeHolder.setFolderId(document.getFolderId());
                placeHolder.setFileName(newFileName);
                if (updatedCols.contains(Metadata.ORIGIN_LITERAL)) {
                    placeHolder.setOriginFolderPath(document.getOriginFolderPath());
                }
                filenameReserver = new FilenameReserverImpl(context, db);
                FilenameReservation reservation = filenameReserver.reserve(placeHolder, true);
                document.setFileName(reservation.getFilename());
                updatedCols.add(Metadata.FILENAME_LITERAL);

                // insert tombstone row to del_infostore table in case of move operations to aid folder based synchronizations
                DocumentMetadataImpl tombstoneDocument = new DocumentMetadataImpl(oldDocument);
                tombstoneDocument.setLastModified(document.getLastModified());
                tombstoneDocument.setModifiedBy(document.getModifiedBy());
                if (updatedCols.contains(Metadata.ORIGIN_LITERAL)) {
                    tombstoneDocument.setOriginFolderPath(document.getOriginFolderPath());
                }
                perform(new ReplaceDocumentIntoDelTableAction(this, QUERIES, context, tombstoneDocument, session), true);

                // remove any object permissions upon move
                document.setObjectPermissions(null);
                updatedCols.add(Metadata.OBJECT_PERMISSIONS_LITERAL);
            } else if (isRename) {
                // this is a rename - reserve in current folder
                DocumentMetadata placeHolder = new DocumentMetadataImpl(oldDocument.getId());
                placeHolder.setFolderId(oldDocument.getFolderId());
                placeHolder.setFileName(document.getFileName());
                filenameReserver = new FilenameReserverImpl(context, db);
                FilenameReservation reservation = filenameReserver.reserve(placeHolder, true);
                document.setFileName(reservation.getFilename());
                updatedCols.add(Metadata.FILENAME_LITERAL);
            }

            String oldTitle = oldDocument.getTitle();
            if (!updatedCols.contains(Metadata.TITLE_LITERAL) && oldDocument.getFileName() != null && oldTitle != null && oldDocument.getFileName().equals(oldTitle)) {
                if (null == document.getFileName()) {
                    document.setTitle(oldDocument.getFileName());
                    document.setFileName(oldDocument.getFileName());
                    updatedCols.add(Metadata.FILENAME_LITERAL);
                } else {
                    document.setTitle(document.getFileName());
                }
                updatedCols.add(Metadata.TITLE_LITERAL);
            }

            if (isMove) {
                VersionControlUtil.doVersionControl(this, Collections.singletonList(document), Collections.singletonList(oldDocument), document.getFolderId(), context);
            }

            Metadata[] modifiedCols;
            if (parameters.hasData()) {
                storeNewData(parameters);
                modifiedCols = updatedCols.toArray(new Metadata[updatedCols.size()]);
            } else {
                modifiedCols = updatedCols.toArray(new Metadata[updatedCols.size()]);
                if (QUERIES.updateVersion(modifiedCols)) {
                    if (!updatedCols.contains(Metadata.VERSION_LITERAL)) {
                        document.setVersion(oldDocument.getVersion());
                    }

                    // Ensure version existence
                    if (checkedVersion > 0 && checkedVersion != document.getVersion()) {
                        load(document.getId(), document.getVersion(), context);
                    }

                    // Perform the version-related updates
                    perform(new UpdateVersionAction(this, QUERIES, context, document, oldDocument, modifiedCols, parameters.getSequenceNumber(), session), true);
                }
            }

            if (QUERIES.updateDocument(modifiedCols)) {
                perform(new UpdateDocumentAction(this, QUERIES, context, document, oldDocument, modifiedCols, parameters.getSequenceNumber(), session), true);
            }

            // Update object permissions as needed
            if (updatedCols.contains(Metadata.OBJECT_PERMISSIONS_LITERAL)) {
                rememberForGuestCleanup(context.getContextId(), Collections.singletonList(oldDocument));
                perform(new UpdateObjectPermissionAction(this, context, document, oldDocument), true);
            }
            return new IDTuple(String.valueOf(document.getFolderId()), String.valueOf(document.getId()));
        } finally {
            if (null != filenameReserver) {
                filenameReserver.cleanUp();
            }
        }
    }

    private void storeNewData(SaveParameters parameters) throws OXException {
        Runnable pending = null;
        try {
            ServerSession session = parameters.getSession();
            int folderAdmin = parameters.getOptFolderAdmin();
            QuotaFileStorage qfs = getFileStorage(folderAdmin, parameters.getContext().getContextId());
            if (0 < parameters.getOffset()) {
                AppendFileAction appendFile = new AppendFileAction(qfs, parameters.getData(), parameters.getOldDocument().getFilestoreLocation(), parameters.getDocument().getFileSize(), parameters.getOffset());
                perform(appendFile, false);
                parameters.getDocument().setFilestoreLocation(parameters.getOldDocument().getFilestoreLocation());
                parameters.getDocument().setFileSize(appendFile.getByteCount() + parameters.getOffset());
                // Invalidate following fields due to append-operation
                parameters.getDocument().setFileMD5Sum(null);
                parameters.getDocument().setCaptureDate(null);
                parameters.getDocument().setGeoLocation(null);
                parameters.getDocument().setWidth(-1);
                parameters.getDocument().setHeight(-1);
                parameters.getDocument().setCameraIsoSpeed(-1);
                parameters.getDocument().setCameraAperture(-1);
                parameters.getDocument().setCameraExposureTime(-1);
                parameters.getDocument().setCameraFocalLength(-1);
                parameters.getDocument().setCameraMake(null);
                parameters.getDocument().setCameraModel(null);
                parameters.getDocument().setMediaMeta(null);
                parameters.getDocument().setMediaStatus(null);
                parameters.getUpdatedCols().addAll(Arrays.asList(
                    Metadata.FILE_MD5SUM_LITERAL,
                    Metadata.FILE_SIZE_LITERAL,
                    Metadata.CAPTURE_DATE_LITERAL,
                    Metadata.GEOLOCATION_LITERAL,
                    Metadata.WIDTH_LITERAL,
                    Metadata.HEIGHT_LITERAL,
                    Metadata.CAMERA_MAKE_LITERAL,
                    Metadata.CAMERA_MODEL_LITERAL,
                    Metadata.CAMERA_ISO_SPEED_LITERAL,
                    Metadata.CAMERA_APERTURE_LITERAL,
                    Metadata.CAMERA_EXPOSURE_TIME_LITERAL,
                    Metadata.CAMERA_FOCAL_LENGTH_LITERAL,
                    Metadata.MEDIA_META_LITERAL,
                    Metadata.MEDIA_STATUS_LITERAL));

                Runnable extraction = triggerMediaDataExtraction(parameters.getDocument(), parameters.getUpdatedCols(), false, false, qfs, session).extraction;
                if (null != extraction) {
                    List<Runnable> tasks = pendingInvocations.get();
                    if (null == tasks) {
                        pending = extraction;
                    } else {
                        tasks.add(extraction);
                    }
                }
            } else {
                SaveFileAction saveFile = new SaveFileAction(qfs, parameters.getData(), parameters.getDocument().getFileSize());
                perform(saveFile, false);
                parameters.getDocument().setFilestoreLocation(saveFile.getFileStorageID());
                parameters.getDocument().setFileSize(saveFile.getByteCount());
                parameters.getDocument().setFileMD5Sum(saveFile.getChecksum());
                parameters.getDocument().setCaptureDate(null);
                parameters.getDocument().setGeoLocation(null);
                parameters.getDocument().setWidth(-1);
                parameters.getDocument().setHeight(-1);
                parameters.getDocument().setCameraIsoSpeed(-1);
                parameters.getDocument().setCameraAperture(-1);
                parameters.getDocument().setCameraExposureTime(-1);
                parameters.getDocument().setCameraFocalLength(-1);
                parameters.getDocument().setCameraMake(null);
                parameters.getDocument().setCameraModel(null);
                parameters.getDocument().setMediaMeta(null);
                parameters.getDocument().setMediaStatus(null);
                parameters.getUpdatedCols().addAll(Arrays.asList(
                    Metadata.FILE_MD5SUM_LITERAL,
                    Metadata.FILE_SIZE_LITERAL,
                    Metadata.CAPTURE_DATE_LITERAL,
                    Metadata.GEOLOCATION_LITERAL,
                    Metadata.WIDTH_LITERAL,
                    Metadata.HEIGHT_LITERAL,
                    Metadata.CAMERA_MAKE_LITERAL,
                    Metadata.CAMERA_MODEL_LITERAL,
                    Metadata.CAMERA_ISO_SPEED_LITERAL,
                    Metadata.CAMERA_APERTURE_LITERAL,
                    Metadata.CAMERA_EXPOSURE_TIME_LITERAL,
                    Metadata.CAMERA_FOCAL_LENGTH_LITERAL,
                    Metadata.MEDIA_META_LITERAL,
                    Metadata.MEDIA_STATUS_LITERAL));

                Runnable extraction = triggerMediaDataExtraction(parameters.getDocument(), parameters.getUpdatedCols(), false, false, qfs, session).extraction;
                if (null != extraction) {
                    List<Runnable> tasks = pendingInvocations.get();
                    if (null == tasks) {
                        pending = extraction;
                    } else {
                        tasks.add(extraction);
                    }
                }
            }

            final GetSwitch get = new GetSwitch(parameters.getOldDocument());
            final SetSwitch set = new SetSwitch(parameters.getDocument());
            for (Metadata m : new Metadata[] { Metadata.DESCRIPTION_LITERAL, Metadata.TITLE_LITERAL, Metadata.FILENAME_LITERAL, Metadata.URL_LITERAL }) {
                if (parameters.getUpdatedCols().contains(m)) {
                    continue;
                }
                set.setValue(m.doSwitch(get));
                m.doSwitch(set);
            }

            parameters.getDocument().setCreatedBy(parameters.getFileCreatedBy());
            if (!parameters.getUpdatedCols().contains(Metadata.CREATION_DATE_LITERAL)) {
                parameters.getDocument().setCreationDate(new Date());
            }

            // Set version
            boolean newVersion = false;
            final UndoableAction action;
            if (parameters.isIgnoreVersion()) {
                parameters.getDocument().setVersion(parameters.getOldDocument().getVersion());
                parameters.getUpdatedCols().add(Metadata.VERSION_LITERAL);
                parameters.getUpdatedCols().add(Metadata.FILESTORE_LOCATION_LITERAL);
                action = new UpdateVersionAction(this, QUERIES, parameters.getContext(), parameters.getDocument(), parameters.getOldDocument(), parameters.getUpdatedCols().toArray(new Metadata[parameters.getUpdatedCols().size()]), parameters.getSequenceNumber(), session);

                // Remove old file "version" if not appended
                if (0 >= parameters.getOffset()) {
                    removeFile(parameters.getContext(), parameters.getOldDocument().getFilestoreLocation(), security.getFolderOwner(parameters.getOldDocument(), parameters.getContext()));
                }
            } else {
                Connection con = null;
                try {
                    con = getReadConnection(parameters.getContext());
                    parameters.getDocument().setVersion(getNextVersionNumberForInfostoreObject(parameters.getContext().getContextId(), parameters.getDocument().getId(), con));
                    parameters.getUpdatedCols().add(Metadata.VERSION_LITERAL);
                } catch (SQLException e) {
                    LoggerHolder.LOG.error("SQL error", e);
                } finally {
                    releaseReadConnection(parameters.getContext(), con);
                }

                action = new CreateVersionAction(this, QUERIES, parameters.getContext(), Collections.singletonList(parameters.getDocument()), session);
                newVersion = true;
            }

            // Perform action
            perform(action, true);

            // Auto-delete old versions (if applicable)
            if (newVersion && folderAdmin > 0) {
                int id = parameters.getDocument().getId();
                if (id != NEW && checkAutodeleteCapabilitySafe(folderAdmin, session)) {
                    int maxVersions = InfostoreAutodeleteSettings.getMaxNumberOfFileVersions(folderAdmin, session.getContextId());
                    if (maxVersions > 0) {
                        int currentVersion = parameters.getDocument().getVersion();
                        new InfostoreAutodeletePerformer(this).removeVersionsByMaxCount(id, currentVersion, maxVersions, session);
                    }
                }
            }
        } finally {
            if (null != pending) {
                pending.run();
            }
        }
    }

    private boolean checkAutodeleteCapabilitySafe(int folderAdmin, Session session) {
        try {
            if (folderAdmin == session.getUserId()) {
                return InfostoreAutodeleteSettings.hasAutodeleteCapability(session);
            }

            Optional<Session> otherSession = findSessionFor(folderAdmin, session.getContextId());
            if (otherSession.isPresent()) {
                return InfostoreAutodeleteSettings.hasAutodeleteCapability(otherSession.get());
            }

            return InfostoreAutodeleteSettings.hasAutodeleteCapability(folderAdmin, session.getContextId());
        } catch (Exception e) {
            LoggerHolder.LOG.error("Failed to check for the capability required to perform auto-deletion of versions. Assumin that capability is not granted for now.", e);
            return false;
        }
    }

    private Optional<Session> findSessionFor(int userId, int contextId) {
        SessiondService sessiondService = SessiondService.SERVICE_REFERENCE.get();
        if (sessiondService == null) {
            return Optional.empty();
        }

        CapabilityService capabilityService = ServerServiceRegistry.getInstance().getService(CapabilityService.class);
        if (capabilityService == null) {
            return Optional.empty();
        }

        Optional<Collection<String>> optionalSessions = Sessions.getSessionsOfUser(userId, contextId, sessiondService);
        if (!optionalSessions.isPresent()) {
            return Optional.empty();
        }

        Collection<String> foundSessions = optionalSessions.get();
        for (String sessionId : foundSessions) {
            Session session = sessiondService.getSession(sessionId);
            if (session != null && (Origin.HTTP_JSON == session.getOrigin())) {
                try {
                    if (capabilityService.getCapabilities(session).contains(Permission.INFOSTORE.getCapabilityName())) {
                        return Optional.of(session);
                    }
                } catch (Exception e) {
                    // Ignore
                    LoggerHolder.LOG.debug("Failed to obtain cababilities for user {} in context {}", I(userId), I(contextId), e);
                }
            }
        }

        // Found no suitable session
        return Optional.empty();
    }

    @Override
    public void removeDocument(final long folderId, final long date, final ServerSession session, boolean hardDelete) throws OXException {
        if (folderId == getSharedFilesFolderID()) {
            throw InfostoreExceptionCodes.NO_DELETE_PERMISSION.create();
        }
        Context context = session.getContext();
        String whereClause = "infostore.folder_id = " + folderId;
        List<DocumentMetadata> allDocuments = SearchIterators.asList(InfostoreIterator.allDocumentsWhere(whereClause, Metadata.VALUES_ARRAY, this, context));
        if (!allDocuments.isEmpty()) {
            objectPermissionLoader.add(allDocuments, context, objectPermissionLoader.load(folderId, context));
            removeDocuments(allDocuments, date, session, null, hardDelete);
        }
    }

    /**
     * Gets the trash folder ID for the given session
     *
     * @param session The session to get the trash folder ID for
     * @param folderAccess The folderAccess for the given session
     * @return The trash folder ID for the given session, or -1 if the trash folder is not present for the given session
     * @throws OXException
     */
    private int getTrashFolderID(ServerSession session, OXFolderAccess folderAccess) throws OXException {
        return session.getUser().isGuest() ? -1 : folderAccess.getDefaultFolderID(session.getUserId(), FolderObject.INFOSTORE, FolderObject.TRASH);
    }

    /**
     * Adds the original folder path to the supplied meta data if necessary
     * <p>
     * The original folder path will be added if document is not new, the document's folder is supposed to be changed to the trash folder
     *
     * @param metadata The document
     * @param modifiedFields The modified fields
     * @param session The session
     * @param folderAccess the folder access
     * @return The new modified fields, enhanced by {@link Metadata#ORIGIN_LITERAL} if the original path was added to the document.
     * @throws OXException
     */
    private Metadata[] addOriginPathIfNecessary(DocumentMetadata metadata, Metadata[] modifiedFields, ServerSession session, OXFolderAccess folderAccess) throws OXException {
        final int trashFolderId = getTrashFolderID(session, folderAccess);
        List<Metadata> fieldsToReturn = new ArrayList<Metadata>(Arrays.asList(modifiedFields));
        if (fieldsToReturn.contains(Metadata.FOLDER_ID_LITERAL) && false == fieldsToReturn.contains(Metadata.ORIGIN_LITERAL)) {
            // File's folder is supposed to be changed and no origin path is set
            if (NEW != metadata.getId() && 0 != metadata.getFolderId() && isBelowTrashFolder((int)metadata.getFolderId(), trashFolderId, folderAccess)) {
                // File is supposed to be moved to a Trash folder
                DocumentMetadata loaded = getDocumentMetadata(-1, metadata.getId(), metadata.getVersion(), session);
                if (loaded.getFolderId() != metadata.getFolderId()) {
                    InfostoreFolderPath originPath = generateOriginPathIfTrashed((int)loaded.getFolderId(), trashFolderId, session, folderAccess);
                    if (null != originPath) {
                        metadata.setOriginFolderPath(originPath);
                        fieldsToReturn.add(Metadata.ORIGIN_LITERAL);
                    }
                }
            }
        }
        return fieldsToReturn.toArray(new Metadata[fieldsToReturn.size()]);
    }

    /**
     * Gets the personal folder for the given session
     *
     * @param session The session
     * @param folderAccess the folder access for the given session
     * @return The personal folder for the given session, or null
     * @throws OXException
     */
    private int getPersonalFolderID(ServerSession session, OXFolderAccess folderAccess) throws OXException {
        return folderAccess.getDefaultFolderID(session.getUserId(), FolderObject.INFOSTORE, FolderObject.PUBLIC);
    }

    /**
     * Checks if the folder with the given name exists in the parent folder and re-creates it if not.
     *
     * @param name The name of the folder to check
     * @param parentFolderId The ID of the parent folder
     * @param pathRecreated
     * @param folderAccess The folder access
     * @param session The session
     * @return The ID of the folder, either the existing
     * @throws OXException
     */
    private int ensureFolderExistsForName(String name, int parentFolderId, boolean[] pathRecreated, OXFolderAccess folderAccess, ServerSession session) throws OXException {

        try {
            //Check if the folder already exists
            TIntList folderIds = OXFolderSQL.lookUpFolders(parentFolderId, name, FolderObject.INFOSTORE, null, session.getContext());
            for (int folderId : folderIds.toArray()) {
                if (name.equals(folderAccess.getFolderName(folderId))) {
                    return folderId;
                }
            }
        } catch (SQLException e) {
            throw InfostoreExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        }

        //Re-Create the folder
        FolderObject parentFolder = folderAccess.getFolderObject(parentFolderId);
        List<OCLPermission> permissions = parentFolder.getPermissions();
        OXFolderManager folderManager = OXFolderManager.getInstance(session, folderAccess);
        FolderObject toCreate = new FolderObject();
        toCreate.setModule(FolderObject.INFOSTORE);
        toCreate.setParentFolderID(parentFolderId);
        toCreate.setFolderName(name);
        toCreate.setPermissions(permissions);
        toCreate.setType(parentFolder.getType());
        folderManager.createFolder(toCreate, true, System.currentTimeMillis());

        pathRecreated[0] = true;
        return toCreate.getObjectID();
    }

    /**
     * Checks if the given folder is below the trash
     *
     * @param folderId The ID of the folder to check
     * @param trashFolderId The ID of the trash folder to check
     * @param folderAccess The folderAccess for the given session
     * @return True, if the given folder is below the trash folder, false otherwise
     * @throws OXException
     */
    private boolean isBelowTrashFolder(int folderId, int trashFolderId, OXFolderAccess folderAccess) throws OXException {
        if (-1 == trashFolderId) {
            return false;
        }
        while (-1 != folderId) {
            if (folderId == trashFolderId) {
                return true;
            }
            if (folderId == FolderObject.SYSTEM_INFOSTORE_FOLDER_ID) {
                return false;
            }
            folderId = folderAccess.getParentFolderID(folderId);
        }
        return false;
    }

    /**
     * Generates the original path for a given folder which is about to be deleted
     *
     * @param oldFolderId The folder
     * @param trashFolderId the ID of the trash folder
     * @param session The session
     * @param folderAccess The folder access for the given session
     * @return The original path
     * @throws OXException
     */
    private InfostoreFolderPath generateOriginPathIfTrashed(int oldFolderId, final int trashFolderId, ServerSession session, OXFolderAccess folderAccess) throws OXException {
        final int personalFolder = getPersonalFolderID(session, folderAccess);

        List<String> result = null;
        while (-1 != oldFolderId && FolderObject.SYSTEM_INFOSTORE_FOLDER_ID != oldFolderId) {
            if (trashFolderId == oldFolderId) {
                // Obviously already located in/below Trash folder. No original path required.
                return null;
            }

            if (null == result) {
                result = new ArrayList<>(6);
            }
            if (oldFolderId == FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID || oldFolderId == FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID || oldFolderId == personalFolder) {
                result.add(Integer.toString(oldFolderId));
                oldFolderId = -1; // force termination of while loop
            } else {
                FolderObject folder = folderAccess.getFolderObject(oldFolderId);
                result.add(folder.getFolderName());
                oldFolderId = folder.getParentFolderID();
            }
        }
        return null == result ? null : InfostoreFolderPath.copyOf(Lists.reverse(result));
    }

    /**
     * Removes the documents (hard delete) and all of the corresponding document versions.
     *
     * @param allDocuments The documents to remove
     * @param date The date
     * @param session The session
     * @param rejected A list which will contain all documents which could not be deleted
     * @throws OXException
     */
    private void removeDocuments(final List<DocumentMetadata> allDocuments, final long date, final ServerSession session, final List<DocumentMetadata> rejected) throws OXException {
        final List<DocumentMetadata> delDocs = new ArrayList<>();
        final List<DocumentMetadata> delVers = new ArrayList<>();
        final TIntSet rejectedIds = new TIntHashSet(allDocuments.size());

        for (final DocumentMetadata m : allDocuments) {
            if (m.getSequenceNumber() > date) {
                if (rejected == null) {
                    throw InfostoreExceptionCodes.NOT_ALL_DELETED.create();
                }
                rejected.add(m);
                rejectedIds.add(m.getId());
            } else {
                checkWriteLock(m, session);
                delDocs.add(m);
            }
        }

        final Context context = session.getContext();

        /*
         * Move records into del_* tables
         */
        perform(new ReplaceDocumentIntoDelTableAction(this, QUERIES, context, delDocs, session), true);

        /*
         * Load versions
         */
        Set<Integer> objectIDs = allDocuments.stream().map(d -> I(d.getId())).collect(Collectors.toSet());
        List<DocumentMetadata> allVersions = new ArrayList<DocumentMetadata>();
        String whereClause;
        if (!objectIDs.isEmpty()) {
            if (1 == objectIDs.size()) {
                whereClause = "infostore.id=" + objectIDs.iterator().next();
            } else {
                StringBuilder stringBuilder = new StringBuilder("infostore.id IN (");
                Strings.join(objectIDs, ",", stringBuilder);
                whereClause = stringBuilder.append(')').toString();
            }
            allVersions = SearchIterators.asList(InfostoreIterator.allVersionsWhere(whereClause, Metadata.VALUES_ARRAY, this, context));
        }

        /*
         * Remove referenced files from underlying storage
         */
        List<String> filestoreLocations = new ArrayList<>(allVersions.size());
        TIntList folderAdmins = new TIntLinkedList();
        for (final DocumentMetadata m : allVersions) {
            if (!rejectedIds.contains(m.getId())) {
                delVers.add(m);
                if (null != m.getFilestoreLocation()) {
                    filestoreLocations.add(m.getFilestoreLocation());
                    folderAdmins.add(security.getFolderOwners(Collections.singletonList(m), context));
                }
            }
        }
        removeFiles(context, filestoreLocations, folderAdmins.toArray());
        /*
         * Delete documents, all versions and object permissions from database
         */
        perform(new DeleteVersionAction(this, QUERIES, context, delVers, session), true);
        perform(new DeleteDocumentAction(this, QUERIES, context, delDocs, session), true);
        perform(new DeleteObjectPermissionAction(this, context, delDocs), true);
        rememberForGuestCleanup(context.getContextId(), delDocs);
    }

    /**
     * Removes a list of documents
     *
     * @param allDocuments The documents to remove
     * @param date The client's timestamp
     * @param session The session
     * @param rejected A list which will contain all documents which could not be deleted after the method returns
     * @param hardDelete True in order to hard delete the documents, false to move them to the trash
     * @throws OXException
     */
    protected void removeDocuments(final List<DocumentMetadata> allDocuments, final long date, final ServerSession session, final List<DocumentMetadata> rejected, boolean hardDelete) throws OXException {
        if (hardDelete) {
            //Perform hard deletion
            removeDocuments(allDocuments, date, session, rejected);
        } else {
            //Move to trash
            moveDocumentsToTrash(allDocuments, date, session, rejected);
        }
    }

    /**
     * Moves documents to the trash.
     * <p>
     * Documents which are already inside the trash will be deleted permanently.
     * Also if the trash folder is not available the documents will be deleted permanently as well.
     *</p>
     *
     * @param allDocuments The documents to delete
     * @param date The client's time stamp
     * @param session The session
     * @param rejected A list which will contain all documents which could not be deleted after the method returns
     * @throws OXException
     */
    private void moveDocumentsToTrash(final List<DocumentMetadata> allDocuments, final long date, final ServerSession session, final List<DocumentMetadata> rejected) throws OXException {
        final OXFolderAccess folderAccess = new OXFolderAccess(session.getContext());

        //Check present of trash folder
        final int trashFolderID = getTrashFolderID(session, folderAccess);
        if (trashFolderID == -1) {
            //Perform hard deletion instead
            removeDocuments(allDocuments, date, session, rejected);
        } else {
            //Distinguish between documents already in or below trash folder
            List<DocumentMetadata> documentsToDelete = new ArrayList<>();
            List<DocumentMetadata> documentsToMove = new ArrayList<>();
            for (DocumentMetadata document : allDocuments) {
                if (isBelowTrashFolder((int) document.getFolderId(), trashFolderID, folderAccess)) {
                    documentsToDelete.add(document);
                } else {
                    documentsToMove.add(document);
                }
            }

            //hard-delete already deleted documents
            if (!documentsToDelete.isEmpty()) {
                removeDocuments(documentsToDelete, date, session, rejected);
            }

            //move other documents to trash
            if (!documentsToMove.isEmpty()) {
                final List<DocumentMetadata> notMoved = moveDocuments(session, documentsToMove, trashFolderID, date, true);
                rejected.addAll(notMoved);
            }
        }
    }

    /**
     * Removes the supplied file from the underlying storage. If a transaction is active, the file is remembered to be deleted during
     * the {@link #commit()}-phase - otherwise it's deleted from the storage directly.
     *
     * @param context The context
     * @param filestoreLocation The location referencing the file to be deleted in the storage
     * @param folderAdmin The folder administrator
     * @throws OXException
     */
    private void removeFile(final Context context, final String filestoreLocation, int folderAdmin) throws OXException {
        removeFiles(context, Collections.singletonList(filestoreLocation), new int[] { folderAdmin });
    }

    /**
     * Removes the supplied files from the underlying storage. If a transaction is active, the files are remembered to be deleted during
     * the {@link #commit()}-phase - otherwise they're deleted from the storage directly.
     *
     * @param context The context
     * @param filestoreLocations A list of locations referencing the files to be deleted in the storage
     * @param folderAdmins The associated folder administrators
     * @throws OXException
     */
    private void removeFiles(Context context, List<String> filestoreLocations, int[] folderAdmins) throws OXException {
        if (null != filestoreLocations) {
            int size = filestoreLocations.size();
            if (0 < size) {
                int contextId = context.getContextId();
                List<FileRemoveInfo> removeList = fileIdRemoveList.get();
                if (null != removeList) {
                    for (int i = 0; i < size; i++) {
                        removeList.add(new FileRemoveInfo(filestoreLocations.get(i), folderAdmins[i], contextId));
                    }
                } else {
                    Map<UserAndContext, List<String>> map = new LinkedHashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        UserAndContext key = UserAndContext.newInstance(folderAdmins[i], contextId);
                        List<String> list = map.get(key);
                        if (null == list) {
                            list = new LinkedList<>();
                            map.put(key, list);
                        }
                        list.add(filestoreLocations.get(i));
                    }
                    for (Map.Entry<UserAndContext, List<String>> entry : map.entrySet()) {
                        List<String> locations = entry.getValue();
                        getFileStorage(entry.getKey().getUserId(), contextId).deleteFiles(locations.toArray(new String[locations.size()]));
                    }
                }
            }
        }
    }

    /**
     * Remembers the permission entities of the supplied documents for subsequent guest cleanup tasks.
     *
     * @param contextID The context identifier
     * @param removedDocuments The documents being removed
     */
    private void rememberForGuestCleanup(int contextID, List<DocumentMetadata> removedDocuments) {
        if (null != removedDocuments && !removedDocuments.isEmpty()) {
            for (DocumentMetadata document : removedDocuments) {
                List<ObjectPermission> objectPermissions = document.getObjectPermissions();
                if (null != objectPermissions && 0 < objectPermissions.size()) {
                    TIntObjectMap<TIntSet> cleanupList = guestCleanupList.get();
                    TIntSet entities = cleanupList.get(contextID);
                    if (null == entities) {
                        entities = new TIntHashSet(objectPermissions.size());
                        cleanupList.put(contextID, entities);
                    }
                    for (ObjectPermission permission : objectPermissions) {
                        if (false == permission.isGroup()) {
                            entities.add(permission.getEntity());
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets a list of infostore documents.
     *
     * @param provider The DB provider
     * @param context The context
     * @param ids The object IDs of the documents to get
     * @param metadata The metadata to retrieve
     * @return The documents
     * @throws OXException
     */
    private static List<DocumentMetadata> getAllDocuments(DBProvider provider, Context context, int[] ids, Metadata[] metadata) throws OXException {
        if (null == ids || 0 == ids.length) {
            return Collections.emptyList();
        }
        if (1 == ids.length) {
            return SearchIterators.asList(InfostoreIterator.allDocumentsWhere("infostore.id = " + ids[0], metadata, provider, context));
        }
        StringBuilder StringBuilder = new StringBuilder("infostore.id IN (");
        StringBuilder.append(String.valueOf(ids[0]));
        for (int i = 1; i < ids.length; i++) {
            StringBuilder.append(',').append(String.valueOf(ids[i]));
        }
        StringBuilder.append(')');
        return SearchIterators.asList(InfostoreIterator.allDocumentsWhere(StringBuilder.toString(), metadata, provider, context));
    }

    /**
     * Moves the supplied documents to another folder.
     *
     * @param session The server session
     * @param documents The source documents to move
     * @param destinationFolderID The destination folder ID
     * @param sequenceNumber The client timestamp to catch concurrent modifications
     * @param adjustFilenamesAsNeeded <code>true</code> to adjust filenames in target folder automatically, <code>false</code>, otherwise
     * @return A list of documents that could not be moved due to concurrent modifications
     * @throws OXException
     */
    protected List<DocumentMetadata> moveDocuments(ServerSession session, List<DocumentMetadata> documents, long destinationFolderID, long sequenceNumber, boolean adjustFilenamesAsNeeded) throws OXException {
        Context context = session.getContext();
        User user = session.getUser();
        UserPermissionBits permissionBits = session.getUserPermissionBits();
        /*
         * check destination folder permissions
         */
        EffectiveInfostoreFolderPermission destinationFolderPermission = security.getFolderPermission(destinationFolderID, context, user, permissionBits);
        if (false == destinationFolderPermission.canCreateObjects()) {
            throw InfostoreExceptionCodes.NO_CREATE_PERMISSION.create();
        }

        /*
         * check source folder permissions, write locks and client timestamp
         */
        List<DocumentMetadata> rejectedDocuments = new LinkedList<>();
        List<DocumentMetadata> sourceDocuments = new ArrayList<>(documents.size());
        List<EffectiveInfostorePermission> permissions = security.getInfostorePermissions(documents, context, user, permissionBits);
        for (EffectiveInfostorePermission permission : permissions) {
            if (!permission.canDeleteObject()) {
                throw InfostoreExceptionCodes.NO_DELETE_PERMISSION.create();
            }
        }

        for (DocumentMetadata document : documents) {
            checkWriteLock(document, session);
            if (document.getSequenceNumber() <= sequenceNumber) {
                sourceDocuments.add(document);
            } else {
                rejectedDocuments.add(document);
            }
        }

        int numberOfDocuments = sourceDocuments.size();
        if (0 < numberOfDocuments) {
            /*
             * prepare move
             */
            Connection readConnection = null;
            FilenameReserver filenameReserver = new FilenameReserverImpl(session.getContext(), this);
            try {
                final OXFolderAccess folderAccess = new OXFolderAccess(session.getContext());
                final int trashFolderID = getTrashFolderID(session, folderAccess);
                readConnection = getReadConnection(context);
                boolean updateOrigin = false;
                List<DocumentMetadata> tombstoneDocuments = new ArrayList<>(numberOfDocuments);
                List<DocumentMetadata> documentsToUpdate = new ArrayList<>(numberOfDocuments);
                List<DocumentMetadata> versionsToUpdate = new ArrayList<>();
                List<DocumentMetadata> objectPermissionsToDelete = new ArrayList<>();
                for (DocumentMetadata document : sourceDocuments) {
                    /*
                     * prepare updated document
                     */
                    DocumentMetadataImpl documentToUpdate = new DocumentMetadataImpl(document);
                    documentToUpdate.setModifiedBy(session.getUserId());
                    documentToUpdate.setFolderId(destinationFolderID);
                    documentsToUpdate.add(documentToUpdate);
                    /*
                     * prepare tombstone entry in del_infostore table for source document
                     */
                    DocumentMetadataImpl tombstoneDocument = new DocumentMetadataImpl(document);
                    tombstoneDocument.setModifiedBy(session.getUserId());
                    tombstoneDocuments.add(tombstoneDocument);
                    /*
                     * check origin path
                     */
                    //@formatter:off
                    InfostoreFolderPath originFolderPath;
                    if (-1 == trashFolderID) {
                        originFolderPath = null;
                    } else if (isBelowTrashFolder((int) document.getFolderId(), trashFolderID, folderAccess)) {
                        // A move from a trash folder...
                        if (!isBelowTrashFolder((int) destinationFolderID, trashFolderID, folderAccess)) {
                            // ... to a non-trash folder. Drop origin path information
                            updateOrigin = true;
                        }
                        originFolderPath = null;
                    } else {
                        originFolderPath = generateOriginPathIfTrashed((int) document.getFolderId(), trashFolderID, session, folderAccess);
                    }
                    //@formatter:on
                    if (null != originFolderPath && !originFolderPath.isEmpty()) {
                        documentToUpdate.setOriginFolderPath(originFolderPath);
                        updateOrigin = true;
                    } else {
                        documentToUpdate.setOriginFolderPath(null);
                    }
                    if (null != originFolderPath && !originFolderPath.isEmpty()) {
                        tombstoneDocument.setOriginFolderPath(originFolderPath);
                    } else {
                        tombstoneDocument.setOriginFolderPath(null);
                    }
                    /*
                     * prepare object permission update / removal
                     */
                    if (null != document.getObjectPermissions() && 0 < document.getObjectPermissions().size()) {
                        objectPermissionsToDelete.add(document);
                    }
                }
                /*
                 * reserve filenames
                 */
                Map<DocumentMetadata, FilenameReservation> reservations = filenameReserver.reserve(documentsToUpdate, adjustFilenamesAsNeeded);
                if (adjustFilenamesAsNeeded) {
                    /*
                     * take over adjusted filenames; remember to update document version, too
                     */
                    for (Entry<DocumentMetadata, FilenameReservation> entry : reservations.entrySet()) {
                        FilenameReservation reservation = entry.getValue();
                        if (reservation.wasAdjusted()) {
                            DocumentMetadata document = entry.getKey();
                            if (reservation.wasSameTitle()) {
                                document.setTitle(reservation.getFilename());
                            }
                            document.setFileName(reservation.getFilename());
                            versionsToUpdate.add(document);
                        }
                    }
                }
                /*
                 * perform tombstone creations
                 */
                perform(new ReplaceDocumentIntoDelTableAction(this, QUERIES, session.getContext(), tombstoneDocuments, session), true);
                /*
                 * Do the version control
                 */
                VersionControlUtil.doVersionControl(this, documentsToUpdate, sourceDocuments, destinationFolderID, context);
                /*
                 * perform document move
                 */
                Metadata[] modified;
                if (updateOrigin) {
                    modified = new Metadata[] { Metadata.SEQUENCE_NUMBER_LITERAL, Metadata.LAST_MODIFIED_LITERAL, Metadata.MODIFIED_BY_LITERAL, Metadata.FOLDER_ID_LITERAL, Metadata.ORIGIN_LITERAL };
                } else {
                    modified = new Metadata[] { Metadata.SEQUENCE_NUMBER_LITERAL, Metadata.LAST_MODIFIED_LITERAL, Metadata.MODIFIED_BY_LITERAL, Metadata.FOLDER_ID_LITERAL };
                }
                perform(new UpdateDocumentAction(this, QUERIES, session.getContext(), documentsToUpdate, sourceDocuments, modified, sequenceNumber, session), true);
                /*
                 * perform object permission inserts / removals
                 */
                if (0 < objectPermissionsToDelete.size()) {
                    perform(new DeleteObjectPermissionAction(this, context, objectPermissionsToDelete), true);
                    rememberForGuestCleanup(context.getContextId(), objectPermissionsToDelete);
                }
                /*
                 * perform version update (only required in case of adjusted filenames)
                 */
                if (0 < versionsToUpdate.size()) {
                    perform(new UpdateVersionAction(this, QUERIES, session.getContext(), versionsToUpdate, sourceDocuments, new Metadata[] { Metadata.FILENAME_LITERAL, Metadata.TITLE_LITERAL }, sequenceNumber, session), true);
                }
            } finally {
                filenameReserver.cleanUp();
                if (null != readConnection) {
                    releaseReadConnection(context, readConnection);
                }
            }
        }
        /*
         * return rejected documents
         */
        return rejectedDocuments;
    }

    @Override
    public IDTuple copyDocument(ServerSession session, IDTuple id, int version, DocumentMetadata update, Metadata[] modifiedFields, InputStream newFile, long sequenceNumber, String targetFolderID) throws OXException {

        int fileId;
        try {
            fileId = i(Integer.valueOf(id.getId()));
        } catch (NumberFormatException e) {
            throw InfostoreExceptionCodes.DOCUMENT_NOT_EXIST.create();
        }

        long folderId;
        try {
            folderId = Long.parseLong(id.getFolder());
        } catch (NumberFormatException e) {
            throw InfostoreExceptionCodes.NOT_INFOSTORE_FOLDER.create(e, targetFolderID);
        }

        long destinationFolderID;
        try {
            destinationFolderID = Long.parseLong(targetFolderID);
        } catch (NumberFormatException e) {
            throw InfostoreExceptionCodes.NOT_INFOSTORE_FOLDER.create(e, targetFolderID);
        }

        InputStream in = newFile;
        try {
            DocumentMetadata orig;
            if (null == id.getFolder()) {
                orig = getDocumentMetadata(-1, fileId, version, session);
            } else {
                orig = getDocumentMetadata(folderId, fileId, version, session);
                if (0 < orig.getFolderId() && folderId != orig.getFolderId()) {
                    throw InfostoreExceptionCodes.DOCUMENT_NOT_EXIST.create();
                }
            }
            final long orignalFolderId = orig.getFolderId();

            if(in == null && orig.getFileName() != null) {
               in = getDocument(fileId, version, session);
            }

            //apply meta data update if provided
            if(update != null) {
                GetSwitch get = new GetSwitch(update);
                SetSwitch set = new SetSwitch(orig);
                for(Metadata modifiedField : modifiedFields) {
                    set.setValue(modifiedField.doSwitch(get));
                    modifiedField.doSwitch(set);
                }
                /*
                 * remove creation date of original file so that the current time will be assigned during creation
                 */
                if (false == Arrays.asList(modifiedFields).contains(Metadata.CREATION_DATE_LITERAL)) {
                    orig.setCreationDate(null);
                }
            }
            orig.setId(NEW);
            orig.setFolderId(destinationFolderID);
            orig.setObjectPermissions(null);
            FileStorageUtility.checkUrl(orig.getURL());

            //Trash handling
            OXFolderAccess folderAccess = new OXFolderAccess(session.getContext());
            int trashFolderID = getTrashFolderID(session, folderAccess);
            if(isBelowTrashFolder((int)destinationFolderID, trashFolderID, folderAccess)) {
                orig.setOriginFolderPath(generateOriginPathIfTrashed((int)orignalFolderId, trashFolderID, session, folderAccess));
            }

            if (in == null) {
                saveDocumentMetadata(orig, sequenceNumber, session);
            } else {
                saveDocument(orig, in, sequenceNumber, session);
            }

            return new IDTuple(targetFolderID, Integer.toString(orig.getId()));
        }
        finally {
            Streams.close(in);
        }
    }

    @Override
    public List<IDTuple> moveDocuments(ServerSession session, List<IDTuple> ids, long sequenceNumber, String targetFolderID, boolean adjustFilenamesAsNeeded) throws OXException {
        if (null == ids || 0 == ids.size()) {
            return Collections.emptyList();
        }
        long destinationFolderID;
        try {
            destinationFolderID = Long.parseLong(targetFolderID);
        } catch (NumberFormatException e) {
            throw InfostoreExceptionCodes.NOT_INFOSTORE_FOLDER.create(e, targetFolderID);
        }
        /*
         * get documents to move
         */
        int[] objectIDs = Tools.getObjectIDArray(ids);
        List<DocumentMetadata> allDocuments = getAllDocuments(this, session.getContext(), objectIDs, Metadata.VALUES_ARRAY);
        objectPermissionLoader.add(allDocuments, session.getContext(), Tools.getIDs(allDocuments));
        /*
         * Ensure folder ids are consistent between request and existing documents
         */
        Map<Integer, Long> idsToFolders = Tools.getIDsToFolders(ids);
        for (DocumentMetadata document : allDocuments) {
            Long requestedFolder = idsToFolders.get(I(document.getId()));
            long expectedFolder = document.getFolderId();
            if (requestedFolder == null || requestedFolder.longValue() != expectedFolder) {
                throw InfostoreExceptionCodes.NOT_EXIST.create();
            }
        }
        /*
         * perform move
         */
        List<DocumentMetadata> rejectedDocuments = moveDocuments(session, allDocuments, destinationFolderID, sequenceNumber, adjustFilenamesAsNeeded);
        if (null == rejectedDocuments || 0 == rejectedDocuments.size()) {
            return Collections.emptyList();
        }
        List<IDTuple> rejectedIDs = new ArrayList<>();
        for (DocumentMetadata rejected : rejectedDocuments) {
            rejectedIDs.add(new IDTuple(Long.toString(rejected.getFolderId()), Integer.toString(rejected.getId())));
        }
        return rejectedIDs;
    }

    /**
     * Restores files from trash folder to origin location. If the path was deleted too, it will be recreated.
     *
     * @param toRestore A mapping of target folder identifiers to files to restore
     * @param session The session
     * @return The identifiers of those documents that could <b>not</b> be restored successfully
     * @throws OXException If restore fails
     */
    private List<IDTuple> restore(Map<String, List<IDTuple>> toRestore, ServerSession session) throws OXException {
        if (null == toRestore || toRestore.size() == 0) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        List<IDTuple> result = new ArrayList<>(toRestore.size());
        for (Map.Entry<String, List<IDTuple>> entry : toRestore.entrySet()) {
            String targetFolderId = entry.getKey();
            List<IDTuple> filesToRestore = entry.getValue();
            result.addAll(moveDocuments(session, filesToRestore, now, targetFolderId, true));
        }
        return result;
    }

    @Override
    public Map<IDTuple, String> restore(List<IDTuple> tuples, long destFolderId, ServerSession session) throws OXException {
        if (null == tuples || tuples.isEmpty()) {
            return Collections.emptyMap();
        }

        // The result list
        int size = tuples.size();
        Map<IDTuple, String> result = new LinkedHashMap<IDTuple, String>(size);

        // Check trash folder existence
        OXFolderAccess folderAccess = new OXFolderAccess(session.getContext());
        int trashFolderID = getTrashFolderID(session, folderAccess);
        if (-1 == trashFolderID) {
            return Collections.emptyMap();
        }

        // Checks tuples to restore
        for (IDTuple tuple : tuples) {
            if (false == isBelowTrashFolder(Integer.parseInt(tuple.getFolder()), trashFolderID, folderAccess)) {
                throw InfostoreExceptionCodes.INVALID_FOLDER_IDENTIFIER.create("File does not reside in trash folder");
            }
        }

        // Load origin paths
        TIntObjectMap<InfostoreFolderPath> originPaths;
        if (size > 1) {
            SearchIterator<DocumentMetadata> iterator = null;
            try {
                TimedResult<DocumentMetadata> documents = getDocuments(tuples, new Metadata[] { Metadata.ID_LITERAL, Metadata.ORIGIN_LITERAL, Metadata.FOLDER_ID_LITERAL }, session);
                iterator = documents.results();
                originPaths = new TIntObjectHashMap<>(size);
                while (iterator.hasNext()) {
                    DocumentMetadata metadata = iterator.next();
                    InfostoreFolderPath originPath = metadata.getOriginFolderPath();
                    if (null != originPath) {
                        originPaths.put(metadata.getId(), originPath);
                    }
                }
            } finally {
                SearchIterators.close(iterator);
            }
        } else {
            IDTuple tuple = tuples.get(0);
            DocumentMetadata metadata = getDocumentMetadata(getUnsignedLong(tuple.getFolder()), getUnsignedInteger(tuple.getId()), InfostoreFacade.CURRENT_VERSION, session);
            InfostoreFolderPath originPath = metadata.getOriginFolderPath();
            originPaths = new TIntObjectHashMap<>(1);
            if (originPath != null) {
                originPaths.put(metadata.getId(), originPath);
            }
        }

        // Iterate tuples to restore
        Map<String, List<IDTuple>> toRestore = new LinkedHashMap<>(size);
        boolean[] pathRecreated = new boolean[] { false };
        int personalFolderId = -1;
        for (IDTuple tuple : tuples) {
            InfostoreFolderPath originPath = originPaths.get(getUnsignedInteger(tuple.getId()));
            if (null == originPath) {
                originPath = InfostoreFolderPath.EMPTY_PATH;
            }

            long folderId;
            try {
                switch (originPath.getType()) {
                    case PRIVATE:
                        if (-1 == personalFolderId) {
                            personalFolderId = getPersonalFolderID(session, folderAccess);
                        }
                        folderId = personalFolderId;
                        break;
                    case PUBLIC:
                        folderId = FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID;
                        break;
                    case SHARED:
                        folderId = FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID;
                        break;
                    case UNDEFINED: /* fall-through */
                    default:
                        folderId = destFolderId;
                        originPath = InfostoreFolderPath.EMPTY_PATH;
                        break;
                }
                if (!originPath.isEmpty()) {
                    pathRecreated[0] = false;
                    for (String name : originPath.getPathForRestore()) {
                        folderId = ensureFolderExistsForName(name, (int) folderId, pathRecreated, folderAccess, session);
                    }
                }
            } catch (OXException e) {
                if (!"FLD".equals(e.getPrefix()) || 6 != e.getCode()) {
                    throw e;
                }

                folderId = destFolderId;
            }

            List<IDTuple> tuplesToRestore = toRestore.get(Long.toString(folderId));
            if (null == tuplesToRestore) {
                tuplesToRestore = new ArrayList<>();
                toRestore.put(Long.toString(folderId), tuplesToRestore);
            }
            tuplesToRestore.add(tuple);
            result.put(IDTuple.copy(tuple), String.valueOf(folderId));
        }

        List<IDTuple> restoreResult = restore(toRestore, session);
        for (IDTuple id : restoreResult) {
            result.remove(id);
        }
        return result;
    }

    @Override
    public List<IDTuple> removeDocument(final List<IDTuple> ids, final long date, final ServerSession session, boolean hardDelete) throws OXException {
        if (null == ids || 0 == ids.size()) {
            return Collections.emptyList();
        }

        final Map<Integer, Long> idsToFolders = Tools.getIDsToFolders(ids);
        final Context context = session.getContext();
        final User user = session.getUser();
        final UserPermissionBits userPermissionBits = session.getUserPermissionBits();

        Set<Integer> objectIDs = idsToFolders.keySet();
        String whereClause;
        if (1 == objectIDs.size()) {
            whereClause = "infostore.id=" + objectIDs.iterator().next();
        } else {
            StringBuilder stringBuilder = new StringBuilder("infostore.id IN (");
            Strings.join(objectIDs, ",", stringBuilder);
            whereClause = stringBuilder.append(')').toString();
        }
        List<DocumentMetadata> allDocuments = SearchIterators.asList(InfostoreIterator.allDocumentsWhere(whereClause, Metadata.VALUES_ARRAY, this, context));
        objectPermissionLoader.add(allDocuments, context, idsToFolders.keySet());

        // Ensure folder ids are consistent between request and existing documents
        for (DocumentMetadata document : allDocuments) {
            Long requestedFolder = idsToFolders.get(I(document.getId()));
            long expectedFolder = document.getFolderId();
            if (requestedFolder == null || requestedFolder.longValue() != expectedFolder) {
                throw InfostoreExceptionCodes.NOT_EXIST.create();
            }
        }

        // Check Permissions
        List<EffectiveInfostorePermission> permissions = security.getInfostorePermissions(allDocuments, context, user, userPermissionBits);
        for (EffectiveInfostorePermission permission : permissions) {
            if (!permission.canDeleteObject()) {
                throw InfostoreExceptionCodes.NO_DELETE_PERMISSION.create();
            }
        }

        final Set<Integer> unknownDocuments = new HashSet<>(idsToFolders.keySet());
        for (DocumentMetadata document : allDocuments) {
            unknownDocuments.remove(I(document.getId()));
        }

        final List<DocumentMetadata> rejectedDocuments = new ArrayList<>();
        removeDocuments(allDocuments, date, session, rejectedDocuments, hardDelete);

        List<IDTuple> rejectedIDs = new ArrayList<>(rejectedDocuments.size() + unknownDocuments.size());
        for (final DocumentMetadata rejected : rejectedDocuments) {
            rejectedIDs.add(new IDTuple(Long.toString(rejected.getFolderId()), Integer.toString(rejected.getId())));
        }

        for (Integer notFound : unknownDocuments) {
            rejectedIDs.add(new IDTuple(idsToFolders.get(notFound).toString(), notFound.toString()));
        }

        return rejectedIDs;
    }

    @Override
    public void removeDocuments(List<IDTuple> ids, Context context) throws OXException {
        if (null == ids || 0 == ids.size()) {
            return;
        }

        final Map<Integer, Long> idsToFolders = Tools.getIDsToFolders(ids);
        Set<Integer> objectIDs = idsToFolders.keySet();
        String whereClause;
        if (1 == objectIDs.size()) {
            whereClause = "infostore.id=" + objectIDs.iterator().next();
        } else {
            StringBuilder stringBuilder = new StringBuilder("infostore.id IN (");
            Strings.join(objectIDs, ",", stringBuilder);
            whereClause = stringBuilder.append(')').toString();
        }
        List<DocumentMetadata> allDocuments = SearchIterators.asList(InfostoreIterator.allDocumentsWhere(whereClause, Metadata.VALUES_ARRAY, this, context));
        List<DocumentMetadata> allVersions = SearchIterators.asList(InfostoreIterator.allVersionsWhere(whereClause, Metadata.VALUES_ARRAY, this, context));

        // Ensure folder ids are consistent between request and existing documents
        for (DocumentMetadata document : allDocuments) {
            Long requestedFolder = idsToFolders.get(I(document.getId()));
            long expectedFolder = document.getFolderId();
            if (requestedFolder == null || requestedFolder.longValue() != expectedFolder) {
                throw InfostoreExceptionCodes.NOT_EXIST.create();
            }
        }

        final Set<Integer> unknownDocuments = new HashSet<>(idsToFolders.keySet());
        for (DocumentMetadata document : allDocuments) {
            unknownDocuments.remove(I(document.getId()));
        }

        if (!unknownDocuments.isEmpty()) {
            throw InfostoreExceptionCodes.NOT_EXIST.create();
        }

        /*
         * Move records into del_* tables
         */
        perform(new ReplaceDocumentIntoDelTableAction(this, QUERIES, context, allDocuments, null), true);
        /*
         * Remove referenced files from underlying storage
         */
        List<String> filestoreLocations = new ArrayList<>(allVersions.size());
        TIntList folderAdmins = new TIntLinkedList();
        for (final DocumentMetadata m : allVersions) {
            if (null != m.getFilestoreLocation()) {
                filestoreLocations.add(m.getFilestoreLocation());
                folderAdmins.add(security.getFolderOwners(Collections.singletonList(m), context));
            }
        }
        removeFiles(context, filestoreLocations, folderAdmins.toArray());
        /*
         * Delete documents, all versions and object permissions from database
         */
        perform(new DeleteVersionAction(this, QUERIES, context, allVersions, null), true);
        perform(new DeleteDocumentAction(this, QUERIES, context, allDocuments, null), true);
        perform(new DeleteObjectPermissionAction(this, context, allDocuments), true);
        rememberForGuestCleanup(context.getContextId(), allDocuments);
    }

    @Override
    public int[] removeVersion(final int id, final int[] versionIds, final ServerSession session) throws OXException {
        return removeVersion(id, versionIds, true, true, session);
    }

    /**
     * Removes denoted versions.
     *
     * @param id The document identifier
     * @param versionIds The identifiers of the versions to remove
     * @param allowRemoveCurrentVersion <code>true</code> to allow removing the current version; otherwise <code>false</code>
     * @param updateLastModified Whether last-modified information is supposed to be updated
     * @param session The session
     * @return The identifiers of those versions that could <b>not</b> be deleted successfully
     * @throws OXException If remove operation fails
     */
    public int[] removeVersion(int id, int[] versionIds, boolean allowRemoveCurrentVersion, boolean updateLastModified, ServerSession session) throws OXException {
        return removeVersion(id, versionIds, allowRemoveCurrentVersion, updateLastModified, session, true);
    }

    /**
     * Removes denoted versions.
     *
     * @param id The document identifier
     * @param versionIds The identifiers of the versions to remove
     * @param allowRemoveCurrentVersion <code>true</code> to allow removing the current version; otherwise <code>false</code>
     * @param updateLastModified Whether last-modified information is supposed to be updated
     * @param session The session
     * @param checkPermissions Whether to check for user permissions or not
     * @return The identifiers of those versions that could <b>not</b> be deleted successfully
     * @throws OXException If remove operation fails
     */
    public int[] removeVersion(int id, int[] versionIds, boolean allowRemoveCurrentVersion, boolean updateLastModified, ServerSession session, boolean checkPermissions) throws OXException {
        if (null == versionIds || 0 == versionIds.length) {
            return new int[0];
        }
        Context context = session.getContext();
        /*
         * load document metadata (including object permissions)
         */
        DocumentMetadata metadata = objectPermissionLoader.add(load(id, CURRENT_VERSION, context), context, null);
        /*
         * check write lock & permissions
         */
        try {
            checkWriteLock(metadata, session);
        } catch (OXException x) {
            LoggerHolder.LOG.trace("", x);
            return versionIds;
        }
        if (checkPermissions) {
            EffectiveInfostorePermission permission = security.getInfostorePermission(session, metadata);
            if (false == permission.canDeleteObject()) {
                throw InfostoreExceptionCodes.NO_DELETE_PERMISSION_FOR_VERSION.create();
            }
        }

        final StringBuilder versions = new StringBuilder().append('(');
        final Set<Integer> versionSet = new HashSet<>();

        for (final int v : versionIds) {
            versions.append(v).append(',');
            versionSet.add(Integer.valueOf(v));
        }
        versions.setCharAt(versions.length() - 1, ')');

        List<DocumentMetadata> allVersions = SearchIterators.asList(InfostoreIterator.allVersionsWhere("infostore_document.infostore_id = " + id + " AND infostore_document.version_number IN " + versions.toString() + " and infostore_document.version_number != 0 ", Metadata.VALUES_ARRAY, this, context));

        boolean anyRemoved = false;
        boolean removeCurrent = false;
        NextVersion: for (Iterator<DocumentMetadata> it = allVersions.iterator(); it.hasNext();) {
            DocumentMetadata v = it.next();
            if (v.getVersion() == metadata.getVersion()) {
                if (allowRemoveCurrentVersion) {
                    removeCurrent = true;
                } else {
                    it.remove();
                    continue NextVersion;
                }
            }
            versionSet.remove(Integer.valueOf(v.getVersion()));
            removeFile(context, v.getFilestoreLocation(), security.getFolderOwner(v, context));
            anyRemoved = true;
        }

        if (false == anyRemoved) {
            int[] retval = new int[versionSet.size()];
            int i = 0;
            for (Integer versionNumber : versionSet) {
                retval[i++] = versionNumber.intValue();
            }
            return retval;
        }

        // update version number if needed

        final DocumentMetadata update = new DocumentMetadataImpl(metadata);

        update.setModifiedBy(session.getUserId());

        final Set<Metadata> updatedFields = new HashSet<>();
        if (updateLastModified || removeCurrent) {
            updatedFields.add(Metadata.LAST_MODIFIED_LITERAL);
            updatedFields.add(Metadata.MODIFIED_BY_LITERAL);
        }

        if (removeCurrent) {

            // Update Version 0
            final DocumentMetadata oldVersion0 = load(id, 0, context);

            final DocumentMetadata version0 = new DocumentMetadataImpl(metadata);
            version0.setVersion(0);
            version0.setFileMIMEType("");

            perform(new UpdateVersionAction(this, QUERIES, context, version0, oldVersion0, new Metadata[] { Metadata.DESCRIPTION_LITERAL, Metadata.TITLE_LITERAL, Metadata.URL_LITERAL, Metadata.LAST_MODIFIED_LITERAL, Metadata.MODIFIED_BY_LITERAL, Metadata.FILE_MIMETYPE_LITERAL }, Long.MAX_VALUE, session), true);

            // Set new Version Number
            update.setVersion(db.getMaxActiveVersion(metadata.getId(), context, allVersions));
            updatedFields.add(Metadata.VERSION_LITERAL);
        }

        FilenameReserver filenameReserver = null;
        try {
            if (removeCurrent) {
                filenameReserver = new FilenameReserverImpl(context, db);
                metadata = load(metadata.getId(), update.getVersion(), context);
                FilenameReservation reservation = filenameReserver.reserve(metadata, true);
                if (reservation.wasAdjusted()) {
                    update.setFileName(reservation.getFilename());
                    updatedFields.add(Metadata.FILENAME_LITERAL);
                }
                if (reservation.wasSameTitle()) {
                    update.setTitle(reservation.getFilename());
                    updatedFields.add(Metadata.TITLE_LITERAL);
                }
            }
            if (!updatedFields.isEmpty()) {
                perform(new UpdateDocumentAction(this, QUERIES, context, update, metadata, updatedFields.toArray(new Metadata[updatedFields.size()]), Long.MAX_VALUE, session), true);
            }

            // Remove Versions
            perform(new DeleteVersionAction(this, QUERIES, context, allVersions, session), true);

            int[] retval = new int[versionSet.size()];
            int i = 0;
            for (Integer versionNumber : versionSet) {
                retval[i++] = versionNumber.intValue();
            }
            return retval;
        } finally {
            if (null != filenameReserver) {
                filenameReserver.cleanUp();
            }
        }
    }

    @Override
    public TimedResult<DocumentMetadata> getDocuments(final long folderId, final ServerSession session) throws OXException {
        return getDocuments(folderId, Metadata.HTTPAPI_VALUES_ARRAY, null, 0, session);
    }

    @Override
    public TimedResult<DocumentMetadata> getDocuments(final long folderId, final Metadata[] columns, final ServerSession session) throws OXException {
        return getDocuments(folderId, columns, null, 0, session);
    }

    @Override
    public TimedResult<DocumentMetadata> getDocuments(long folderId, Metadata[] columns, Metadata sort, int order, ServerSession session) throws OXException {
        return getDocuments(folderId, columns, sort, order, -1, -1, session);
    }

    @Override
    public TimedResult<DocumentMetadata> getDocuments(long folderId, Metadata[] columns, Metadata sort, int order, int start, int end, Context context, User user, UserPermissionBits permissionBits) throws OXException {
        return getDocuments(context, user, null, permissionBits, folderId, columns, sort, order, start, end);
    }

    @Override
    public TimedResult<DocumentMetadata> getDocuments(final long folderId, Metadata[] columns, Metadata sort, int order, int start, int end, ServerSession session) throws OXException {
        return getDocuments(session.getContext(), session.getUser(), session, session.getUserPermissionBits(), folderId, columns, sort, order, start, end);
    }

    @Override
    public TimedResult<DocumentMetadata> getUserSharedDocuments(Metadata[] columns, Metadata sort, int order, int start, int end, ServerSession session) throws OXException {
        boolean shouldTriggerMediaDataExtraction = shouldTriggerMediaDataExtraction();
        Metadata[] fields = Tools.getFieldsToQuery(columns, Metadata.LAST_MODIFIED_LITERAL, Metadata.ID_LITERAL, Metadata.FOLDER_ID_LITERAL);
        fields = addDateFieldsIfNeeded(fields, sort);
        if (shouldTriggerMediaDataExtraction) {
            fields = addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(fields);
        }
        Context context = session.getContext();
        /*
         * search documents shared by user
         */
        List<DocumentMetadata> documents;
        InfostoreIterator iterator = null;
        try {
            iterator = InfostoreIterator.sharedDocumentsByUser(context, session.getUser(), fields, sort, order, start, end, db);
            documents = Tools.removeNonPrivate(iterator, session, db);
        } finally {
            SearchIterators.close(iterator);
        }
        if (shouldTriggerMediaDataExtraction) {
            /*
             * trigger media meta-data extraction
             */
            new TriggerMediaMetaDataExtractionByCollection(documents, this, session).considerDocuments();
        }
        if (contains(columns, Metadata.SHAREABLE_LITERAL)) {
            for (DocumentMetadata document : documents) {
                /*
                 * assume document still shareable if loaded via "shared documents" query
                 */
                document.setShareable(true);
            }
        }
        if (contains(columns, Metadata.LOCKED_UNTIL_LITERAL)) {
            documents = lockedUntilLoader.add(documents, context, (Map<Integer, List<Lock>>) null);
        }
        if (contains(columns, Metadata.OBJECT_PERMISSIONS_LITERAL)) {
            documents = objectPermissionLoader.add(documents, context, (Map<Integer, List<ObjectPermission>>) null);
        }
        if (contains(columns, Metadata.CREATED_FROM_LITERAL)) {
            CreatedFromLoader createdFromLoader = new CreatedFromLoader(documents.stream().collect(Collectors.toMap(DocumentMetadata::getId, document -> document)), entityInfoLoader, session);
            documents = createdFromLoader.add(documents, context, (Map<Integer, EntityInfo>) null);
        }
        if (contains(columns, Metadata.MODIFIED_FROM_LITERAL)) {
            ModifiedFromLoader modifiedFromLoader = new ModifiedFromLoader(documents.stream().collect(Collectors.toMap(DocumentMetadata::getId, document -> document)), entityInfoLoader, session);
            documents = modifiedFromLoader.add(documents, context, (Map<Integer, EntityInfo>) null);
        }
        return new InfostoreTimedResult(new SearchIteratorAdapter<>(documents.iterator(), documents.size()));
    }

    @Override
    public TimedResult<DocumentMetadata> getVersions(final int id, final ServerSession session) throws OXException {
        return getVersions(-1, id, Metadata.HTTPAPI_VALUES_ARRAY, null, 0, session);
    }

    @Override
    public TimedResult<DocumentMetadata> getVersions(final long folderId, final int id, final Metadata[] columns, final ServerSession session) throws OXException {
        return getVersions(folderId, id, columns, null, 0, session);
    }

    @Override
    public TimedResult<DocumentMetadata> getVersions(final long folderId, final int id, Metadata[] columns, final Metadata sort, final int order, final ServerSession session) throws OXException {
        Context context = session.getContext();
        final EffectiveInfostorePermission infoPerm = security.getInfostorePermission(session, id);
        if (false == infoPerm.canReadObject()) {
            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
        }
        boolean shouldTriggerMediaDataExtraction = shouldTriggerMediaDataExtraction();
        boolean addCreatedFrom = contains(columns, Metadata.CREATED_FROM_LITERAL);
        boolean addModifiedFrom = contains(columns, Metadata.MODIFIED_FROM_LITERAL);
        Metadata[] cols = addSequenceNumberIfNeeded(columns);
        cols = addDateFieldsIfNeeded(cols, sort);
        if (shouldTriggerMediaDataExtraction) {
            cols = addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(cols);
        }
        InfostoreIterator iter = InfostoreIterator.versions(id, cols, sort, order, this, context);
        try {
            DocumentCustomizer customizer = new DocumentCustomizer() {

                @Override
                public DocumentMetadata handle(DocumentMetadata document) {
                    if (false == infoPerm.canReadObjectInFolder()) {
                        document.setOriginalFolderId(document.getFolderId());
                        document.setFolderId(getSharedFilesFolderID());
                        /*
                         * Re-sharing of files is not allowed.
                         */
                        document.setShareable(false);
                    } else {
                        document.setShareable(infoPerm.canShareObject());
                    }
                    return document;
                }
            };
            if (shouldTriggerMediaDataExtraction) {
                customizer = new TriggerMediaMetaDataExtractionDocumentCustomizer(this, null, session, customizer);
            }
            if (addCreatedFrom || addModifiedFrom) {
                customizer = new LoadEntityInfoCustomizer(entityInfoLoader, addCreatedFrom, addModifiedFrom, session, customizer);
            }
            iter.setCustomizer(customizer);
            TimedResult<DocumentMetadata> timedResult = new InfostoreTimedResult(iter);
            if (contains(columns, Metadata.LOCKED_UNTIL_LITERAL)) {
                timedResult = lockedUntilLoader.add(timedResult, context, Collections.singleton(I(id)));
            }
            if (contains(columns, Metadata.OBJECT_PERMISSIONS_LITERAL)) {
                timedResult = objectPermissionLoader.add(timedResult, context, Collections.singleton(I(id)));
            }
            iter = null; // Avoid premature closing
            return timedResult;
        } finally {
            SearchIterators.close(iter);
        }
    }

    @Override
    public TimedResult<DocumentMetadata> getDocuments(List<IDTuple> ids, Metadata[] columns, final ServerSession session) throws OXException {
        final Context context = session.getContext();
        final User user = session.getUser();
        final Map<Integer, Long> idsToFolders = Tools.getIDsToFolders(ensureFolderIDs(context, ids));
        List<Integer> objectIDs = Tools.getObjectIDs(ids);
        boolean shouldTriggerMediaDataExtraction = shouldTriggerMediaDataExtraction();
        Metadata[] cols = addSequenceNumberIfNeeded(columns);
        cols = addDateFieldsIfNeeded(cols, null);
        if (shouldTriggerMediaDataExtraction) {
            cols = addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(cols);
        }
        /*
         * pre-fetch object permissions if needed for result anyway
         */
        boolean addObjectPermissions = contains(cols, Metadata.OBJECT_PERMISSIONS_LITERAL);
        final Map<Integer, List<ObjectPermission>> knownObjectPermissions = addObjectPermissions ? objectPermissionLoader.load(objectIDs, context) : null;
        /*
         * get items, checking permissions as lazy as possible
         */
        final Map<Long, EffectiveInfostoreFolderPermission> knownFolderPermissions = new HashMap<>();
        InfostoreIterator iterator = InfostoreIterator.list(Autoboxing.I2i(objectIDs), cols, this, session.getContext());
        boolean addCreatedFrom = contains(columns, Metadata.CREATED_FROM_LITERAL);
        boolean addModifiedFrom = contains(columns, Metadata.MODIFIED_FROM_LITERAL);
        DocumentCustomizer customizer = new DocumentCustomizer() {

            @Override
            public DocumentMetadata handle(DocumentMetadata document) throws OXException {
                /*
                 * get & remember permissions for parent folder
                 */
                Long folderID = Long.valueOf(document.getFolderId());
                EffectiveInfostoreFolderPermission folderPermission = knownFolderPermissions.get(folderID);
                if (null == folderPermission) {
                    folderPermission = security.getFolderPermission(folderID.longValue(), context, user, session.getUserPermissionBits());
                    knownFolderPermissions.put(folderID, folderPermission);
                }
                /*
                 * check read permissions, trying the folder permissions first
                 */
                if (false == new EffectiveInfostorePermission(folderPermission.getPermission(), document, user, -1).canReadObject()) {
                    /*
                     * check object permissions, too
                     */
                    EffectiveInfostorePermission infostorePermission = null;
                    List<ObjectPermission> objectPermissions = null != knownObjectPermissions ? knownObjectPermissions.get(I(document.getId())) : objectPermissionLoader.load(document.getId(), context);
                    if (null != objectPermissions) {
                        ObjectPermission matchingPermission = EffectiveObjectPermissions.find(user, objectPermissions);
                        if (null != matchingPermission) {
                            EffectiveObjectPermission objectPermission = EffectiveObjectPermissions.convert(FolderObject.INFOSTORE, (int) document.getFolderId(), document.getId(), matchingPermission, session.getUserPermissionBits());
                            infostorePermission = new EffectiveInfostorePermission(folderPermission.getPermission(), objectPermission, document, user, -1);
                        }
                    }
                    if (null == infostorePermission || false == infostorePermission.canReadObject()) {
                        throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
                    }
                    /*
                     * in case of available object permissions, check requested folder
                     */
                    if (false == infostorePermission.canReadObjectInFolder()) {
                        Long requestedFolderID = idsToFolders.get(I(document.getId()));
                        if (null == requestedFolderID || getSharedFilesFolderID() != requestedFolderID.intValue()) {
                            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
                        }
                        /*
                         * adjust parent folder id to match requested identifier
                         */
                        document.setOriginalFolderId(document.getFolderId());
                        document.setFolderId(getSharedFilesFolderID());
                        /*
                         * Re-sharing of files is not allowed.
                         */
                        document.setShareable(false);
                    }
                } else {
                    /*
                     * adjust parent folder id to match requested identifier
                     */
                    Long requestedFolderID = idsToFolders.get(I(document.getId()));
                    if (getSharedFilesFolderID() == requestedFolderID.intValue()) {
                        document.setOriginalFolderId(document.getFolderId());
                        document.setFolderId(requestedFolderID.longValue());
                        /*
                         * Re-sharing of files is not allowed.
                         */
                        document.setShareable(false);
                    } else {
                        document.setShareable(folderPermission.canShareAllObjects() || folderPermission.canShareOwnObjects() && document.getCreatedBy() == user.getId());
                    }
                }
                return document;
            }
        };
        if (shouldTriggerMediaDataExtraction) {
            customizer = new TriggerMediaMetaDataExtractionDocumentCustomizer(this, null, session, customizer);
        }
        if (addCreatedFrom || addModifiedFrom) {
            customizer = new LoadEntityInfoCustomizer(entityInfoLoader, addCreatedFrom, addModifiedFrom, session, customizer);
        }
        iterator.setCustomizer(customizer);
        /*
         * wrap iterator into timed result, adding additional metadata as needed
         */
        TimedResult<DocumentMetadata> timedResult = new InfostoreTimedResult(iterator);
        if (addObjectPermissions) {
            timedResult = objectPermissionLoader.add(timedResult, context, knownObjectPermissions);
        }
        if (contains(cols, Metadata.LOCKED_UNTIL_LITERAL)) {
            timedResult = lockedUntilLoader.add(timedResult, context, objectIDs);
        }
        if (contains(cols, Metadata.NUMBER_OF_VERSIONS_LITERAL)) {
            timedResult = numberOfVersionsLoader.add(timedResult, context, objectIDs);
        }
        return timedResult;
    }

    @Override
    public Delta<DocumentMetadata> getDelta(final long folderId, final long updateSince, final Metadata[] columns, final boolean ignoreDeleted, final ServerSession session) throws OXException {
        return getDelta(folderId, updateSince, columns, null, 0, ignoreDeleted, session);
    }

    @Override
    public Delta<DocumentMetadata> getDelta(final long folderId, final long updateSince, Metadata[] columns, final Metadata sort, final int order, final boolean ignoreDeleted, final ServerSession session) throws OXException {
        final Context context = session.getContext();
        final User user = session.getUser();
        final Map<Integer, List<Lock>> locks = loadLocksInFolderAndExpireOldLocks(folderId, session);

        InfostoreIterator modIter = null;
        InfostoreIterator delIter = null;
        boolean shouldTriggerMediaDataExtraction = shouldTriggerMediaDataExtraction();
        boolean addCreatedFrom = contains(columns, Metadata.CREATED_FROM_LITERAL);
        boolean addModifiedFrom = contains(columns, Metadata.MODIFIED_FROM_LITERAL);
        boolean addObjectPermission = contains(columns, Metadata.OBJECT_PERMISSIONS_LITERAL);
        
        Metadata[] cols = addSequenceNumberIfNeeded(columns);
        cols = addDateFieldsIfNeeded(cols, sort);
        if (shouldTriggerMediaDataExtraction) {
            cols = addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(cols);
        }
        final int sharedFilesFolderID = getSharedFilesFolderID();
        if (folderId == sharedFilesFolderID) {
            DocumentCustomizer customizer = new DocumentCustomizer() {

                @Override
                public DocumentMetadata handle(DocumentMetadata document) {
                    document.setOriginalFolderId(document.getFolderId());
                    document.setFolderId(sharedFilesFolderID);
                    return document;
                }
            };
            if (shouldTriggerMediaDataExtraction) {
                customizer = new TriggerMediaMetaDataExtractionDocumentCustomizer(this, null, session, customizer);
            }
            if (addCreatedFrom || addModifiedFrom) {
                customizer = new LoadEntityInfoCustomizer(entityInfoLoader, addCreatedFrom, addModifiedFrom, session, customizer);
            }
            if (addObjectPermission) {
                customizer = new ObjectPermissionCustomizer(objectPermissionLoader, context, customizer);
            }
            modIter = InfostoreIterator.modifiedSharedDocumentsForUser(context, user, columns, sort, order, updateSince, this);
            modIter.setCustomizer(customizer);
            if (!ignoreDeleted) {
                delIter = InfostoreIterator.deletedSharedDocumentsForUser(context, user, columns, sort, order, updateSince, this);
                delIter.setCustomizer(customizer);
            }
        } else {
            boolean onlyOwn = false;
            final EffectiveInfostoreFolderPermission isperm = security.getFolderPermission(folderId, context, user, session.getUserPermissionBits());
            if (isperm.getReadPermission() == OCLPermission.NO_PERMISSIONS) {
                throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
            } else if (isperm.getReadPermission() == OCLPermission.READ_OWN_OBJECTS) {
                onlyOwn = true;
            }

            DocumentCustomizer customizer = null;
            if (shouldTriggerMediaDataExtraction) {
                QuotaFileStorage fileStorage = getFileStorage(isperm.getFolderOwner(), context.getContextId());
                customizer = new TriggerMediaMetaDataExtractionDocumentCustomizer(this, fileStorage, session);
            }
            if (addCreatedFrom || addModifiedFrom) {
                customizer = new LoadEntityInfoCustomizer(entityInfoLoader, addCreatedFrom, addModifiedFrom, session, customizer);
            }
            if (addObjectPermission) {
                customizer = new ObjectPermissionCustomizer(objectPermissionLoader, context, customizer);
            }

            if (onlyOwn) {
                modIter = InfostoreIterator.modifiedDocumentsByCreator(folderId, user.getId(), cols, sort, order, updateSince, this, context);
                modIter.setCustomizer(customizer);
                if (!ignoreDeleted) {
                    delIter = InfostoreIterator.deletedDocumentsByCreator(folderId, user.getId(), sort, order, updateSince, this, context);
                }
            } else {
                modIter = InfostoreIterator.modifiedDocuments(folderId, cols, sort, order, updateSince, this, context);
                modIter.setCustomizer(customizer);
                if (!ignoreDeleted) {
                    delIter = InfostoreIterator.deletedDocuments(folderId, sort, order, updateSince, this, context);
                }
            }
        }

        boolean addLocked = false;
        boolean addNumberOfVersions = false;
        for (final Metadata m : columns) {
            if (m == Metadata.LOCKED_UNTIL_LITERAL) {
                addLocked = true;
                break;
            }
            if (m == Metadata.NUMBER_OF_VERSIONS_LITERAL) {
                addNumberOfVersions = true;
                break;
            }
        }

        SearchIterator<DocumentMetadata> it = ignoreDeleted ? SearchIteratorAdapter.emptyIterator() : delIter;
        SearchIterator<DocumentMetadata> newIter = SearchIteratorAdapter.emptyIterator(); // New documents are covered by the query of modified files, therefore no special new search iterator is needed (see MWB-981)
        Delta<DocumentMetadata> delta = new FileDelta(newIter, modIter, it, System.currentTimeMillis());
        if (addLocked) {
            delta = lockedUntilLoader.add(delta, context, locks);
        }
        if (addNumberOfVersions) {
            delta = numberOfVersionsLoader.add(delta, context, (Map<Integer, Integer>) null);
        }
        return delta;
    }

    @Override
    public Map<Long, Long> getSequenceNumbers(List<Long> folderIds, boolean versionsOnly, final ServerSession session) throws OXException {
        if (0 == folderIds.size()) {
            return Collections.emptyMap();
        }

        final Map<Long, Long> sequenceNumbers = new HashMap<>(folderIds.size());
        try {
            User user = session.getUser();
            int contextId = session.getContextId();
            Context context = session.getContext();
            final Long userInfostoreId = new Long(FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID);
            if (folderIds.remove(userInfostoreId)) {
                performQuery(context, QUERIES.getSharedDocumentsSequenceNumbersQuery(versionsOnly, true, false, contextId, user.getId(), user.getGroups()), new ResultProcessor<Void>() {

                    @Override
                    public Void process(ResultSet rs) throws SQLException {
                        while (rs.next()) {
                            long newSequence = rs.getLong(1);
                            Long oldSequence = sequenceNumbers.get(userInfostoreId);
                            if (oldSequence == null || oldSequence.longValue() < newSequence) {
                                sequenceNumbers.put(userInfostoreId, Long.valueOf(newSequence));
                            }
                        }
                        return null;
                    }
                });
                performQuery(context, QUERIES.getSharedDocumentsSequenceNumbersQuery(versionsOnly, true, true, contextId, user.getId(), user.getGroups()), new ResultProcessor<Void>() {

                    @Override
                    public Void process(ResultSet rs) throws SQLException {
                        while (rs.next()) {
                            long newSequence = rs.getLong(1);
                            Long oldSequence = sequenceNumbers.get(userInfostoreId);
                            if (oldSequence == null || oldSequence.longValue() < newSequence) {
                                sequenceNumbers.put(userInfostoreId, Long.valueOf(newSequence));
                            }
                        }
                        return null;
                    }
                });
                performQuery(context, QUERIES.getSharedDocumentsSequenceNumbersQuery(versionsOnly, false, false, contextId, user.getId(), user.getGroups()), new ResultProcessor<Void>() {

                    @Override
                    public Void process(ResultSet rs) throws SQLException {
                        while (rs.next()) {
                            long newSequence = rs.getLong(1);
                            Long oldSequence = sequenceNumbers.get(userInfostoreId);
                            if (oldSequence == null || oldSequence.longValue() < newSequence) {
                                sequenceNumbers.put(userInfostoreId, Long.valueOf(newSequence));
                            }
                        }
                        return null;
                    }
                });
            }
            if (false == folderIds.isEmpty()) {
                performQuery(context, QUERIES.getFolderSequenceNumbersQuery(folderIds, versionsOnly, true, contextId), new ResultProcessor<Void>() {

                    @Override
                    public Void process(ResultSet rs) throws SQLException {
                        while (rs.next()) {
                            sequenceNumbers.put(Long.valueOf(rs.getLong(1)), Long.valueOf(rs.getLong(2)));
                        }
                        return null;
                    }
                });
                performQuery(context, QUERIES.getFolderSequenceNumbersQuery(folderIds, versionsOnly, false, contextId), new ResultProcessor<Void>() {

                    @Override
                    public Void process(ResultSet rs) throws SQLException {
                        while (rs.next()) {
                            Long folderID = Long.valueOf(rs.getLong(1));
                            long newSequence = rs.getLong(2);
                            Long oldSequence = sequenceNumbers.get(folderID);
                            if (oldSequence == null || oldSequence.longValue() < newSequence) {
                                sequenceNumbers.put(folderID, Long.valueOf(newSequence));
                            }
                        }
                        return null;
                    }
                });
            }
        } catch (SQLException e) {
            throw InfostoreExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        }
        return sequenceNumbers;
    }

    private Map<Integer, List<Lock>> loadLocksInFolderAndExpireOldLocks(final long folderId, final ServerSession session) throws OXException {
        final Map<Integer, List<Lock>> locks = new HashMap<>();
        final InfostoreIterator documents = InfostoreIterator.documents(folderId, new Metadata[] { Metadata.ID_LITERAL }, null, -1, -1, -1, this, session.getContext());
        try {
            while (documents.hasNext()) {
                final DocumentMetadata document = documents.next();
                lockManager.findLocks(document.getId(), session);
            }
        } finally {
            documents.close();
        }
        return locks;
    }

    @Override
    public int countDocuments(final long folderId, final ServerSession session) throws OXException {
        if (folderId == getSharedFilesFolderID()) {
            return SearchIterators.asList(InfostoreIterator.sharedDocumentsForUser(session.getContext(), session.getUser(), ObjectPermission.READ, new Metadata[] { Metadata.ID_LITERAL }, null, 0, -1, -1, this)).size();
        }

        boolean onlyOwn = false;
        User user = session.getUser();
        final EffectiveInfostoreFolderPermission isperm = security.getFolderPermission(folderId, session.getContext(), user, session.getUserPermissionBits());
        if (!(isperm.canReadAllObjects()) && !(isperm.canReadOwnObjects())) {
            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
        } else if (!isperm.canReadAllObjects() && isperm.canReadOwnObjects()) {
            onlyOwn = true;
        }
        return db.countDocuments(folderId, onlyOwn, session.getContext(), user);
    }

    @Override
    public long getTotalSize(long folderId, ServerSession session) throws OXException {
        return db.getTotalSize(session.getContext(), folderId);
    }

    @Override
    public boolean hasFolderForeignObjects(final long folderId, final ServerSession session) throws OXException {
        if (folderId == getSharedFilesFolderID()) {
            return true;
        }

        return db.hasFolderForeignObjects(folderId, session.getContext(), session.getUser());
    }

    @Override
    public boolean isFolderEmpty(final long folderId, final Context ctx) throws OXException {
        if (folderId == FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID) {
            return true; // We can't determine this without a user id...
        }

        return db.isFolderEmpty(folderId, ctx);
    }

    @Override
    public void removeUser(final int userId, final Context ctx, Integer destUser, final ServerSession session) throws OXException {
        db.removeUser(userId, ctx, destUser, session, lockManager);
    }

    @Override
    public SearchIterator<DocumentMetadata> search(ServerSession session, String query, int folderId, Metadata[] cols, Metadata sortedBy, int dir, int start, int end) throws OXException {
        return search(session, query, folderId, false, cols, sortedBy, dir, start, end);
    }

    @Override
    public SearchIterator<DocumentMetadata> search(ServerSession session, String query, int folderId, boolean includeSubfolders, Metadata[] cols, Metadata sortedBy, int dir, int start, int end) throws OXException {
        /*
         * get folders for search and corresponding permissions
         */
        List<Integer> all = new ArrayList<>();
        List<Integer> own = new ArrayList<>();
        Map<Integer, EffectiveInfostoreFolderPermission> permissionsByFolderID;
        if (NOT_SET == folderId || NO_FOLDER == folderId) {
            permissionsByFolderID = Tools.gatherVisibleFolders(session, security, db, null, all, own);
        } else if (includeSubfolders) {
            permissionsByFolderID = Tools.gatherVisibleFolders(session, security, db, folderId, all, own);
        } else {
            permissionsByFolderID = Tools.gatherVisibleFolders(session, security, db, new int[] { folderId }, all, own);
        }
        if (all.isEmpty() && own.isEmpty()) {
            return SearchIteratorAdapter.emptyIterator();
        }
        /*
         * perform search & enhance results with additional metadata as needed
         */
        boolean shouldTriggerMediaDataExtraction = shouldTriggerMediaDataExtraction();
        Metadata[] fields = Tools.getFieldsToQuery(cols, Metadata.ID_LITERAL, Metadata.FOLDER_ID_LITERAL);
        fields = addDateFieldsIfNeeded(fields, sortedBy);
        if (shouldTriggerMediaDataExtraction) {
            fields = addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(fields);
        }
        SearchIterator<DocumentMetadata> searchIterator = searchEngine.search(session, query, all, own, fields, sortedBy, dir, start, end);
        return postProcessSearch(session, searchIterator, fields, permissionsByFolderID, shouldTriggerMediaDataExtraction);
    }

    @Override
    public SearchIterator<DocumentMetadata> search(ServerSession session, SearchTerm<?> searchTerm, int[] folderIds, Metadata[] cols, Metadata sortedBy, int dir, int start, int end) throws OXException {
        /*
         * get folders for search and corresponding permissions
         */
        List<Integer> all = new ArrayList<>();
        List<Integer> own = new ArrayList<>();
        int[] requestedFolderIDs = null == folderIds || 0 == folderIds.length ? null : folderIds;
        Map<Integer, EffectiveInfostoreFolderPermission> permissionsByFolderID = Tools.gatherVisibleFolders(session, security, db, requestedFolderIDs, all, own);
        if (all.isEmpty() && own.isEmpty()) {
            return SearchIteratorAdapter.emptyIterator();
        }
        /*
         * perform search & enhance results with additional metadata as needed
         */
        boolean shouldTriggerMediaDataExtraction = shouldTriggerMediaDataExtraction();
        Metadata[] fields = Tools.getFieldsToQuery(cols, Metadata.ID_LITERAL, Metadata.FOLDER_ID_LITERAL);
        fields = addDateFieldsIfNeeded(fields, sortedBy);
        if (shouldTriggerMediaDataExtraction) {
            fields = addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(fields);
        }
        SearchIterator<DocumentMetadata> searchIterator = searchEngine.search(session, searchTerm, all, own, fields, sortedBy, dir, start, end);
        return postProcessSearch(session, searchIterator, fields, permissionsByFolderID, shouldTriggerMediaDataExtraction);
    }

    @Override
    public SearchIterator<DocumentMetadata> search(ServerSession session, SearchTerm<?> searchTerm, int folderId, boolean includeSubfolders, Metadata[] cols, Metadata sortedBy, int dir, int start, int end) throws OXException {
        /*
         * get folders for search and corresponding permissions
         */
        List<Integer> all = new ArrayList<>();
        List<Integer> own = new ArrayList<>();
        Map<Integer, EffectiveInfostoreFolderPermission> permissionsByFolderID;
        if (includeSubfolders) {
            permissionsByFolderID = Tools.gatherVisibleFolders(session, security, db, folderId, all, own);
        } else {
            permissionsByFolderID = Tools.gatherVisibleFolders(session, security, db, new int[] { folderId }, all, own);
        }
        if (all.isEmpty() && own.isEmpty()) {
            return SearchIteratorAdapter.emptyIterator();
        }
        /*
         * perform search & enhance results with additional metadata as needed
         */
        boolean shouldTriggerMediaDataExtraction = shouldTriggerMediaDataExtraction();
        Metadata[] fields = Tools.getFieldsToQuery(cols, Metadata.ID_LITERAL, Metadata.FOLDER_ID_LITERAL);
        fields = addDateFieldsIfNeeded(fields, sortedBy);
        if (shouldTriggerMediaDataExtraction) {
            fields = addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(fields);
        }
        SearchIterator<DocumentMetadata> searchIterator = searchEngine.search(session, searchTerm, all, own, fields, sortedBy, dir, start, end);
        return postProcessSearch(session, searchIterator, fields, permissionsByFolderID, shouldTriggerMediaDataExtraction);
    }

    /**
     * Adds additional metadata based on the requested columns to a search iterator result.
     *
     * @param session The session
     * @param searchIterator The search iterator as fetched from the search engine
     * @param fields The requested fields
     * @param permissionsByFolderID A map holding the effective permissions of all used folders during the search, or <code>null</code> to
     *            assume all documents being readable & shareable by the current user
     * @param shouldTriggerMediaDataExtraction <code>true</code> to trigger extraction of media metadata; otherwise <code>false</code>
     * @return The enhanced search results
     */
    private SearchIterator<DocumentMetadata> postProcessSearch(ServerSession session, SearchIterator<DocumentMetadata> searchIterator, Metadata[] fields, final Map<Integer, EffectiveInfostoreFolderPermission> permissionsByFolderID, boolean shouldTriggerMediaDataExtraction) throws OXException {
        /*
         * check requested metadata
         */
        int sharedFilesFolderID = FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID;
        boolean containsSharedFilesResults = null != permissionsByFolderID && permissionsByFolderID.containsKey(I(sharedFilesFolderID));
        Set<Metadata> set = Metadata.contains(fields, Metadata.LOCKED_UNTIL_LITERAL, Metadata.NUMBER_OF_VERSIONS_LITERAL, Metadata.OBJECT_PERMISSIONS_LITERAL, Metadata.SHAREABLE_LITERAL, Metadata.CREATED_FROM_LITERAL, Metadata.MODIFIED_FROM_LITERAL);
        boolean addLocked = set.contains(Metadata.LOCKED_UNTIL_LITERAL);
        boolean addNumberOfVersions = set.contains(Metadata.NUMBER_OF_VERSIONS_LITERAL);
        boolean addObjectPermissions = set.contains(Metadata.OBJECT_PERMISSIONS_LITERAL);
        boolean addShareable = set.contains(Metadata.SHAREABLE_LITERAL);
        boolean addCreatedFrom = set.contains(Metadata.CREATED_FROM_LITERAL);
        boolean addModifiedFrom = set.contains(Metadata.MODIFIED_FROM_LITERAL);
        if (false == addLocked && false == addNumberOfVersions && false == addObjectPermissions && false == addShareable && false == containsSharedFilesResults) {
            /*
             * stick to plain search iterator result if no further metadata is needed
             */
            return shouldTriggerMediaDataExtraction ? new TriggerMediaMetaDataExtractionSearchIterator(searchIterator, this, session) : searchIterator;
        }
        /*
         * prepare customizable search iterator to add additional metadata as requested
         */
        List<DocumentMetadata> documents;
        try {
            documents = SearchIterators.asList(searchIterator);
        } finally {
            SearchIterators.close(searchIterator);
        }
        if (null == documents || 0 == documents.size()) {
            return SearchIteratorAdapter.emptyIterator();
        }
        List<Integer> objectIDs = Tools.getIDs(documents);
        if (shouldTriggerMediaDataExtraction) {
            /*
             * trigger media meta-data extraction
             */
            new TriggerMediaMetaDataExtractionByCollection(documents, this, session).considerDocuments();
        }
        /*
         * add object permissions if requested or needed to evaluate "shareable" flag
         */
        if (addObjectPermissions || addShareable || containsSharedFilesResults) {
            documents = objectPermissionLoader.add(documents, session.getContext(), objectIDs);
        }
        if (addLocked) {
            documents = lockedUntilLoader.add(documents, session.getContext(), objectIDs);
        }
        if (addNumberOfVersions) {
            documents = numberOfVersionsLoader.add(documents, session.getContext(), objectIDs);
        }
        if (addCreatedFrom) {
            CreatedFromLoader createdFromLoader = new CreatedFromLoader(documents.stream().collect(Collectors.toMap(DocumentMetadata::getId, document -> document)), entityInfoLoader, session);
            documents = createdFromLoader.add(documents, session.getContext(), objectIDs);
        }
        if (addModifiedFrom) {
            ModifiedFromLoader modifiedFromLoader = new ModifiedFromLoader(documents.stream().collect(Collectors.toMap(DocumentMetadata::getId, document -> document)), entityInfoLoader, session);
            documents = modifiedFromLoader.add(documents, session.getContext(), objectIDs);
        }
        if (addShareable || containsSharedFilesResults) {
            boolean hasSharedFolderAccess = session.getUserConfiguration().hasFullSharedFolderAccess();
            for (DocumentMetadata document : documents) {
                int physicalFolderID = (int) document.getFolderId();
                if (null == permissionsByFolderID) {
                    /*
                     * assume document shareable & readable at physical location
                     */
                    document.setShareable(hasSharedFolderAccess);
                    continue;
                }
                EffectiveInfostoreFolderPermission folderPermission = permissionsByFolderID.get(I(physicalFolderID));
                if (null != folderPermission && (folderPermission.canReadAllObjects() || folderPermission.canReadOwnObjects() && document.getCreatedBy() == session.getUserId())) {
                    /*
                     * document is readable at physical location
                     */
                    document.setShareable(folderPermission.canShareAllObjects() || folderPermission.canShareOwnObjects() && document.getCreatedBy() == session.getUserId());
                } else {
                    /*
                     * set 'shareable' flag and parent folder based on object permissions
                     */
                    List<ObjectPermission> objectPermissions = document.getObjectPermissions();
                    if (null != objectPermissions) {
                        ObjectPermission matchingPermission = EffectiveObjectPermissions.find(session.getUser(), objectPermissions);
                        if (null != matchingPermission && matchingPermission.canRead()) {
                            document.setOriginalFolderId(document.getFolderId());
                            document.setFolderId(sharedFilesFolderID);
                            /*
                             * Re-sharing of files is not allowed.
                             */
                            document.setShareable(false);
                        } else {
                            throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
                        }
                    } else {
                        throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
                    }
                }
            }
        }
        return new SearchIteratorDelegator<>(documents);
    }

    private int getId(final Context context, final Connection writeCon) throws SQLException {
        final boolean autoCommit = writeCon.getAutoCommit();
        if (autoCommit) {
            writeCon.setAutoCommit(false);
        }
        try {
            return IDGenerator.getId(context, Types.INFOSTORE, writeCon);
        } finally {
            if (autoCommit) {
                writeCon.commit();
                writeCon.setAutoCommit(true);
            }
        }
    }

    /**
     * Adds capture date and last-modified date to metadata array in case field is {@link Metadata#MEDIA_DATE} requested.
     *
     * @param columns The metadata fields to enhance
     * @param optSort The sort field or <code>null</code>
     * @return The possibly enhanced metadata fields
     */
    private Metadata[] addDateFieldsIfNeeded(final Metadata[] columns, Metadata optSort) {
        boolean mediaDateRequested = (Metadata.MEDIA_DATE_LITERAL == optSort);
        for (int j = columns.length; !mediaDateRequested && j-- > 0;) {
            if (columns[j] == Metadata.MEDIA_DATE_LITERAL) {
                mediaDateRequested = true;
            }
        }

        if (!mediaDateRequested) {
            return columns;
        }

        // When sorting by media sort date (either capture date or fall-back to last-modified) ensure both fields are queried
        return Metadata.addIfAbsent(columns, Metadata.LAST_MODIFIED_LITERAL, Metadata.CAPTURE_DATE_LITERAL);
    }

    private Metadata[] addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(final Metadata[] columns) {
        return Metadata.addIfAbsent(columns, Metadata.ID_LITERAL, Metadata.FOLDER_ID_LITERAL, Metadata.VERSION_LITERAL,
            Metadata.MEDIA_STATUS_LITERAL, Metadata.FILESTORE_LOCATION_LITERAL, Metadata.FILE_MIMETYPE_LITERAL, Metadata.FILENAME_LITERAL,
            Metadata.SEQUENCE_NUMBER_LITERAL);
    }

    private Metadata[] addSequenceNumberIfNeeded(final Metadata[] columns) {
        return Metadata.addIfAbsent(columns, Metadata.SEQUENCE_NUMBER_LITERAL);
    }

    private Metadata[] addFilenameIfNeeded(final Metadata[] columns) {
        return Metadata.addIfAbsent(columns, Metadata.FILENAME_LITERAL);
    }

    public InfostoreSecurity getSecurity() {
        return security;
    }

    private static enum ServiceMethod {
        COMMIT, FINISH, ROLLBACK, SET_REQUEST_TRANSACTIONAL, START_TRANSACTION, SET_PROVIDER;

        public void call(final Object o, final Object... args) {
            if (!(o instanceof DBService)) {
                return;
            }
            final DBService service = (DBService) o;
            switch (this) {
                default:
                    return;
                case SET_REQUEST_TRANSACTIONAL:
                    service.setRequestTransactional(((Boolean) args[0]).booleanValue());
                    break;
                case SET_PROVIDER:
                    service.setProvider((DBProvider) args[0]);
                    break;
            }
        }

        public void callUnsafe(final Object o, final Object... args) throws OXException {
            if (!(o instanceof DBService)) {
                return;
            }
            final DBService service = (DBService) o;
            switch (this) {
                default:
                    call(o, args);
                    break;
                case COMMIT:
                    service.commit();
                    break;
                case FINISH:
                    service.finish();
                    break;
                case ROLLBACK:
                    service.rollback();
                    break;
                case START_TRANSACTION:
                    service.startTransaction();
                    break;
            }
        }

    }

    @Override
    public void commit() throws OXException {
        db.commit();
        ServiceMethod.COMMIT.callUnsafe(security);
        lockManager.commit();
        final List<FileRemoveInfo> filesToRemove = fileIdRemoveList.get();
        if (null != filesToRemove) {
            int size = filesToRemove.size();
            if (size > 0) {
                if (1 == size) {
                    final FileRemoveInfo removeInfo = filesToRemove.get(0);
                    final QuotaFileStorage fileStorage = getFileStorage(removeInfo.folderAdmin, removeInfo.contextId);
                    Task<Void> task = new AbstractTask<Void>() {

                        @Override
                        public Void call() {
                            try {
                                fileStorage.deleteFile(removeInfo.fileId);
                            } catch (Exception e) {
                                LoggerHolder.LOG.error("Failed to delete file {} from storage of owner {} in context {}", removeInfo.fileId, I(removeInfo.folderAdmin), I(removeInfo.contextId), e);
                            }
                            return null;
                        }
                    };
                    ThreadPools.submitElseExecute(task);
                } else {
                    // Group by owner/context pair
                    Map<UserAndContext, List<String>> removalsPerStorage = new LinkedHashMap<>();
                    for (FileRemoveInfo removeInfo : filesToRemove) {
                        UserAndContext key = UserAndContext.newInstance(removeInfo.folderAdmin, removeInfo.contextId);
                        List<String> removals = removalsPerStorage.get(key);
                        if (null == removals) {
                            removals = new ArrayList<>();
                            removalsPerStorage.put(key, removals);
                        }
                        removals.add(removeInfo.fileId);
                    }
                    for (Map.Entry<UserAndContext, List<String>> entry : removalsPerStorage.entrySet()) {
                        final UserAndContext key = entry.getKey();
                        final List<String> locations = entry.getValue();
                        final QuotaFileStorage fileStorage = getFileStorage(key.getUserId(), key.getContextId());
                        Task<Void> task = new AbstractTask<Void>() {

                            @Override
                            public Void call() {
                                try {
                                    fileStorage.deleteFiles(locations.toArray(new String[locations.size()]));
                                } catch (Exception e) {
                                    LoggerHolder.LOG.error("Failed to delete files {} from storage of owner {} in context {}", locations, I(key.getUserId()), I(key.getContextId()), e);
                                }
                                return null;
                            }
                        };
                        ThreadPools.submitElseExecute(task);
                    }
                }
            }
        }
        /*
         * schedule guest cleanup tasks as needed
         */
        TIntObjectMap<TIntSet> guestsToCleanup = guestCleanupList.get();
        if (null != guestsToCleanup && !guestsToCleanup.isEmpty()) {
            TIntObjectIterator<TIntSet> iter = guestsToCleanup.iterator();
            for (int i = guestsToCleanup.size(); i-- > 0;) {
                iter.advance();
                final int contextID = iter.key();
                final int[] guestIDs = filterGuests(contextID, iter.value());
                if (null != guestIDs) {
                    final ShareService shareService = ServerServiceRegistry.getServize(ShareService.class);
                    if (null != shareService) {
                        Task<Void> task = new AbstractTask<Void>() {

                            @Override
                            public Void call() throws Exception {
                                try {
                                    shareService.scheduleGuestCleanup(contextID, guestIDs);
                                } catch (Exception e) {
                                    LoggerHolder.LOG.error("Failed to clean-up guests {} in context {}", Arrays.toString(guestIDs), I(contextID), e);
                                }
                                return null;
                            }
                        };
                        ThreadPools.submitElseExecute(task);
                    }
                }
            }
        }
        /*
         * remaining pending invocations
         */
        List<Runnable> tasks = pendingInvocations.get();
        if (null != tasks && !tasks.isEmpty()) {
            for (Runnable task : tasks) {
                try {
                    task.run();
                } catch (Exception e) {
                    LoggerHolder.LOG.error("Failed to perform task {}", task.getClass().getName(), e);
                }
            }
        }
        super.commit();
    }

    private int[] filterGuests(int contextID, TIntSet entityIDs) throws OXException {
        if (null == entityIDs) {
            return null;
        }
        UserService userService = ServerServiceRegistry.getServize(UserService.class);
        TIntSet guestIDs = new TIntHashSet(entityIDs.size());
        TIntIterator iter = entityIDs.iterator();
        for (int k = entityIDs.size(); k-- > 0;) {
            int id = iter.next();
            try {
                if (userService.isGuest(id, contextID)) {
                    guestIDs.add(id);
                }
            } catch (OXException e) {
                if (UserExceptionCode.USER_NOT_FOUND.equals(e)) {
                    continue;
                }
                throw e;
            }
        }
        return guestIDs.toArray();
    }

    @Override
    public void finish() throws OXException {
        fileIdRemoveList.set(null);
        guestCleanupList.set(null);
        pendingInvocations.set(null);
        db.finish();
        ServiceMethod.FINISH.callUnsafe(security);
        lockManager.finish();
        super.finish();
    }

    @Override
    public void rollback() throws OXException {
        db.rollback();
        ServiceMethod.ROLLBACK.callUnsafe(security);
        lockManager.rollback();
        super.rollback();
    }

    @Override
    public void setRequestTransactional(final boolean transactional) {
        db.setRequestTransactional(transactional);
        ServiceMethod.SET_REQUEST_TRANSACTIONAL.call(security, Boolean.valueOf(transactional));
        lockManager.setRequestTransactional(transactional);
        super.setRequestTransactional(transactional);
    }

    @Override
    public void setTransactional(final boolean transactional) {
        lockManager.setTransactional(transactional);
    }

    @Override
    public void startTransaction() throws OXException {
        fileIdRemoveList.set(new LinkedList<InfostoreFacadeImpl.FileRemoveInfo>());
        guestCleanupList.set(new TIntObjectHashMap<TIntSet>());
        pendingInvocations.set(new LinkedList<Runnable>());
        db.startTransaction();
        ServiceMethod.START_TRANSACTION.callUnsafe(security);
        lockManager.startTransaction();
        super.startTransaction();
    }

    @Override
    public void setProvider(final DBProvider provider) {
        super.setProvider(provider);
        db.setProvider(this);
        ServiceMethod.SET_PROVIDER.call(security, this);
        ServiceMethod.SET_PROVIDER.call(lockManager, this);
    }

    private static interface ResultProcessor<T> {

        public T process(ResultSet rs) throws SQLException;
    }

    @Override
    public void setSessionHolder(final SessionHolder sessionHolder) {
        expiredLocksListener.setSessionHolder(sessionHolder);
    }

    /**
     * Processes the list of supplied ID tuples to ensure that each entry has an assigned folder ID.
     *
     * @param context The context
     * @param tuples The ID tuples to process
     * @return The ID tuples, with each entry holding its full file- and folder-ID information
     * @throws OXException
     */
    private List<IDTuple> ensureFolderIDs(Context context, List<IDTuple> tuples) throws OXException {
        if (null == tuples || 0 == tuples.size()) {
            return tuples;
        }
        Map<Integer, IDTuple> incompleteTuples = new HashMap<>();
        for (IDTuple tuple : tuples) {
            if (null == tuple.getFolder()) {
                try {
                    incompleteTuples.put(Integer.valueOf(tuple.getId()), tuple);
                } catch (NumberFormatException e) {
                    throw InfostoreExceptionCodes.NOT_EXIST.create(e);
                }
            }
        }
        if (0 < incompleteTuples.size()) {
            InfostoreIterator iterator = null;
            try {
                iterator = InfostoreIterator.list(Autoboxing.I2i(incompleteTuples.keySet()), new Metadata[] { Metadata.ID_LITERAL, Metadata.FOLDER_ID_LITERAL }, this, context);
                while (iterator.hasNext()) {
                    DocumentMetadata document = iterator.next();
                    IDTuple tuple = incompleteTuples.get(Integer.valueOf(document.getId()));
                    if (null != tuple) {
                        tuple.setFolder(String.valueOf(document.getFolderId()));
                    }
                }
            } finally {
                SearchIterators.close(iterator);
            }
        }
        return tuples;
    }

    /**
     * Gets an chain of validation checks to use before saving documents.
     *
     * @return The validation chain
     */
    private ValidationChain getValidationChain() {
        return new ValidationChain(new InvalidCharactersValidator(), new FilenamesMayNotContainSlashesValidator(), new ObjectPermissionValidator(this), new PermissionSizeValidator());
    }

    private TimedResult<DocumentMetadata> getDocuments(Context context, final User user, ServerSession optSession, UserPermissionBits permissionBits, final long folderId, Metadata[] columns, Metadata sort, int order, int start, int end) throws OXException {
        /*
         * check requested metadata
         */
        boolean shouldTriggerMediaDataExtraction = shouldTriggerMediaDataExtraction();
        Set<Metadata> set = Metadata.contains(columns, Metadata.LOCKED_UNTIL_LITERAL, Metadata.NUMBER_OF_VERSIONS_LITERAL, Metadata.OBJECT_PERMISSIONS_LITERAL, Metadata.SHAREABLE_LITERAL, Metadata.CREATED_FROM_LITERAL, Metadata.MODIFIED_FROM_LITERAL);
        boolean addLocked = set.contains(Metadata.LOCKED_UNTIL_LITERAL);
        boolean addNumberOfVersions = set.contains(Metadata.NUMBER_OF_VERSIONS_LITERAL);
        boolean addObjectPermissions = set.contains(Metadata.OBJECT_PERMISSIONS_LITERAL);
        boolean addShareable = set.contains(Metadata.SHAREABLE_LITERAL);
        boolean addCreatedFrom = set.contains(Metadata.CREATED_FROM_LITERAL);
        boolean addModifiedFrom = set.contains(Metadata.MODIFIED_FROM_LITERAL);
        Metadata[] cols = addDateFieldsIfNeeded(addSequenceNumberIfNeeded(columns), sort);
        if (shouldTriggerMediaDataExtraction) {
            cols = addFieldsForTriggeringMediaMetaDataExtractionIfNeeded(cols);
        }
        if (addCreatedFrom) {
            cols = Metadata.addIfAbsent(cols, Metadata.CREATED_BY_LITERAL);
        }
        if (addModifiedFrom) {
            cols = Metadata.addIfAbsent(cols, Metadata.MODIFIED_BY_LITERAL);
        }
        /*
         * get appropriate infostore iterator
         */
        final long sharedFilesFolderID = getSharedFilesFolderID();
        final EffectiveInfostoreFolderPermission folderPermission;
        InfostoreIterator iterator = null;
        try {
            if (sharedFilesFolderID == folderId) {
                /*
                 * load readable documents from virtual shared files folder
                 */
                folderPermission = null;
                iterator = InfostoreIterator.sharedDocumentsForUser(context, user, ObjectPermission.READ, cols, sort, order, start, end, db);
                DocumentCustomizer customizer = new DocumentCustomizer() {

                    @Override
                    public DocumentMetadata handle(DocumentMetadata document) {
                        document.setOriginalFolderId(document.getFolderId());
                        document.setFolderId(sharedFilesFolderID);
                        return document;
                    }
                };
                if (shouldTriggerMediaDataExtraction) {
                    ServerSession session = getSession(optSession);
                    if (session != null) {
                        customizer = new TriggerMediaMetaDataExtractionDocumentCustomizer(this, null, session, customizer);
                    }
                }
                iterator.setCustomizer(customizer);
            } else {
                if (null == permissionBits) {
                    throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
                }
                /*
                 * load all / own objects from physical folder
                 */
                folderPermission = security.getFolderPermission(folderId, context, user, permissionBits);
                if (folderPermission.canReadAllObjects()) {
                    iterator = InfostoreIterator.documents(folderId, cols, sort, order, start, end, this, context);
                } else if (folderPermission.canReadOwnObjects()) {
                    iterator = InfostoreIterator.documentsByCreator(folderId, user.getId(), cols, sort, order, start, end, this, context);
                } else {
                    throw InfostoreExceptionCodes.NO_READ_PERMISSION.create();
                }
                if (shouldTriggerMediaDataExtraction) {
                    ServerSession session = getSession(optSession);
                    if (session != null) {
                        QuotaFileStorage fileStorage = getFileStorage(folderPermission.getFolderOwner(), context.getContextId());
                        iterator.setCustomizer(new TriggerMediaMetaDataExtractionDocumentCustomizer(this, fileStorage, session));
                    }
                }
            }
            /*
             * stick to plain infostore timed result if no further metadata is needed
             */
            if (false == addLocked && false == addNumberOfVersions && false == addObjectPermissions && false == addShareable && false == addCreatedFrom && false == addModifiedFrom) {
                InfostoreTimedResult timedResult = new InfostoreTimedResult(iterator);
                iterator = null; // Avoid premature closing
                return timedResult;
            }
            /*
             * prepare customizable timed result to add additional metadata as requested
             */
            final List<DocumentMetadata> documents = SearchIterators.asList(iterator);
            if (0 == documents.size()) {
                return com.openexchange.groupware.results.Results.emptyTimedResult();
            }
            long maxSequenceNumber = 0;
            List<Integer> objectIDs = new ArrayList<>(documents.size());
            for (DocumentMetadata document : documents) {
                maxSequenceNumber = Math.max(maxSequenceNumber, document.getSequenceNumber());
                objectIDs.add(Integer.valueOf(document.getId()));
            }
            final long sequenceNumber = maxSequenceNumber;
            TimedResult<DocumentMetadata> timedResult = new TimedResult<DocumentMetadata>() {

                @Override
                public SearchIterator<DocumentMetadata> results() throws OXException {
                    return new SearchIteratorAdapter<>(documents.iterator());
                }

                @Override
                public long sequenceNumber() throws OXException {
                    return sequenceNumber;
                }
            };
            /*
             * add object permissions if requested or needed to evaluate "shareable" flag
             */
            if (addObjectPermissions) {
                timedResult = objectPermissionLoader.add(timedResult, context, objectIDs);
            }
            if (addLocked) {
                timedResult = lockedUntilLoader.add(timedResult, context, objectIDs);
            }
            if (addNumberOfVersions) {
                timedResult = numberOfVersionsLoader.add(timedResult, context, objectIDs);
            }
            {
                Map<Integer, DocumentMetadata> id2document = null;
                if (addCreatedFrom && null != optSession) {
                    id2document = new LinkedHashMap<>(documents.size());
                    for (DocumentMetadata document : documents) {
                        id2document.putIfAbsent(I(document.getId()), document);
                    }
                    CreatedFromLoader createdFromLoader = new CreatedFromLoader(id2document, entityInfoLoader, optSession);
                    timedResult = createdFromLoader.add(timedResult, context, objectIDs);
                }
                if (addModifiedFrom && null != optSession) {
                    if (id2document == null) {
                        id2document = new LinkedHashMap<>(documents.size());
                        for (DocumentMetadata document : documents) {
                            id2document.putIfAbsent(I(document.getId()), document);
                        }
                    }
                    ModifiedFromLoader modifiedFromLoader = new ModifiedFromLoader(id2document, entityInfoLoader, optSession);
                    timedResult = modifiedFromLoader.add(timedResult, context, objectIDs);
                }
            }
            if (addShareable) {
                final boolean hasSharedFolderAccess = permissionBits.hasFullSharedFolderAccess();
                timedResult = new CustomizableTimedResult<>(timedResult, new Customizer<DocumentMetadata>() {

                    @Override
                    public DocumentMetadata customize(DocumentMetadata document) throws OXException {
                        if (false == hasSharedFolderAccess || sharedFilesFolderID == folderId) {
                            /*
                             * no permissions to share or re-share
                             */
                            document.setShareable(false);
                        } else {
                            /*
                             * set "shareable" flag based on folder permissions
                             */
                            document.setShareable(null != folderPermission && (folderPermission.canWriteAllObjects() || folderPermission.canWriteOwnObjects() && document.getCreatedBy() == user.getId()));
                        }
                        return document;
                    }
                });
            }
            if (null != optSession && (addCreatedFrom || addModifiedFrom)) {
                EntityInfoLoader loader = new EntityInfoLoader();
                timedResult = new CustomizableTimedResult<>(timedResult, new Customizer<DocumentMetadata>() {

                    @Override
                    public DocumentMetadata customize(DocumentMetadata document) throws OXException {
                        if (addCreatedFrom && 0 < document.getCreatedBy()) {
                            document.setCreatedFrom(loader.load(document.getCreatedBy(), optSession));
                        }
                        if (addModifiedFrom && 0 < document.getModifiedBy()) {
                            document.setModifiedFrom(loader.load(document.getModifiedBy(), optSession));
                        }
                        return document;
                    }
                });
            }
            return timedResult;
        } finally {
            SearchIterators.close(iterator);
        }
    }

    /**
     * Gets the identifier of the folder holding single documents shared to the session's user based on extended object permissions.
     *
     * @return The identifier of the shared documents folder
     */
    protected int getSharedFilesFolderID() {
        return FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID;
    }

    private ServerSession getSession(ServerSession optSession) throws OXException {
        if (null != optSession) {
            return optSession;
        }

        Optional<Session> optionalSession = Sessions.getSessionForCurrentThread();
        if (!optionalSession.isPresent()) {
            return null;
        }

        return ServerSessionAdapter.valueOf(optionalSession.get());
    }

}
