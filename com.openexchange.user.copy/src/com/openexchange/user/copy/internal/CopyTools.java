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

package com.openexchange.user.copy.internal;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.user.User;
import com.openexchange.user.copy.ObjectMapping;
import com.openexchange.user.copy.UserCopyExceptionCodes;

/**
 * {@link CopyTools}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 */
public class CopyTools {

    private final Map<String, ObjectMapping<?>> copied;

    private ObjectMapping<Integer> contextIdMapping = null;
    private ObjectMapping<Context> contextMapping = null;
    private ObjectMapping<Integer> userIdMapping = null;
    private ObjectMapping<User> userMapping = null;
    private ObjectMapping<Connection> connectionMapping = null;
    private ObjectMapping<FolderObject> folderMapping = null;

    public CopyTools(final Map<String, ObjectMapping<?>> copied) {
        super();
        this.copied = copied;
    }

    public Integer getSourceContextId() throws OXException {
        checkContextIdMapping();
        return contextIdMapping.getSource(0);
    }

    public Integer getDestinationContextId() throws OXException {
        checkContextIdMapping();
        return contextIdMapping.getDestination(I(0));
    }

    public Context getSourceContext() throws OXException {
        checkContextMapping();
        return contextMapping.getSource(i(getSourceContextId()));
    }

    public Context getDestinationContext() throws OXException {
        checkContextMapping();
        return contextMapping.getDestination(getSourceContext());
    }

    public Integer getSourceUserId() throws OXException {
        checkUserIdMapping();
        return userIdMapping.getSource(0);
    }

    public Integer getDestinationUserId() throws OXException {
        return I(getDestinationUser().getId());
    }

    public User getSourceUser() throws OXException {
        checkUserMapping();
        return userMapping.getSource(i(getSourceUserId()));
    }

    public User getDestinationUser() throws OXException {
        checkUserMapping();
        return userMapping.getDestination(getSourceUser());
    }

    public Connection getSourceConnection() throws OXException {
        checkConnectionMapping();
        return connectionMapping.getSource(i(getSourceContextId()));
    }

    public Connection getDestinationConnection() throws OXException {
        checkConnectionMapping();
        return connectionMapping.getDestination(getSourceConnection());
    }

    public ObjectMapping<FolderObject> getFolderMapping() throws OXException {
        checkFolderMapping();
        return folderMapping;
    }


    /*
     * Static methods
     */
    public static String replaceIdsInQuery(final String placeholder, final String statement, final Collection<Integer> ids) {
        if (null == ids || ids.isEmpty()) {
            // Nothing to replace with
            return statement.replaceFirst(placeholder, "");
        }
        final StringBuilder folderIdString = new StringBuilder(32);
        for (final int id : ids) {
            folderIdString.append(',').append(id);
        }
        folderIdString.deleteCharAt(0);
        final String selectStatement = statement.replaceFirst(placeholder, folderIdString.toString());
        return selectStatement;
    }

    public static void setStringOrNull(final int parameter, final PreparedStatement stmt, final String value) throws SQLException {
        if (value == null) {
            stmt.setNull(parameter, java.sql.Types.VARCHAR);
        } else {
            stmt.setString(parameter, value);
        }
    }

    public static void setBinaryOrNull(int parameter, PreparedStatement stmt, byte[] value) throws SQLException {
        if (value == null) {
            stmt.setNull(parameter, java.sql.Types.BINARY);
        } else {
            stmt.setBytes(parameter, value);
        }
    }

    public static void setIntOrNull(final int parameter, final PreparedStatement stmt, final int value) throws SQLException {
        if (value == -1) {
            stmt.setNull(parameter, java.sql.Types.VARCHAR);
        } else {
            stmt.setInt(parameter, value);
        }
    }

    public static int getIntOrNegative(final int parameter, final ResultSet rs) throws SQLException {
        int value = rs.getInt(parameter);
        if (rs.wasNull()) {
            value = -1;
        }

        return value;
    }

    public static void setLongOrNull(final int parameter, final PreparedStatement stmt, final Date date) throws SQLException {
        if (date == null) {
            stmt.setNull(parameter, java.sql.Types.BIGINT);
        } else {
            stmt.setLong(parameter, date.getTime());
        }
    }


    /*
     * Extraction methods
     */

    private void checkConnectionMapping() throws OXException {
        if (connectionMapping == null) {
            connectionMapping = checkAndExtractGenericMapping(Connection.class.getName());
        }
    }

    private void checkContextIdMapping() throws OXException {
        if (contextIdMapping == null) {
            contextIdMapping = checkAndExtractGenericMapping(Constants.CONTEXT_ID_KEY);
        }
    }

    private void checkContextMapping() throws OXException {
        if (null == contextMapping) {
            contextMapping = checkAndExtractGenericMapping(Context.class.getName());
        }
    }

    private void checkUserIdMapping() throws OXException {
        if (userIdMapping == null) {
            userIdMapping = checkAndExtractGenericMapping(Constants.USER_ID_KEY);
        }
    }

    private void checkUserMapping() throws OXException {
        if (userMapping == null) {
            userMapping = checkAndExtractGenericMapping(User.class.getName());
        }
    }

    private void checkFolderMapping() throws OXException {
        if (folderMapping == null) {
            folderMapping = checkAndExtractGenericMapping(com.openexchange.groupware.container.FolderObject.class.getName());
        }
    }

    @SuppressWarnings("unchecked")
    public <T> ObjectMapping<T> checkAndExtractGenericMapping(final String key) throws OXException {
        final ObjectMapping<?> tmp = copied.get(key);
        if (tmp != null) {
            return (ObjectMapping<T>) tmp;
        }

        throw UserCopyExceptionCodes.UNKNOWN_PROBLEM.create();
    }
}
