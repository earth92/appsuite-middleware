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

package liquibase.precondition.ext;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.commons.lang.Validate;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.precondition.CustomPrecondition;

/**
 * Verifies the size of the database column by ignoring the type of the column!
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.6.1
 */
public class ColumnSizePrecondition implements CustomPrecondition {

    private int expectedSize;

    private String tableName;

    private String columnName;

    /**
     * Sets the expected size of the column (by reflection from liquibase)
     *
     * @param expectedSize - String with the size
     */
    public void setExpectedSize(String expectedSize) {
        this.expectedSize = Integer.parseInt(expectedSize);
    }

    /**
     * Sets the tableName (by reflection from liquibase)
     *
     * @param tableName The tableName to set
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Sets the columnName (by reflection from liquibase)
     *
     * @param columnName The columnName to set
     */
    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void check(final Database database) throws CustomPreconditionFailedException, CustomPreconditionErrorException {
        try {
            Validate.notNull(database, "Database provided by Liquibase might not be null!");

            DatabaseConnection databaseConnection = database.getConnection();
            Validate.notNull(databaseConnection, "DatabaseConnection might not be null!");

            JdbcConnection connection = null;
            if (databaseConnection instanceof JdbcConnection) {
                connection = (JdbcConnection)databaseConnection;
            } else {
                throw new CustomPreconditionErrorException("Cannot get underlying connection because database connection is not from type JdbcConnection. Type is: " + databaseConnection.getClass().getName());
            }

            boolean columnFound = false;
            ResultSet rsColumns = null;
            try {
                DatabaseMetaData meta = connection.getUnderlyingConnection().getMetaData();
                rsColumns = meta.getColumns(null, null, tableName, null);
                if (!rsColumns.next()) {
                    throw new CustomPreconditionErrorException("No columns for table " + tableName + " found! Aborting database migration execution for the given changeset.");
                }
                rsColumns.beforeFirst();

                while (rsColumns.next()) {
                    final String lColumnName = rsColumns.getString("COLUMN_NAME");
                    if (columnName.equals(lColumnName)) {
                        columnFound = true;
                        final int size = rsColumns.getInt("COLUMN_SIZE");
                        if (size == expectedSize) {
                            throw new CustomPreconditionFailedException("Column size is already up to date! Nothing to do.");
                        }
                    }
                }
            } catch (SQLException sqlException) {
                throw new CustomPreconditionErrorException("Error while evaluating type of column " + columnName + " in table " + tableName + ".", sqlException);
            } finally {
                if (null != rsColumns) {
                    try { rsColumns.close(); } catch (SQLException e) { /* Ignore */ }
                }
            }
            if (!columnFound) {
                throw new CustomPreconditionErrorException("Desired column to update not found! Tried update for column " + columnName + " on table " + tableName);
            }
        } catch (CustomPreconditionErrorException e) {
            throw e;
        } catch (CustomPreconditionFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomPreconditionErrorException("Unexpected error", e);
        }
    }
}
