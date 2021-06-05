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

package com.openexchange.guest.impl.internal;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.guest.GuestAssignment;
import com.openexchange.guest.GuestExceptionCodes;
import com.openexchange.guest.impl.storage.GuestStorage;

/**
 *
 * Database storage implementation for guests
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.8.0
 */
public class RdbGuestStorage extends GuestStorage {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RdbGuestStorage.class);

    /**
     * SQL statement for resolving the internal unique id based on the mail address
     */
    protected static final String RESOLVE_GUEST_ID_BY_MAIL = "SELECT id FROM guest WHERE mail_address=? AND gid=?";

    /**
     * SQL statement for resolving the internal unique id based on the assigned group
     */
    protected static final String RESOLVE_GUEST_ID_BY_GROUP = "SELECT id FROM guest WHERE gid=?";

    /**
     * SQL statement for resolving the internal unique id based on context
     */
    protected static final String RESOLVE_GUESTS_FOR_CONTEXT = "SELECT guest_id FROM guest2context WHERE cid=?";

    /**
     * SQL statement for resolving the internal unique id based on the groupId
     */
    protected static final String RESOLVE_GUESTS_FOR_GROUP = "SELECT id FROM guest WHERE gid=?";

    /**
     * SQL statement for getting one assignment made for a user (resolved by guest id, context and user id)<br>
     * <br>
     * Checks if exactly the same user is existing.
     */
    protected static final String RESOLVE_GUEST_ASSIGNMENT = "SELECT * FROM guest2context WHERE cid=? AND uid=? AND guest_id=?";

    /**
     * SQL statement for getting password and password mechanism for a user (resolved by guest id, context and user id)
     */
    protected static final String RESOLVE_GUEST_ASSIGNMENT_PASSWORD = "SELECT password, passwordMech, salt FROM guest2context WHERE cid=? AND uid=? AND guest_id=?";

    /**
     * SQL statement for getting assignments made for a user based on the mail address<br>
     * <br>
     * Checks if the given user has assignments.
     */
    protected static final String RESOLVE_GUEST_ASSIGNMENTS = "SELECT cid,uid,password,passwordMech,salt FROM guest2context WHERE guest_id=?";

    /**
     * SQL statement to count assignments that currently exist.
     */
    protected static final String RESOLVE_NUMBER_OF_GUEST_ASSIGNMENTS_BY_GUESTID = "SELECT COUNT(*) FROM guest2context WHERE guest_id=?";

    /**
     * SQL statement to update password and passwordMech for the given user
     */
    protected static final String UPDATE_GUEST_PASSWORD = "UPDATE guest2context SET password=?, passwordMech=?, salt=? WHERE cid=? AND uid=? AND guest_id=?";

    /**
     * SQL statement to insert a new assignment for an existing guest
     */
    protected static final String INSERT_GUEST_ASSIGNMENT = "INSERT INTO guest2context (guest_id, cid, uid, password, passwordMech, salt) VALUES (?, ?, ?, ?, ?, ?)";

    /**
     * SQL statement to insert a new guest for an unknown mail address
     */
    protected static final String INSERT_GUEST = "INSERT INTO guest (mail_address, gid) VALUES (?, ?)";

    /**
     * SQL statement for deleting a guest assignment based on the context and user id.
     */
    protected static final String DELETE_GUEST_ASSIGNMENT = "DELETE FROM guest2context where guest_id=? AND cid=? AND uid=?";

    /**
     * SQL statement for deleting a guest based on its internal guest id.
     */
    protected static final String DELETE_GUEST = "DELETE FROM guest where id=?";

    /**
     * SQL statement for deleting guests based on the assigned group id
     */
    protected static final String DELETE_GUESTS = "DELETE FROM guest where gid=?";

    /**
     * SQL statement for deleting guests based on its context.
     */
    protected static final String DELETE_GUEST_ASSIGNMENTS = "DELETE FROM guest2context where cid=?";

    /**
     * SQL statement for deleting guests based on its groupId.
     */
    protected static final String DELETE_GUEST_ASSIGNMENTS_FOR_GROUP = "DELETE FROM guest2context where guest_id IN (?)";

    /**
     * {@inheritDoc}
     */
    @Override
    public long addGuest(String mailAddress, String groupId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        long guestId = NOT_FOUND;
        try {
            statement = connection.prepareStatement(INSERT_GUEST, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, mailAddress.toLowerCase());
            statement.setString(2, groupId);
            long affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw GuestExceptionCodes.SQL_ERROR.create("Not able to create guest with mail address '" + mailAddress + "' and group '" + groupId + "' as desired!");
            }
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    guestId = generatedKeys.getLong(1);
                } else {
                    throw GuestExceptionCodes.SQL_ERROR.create("Creating guest with mail address '" + mailAddress + "' and group '" + groupId + "' failed, no ID obtained!");
                }
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }

        return guestId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addGuestAssignment(GuestAssignment assignment, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_GUEST_ASSIGNMENT);
            statement.setLong(1, assignment.getGuestId());
            statement.setInt(2, assignment.getContextId());
            statement.setInt(3, assignment.getUserId());
            statement.setString(4, assignment.getPassword());
            statement.setString(5, assignment.getPasswordMech());
            statement.setBytes(6, assignment.getSalt());

            long affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                String sqlStmt = statement.toString(); // Call PreparedStatement.toString() here to avoid race condition with asynchronous logging behavior
                LOG.error("There have been {} changes for adding guest assignment but there should only be 1. Executed SQL: {}", Long.valueOf(affectedRows), sqlStmt);
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getGuestId(String mailAddress, String groupId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        ResultSet result = null;
        long guestId = NOT_FOUND;
        try {
            statement = connection.prepareStatement(RESOLVE_GUEST_ID_BY_MAIL);
            statement.setString(1, mailAddress.toLowerCase());
            statement.setString(2, groupId);
            result = statement.executeQuery();
            if (result.next()) {
                guestId = result.getLong(1);
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(result, statement);
        }

        return guestId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getGuestIds(String groupId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        ResultSet result = null;
        List<Long> guestIds = new ArrayList<Long>();
        try {
            statement = connection.prepareStatement(RESOLVE_GUEST_ID_BY_GROUP);
            statement.setString(1, groupId);
            result = statement.executeQuery();

            while (result.next()) {
                long guestId = result.getLong(1);
                guestIds.add(L(guestId));
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(result, statement);
        }

        return guestIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeGuestAssignment(long guestId, int contextId, int userId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(DELETE_GUEST_ASSIGNMENT);
            statement.setLong(1, guestId);
            statement.setInt(2, contextId);
            statement.setInt(3, userId);
            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                String sql = statement.toString(); // Invoke PreparedStatement.toString() to avoid race condition with asynchronous logging behavior
                LOG.error("There have been {} changes for removing a guest assignment but there should only be 1. Executed SQL: {}", I(affectedRows), sql);
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void removeGuest(long guestId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(DELETE_GUEST);
            statement.setLong(1, guestId);
            int affectedRows = statement.executeUpdate();

            if (affectedRows > 1) {
                String sql = statement.toString(); // Invoke PreparedStatement.toString() to avoid race condition with asynchronous logging behavior
                LOG.error("There have been {} guests removed but there should max be 1. Executed SQL: {}", I(affectedRows), sql);
                throw GuestExceptionCodes.TOO_MANY_GUESTS_REMOVED.create(Long.toString(affectedRows), statement.toString());
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void removeGuests(String groupId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(DELETE_GUESTS);
            statement.setString(1, groupId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public List<Long> resolveGuestAssignments(int contextId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        List<Long> guestIdsAssigmentsRemovedFor = new ArrayList<Long>();

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(RESOLVE_GUESTS_FOR_CONTEXT);
            statement.setInt(1, contextId);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                long guestId = result.getLong(1);
                guestIdsAssigmentsRemovedFor.add(L(guestId));
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }
        return guestIdsAssigmentsRemovedFor;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long removeGuestAssignments(List<Long> groupIds, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }
        if (groupIds.isEmpty()) {
            return 0L;
        }

        PreparedStatement statement = null;
        long affectedRows = NOT_FOUND;
        try {
            String groupIdsAsString = getIdsAsString(groupIds);
            statement = connection.prepareStatement(DELETE_GUEST_ASSIGNMENTS_FOR_GROUP.replace("?", groupIdsAsString));
            affectedRows = statement.executeUpdate();
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }

        return affectedRows;
    }

    /**
     * Converts the given list of group ids to a String
     *
     * @param groupIds
     * @return
     */
    protected String getIdsAsString(List<Long> groupIds) {
        StringBuilder commaSepValueBuilder = new StringBuilder();

        for (int i = 0; i < groupIds.size(); i++) {
            commaSepValueBuilder.append(groupIds.get(i));

            if (i != groupIds.size() - 1) {
                commaSepValueBuilder.append(",");
            }
        }

        return commaSepValueBuilder.toString().replaceAll("\\s", "");
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long removeGuestAssignments(int contextId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        long affectedRows = NOT_FOUND;
        try {
            statement = connection.prepareStatement(DELETE_GUEST_ASSIGNMENTS);
            statement.setInt(1, contextId);
            affectedRows = statement.executeUpdate();
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }

        return affectedRows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getNumberOfAssignments(long guestId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        long guestAssignments = 0;
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection.prepareStatement(RESOLVE_NUMBER_OF_GUEST_ASSIGNMENTS_BY_GUESTID);
            statement.setLong(1, guestId);
            result = statement.executeQuery();
            if (result.next()) {
                guestAssignments = result.getInt(1);
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(result, statement);
        }

        return guestAssignments;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAssignmentExisting(long guestId, int contextId, int userId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection.prepareStatement(RESOLVE_GUEST_ASSIGNMENT);
            statement.setInt(1, contextId);
            statement.setInt(2, userId);
            statement.setLong(3, guestId);
            result = statement.executeQuery();
            if (result.next()) {
                return true;
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(result, statement);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GuestAssignment> getGuestAssignments(long guestId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        final List<GuestAssignment> guestAssignments = new ArrayList<GuestAssignment>();
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection.prepareStatement(RESOLVE_GUEST_ASSIGNMENTS);
            statement.setLong(1, guestId);
            result = statement.executeQuery();
            while (result.next()) {
                int cid = result.getInt(1);
                int uid = result.getInt(2);
                String password = result.getString(3);
                String passwordMech = result.getString(4);
                byte[] salt = result.getBytes(5);
                guestAssignments.add(new GuestAssignment(guestId, cid, uid, password, passwordMech, salt));
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(result, statement);
        }
        return guestAssignments;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GuestAssignment getGuestAssignment(long guestId, int contextId, int userId, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection.prepareStatement(RESOLVE_GUEST_ASSIGNMENT_PASSWORD);
            statement.setInt(1, contextId);
            statement.setInt(2, userId);
            statement.setLong(3, guestId);
            result = statement.executeQuery();
            while (result.next()) {
                String password = result.getString(1);
                String passwordMech = result.getString(2);
                byte[] salt = result.getBytes(3);
                return new GuestAssignment(guestId, contextId, userId, password, passwordMech, salt);
            }

        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(result, statement);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateGuestAssignment(GuestAssignment assignment, Connection connection) throws OXException {
        if (connection == null) {
            throw GuestExceptionCodes.NO_CONNECTION_PROVIDED.create();
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(UPDATE_GUEST_PASSWORD);
            statement.setString(1, assignment.getPassword());
            statement.setString(2, assignment.getPasswordMech());
            statement.setBytes(3, assignment.getSalt());
            statement.setInt(4, assignment.getContextId());
            statement.setInt(5, assignment.getUserId());
            statement.setLong(6, assignment.getGuestId());

            final int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                String sql = statement.toString(); // Invoke PreparedStatement.toString() to avoid race condition with asynchronous logging behavior
                LOG.error("There have been {} changes for updating the guest user. Executed SQL: {}", I(affectedRows), sql);
                throw GuestExceptionCodes.SQL_ERROR.create("There have been " + affectedRows + " changes for updating the guest user. Executed SQL: " + statement.toString());
            }
        } catch (SQLException e) {
            throw GuestExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(statement);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void shutDown() {
        // Nothing to do.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void startUp() {
        // Nothing to do.
    }

}
