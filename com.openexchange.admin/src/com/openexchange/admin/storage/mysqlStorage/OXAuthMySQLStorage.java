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

package com.openexchange.admin.storage.mysqlStorage;

import static com.openexchange.admin.storage.mysqlStorage.AdminMySQLStorageUtil.leaseConnectionForContext;
import static com.openexchange.admin.storage.mysqlStorage.AdminMySQLStorageUtil.releaseWriteContextConnectionAfterReading;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.openexchange.admin.daemons.ClientAdminThread;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Credentials;
import com.openexchange.admin.rmi.exceptions.NoSuchUserException;
import com.openexchange.admin.rmi.exceptions.StorageException;
import com.openexchange.admin.storage.interfaces.OXAuthStorageInterface;
import com.openexchange.admin.storage.interfaces.OXToolStorageInterface;
import com.openexchange.admin.tools.AdminCache;
import com.openexchange.admin.tools.GenericChecks;
import com.openexchange.database.Databases;

/**
 * Default MySQL implementation for administrator authentication.
 *
 * @author cutmasta
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class OXAuthMySQLStorage extends OXAuthStorageInterface {

    private static final String SELECT_USER = "SELECT u.userPassword,u.passwordMech,u.salt FROM user u JOIN login2user l ON u.id = l.id AND u.cid = l.cid WHERE u.cid = ? AND l.uid = ?";

    private static final String SELECT_CONTEXT_ADMIN = "SELECT u.userPassword,u.passwordMech,u.salt FROM user u JOIN login2user l JOIN user_setting_admin usa ON u.id = l.id AND u.cid = l.cid AND u.cid = usa.cid AND u.id = usa.user WHERE u.cid = ? AND l.uid = ?";

    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OXAuthMySQLStorage.class);

    private final AdminCache cache;

    /**
     * Initialises a new {@link OXAuthMySQLStorage}.
     */
    public OXAuthMySQLStorage() {
        super();
        cache = ClientAdminThread.cache;
    }

    @Override
    public boolean authenticate(final Credentials authdata) {
        return false;
    }

    @Override
    public boolean authenticate(final Credentials authdata, final Context ctx) throws StorageException {
        if (!validCredentials(authdata)) {
            return false;
        }

        if (!isAdmin(authdata, ctx)) {
            return false;
        }

        return authenticate(authdata, ctx, SELECT_CONTEXT_ADMIN, true);
    }

    @Override
    public boolean authenticateUser(final Credentials authdata, final Context ctx) throws StorageException {
        if (!validCredentials(authdata)) {
            return false;
        }
        return authenticate(authdata, ctx, SELECT_USER, false);
    }

    //////////////////////////////////////////////// HELPERS /////////////////////////////////////////////////////

    /**
     * Checks if the specified {@link Credentials} object holds information about the context administrator
     * of the specified {@link Context}
     *
     * @param credentials The {@link Credentials} object
     * @param context The {@link Context}
     * @return <code>true</code> if the {@link Credentials} do hold information about the context administrator;
     *         <code>false</code> otherwise
     * @throws StorageException if no such user exists
     */
    private boolean isAdmin(Credentials credentials, Context context) throws StorageException {
        try {
            OXToolStorageInterface instance = OXToolStorageInterface.getInstance();
            int uid = instance.getUserIDByUsername(context, credentials.getLogin());
            return instance.isContextAdmin(context, uid);
        } catch (NoSuchUserException e) {
            throw new StorageException(e);
        }
    }

    /**
     * Checks the specified {@link Credentials} object for validity:
     * <ul>
     * <li>Whether the object is <code>null</code>
     * <li>Whether the login and/or the password are <code>null</code>
     * </ul>
     * In case one of the above is <code>null</code>, <code>false</code> will be returned; <code>true</code>otherwise
     *
     * @param credentials The {@link Credentials} object to check
     * @return In case the provided {@link Credentials}, and/or the username and/or the password are <code>null</code>
     *         then <code>false</code> will be returned; <code>true</code>otherwise
     */
    private boolean validCredentials(Credentials credentials) {
        if (credentials == null) {
            return false;
        }
        if (credentials.getLogin() == null) {
            return false;
        }
        if (credentials.getPassword() == null) {
            return false;
        }
        return true;
    }

    /**
     * Performs the authentication
     *
     * @param credentials the {@link Credentials}
     * @param context The {@link Context}
     * @param query The SQL query to perform
     * @param isAdmin <code>true</code> if the specified {@link Credentials} denote an admin user
     * @return <code>true</code> if the user is successfully authenticated; <code>false</code> otherwise
     * @throws StorageException If a storage error is occurred
     */
    private boolean authenticate(Credentials credentials, Context context, String query, boolean isAdmin) throws StorageException {
        int contextId = context.getId().intValue();

        Connection connection = null;
        PreparedStatement prep = null;
        ResultSet rs = null;
        try {
            connection = leaseConnectionForContext(contextId, cache);
            prep = connection.prepareStatement(query);
            prep.setInt(1, contextId);
            prep.setString(2, credentials.getLogin());

            rs = prep.executeQuery();
            if (!rs.next()) {
                log.debug("{} \"{}\" not found in context \"{}\"!", isAdmin ? "Admin user" : "User", credentials.getLogin(), I(contextId));
                return false;
            }
            String encryptedPassword = rs.getString("userPassword");
            String passwordMechanism = rs.getString("passwordMech");
            byte[] salt = rs.getBytes("salt");
            // Check via our crypt mech the password
            if (!GenericChecks.authByMech(encryptedPassword, credentials.getPassword(), passwordMechanism, salt)) {
                log.debug("Password for {} \"{}\" did not match!", isAdmin ? "admin user" : "user", credentials.getLogin());
                return false;
            }

            // Set admin creds to cache
            if (isAdmin) {
                Credentials cauth = new Credentials(credentials.getLogin(), encryptedPassword);
                cache.setAdminCredentials(context, passwordMechanism, cauth);
            }
            return true;
        } catch (SQLException e) {
            log.error("", e);
            throw new StorageException(e);
        } finally {
            Databases.closeSQLStuff(rs, prep);
            releaseWriteContextConnectionAfterReading(connection, contextId, cache);
        }
    }
}
