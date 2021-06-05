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

package com.openexchange.database.internal;

import java.sql.Connection;
import java.util.Map;
import com.openexchange.database.Assignment;
import com.openexchange.exception.OXException;

/**
 * {@link ContextDatabaseAssignmentService}
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
interface ContextDatabaseAssignmentService {

    /**
     * Gets a database assignment for a context. If the cache is enabled this method looks into the cache for the assignment and loads it
     * from the database if cache is disabled or the cache doesn't contain the entry.
     *
     * @param contextId The unique identifier of the context.
     * @return the assignment.
     * @throws OXException If getting the assignment fails.
     */
    AssignmentImpl getAssignment(int contextId) throws OXException;

    /**
     * Invalidates the assignments for one or more contexts in the cache.
     * @param con writable connection to the config database in a transaction.
     * @param contextIds The unique identifiers of the contexts
     * @throws OXException If getting the server identifier fails.
     */
    void invalidateAssignment(int... contextIds) throws OXException;

    /**
     * Writes a database assignment for a context into the database. Normally this is done within a transaction on the config database.
     * Therefore a connection to the config database must be given. This connections needs to be to the write host and in a transaction.
     * This method can overwrite existing assignments.
     * @param con writable database connection to the config database.
     * @param assignment database assignment for a context that should be written.
     * @throws OXException if writing to the persistent storage fails.
     */
    void writeAssignment(Connection con, Assignment assignment) throws OXException;

    /**
     * Deletes a database assignment for the given context. This should be done within a transaction on the config database.
     * @param con writable database connection to the config database. This connection should be in a transaction.
     * @param contextId identifier of the context that database assignment should be deleted.
     * @throws OXException if deleting in the persistent storage fails.
     */
    void deleteAssignment(Connection con, int contextId) throws OXException;

    /**
     * Determines all context IDs which reside in given schema.
     * @param con a connection to the config database
     * @param schema the database schema
     * @param writePoolId corresponding write pool ID (master database)
     * @return an array of <code>int</code> representing all retrieved context identifier
     * @throws OXException if there is no connection to the config database slave is available or reading from the database fails.
     */
    int[] getContextsFromSchema(Connection con, int writePoolId, String schema) throws OXException;

    /**
     * Retrieves the identifiers of all contexts that reside in specified database host.
     *
     * @param poolId The identifier of the database host
     * @param offset The start offset or <code>-1</code> to retrieve all
     * @param length The max. number of context identifiers to return or <code>-1</code> to retrieve all
     * @return The context identifiers
     * @throws OXException If context identifiers cannot be returned
     */
    int[] getContextsInDatabase(int poolId, int offset, int length) throws OXException;

    String[] getUnfilledSchemas(Connection con, int poolId, int maxContexts) throws OXException;

    /**
     * Gets the number of contexts per schema that are located in given database identified by <code>poolId</code>.
     *
     * @param con The connection to the config database
     * @param poolId The pool identifier
     * @param maxContexts The max. number of contexts per schema
     * @return A maping providing the count per schema
     * @throws OXException If schema count cannot be returned
     */
    Map<String, Integer> getContextCountPerSchema(Connection con, int poolId, int maxContexts) throws OXException;

    /**
     * Acquires a global lock for specified database
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * <b>Note</b>: Given connection is required to be in transaction mode.
     * </div>
     * <p>
     *
     * @param con The connection (in transaction mode)
     * @param writePoolId The identifier of the (read-write) database for which to acquire a lock
     * @throws OXException If lock cannot be acquired
     */
    void lock(Connection con, int writePoolId) throws OXException;

    /**
     * Gets all existing schemas in this installation.
     *
     * @param con The connection to the config database
     * @return All existing schemas as a mapping from schema-name to read DB pool ID
     * @throws OXException If all existing schemas cannot be retrieved from config database
     */
    Map<String, Integer> getAllSchemata(Connection con) throws OXException;
}
