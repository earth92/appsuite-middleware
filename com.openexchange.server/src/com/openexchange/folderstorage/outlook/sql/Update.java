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

package com.openexchange.folderstorage.outlook.sql;

import static com.openexchange.folderstorage.outlook.sql.Utility.debugSQL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.Folder;
import com.openexchange.folderstorage.FolderEventConstants;
import com.openexchange.folderstorage.FolderExceptionErrorMessage;
import com.openexchange.folderstorage.outlook.osgi.Services;

/**
 * {@link Update} - SQL for updating a MS outlook folder.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class Update {

    /**
     * Initializes a new {@link Update}.
     */
    private Update() {
        super();
    }

    /**
     * Updates identifiers.
     *
     * @param cid The context identifier
     * @param tree The tree identifier
     * @param user The user identifier
     * @param newId The new identifier
     * @param oldId The old identifier
     * @param delim The delimiter
     * @throws OXException If an error occurs
     */
    public static void updateIds(final int cid, final int tree, final int user, final String newId, final String oldId, final String delim) throws OXException {
        final DatabaseService databaseService = Utility.getDatabaseService();
        // Get a connection
        final Connection con;
        try {
            con = databaseService.getWritable(cid);
            con.setAutoCommit(false); // BEGIN
        } catch (SQLException e) {
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        }
        try {
            updateIds(cid, tree, user, newId, oldId, delim, con);
            con.commit(); // COMMIT
        } catch (SQLException e) {
            Databases.rollback(con); // ROLLBACK
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        } catch (OXException e) {
            Databases.rollback(con); // ROLLBACK
            throw e;
        } catch (Exception e) {
            Databases.rollback(con); // ROLLBACK
            throw FolderExceptionErrorMessage.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            Databases.autocommit(con);
            databaseService.backWritable(cid, con);
        }
    }

    /**
     * Updates identifiers.
     *
     * @param cid The context identifier
     * @param tree The tree identifier
     * @param user The user identifier
     * @param newId The new identifier
     * @param oldId The old identifier
     * @param delim The delimiter
     * @param con The connection to use
     * @throws OXException If an error occurs
     */
    public static void updateIds(final int cid, final int tree, final int user, final String newId, final String oldId, final String delim, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("UPDATE virtualTree SET parentId = ? WHERE cid = ? AND tree = ? AND user = ? AND parentId = ?");
            stmt.setString(1, newId);
            stmt.setInt(2, cid);
            stmt.setInt(3, tree);
            stmt.setInt(4, user);
            stmt.setString(5, oldId);
            stmt.executeUpdate();
            Databases.closeSQLStuff(stmt);

            final StringBuilder tmp = new StringBuilder();
            final String prefix = tmp.append(oldId).append(delim).toString();
            stmt = con.prepareStatement("SELECT parentId FROM virtualTree WHERE cid = ? AND tree = ? AND user = ? AND SUBSTRING(parentId,1,"+prefix.length()+") = ?");
            stmt.setInt(1, cid);
            stmt.setInt(2, tree);
            stmt.setInt(3, user);
            stmt.setString(4, prefix);
            rs = stmt.executeQuery();
            if (rs.next()) {
                final Map<String, String> parentIds = new HashMap<String, String>();
                do {
                    final String oldS = rs.getString(1);
                    final String tail = oldS.substring(prefix.length() + 1);
                    tmp.setLength(0);
                    final String newS = tmp.append(newId).append(delim).append(tail).toString();

                    parentIds.put(newS, oldS);
                } while (rs.next());
                Databases.closeSQLStuff(rs, stmt);

                stmt = con.prepareStatement("UPDATE virtualTree SET parentId = ? WHERE cid = ? AND tree = ? AND user = ? AND parentId = ?");
                for (final Entry<String, String> entry : parentIds.entrySet()) {
                    stmt.setString(1, entry.getKey());
                    stmt.setInt(2, cid);
                    stmt.setInt(3, tree);
                    stmt.setInt(4, user);
                    stmt.setString(5, entry.getValue());
                    stmt.addBatch();

                    // Post event
                    postChangedId(entry.getValue(), user, cid);
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            debugSQL(stmt);
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    /**
     * Updates specified folder.
     *
     * @param cid The context identifier
     * @param tree The tree identifier
     * @param user The user identifier
     * @param folder The folder
     * @throws OXException If update fails
     */
    public static void updateFolder(final int cid, final int tree, final int user, final Folder folder) throws OXException {
        final DatabaseService databaseService = Utility.getDatabaseService();
        // Get a connection
        final Connection con;
        try {
            con = databaseService.getWritable(cid);
            con.setAutoCommit(false); // BEGIN
        } catch (SQLException e) {
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        }
        try {
            if (Delete.deleteFolder(cid, tree, user, folder.getID(), false, false, con)) {
                Insert.insertFolder(cid, tree, user, folder, con);
            }
            con.commit(); // COMMIT
        } catch (SQLException e) {
            Databases.rollback(con); // ROLLBACK
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        } catch (OXException e) {
            Databases.rollback(con); // ROLLBACK
            throw e;
        } catch (Exception e) {
            Databases.rollback(con); // ROLLBACK
            throw FolderExceptionErrorMessage.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            Databases.autocommit(con);
            databaseService.backWritable(cid, con);
        }
    }

    private static final String SQL_UPDATE_LM =
        "UPDATE virtualTree SET lastModified = ?, modifiedBy = ? WHERE cid = ? AND tree = ? AND user = ? AND folderId = ? AND lastModified IS NOT NULL";

    /**
     * Updates last-modified time stamp of specified folder in virtual table.
     *
     * @param cid The context identifier
     * @param tree The tree identifier
     * @param user The user identifier
     * @param folderId The folder identifier
     * @param lastModified The last-modified time stamp
     * @throws OXException If update fails
     */
    public static void updateLastModified(final int cid, final int tree, final int user, final String folderId, final long lastModified) throws OXException {
        final DatabaseService databaseService = Utility.getDatabaseService();
        /*
         * Get a connection
         */
        final Connection con;
        try {
            con = databaseService.getWritable(cid);
            con.setAutoCommit(false); // BEGIN
        } catch (SQLException e) {
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        }
        try {
            updateLastModified(cid, tree, user, folderId, lastModified, con);
            con.commit(); // COMMIT
        } catch (SQLException e) {
            Databases.rollback(con); // ROLLBACK
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        } catch (OXException e) {
            Databases.rollback(con); // ROLLBACK
            throw e;
        } catch (Exception e) {
            Databases.rollback(con); // ROLLBACK
            throw FolderExceptionErrorMessage.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            Databases.autocommit(con);
            databaseService.backWritable(cid, con);
        }
    }

    /**
     * Updates last-modified time stamp of specified folder in virtual table.
     *
     * @param cid The context identifier
     * @param tree The tree identifier
     * @param user The user identifier
     * @param folderId The folder identifier
     * @param lastModified The last-modified time stamp
     * @param con The connection to use
     * @throws OXException If update fails
     */
    public static void updateLastModified(final int cid, final int tree, final int user, final String folderId, final long lastModified, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(SQL_UPDATE_LM);
            int pos = 1;
            stmt.setLong(pos++, lastModified);
            stmt.setInt(pos++, user);
            stmt.setInt(pos++, cid);
            stmt.setInt(pos++, tree);
            stmt.setInt(pos++, user);
            stmt.setString(pos, folderId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            debugSQL(stmt);
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private static final String SQL_UPDATE_PARENT =
        "UPDATE virtualTree SET parentId = ? WHERE cid = ? AND tree = ? AND user = ? AND folderId = ?";

    /**
     * Updates last-modified time stamp of specified folder in virtual table.
     *
     * @param cid The context identifier
     * @param tree The tree identifier
     * @param user The user identifier
     * @param folderId The folder identifier
     * @param parentId The new parent identifier
     * @param con The connection to use
     * @throws OXException If update fails
     */
    public static void updateParent(final int cid, final int tree, final int user, final String folderId, final String parentId, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(SQL_UPDATE_PARENT);
            int pos = 1;
            stmt.setString(pos++, parentId);
            stmt.setInt(pos++, cid);
            stmt.setInt(pos++, tree);
            stmt.setInt(pos++, user);
            stmt.setString(pos, folderId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            debugSQL(stmt);
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private static final String SQL_UPDATE_ID =
        "UPDATE virtualTree SET folderId = ? WHERE cid = ? AND tree = ? AND user = ? AND folderId = ?";

    /**
     * Updates last-modified time stamp of specified folder in virtual table.
     *
     * @param cid The context identifier
     * @param tree The tree identifier
     * @param user The user identifier
     * @param folderId The folder identifier
     * @param newFolderId The new folder identifier
     * @param con The connection to use
     * @throws OXException If update fails
     */
    public static void updateId(final int cid, final int tree, final int user, final String folderId, final String newFolderId, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(SQL_UPDATE_ID);
            int pos = 1;
            stmt.setString(pos++, newFolderId);
            stmt.setInt(pos++, cid);
            stmt.setInt(pos++, tree);
            stmt.setInt(pos++, user);
            stmt.setString(pos, folderId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            debugSQL(stmt);
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private static final String SQL_UPDATE_NAME =
        "UPDATE virtualTree SET name = ? WHERE cid = ? AND tree = ? AND user = ? AND folderId = ?";

    /**
     * Updates last-modified time stamp of specified folder in virtual table.
     *
     * @param cid The context identifier
     * @param tree The tree identifier
     * @param user The user identifier
     * @param folderId The folder identifier
     * @param name The new name
     * @param con The connection to use
     * @throws OXException If update fails
     */
    public static void updateName(final int cid, final int tree, final int user, final String folderId, final String name, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(SQL_UPDATE_NAME);
            int pos = 1;
            stmt.setString(pos++, name);
            stmt.setInt(pos++, cid);
            stmt.setInt(pos++, tree);
            stmt.setInt(pos++, user);
            stmt.setString(pos, folderId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            debugSQL(stmt);
            throw FolderExceptionErrorMessage.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private static void postChangedId(final String folderId, final int userId, final int contextId) {
        final EventAdmin eventAdmin = Services.getService(EventAdmin.class);
        if (null != eventAdmin) {
            final Map<String, Object> properties = new HashMap<String, Object>(6);
            properties.put(FolderEventConstants.PROPERTY_CONTEXT, Integer.valueOf(contextId));
            properties.put(FolderEventConstants.PROPERTY_USER, Integer.valueOf(userId));
            properties.put(FolderEventConstants.PROPERTY_CONTENT_RELATED, Boolean.FALSE);
            properties.put(FolderEventConstants.PROPERTY_FOLDER, folderId);
            properties.put(FolderEventConstants.PROPERTY_IMMEDIATELY, Boolean.TRUE);
            final Event event = new Event(FolderEventConstants.TOPIC, properties);
            eventAdmin.postEvent(event);
        }
    }

}
