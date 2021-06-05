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

package com.openexchange.admin.storage.interfaces;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import com.openexchange.admin.daemons.ClientAdminThreadExtended;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Database;
import com.openexchange.admin.rmi.dataobjects.Filestore;
import com.openexchange.admin.rmi.dataobjects.MaintenanceReason;
import com.openexchange.admin.rmi.dataobjects.Server;
import com.openexchange.admin.rmi.dataobjects.User;
import com.openexchange.admin.rmi.exceptions.StorageException;
import com.openexchange.admin.tools.AdminCacheExtended;
import com.openexchange.admin.tools.PropertyHandler;
import com.openexchange.admin.tools.PropertyHandlerExtended;

/**
 * This interface provides an abstraction to the storage of the util information
 *
 * @author d7
 * @author cutmasta
 *
 */
public abstract class OXUtilStorageInterface {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OXUtilStorageInterface.class);

    private static volatile OXUtilStorageInterface instance;

    /**
     * Creates a new instance implementing the group storage interface.
     *
     * @return an instance implementing the group storage interface.
     * @throws com.openexchange.admin.rmi.exceptions.StorageException Storage exception
     */
    public static OXUtilStorageInterface getInstance() throws StorageException {
        OXUtilStorageInterface inst = instance;
        if (null == inst) {
            synchronized (OXUtilStorageInterface.class) {
                inst = instance;
                if (null == inst) {
                    Class<? extends OXUtilStorageInterface> implementingClass;
                    AdminCacheExtended cache = ClientAdminThreadExtended.cache;
                    PropertyHandler prop = cache.getProperties();
                    final String className = prop.getProp(PropertyHandlerExtended.UTIL_STORAGE, null);
                    if (null != className) {
                        try {
                            implementingClass = Class.forName(className).asSubclass(OXUtilStorageInterface.class);
                        } catch (ClassNotFoundException e) {
                            log.error("", e);
                            throw new StorageException(e);
                        }
                    } else {
                        final StorageException storageException = new StorageException("Property for util_storage not defined");
                        log.error("", storageException);
                        throw storageException;
                    }

                    Constructor<? extends OXUtilStorageInterface> cons;
                    try {
                        cons = implementingClass.getConstructor(new Class[] {});
                        inst = cons.newInstance(new Object[] {});
                        instance = inst;
                    } catch (SecurityException e) {
                        log.error("", e);
                        throw new StorageException(e);
                    } catch (NoSuchMethodException e) {
                        log.error("", e);
                        throw new StorageException(e);
                    } catch (IllegalArgumentException e) {
                        log.error("", e);
                        throw new StorageException(e);
                    } catch (InstantiationException e) {
                        log.error("", e);
                        throw new StorageException(e);
                    } catch (IllegalAccessException e) {
                        log.error("", e);
                        throw new StorageException(e);
                    } catch (InvocationTargetException e) {
                        log.error("", e);
                        throw new StorageException(e);
                    }
                }
            }
        }
        return inst;
    }

    /**
     * Register filestore in configbdb.
     *
     * @param fstore
     *            filestore object
     * @return the id of the created filestore as a long.
     * @throws StorageException
     */
    public abstract int registerFilestore(final Filestore fstore) throws StorageException;

    /**
     * Changes a given filestore
     *
     * @param fstore
     *            filestore object
     * @throws StorageException
     */
    public abstract void changeFilestore(final Filestore fstore) throws StorageException;

    /**
     * @param ctx Context with Filestore data set!
     * @throws StorageException
     */
    public abstract void changeFilestoreDataFor(Context ctx) throws StorageException;

    /**
     * @param ctx Context with Filestore data set!
     * @param configDbCon The connection to use
     * @throws StorageException
     */
    public abstract void changeFilestoreDataFor(Context ctx, Connection configDbCon) throws StorageException;

    /**
     * @param user The associated user
     * @param ctx Context with Filestore data set!
     * @param con The connection to use
     * @throws StorageException
     */
    public abstract void changeFilestoreDataFor(User user, Context ctx) throws StorageException;

    /**
     * @param user The associated user
     * @param ctx Context with Filestore data set!
     * @param con The connection to use
     * @throws StorageException
     */
    public abstract void changeFilestoreDataFor(User user, Context ctx, Connection con) throws StorageException;

    /**
     * Prepares filestore usage for given user
     *
     * @param user The user
     * @param ctx The context
     * @throws StorageException If operation fails
     */
    public abstract void prepareFilestoreUsageFor(User user, Context ctx) throws StorageException;

    /**
     * Prepares filestore usage for given user
     *
     * @param user The user
     * @param ctx The context
     * @param con The connection to use
     * @throws StorageException If operation fails
     */
    public abstract void prepareFilestoreUsageFor(User user, Context ctx, Connection con) throws StorageException;

    /**
     * Cleans filestore usage for given user
     *
     * @param user The user
     * @param ctx The context
     * @throws StorageException If operation fails
     */
    public abstract void cleanseFilestoreUsageFor(User user, Context ctx) throws StorageException;

    /**
     * Cleans filestore usage for given user
     *
     * @param user The user
     * @param ctx The context
     * @param con The connection to use
     * @throws StorageException If operation fails
     */
    public abstract void cleanseFilestoreUsageFor(User user, Context ctx, Connection con) throws StorageException;

    /**
     * Gets the URIs of the file storages that are in use by specified context (either itself or by one if its users).
     *
     * @param contextId The context identifier
     * @return The file storages in use
     * @throws StorageException If file storages cannot be determined
     */
    public abstract List<URI> getUrisforFilestoresUsedBy(int contextId) throws StorageException;

    /**
     * List all registered file stores.
     *
     * @param pattern a pattern to search for
     * @return an array of file store objects
     * @throws StorageException
     */
    public abstract Filestore[] listFilestores(String pattern, boolean omitUsage) throws StorageException;

    /**
     * Loads filestore information, but w/o any usage information. Only basic information.
     *
     * @param id The unique identifier of the filestore.
     * @return Basic filestore information
     * @throws StorageException if loading the filestore information fails.
     */
    public abstract Filestore getFilestoreBasic(int id) throws StorageException;

    /**
     * Gets the filestore associated with given identifier
     *
     * @param id The filestore identifier
     * @return The filestore instance
     * @throws StorageException If filestore instance cannot be returned
     */
    public abstract Filestore getFilestore(final int id) throws StorageException;

    /**
     * Load a filestore. Specify whether the file store usage should be calculated by summing up all filestore usages.
     *
     * @param filestoreId The filestore identifier
     * @param loadUsage Whether the usage must be determined. Note: This is very slow.
     * @return The filestore instance
     * @throws StorageException If filestore instance cannot be returned
     */
    public abstract Filestore getFilestore(int filestoreId, boolean loadUsage) throws StorageException;

    /**
     * Loads the base URI from specified filestore.
     *
     * @param filestoreId The filestore identifier
     * @return The filestore base URI
     * @throws StorageException If filestore base URI cannot be returned
     */
    public abstract java.net.URI getFilestoreURI(int filestoreId) throws StorageException;

    /**
     * Unregister filestore from configbdb
     *
     * @param store_id
     *            the id of the filestore
     * @throws StorageException
     */
    public abstract void unregisterFilestore(final int store_id) throws StorageException;

    /**
     * Iterates across all existing file storages and searches for one having enough space for a context.
     */
    public abstract Filestore findFilestoreForContext() throws StorageException;

    /**
     * Iterates across all existing file storages and searches for one having enough space for a context.
     *
     * @param configDbCon A writable {@link Connection} to the ConfigDB
     */
    public abstract Filestore findFilestoreForContext(Connection configDbCon) throws StorageException;

    /**
     * Iterates across all existing file storages and searches for one having enough space for a user.
     *
     * @param fileStoreId The optional identifier of the file storage to prefer during auto-selection or <code>-1</code> to ignore
     */
    public abstract Filestore findFilestoreForUser(int fileStoreId) throws StorageException;

    /**
     * Gets the identifier of the file storage currently assigned to given context
     *
     * @param contextId The context identifier
     * @return The identifier of the file storage
     * @throws StorageException If the identifier of the file storage cannot be returned
     */
    public abstract int getFilestoreIdFromContext(int contextId) throws StorageException;

    /**
     * Checks if specified file storage offers enough space for a further context assignment.
     *
     * @param filestore The file storage to which a further context is supposed to be assigned
     * @return <code>true</code> if enough space is available; otherwise <code>false</code>
     * @throws StorageException If check for enough space fails
     */
    public abstract boolean hasSpaceForAnotherContext(Filestore filestore) throws StorageException;

    /**
     * Checks if specified file storage offers enough space for a further user assignment.
     *
     * @param filestore The file storage to which a further user is supposed to be assigned
     * @return <code>true</code> if enough space is available; otherwise <code>false</code>
     * @throws StorageException If check for enough space fails
     */
    public abstract boolean hasSpaceForAnotherUser(Filestore filestore) throws StorageException;

    /**
     * Create a new maintenance reason in configdb.They are needed to disable a
     * context.
     *
     * @param reason
     *            the MaintenanceReason
     * @return the id as a long of the new created reason
     * @throws StorageException
     */
    public abstract int createMaintenanceReason(final MaintenanceReason reason) throws StorageException;

    /**
     * Delete reason from configdb
     *
     * @param reason
     *            the MaintenanceReason
     * @throws StorageException
     */
    public abstract void deleteMaintenanceReason(final int[] reason_ids) throws StorageException;

    /**
     * @param reason_id
     *            the id of a MaintenanceReason
     * @return MaintenanceReason from configdb identified by the reason_id
     * @throws StorageException
     */
    public abstract MaintenanceReason[] getMaintenanceReasons(final int[] reason_id) throws StorageException;

    /**
     * @return an array of all available MaintenanceReasons in configdb.
     * @throws StorageException
     */
    public abstract MaintenanceReason[] getAllMaintenanceReasons() throws StorageException;

    /**
     * @return an array of all available MaintenanceReasons in configdb match the specified pattern
     * @throws StorageException
     */
    public abstract MaintenanceReason[] listMaintenanceReasons(final String search_pattern) throws StorageException;

    /**
     * Registers a new {@link Database#isMaster() master} or slave database (host) in configdb
     *
     * @param db A database object to register
     * @param createSchemas Whether the schemas holding payload data are supposed to be pre-created
     * @param optNumberOfSchemas Given that <code>createSchemas</code> is <code>true</code> that parameter specifies the number of schemas that shall be created;
     *            if not set number of schemas is determined by max. units for associated database divides by <code>CONTEXTS_PER_SCHEMA</code> configuration option
     * @return The identifier of the database
     * @throws StorageException
     */
    public abstract int registerDatabase(Database db, boolean createSchemas, int optNumberOfSchemas) throws StorageException;

    /**
     * Creates schemas on given database host.
     *
     * @param db A database host
     * @param optNumberOfSchemas Specifies the number of schemas that shall be created; if not set number of schemas is determined by max. units for associated database divides by <code>CONTEXTS_PER_SCHEMA</code> configuration option
     * @return The newly created schemas
     * @throws StorageException If schemas cannot be created
     */
    public abstract List<String> createDatabaseSchemas(Database db, int optNumberOfSchemas) throws StorageException;

    /**
     * Deletes empty schemas from given database host.
     *
     * @param db An optional database host; possibly also specifying a certain schema
     * @param optNumberOfSchemasToKeep Specifies the number of schemas to keep (per database); if not specified all empty schemas are supposed to be deleted
     * @return The empty schemas that were deleted (grouped by database host association)
     * @throws StorageException If schema cannot be deleted
     */
    public abstract Map<Database, List<String>> deleteEmptyDatabaseSchemas(Database db, int optNumberOfSchemasToKeep) throws StorageException;

    /**
     * Creates a new database from scratch on the given database host. Is used
     * ONLY internally at the moment.
     *
     * @param db a database object to create
     * @param con A writable {@link Connection} to the configdb
     * @throws StorageException
     */
    public abstract void createDatabase(final Database db, Connection con) throws StorageException;

    // TODO: cutamasta: please fill javadoc comment
    /**
     * @param db
     *            a database object to be changed
     * @throws StorageException
     */
    public abstract void changeDatabase(final Database db) throws StorageException;

    /**
     * Registers a new server in the configdb
     *
     * @param serverName
     *            a server name to be registered
     * @return long with the id of the server
     * @throws StorageException
     */
    public abstract int registerServer(final String serverName) throws StorageException;

    /**
     * Unregister a database from configdb
     *
     * @param db_id
     *            a database id which is unregistered
     * @throws StorageException
     */
    public abstract void unregisterDatabase(final int db_id, final boolean isMaster) throws StorageException;

    /**
     * Unregister a server from configdb
     *
     * @param server_id
     *            a server id which is unregistered
     * @throws StorageException
     */
    public abstract void unregisterServer(final int server_id) throws StorageException;

    /**
     * Changes the server identifier for the specified schema
     *
     * @param serverId The server identifier
     * @param schemaName The schema name
     * @throws StorageException
     */
    public abstract void changeServer(int serverId, String schemaName) throws StorageException;

    /**
     * Searches for databases matching search_pattern
     *
     * @param search_pattern
     *            a pattern to search for
     * @return a database array
     * @throws StorageException
     */
    public abstract Database[] searchForDatabase(final String search_pattern) throws StorageException;

    /**
     * Searches for databases schemas matching search_pattern
     *
     * @param search_pattern
     *            a pattern to search for
     * @param onlyEmptySchemas Whether only empty database schemas are supposed to be considered
     * @return a database array with schema information
     * @throws StorageException
     */
    public abstract Database[] searchForDatabaseSchema(final String search_pattern, boolean onlyEmptySchemas) throws StorageException;

    /**
     * Counts schemas per database host matching search_pattern
     *
     * @param search_pattern A pattern to search for
     * @param onlyEmptySchemas Whether only empty database schemas are supposed to be counted
     * @return The schema counts per database host
     * @throws StorageException
     */
    public abstract Map<Database, Integer> countDatabaseSchema(final String search_pattern, boolean onlyEmptySchemas) throws StorageException;

    /**
     * Searchs for server matching given search_pattern
     *
     * @param search_pattern
     *            a pattern to search for
     * @return Server array with found servers
     * @throws StorageException
     */
    public abstract Server[] searchForServer(final String search_pattern) throws StorageException;

    /**
     * Get the write pool identifier for the specified cluster
     *
     * @param clusterId The cluster identifier
     * @return The write pool identifier
     * @throws StorageException
     */
    public abstract int getWritePoolIdForCluster(final int clusterId) throws StorageException;

    /**
     * Creates a new schema in the given database if possible. In case the optDBId is null the best suitable DB is selected automatically.
     *
     * @param optDatabaseId Optional database identifier. If missing the best suitable database is selected automatically.
     * @return The schema name.
     * @throws StorageException
     */
    public abstract Database createSchema(Integer optDatabaseId) throws StorageException;

    /**
     * Determine the next database to use depending on database weight factor. Each database should be equal full according to their weight.
     * Additionally check each master for availability.
     *
     * @param con
     * @param forContext <code>true</code> if a suitable database is supposed to be determined for a context; otherwise <code>false</code>
     * @return the database
     * @throws SQLException
     * @throws StorageException
     */
    public abstract Database getNextDBHandleByWeight(Connection con, boolean forContext) throws SQLException, StorageException;

    /**
     * Checks the consistencies for the count tables
     *
     * @param configCon The connection to the ConfigDb
     * @param checkDatabaseCounts Whether to check the counts related to context to database/schema associations
     * @param checkFilestoreCounts Whether to check the counts related to context to filestore associations
     * @throws StorageException If check fails
     */
    public abstract void checkCountsConsistency(Connection configCon, boolean checkDatabaseCounts, boolean checkFilestoreCounts) throws StorageException;

    /**
     * Checks the consistencies for the count tables
     *
     * @param checkDatabaseCounts Whether to check the counts related to context to database/schema associations
     * @param checkFilestoreCounts Whether to check the counts related to context to filestore associations
     * @throws StorageException If check fails
     */
    public abstract void checkCountsConsistency(boolean checkDatabaseCounts, boolean checkFilestoreCounts) throws StorageException;
}
