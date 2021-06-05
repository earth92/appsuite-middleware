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

package com.openexchange.tools.oxfolder;

import static com.openexchange.chronos.service.CalendarParameters.PARAMETER_CONNECTION;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import static com.openexchange.tools.arrays.Arrays.contains;
import java.sql.Connection;
import java.sql.DataTruncation;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.collect.ImmutableMap;
import com.openexchange.ajax.fields.FolderChildFields;
import com.openexchange.ajax.fields.FolderFields;
import com.openexchange.cache.impl.FolderCacheManager;
import com.openexchange.cache.impl.FolderQueryCacheManager;
import com.openexchange.chronos.SchedulingControl;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.service.CalendarService;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.lean.DefaultProperty;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.config.lean.Property;
import com.openexchange.contact.ContactService;
import com.openexchange.database.Databases;
import com.openexchange.database.IncorrectStringSQLException;
import com.openexchange.database.provider.DBPoolProvider;
import com.openexchange.database.provider.StaticDBPoolProvider;
import com.openexchange.event.impl.EventClient;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionConstants;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FolderPath;
import com.openexchange.folder.FolderDeleteListenerService;
import com.openexchange.folder.internal.FolderDeleteListenerRegistry;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.cache.CacheFolderStorage;
import com.openexchange.groupware.Types;
import com.openexchange.groupware.container.DataObject;
import com.openexchange.groupware.container.FolderChildObject;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.container.FolderPathObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.impl.IDGenerator;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.infostore.InfostoreFacade;
import com.openexchange.groupware.infostore.database.impl.versioncontrol.VersionControlResult;
import com.openexchange.groupware.infostore.database.impl.versioncontrol.VersionControlUtil;
import com.openexchange.groupware.infostore.facade.impl.EventFiringInfostoreFacadeImpl;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.groupware.tasks.Tasks;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.groupware.userconfiguration.UserPermissionBitsStorage;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.mail.MailSessionParameterNames;
import com.openexchange.preferences.ServerUserSetting;
import com.openexchange.server.impl.ComparedOCLFolderPermissions;
import com.openexchange.server.impl.DBPool;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.tools.StringCollection;
import com.openexchange.tools.exceptions.SimpleIncorrectStringAttribute;
import com.openexchange.tools.oxfolder.memory.ConditionTreeMapManagement;
import com.openexchange.tools.oxfolder.treeconsistency.CheckPermissionOnInsert;
import com.openexchange.tools.oxfolder.treeconsistency.CheckPermissionOnRemove;
import com.openexchange.tools.oxfolder.treeconsistency.CheckPermissionOnUpdate;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.tools.sql.DBUtils;
import com.openexchange.user.User;
import gnu.trove.TIntCollection;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * {@link OXFolderManagerImpl} - The {@link OXFolderManager} implementation
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class OXFolderManagerImpl extends OXFolderManager implements OXExceptionConstants {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OXFolderManagerImpl.class);

    public static final Property MAX_FOLDER_PERMISSIONS = DefaultProperty.valueOf("com.openexchange.folder.maxPermissionEntities", I(100));

    private static final int[] SYSTEM_PUBLIC_FOLDERS = { FolderObject.SYSTEM_PUBLIC_FOLDER_ID, FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID };

    private static volatile Boolean setAdminAsCreatorForPublicDriveFolder;
    private static boolean setAdminAsCreatorForPublicDriveFolder() {
        Boolean tmp = setAdminAsCreatorForPublicDriveFolder;
        if (null == tmp) {
            synchronized (OXFolderManagerImpl.class) {
                tmp = setAdminAsCreatorForPublicDriveFolder;
                if (null == tmp) {
                    boolean defaultValue = false;
                    ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                    if (null == service) {
                        return defaultValue;
                    }
                    tmp = Boolean.valueOf(service.getBoolProperty("com.openexchange.infostore.setAdminAsCreatorForPublicDriveFolder", defaultValue));
                    setAdminAsCreatorForPublicDriveFolder = tmp;
                }
            }
        }
        return tmp.booleanValue();
    }

    /**
     * No options.
     */
    static final int OPTION_NONE = 0;

    /**
     * The option to deny updating a folder's module (provided that folder is empty).
     */
    static final int OPTION_DENY_MODULE_UPDATE = 1;

    /**
     * Allows changing the "created by" property.
     */
    private static final int OPTION_OVERRIDE_CREATED_BY = 2;

    /**
     * Signals a move to trash through a delete.
     */
    private static final int OPTION_TRASHING = 4;

    private static final String TABLE_OXFOLDER_TREE = "oxfolder_tree";

    private final Connection readCon;
    private final Connection writeCon;
    private final Context ctx;
    private final UserPermissionBits userPerms;
    private final User user;
    private final Session session;
    private final List<OXException> warnings;
    private final Collection<UpdatedFolderHandler> handlers;
    private OXFolderAccess oxfolderAccess;

    /**
     * Constructor which only uses <code>Session</code>. Optional connections are going to be set to <code>null</code>.
     *
     * @param session The session providing needed user data
     * @throws OXException If instantiation fails
     */
    OXFolderManagerImpl(final Session session) throws OXException {
        this(session, null, null);
    }

    /**
     * Constructor which only uses <code>Session</code> and <code>OXFolderAccess</code>. Optional connection are going to be set to
     * <code>null</code>.
     *
     * @throws OXException If instantiation fails
     */
    OXFolderManagerImpl(final Session session, final OXFolderAccess oxfolderAccess) throws OXException {
        this(session, oxfolderAccess, null, null, null);
    }

    /**
     * Constructor which uses <code>Session</code> and also uses a readable and a writable <code>Connection</code>.
     *
     * @throws OXException If instantiation fails
     */
    OXFolderManagerImpl(final Session session, final Connection readCon, final Connection writeCon) throws OXException {
        this(session, null, null, readCon, writeCon);
    }

    /**
     * Constructor which uses <code>Session</code> and also uses a readable and a writable <code>Connection</code>.
     *
     * @throws OXException If instantiation fails
     */
    OXFolderManagerImpl(Session session, Collection<UpdatedFolderHandler> handlers, Connection readCon, Connection writeCon) throws OXException {
        this(session, null, handlers, readCon, writeCon);
    }

    /**
     * Constructor which uses <code>Session</code>, <code>OXFolderAccess</code> and also uses a readable and a writable
     * <code>Connection</code>.
     *
     * @throws OXException If instantiation fails
     */
    OXFolderManagerImpl(Session session, final OXFolderAccess oxfolderAccess, Collection<UpdatedFolderHandler> handlers, Connection readCon, Connection writeCon) throws OXException {
        super();
        this.session = session;
        if (session instanceof ServerSession) {
            final ServerSession serverSession = (ServerSession) session;
            ctx = serverSession.getContext();
            userPerms = serverSession.getUserPermissionBits();
            user = serverSession.getUser();
        } else {
            ctx = ContextStorage.getStorageContext(session.getContextId());
            userPerms = UserPermissionBitsStorage.getInstance().getUserPermissionBits(session.getUserId(), ctx);
            user = UserStorage.getInstance().getUser(session.getUserId(), ctx);
        }
        this.readCon = readCon;
        this.writeCon = writeCon;
        this.oxfolderAccess = oxfolderAccess;
        this.handlers = handlers;
        warnings = new LinkedList<>();
    }

    @Override
    public List<OXException> getWarnings() {
        return warnings;
    }

    private OXFolderAccess getOXFolderAccess() {
        if (oxfolderAccess != null) {
            return oxfolderAccess;
        }
        return (oxfolderAccess = new OXFolderAccess(writeCon, ctx));
    }

    @Override
    public FolderObject createFolder(final FolderObject folderObj, final boolean checkPermissions, final long createTime) throws OXException {
        /*
         * No need to synchronize here since new folder IDs are unique
         */
        if (!folderObj.containsFolderName() || folderObj.getFolderName() == null || folderObj.getFolderName().length() == 0) {
            throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(FolderFields.TITLE, "", I(ctx.getContextId()));
        }
        if (!folderObj.containsParentFolderID()) {
            throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(FolderChildFields.FOLDER_ID, "", I(ctx.getContextId()));
        }
        if (!folderObj.containsModule()) {
            throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(FolderFields.MODULE, "", I(ctx.getContextId()));
        }
        if (!folderObj.containsType()) {
            throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(FolderFields.TYPE, "", I(ctx.getContextId()));
        } else if (FolderObject.SYSTEM_INFOSTORE_FOLDER_ID == folderObj.getParentFolderID()) {
            folderObj.setType(FolderObject.PUBLIC);
        }
        if (folderObj.getPermissions() == null || folderObj.getPermissions().size() == 0) {
            throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(FolderFields.PERMISSIONS, "", I(ctx.getContextId()));
        }
        checkFolderPermissions(folderObj, Optional.empty());
        final FolderObject parentFolder = getOXFolderAccess().getFolderObject(folderObj.getParentFolderID());
        if (checkPermissions) {
            /*
             * Check, if user holds right to create a sub-folder in given parent folder
             */
            try {
                final EffectivePermission p = parentFolder.getEffectiveUserPermission(user.getId(), userPerms, readCon);
                if (!p.canCreateSubfolders()) {
                    final OXException fe = OXFolderExceptionCode.NO_CREATE_SUBFOLDER_PERMISSION.create(I(user.getId()), I(parentFolder.getObjectID()), I(ctx.getContextId()));
                    if (p.getUnderlyingPermission().canCreateSubfolders()) {
                        fe.setCategory(CATEGORY_PERMISSION_DENIED);
                    }
                    throw fe;
                }
                if (!userPerms.hasModuleAccess(folderObj.getModule())) {
                    throw OXFolderExceptionCode.NO_MODULE_ACCESS.create(CATEGORY_PERMISSION_DENIED, I(user.getId()), OXFolderUtility.folderModule2String(folderObj.getModule()), I(ctx.getContextId()));
                }
                if ((parentFolder.getType() == FolderObject.PUBLIC) && !userPerms.hasFullPublicFolderAccess() && (folderObj.getModule() != FolderObject.INFOSTORE)) {
                    throw OXFolderExceptionCode.NO_PUBLIC_FOLDER_WRITE_ACCESS.create(I(user.getId()), I(parentFolder.getObjectID()), I(ctx.getContextId()));
                }
            } catch (SQLException e) {
                throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
            }
        }
        /*
         * Check folder types
         */
        if (!OXFolderUtility.checkFolderTypeAgainstParentType(parentFolder, folderObj.getType())) {
            throw OXFolderExceptionCode.INVALID_TYPE.create(I(parentFolder.getObjectID()), OXFolderUtility.folderType2String(folderObj.getType()), I(ctx.getContextId()));
        }
        /*
         * Check folder module
         */
        if (!isKnownModule(folderObj.getModule())) {
            throw OXFolderExceptionCode.UNKNOWN_MODULE.create(OXFolderUtility.folderModule2String(folderObj.getModule()), I(ctx.getContextId()));
        }
        if (!OXFolderUtility.checkFolderModuleAgainstParentModule(parentFolder.getObjectID(), parentFolder.getModule(), folderObj.getModule(), ctx.getContextId())) {
            throw OXFolderExceptionCode.INVALID_MODULE.create(I(parentFolder.getObjectID()), OXFolderUtility.folderModule2String(folderObj.getModule()), I(ctx.getContextId()));
        }
        /*
         * Check if parent folder is a shared folder OR
         * if folder is a public InfoStore folder and setting context admin as creator is desired
         */
        if (parentFolder.isShared(user.getId())) {
            /*
             * Current user wants to create a subfolder underneath a shared folder
             */
            OXFolderUtility.checkSharedSubfolderOwnerPermission(parentFolder, folderObj, user.getId(), ctx);
            /*
             * Set folder creator for next permission check and for proper insert value
             */
            folderObj.setCreatedBy(parentFolder.getCreatedBy());
        } else if (FolderObject.INFOSTORE == folderObj.getModule() && setAdminAsCreatorForPublicDriveFolder() && isPublicInfoStoreFolder(parentFolder)) {
            folderObj.setCreatedBy(ctx.getMailadmin());
        }
        OXFolderUtility.checkPermissionsAgainstSessionUserConfig(session, folderObj, parentFolder.getNonSystemPermissionsAsArray());
        /*
         * Check if admin exists and permission structure
         */
        OXFolderUtility.checkFolderPermissions(folderObj, user.getId(), ctx, warnings);
        OXFolderUtility.checkPermissionsAgainstUserConfigs(folderObj, ctx);
        if (FolderObject.PUBLIC == folderObj.getType()) {
            new CheckPermissionOnInsert(session, writeCon, ctx).checkParentPermissions(parentFolder.getObjectID(), folderObj.getNonSystemPermissionsAsArray(), createTime);
        }
        /*
         * Check against reserved / duplicate / invalid folder names in target folder
         */
        OXFolderUtility.checkTargetFolderName(readCon, ctx, user, -1, folderObj.getModule(), folderObj.getParentFolderID(), folderObj.getFolderName(), user.getId());
        /*
         * This folder shall be shared to other users
         */
        if (folderObj.getType() == FolderObject.PRIVATE && folderObj.getPermissions().size() > 1) {
            final TIntSet diff = OXFolderUtility.getShareUsers(null, folderObj.getPermissions(), user.getId(), ctx);
            if (!diff.isEmpty()) {
                final FolderObject[] allSharedFolders;
                try {
                    /*
                     * Check duplicate folder names
                     */
                    final TIntCollection fuids = OXFolderSQL.getSharedFoldersOf(user.getId(), readCon, ctx);
                    final int length = fuids.size();
                    allSharedFolders = new FolderObject[length];
                    final TIntIterator iter = fuids.iterator();
                    for (int i = 0; i < length; i++) {
                        allSharedFolders[i] = getOXFolderAccess().getFolderObject(iter.next());
                    }
                } catch (DataTruncation e) {
                    throw parseTruncated(e, folderObj, TABLE_OXFOLDER_TREE);
                } catch (IncorrectStringSQLException e) {
                    throw handleIncorrectStringError(e, session);
                } catch (SQLException e) {
                    throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
                }
                OXFolderUtility.checkSimilarNamedSharedFolder(diff, allSharedFolders, folderObj.getFolderName(), ctx);
            }
        }
        /*
         * Check duplicate permissions
         */
        OXFolderUtility.checkForDuplicateNonSystemPermissions(folderObj, ctx);
        /*
         * Get new folder ID
         */
        int fuid = generateFolderID();

        /*
         * Call SQL insert
         */
        boolean created = false;
        try {
            OXFolderSQL.insertFolderSQL(fuid, user.getId(), folderObj, createTime, ctx, writeCon);
            created = true;
            folderObj.setObjectID(fuid);
        } catch (DataTruncation e) {
            throw parseTruncated(e, folderObj, TABLE_OXFOLDER_TREE);
        } catch (IncorrectStringSQLException e) {
            throw handleIncorrectStringError(e, session);
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            if (false == created) {
                FolderCacheManager manager = FolderCacheManager.getInstance();
                manager.removeFolderObject(parentFolder.getObjectID(), ctx);
            }
        }
        /*
         * Update cache with writable connection!
         */
        final Date creatingDate = new Date(createTime);
        folderObj.setCreationDate(creatingDate);
        if (!folderObj.containsCreatedBy()) {
            folderObj.setCreatedBy(user.getId());
        }
        folderObj.setLastModified(creatingDate);
        folderObj.setModifiedBy(user.getId());
        folderObj.setSubfolderFlag(false);
        folderObj.setDefaultFolder(false);
        parentFolder.setSubfolderFlag(true);
        parentFolder.setLastModified(creatingDate);
        {
            ConditionTreeMapManagement.dropFor(ctx.getContextId());
            Connection wc = writeCon;
            final boolean create = (wc == null);
            try {
                if (create) {
                    wc = DBPool.pickupWriteable(ctx);
                }
                if (FolderCacheManager.isInitialized()) {
                    final FolderCacheManager manager = FolderCacheManager.getInstance();
                    manager.removeFolderObject(parentFolder.getObjectID(), ctx);
                    manager.loadFolderObject(parentFolder.getObjectID(), ctx, wc);
                    folderObj.fill(manager.getFolderObject(fuid, false, ctx, wc));
                } else {
                    folderObj.fill(FolderObject.loadFolderObjectFromDB(fuid, ctx, wc));
                }
                if (FolderQueryCacheManager.isInitialized()) {
                    FolderQueryCacheManager.getInstance().invalidateContextQueries(session);
                }
                try {
                    if (FolderObject.INFOSTORE == folderObj.getModule()) {
                        new EventClient(session).create(folderObj, parentFolder, getFolderPath(folderObj, parentFolder, wc));
                    } else {
                        new EventClient(session).create(folderObj, parentFolder);
                    }
                } catch (OXException e) {
                    LOG.warn("Create event could not be enqueued", e);
                }
                return folderObj;
            } finally {
                if (create && wc != null) {
                    DBPool.closeWriterAfterReading(ctx, wc);
                    wc = null;
                }
            }
        }
    }

    private boolean isPublicInfoStoreFolder(FolderObject parentFolder) throws OXException {
        int fuid = parentFolder.getObjectID();
        if (fuid <= FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID) {
            return false;
        }
        if (FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID == fuid) {
            return true;
        }

        // Recursive check with grand parent
        return isPublicInfoStoreFolder(getOXFolderAccess().getFolderObject(parentFolder.getParentFolderID()));
    }

    private int generateFolderID() throws OXException {
        int fuid = -1;
        boolean created = false;
        boolean transactionStarted = false;
        Connection wc = writeCon;
        if (wc == null) {
            wc = DBPool.pickupWriteable(ctx);
            created = true;
        }

        try {
            if (created) {
                Databases.startTransaction(wc);
            } else if (wc.getAutoCommit()) {
                Databases.startTransaction(wc);
                transactionStarted = true;
            }

            fuid = IDGenerator.getId(ctx, Types.FOLDER, wc);

            if (created || transactionStarted) {
                wc.commit();
            }
        } catch (SQLException e) {
            if (created) {
                Databases.rollback(wc);
            }
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            if (created) {
                Databases.autocommit(wc);
                DBPool.closeWriterSilent(ctx, wc);
            } else if (transactionStarted) {
                Databases.autocommit(wc);
            }
        }

        if (fuid < FolderObject.MIN_FOLDER_ID) {
            throw OXFolderExceptionCode.INVALID_SEQUENCE_ID.create(I(fuid), I(FolderObject.MIN_FOLDER_ID), I(ctx.getContextId()));
        }

        return fuid;
    }

    @Override
    public FolderObject updateFolder(final FolderObject fo, final boolean checkPermissions, final boolean handDown, final long lastModified) throws OXException {
        return updateFolder(fo, checkPermissions, handDown, lastModified, OPTION_NONE);
    }

    private FolderObject updateFolder(final FolderObject fo, final boolean checkPermissions, final boolean handDown, final long lastModified, int options) throws OXException {
        if (checkPermissions) {
            if (fo.containsType() && fo.getType() == FolderObject.PUBLIC && fo.getModule() != FolderObject.INFOSTORE && !userPerms.hasFullPublicFolderAccess()) {
                throw OXFolderExceptionCode.NO_PUBLIC_FOLDER_WRITE_ACCESS.create(I(session.getUserId()), I(fo.getObjectID()), I(ctx.getContextId()));
            }
            /*
             * Fetch effective permission from storage
             */
            final EffectivePermission perm = getOXFolderAccess().getFolderPermission(fo.getObjectID(), user.getId(), userPerms);

            if (!fo.containsType() && perm.getFolderType() == FolderObject.PUBLIC && perm.getFolderModule() != FolderObject.INFOSTORE && !userPerms.hasFullPublicFolderAccess()) {
                throw OXFolderExceptionCode.NO_PUBLIC_FOLDER_WRITE_ACCESS.create(I(session.getUserId()), I(fo.getObjectID()), I(ctx.getContextId()));
            }

            if (!perm.isFolderVisible() || !perm.getUnderlyingPermission().isFolderVisible()) {
                throw OXFolderExceptionCode.NOT_VISIBLE.create(I(fo.getObjectID()), I(session.getUserId()), I(ctx.getContextId()));
            }
            if (!perm.isFolderAdmin() || !perm.getUnderlyingPermission().isFolderAdmin()) {
                throw OXFolderExceptionCode.NO_ADMIN_ACCESS.create(I(session.getUserId()), I(fo.getObjectID()), I(ctx.getContextId()));
            }
            if (fo.getObjectID() == getPublishedMailAttachmentsFolder(session)) {
                throw OXFolderExceptionCode.NO_ADMIN_ACCESS.create(I(session.getUserId()), I(fo.getObjectID()), I(ctx.getContextId()));
            }
        }

        FolderObject originalFolder = getFolderFromMaster(fo.getObjectID());
        checkFolderPermissions(fo, Optional.ofNullable(originalFolder));
        int oldParentId = originalFolder.getParentFolderID();
        FolderObject storageObject = originalFolder.clone();

        int optionz = options;
        if (((optionz & OPTION_TRASHING) > 0) && fo.containsParentFolderID()) {
            int newParentFolderID = fo.getParentFolderID();
            if (newParentFolderID > 0 && newParentFolderID != storageObject.getParentFolderID()) {
                if ((FolderObject.TRASH == getFolderTypeFromMaster(newParentFolderID)) && (FolderObject.TRASH != getFolderTypeFromMaster(storageObject.getParentFolderID()))) {
                    // Move to trash
                    int defaultFolderId = oxfolderAccess.getDefaultFolderID(session.getUserId(), FolderObject.INFOSTORE, FolderObject.PUBLIC);
                    int folderId = fo.getObjectID();
                    String name = fo.containsFolderName() && Strings.isNotEmpty(fo.getFolderName()) ? fo.getFolderName() : storageObject.getFolderName();
                    try {
                        while (-1 != OXFolderSQL.lookUpFolderOnUpdate(folderId, newParentFolderID, name, storageObject.getModule(), readCon, ctx)) {
                            name = incrementSequenceNumber(name);
                        }
                    } catch (SQLException e) {
                        throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
                    }
                    if (fo.getModule() == FolderObject.INFOSTORE) {
                        try {
                            FolderPathObject path = OXFolderSQL.generateFolderPathFor(folderId, defaultFolderId, readCon, ctx);
                            fo.setOriginPath(path);
                        } catch (SQLException e) {
                            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
                        }
                    }
                    /*
                     * remove any folder-dependent entities
                     */
                    deleteDependentEntities(writeCon, storageObject, true);
                    /*
                     * perform move to trash
                     */
                    fo.setFolderName(name);
                    fo.setPermissions(getFolderFromMaster(newParentFolderID).getPermissions());
                    // when deleting a folder, the permissions should always be inherited from the parent trash folder
                    // in order to do so, "created by" is overridden intentionally here to not violate permission restrictions,
                    // and to prevent synthetic system permissions to get inserted implicitly
                    optionz |= user.getId() != storageObject.getCreatedBy() ? OPTION_OVERRIDE_CREATED_BY : OPTION_NONE;
                }
            }
        }

        Map<Integer, Integer> folderId2OldOwner = null;
        try {
            if (fo.containsPermissions() || fo.containsModule() || fo.containsMeta() || fo.containsOriginPath()) {
                int newParentFolderID = fo.getParentFolderID();
                if (fo.containsParentFolderID() && newParentFolderID > 0 && newParentFolderID != storageObject.getParentFolderID()) {
                    folderId2OldOwner = determineCurrentOwnerships(originalFolder);
                    move(fo.getObjectID(), newParentFolderID, fo.getCreatedBy(), fo.getFolderName(), storageObject, lastModified);
                    // Reload storage's folder for following update
                    storageObject = getFolderFromMaster(fo.getObjectID());
                } else {
                    // Check if permissions of a trash folder are supposed to be changed
                    if (fo.containsPermissions()) {
                        checkTrashFolderPermissionChange(fo, storageObject);
                    }
                }
                update(fo, optionz, storageObject, lastModified, handDown);
            } else if (fo.containsFolderName()) {
                int newParentFolderID = fo.getParentFolderID();
                if (fo.containsParentFolderID() && newParentFolderID > 0 && newParentFolderID != storageObject.getParentFolderID()) {
                    // Perform move
                    folderId2OldOwner = determineCurrentOwnerships(originalFolder);
                    move(fo.getObjectID(), newParentFolderID, fo.getCreatedBy(), fo.getFolderName(), storageObject, lastModified);
                } else {
                    rename(fo, storageObject, lastModified);
                }
            } else if (fo.containsParentFolderID()) {
                int newParentFolderID = fo.getParentFolderID();
                if (newParentFolderID > 0 && newParentFolderID != storageObject.getParentFolderID()) {
                    // Perform move
                    folderId2OldOwner = determineCurrentOwnerships(originalFolder);
                    move(fo.getObjectID(), fo.getParentFolderID(), fo.getCreatedBy(), null, storageObject, lastModified);
                }
            }
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        }
        /*
         * Possibly changed file storage?
         */
        if (null != folderId2OldOwner) {
            adjustFileStorageLocations(folderId2OldOwner);
        }
        /*
         * Finally update cache
         */
        {
            ConditionTreeMapManagement.dropFor(ctx.getContextId());
            Connection wc = writeCon;
            final boolean create = (wc == null);
            try {
                if (create) {
                    wc = DBPool.pickupWriteable(ctx);
                }
                if (FolderCacheManager.isEnabled()) {
                    final FolderCacheManager cacheManager = FolderCacheManager.getInstance();
                    cacheManager.removeFolderObject(ctx.getContextId(), fo.getObjectID(), wc);
                    {
                        FolderObject tmp = cacheManager.loadFolderObject(fo.getObjectID(), ctx, wc);
                        fo.fill(tmp);
                        if (null != handlers) {
                            for (UpdatedFolderHandler handler : handlers) {
                                handler.onFolderUpdated(tmp, wc);
                            }
                        }
                    }
                    final int parentFolderID = fo.getParentFolderID();
                    if (parentFolderID > 0) {
                        /*
                         * Update parent, too
                         * it is needed to do this by removing the folder object so the invalidation is distributed every time also in the
                         * event that it is not in the local cache
                         */
                        cacheManager.removeFolderObject(ctx.getContextId(), parentFolderID, wc);
                        FolderObject tmp = cacheManager.loadFolderObject(parentFolderID, ctx, wc);
                        if (null != handlers) {
                            for (UpdatedFolderHandler handler : handlers) {
                                handler.onFolderUpdated(tmp, wc);
                            }
                        }
                    }
                    if (0 < oldParentId && oldParentId != parentFolderID) {
                        /*
                         * Update old parent, too
                         */
                        cacheManager.removeFolderObject(ctx.getContextId(), oldParentId, wc);
                        FolderObject tmp = cacheManager.loadFolderObject(oldParentId, ctx, wc);
                        if (null != handlers) {
                            for (UpdatedFolderHandler handler : handlers) {
                                handler.onFolderUpdated(tmp, wc);
                            }
                        }
                    }
                } else {
                    fo.fill(FolderObject.loadFolderObjectFromDB(fo.getObjectID(), ctx, wc));
                }
                if (FolderQueryCacheManager.isInitialized()) {
                    FolderQueryCacheManager.getInstance().invalidateContextQueries(session);
                }
                if (FolderObject.SYSTEM_MODULE != fo.getModule()) {
                    try {
                        FolderObject newParentFolder = FolderObject.loadFolderObjectFromDB(fo.getParentFolderID(), ctx, wc, true, false);
                        if (FolderObject.INFOSTORE == fo.getModule()) {
                            new EventClient(session).modify(originalFolder, fo, newParentFolder, getFolderPath(fo, newParentFolder, wc));
                        } else {
                            new EventClient(session).modify(originalFolder, fo, newParentFolder);
                        }
                    } catch (OXException e) {
                        LOG.warn("Update event could not be enqueued", e);
                    }
                }
                return fo;
            } finally {
                if (create && wc != null) {
                    DBPool.closeWriterAfterReading(ctx, wc);
                    wc = null;
                }
            }
        }
    }

    private void checkTrashFolderPermissionChange(final FolderObject fo, FolderObject storageObject) throws OXException {
        FolderObject trashFolder = getTrashFolder(storageObject.getModule());
        if (null != trashFolder) {
            boolean belowTrash;
            int trashFolderID = trashFolder.getObjectID();
            if (storageObject.getObjectID() == trashFolderID || storageObject.getParentFolderID() == trashFolderID) {
                belowTrash = true;
            } else {
                OXFolderAccess folderAccess = getOXFolderAccess();
                FolderObject p = storageObject;
                while (p.getParentFolderID() != trashFolderID && FolderObject.MIN_FOLDER_ID < p.getParentFolderID()) {
                    p = folderAccess.getFolderObject(p.getParentFolderID());
                }
                belowTrash = p.getParentFolderID() == trashFolderID;
            }

            if (belowTrash && !OXFolderUtility.equalPermissions(fo.getNonSystemPermissionsAsArray(), storageObject.getNonSystemPermissionsAsArray())) {
                throw OXFolderExceptionCode.NO_TRASH_PERMISSIONS_CHANGE_ALLOWED.create(I(fo.getObjectID()), I(ctx.getContextId()));
            }
        }
    }

    private Map<Integer, Integer> determineCurrentOwnerships(FolderObject folder) throws OXException {
        Map<Integer, Integer> folderId2OldOwner;
        List<Integer> folderIds = new ArrayList<>(8);
        folderIds.add(I(folder.getObjectID()));
        if (folder.hasSubfolders()) {
            try {
                folderIds.addAll(OXFolderSQL.getSubfolderIDs(folder.getObjectID(), writeCon, ctx, true));
            } catch (SQLException e) {
                throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
            }
        }
        folderId2OldOwner = new LinkedHashMap<>(folderIds.size());
        for (Integer folderId : folderIds) {
            folderId2OldOwner.put(folderId, I(getFolderFromMaster(folderId.intValue()).getCreatedBy()));
        }
        return folderId2OldOwner;
    }

    private void adjustFileStorageLocations(Map<Integer, Integer> folderId2OldOwner) throws OXException {
        List<Map<Integer, List<VersionControlResult>>> results = new LinkedList<>();
        boolean error = true;
        try {
            for (Entry<Integer, Integer> f2o : folderId2OldOwner.entrySet()) {
                int folderId = f2o.getKey().intValue();
                int newOwner = getFolderOwnerFromMaster(folderId);
                int oldOwner = f2o.getValue().intValue();
                if (oldOwner != newOwner) {
                    // File storage location might be changed due to changed ownership
                    Connection wc = writeCon;
                    boolean create = (wc == null);
                    Map<Integer, List<VersionControlResult>> modified = null;
                    try {
                        if (create) {
                            wc = DBPool.pickupWriteable(ctx);
                        }
                        modified = VersionControlUtil.changeFileStoreLocationsIfNecessary(oldOwner, newOwner, folderId, ctx, wc);
                        if (!modified.isEmpty()) {
                            results.add(modified);
                        }
                    } finally {
                        if (create && wc != null) {
                            if (null != modified && !modified.isEmpty()) {
                                DBPool.closeWriterSilent(ctx, wc);
                            } else {
                                DBPool.closeWriterAfterReading(ctx, wc);
                            }
                            wc = null;
                        }
                    }
                }
            }
            error = false;
        } finally {
            if (error) {
                Connection wc = writeCon;
                boolean create = (wc == null);
                try {
                    if (create) {
                        wc = DBPool.pickupWriteable(ctx);
                    }
                    for (Map<Integer,List<VersionControlResult>> resultMap : results) {
                        for (Map.Entry<Integer, List<VersionControlResult>> documentEntry : resultMap.entrySet()) {
                            Integer documentId = documentEntry.getKey();
                            List<VersionControlResult> versionInfo = documentEntry.getValue();

                            try {
                                VersionControlUtil.restoreVersionControl(Collections.singletonMap(documentId, versionInfo), ctx, wc);
                            } catch (Exception e) {
                                LOG.error("Failed to restore InfoStore/Drive files for document {} in context {}", documentId, I(ctx.getContextId()), e);
                            }
                        }
                    }
                } finally {
                    if (create && wc != null) {
                        DBPool.closeWriterSilent(ctx, wc);
                        wc = null;
                    }
                }
            }
        }
    }

    protected void update(FolderObject fo, int options, FolderObject storageObj, long lastModified, boolean handDown) throws OXException {
        doUpdate(fo, options, storageObj, lastModified, handDown, new TIntObjectHashMap<TIntSet>());
    }

    void doUpdate(FolderObject fo, int options, FolderObject storageObj, long lastModified, boolean handDown, TIntObjectMap<TIntSet> alreadyCheckedParents) throws OXException {
        if (fo.getObjectID() <= 0) {
            throw OXFolderExceptionCode.INVALID_OBJECT_ID.create(I(fo.getObjectID()));
        }
        /*
         * Get storage version (and thus implicitly check existence)
         */
        final boolean containsPermissions = fo.containsPermissions();
        if (fo.getPermissions() == null || fo.getPermissions().isEmpty()) {
            if (containsPermissions) {
                /*
                 * Deny to set empty permissions
                 */
                throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(FolderFields.PERMISSIONS, I(fo.getObjectID()), I(ctx.getContextId()));
            }
            /*
             * Pass storage's permissions
             */
            fo.setPermissionsAsArray(storageObj.getPermissionsAsArray());
        }
        /*
         * Check if a move is done here
         */
        if (fo.containsParentFolderID() && fo.getParentFolderID() > 0 && storageObj.getParentFolderID() != fo.getParentFolderID()) {
            throw OXFolderExceptionCode.NO_MOVE_THROUGH_UPDATE.create(I(fo.getObjectID()));
        }
        /*
         * Check folder name
         */
        if (fo.containsFolderName()) {
            if (fo.getFolderName() == null || fo.getFolderName().trim().length() == 0) {
                throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(FolderFields.TITLE, I(fo.getObjectID()), I(ctx.getContextId()));
            } else if (storageObj.isDefaultFolder() && !fo.getFolderName().equals(storageObj.getFolderName())) {
                throw OXFolderExceptionCode.NO_DEFAULT_FOLDER_RENAME.create(I(fo.getObjectID()), I(ctx.getContextId()));
            }
        }
        /*
         * Check if folder module is supposed to be updated
         */
        if (fo.containsModule() && fo.getModule() != storageObj.getModule() && FolderObject.SYSTEM_MODULE != storageObj.getModule()) {
            /*
             * Module update only allowed if known and folder is empty
             */
            if (!isKnownModule(fo.getModule())) {
                throw OXFolderExceptionCode.UNKNOWN_MODULE.create(OXFolderUtility.folderModule2String(fo.getModule()), I(ctx.getContextId()));
            }
            if (storageObj.isDefaultFolder()) {
                /*
                 * A default folder's module must not be changed
                 */
                throw OXFolderExceptionCode.NO_DEFAULT_FOLDER_MODULE_UPDATE.create();
            } else if (!isFolderEmpty(storageObj.getObjectID(), storageObj.getModule())) {
                /*
                 * Module cannot be updated since folder already contains elements
                 */
                throw OXFolderExceptionCode.DENY_FOLDER_MODULE_UPDATE.create();
            } else if ((options & OPTION_DENY_MODULE_UPDATE) > 0) {
                /*
                 * Folder module must not be updated
                 */
                throw OXFolderExceptionCode.NO_FOLDER_MODULE_UPDATE.create();
            }
            FolderObject parent = getFolderFromMaster(storageObj.getParentFolderID());
            if (!OXFolderUtility.checkFolderModuleAgainstParentModule(parent.getObjectID(), parent.getModule(), fo.getModule(), ctx.getContextId())) {
                throw OXFolderExceptionCode.INVALID_MODULE.create(I(parent.getObjectID()), OXFolderUtility.folderModule2String(fo.getModule()), I(ctx.getContextId()));
            }
        } else {
            fo.setModule(storageObj.getModule());
        }
        /*
         * Check if shared
         */
        if (storageObj.isShared(user.getId())) {
            throw OXFolderExceptionCode.NO_SHARED_FOLDER_UPDATE.create(I(fo.getObjectID()), I(ctx.getContextId()));
        }
        /*
         * Check Permissions
         */
        fo.setType(storageObj.getType());
        if ((options & OPTION_OVERRIDE_CREATED_BY) <= 0) {
            fo.setCreatedBy(storageObj.getCreatedBy());
        }
        fo.setDefaultFolder(storageObj.isDefaultFolder());
        {
            OCLPermission[] originalPermissions = storageObj.getNonSystemPermissionsAsArray();
            OCLPermission[] updatedPermissions = fo.getNonSystemPermissionsAsArray();
            OXFolderUtility.checkPermissionsAgainstSessionUserConfig(session, fo, updatedPermissions, originalPermissions);
            OXFolderUtility.checkFolderPermissions(fo, user.getId(), ctx, warnings);
            OXFolderUtility.checkPermissionsAgainstUserConfigs(readCon, fo, ctx);
            OXFolderUtility.checkSystemFolderPermissions(fo.getObjectID(), updatedPermissions, user, ctx);
            if (FolderObject.PUBLIC == fo.getType() || FolderObject.INFOSTORE == storageObj.getModule()) {
                {
                    final OCLPermission[] removedPerms = OXFolderUtility.getPermissionsWithoutFolderAccess(updatedPermissions, originalPermissions);
                    if (removedPerms.length > 0) {
                        new CheckPermissionOnRemove(session, writeCon, ctx).checkPermissionsOnUpdate(fo.getObjectID(), removedPerms, lastModified);
                    }
                }

                // add a TIntSet for each permission entity
                boolean allChecked = true;
                for (OCLPermission perm : updatedPermissions) {
                    if (null == alreadyCheckedParents.putIfAbsent(perm.getEntity(), new TIntHashSet())) {
                        allChecked = false;
                    }
                }
                if (!allChecked) {
                    new CheckPermissionOnUpdate(session, writeCon, ctx).checkParentPermissions(storageObj.getParentFolderID(), updatedPermissions, originalPermissions, lastModified, alreadyCheckedParents);
                }
            }
            /*
             * Check duplicate permissions
             */
            OXFolderUtility.checkForDuplicatePermissions(updatedPermissions);
        }

        boolean rename = false;
        if (fo.containsFolderName() && !storageObj.getFolderName().equals(fo.getFolderName())) {
            rename = true;
            /*
             * Check against reserved / duplicate / invalid folder names in target folder
             */
            OXFolderUtility.checkTargetFolderName(readCon, ctx, user, fo.getObjectID(), fo.getModule(), storageObj.getParentFolderID(), fo.getFolderName(), storageObj.getCreatedBy());
        }
        /*
         * This folder shall be shared to other users
         */
        if (fo.getType() == FolderObject.PRIVATE && fo.getPermissions().size() > 1) {
            final TIntSet diff = OXFolderUtility.getShareUsers(rename ? null : storageObj.getPermissions(), fo.getPermissions(), user.getId(), ctx);
            if (!diff.isEmpty()) {
                OXFolderAccess folderAccess = getOXFolderAccess();
                final FolderObject[] allSharedFolders;
                try {
                    /*
                     * Check duplicate folder names
                     */
                    final TIntCollection fuids = OXFolderSQL.getSharedFoldersOf(user.getId(), readCon, ctx);
                    final int size = fuids.size();
                    allSharedFolders = new FolderObject[size];
                    final TIntIterator iter = fuids.iterator();
                    for (int i = 0; i < size; i++) {
                        /*
                         * Remove currently updated folder
                         */
                        final int fuid = iter.next();
                        if (fuid != fo.getObjectID()) {
                            allSharedFolders[i] = folderAccess.getFolderObject(fuid);
                        }
                    }
                } catch (DataTruncation e) {
                    throw parseTruncated(e, fo, TABLE_OXFOLDER_TREE);
                } catch (IncorrectStringSQLException e) {
                    throw handleIncorrectStringError(e, session);
                } catch (SQLException e) {
                    throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
                }
                OXFolderUtility.checkSimilarNamedSharedFolder(diff, allSharedFolders, rename ? fo.getFolderName() : storageObj.getFolderName(), ctx);
            }
        }
        /*
         * Call SQL update
         */
        try {
            OXFolderSQL.updateFolderSQL(user.getId(), fo, lastModified, ctx, writeCon);
        } catch (DataTruncation e) {
            throw parseTruncated(e, fo, TABLE_OXFOLDER_TREE);
        } catch (IncorrectStringSQLException e) {
            throw handleIncorrectStringError(e, session);
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        }
        if (handDown) {
            /*
             * Check if permissions are supposed to be handed down to subfolders
             */
            try {
                if (containsPermissions) {
                    final List<OCLPermission> permissions = fo.getPermissions();
                    if (permissions != null && !permissions.isEmpty()) {
                        List<OCLPermission> origPermissions = storageObj.getPermissions();

                        ComparedOCLFolderPermissions compPerm = new ComparedOCLFolderPermissions(permissions.toArray(new OCLPermission[permissions.size()]), origPermissions.toArray(new OCLPermission[origPermissions.size()]));
                        handDown(fo.getObjectID(), options, compPerm, lastModified, alreadyCheckedParents, FolderCacheManager.isEnabled() ? FolderCacheManager.getInstance() : null);
                    }
                }
            } catch (DataTruncation e) {
                throw parseTruncated(e, fo, TABLE_OXFOLDER_TREE);
            } catch (IncorrectStringSQLException e) {
                throw handleIncorrectStringError(e, session);
            } catch (SQLException e) {
                throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
            } catch (ProcedureFailedException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof OXException) {
                    throw (OXException) cause;
                }
                if (cause instanceof SQLException) {
                    throw OXFolderExceptionCode.SQL_ERROR.create(cause, cause.getMessage());
                }
                throw OXFolderExceptionCode.RUNTIME_ERROR.create(cause, cause.getMessage());
            }
        }
    }

    protected void handDown(final int folderId, final int options, final ComparedOCLFolderPermissions permission, final long lastModified, final TIntObjectMap<TIntSet> alreadyCheckedParents, final FolderCacheManager cacheManager) throws OXException, SQLException {
        final Context ctx = this.ctx;
        final TIntList subfolders = OXFolderSQL.getSubfolderIDs(folderId, writeCon, ctx);
        if (!subfolders.isEmpty()) {
            final Session session = this.session;
            subfolders.forEach(new TIntProcedure() {

                @Override
                public boolean execute(final int subfolderId) {
                    try {
                        final FolderObject tmp = new FolderObject(subfolderId);
                        FolderObject folderFromMaster = getFolderFromMaster(subfolderId);
                        List<OCLPermission> tmpPermissions = adjustPermissions(permission, folderId);
                        tmp.setPermissions(tmpPermissions);
                        doUpdate(tmp, options, folderFromMaster, lastModified, true, alreadyCheckedParents);  // Calls handDown() for subfolder, as well
                        if (null != cacheManager) {
                            cacheManager.removeFolderObject(subfolderId, ctx);
                        }
                        CacheFolderStorage.getInstance().removeFromCache(Integer.toString(subfolderId), FolderStorage.REAL_TREE_ID, true, session);
                        return true;
                    } catch (OXException e) {
                        throw new ProcedureFailedException(e);
                    } catch (RuntimeException e) {
                        throw new ProcedureFailedException(e);
                    }
                }
            });
        }
    }


    /**
     * Merges old and new permissions for hand down
     *
     * @param permission The new permissions
     * @param parentId The id of the parent folder
     */
    List<OCLPermission> adjustPermissions(ComparedOCLFolderPermissions permission, int parentId) {
        List<OCLPermission> result = new ArrayList<>(permission.getNewPermissions().size());
        for (OCLPermission perm : permission.getNewPermissions()) {
            result.add(perm);
        }
        adjustTypeOfInheritedPermissions(result, parentId);
        return result;
    }

    /**
     * Ensures that {@link FolderPermissionType#LEGATOR} is handed down as {@link FolderPermissionType#INHERITED}
     *
     * @param permissions The new permissions
     * @param parentId The id of the parent folder
     */
    void adjustTypeOfInheritedPermissions(List<OCLPermission> permissions, int parentId) {
        for (OCLPermission perm : permissions) {
            if (perm.getSystem() != 1 && perm.getType() == FolderPermissionType.LEGATOR) {
                perm.setType(FolderPermissionType.INHERITED);
                perm.setPermissionLegator(String.valueOf(parentId));
            }
        }
    }

    private int getFolderTypeFromMaster(int folderId) throws OXException {
        return getFolderFromMaster(folderId, false, false).getType();
    }

    private int getFolderOwnerFromMaster(int folderId) throws OXException {
        return getFolderFromMaster(folderId, false, false).getCreatedBy();
    }

    protected FolderObject getFolderFromMaster(int folderId) throws OXException {
        return getFolderFromMaster(folderId, false);
    }

    private FolderObject getFolderFromMaster(int folderId, boolean withSubfolders) throws OXException {
        return getFolderFromMaster(folderId, true, withSubfolders);
    }

    private FolderObject getFolderFromMaster(int folderId, boolean loadPermissions, boolean withSubfolders) throws OXException {
        // Use writable connection to ensure to fetch from master database
        Connection wc = writeCon;
        if (wc != null) {
            return FolderObject.loadFolderObjectFromDB(folderId, ctx, wc, loadPermissions, withSubfolders);
        }

        // Fetch new writable connection
        wc = DBPool.pickupWriteable(ctx);
        try {
            return FolderObject.loadFolderObjectFromDB(folderId, ctx, wc, loadPermissions, withSubfolders);
        } finally {
            DBPool.closeWriterAfterReading(ctx, wc);
        }
    }

    private boolean isFolderEmpty(final int folderId, final int module) throws OXException {
        if (module == FolderObject.TASK) {
            final Tasks tasks = Tasks.getInstance();
            return readCon == null ? tasks.isFolderEmpty(ctx, folderId) : tasks.isFolderEmpty(ctx, readCon, folderId);
        } else if (module == FolderObject.CALENDAR) {
            CalendarSession calendarSession = ServerServiceRegistry.getInstance().getService(CalendarService.class, true).init(session);
            calendarSession.set(PARAMETER_CONNECTION(), readCon);
            return 0 == calendarSession.getCalendarService().getUtilities().countEvents(calendarSession, String.valueOf(folderId));
        } else if (module == FolderObject.CONTACT) {
            ContactService contactService = ServerServiceRegistry.getInstance().getService(ContactService.class, true);
            return contactService.isFolderEmpty(session, String.valueOf(folderId));
        } else if (module == FolderObject.INFOSTORE) {
            final InfostoreFacade db = new EventFiringInfostoreFacadeImpl(readCon == null ? new DBPoolProvider() : new StaticDBPoolProvider(readCon));
            return db.isFolderEmpty(folderId, ctx);
        } else if (module == FolderObject.SYSTEM_MODULE) {
            return true;
        } else {
            throw OXFolderExceptionCode.UNKNOWN_MODULE.create(OXFolderUtility.folderModule2String(module),
                I(ctx.getContextId()));
        }
    }

    private static boolean isKnownModule(final int module) {
        return ((module == FolderObject.TASK) || (module == FolderObject.CALENDAR) || (module == FolderObject.CONTACT) || (module == FolderObject.INFOSTORE));
    }

    private void rename(final FolderObject folderObj, final FolderObject storageObj, final long lastModified) throws OXException {
        if (folderObj.getObjectID() <= 0) {
            throw OXFolderExceptionCode.INVALID_OBJECT_ID.create(I(folderObj.getObjectID()));
        } else if (!folderObj.containsFolderName() || folderObj.getFolderName() == null || folderObj.getFolderName().trim().length() == 0) {
            throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(FolderFields.TITLE, "", I(ctx.getContextId()));
        }
        /*
         * Check if rename can be avoided (cause new name equals old one) and prevent default folder rename
         */
        if (storageObj.getFolderName().equals(folderObj.getFolderName())) {
            return;
        }
        if (storageObj.isDefaultFolder()) {
            throw OXFolderExceptionCode.NO_DEFAULT_FOLDER_RENAME.create(I(folderObj.getObjectID()), I(ctx.getContextId()));
        }
        /*
         * Check against reserved / duplicate / invalid folder names in target folder
         */
        OXFolderUtility.checkTargetFolderName(readCon, ctx, user, folderObj.getObjectID(), storageObj.getModule(), storageObj.getParentFolderID(), folderObj.getFolderName(), storageObj.getCreatedBy());
        /*
         * This folder shall be shared to other users
         */
        if (storageObj.getType() == FolderObject.PRIVATE && storageObj.getPermissions().size() > 1) {
            final TIntSet diff = OXFolderUtility.getShareUsers(null, storageObj.getPermissions(), user.getId(), ctx);
            if (!diff.isEmpty()) {
                final FolderObject[] allSharedFolders;
                try {
                    /*
                     * Check duplicate folder names
                     */
                    final TIntCollection fuids = OXFolderSQL.getSharedFoldersOf(user.getId(), readCon, ctx);
                    final int size = fuids.size();
                    allSharedFolders = new FolderObject[size];
                    final TIntIterator iter = fuids.iterator();
                    for (int i = 0; i < size; i++) {
                        /*
                         * Remove currently renamed folder
                         */
                        final int fuid = iter.next();
                        if (fuid != folderObj.getObjectID()) {
                            allSharedFolders[i] = getOXFolderAccess().getFolderObject(fuid);
                        }
                    }
                } catch (DataTruncation e) {
                    throw parseTruncated(e, folderObj, TABLE_OXFOLDER_TREE);
                } catch (IncorrectStringSQLException e) {
                    throw handleIncorrectStringError(e, session);
                } catch (SQLException e) {
                    throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
                }
                OXFolderUtility.checkSimilarNamedSharedFolder(diff, allSharedFolders, folderObj.getFolderName(), ctx);
            }
        }
        /*
         * Call SQL rename
         */
        try {
            OXFolderSQL.updateName(storageObj, folderObj.getFolderName(), lastModified, user.getId(), ctx, writeCon);
        } catch (DataTruncation e) {
            throw parseTruncated(e, folderObj, TABLE_OXFOLDER_TREE);
        } catch (IncorrectStringSQLException e) {
            throw handleIncorrectStringError(e, session);
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Moves and/or renames a folder in the database.
     *
     * @param folderId The identifier of the folder to move and/or rename
     * @param targetFolderId The identifier of the target folder
     * @param createdBy The user who created the folder
     * @param newName The new folder name, or <code>null</code> if unchanged
     * @param storageSrc The folder being moved, reloaded from the storage
     * @param lastModified The new time stamp to store as last modification date
     */
    private void move(int folderId, int targetFolderId, int createdBy, String newName, FolderObject storageSrc, long lastModified) throws OXException, SQLException {
        /*
         * Folder is already in target folder and does not need to be renamed
         */
        int oldParentId = storageSrc.getParentFolderID();
        if (oldParentId == targetFolderId && (null == newName || newName.equals(storageSrc.getFolderName()))) {
            return;
        }
        /*
         * Default folder must not be moved
         */
        if (storageSrc.isDefaultFolder()) {
            throw OXFolderExceptionCode.NO_DEFAULT_FOLDER_MOVE.create(I(storageSrc.getObjectID()), I(ctx.getContextId()));
        }
        /*
         * Check if duplicate / reserved folder exists in target folder
         */
        String folderName = null == newName ? storageSrc.getFolderName() : newName;
        OXFolderUtility.checkTargetFolderName(readCon, ctx, user, folderId, storageSrc.getModule(), targetFolderId, folderName, createdBy);
        /*
         * For further checks we need to load destination folder
         */
        final FolderObject storageDest = getOXFolderAccess().getFolderObject(targetFolderId);
        /*
         * Check a bunch of possible errors
         */
        try {
            if (storageSrc.isShared(user.getId())) {
                throw OXFolderExceptionCode.NO_SHARED_FOLDER_MOVE.create(I(storageSrc.getObjectID()), I(ctx.getContextId()));
            } else if (storageDest.isShared(user.getId())) {
                throw OXFolderExceptionCode.NO_SHARED_FOLDER_TARGET.create(I(storageDest.getObjectID()), I(ctx.getContextId()));
            } else if (storageSrc.getType() == FolderObject.SYSTEM_TYPE) {
                throw OXFolderExceptionCode.NO_SYSTEM_FOLDER_MOVE.create(I(storageSrc.getObjectID()), I(ctx.getContextId()));
            } else if (storageSrc.getType() == FolderObject.PRIVATE && ((storageDest.getType() == FolderObject.PUBLIC || (storageDest.getType() == FolderObject.SYSTEM_TYPE && targetFolderId != FolderObject.SYSTEM_PRIVATE_FOLDER_ID)))) {
                throw OXFolderExceptionCode.ONLY_PRIVATE_TO_PRIVATE_MOVE.create(I(storageSrc.getObjectID()), I(ctx.getContextId()));
            } else if (storageSrc.getType() == FolderObject.PUBLIC && ((storageDest.getType() == FolderObject.PRIVATE || (storageDest.getType() == FolderObject.SYSTEM_TYPE && false == com.openexchange.tools.arrays.Arrays.contains(SYSTEM_PUBLIC_FOLDERS, targetFolderId))))) {
                throw OXFolderExceptionCode.ONLY_PUBLIC_TO_PUBLIC_MOVE.create(I(storageSrc.getObjectID()), I(ctx.getContextId()));
            } else if (storageSrc.getModule() == FolderObject.INFOSTORE && storageDest.getModule() != FolderObject.INFOSTORE && targetFolderId != FolderObject.SYSTEM_INFOSTORE_FOLDER_ID) {
                throw OXFolderExceptionCode.INCOMPATIBLE_MODULES.create(OXFolderUtility.folderModule2String(storageSrc.getModule()), OXFolderUtility.folderModule2String(storageDest.getModule()));
            } else if (storageSrc.getModule() != FolderObject.INFOSTORE && storageDest.getModule() == FolderObject.INFOSTORE) {
                throw OXFolderExceptionCode.INCOMPATIBLE_MODULES.create(OXFolderUtility.folderModule2String(storageSrc.getModule()), OXFolderUtility.folderModule2String(storageDest.getModule()));
            } else if (storageDest.getEffectiveUserPermission(user.getId(), userPerms).getFolderPermission() < OCLPermission.CREATE_SUB_FOLDERS) {
                throw OXFolderExceptionCode.NO_CREATE_SUBFOLDER_PERMISSION.create(I(user.getId()), I(storageDest.getObjectID()), I(ctx.getContextId()));
            } else if (folderId == targetFolderId) {
                throw OXFolderExceptionCode.NO_EQUAL_MOVE.create(I(ctx.getContextId()));
            }
        } catch (RuntimeException e) {
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(e, I(ctx.getContextId()));
        }
        /*
         * Check if source folder has subfolders
         */
        if (storageSrc.hasSubfolders()) {
            /*
             * Check if target is a descendant folder
             */
            TIntList parentIDList = new TIntArrayList(1);
            parentIDList.add(storageSrc.getObjectID());
            if (OXFolderUtility.isDescendentFolder(parentIDList, targetFolderId, readCon, ctx)) {
                throw OXFolderExceptionCode.NO_SUBFOLDER_MOVE.create(I(storageSrc.getObjectID()), I(ctx.getContextId()));
            }
            parentIDList = null;
            /*
             * Count all moveable subfolders: TODO: Recursive check???
             */
            int numOfMoveableSubfolders = OXFolderSQL.getNumOfMoveableSubfolders(storageSrc.getObjectID(), user.getId(), user.getGroups(), readCon, ctx);
            if (numOfMoveableSubfolders < storageSrc.getSubfolderIds(true, ctx).size()) {
                throw OXFolderExceptionCode.NO_SUBFOLDER_MOVE_ACCESS.create(I(session.getUserId()), I(storageSrc.getObjectID()), I(ctx.getContextId()));
            }
        }
        /*
         * First treat as a delete prior to actual move
         */
        processDeletedFolderThroughMove(storageSrc, new CheckPermissionOnRemove(session, writeCon, ctx), lastModified);
        /*
         * Call SQL move
         */
        try {
            storageSrc.setFolderName(newName);
            OXFolderSQL.moveFolderSQL(user.getId(), storageSrc, storageDest, lastModified, ctx, writeCon);
        } catch (DataTruncation e) {
            throw parseTruncated(e, storageSrc, TABLE_OXFOLDER_TREE);
        } catch (IncorrectStringSQLException e) {
            throw handleIncorrectStringError(e, session);
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        }
        /*
         * Now treat as an insert after actual move if not moved below trash
         */
        if (FolderObject.TRASH != storageDest.getType()) {
            processInsertedFolderThroughMove(getFolderFromMaster(folderId), new CheckPermissionOnInsert(session, writeCon, ctx), lastModified);
        }
        /*
         * Inherit folder type recursively if move from or to special folder
         */
        {
            int optNewOwner = FolderObject.TRASH == storageDest.getType() ? user.getId() : 0;
            adjustFolderTypeOnMove(storageSrc, storageDest, true, optNewOwner);
        }
        /*
         * Adjust owner (if necessary)
         */
        changeOwnerOnMove(storageSrc, targetFolderId, oldParentId, true);
        /*
         * Update last-modified time stamps
         */
        OXFolderSQL.updateLastModified(storageSrc.getParentFolderID(), lastModified, user.getId(), writeCon, ctx);
        OXFolderSQL.updateLastModified(storageSrc.getObjectID(), lastModified, user.getId(), writeCon, ctx);
        OXFolderSQL.updateLastModified(storageDest.getObjectID(), lastModified, user.getId(), writeCon, ctx);
        /*
         * Update OLD parent in cache, cause this can only be done here
         */
        if (FolderCacheManager.isEnabled()) {
            Connection wc = writeCon;
            final boolean create = (wc == null);
            if (create) {
                wc = DBPool.pickupWriteable(ctx);
            }
            try {
                final int srcParentId = storageSrc.getParentFolderID();
                if (srcParentId > 0) {
                    FolderCacheManager.getInstance().loadFolderObject(srcParentId, ctx, wc);
                }
                final int destParentId = storageDest.getParentFolderID();
                if (destParentId > 0) {
                    FolderCacheManager.getInstance().loadFolderObject(destParentId, ctx, wc);
                }
            } finally {
                if (create && wc != null) {
                    DBPool.closeWriterSilent(ctx, wc);
                }
            }
        }
    }

    /**
     * Adjusts the folder's type as needed after moving a folder to a new parent folder.
     *
     * @param sourceFolder The folder being moved
     * @param destinationFolder The destination folder, i.e. the new parent folder
     * @param recursive <code>true</code> to inherit the new folder type to any subfolders recursively, <code>false</code>, otherwise
     * @param optNewOwner The optional new owner or less than/equal to <code>0</code> (zero)
     * @return <code>true</code> if the folder type was adjusted, <code>false</code>, otherwise
     */
    private boolean adjustFolderTypeOnMove(FolderObject sourceFolder, FolderObject destinationFolder, boolean recursive, int optNewOwner) throws OXException, SQLException {
        if (sourceFolder.getType() != destinationFolder.getType()) {
            int[] inheritingTypes = { FolderObject.TRASH, FolderObject.DOCUMENTS, FolderObject.PICTURES, FolderObject.MUSIC, FolderObject.VIDEOS, FolderObject.TEMPLATES };
            if (contains(inheritingTypes, destinationFolder.getType()) || contains(inheritingTypes, sourceFolder.getType())) {
                List<Integer> folderIDs;
                List<Integer> children = null;
                if (false == recursive || false == sourceFolder.hasSubfolders()) {
                    folderIDs = Collections.singletonList(I(sourceFolder.getObjectID()));
                } else {
                    folderIDs = new ArrayList<>();
                    folderIDs.add(I(sourceFolder.getObjectID()));
                    children = OXFolderSQL.getSubfolderIDs(sourceFolder.getObjectID(), readCon, ctx, true);
                    folderIDs.addAll(children);
                }
                int type = FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID == destinationFolder.getObjectID() ? FolderObject.PUBLIC : destinationFolder.getType();
                boolean result = 0 < OXFolderSQL.updateFolderType(writeCon, ctx, type, optNewOwner, folderIDs);
                if (recursive && children != null && !children.isEmpty() && FolderCacheManager.isEnabled()) {
                    Connection wc = writeCon;
                    final boolean create = (wc == null);
                    if (create) {
                        wc = DBPool.pickupWriteable(ctx);
                    }
                    try {
                        final FolderCacheManager cacheManager = FolderCacheManager.getInstance();
                        for (int i : children) {
                            cacheManager.loadFolderObject(i, ctx, wc);
                        }
                    } finally {
                        if (create && wc != null) {
                            DBPool.closeWriterSilent(ctx, wc);
                        }
                    }
                }
                return result;
            }
        }
        return false;
    }

    private boolean changeOwnerOnMove(FolderObject fo, int newParentId, int oldParentId, boolean recursive) throws OXException, SQLException {
        if (FolderObject.INFOSTORE == fo.getModule() && (newParentId > 0 && newParentId != oldParentId) && setAdminAsCreatorForPublicDriveFolder()) {
            FolderObject newParent = getFolderFromMaster(newParentId);
            boolean isPublicInfoStoreFolder = isPublicInfoStoreFolder(newParent);

            FolderObject oldParent = getFolderFromMaster(oldParentId);
            boolean wasPublicInfoStoreFolder = isPublicInfoStoreFolder(oldParent);

            if (isPublicInfoStoreFolder != wasPublicInfoStoreFolder) {
                List<Integer> folderIDs;
                List<Integer> children = null;
                if (false == recursive || false == fo.hasSubfolders()) {
                    folderIDs = Collections.singletonList(I(fo.getObjectID()));
                } else {
                    folderIDs = new ArrayList<>();
                    folderIDs.add(I(fo.getObjectID()));
                    children = OXFolderSQL.getSubfolderIDs(fo.getObjectID(), readCon, ctx, true);
                    folderIDs.addAll(children);

                }

                int newOwner = isPublicInfoStoreFolder ? ctx.getMailadmin() : newParent.getCreatedBy();
                boolean result = 0 < OXFolderSQL.updateFolderOwner(writeCon, ctx, newOwner, folderIDs);
                if (recursive && children != null && !children.isEmpty() && FolderCacheManager.isEnabled()) {
                    Connection wc = writeCon;
                    final boolean create = (wc == null);
                    if (create) {
                        wc = DBPool.pickupWriteable(ctx);
                    }
                    try {
                        for (int i : children) {
                            FolderCacheManager.getInstance().loadFolderObject(i, ctx, wc);
                        }
                    } finally {
                        if (create && wc != null) {
                            DBPool.closeWriterSilent(ctx, wc);
                        }
                    }
                }
                return result;
            }
        }
        return false;
    }

    private void processDeletedFolderThroughMove(final FolderObject folder, final CheckPermissionOnRemove checker, final long lastModified) throws OXException, SQLException {
        final int folderId = folder.getObjectID();
        final List<Integer> subflds = FolderObject.getSubfolderIds(folderId, ctx, writeCon);
        for (final Integer subfld : subflds) {
            processDeletedFolderThroughMove(getOXFolderAccess().getFolderObject(subfld.intValue()), checker, lastModified);
        }
        checker.checkPermissionsOnDelete(folder.getParentFolderID(), folderId, folder.getNonSystemPermissionsAsArray(), lastModified);
        /*
         * Now strip all system permissions
         */
        OXFolderSQL.deleteAllSystemPermission(folderId, writeCon, ctx);
    }

    private void processInsertedFolderThroughMove(final FolderObject folder, final CheckPermissionOnInsert checker, final long lastModified) throws OXException, SQLException {
        final int folderId = folder.getObjectID();
        checker.checkParentPermissions(folder.getParentFolderID(), folder.getNonSystemPermissionsAsArray(), lastModified);
        final List<Integer> subflds = FolderObject.getSubfolderIds(folderId, ctx, writeCon);
        for (final Integer subfld : subflds) {
            processInsertedFolderThroughMove(getOXFolderAccess().getFolderObject(subfld.intValue()), checker, lastModified);
        }
    }

    @Override
    public FolderObject clearFolder(final FolderObject fo, final boolean checkPermissions, final long lastModified) throws OXException {
        if (fo.getObjectID() <= 0) {
            throw OXFolderExceptionCode.INVALID_OBJECT_ID.create(I(fo.getObjectID()));
        }
        if (!fo.containsParentFolderID() || fo.getParentFolderID() <= 0) {
            /*
             * Incomplete, whereby its existence is checked
             */
            fo.setParentFolderID(getOXFolderAccess().getParentFolderID(fo.getObjectID()));
        } else {
            /*
             * Check existence
             */
            try {
                if (!OXFolderSQL.exists(fo.getObjectID(), readCon, ctx)) {
                    throw OXFolderExceptionCode.NOT_EXISTS.create(I(fo.getObjectID()), I(ctx.getContextId()));
                }
            } catch (SQLException e) {
                throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
            }
        }
        if (checkPermissions) {
            /*
             * Check permissions
             */
            final EffectivePermission p = getOXFolderAccess().getFolderPermission(fo.getObjectID(), user.getId(), userPerms);
            if (!p.isFolderVisible()) {
                if (p.getUnderlyingPermission().isFolderVisible()) {
                    throw OXFolderExceptionCode.NOT_VISIBLE.create(I(fo.getObjectID()), I(user.getId()), I(ctx.getContextId()));
                }
                throw OXFolderExceptionCode.NOT_VISIBLE.create(CATEGORY_PERMISSION_DENIED, I(fo.getObjectID()), I(user.getId()), I(ctx.getContextId()));
            }
        }
        /*
         * Check delete permission on folder's objects
         */
        if (!getOXFolderAccess().canDeleteAllObjectsInFolder(fo, session, ctx)) {
            throw OXFolderExceptionCode.NOT_ALL_OBJECTS_DELETION.create(I(user.getId()), I(fo.getObjectID()), I(ctx.getContextId()));
        }
        /*
         * Finally, delete folder content
         */
        final int module = fo.getModule();
        switch (module) {
            case FolderObject.CALENDAR:
                deleteContainedEvents(fo.getObjectID());
                break;
            case FolderObject.TASK:
                deleteContainedTasks(fo.getObjectID());
                break;
            case FolderObject.CONTACT:
                deleteContainedContacts(fo.getObjectID());
                break;
            case FolderObject.UNBOUND:
                break;
            case FolderObject.INFOSTORE:
                deleteContainedDocuments(fo.getObjectID());
                break;
            default:
                throw OXFolderExceptionCode.UNKNOWN_MODULE.create(I(module), I(ctx.getContextId()));
        }
        /*
         * delete subfolders, too, when clearing the trash folder
         */
        if (FolderObject.TRASH == fo.getType()) {
            /*
             * Gather all deletable subfolders recursively
             */
            TIntObjectMap<TIntObjectMap<?>> deleteableFolders = new TIntObjectHashMap<>();
            try {
                TIntList subfolders = OXFolderSQL.getSubfolderIDs(fo.getObjectID(), readCon, ctx);
                for (int i = 0; i < subfolders.size(); i++) {
                    deleteableFolders.putAll(gatherDeleteableFolders(subfolders.get(i), user.getId(), userPerms, StringCollection.getSqlInString(user.getId(), user.getGroups())));
                }
            } catch (SQLException e) {
                throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
            }
            /*
             * Delete subfolders
             */
            if (0 < deleteableFolders.size()) {
                deleteValidatedFolders(deleteableFolders, lastModified, fo.getType());
            }
        }
        return fo;
    }

    @Override
    public FolderObject deleteFolder(final FolderObject fo, final boolean checkPermissions, final long lastModified, boolean hardDelete, FolderPath originPath) throws OXException {
        final int folderId = fo.getObjectID();
        if (folderId <= 0) {
            throw OXFolderExceptionCode.INVALID_OBJECT_ID.create(I(fo.getObjectID()));
        }
        if (folderId < FolderObject.MIN_FOLDER_ID) {
            throw OXFolderExceptionCode.NO_SYSTEM_FOLDER_MOVE.create(I(fo.getObjectID()), I(ctx.getContextId()));
        }
        /*
         * Reload original folder
         */
        OXFolderAccess folderAccess = getOXFolderAccess();
        FolderObject folder = folderAccess.getFolderObject(folderId);
        if (checkPermissions) {
            /*
             * Check permissions
             */
            final EffectivePermission p = folder.getEffectiveUserPermission(user.getId(), userPerms);
            if (!p.isFolderVisible()) {
                if (p.getUnderlyingPermission().isFolderVisible()) {
                    throw OXFolderExceptionCode.NOT_VISIBLE.create(I(folderId), I(user.getId()), I(ctx.getContextId()));
                }
                throw OXFolderExceptionCode.NOT_VISIBLE.create(CATEGORY_PERMISSION_DENIED, I(folderId), I(user.getId()), I(ctx.getContextId()));
            }
            if (!p.isFolderAdmin()) {
                if (!p.getUnderlyingPermission().isFolderAdmin()) {
                    throw OXFolderExceptionCode.NO_ADMIN_ACCESS.create(I(user.getId()), I(folder.getObjectID()), I(ctx.getContextId()));
                }
                throw OXFolderExceptionCode.NO_ADMIN_ACCESS.create(CATEGORY_PERMISSION_DENIED, I(user.getId()), I(folder.getObjectID()), I(ctx.getContextId()));
            }
        }
        /*
         * get parent
         */
        final FolderObject parentFolder = folderAccess.getFolderObject(folder.getParentFolderID());
        /*
         * check if folder can be soft-deleted
         */
        if (false == hardDelete) {
            FolderObject trashFolder = getTrashFolder(folder.getModule());
            if (null != trashFolder) {
                /*
                 * trash folder available, check if folder already located below trash
                 */
                boolean belowTrash;
                int trashFolderID = trashFolder.getObjectID();
                if (parentFolder.getObjectID() == trashFolderID || parentFolder.getParentFolderID() == trashFolderID) {
                    belowTrash = true;
                } else {
                    FolderObject p = parentFolder;
                    while (p.getParentFolderID() != trashFolderID && FolderObject.MIN_FOLDER_ID < p.getParentFolderID()) {
                        p = folderAccess.getFolderObject(p.getParentFolderID());
                    }
                    belowTrash = p.getParentFolderID() == trashFolderID;
                }
                if (false == belowTrash) {
                    /*
                     * move to trash possible, append sequence number to folder name in case of conflicts
                     */
                    String name = folder.getFolderName();
                    try {
                        while (-1 != OXFolderSQL.lookUpFolderOnUpdate(folderId, trashFolderID, name, folder.getModule(), readCon, ctx)) {
                            name = incrementSequenceNumber(name);
                        }
                    } catch (SQLException e) {
                        throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
                    }
                    /*
                     * remove any folder-dependent entities
                     */
                    deleteDependentEntities(writeCon, folder, true);
                    /*
                     * perform move to trash
                     */
                    FolderObject toUpdate = new FolderObject(name, folderId, folder.getModule(), folder.getType(), user.getId());
                    toUpdate.setParentFolderID(trashFolderID);
                    toUpdate.setPermissions(trashFolder.getPermissions());
                    // when deleting a folder, the permissions should always be inherited from the parent trash folder
                    // in order to do so, "created by" is overridden intentionally here to not violate permission restrictions,
                    // and to prevent synthetic system permissions to get inserted implicitly
                    int options = OPTION_TRASHING;
                    options |= (user.getId() != folder.getCreatedBy() ? OPTION_OVERRIDE_CREATED_BY : OPTION_NONE);
                    return updateFolder(toUpdate, false, true, lastModified, options);
                }
            }
        }
        /*
         * perform hard delete of folder and all subfolders
         */
        final TIntObjectMap<TIntObjectMap<?>> deleteableFolders;
        try {
            deleteableFolders = gatherDeleteableFolders(folderId, user.getId(), userPerms, StringCollection.getSqlInString(user.getId(), user.getGroups()));
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        }
        /*
         * Remember folder type
         */
        final int type = getOXFolderAccess().getFolderType(folderId);
        /*
         * Delete folders
         */
        deleteValidatedFolders(deleteableFolders, lastModified, type);
        /*
         * Invalidate query caches
         */
        if (FolderQueryCacheManager.isInitialized()) {
            FolderQueryCacheManager.getInstance().invalidateContextQueries(session);
        }
        /*
         * Continue
         */
        ConditionTreeMapManagement.dropFor(ctx.getContextId());
        try {
            Connection wc = writeCon;
            final boolean create = (wc == null);
            try {
                if (create) {
                    wc = DBPool.pickupWriteable(ctx);
                }
                /*
                 * Check parent subfolder flag
                 */
                final boolean hasSubfolders = !OXFolderSQL.getSubfolderIDs(parentFolder.getObjectID(), wc, ctx).isEmpty();
                OXFolderSQL.updateSubfolderFlag(parentFolder.getObjectID(), hasSubfolders, lastModified, wc, ctx);
                /*
                 * Update cache
                 */
                if (FolderCacheManager.isEnabled() && FolderCacheManager.isInitialized()) {
                    FolderCacheManager.getInstance().removeFolderObject(parentFolder.getObjectID(), ctx);
                    FolderCacheManager.getInstance().loadFolderObject(parentFolder.getObjectID(), ctx, wc);
                }
                /*
                 * Load return value
                 */
                fo.fill(FolderObject.loadFolderObjectFromDB(folderId, ctx, wc, true, false, "del_oxfolder_tree", "del_oxfolder_permissions"));
                return fo;
            } finally {
                if (create && wc != null) {
                    DBPool.closeWriterSilent(ctx, wc);
                }
            }
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Deletes the validated folders.
     *
     * @param deleteableIDs The gathered IDs of validated folders
     * @param lastModified The last-modified time stamp
     * @param type The folder type
     * @throws OXException If deletion fails for any folder
     */
    void deleteValidatedFolders(final TIntObjectMap<TIntObjectMap<?>> deleteableIDs, final long lastModified, final int type) throws OXException {
        final TIntSet validatedFolders = new TIntHashSet(deleteableIDs.size());
        TIntObjectProcedure<TIntObjectMap<?>> procedure = new TIntObjectProcedure<TIntObjectMap<?>>() {

            @Override
            public boolean execute(int folderId, TIntObjectMap<?> hashMap) {
                if (null != hashMap) {
                    final @SuppressWarnings("unchecked") TIntObjectMap<TIntObjectMap<?>> tmp = (TIntObjectMap<TIntObjectMap<?>>) hashMap;
                    tmp.forEachEntry(this);
                }
                validatedFolders.add(folderId);
                return true;
            }
        };
        deleteableIDs.forEachEntry(procedure);
        deleteValidatedFolders(validatedFolders.toArray(), lastModified, type, false);
    }

    /**
     * Deletes any existing dependent entities (e.g. subscriptions, shares) for the supplied folder ID.
     *
     * @param wcon A "write" connection to the database
     * @param folder The deleted folder. Must be fully initialized.
     * @param handDown <code>true</code> to also remove the subscriptions of any nested subfolder, <code>false</code>,
     *            otherwise
     * @return The number of removed subscriptions
     * @throws OXException
     */
    private void deleteDependentEntities(Connection wcon, FolderObject folder, boolean handDown) throws OXException {
        if (null == wcon) {
            Connection wc = null;
            try {
                wc = DBPool.pickupWriteable(ctx);
                OXFolderDependentDeleter.folderDeleted(wc, session, folder, handDown);
            } finally {
                if (null != wc) {
                    DBPool.closeWriterSilent(ctx, wc);
                }
            }
        } else {
            OXFolderDependentDeleter.folderDeleted(wcon, session, folder, handDown);
        }
    }

    /**
     * Deletes any existing dependent entities (e.g. subscriptions, shares) for the supplied folder ID.
     *
     * @param wcon A "write" connection to the database
     * @param folders The deleted folders. Must be fully initialized.
     * @param handDown <code>true</code> to also remove the subscriptions of any nested subfolder, <code>false</code>,
     *            otherwise
     * @return The number of removed subscriptions
     * @throws OXException
     */
    private void deleteDependentEntities(Connection wcon, Collection<FolderObject> folders, boolean handDown) throws OXException {
        if (null == wcon) {
            Connection wc = null;
            try {
                wc = DBPool.pickupWriteable(ctx);
                OXFolderDependentDeleter.foldersDeleted(wc, session, folders, handDown);
            } finally {
                if (null != wc) {
                    DBPool.closeWriterSilent(ctx, wc);
                }
            }
        } else {
            OXFolderDependentDeleter.foldersDeleted(wcon, session, folders, handDown);
        }
    }

    /**
     * Increments or appends an initial sequence number to the supplied folder name to avoid conflicting names, e.g. the string
     * <code>test</code> will get enhanced to <code>test (1)</code>, while the string <code>test (3)</code> will get enhanced to
     * <code>test (4)</code>.
     *
     * @param folderName The name to increment the sequence number in
     * @return The name with an incremented or initially appended sequence number
     */
    private static String incrementSequenceNumber(final String folderName) {
        String name = folderName;
        Pattern regex = Pattern.compile("\\((\\d+)\\)\\z");
        Matcher matcher = regex.matcher(name);
        if (false == matcher.find()) {
            /*
             * append new initial sequence number
             */
            name += " (1)";
        } else if (0 == matcher.groupCount() || 0 < matcher.groupCount() && Strings.isEmpty(matcher.group(1))) {
            /*
             * append new initial sequence number
             */
            name = name.substring(0, matcher.start()) + " (1)";
        } else {
            /*
             * incremented existing sequence number
             */
            int number = 0;
            try {
                number = Integer.parseInt(matcher.group(1).trim());
            } catch (NumberFormatException e) {
                // should not get here
            }
            name = name.substring(0, matcher.start()) + '(' + String.valueOf(1 + number) + ')';
        }
        return name;
    }

    /**
     * Gets the user's trash folder for the supplied module.
     *
     * @param module The module to get the trash folder for
     * @return The folder, or <code>null</code> if no trash folder was found
     * @throws OXException
     */
    private FolderObject getTrashFolder(int module) throws OXException {
        try {
            return getOXFolderAccess().getDefaultFolder(user.getId(), module, FolderObject.TRASH);
        } catch (OXException e) {
            if (false == OXFolderExceptionCode.NO_DEFAULT_FOLDER_FOUND.equals(e)) {
                throw e;
            }
        }
        return null;
    }

    /**
     * Deletes the validated folder.
     *
     * @param folderID The folder ID
     * @param lastModified The last-modified time stamp
     * @param type The folder type
     * @throws OXException If deletion fails
     */
    @Override
    public void deleteValidatedFolder(int folderID, long lastModified, int type, boolean hardDelete) throws OXException {
        deleteValidatedFolders(new int[] { folderID }, lastModified, type , hardDelete);
    }

    /**
     * Deletes the validated folders.
     *
     * @param folderIDs The folder IDs
     * @param lastModified The last-modified time stamp
     * @param type The folder type
     * @throws OXException If deletion fails
     */
    private void deleteValidatedFolders(int[] folderIDs, long lastModified, int type, boolean hardDelete) throws OXException {
        List<FolderObject> storageFolders = new ArrayList<>(folderIDs.length);
        for (int folderID : folderIDs) {
            FolderObject storageFolder;
            try {
                storageFolder = getFolderFromMaster(folderID, false);
            } catch (OXException e) {
                if (!OXFolderExceptionCode.NOT_EXISTS.equals(e)) {
                    throw e;
                }

                // Already deleted
                storageFolder = null;
            }

            if (null != storageFolder) {
                storageFolders.add(storageFolder);
                if (hardDelete) {
                    /*
                     * Delete contained items
                     */
                    deleteContainedItems(folderID);
                    /*
                     * Call SQL delete
                     */
                    try {
                        OXFolderSQL.delOXFolder(folderID, session.getUserId(), lastModified, true, false, ctx, writeCon);
                    } catch (SQLException e) {
                        throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
                    }
                } else {
                    /*
                     * Iterate possibly listening folder delete listeners
                     */
                    for (final Iterator<FolderDeleteListenerService> iter = FolderDeleteListenerRegistry.getInstance().getDeleteListenerServices(); iter.hasNext();) {
                        final FolderDeleteListenerService next = iter.next();
                        try {
                            next.onFolderDelete(folderID, ctx);
                        } catch (OXException e) {
                            LOG.error("Folder delete listener \"{}\" failed for folder {} int context {}", next.getClass().getName(), I(folderID), I(ctx.getContextId()), e);
                            throw e;
                        }
                    }
                    /*
                     * Delete contained items
                     */
                    deleteContainedItems(folderID);
                    /*
                     * Remember values
                     */
                    final OCLPermission[] perms = getOXFolderAccess().getFolderObject(folderID).getPermissionsAsArray();
                    final int parent = getOXFolderAccess().getParentFolderID(folderID);
                    /*
                     * Call SQL delete
                     */
                    try {
                        OXFolderSQL.delWorkingOXFolder(folderID, session.getUserId(), lastModified, ctx, writeCon);
                    } catch (SQLException e) {
                        throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
                    }
                    /*
                     * Process system permissions
                     */
                    if (FolderObject.PUBLIC == type) {
                        new CheckPermissionOnRemove(session, writeCon, ctx).checkPermissionsOnDelete(parent, folderID, perms, lastModified);
                    }
                }
            }
        }

        /*
         * Remove from cache
         */
        if (FolderQueryCacheManager.isInitialized()) {
            FolderQueryCacheManager.getInstance().invalidateContextQueries(ctx.getContextId());
        }
        ConditionTreeMapManagement.dropFor(ctx.getContextId());
        if (FolderCacheManager.isEnabled() && FolderCacheManager.isInitialized()) {
            try {
                FolderCacheManager.getInstance().removeFolderObjects(folderIDs, ctx);
            } catch (OXException e) {
                LOG.error("", e);
            }
        }

        /*
         * Remove remaining links & deactivate contact collector if necessary
         */
        Connection wc = writeCon;
        boolean closeWriter = false;
        if (wc == null) {
            wc = DBPool.pickupWriteable(ctx);
            closeWriter = true;
        }
        try {
            ServerUserSetting sus = ServerUserSetting.getInstance(wc);
            Integer collectFolder = sus.getContactCollectionFolder(ctx.getContextId(), user.getId());
            for (int j = 0, k = folderIDs.length; k-- > 0; j++) {
                int folderID = folderIDs[j];
                if (null != collectFolder && folderID == collectFolder.intValue()) {
                    sus.setContactCollectOnMailAccess(ctx.getContextId(), user.getId(), false);
                    sus.setContactCollectOnMailTransport(ctx.getContextId(), user.getId(), false);
                    sus.setContactCollectionFolder(ctx.getContextId(), user.getId(), null);
                    k = 0;
                }
            }
            /*
             * Subscriptions
             */
            deleteDependentEntities(wc, storageFolders, false);
            /*
             * Propagate
             */
            if (false == hardDelete) {
                for (int folderID : folderIDs) {
                    FolderObject fo = FolderObject.loadFolderObjectFromDB(folderID, ctx, wc, true, false, "del_oxfolder_tree", "del_oxfolder_permissions");
                    try {
                        if (FolderObject.INFOSTORE == fo.getModule()) {
                            FolderObject parentFolder = FolderObject.loadFolderObjectFromDB(fo.getParentFolderID(), ctx, wc, true, false);
                            new EventClient(session).delete(fo, parentFolder, getFolderPath(fo, parentFolder, wc));
                        } else {
                            new EventClient(session).delete(fo);
                        }
                    } catch (OXException e) {
                        if (OXFolderExceptionCode.NOT_EXISTS.getNumber() == e.getCode() && OXFolderExceptionCode.NOT_EXISTS.getPrefix().equals(e.getPrefix())) {
                            // Ignore non-existent folder
                        } else {
                            LOG.warn("Delete event could not be enqueued", e);
                        }
                    }
                }
            }
        } finally {
            if (closeWriter) {
                DBPool.closeWriterSilent(ctx, wc);
            }
        }
    }

    private void deleteContainedItems(final int folderID) throws OXException {
        final int module = getOXFolderAccess().getFolderModule(folderID);
        switch (module) {
            case FolderObject.CALENDAR:
                deleteContainedEvents(folderID);
                break;
            case FolderObject.TASK:
                deleteContainedTasks(folderID);
                break;
            case FolderObject.CONTACT:
                deleteContainedContacts(folderID);
                break;
            case FolderObject.UNBOUND:
                break;
            case FolderObject.INFOSTORE:
                deleteContainedDocuments(folderID);
                break;
            default:
                throw OXFolderExceptionCode.UNKNOWN_MODULE.create(I(module), I(ctx.getContextId()));
        }
    }

    private void deleteContainedEvents(int folderId) throws OXException {
        CalendarSession calendarSession = ServerServiceRegistry.getInstance().getService(CalendarService.class, true).init(session);
        calendarSession.set(PARAMETER_CONNECTION(), writeCon);
        calendarSession.set(CalendarParameters.PARAMETER_SCHEDULING, SchedulingControl.NONE);
        calendarSession.getCalendarService().clearEvents(calendarSession, String.valueOf(folderId), CalendarUtils.DISTANT_FUTURE);
    }

    private void deleteContainedTasks(final int folderID) throws OXException {
        final Tasks tasks = Tasks.getInstance();
        if (null == writeCon) {
            Connection wc = null;
            try {
                wc = DBPool.pickupWriteable(ctx);
                tasks.deleteTasksInFolder(session, wc, folderID);
            } finally {
                if (null != wc) {
                    DBPool.closeWriterSilent(ctx, wc);
                }
            }
        } else {
            tasks.deleteTasksInFolder(session, writeCon, folderID);
        }
    }

    private void deleteContainedContacts(final int folderID) throws OXException {
        LogProperties.put(LogProperties.Name.SUBSCRIPTION_ADMIN, "true");
        try {
            ServerServiceRegistry.getInstance().getService(ContactService.class).deleteContacts(session, String.valueOf(folderID));
        } finally {
            LogProperties.remove(LogProperties.Name.SUBSCRIPTION_ADMIN);
        }
    }

    private void deleteContainedDocuments(final int folderID) throws OXException {
        final InfostoreFacade infostoreFacade;
        if (writeCon == null) {
            infostoreFacade = new EventFiringInfostoreFacadeImpl(new DBPoolProvider());
        } else {
            infostoreFacade = new EventFiringInfostoreFacadeImpl(new StaticDBPoolProvider(writeCon));
            infostoreFacade.setCommitsTransaction(false);
        }
        infostoreFacade.setTransactional(true);
        infostoreFacade.startTransaction();
        try {
            infostoreFacade.removeDocument(folderID, FileStorageFileAccess.DISTANT_FUTURE, ServerSessionAdapter.valueOf(session, ctx));
            infostoreFacade.commit();
        } catch (OXException x) {
            infostoreFacade.rollback();
            if (InfostoreExceptionCodes.CURRENTLY_LOCKED.equals(x)) {
                throw OXFolderExceptionCode.DELETE_FAILED_LOCKED_DOCUMENTS.create(x, I(folderID), I(ctx.getContextId()));
            }
            throw x;
        } catch (RuntimeException x) {
            infostoreFacade.rollback();
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(x, I(ctx.getContextId()));
        } finally {
            infostoreFacade.finish();
        }
    }

    private static final int SPECIAL_CONTACT_COLLECT_FOLDER = 0;

    /**
     * Gathers all folders which are allowed to be deleted
     */
    private TIntObjectMap<TIntObjectMap<?>> gatherDeleteableFolders(final int folderID, final int userId, final UserPermissionBits userPerms, final String permissionIDs) throws OXException, SQLException {
        final TIntObjectMap<TIntObjectMap<?>> deleteableIDs = new TIntObjectHashMap<>();
        final Integer[] specials = new Integer[1];
        // Initialize special folders that must not be deleted
        {
            Integer i = null;
            final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
            if (null != service && service.getBoolProperty("com.openexchange.contactcollector.folder.deleteDenied", false)) {
                i = ServerUserSetting.getInstance(writeCon).getContactCollectionFolder(ctx.getContextId(), userId);
            }
            specials[SPECIAL_CONTACT_COLLECT_FOLDER] = i;
        }
        gatherDeleteableSubfoldersRecursively(folderID, userId, userPerms, permissionIDs, deleteableIDs, folderID, specials);
        return deleteableIDs;
    }

    /**
     * Gathers all folders which are allowed to be deleted in a recursive manner
     * @param specials
     */
    private void gatherDeleteableSubfoldersRecursively(final int folderID, final int userId, final UserPermissionBits userPerms, final String permissionIDs, final TIntObjectMap<TIntObjectMap<?>> deleteableIDs, final int initParent, final Integer[] specials) throws OXException, SQLException {
        final FolderObject delFolder = getOXFolderAccess().getFolderObject(folderID);
        /*
         * Check if shared
         */
        if (delFolder.isShared(userId)) {
            throw OXFolderExceptionCode.NO_SHARED_FOLDER_DELETION.create(I(userId), I(folderID), I(ctx.getContextId()));
        }
        /*
         * Check if marked as default folder
         */
        if (delFolder.isDefaultFolder()) {
            throw OXFolderExceptionCode.NO_DEFAULT_FOLDER_DELETION.create(I(userId), I(folderID), I(ctx.getContextId()));
        }
        /*
         * Check user's effective permission
         */
        final EffectivePermission effectivePerm = getOXFolderAccess().getFolderPermission(folderID, userId, userPerms);
        if (!effectivePerm.isFolderVisible()) {
            if (!effectivePerm.getUnderlyingPermission().isFolderVisible()) {
                if (initParent == folderID) {
                    throw OXFolderExceptionCode.NOT_VISIBLE.create(I(folderID), I(userId), I(ctx.getContextId()));
                }
                throw OXFolderExceptionCode.HIDDEN_FOLDER_ON_DELETION.create(I(initParent), I(ctx.getContextId()), I(userId));
            }
            if (initParent == folderID) {
                throw OXFolderExceptionCode.NOT_VISIBLE.create(CATEGORY_PERMISSION_DENIED, I(folderID), I(userId), I(ctx.getContextId()));
            }
            throw OXFolderExceptionCode.HIDDEN_FOLDER_ON_DELETION.create(CATEGORY_PERMISSION_DENIED, I(initParent), I(ctx.getContextId()), I(userId));
        }
        if (!effectivePerm.isFolderAdmin()) {
            if (!effectivePerm.getUnderlyingPermission().isFolderAdmin()) {
                throw OXFolderExceptionCode.NO_ADMIN_ACCESS.create(I(userId), I(folderID), I(ctx.getContextId()));
            }
            throw OXFolderExceptionCode.NO_ADMIN_ACCESS.create(CATEGORY_PERMISSION_DENIED, I(userId), I(folderID), I(ctx.getContextId()));
        }
        /*
         * Check delete permission on folder's objects
         */
        if (!getOXFolderAccess().canDeleteAllObjectsInFolder(delFolder, session, ctx)) {
            throw OXFolderExceptionCode.NOT_ALL_OBJECTS_DELETION.create(I(userId), I(folderID), I(ctx.getContextId()));
        }
        /*
         * Check for special folder
         */
        for (final Integer special : specials) {
            if (null != special && special.intValue() == folderID) {
                throw OXFolderExceptionCode.DELETE_DENIED.create(I(folderID), I(ctx.getContextId()));
            }
        }
        /*
         * Check, if folder has subfolders
         */
        final TIntList subfolders = OXFolderSQL.getSubfolderIDs(delFolder.getObjectID(), readCon, ctx);
        if (subfolders.isEmpty()) {
            deleteableIDs.put(folderID, null);
            return;
        }
        final TIntObjectMap<TIntObjectMap<?>> subMap = new TIntObjectHashMap<>();
        final int size = subfolders.size();
        for (int i = 0; i < size; i++) {
            gatherDeleteableSubfoldersRecursively(subfolders.get(i), userId, userPerms, permissionIDs, subMap, initParent, specials);
        }
        deleteableIDs.put(folderID, subMap);
    }

    /**
     * Gets a folder's path down to the root folder, ready to be used in events.
     *
     * @param folder The folder to get the path for
     * @param parentFolder The parent folder if known, or <code>null</code> if not
     * @param connection A connection to use
     * @return The folder path
     * @throws OXException
     */
    private String[] getFolderPath(FolderObject folder, FolderObject parentFolder, Connection connection) throws OXException {
        List<String> folderPath = new ArrayList<>();
        folderPath.add(String.valueOf(folder.getObjectID()));
        int startID;
        if (null == parentFolder) {
            startID = folder.getParentFolderID();
            folderPath.add(String.valueOf(startID));
        } else {
            folderPath.add(String.valueOf(parentFolder.getObjectID()));
            startID = parentFolder.getParentFolderID();
            folderPath.add(String.valueOf(startID));
        }
        if (FolderObject.SYSTEM_ROOT_FOLDER_ID != startID) {
            try {
                List<Integer> pathToRoot = OXFolderSQL.getPathToRoot(startID, connection, ctx);
                for (Integer id : pathToRoot) {
                    folderPath.add(String.valueOf(id));
                }
            } catch (SQLException e) {
                throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
            }
        }
        return folderPath.toArray(new String[folderPath.size()]);
    }

    /**
     * This routine is called through AJAX' folder tests!
     */
    @Override
    public void cleanUpTestFolders(final int[] fuids, final Context ctx) {
        for (int fuid : fuids) {
            try {
                OXFolderSQL.hardDeleteOXFolder(fuid, ctx, null);
                ConditionTreeMapManagement.dropFor(ctx.getContextId());
                if (FolderCacheManager.isEnabled() && FolderCacheManager.isInitialized()) {
                    try {
                        FolderCacheManager.getInstance().removeFolderObject(fuid, ctx);
                    } catch (OXException e) {
                        LOG.warn("", e);
                    }
                }
            } catch (Exception e) {
                LOG.error("", e);
            }
        }
    }

    /*-
     * ----------------- STATIC HELPER METHODS ---------------------
     */

    private static Map<String, Integer> fieldMapping;

    static {
        final Map<String, Integer> fieldMapping = new HashMap<>(9);
        fieldMapping.put("fuid", I(DataObject.OBJECT_ID));
        fieldMapping.put("parent", I(FolderChildObject.FOLDER_ID));
        fieldMapping.put("fname", I(FolderObject.FOLDER_NAME));
        fieldMapping.put("module", I(FolderObject.MODULE));
        fieldMapping.put("type", I(FolderObject.TYPE));
        fieldMapping.put("creating_date", I(DataObject.CREATION_DATE));
        fieldMapping.put("created_from", I(DataObject.CREATED_BY));
        fieldMapping.put("changing_date", I(DataObject.LAST_MODIFIED));
        fieldMapping.put("changed_from", I(DataObject.MODIFIED_BY));
        OXFolderManagerImpl.fieldMapping = ImmutableMap.copyOf(fieldMapping);
    }

    private static User getUser(Session session) throws OXException {
        if (session instanceof ServerSession) {
            return ((ServerSession) session).getUser();
        }
        return UserStorage.getInstance().getUser(session.getUserId(), session.getContextId());
    }

    private static OXException handleIncorrectStringError(IncorrectStringSQLException e, Session session) throws OXException {
        String column = e.getColumn();
        if (null == column) {
            return OXFolderExceptionCode.INVALID_CHARACTER_SIMPLE.create(e);
        }

        String displayName = OXFolderUtility.column2Field(column);
        if (null == displayName) {
            return OXFolderExceptionCode.INVALID_CHARACTER.create(e, e.getIncorrectString(), e.getColumn());
        }
        if (null == session) {
            return OXFolderExceptionCode.INVALID_CHARACTER.create(e, e.getIncorrectString(), displayName);
        }

        String translatedName = StringHelper.valueOf(getUser(session).getLocale()).getString(displayName);
        OXException oxe = OXFolderExceptionCode.INVALID_CHARACTER.create(e, e.getIncorrectString(), translatedName);
        oxe.addProblematic(new SimpleIncorrectStringAttribute(fieldMapping.get(column).intValue(), e.getIncorrectString()));
        return oxe;
    }

    private static Object getFolderValue(final int folderField, final FolderObject folder) {
        if (FolderObject.FOLDER_NAME == folderField) {
            return folder.getFolderName();
        } else if (DataObject.OBJECT_ID == folderField) {
            return I(folder.getObjectID());
        } else if (FolderChildObject.FOLDER_ID == folderField) {
            return I(folder.getParentFolderID());
        } else if (FolderObject.MODULE == folderField) {
            return I(folder.getModule());
        } else if (FolderObject.TYPE == folderField) {
            return I(folder.getType());
        } else if (DataObject.CREATION_DATE == folderField) {
            return folder.getCreationDate();
        } else if (DataObject.CREATED_BY == folderField) {
            return I(folder.getCreatedBy());
        } else if (DataObject.LAST_MODIFIED == folderField) {
            return folder.getLastModified();
        } else if (DataObject.MODIFIED_BY == folderField) {
            return I(folder.getModifiedBy());
        } else {
            throw new IllegalStateException("Unknown folder field ID: " + folder);
        }
    }

    private OXException parseTruncated(final DataTruncation exc, final FolderObject folder, final String tableName) {
        final String[] fields = DBUtils.parseTruncatedFields(exc);
        final OXException.Truncated[] truncateds = new OXException.Truncated[fields.length];
        final StringBuilder sFields = new StringBuilder(fields.length << 3);
        for (int i = 0; i < fields.length; i++) {
            sFields.append(fields[i]);
            sFields.append(", ");
            final int valueLength;
            final int fieldId = fieldMapping.get(fields[i]).intValue();
            final Object tmp = getFolderValue(fieldId, folder);
            if (tmp instanceof String) {
                valueLength = Charsets.getBytes((String) tmp, Charsets.UTF_8).length;
            } else {
                valueLength = 0;
            }
            int tmp2 = -1;
            boolean closeReadCon = false;
            Connection readCon = this.readCon;
            if (readCon == null) {
                try {
                    readCon = DBPool.pickup(ctx);
                } catch (OXException e) {
                    LOG.error("A readable connection could not be fetched from pool", e);
                    return OXFolderExceptionCode.SQL_ERROR.create(exc, exc.getMessage());
                }
                closeReadCon = true;
            }
            try {
                tmp2 = DBUtils.getColumnSize(readCon, tableName, fields[i]);
            } catch (SQLException e) {
                LOG.error("", e);
                tmp2 = -1;
            } finally {
                if (closeReadCon) {
                    DBPool.closeReaderSilent(ctx, readCon);
                }
            }
            final int length = -1 == tmp2 ? 0 : tmp2;
            truncateds[i] = new OXException.Truncated() {

                @Override
                public int getId() {
                    return fieldId;
                }

                @Override
                public int getLength() {
                    return valueLength;
                }

                @Override
                public int getMaxSize() {
                    return length;
                }
            };
        }
        sFields.setLength(sFields.length() - 2);
        final OXException fe;
        if (truncateds.length > 0) {
            final OXException.Truncated truncated = truncateds[0];
            if (1 == truncateds.length && FolderObject.FOLDER_NAME == truncated.getId()) {
                fe =  OXFolderExceptionCode.TRUNCATED_FOLDERNAME.create(exc,
                    sFields.toString(),
                    I(truncated.getMaxSize()),
                    I(truncated.getLength()));
            } else {
                fe = OXFolderExceptionCode.TRUNCATED.create(exc,
                    sFields.toString(),
                    I(truncated.getMaxSize()),
                    I(truncated.getLength()));
            }
        } else {
            fe = OXFolderExceptionCode.TRUNCATED.create(exc,
                sFields.toString(),
                I(0),
                I(0));
        }
        for (final OXException.Truncated truncated : truncateds) {
            fe.addProblematic(truncated);
        }
        return fe;
    }

    private static final class ProcedureFailedException extends RuntimeException {

        private static final long serialVersionUID = 1821041261492515385L;

        public ProcedureFailedException(final Throwable cause) {
            super(cause);
        }

    }

    @SuppressWarnings("deprecation")
    private static int getPublishedMailAttachmentsFolder(final Session session) {
        if (null == session) {
            return -1;
        }
        final Integer i = (Integer) session.getParameter(MailSessionParameterNames.getParamPublishingInfostoreFolderID());
        return null == i ? -1 : i.intValue();
    }

    @Override
    public void cleanLocksForFolder(FolderObject folder, int[] userIds) throws OXException {
        try {
            OXFolderSQL.cleanLocksForFolder(folder.getObjectID(), userIds, this.writeCon, ctx);
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Checks if the folder contains too many permissions.
     *
     * @param folder The folder to check
     * @param previousFolder The optional previous folder in case of an update
     * @throws OXException in case the folder contains too many permissions
     */
    protected void checkFolderPermissions(FolderObject folder, Optional<FolderObject> previousFolder) throws OXException {
        if (folder == null) {
            return;
        }
        List<OCLPermission> perms = folder.getPermissions();
        if (perms != null) {
            int max = i((Integer) MAX_FOLDER_PERMISSIONS.getDefaultValue());
            LeanConfigurationService lean = ServerServiceRegistry.getServize(LeanConfigurationService.class, true);
            if (lean == null) {
                LOG.warn("Missing {} service. Falling back to default value of {}:{}", LeanConfigurationService.class.getSimpleName(), MAX_FOLDER_PERMISSIONS.getFQPropertyName(), I(max));
            } else {
                max = lean.getIntProperty(user.getId(), ctx.getContextId(), MAX_FOLDER_PERMISSIONS);
            }
            if (max > 0 && perms.size() > max) {
                if (previousFolder.isPresent() && previousFolder.get().getPermissions() != null && previousFolder.get().getPermissions().size() >= perms.size()) {
                    LOG.debug("Updated folder with id {} in context {} contains too many permissions but accept it anyway, because the overall number didn't increase.", I(previousFolder.get().getObjectID()), I(ctx.getContextId()));
                    return;
                }
                throw OXFolderExceptionCode.TOO_MANY_PERMISSIONS.create();
            }
        }
    }

}
