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

/**
 *
 */
package com.openexchange.admin.contextrestore.storage.mysqlStorage;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import com.openexchange.admin.contextrestore.rmi.exceptions.OXContextRestoreException;
import com.openexchange.admin.contextrestore.rmi.exceptions.OXContextRestoreException.Code;
import com.openexchange.admin.contextrestore.rmi.impl.OXContextRestore.Parser.PoolIdSchemaAndVersionInfo;
import com.openexchange.admin.contextrestore.storage.sqlStorage.OXContextRestoreSQLStorage;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.exceptions.StorageException;
import com.openexchange.database.Databases;
import com.openexchange.databaseold.Database;
import com.openexchange.exception.OXException;

/**
 * This class contains all the mysql database related code
 *
 * @author <a href="mailto:dennis.sieben@open-xchange.com">Dennis Sieben</a>
 *
 */
public final class OXContextRestoreMySQLStorage extends OXContextRestoreSQLStorage {

    private final static org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OXContextRestoreMySQLStorage.class);

    @Override
    public String restorectx(final Context ctx, final PoolIdSchemaAndVersionInfo poolidandschema, String configdbname) throws SQLException, IOException, OXContextRestoreException, StorageException {
        Connection connection = null;
        Connection connection2 = null;
        PreparedStatement prepareStatement = null;
        PreparedStatement prepareStatement2 = null;
        PreparedStatement prepareStatement3 = null;
        final int poolId = poolidandschema.getPoolId();
        boolean doRollback = false;
        final Map<String, File> tempfilemap = poolidandschema.getTempfilemap();
        try {
            File file = tempfilemap.get(poolidandschema.getSchema());
            BufferedReader reader;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            } catch (FileNotFoundException e1) {
                throw new OXContextRestoreException(Code.CONFIGDB_FILE_NOT_FOUND, e1);
            }
            try {
                connection = Database.get(poolId, poolidandschema.getSchema());
                connection.setAutoCommit(false);
                doRollback = true;
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        prepareStatement = connection.prepareStatement(line);
                        prepareStatement.execute();
                        prepareStatement.close();
                    } catch (SQLException e) {
                        LOG.error("Executing the following SQL statement failed: {}", line, e);
                        throw e;
                    }
                }
            } finally {
                close(reader);
            }
            file = tempfilemap.get(configdbname);
            try {
                reader = new BufferedReader(new FileReader(file));
            } catch (FileNotFoundException e1) {
                throw new OXContextRestoreException(Code.USERDB_FILE_NOT_FOUND);
            }
            try {
                connection2 = Database.get(true);
                connection2.setAutoCommit(false);
                doRollback = true;
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        prepareStatement2 = connection2.prepareStatement(line);
                        prepareStatement2.execute();
                        prepareStatement2.close();
                    } catch (SQLException e) {
                        LOG.error("Executing the following SQL statement failed: {}", line, e);
                        throw e;
                    }
                }
            } finally {
                close(reader);
            }
            connection.commit();
            connection2.commit();
            doRollback = false;

            connection2.setAutoCommit(true);
            prepareStatement3 = connection2.prepareStatement("SELECT `filestore_name`, `uri` FROM `context` INNER JOIN `filestore` ON context.filestore_id = filestore.id WHERE cid=?");
            prepareStatement3.setInt(1, ctx.getId().intValue());
            final ResultSet executeQuery = prepareStatement3.executeQuery();
            if (!executeQuery.next()) {
                throw new OXContextRestoreException(Code.NO_FILESTORE_VALUE);
            }
            final String filestore_name = executeQuery.getString(1);
            final String uri = executeQuery.getString(2);
            return uri + File.separatorChar + filestore_name;
        } catch (OXException e) {
            throw new StorageException(e.getMessage(), e);
        } finally {
            if (doRollback) {
                dorollback(connection, connection2);
            }
            Databases.closeSQLStuff(prepareStatement, prepareStatement2, prepareStatement3);
            if (null != connection) {
                autocommit(connection);
                Database.back(poolId, connection);
            }
            if (null != connection2) {
                autocommit(connection2);
                Database.back(true, connection2);
            }
            for (final File file : tempfilemap.values()) {
                file.delete();
            }
        }
    }

    private static void dorollback(final Connection... connections) {
        for (final Connection con : connections) {
            Databases.rollback(con);
        }
    }

    /**
     * Convenience method to set the auto-commit of a connection to <code>true</code>.
     *
     * @param con connection that should go into auto-commit mode.
     */
    private static void autocommit(final Connection con) {
        if (null == con) {
            return;
        }
        try {
            if (!con.isClosed() && !con.getAutoCommit()) {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOG.error("", e);
        }
    }

    /**
     * Safely closes specified {@link Closeable} instance.
     *
     * @param toClose The {@link Closeable} instance
     */
    private static void close(final Closeable toClose) {
        if (null != toClose) {
            try {
                toClose.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

}
