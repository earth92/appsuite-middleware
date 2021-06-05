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

package com.openexchange.admin.storage.mysqlStorage.user.attribute.changer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.database.Databases;

/**
 * {@link AbstractAttributeChanger}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.1
 */
public abstract class AbstractAttributeChanger {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAttributeChanger.class);

    protected static final String TABLE_TOKEN = "<TABLE>";
    protected static final String COLUMN_TOKEN = "<COLUMN>";

    protected final Map<Class<?>, Setter> setters;
    protected final Map<Class<?>, Unsetter> unsetters;

    /**
     * Initialises a new {@link AbstractAttributeChanger}.
     */
    public AbstractAttributeChanger() {
        super();

        Map<Class<?>, Setter> s = new HashMap<>();
        s.put(Byte.class, (stmt, x, parameterIndex) -> stmt.setBytes(parameterIndex, (byte[]) x));
        s.put(String.class, (stmt, x, parameterIndex) -> stmt.setString(parameterIndex, (String) x));
        s.put(Boolean.class, (stmt, x, parameterIndex) -> stmt.setBoolean(parameterIndex, ((Boolean) x).booleanValue()));
        s.put(Integer.class, (stmt, x, parameterIndex) -> stmt.setInt(parameterIndex, ((Integer) x).intValue()));
        s.put(Long.class, (stmt, x, parameterIndex) -> stmt.setLong(parameterIndex, ((Long) x).longValue()));
        s.put(Date.class, (stmt, x, parameterIndex) -> stmt.setTimestamp(parameterIndex, new Timestamp(((Long) x).longValue())));

        Map<Class<?>, Unsetter> u = new HashMap<>();
        u.put(Byte.class, (stmt, parameterIndex) -> stmt.setNull(parameterIndex, Types.BINARY));
        u.put(String.class, (stmt, parameterIndex) -> stmt.setNull(parameterIndex, Types.VARCHAR));
        u.put(Boolean.class, (stmt, parameterIndex) -> stmt.setNull(parameterIndex, Types.BOOLEAN));
        u.put(Integer.class, (stmt, parameterIndex) -> stmt.setNull(parameterIndex, Types.INTEGER));
        u.put(Long.class, (stmt, parameterIndex) -> stmt.setNull(parameterIndex, Types.BIGINT));
        u.put(Date.class, (stmt, parameterIndex) -> stmt.setNull(parameterIndex, Types.DATE));

        setters = Collections.unmodifiableMap(s);
        unsetters = Collections.unmodifiableMap(u);
    }

    /**
     * Sets the specified {@link Attribute}s
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param attributes The {@link Attribute}s to set
     * @param value The value to set
     * @param connection The {@link Connection}
     * @return <code>true</code> if the attribute was successfully set; <code>false</code> if there is no
     *         associated {@link Setter} for the specified {@link Attribute}, or if the value is <code>null</code>
     *         or empty.
     * @throws SQLException if an SQL error is occurred
     */
    protected boolean setAttributes(int userId, int contextId, String table, Map<Attribute, Object> attributes, Connection connection) throws SQLException {
        Map<Attribute, Setter> settersMap = new HashMap<>();
        for (Attribute attribute : attributes.keySet()) {
            Setter setter = setters.get(attribute.getOriginalType());
            if (setter == null) {
                LOG.debug("No setter found for attribute '{}' in table '{}'. That attribute will not be set", attribute.getSQLFieldName(), attribute.getSQLTableName());
                continue;
            }
            settersMap.put(attribute, setter);
        }
        if (settersMap.isEmpty()) {
            return false;
        }

        PreparedStatement stmt = prepareStatement(table, attributes.keySet(), connection);
        try {
            if (stmt == null) {
                return false;
            }
            fillSetStatement(stmt, settersMap, attributes, userId, contextId);
            return stmt.executeUpdate() == 1; // we expect exactly one change
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     * Resets the specified attributes to their default values
     *
     * @param userId The user identifier
     * @param contextId the context identifier
     * @param table the table
     * @param attributes The {@link Attribute} to reset
     * @param connection the {@link Connection}
     * @return <code>true</code> if the attributes was successfully reset; <code>false</code> otherwise
     * @throws SQLException if an SQL error is occurred
     */
    protected boolean setAttributesDefault(int userId, int contextId, String table, Set<Attribute> attributes, Connection connection) throws SQLException {
        PreparedStatement stmt = prepareDefaultStatement(table, attributes, connection);
        try {
            if (stmt == null) {
                return false;
            }
            int parameterIndex = 1;
            appendContextUser(contextId, userId, stmt, parameterIndex);
            return stmt.executeUpdate() == 1; // we expect exactly one change
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     * Un-sets the specified {@link Attribute}s
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param attributes The {@link Attribute}s to un-set
     * @param connection The {@link Connection}
     * @return <code>true</code> if the attribute was successfully un-set; <code>false</code> otherwise
     * @throws SQLException if an SQL error is occurred
     */
    protected boolean unsetAttributes(int userId, int contextId, String table, Set<Attribute> attributes, Connection connection) throws SQLException {
        Map<Attribute, Unsetter> unsettersMap = new HashMap<>();
        for (Attribute attribute : attributes) {
            Unsetter setter = unsetters.get(attribute.getOriginalType());
            if (setter == null) {
                LOG.debug("No unsetter found for attribute '{}' in table '{}'. That attribute will not be set", attribute.getSQLFieldName(), attribute.getSQLTableName());
                continue;
            }
            unsettersMap.put(attribute, setter);
        }
        if (unsettersMap.isEmpty()) {
            return false;
        }
        PreparedStatement stmt = prepareStatement(table, attributes, connection);
        try {
            if (stmt == null) {
                return false;
            }
            fillUnsetStatement(stmt, unsettersMap, attributes, userId, contextId);
            return stmt.executeUpdate() == 1; // we expect exactly one change
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     * Appends the context and user identifiers (in that order) to the specified {@link PreparedStatement} in the positions
     * indicated by the parameter index.
     *
     * @param contextId the context identifier
     * @param userId The user identifier
     * @param stmt The {@link PreparedStatement}
     * @param parameterIndex The position at which the user and context identifiers will be set
     *
     * @throws SQLException if an SQL error is occurred
     */
    protected int appendContextUser(int contextId, int userId, PreparedStatement stmt, int parameterIndex) throws SQLException {
        int index = parameterIndex;
        stmt.setInt(index++, contextId);
        stmt.setInt(index++, userId);
        return index;
    }

    /**
     * Prepares the specified {@link Attribute}s for an SQL statement
     *
     * @param attributes The {@link Attribute}s to prepare
     * @return A string with the prepared {@link Attribute}s
     */
    protected String prepareAttributes(Set<Attribute> attributes) {
        StringBuilder builder = new StringBuilder();
        for (Attribute attribute : attributes) {
            builder.append(attribute.getSQLFieldName()).append("=?,");
        }
        builder.setLength(builder.length() - 1);
        return builder.toString();
    }

    /**
     * Fills the specified {@link PreparedStatement} with the specified user id, context id and value for the specified {@link Attribute}
     *
     * @param stmt The {@link PreparedStatement} to fill
     * @param setter The value {@link Setter}
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param value The value to set
     * @param attribute The {@link Attribute}
     * @throws SQLException if an SQL error is occurred
     */
    protected abstract int fillSetStatement(PreparedStatement stmt, Map<Attribute, Setter> setters, Map<Attribute, Object> attributes, int userId, int contextId) throws SQLException;

    /**
     * Fills the specified {@link PreparedStatement} with the specified user id, context id and value for the specified {@link Attribute}
     *
     * @param stmt The {@link PreparedStatement} to fill
     * @param unsetter The value {@link Unsetter}
     * @param userId The user identifier
     * @param contextId The context identifier
     * @throws SQLException if an SQL error is occurred
     */
    protected abstract int fillUnsetStatement(PreparedStatement stmt, Map<Attribute, Unsetter> unsetters, Set<Attribute> attributes, int userId, int contextId) throws SQLException;

    /**
     * Creates a new {@link PreparedStatement} for the specified {@link Attribute}s
     *
     * @param attributes the {@link Attribute}s
     * @param connection The {@link Connection}
     * @return The {@link PreparedStatement}
     * @throws SQLException if an SQL error is occurred
     */
    protected abstract PreparedStatement prepareStatement(String table, Set<Attribute> attributes, Connection connection) throws SQLException;

    /**
     * Creates a new {@link PreparedStatement} for the specified {@link Attribute}s to set them
     * to their 'DEFAULT' values
     *
     * @param attribute the {@link Attribute}
     * @param connection The {@link Connection}
     * @return The {@link PreparedStatement}
     * @throws SQLException if an SQL error is occurred
     */
    protected abstract PreparedStatement prepareDefaultStatement(String table, Set<Attribute> attributes, Connection connection) throws SQLException;

    //////////////////////////////// PRIVATE INTERFACES ///////////////////////////////

    /**
     * {@link Setter} - Private functional Setter interface
     */
    public interface Setter {

        /**
         * Sets the specified value to the specified {@link PreparedStatement} at the specified position
         *
         * @param stmt The {@link PreparedStatement}
         * @param value The value to set
         * @param parameterIndex The position at which the value will be set
         * @throws SQLException if an SQL error is occurred
         */
        void set(PreparedStatement stmt, Object value, int parameterIndex) throws SQLException;
    }

    /**
     * {@link Setter} - Private functional Unsetter interface
     */
    public interface Unsetter {

        /**
         * Un-sets the value from the specified {@link PreparedStatement} and the specified position
         *
         * @param stmt The {@link PreparedStatement}
         * @param parameterIndex The position at which the value will be un-set
         * @throws SQLException if an SQL error is occurred
         */
        void unset(PreparedStatement stmt, int parameterIndex) throws SQLException;
    }
}
