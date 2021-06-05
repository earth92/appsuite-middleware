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

package com.openexchange.objectusecount.impl;

import static com.openexchange.database.Databases.closeSQLStuff;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import com.openexchange.database.DatabaseService;
import com.openexchange.exception.OXException;
import com.openexchange.objectusecount.AbstractArguments;
import com.openexchange.objectusecount.BatchIncrementArguments.ObjectAndFolder;
import com.openexchange.objectusecount.IncrementArguments;
import com.openexchange.objectusecount.ObjectUseCountService;
import com.openexchange.objectusecount.SetArguments;
import com.openexchange.objectusecount.exception.ObjectUseCountExceptionCode;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.Task;
import com.openexchange.threadpool.ThreadPools;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.map.TIntIntMap;

/**
 * {@link ObjectUseCountServiceImpl}
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.1
 */
public class ObjectUseCountServiceImpl implements ObjectUseCountService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ObjectUseCountServiceImpl.class);

    /** The service look-up */
    final ServiceLookup services;

    /**
     * Initializes a new {@link ObjectUseCountServiceImpl}.
     *
     * @param services The service look-up
     */
    public ObjectUseCountServiceImpl(ServiceLookup services) {
        super();
        this.services = services;
    }

    /**
     * Checks if specified arguments allow to modify the use count asynchronously.
     *
     * @param arguments The arguments to check
     * @return <code>true</code> if asynchronous execution is possible; otherwise <code>false</code> for synchronous execution
     */
    private boolean doPerformAsynchronously(AbstractArguments arguments) {
        return null == arguments.getCon() && false == arguments.isThrowException();
    }

    @Override
    public int getObjectUseCount(Session session, int folderId, int objectId) throws OXException {
        DatabaseService dbService = services.getService(DatabaseService.class);
        if (null == dbService) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
        }

        Connection con = dbService.getReadOnly(session.getContextId());
        try {
            return getObjectUseCount(session, folderId, objectId, con);
        } finally {
            dbService.backReadOnly(session.getContextId(), con);
        }
    }

    @Override
    public int getObjectUseCount(Session session, int folderId, int objectId, Connection con) throws OXException {
        if (null == con) {
            return getObjectUseCount(session, folderId, objectId);
        }

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT value FROM object_use_count WHERE cid = ? AND user = ? AND folder = ? AND object = ?");
            stmt.setInt(1, session.getContextId());
            stmt.setInt(2, session.getUserId());
            stmt.setInt(3, folderId);
            stmt.setInt(4, objectId);
            rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw ObjectUseCountExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(rs);
            closeSQLStuff(stmt);
        }
    }

    @Override
    public void incrementObjectUseCount(final Session session, final IncrementArguments arguments) throws OXException {
        try {
            Task<Void> task = new IncrementObjectUseCountTask(arguments, session, this);
            if (doPerformAsynchronously(arguments)) {
                // Execute asynchronously; as a new connection is supposed to be fetched and no error should be signaled; thus "fire & forget"
                ThreadPools.submitElseExecute(task);
            } else {
                task.call();
            }
        } catch (OXException e) {
            if (arguments.isThrowException()) {
                throw e;
            }

            LOG.debug("Failed to increment object use count", e);
        } catch (RuntimeException e) {
            if (arguments.isThrowException()) {
                throw ObjectUseCountExceptionCode.UNKNOWN.create(e, e.getMessage());
            }

            LOG.debug("Failed to increment object use count", e);
        } catch (Exception e) {
            if (arguments.isThrowException()) {
                throw ObjectUseCountExceptionCode.UNKNOWN.create(e, e.getMessage());
            }

            LOG.debug("Failed to increment object use count", e);
        }
    }

    private void batchIncrementObjectUseCount(Map<ObjectAndFolder, Integer> counts, int userId, int contextId) throws OXException {
        if (null == counts || counts.isEmpty()) {
            return;
        }

        DatabaseService dbService = services.getService(DatabaseService.class);
        if (null == dbService) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
        }
        Connection con = dbService.getWritable(contextId);
        try {
            batchIncrementObjectUseCount(counts, userId, contextId, con);
        } finally {
            dbService.backWritable(contextId, con);
        }
    }

    void batchIncrementObjectUseCount(Map<ObjectAndFolder, Integer> counts, int userId, int contextId, Connection con) throws OXException {
        if (null == con) {
            batchIncrementObjectUseCount(counts, userId, contextId);
            return;
        }

        if (null == counts || counts.isEmpty()) {
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("INSERT INTO object_use_count (cid, user, folder, object, value) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value=value + ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);

            Iterator<Entry<ObjectAndFolder, Integer>> iterator = counts.entrySet().iterator();
            int size = counts.size();
            if (size > 1) {
                for (int i = size; i-- > 0;) {
                    Map.Entry<ObjectAndFolder, Integer> entry = iterator.next();
                    ObjectAndFolder key = entry.getKey();
                    int count = entry.getValue().intValue();
                    stmt.setInt(3, key.getFolderId());
                    stmt.setInt(4, key.getObjectId());
                    stmt.setInt(5, count);
                    stmt.setInt(6, count);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } else {
                Map.Entry<ObjectAndFolder, Integer> entry = iterator.next();
                ObjectAndFolder key = entry.getKey();
                int count = entry.getValue().intValue();
                stmt.setInt(3, key.getFolderId());
                stmt.setInt(4, key.getObjectId());
                stmt.setInt(5, count);
                stmt.setInt(6, count);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw ObjectUseCountExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(stmt);
        }
    }

    private void incrementObjectUseCount(TIntIntMap object2folder, int userId, int contextId) throws OXException {
        if (null == object2folder || object2folder.isEmpty()) {
            return;
        }

        DatabaseService dbService = services.getService(DatabaseService.class);
        if (null == dbService) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
        }
        Connection con = dbService.getWritable(contextId);
        try {
            incrementObjectUseCount(object2folder, userId, contextId, con);
        } finally {
            dbService.backWritable(contextId, con);
        }
    }

    void incrementObjectUseCount(TIntIntMap contact2folder, int userId, int contextId, Connection con) throws OXException {
        if (null == contact2folder || contact2folder.isEmpty()) {
            return;
        }

        if (null == con) {
            incrementObjectUseCount(contact2folder, userId, contextId);
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("INSERT INTO object_use_count (cid, user, folder, object, value) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value=value+1");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);

            TIntIntIterator iterator = contact2folder.iterator();
            int size = contact2folder.size();
            if (size > 1) {
                for (int i = size; i-- > 0;) {
                    iterator.advance();
                    int folderId = iterator.value();
                    int objectId = iterator.key();
                    stmt.setInt(3, folderId);
                    stmt.setInt(4, objectId);
                    stmt.setInt(5, 1);
                    stmt.addBatch();
                    LOG.debug("Incremented object use count for user {}, folder {}, object {} in context {}.", I(userId), I(folderId), I(objectId), I(contextId));
                }
                stmt.executeBatch();
            } else {
                iterator.advance();
                int folderId = iterator.value();
                int objectId = iterator.key();
                stmt.setInt(3, folderId);
                stmt.setInt(4, objectId);
                stmt.setInt(5, 1);
                stmt.executeUpdate();
                LOG.debug("Incremented object use count for user {}, folder {}, object {} in context {}.", I(userId), I(folderId), I(objectId), I(contextId));
            }
        } catch (SQLException e) {
            throw ObjectUseCountExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(stmt);
        }
    }

    @Override
    public void resetObjectUseCount(Session session, int folder, int objectId) throws OXException {
        DatabaseService dbService = services.getService(DatabaseService.class);
        if (null == dbService) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
        }

        Connection con = dbService.getWritable(session.getContextId());
        try {
            resetObjectUseCount(session, folder, objectId, con);
        } finally {
            dbService.backWritable(session.getContextId(), con);
        }
    }

    @Override
    public void resetObjectUseCount(Session session, int folder, int objectId, Connection con) throws OXException {
        if (null == con) {
            resetObjectUseCount(session, folder, objectId);
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("UPDATE object_use_count SET value = 0 WHERE cid = ? AND user = ? AND folder = ? AND object = ?");
            stmt.setInt(1, session.getContextId());
            stmt.setInt(2, session.getUserId());
            stmt.setInt(3, folder);
            stmt.setInt(4, objectId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw ObjectUseCountExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(stmt);
        }
    }

    @Override
    public void setObjectUseCount(final Session session, final SetArguments arguments) throws OXException {
        try {
            Task<Void> task = new AbstractTask<Void>() {

                @Override
                public Void call() throws Exception {
                    setObjectUseCount(arguments.getFolderId(), arguments.getObjectId(), arguments.getValue(), session.getUserId(), session.getContextId(), arguments.getCon());

                    return null;
                }
            };

            if (doPerformAsynchronously(arguments)) {
                // Execute asynchronously; as a new connection is supposed to be fetched and no error should be signaled; thus "fire & forget"
                ThreadPools.submitElseExecute(task);
            } else {
                task.call();
            }
        } catch (OXException e) {
            if (arguments.isThrowException()) {
                throw e;
            }

            LOG.debug("Failed to set object use count", e);
        } catch (RuntimeException e) {
            if (arguments.isThrowException()) {
                throw ObjectUseCountExceptionCode.UNKNOWN.create(e, e.getMessage());
            }

            LOG.debug("Failed to set object use count", e);
        } catch (Exception e) {
            if (arguments.isThrowException()) {
                throw ObjectUseCountExceptionCode.UNKNOWN.create(e, e.getMessage());
            }

            LOG.debug("Failed to set object use count", e);
        }
    }

    private void setObjectUseCount(int folderId, int objectId, int value, int userId, int contextId) throws OXException {
        DatabaseService dbService = services.getService(DatabaseService.class);
        if (null == dbService) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
        }
        Connection con = dbService.getWritable(contextId);
        try {
            setObjectUseCount(folderId, objectId, value, userId, contextId, con);
        } finally {
            dbService.backWritable(contextId, con);
        }
    }

    void setObjectUseCount(int folderId, int objectId, int value, int userId, int contextId, Connection con) throws OXException {
        if (null == con) {
            setObjectUseCount(folderId, objectId, value, userId, contextId);
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("INSERT INTO object_use_count (cid, user, folder, object, value) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value=?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.setInt(3, folderId);
            stmt.setInt(4, objectId);
            stmt.setInt(5, value);
            stmt.setInt(6, value);
            stmt.executeUpdate();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Set object use count to {} for user {}, folder {}, object {} in context {}", I(value), I(userId), I(folderId), I(objectId), I(contextId), new Throwable("use-count-trace"));
            }
        } catch (SQLException e) {
            throw ObjectUseCountExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(stmt);
        }
    }

}
