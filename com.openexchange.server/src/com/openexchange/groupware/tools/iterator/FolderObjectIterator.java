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

package com.openexchange.groupware.tools.iterator;

import static com.openexchange.database.Databases.closeSQLStuff;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.json.JSONException;
import com.openexchange.cache.impl.FolderCacheManager;
import com.openexchange.caching.ElementAttributes;
import com.openexchange.configuration.ServerConfig;
import com.openexchange.configuration.ServerConfig.Property;
import com.openexchange.databaseold.Database;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.BoolReference;
import com.openexchange.java.Streams;
import com.openexchange.server.impl.DBPool;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIteratorExceptionCodes;
import com.openexchange.tools.oxfolder.OXFolderProperties;
import com.openexchange.tools.oxfolder.OXFolderUtility;
import com.openexchange.tools.oxfolder.permissionLoader.PermissionLoader;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * {@link FolderObjectIterator} - A {@link SearchIterator} especially for instances of {@link FolderObject}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class FolderObjectIterator implements SearchIterator<FolderObject> {

    protected static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FolderObjectIterator.class);

    /**
     * The empty folder iterator
     */
    public static final FolderObjectIterator EMPTY_FOLDER_ITERATOR = new FolderObjectIterator() {

        @Override
        public boolean hasNext() throws com.openexchange.exception.OXException {
            return false;
        }

        @Override
        public FolderObject next() throws com.openexchange.exception.OXException {
            return null;
        }

        @Override
        public void close() {
            // Nothing to close
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        public boolean hasSize() {
            return true;
        }

        @Override
        public boolean hasWarnings() {
            return false;
        }

        @Override
        public com.openexchange.exception.OXException[] getWarnings() {
            return null;
        }

        @Override
        public void addWarning(final com.openexchange.exception.OXException warning) {
            // Nothing to add
        }
    };

    private final boolean dbGrouping = OXFolderProperties.isEnableDBGrouping();

    private FolderCacheManager cache;

    private final Queue<FolderObject> prefetchQueue;

    private boolean isClosed;

    private final boolean closeCon;

    private final TIntSet folderIds;

    private FolderObject next;

    private Statement stmt;

    private ResultSet rs;

    private Connection readCon;

    private final Context ctx;

    private ElementAttributes attribs;

    private final boolean resideInCache;

    private final List<OXException> warnings;

    private FolderObject future;

    private final PermissionLoader permissionLoader;

    private static final String[] selectFields = {
        "fuid", "parent", "fname", "module", "type", "creating_date", "created_from", "changing_date", "changed_from", "permission_flag",
        "subfolder_flag", "default_flag", "meta" };

    /**
     * Gets all necessary fields in right order to be used in an SQL <i>SELECT</i> statement needed to create instances of
     * {@link FolderObject}.
     *
     * @param tableAlias The table alias used throughout corresponding SQL <i>SELECT</i> statement or <code>null</code> if no alias used.
     * @return All necessary fields in right order to be used in an SQL <i>SELECT</i> statement
     */
    public static final String getFieldsForSQL(final String tableAlias) {
        final StringBuilder fields = new StringBuilder(128);
        final String delim = ", ";
        if (tableAlias != null) {
            final String prefix = fields.append(tableAlias).append('.').toString();
            fields.setLength(0);
            fields.append(prefix).append(selectFields[0]);
            for (int i = 1; i < selectFields.length; i++) {
                fields.append(delim).append(prefix).append(selectFields[i]);
            }
        } else {
            fields.append(selectFields[0]);
            for (int i = 1; i < selectFields.length; i++) {
                fields.append(delim).append(selectFields[i]);
            }
        }
        return fields.toString();
    }

    private static final String[] selectFieldsPerm = { "permission_id", "fp", "orp", "owp", "odp", "admin_flag", "group_flag", "`system`", "type", "sharedParentFolder" };

    /**
     * Gets all necessary fields in right order to be used in an SQL <i>SELECT</i> statement needed to create instances of
     * {@link FolderObject} with permissions applied.
     *
     * @param folderAlias The table alias for folder used throughout corresponding SQL <i>SELECT</i> statement or <code>null</code> if no
     *            alias used.
     * @param permAlias The table alias for permissions used throughout corresponding SQL <i>SELECT</i> statement or <code>null</code> if no
     *            alias used.
     * @return All necessary fields in right order to be used in an SQL <i>SELECT</i> statement
     */
    public static final String getFieldsForSQLWithPermissions(final String folderAlias, final String permAlias) {
        final StringBuilder fields = new StringBuilder(256);
        final String delim = ", ";
        if (permAlias != null) {
            final String prefix = fields.append(permAlias).append('.').toString();
            fields.setLength(0);
            fields.append(prefix).append(selectFieldsPerm[0]);
            for (int i = 1; i < selectFieldsPerm.length; i++) {
                fields.append(delim).append(prefix).append(selectFieldsPerm[i]);
            }
        } else {
            fields.append(selectFieldsPerm[0]);
            for (int i = 1; i < selectFieldsPerm.length; i++) {
                fields.append(delim).append(selectFieldsPerm[i]);
            }
        }
        final String permFields = fields.toString();
        fields.setLength(0);
        /*
         * Now folder fields
         */
        if (folderAlias != null) {
            final String prefix = fields.append(folderAlias).append('.').toString();
            fields.setLength(0);
            fields.append(prefix).append(selectFields[0]);
            for (int i = 1; i < selectFields.length; i++) {
                fields.append(delim).append(prefix).append(selectFields[i]);
            }
        } else {
            fields.append(selectFields[0]);
            for (int i = 1; i < selectFields.length; i++) {
                fields.append(delim).append(selectFields[i]);
            }
        }
        /*
         * Append permission fields & return
         */
        return fields.append(delim).append(permFields).toString();
    }

    /**
     * Initializes a new {@link FolderObjectIterator}
     */
    FolderObjectIterator() {
        super();
        FolderCacheManager manager;
        try {
            manager = FolderCacheManager.isInitialized() ? FolderCacheManager.getInstance() : null;
        } catch (@SuppressWarnings("unused") OXException e) {
            manager = null;
        }
        cache = manager;
        closeCon = false;
        resideInCache = false;
        ctx = null;
        prefetchQueue = null;
        folderIds = null;
        warnings = new ArrayList<OXException>(2);
        permissionLoader = null;
    }

    /**
     * Initializes a new {@link FolderObjectIterator} from specified collection.
     *
     * @param col The collection containing instances of {@link FolderObject}
     * @param resideInCache If objects shall reside in cache permanently or shall be removed according to cache policy
     */
    public FolderObjectIterator(final Collection<FolderObject> col, final boolean resideInCache) {
        super();
        FolderCacheManager manager;
        try {
            manager = FolderCacheManager.isInitialized() ? FolderCacheManager.getInstance() : null;
        } catch (@SuppressWarnings("unused") OXException e) {
            manager = null;
        }
        cache = manager;
        folderIds = null;
        warnings = new ArrayList<OXException>(2);
        rs = null;
        stmt = null;
        ctx = null;
        closeCon = false;
        this.resideInCache = resideInCache;
        if ((col == null) || col.isEmpty()) {
            next = null;
            prefetchQueue = null;
        } else {
            prefetchQueue = new LinkedList<FolderObject>(col);
            next = prefetchQueue.poll();
        }
        permissionLoader = null;
    }

    /**
     * Initializes a new {@link FolderObjectIterator}
     *
     * @param rs The result set providing selected folder data
     * @param stmt The fired statement (to release all resources on iterator end)
     * @param resideInCache If objects shall reside in cache permanently or shall be removed according to cache policy
     * @param ctx The context
     * @param readCon A connection holding at least read capability
     * @param closeCon Whether to close given connection or not
     * @throws OXException If instantiation fails.
     */
    public FolderObjectIterator(final ResultSet rs, final Statement stmt, final boolean resideInCache, final Context ctx, final Connection readCon, final boolean closeCon) throws OXException {
        super();
        if (dbGrouping) {
            folderIds = null;
        } else {
            folderIds = new TIntHashSet();
        }
        FolderCacheManager manager;
        try {
            manager = FolderCacheManager.isInitialized() && FolderCacheManager.isEnabled() ? FolderCacheManager.getInstance() : null;
        } catch (@SuppressWarnings("unused") OXException e) {
            manager = null;
        }
        cache = manager;
        warnings = new ArrayList<OXException>(2);
        this.rs = rs;
        this.stmt = stmt;
        this.readCon = readCon;
        this.ctx = ctx;
        this.closeCon = closeCon;
        this.resideInCache = resideInCache;
        permissionLoader = new PermissionLoader();
        /*
         * Set next to first result set entry
         */
        final boolean prefetchEnabled = ServerConfig.getBoolean(Property.PrefetchEnabled);
        try {
            if (this.rs.next()) {
                next = createFolderObjectFromSelectedEntry(true, null);
            } else if (!prefetchEnabled) {
                closeResources();
            }
        } catch (SQLException e) {
            throw SearchIteratorExceptionCodes.SQL_ERROR.create(e, e.getMessage()).setPrefix("FLD");
        }
        if (prefetchEnabled) {
            prefetchQueue = new LinkedList<FolderObject>();
            /*
             * ResultSet prefetch is enabled. Fill iterator with whole ResultSet's content
             */
            try {
                BoolReference fetchedFromCache = new BoolReference(false);
                TIntList tmp = new TIntArrayList();
                while (this.rs.next()) {
                    FolderObject fo = createFolderObjectFromSelectedEntry(false, fetchedFromCache);
                    while ((fo == null) && rs.next()) {
                        fo = createFolderObjectFromSelectedEntry(false, fetchedFromCache);
                    }
                    if (null != fo) {
                        prefetchQueue.offer(fo);
                        if (false == fetchedFromCache.getValue()) {
                            // Need to load permissions since NOT fetched from cache
                            tmp.add(fo.getObjectID());
                        }
                    }
                }
                if (!tmp.isEmpty()) {
                    permissionLoader.submitPermissionsFor(ctx.getContextId(), tmp.toArray());
                }
                if (future != null) {
                    prefetchQueue.offer(future);
                    future = null;
                }
            } catch (SQLException e) {
                throw SearchIteratorExceptionCodes.SQL_ERROR.create(e, e.getMessage()).setPrefix("FLD");
            } finally {
                closeResources();
            }
        } else {
            prefetchQueue = null;
        }
    }

    /**
     * Releases associated cache
     *
     * @return This iterator with cache released
     */
    public FolderObjectIterator releaseCache() {
        cache = null;
        return this;
    }

    private final ElementAttributes getEternalAttributes() throws OXException {
        ElementAttributes attribs = this.attribs;
        if (attribs == null) {
            attribs = cache.getDefaultFolderObjectAttributes();
            if (null == attribs) {
                return null;
            }

            attribs.setIdleTime(-1); // eternal
            attribs.setMaxLifeSeconds(-1); // eternal
            attribs.setIsEternal(true);
            this.attribs = attribs;
        }
        return attribs.copy();
    }

    /**
     * @return a <code>FolderObject</code> from current <code>ResultSet.next()</code> data
     */
    private final FolderObject createFolderObjectFromSelectedEntry(boolean submitPermissions) throws SQLException, OXException {
        return createFolderObjectFromSelectedEntry(submitPermissions, null);
    }

    /**
     * @return a <code>FolderObject</code> from current <code>ResultSet.next()</code> data
     */
    private final FolderObject createFolderObjectFromSelectedEntry(boolean submitPermissions, BoolReference fetchedFromCache) throws SQLException, OXException {
        // fname, fuid, module, type, creator
        if (null != fetchedFromCache) {
            fetchedFromCache.setValue(false);
        }
        final int folderId = rs.getInt(1);
        final FolderObject fo;
        {
            if (!dbGrouping) {
                if (folderIds.contains(folderId)) {
                    return null;
                }
                folderIds.add(folderId);
            }
            /*
             * Look up cache
             */
            if (null != cache) {
                final FolderObject fld = cache.getFolderObject(folderId, ctx);
                if (fld != null) {
                    if (null != fetchedFromCache) {
                        fetchedFromCache.setValue(true);
                    }
                    return fld;
                }
            }
            /*
             * Not in cache; create from read data
             */
            fo = createNewFolderObject(folderId);
            /*
             * Read & set permissions
             */
            if (submitPermissions) {
                if (null == permissionLoader) {
                    fo.setPermissionsAsArray(loadFolderPermissions(folderId, ctx.getContextId(), readCon));
                } else {
                    permissionLoader.submitPermissionsFor(ctx.getContextId(), folderId);
                }
            }
        }
        return fo;
    }

    private FolderObject createNewFolderObject(final int folderId) throws SQLException {
        final FolderObject fo = new FolderObject(rs.getString(3), folderId, rs.getInt(4), rs.getInt(5), rs.getInt(7));
        fo.setParentFolderID(rs.getInt(2)); // parent
        long tStmp = rs.getLong(6); // creating_date
        if (rs.wasNull()) {
            fo.setCreationDate(new Date());
        } else {
            fo.setCreationDate(new Date(tStmp));
        }
        fo.setCreatedBy(rs.getInt(7)); // created_from
        tStmp = rs.getLong(8); // changing_date
        if (rs.wasNull()) {
            fo.setLastModified(new Date());
        } else {
            fo.setLastModified(new Date(tStmp));
        }
        fo.setModifiedBy(rs.getInt(9)); // changed_from
        fo.setPermissionFlag(rs.getInt(10));
        int subfolder = rs.getInt(11);
        if (rs.wasNull()) {
            subfolder = 0;
        }
        fo.setSubfolderFlag(subfolder > 0);
        int defaultFolder = rs.getInt(12);
        if (rs.wasNull()) {
            defaultFolder = 0;
        }
        fo.setDefaultFolder(defaultFolder > 0);
        InputStream jsonBlobStream = rs.getBinaryStream(13);
        if (!rs.wasNull() && null != jsonBlobStream) {
            try {
                fo.setMeta(OXFolderUtility.deserializeMeta(jsonBlobStream));
            } catch (JSONException e) {
                throw new SQLException(e);
            } finally {
                Streams.close(jsonBlobStream);
            }
        }
        return fo;
    }

    private final void closeResources() throws OXException {
        OXException error = null;
        /*
         * Close ResultSet
         */
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOG.error("", e);
                error = SearchIteratorExceptionCodes.SQL_ERROR.create(e, e.getMessage()).setPrefix("FLD");
            }
            rs = null;
        }
        /*
         * Close Statement
         */
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                LOG.error("", e);
                if (error == null) {
                    error = SearchIteratorExceptionCodes.SQL_ERROR.create(e, e.getMessage()).setPrefix("FLD");
                }
            }
            stmt = null;
        }
        /*
         * Close connection
         */
        if (closeCon && (readCon != null)) {
            DBPool.push(ctx, readCon);
            readCon = null;
        }
        if (error != null) {
            throw error;
        }
    }

    @Override
    public boolean hasNext() throws com.openexchange.exception.OXException {
        if (isClosed) {
            return false;
        }
        return next != null;
    }

    @Override
    public FolderObject next() throws OXException {
        if (isClosed) {
            throw SearchIteratorExceptionCodes.CLOSED.create().setPrefix("FLD");
        }
        try {
            final FolderObject retval = prepareFolderObject(next);
            next = null;
            if (prefetchQueue == null) {
                /*
                 * Select next from underlying ResultSet
                 */
                if (rs.next()) {
                    next = createFolderObjectFromSelectedEntry(true);
                    while ((next == null) && rs.next()) {
                        next = createFolderObjectFromSelectedEntry(true);
                    }
                    if (next == null) {
                        close();
                    }
                } else {
                    next = future;
                    close();
                }
            } else {
                /*
                 * Select next from queue
                 */
                if (!prefetchQueue.isEmpty()) {
                    next = prefetchQueue.poll();
                    while ((next == null) && !prefetchQueue.isEmpty()) {
                        next = prefetchQueue.poll();
                    }
                }
            }
            return retval;
        } catch (SQLException e) {
            throw SearchIteratorExceptionCodes.SQL_ERROR.create(e, e.getMessage()).setPrefix("FLD");
        }
    }

    private FolderObject prepareFolderObject(final FolderObject fo) throws OXException {
        if (null == fo) {
            return null;
        }
        final int folderId = fo.getObjectID();
        if (!fo.containsPermissions()) {
            /*
             * No permissions set, yet
             */
            final OCLPermission[] permissions = null == permissionLoader ? null : permissionLoader.pollPermissions(folderId, ctx.getContextId());
            fo.setPermissionsAsArray(null == permissions ? getFolderPermissions(folderId) : permissions);
        }
        /*
         * Determine if folder object should be put into cache or not
         */
        if (null != ctx && null != cache) {
            try {
                cache.putIfAbsent(fo, ctx, resideInCache ? getEternalAttributes() : null);
            } catch (OXException e) {
                LOG.error("", e);
            }
        }
        /*
         * Return prepared folder object
         */
        return fo;
    }

    private OCLPermission[] getFolderPermissions(final int folderId) throws OXException {
        Connection con = readCon;
        if (null == con) {
            con = Database.get(ctx, false);
            try {
                return loadFolderPermissions(folderId, ctx.getContextId(), con);
            } finally {
                Database.back(ctx, false, con);
            }
        }
        /*
         * readCon was not null
         */
        return loadFolderPermissions(folderId, ctx.getContextId(), con);
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        next = null;
        try {
            closeResources();
        } catch (OXException e) {
            LOG.error("", e);
        }
        /*
         * Close other stuff
         */
        if (null != permissionLoader) {
            permissionLoader.stop();
        }
        if (null != prefetchQueue) {
            prefetchQueue.clear();
        }
        if (null != folderIds) {
            folderIds.clear();
        }
        isClosed = true;
    }

    @Override
    public int size() {
        if (prefetchQueue != null) {
            return prefetchQueue.size() + (next == null ? 0 : 1);
        }
        return -1;
    }

    public boolean hasSize() {
        /*
         * Size can be predicted if prefetch queue is not null
         */
        return (prefetchQueue != null);
    }

    @Override
    public void addWarning(final com.openexchange.exception.OXException warning) {
        warnings.add(warning);
    }

    @Override
    public com.openexchange.exception.OXException[] getWarnings() {
        return warnings.isEmpty() ? null : warnings.toArray(new OXException[warnings.size()]);
    }

    @Override
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Creates a <code>java.util.Queue</code> containing all iterator's elements. All resources are closed immediately.
     *
     * @return iterator's content backed up by a <code>java.util.Queue</code>
     * @throws OXException if any error occurs
     */
    public Queue<FolderObject> asQueue() throws OXException {
        return asLinkedList();
    }

    /**
     * Creates a <code>java.util.List</code> containing all iterator's elements. All resources are closed immediately.
     *
     * @return iterator's content backed up by a <code>java.util.List</code>
     * @throws OXException if any error occurs
     */
    public List<FolderObject> asList() throws OXException {
        return asLinkedList();
    }

    private LinkedList<FolderObject> asLinkedList() throws OXException {
        final LinkedList<FolderObject> retval = new LinkedList<FolderObject>();
        if (isClosed) {
            return retval;
        }
        try {
            if (next == null) {
                return retval;
            }
            retval.add(prepareFolderObject(next));
            if (prefetchQueue != null) {
                for (FolderObject fo; (fo = prefetchQueue.poll()) != null;) {
                    retval.add(prepareFolderObject(fo));
                }
                return retval;
            }
            while (rs.next()) {
                FolderObject fo = createFolderObjectFromSelectedEntry(true);
                while ((fo == null) && rs.next()) {
                    fo = createFolderObjectFromSelectedEntry(true);
                }
                if (fo != null) {
                    retval.offer(prepareFolderObject(fo));
                }
            }
            return retval;
        } catch (SQLException e) {
            throw SearchIteratorExceptionCodes.SQL_ERROR.create(e, e.getMessage()).setPrefix("FLD");
        } finally {
            next = null;
            try {
                closeResources();
            } catch (OXException e) {
                LOG.error("", e);
            }
            /*
             * Close other stuff
             */
            if (null != permissionLoader) {
                permissionLoader.stop();
            }
            if (null != prefetchQueue) {
                prefetchQueue.clear();
            }
            if (null != folderIds) {
                folderIds.clear();
            }
            isClosed = true;
        }
    }

    private static final String SQL_LOAD_P =
        "SELECT permission_id, fp, orp, owp, odp, admin_flag, group_flag, `system`, type, sharedParentFolder FROM oxfolder_permissions WHERE cid = ? AND fuid = ?";

    private static final String SQL_LOAD_P_BACKUP =
        "SELECT permission_id, fp, orp, owp, odp, admin_flag, group_flag, `system`, type, sharedParentFolder FROM del_oxfolder_permissions WHERE cid = ? AND fuid = ?";

    private static final OCLPermission[] loadFolderPermissions(final int folderId, final int cid, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(SQL_LOAD_P);
            stmt.setInt(1, cid);
            stmt.setInt(2, folderId);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                /*
                 * Retry with backup table
                 */
                closeSQLStuff(rs, stmt);
                stmt = con.prepareStatement(SQL_LOAD_P_BACKUP);
                stmt.setInt(1, cid);
                stmt.setInt(2, folderId);
                rs = stmt.executeQuery();
                if (!rs.next()) {
                    /*
                     * Empty result set
                     */
                    return new OCLPermission[0];
                }
            }
            final ArrayList<OCLPermission> ret = new ArrayList<OCLPermission>(8);
            do {
                final OCLPermission p = new OCLPermission();
                p.setEntity(rs.getInt(1)); // Entity
                p.setAllPermission(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)); // fp, orp, owp, and odp
                p.setFolderAdmin(rs.getInt(6) > 0 ? true : false); // admin_flag
                p.setGroupPermission(rs.getInt(7) > 0 ? true : false); // group_flag
                p.setSystem(rs.getInt(8)); // system
                p.setType(FolderPermissionType.getType(rs.getInt(9)));
                int legator = rs.getInt(10);
                p.setPermissionLegator(legator == 0 ? null : String.valueOf(legator)); // permission legator
                ret.add(p);
            } while (rs.next());
            return ret.toArray(new OCLPermission[ret.size()]);
        } catch (SQLException e) {
            throw SearchIteratorExceptionCodes.SQL_ERROR.create(e, e.getMessage()).setPrefix("FLD");
        } finally {
            closeSQLStuff(rs, stmt);
        }
    }

}
