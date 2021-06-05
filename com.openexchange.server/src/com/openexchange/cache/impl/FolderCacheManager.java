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

package com.openexchange.cache.impl;

import static com.openexchange.java.Autoboxing.I;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import com.openexchange.ajax.fields.DataFields;
import com.openexchange.caching.Cache;
import com.openexchange.caching.CacheKey;
import com.openexchange.caching.CacheService;
import com.openexchange.caching.ElementAttributes;
import com.openexchange.database.AfterCommitDatabaseConnectionListener;
import com.openexchange.database.DatabaseConnectionListener;
import com.openexchange.database.DatabaseConnectionListenerAnnotatable;
import com.openexchange.database.Databases;
import com.openexchange.databaseold.Database;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.cache.memory.FolderMapManagement;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.util.Tools;
import com.openexchange.lock.LockService;
import com.openexchange.log.LogProperties;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.tools.oxfolder.OXFolderExceptionCode;
import com.openexchange.tools.oxfolder.OXFolderProperties;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * {@link FolderCacheManager} - Holds a cache for instances of {@link FolderObject}
 * <p>
 * <b>NOTE:</b> Only cloned versions of {@link FolderObject} instances are put into or received from cache. That prevents the danger of
 * further working on and therefore changing cached instances.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class FolderCacheManager {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FolderCacheManager.class);

    private static final String REGION_NAME = "OXFolderCache";

    private static volatile FolderCacheManager instance;

    /**
     * Checks if folder cache has been initialized.
     *
     * @return <code>true</code> if folder cache has been initialized; otherwise <code>false</code>
     */
    public static boolean isInitialized() {
        return (instance != null);
    }

    /**
     * Checks if folder cache is enabled (through configuration).
     *
     * @return <code>true</code> if folder cache is enabled; otherwise <code>false</code>
     */
    public static boolean isEnabled() {
        return OXFolderProperties.isEnableFolderCache();
    }

    /**
     * Initializes the singleton instance of folder cache {@link FolderCacheManager manager}.
     *
     * @throws OXException If initialization fails
     */
    public static void initInstance() throws OXException {
        if (instance == null) {
            synchronized (FolderCacheManager.class) {
                if (instance == null) {
                    instance = new FolderCacheManager();
                }
            }
        }
    }

    /**
     * Gets the singleton instance of folder cache {@link FolderCacheManager manager}.
     *
     * @return The singleton instance of folder cache {@link FolderCacheManager manager}.
     * @throws OXException If initialization fails
     */
    public static FolderCacheManager getInstance() throws OXException {
        if (!OXFolderProperties.isEnableFolderCache()) {
            throw OXFolderExceptionCode.CACHE_NOT_ENABLED.create();
        }
        if (instance == null) {
            synchronized (FolderCacheManager.class) {
                if (instance == null) {
                    instance = new FolderCacheManager();
                }
            }
        }
        return instance;
    }

    /**
     * Releases the singleton instance of folder cache {@link FolderCacheManager manager}.
     */
    public static void releaseInstance() {
        if (instance != null) {
            synchronized (FolderCacheManager.class) {
                if (instance != null) {
                    instance = null;

                    CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
                    if (null != cacheService) {
                        try {
                            cacheService.freeCache(REGION_NAME);
                        } catch (OXException e) {
                            LOG.error("", e);
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    private volatile Cache folderCache;

    private final Lock cacheLock;

    /**
     * Initializes a new {@link FolderCacheManager}.
     *
     * @throws OXException If initialization fails
     */
    private FolderCacheManager() throws OXException {
        super();
        cacheLock = new ReentrantLock();
        initCache();
    }

    /**
     * Initializes cache reference.
     *
     * @throws OXException If initializing the cache reference fails
     */
    public void initCache() throws OXException {
        if (folderCache != null) {
            return;
        }
        folderCache = ServerServiceRegistry.getInstance().getService(CacheService.class).getCache(REGION_NAME);
    }

    /**
     * Releases cache reference.
     *
     * @throws OXException If clearing cache fails
     */
    public void releaseCache() throws OXException {
        final Cache folderCache = this.folderCache;
        if (folderCache == null) {
            return;
        }
        folderCache.clear();
        this.folderCache = null;
    }

    CacheKey getCacheKeyUsing(int cid, int objectId, Cache folderCache) {
        return folderCache.newCacheKey(cid, objectId);
    }

    public void clearFor(final Context ctx, final boolean async) {
        Runnable task = new Runnable() {

            @Override
            public void run() {
                int contextId = ctx.getContextId();
                Connection con = null;
                PreparedStatement stmt = null;
                ResultSet rs = null;
                try {
                    con = Database.get(contextId, false);
                    stmt = con.prepareStatement("SELECT fuid FROM oxfolder_tree WHERE cid=?");
                    stmt.setInt(1, contextId);
                    rs = stmt.executeQuery();
                    final TIntSet folderIds = new TIntHashSet(1024);
                    while (rs.next()) {
                        folderIds.add(rs.getInt(1));
                    }
                    Databases.closeSQLStuff(rs, stmt);
                    stmt = con.prepareStatement("SELECT fuid FROM del_oxfolder_tree WHERE cid=?");
                    stmt.setInt(1, contextId);
                    rs = stmt.executeQuery();
                    while (rs.next()) {
                        folderIds.add(rs.getInt(1));
                    }
                    // Release resources
                    Databases.closeSQLStuff(rs, stmt);
                    rs = null;
                    stmt = null;
                    Database.back(contextId, false, con);
                    con = null;
                    // Continue
                    removeFolderObjects(folderIds.toArray(), ctx);
                } catch (Exception x) {
                    // Ignore
                } finally {
                    Databases.closeSQLStuff(rs, stmt);
                    if (null != con) {
                        Database.back(contextId, false, con);
                    }
                }
            }
        };
        if (async && ThreadPools.getThreadPool() != null) {
            ThreadPools.getThreadPool().submit(ThreadPools.task(task));
        } else {
            task.run();
        }
    }

    /**
     * Fetches <code>FolderObject</code> which matches given object id. If none found or <code>fromCache</code> is not set the folder will
     * be loaded from underlying database store and automatically put into cache.
     * <p>
     * <b>NOTE:</b> This method returns a clone of cached <code>FolderObject</code> instance. Thus any modifications made to the referenced
     * object will not affect cached version
     *
     * @throws OXException If a caching error occurs
     */
    public FolderObject getFolderObject(int objectId, boolean fromCache, Context ctx, Connection readCon) throws OXException {
        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            throw OXFolderExceptionCode.CACHE_NOT_ENABLED.create();
        }

        CacheKey cacheKey = getCacheKeyUsing(ctx.getContextId(), objectId, folderCache);
        if (fromCache) {
            // Look-up cache
            Object object = folderCache.get(cacheKey);
            if (object instanceof FolderObject) {
                return ((FolderObject) object).clone();
            }

            // Conditional put into cache: Put only if absent.
            if (null == readCon) {
                // This will save one folderCache.get call additionally, it will have the same effect as the later called putFolderObject(fo, ctx, false == fromCache, null);
                FolderObject cloned = putFolderObject(new LoadingFolderProvider(objectId, ctx, null), ctx, false, null, true);
                if (null != cloned) {
                    return cloned;
                }
            } else {
                putIfAbsentInternal(new LoadingFolderProvider(objectId, ctx, readCon), ctx, null, cacheKey);
            }
        } else {
            // Forced put into cache: Always put.
            putFolderObject(loadFolderObjectInternal(objectId, ctx, readCon), ctx, true, null);
        }

        // Return object
        Object object = folderCache.get(cacheKey);
        if (object instanceof FolderObject) {
            return ((FolderObject) object).clone();
        }

        // It should be nearly impossible to end at this point but to be sure to always return a working FolderObject, the following lines are necessary
        FolderObject fo = loadFolderObjectInternal(objectId, ctx, readCon);
        putFolderObject(fo, ctx, false == fromCache, null);
        return fo.clone();
    }

    /**
     * Fetches <code>FolderObject</code> which matches given object id.
     * <p>
     * <b>NOTE:</b> This method returns a clone of cached <code>FolderObject</code> instance. Thus any modifications made to the referenced
     * object will not affect cached version
     *
     * @return The matching <code>FolderObject</code> instance else <code>null</code>
     */
    public FolderObject getFolderObject(int objectId, Context ctx) {
        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            return null;
        }

        Object tmp = folderCache.get(getCacheKeyUsing(ctx.getContextId(), objectId, folderCache));
        if (tmp instanceof FolderObject) {
            return ((FolderObject) tmp).clone();
        }
        return null;
    }

    /**
     * <p>
     * Fetches <code>FolderObject</code>s which matches given object identifiers.
     * </p>
     * <p>
     * <b>NOTE:</b> This method returns a clone of cached <code>FolderObject</code> instances. Thus any modifications made to the referenced
     * objects will not affect cached versions
     * </p>
     *
     * @return The matching <code>FolderObject</code> instances else an empty list
     */
    public List<FolderObject> getTrimedFolderObjects(int[] objectIds, Context ctx) {
        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            return Collections.emptyList();
        }

        List<FolderObject> ret = new ArrayList<FolderObject>(objectIds.length);
        int contextId = ctx.getContextId();
        for (int objectId : objectIds) {
            Object tmp = folderCache.get(getCacheKeyUsing(contextId, objectId, folderCache));
            // Refresher uses Condition objects to prevent multiple threads loading same folder.
            if (tmp instanceof FolderObject) {
                ret.add(((FolderObject) tmp).clone());
            }
        }
        return ret;
    }

    /**
     * Loads the folder which matches given object id from underlying database store and puts it into cache.
     * <p>
     * <b>NOTE:</b> This method returns a clone of cached <code>FolderObject</code> instance. Thus any modifications made to the referenced
     * object will not affect cached version
     *
     * @return The matching <code>FolderObject</code> instance fetched from storage else <code>null</code>
     * @throws OXException If a caching error occurs
     */
    public FolderObject loadFolderObject(final int folderId, final Context ctx, final Connection readCon) throws OXException {
        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            throw OXFolderExceptionCode.CACHE_NOT_ENABLED.create();
        }

        CacheKey key = getCacheKeyUsing(ctx.getContextId(), folderId, folderCache);
        if (folderCache.isReplicated()) {
            LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
            Lock lock = null == lockService ? cacheLock : lockService.getSelfCleaningLockFor(new StringBuilder(32).append(REGION_NAME).append('-').append(ctx.getContextId()).append('-').append(folderId).toString());
            lock.lock();
            try {
                final Object tmp = folderCache.get(key);
                if (tmp instanceof FolderObject) {
                    folderCache.remove(key);
                    // Dirty hack
                    final CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
                    if (null != cacheService) {
                        try {
                            final Cache globalCache = cacheService.getCache("GlobalFolderCache");
                            final CacheKey cacheKey = cacheService.newCacheKey(1, FolderStorage.REAL_TREE_ID, String.valueOf(folderId));
                            globalCache.removeFromGroup(cacheKey, String.valueOf(ctx.getContextId()));
                        } catch (OXException e) {
                            LOG.warn("", e);
                        }
                    }
                    cleanseFromLocalCache(folderId, ctx.getContextId());
                }
            } finally {
                lock.unlock();
            }
        }
        if (null != readCon) {
            putIfAbsent(loadFolderObjectInternal(folderId, ctx, readCon), ctx, null);
        }

        // Return element
        Object object = folderCache.get(key);
        if (object instanceof FolderObject) {
            return ((FolderObject) object).clone();
        }

        FolderObject fo = loadFolderObjectInternal(folderId, ctx, readCon);
        putFolderObject(fo, ctx, true, null);
        return fo.clone();
    }

    /**
     * If the specified folder object is not already in cache, it is put into cache.
     * <p>
     * <b>NOTE:</b> This method puts a clone of given <code>FolderObject</code> instance into cache. Thus any modifications made to the
     * referenced object will not affect cached version
     *
     * @param folderObj The folder object
     * @param ctx The context
     * @param elemAttribs The element's attributes (<b>optional</b>), pass <code>null</code> to use the default attributes
     * @return The previous folder object available in cache, or <tt>null</tt> if there was none
     * @throws OXException If put-if-absent operation fails
     */
    public FolderObject putIfAbsent(final FolderObject folderObj, final Context ctx, final ElementAttributes elemAttribs) throws OXException {
        if (!folderObj.containsObjectID()) {
            throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(DataFields.ID, I(-1), I(ctx.getContextId()));
        }

        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            throw OXFolderExceptionCode.CACHE_NOT_ENABLED.create();
        }

        return putIfAbsentInternal(new InstanceFolderProvider(folderObj.clone()), ctx, elemAttribs, getCacheKeyUsing(ctx.getContextId(), folderObj.getObjectID(), folderCache));
    }

    private FolderObject putIfAbsentInternal(FolderProvider folderProvider, Context ctx, ElementAttributes elemAttribs, CacheKey cacheKey) throws OXException {
        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            throw OXFolderExceptionCode.CACHE_NOT_ENABLED.create();
        }

        CacheKey key = null == cacheKey ? getCacheKeyUsing(ctx.getContextId(), folderProvider.getObjectID(), folderCache) : cacheKey;
        FolderObject retval;

        LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
        Lock lock = null == lockService ? cacheLock : lockService.getSelfCleaningLockFor(new StringBuilder(32).append(REGION_NAME).append('-').append(ctx.getContextId()).append('-').append(folderProvider.getObjectID()).toString());
        lock.lock();
        try {
            Object tmp = folderCache.get(key);
            if (tmp instanceof FolderObject) {
                // Already in cache
                retval = ((FolderObject) tmp);
            } else {
                Condition cond = null;
                if (tmp instanceof Condition) {
                    cond = (Condition) tmp;
                }
                if (elemAttribs == null || folderCache.isDistributed()) {
                    // Put with default attributes
                    folderCache.put(key, folderProvider.getFolderObject(), false);
                } else {
                    folderCache.put(key, folderProvider.getFolderObject(), elemAttribs, false);
                }
                if (null != cond) {
                    cond.signalAll();
                }
                // Return null to indicate successful insertion
                retval = null;
            }
        } finally {
            lock.unlock();
        }
        return null == retval ? retval : retval.clone();
    }

    /**
     * Simply puts given <code>FolderObject</code> into cache if object's id is different to zero.
     * <p>
     * <b>NOTE:</b> This method puts a clone of given <code>FolderObject</code> instance into cache. Thus any modifications made to the
     * referenced object will not affect cached version
     *
     * @param folderObj The folder object
     * @param ctx The context
     * @throws OXException If a caching error occurs
     */
    public void putFolderObject(final FolderObject folderObj, final Context ctx) throws OXException {
        putFolderObject(folderObj, ctx, true, null);
    }

    /**
     * <p>
     * Simply puts given <code>FolderObject</code> into cache if object's id is different to zero. If flag <code>overwrite</code> is set to
     * <code>false</code> then this method returns immediately if cache already holds a matching entry.
     * </p>
     * <p>
     * <b>NOTE:</b> This method puts a clone of given <code>FolderObject</code> instance into cache. Thus any modifications made to the
     * referenced object will not affect cached version
     * </p>
     *
     * @param folderObj The folder object
     * @param ctx The context
     * @param overwrite <code>true</code> to overwrite; otherwise <code>false</code>
     * @param elemAttribs The element's attributes (<b>optional</b>), pass <code>null</code> to use the default attributes
     * @throws OXException If a caching error occurs
     */
    public void putFolderObject(final FolderObject folderObj, final Context ctx, final boolean overwrite, final ElementAttributes elemAttribs) throws OXException {
        if (!folderObj.containsObjectID()) {
            throw OXFolderExceptionCode.MISSING_FOLDER_ATTRIBUTE.create(DataFields.ID, I(-1), I(ctx.getContextId()));
        }
        putFolderObject(new InstanceFolderProvider(folderObj), ctx, overwrite, elemAttribs, false);
    }

    private FolderObject putFolderObject(FolderProvider folderProvider, Context ctx, boolean overwrite, ElementAttributes elemAttribs, boolean returnFolder) throws OXException {
        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            return null;
        }
        if (null != elemAttribs) {
            // Ensure isLateral is set to false
            elemAttribs.setIsLateral(false);
        }

        // Put clone of new object into cache.
        CacheKey key = getCacheKeyUsing(ctx.getContextId(), folderProvider.getObjectID(), folderCache);
        LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
        Lock lock = null == lockService ? cacheLock : lockService.getSelfCleaningLockFor(new StringBuilder(32).append(REGION_NAME).append('-').append(ctx.getContextId()).append('-').append(folderProvider.getObjectID()).toString());
        lock.lock();
        try {
            Object currentFromCache = folderCache.get(key);

            // If there is currently an object associated with this key in the region it is replaced.
            Condition cond = null;
            if (overwrite) {
                if (currentFromCache instanceof FolderObject) {
                    // Remove to distribute PUT as REMOVE
                    folderCache.remove(key);
                    // Dirty hack
                    final CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
                    if (null != cacheService) {
                        try {
                            final Cache globalCache = cacheService.getCache("GlobalFolderCache");
                            final CacheKey cacheKey = cacheService.newCacheKey(1, FolderStorage.REAL_TREE_ID, String.valueOf(folderProvider.getObjectID()));
                            globalCache.removeFromGroup(cacheKey, String.valueOf(ctx.getContextId()));
                        } catch (OXException e) {
                            LOG.warn("", e);
                        }
                    }
                    cleanseFromLocalCache(folderProvider.getObjectID(), ctx.getContextId());
                } else if (currentFromCache instanceof Condition) {
                    cond = (Condition) currentFromCache;
                }
            } else {
                // Another thread made a PUT in the meantime. Return because we must not overwrite.
                if (currentFromCache instanceof FolderObject) {
                    return returnFolder ? ((FolderObject) currentFromCache).clone() : null;
                } else if (currentFromCache instanceof Condition) {
                    cond = (Condition) currentFromCache;
                }
            }

            // Clone folder
            FolderObject clone = folderProvider.getFolderObject().clone();
            if (elemAttribs == null || folderCache.isDistributed()) {
                // Put with default attributes
                folderCache.put(key, clone, false);
            } else {
                folderCache.put(key, clone, elemAttribs, false);
            }
            if (null != cond) {
                cond.signalAll();
            }
            return returnFolder ? clone : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes matching folder object from cache
     *
     * @param folderId The key
     * @param ctx The context
     * @throws OXException If a caching error occurs
     */
    public void removeFolderObject(final int folderId, final Context ctx) throws OXException {
        removeFolderObject(ctx.getContextId(), folderId, null);
    }

    /**
     * Removes a matching folder object from cache.
     * <p/>
     * If a database connection is supplied and is currently in a transaction, the cache removal will implicitly be repeated after the
     * connection is committed by registering an appropriate {@link DatabaseConnectionListener}.
     *
     * @param contextId The context identifier the folder is located in
     * @param folderId The identifier of the folder to remove from the cache
     * @param optConnection A database connection to register an additional invalidation callback for, or <code>null</code> if not applicable
     */
    public void removeFolderObject(int contextId, int folderId, Connection optConnection) throws OXException {
        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            return;
        }

        // Remove object from cache if exist
        if (folderId > 0) {
            CacheKey cacheKey = getCacheKeyUsing(contextId, folderId, folderCache);
            LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
            Lock lock = null == lockService ? cacheLock : lockService.getSelfCleaningLockFor(new StringBuilder(32).append(REGION_NAME).append('-').append(contextId).append('-').append(folderId).toString());
            lock.lock();
            try {
                final Object tmp = folderCache.get(cacheKey);
                if (!(tmp instanceof Condition)) {
                    folderCache.remove(cacheKey);
                }
            } finally {
                lock.unlock();
            }

            // Dirty hack
            CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
            if (null != cacheService) {
                try {
                    Cache globalCache = cacheService.getCache("GlobalFolderCache");
                    cacheKey = cacheService.newCacheKey(1, FolderStorage.REAL_TREE_ID, String.valueOf(folderId));
                    globalCache.removeFromGroup(cacheKey, String.valueOf(contextId));
                } catch (OXException e) {
                    LOG.warn("", e);
                }
            }
            cleanseFromLocalCache(folderId, contextId);
        }

        // Invalidate again after transaction is committed
        if (null != optConnection) {
            addAfterCommitCallback(optConnection, (c) -> {
                try {
                    removeFolderObject(contextId, folderId, null);
                } catch (OXException e) {
                    LOG.warn("", e);
                }
            });
        }
    }

    /**
     * Removes matching folder objects from cache
     *
     * @param folderIds The keys
     * @param ctx The context
     * @throws OXException If a caching error occurs
     */
    public void removeFolderObjects(final int[] folderIds, final Context ctx) throws OXException {
        removeFolderObjects(ctx.getContextId(), folderIds, null);
    }

    /**
     * Removes matching folder objects from cache.
     * <p/>
     * If a database connection is supplied and is currently in a transaction, the cache removal will implicitly be repeated after the
     * connection is committed by registering an appropriate {@link DatabaseConnectionListener}.
     *
     * @param contextId The context identifier the folders are located in
     * @param folderIds The identifiers of the folders to remove from the cache
     * @param optConnection A database connection to register an additional invalidation callback for, or <code>null</code> if not applicable
     */
    public void removeFolderObjects(int contextId, int[] folderIds, Connection optConnection) throws OXException {
        if (folderIds == null || folderIds.length == 0) {
            return;
        }

        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            return;
        }

        List<Serializable> cacheKeys = new ArrayList<Serializable>(folderIds.length);
        for (int key : folderIds) {
            if (key > 0) {
                cacheKeys.add(getCacheKeyUsing(contextId, key, folderCache));
            }
        }

        // Remove objects from cache
        folderCache.remove(cacheKeys);

        // Dirty hack
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (null != cacheService) {
            try {
                Cache globalCache = cacheService.getCache("GlobalFolderCache");
                for (int key : folderIds) {
                    CacheKey cacheKey = cacheService.newCacheKey(1, FolderStorage.REAL_TREE_ID, String.valueOf(key));
                    globalCache.removeFromGroup(cacheKey, String.valueOf(contextId));
                }
            } catch (OXException e) {
                LOG.warn("", e);
            }
        }
        cleanseFromLocalCache(folderIds, contextId);

        // Invalidate again after transaction is committed
        if (null != optConnection) {
            addAfterCommitCallback(optConnection, (c) -> {
                try {
                    removeFolderObjects(contextId, folderIds, null);
                } catch (OXException e) {
                    LOG.warn("", e);
                }
            });
        }
    }

    private void cleanseFromLocalCache(int folderId, int contextId) {
        int userId = Tools.getUnsignedInteger(LogProperties.get(LogProperties.Name.SESSION_USER_ID));
        if (userId > 0) {
            FolderMapManagement.getInstance().dropFor(Integer.toString(folderId), FolderStorage.REAL_TREE_ID, userId, contextId);
        }
    }

    private void cleanseFromLocalCache(int[] folderIds, int contextId) {
        int userId = Tools.getUnsignedInteger(LogProperties.get(LogProperties.Name.SESSION_USER_ID));
        if (userId > 0) {
            List<String> fids = new ArrayList<>(folderIds.length);
            for (int folderId : folderIds) {
                fids.add(Integer.toString(folderId));
            }
            FolderMapManagement.getInstance().dropFor(fids, FolderStorage.REAL_TREE_ID, userId, contextId, null);
        }
    }

    /**
     * Removes all folder objects from this cache
     *
     * @throws OXException If folder cache cannot be cleared
     */
    public void clearAll() throws OXException {
        Cache folderCache = this.folderCache;
        if (null == folderCache) {
            return;
        }
        folderCache.clear();

        // Dirty hack
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (null != cacheService) {
            try {
                Cache globalCache = cacheService.getCache("GlobalFolderCache");
                globalCache.clear();
            } catch (OXException e) {
                LOG.warn("", e);
            }
        }
    }

    /**
     * Returns default element attributes for this cache
     *
     * @return default element attributes for this cache or <code>null</code>
     * @throws OXException If a caching error occurs
     */
    public ElementAttributes getDefaultFolderObjectAttributes() throws OXException {
        final Cache folderCache = this.folderCache;
        if (null == folderCache) {
            return null;
        }
        /*
         * Returns a copy NOT a reference
         */
        return folderCache.getDefaultElementAttributes();
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    private static interface FolderProvider {

        FolderObject getFolderObject() throws OXException;

        int getObjectID();
    }

    private static final class InstanceFolderProvider implements FolderProvider {

        private final FolderObject folderObject;

        InstanceFolderProvider(final FolderObject folderObject) {
            super();
            this.folderObject = folderObject;
        }

        @Override
        public FolderObject getFolderObject() {
            return folderObject;
        }

        @Override
        public int getObjectID() {
            return folderObject.getObjectID();
        }

    }

    private static final class LoadingFolderProvider implements FolderProvider {

        private final Connection readCon;
        private final int folderId;
        private final Context ctx;

        LoadingFolderProvider(final int folderId, final Context ctx, final Connection readCon) {
            super();
            this.folderId = folderId;
            this.ctx = ctx;
            this.readCon = readCon;
        }

        @Override
        public FolderObject getFolderObject() throws OXException {
            return loadFolderObjectInternal(folderId, ctx, readCon);
        }

        @Override
        public int getObjectID() {
            return folderId;
        }
    }

    /**
     * Loads the folder object from underlying database storage whose id matches given parameter <code>folderId</code>.
     *
     * @param folderId The folder ID
     * @param ctx The context
     * @param readCon A readable connection (<b>optional</b>), pass <code>null</code> to fetch a new one from connection pool
     * @return The object loaded from DB.
     * @throws OXException If folder object could not be loaded or a caching error occurs
     */
    static FolderObject loadFolderObjectInternal(int folderId, Context ctx, Connection readCon) throws OXException {
        if (folderId <= 0) {
            throw OXFolderExceptionCode.NOT_EXISTS.create(Integer.valueOf(folderId), Integer.valueOf(ctx.getContextId()));
        }
        return FolderObject.loadFolderObjectFromDB(folderId, ctx, readCon);
    }

    /**
     * Tries to add a callback routine that'll be invoked after the supplied database connection has been committed.
     *
     * @param connection The connection to add the callback routine for, or <code>null</code> for a no-op
     * @param callback The callback routine to add
     * @return <code>true</code> if the callback routine could be added, <code>false</code>, otherwise
     */
    private static boolean addAfterCommitCallback(Connection connection, Consumer<Connection> callback) {
        try {
            if (null == connection || false == Databases.isInTransaction(connection)) {
                return false;
            }
        } catch (SQLException e) {
            LOG.warn("", e);
            return false;
        }
        DatabaseConnectionListenerAnnotatable listenerAnnotatable = optDatabaseConnectionListenerAnnotatable(connection);
        if (null == listenerAnnotatable) {
            return false;
        }
        listenerAnnotatable.addListener(new AfterCommitDatabaseConnectionListener(callback));
        return true;
    }

    /**
     * Obtains a reference to the connection listener implemented by the supplied database connection if possible.
     *
     * @param connection The connection to get the connection listener annotatable for, or <code>null</code> for a no-op
     * @return A reference to the connection listener implemented by the supplied database connection, or <code>null</code> if not available
     */
    private static DatabaseConnectionListenerAnnotatable optDatabaseConnectionListenerAnnotatable(Connection connection) {
        if (null != connection) {
            if (DatabaseConnectionListenerAnnotatable.class.isInstance(connection)) {
                return (DatabaseConnectionListenerAnnotatable) connection;
            }
            try {
                if (connection.isWrapperFor(DatabaseConnectionListenerAnnotatable.class)) {
                    return connection.unwrap(DatabaseConnectionListenerAnnotatable.class);
                }
            } catch (SQLException e) {
                LOG.warn("", e);
            }
        }
        return null;
    }

}
