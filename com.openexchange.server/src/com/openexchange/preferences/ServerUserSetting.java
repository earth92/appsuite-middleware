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

package com.openexchange.preferences;

import static com.openexchange.database.Databases.closeSQLStuff;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import com.openexchange.config.ConfigurationService;
import com.openexchange.databaseold.Database;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.settings.SettingExceptionCodes;
import com.openexchange.java.util.UUIDs;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.sql.DBUtils;

/**
 * Interface for accessing configuration settings.
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.org">Martin Herfurth</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class ServerUserSetting {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ServerUserSetting.class);

    private static final Attribute<Integer> CONTACT_COLLECT_FOLDER = new Attribute<Integer>() {

        @Override
        public Integer getAttribute(final ResultSet rs) throws SQLException {
            final int retval = rs.getInt(getColumnName());
            return rs.wasNull() ? null : I(retval);
        }

        @Override
        public String getColumnName() {
            return "contact_collect_folder";
        }

        @Override
        public void setAttribute(final PreparedStatement pstmt, final Integer value) throws SQLException {
            if (null == value) {
                pstmt.setNull(1, Types.INTEGER);
            } else {
                pstmt.setInt(1, value.intValue());
            }
        }

    };

    private static final Attribute<Boolean> CONTACT_COLLECT_ON_MAIL_ACCESS = new Attribute<Boolean>() {

        @Override
        public Boolean getAttribute(final ResultSet rs) throws SQLException {
            final boolean b = rs.getBoolean(getColumnName());
            return rs.wasNull() ? null : Boolean.valueOf(b);
        }

        @Override
        public String getColumnName() {
            return "contactCollectOnMailAccess";
        }

        @Override
        public void setAttribute(final PreparedStatement pstmt, final Boolean value) throws SQLException {
            if (value == null) {
                pstmt.setBoolean(1, true);
            } else {
                pstmt.setBoolean(1, value.booleanValue());
            }
        }

    };

    private static final Attribute<Boolean> CONTACT_COLLECT_ON_MAIL_TRANSPORT = new Attribute<Boolean>() {

        @Override
        public Boolean getAttribute(final ResultSet rs) throws SQLException {
            final boolean b = rs.getBoolean(getColumnName());
            return rs.wasNull() ? null : Boolean.valueOf(b);
        }

        @Override
        public String getColumnName() {
            return "contactCollectOnMailTransport";
        }

        @Override
        public void setAttribute(final PreparedStatement pstmt, final Boolean value) throws SQLException {
            if (value == null) {
                pstmt.setBoolean(1, true);
            } else {
                pstmt.setBoolean(1, value.booleanValue());
            }
        }

    };

    private static final Attribute<Integer> DEFAULT_STATUS_PRIVATE = new Attribute<Integer>() {

        @Override
        public Integer getAttribute(final ResultSet rs) throws SQLException {
            return I(rs.getInt(getColumnName()));
        }

        @Override
        public String getColumnName() {
            return "defaultStatusPrivate";
        }

        @Override
        public void setAttribute(final PreparedStatement pstmt, final Integer value) throws SQLException {
            if (value == null) {
                pstmt.setInt(1, 0);
            } else {
                pstmt.setInt(1, value.intValue());
            }
        }

    };

    private static final Attribute<Integer> DEFAULT_STATUS_PUBLIC = new Attribute<Integer>() {

        @Override
        public Integer getAttribute(final ResultSet rs) throws SQLException {
            return I(rs.getInt(getColumnName()));
        }

        @Override
        public String getColumnName() {
            return "defaultStatusPublic";
        }

        @Override
        public void setAttribute(final PreparedStatement pstmt, final Integer value) throws SQLException {
            if (value == null) {
                pstmt.setInt(1, 0);
            } else {
                pstmt.setInt(1, value.intValue());
            }
        }

    };

    private static final Attribute<Integer> FOLDER_TREE = new Attribute<Integer>() {

        @Override
        public Integer getAttribute(final ResultSet rs) throws SQLException {
            final int tmp = rs.getInt(getColumnName());
            final Integer retval;
            if (rs.wasNull()) {
                retval = null;
            } else {
                retval = I(tmp);
            }
            return retval;
        }

        @Override
        public String getColumnName() {
            return "folderTree";
        }

        @Override
        public void setAttribute(final PreparedStatement pstmt, final Integer value) throws SQLException {
            if (value == null) {
                pstmt.setInt(1, 0);
            } else {
                pstmt.setInt(1, value.intValue());
            }
        }
    };

    private static final ServerUserSetting defaultInstance = new ServerUserSetting();

    /**
     * Gets the default instance.
     *
     * @return The default instance.
     */
    public static ServerUserSetting getInstance() {
        return defaultInstance;
    }

    /**
     * Gets the instance using specified connection.
     *
     * @param connection The connection to use.
     * @return The instance using specified connection.
     */
    public static ServerUserSetting getInstance(final Connection connection) {
        return new ServerUserSetting(connection);
    }

    /*-
     * ################### Member fields & methods ###################
     */

    private final Connection connection;

    /**
     * Initializes a new {@link ServerUserSetting}.
     */
    private ServerUserSetting() {
        this(null);
    }

    /**
     * Initializes a new {@link ServerUserSetting}.
     *
     * @param connection The connection to use.
     */
    private ServerUserSetting(final Connection connection) {
        super();
        this.connection = connection;
    }

    /**
     * Sets the folder used to store collected contacts.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @param folder folder id
     */
    public void setContactCollectionFolder(final int contextId, final int userId, final Integer folder) throws OXException {
        setAttribute(contextId, userId, CONTACT_COLLECT_FOLDER, folder);
    }

    /**
     * Returns the folder used to store collected contacts.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @return folder id or <code>null</code> if no entry found.
     */
    public Integer getContactCollectionFolder(final int contextId, final int userId) throws OXException {
        return getAttribute(contextId, userId, CONTACT_COLLECT_FOLDER);
    }

    /**
     * Sets the flag for contact collection on incoming mails.
     *
     * @param contextId The context id
     * @param user The user id
     * @param value The flag to set
     * @throws OXException If a setting error occurs
     */
    public void setContactCollectOnMailAccess(final int contextId, final int userId, final boolean value) throws OXException {
        setAttribute(contextId, userId, CONTACT_COLLECT_ON_MAIL_ACCESS, Boolean.valueOf(value));
    }

    /**
     * Gets the flag for contact collection on incoming mails. If <code>null</code> default if <code>false</code>.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @return The flag for contact collection on incoming mails or <code>false</code>
     * @throws OXException If a setting error occurs
     */
    public Boolean isContactCollectOnMailAccess(final int contextId, final int userId) throws OXException {
        final Boolean attribute = getAttribute(contextId, userId, CONTACT_COLLECT_ON_MAIL_ACCESS);
        if (null != attribute) {
            return attribute;
        }
        /*
         * Return as configured
         */
        final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
        return null == service ? Boolean.FALSE : Boolean.valueOf(service.getBoolProperty("com.openexchange.user.contactCollectOnMailAccess", false));
    }

    /**
     * Sets the flag for contact collection on outgoing mails.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @param value The flag to set
     * @throws OXException If a setting error occurs
     */
    public void setContactCollectOnMailTransport(final int contextId, final int userId, final boolean value) throws OXException {
        setAttribute(contextId, userId, CONTACT_COLLECT_ON_MAIL_TRANSPORT, Boolean.valueOf(value));
    }

    /**
     * Gets the flag for contact collection on outgoing mails. If <code>null</code> default if <code>false</code>.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @return The flag for contact collection on outgoing mails or <code>false</code>
     * @throws OXException If a setting error occurs
     */
    public Boolean isContactCollectOnMailTransport(final int contextId, final int userId) throws OXException {
        final Boolean attribute = getAttribute(contextId, userId, CONTACT_COLLECT_ON_MAIL_TRANSPORT);
        if (null != attribute) {
            return attribute;
        }
        /*
         * Return as configured
         */
        final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
        return null == service ? Boolean.FALSE : Boolean.valueOf(service.getBoolProperty("com.openexchange.user.contactCollectOnMailTransport", false));
    }

    /**
     * Returns the default confirmation status for private folders. If no value is set this parameter defaults to 0.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @return
     * @throws OXException If a setting error occurs
     */
    public Integer getDefaultStatusPrivate(final int contextId, final int userId) throws OXException {
        Integer value = getAttribute(contextId, userId, DEFAULT_STATUS_PRIVATE);
        if (value == null) {
            value = I(0);
        }
        return value;
    }

    /**
     * Sets the default confirmation status for private folders. <code>null</code> will default to 0.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @param status
     * @throws OXException
     */
    public void setDefaultStatusPrivate(final int contextId, final int userId, final Integer status) throws OXException {
        setAttribute(contextId, userId, DEFAULT_STATUS_PRIVATE, status);
    }

    /**
     * Returns the default confirmation status for public folders. If no value is set this parameter defaults to 0.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @return
     * @throws OXException
     */
    public Integer getDefaultStatusPublic(final int contextId, final int userId) throws OXException {
        Integer value = getAttribute(contextId, userId, DEFAULT_STATUS_PUBLIC);
        if (value == null) {
            value = I(0);
        }
        return value;
    }

    /**
     * Sets the default confirmation status for public folders. <code>null</code> will default to 0.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @param status
     * @throws OXException
     */
    public void setDefaultStatusPublic(final int contextId, final int userId, final Integer status) throws OXException {
        setAttribute(contextId, userId, DEFAULT_STATUS_PUBLIC, status);
    }

    /**
     * Get the selected folder tree for the user. Return value may be <code>null</code> if the user does not have selected a folder tree.
     *
     * @param contextId The context identifier
     * @param userId The user identifier.
     * @return the selected folder tree or <code>null</code>
     * @throws OXException if reading the value from the database fails.
     */
    public Integer getFolderTree(final int contextId, final int userId) throws OXException {
        return getAttribute(contextId, userId, FOLDER_TREE);
    }

    public void setFolderTree(final int contextId, final int userId, final Integer value) throws OXException {
        setAttribute(contextId, userId, FOLDER_TREE, value);
    }

    private <T> T getAttribute(final int contextId, final int userId, final Attribute<T> attribute) throws OXException {
        if (connection != null) {
            return getAttribute(contextId, userId, attribute, connection);
        }

        Connection con = Database.get(contextId, false);
        try {
            return getAttribute(contextId, userId, attribute, con);
        } finally {
            Database.back(contextId, false, con);
        }
    }

    private <T> void setAttribute(final int contextId, final int userId, final Attribute<T> attribute, final T value) throws OXException {
        if (connection != null) {
            doSetAttribute(contextId, userId, attribute, value, connection);
        } else {
            Connection con = Database.get(contextId, true);
            try {
                doSetAttribute(contextId, userId, attribute, value, con);
            } finally {
                Database.back(contextId, true, con);
            }
        }
    }

    private <T> void doSetAttribute(int contextId, int userId, Attribute<T> attribute, T value, Connection con) throws OXException {
        if (hasEntry(contextId, userId, con)) {
            updateAttribute(contextId, userId, attribute, value, con);
        } else {
            insertAttribute(contextId, userId, attribute, value, con);
        }
    }

    void deleteEntry(final int contextId, final int userId) throws OXException {
        if (connection != null) {
            deleteEntry(contextId, userId, connection);
        } else {
            Connection con = Database.get(contextId, true);
            try {
                deleteEntry(contextId, userId, con);
            } finally {
                Database.back(contextId, true, con);
            }
        }
    }

    private <T> T getAttribute(final int contextId, final int userId, final Attribute<T> attribute, final Connection con) throws OXException {
        T retval = null;
        final String select = "SELECT " + attribute.getColumnName() + " FROM user_setting_server WHERE cid=? AND user=?";
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(select);
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                retval = attribute.getAttribute(rs);
            }
        } catch (SQLException e) {
            throw SettingExceptionCodes.SQL_ERROR.create(e);
        } finally {
            closeSQLStuff(rs, stmt);
        }
        return retval;
    }

    private <T> void updateAttribute(final int contextId, final int userId, final Attribute<T> attribute, final T value, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("UPDATE user_setting_server SET " + attribute.getColumnName() + "=? WHERE cid=? AND user=?");
            attribute.setAttribute(stmt, value);
            stmt.setInt(2, contextId);
            stmt.setInt(3, userId);
            stmt.execute();
        } catch (SQLException e) {
            throw SettingExceptionCodes.SQL_ERROR.create(e);
        } finally {
            closeSQLStuff(stmt);
        }
    }

    private <T> void insertAttribute(final int contextId, final int userId, final Attribute<T> attribute, final T value, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        try {
            int pos = 2;
            {
                final String start = "INSERT INTO user_setting_server (" + attribute.getColumnName();
                if (CONTACT_COLLECT_ON_MAIL_ACCESS.equals(attribute)) {
                    stmt = con.prepareStatement(start + ",contactCollectOnMailTransport,cid,user, uuid) VALUES (?,?,?,?,?)");
                    attribute.setAttribute(stmt, value);
                    /*
                     * As configured
                     */
                    final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                    final boolean b = null == service ? false : service.getBoolProperty("com.openexchange.user.contactCollectOnMailTransport", false);
                    stmt.setBoolean(pos++, b);
                } else if (CONTACT_COLLECT_ON_MAIL_TRANSPORT.equals(attribute)) {
                    stmt = con.prepareStatement(start + ",contactCollectOnMailAccess,cid,user, uuid) VALUES (?,?,?,?,?)");
                    attribute.setAttribute(stmt, value);
                    /*
                     * As configured
                     */
                    final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                    final boolean b = null == service ? false : service.getBoolProperty("com.openexchange.user.contactCollectOnMailAccess", false);
                    stmt.setBoolean(pos++, b);
                } else {
                    stmt = con.prepareStatement(start + ",contactCollectOnMailAccess,contactCollectOnMailTransport,cid,user, uuid) VALUES (?,?,?,?,?,?)");
                    attribute.setAttribute(stmt, value);
                    /*
                     * As configured
                     */
                    final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                    boolean b = null == service ? false : service.getBoolProperty("com.openexchange.user.contactCollectOnMailAccess", false);
                    stmt.setBoolean(pos++, b);
                    b = null == service ? false : service.getBoolProperty("com.openexchange.user.contactCollectOnMailTransport", false);
                    stmt.setBoolean(pos++, b);
                }
            }
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            UUID uuid = UUID.randomUUID();
            byte[] uuidBinary = UUIDs.toByteArray(uuid);
            stmt.setBytes(pos, uuidBinary);
            LOG.debug("INSERTing user settings: {}", DBUtils.getStatementString(stmt));
            stmt.execute();
        } catch (SQLException e) {
            throw SettingExceptionCodes.SQL_ERROR.create(e);
        } finally {
            closeSQLStuff(null, stmt);
        }
    }

    private void deleteEntry(final int contextId, final int userId, final Connection con) throws OXException {
        PreparedStatement stmt = null;
        final ResultSet rs = null;
        try {
            stmt = con.prepareStatement("DELETE FROM user_setting_server WHERE cid=? AND user=?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw SettingExceptionCodes.SQL_ERROR.create(e);
        } finally {
            closeSQLStuff(rs, stmt);
        }
    }

    private boolean hasEntry(final int contextId, final int userId, final Connection con) throws OXException {
        boolean retval = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT 1 FROM user_setting_server WHERE cid=? AND user=?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            retval = rs.next();
        } catch (SQLException e) {
            throw SettingExceptionCodes.SQL_ERROR.create(e);
        } finally {
            closeSQLStuff(rs, stmt);
        }
        return retval;
    }

    private static interface Attribute<T> {

        void setAttribute(PreparedStatement pstmt, T value) throws SQLException;

        T getAttribute(ResultSet rs) throws SQLException;

        String getColumnName();
    }

}
