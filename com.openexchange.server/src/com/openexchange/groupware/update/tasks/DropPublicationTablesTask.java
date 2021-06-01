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

package com.openexchange.groupware.update.tasks;

import static com.openexchange.database.Databases.autocommit;
import static com.openexchange.database.Databases.rollback;
import static com.openexchange.database.Databases.startTransaction;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.update.Attributes;
import com.openexchange.groupware.update.PerformParameters;
import com.openexchange.groupware.update.TaskAttributes;
import com.openexchange.groupware.update.UpdateConcurrency;
import com.openexchange.groupware.update.UpdateExceptionCodes;
import com.openexchange.groupware.update.UpdateTaskV2;
import com.openexchange.groupware.update.WorkingLevel;
import com.openexchange.tools.update.Tools;

/**
 * 
 * {@link DropPublicationTablesTask} - Deletes the tables "sequence_publications", "publications" and "publication_users".
 * If any publication is found, data will be logged. Afterwards the data will be removed!
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.2
 */
public class DropPublicationTablesTask implements UpdateTaskV2 {

    /*
     * @formatter:off
     * ------------------------- OLD TABLE DEFINITIONS -------------------------
     * 
     * mysql> desc publications;
     * +------------------+------------------+------+-----+---------+-------+
     * | Field            | Type             | Null | Key | Default | Extra |
     * +------------------+------------------+------+-----+---------+-------+
     * | cid              | int(10) unsigned | NO   | PRI | NULL    |       |
     * | id               | int(10) unsigned | NO   | PRI | NULL    |       |
     * | user_id          | int(10) unsigned | NO   |     | NULL    |       |
     * | entity           | int(10) unsigned | NO   |     | NULL    |       |
     * | module           | varchar(255)     | NO   |     | NULL    |       |
     * | configuration_id | int(10) unsigned | NO   |     | NULL    |       | --->  Key 'id' in 'genconf_attributes_strings'
     * | target_id        | varchar(255)     | NO   |     | NULL    |       |       and 'genconf_attributes_bools'
     * | enabled          | tinyint(1)       | NO   |     | 1       |       |
     * | created          | bigint(20)       | NO   |     | 0       |       |
     * | lastModified     | bigint(20)       | NO   |     | 0       |       |
     * +------------------+------------------+------+-----+---------+-------+
     * 
     * --------------------------------------------------------------------------
     * 
     * mysql> desc publication_users;
     * +--------------+------------------+------+-----+---------+-------+
     * | Field        | Type             | Null | Key | Default | Extra |
     * +--------------+------------------+------+-----+---------+-------+
     * | cid          | int(10) unsigned | NO   | PRI | NULL    |       |
     * | id           | int(10) unsigned | NO   | PRI | NULL    |       |
     * | name         | varchar(255)     | NO   | PRI | NULL    |       |
     * | password     | varchar(255)     | NO   |     | NULL    |       |
     * | created      | bigint(20)       | NO   |     | 0       |       |
     * | lastModified | bigint(20)       | NO   |     | 0       |       |
     * +--------------+------------------+------+-----+---------+-------+
     * 
     * --------------------------------------------------------------------------
     * @formatter:on 
     */

    private static final String PUBLICATIONS = "publications";

    private static final String PUBLICATION_USERS = "publication_users";

    private static final String SEQUENCE_PUBLICATIONS = "sequence_publications";

    private static final String GENCONF_ATTRIBUTES_BOOLS = "genconf_attributes_bools";

    private static final String GENCONF_ATTRIBUTES_STRINGS = "genconf_attributes_strings";

    private final static Logger LOGGER = LoggerFactory.getLogger(DropPublicationTablesTask.class);

    public DropPublicationTablesTask() {
        super();
    }

    @Override
    public void perform(PerformParameters params) throws OXException {
        Connection con = params.getConnection();
        boolean rollback = false;

        LOGGER.info("Start purging publication related tables.");

        try {
            startTransaction(con);
            rollback = true;

            boolean existsPublication = Tools.tableExists(con, PUBLICATIONS);

            /*
             * Get all publication users
             */
            Map<Integer, Map<Integer, List<PublicationUser>>> contextToPub = new HashMap<>();
            if (Tools.tableExists(con, PUBLICATION_USERS)) {
                // Only fetch data if we can log something useful
                if (existsPublication) {
                    executeQuery(con, "SELECT cid, id, name, created, lastModified FROM publication_users", (ResultSet resultSet) -> {
                        int i = 1;
                        do {
                            Integer contextId = I(resultSet.getInt(i++));
                            Integer publicationId = I(resultSet.getInt(i++));

                            PublicationUser publicationUser = new PublicationUser(resultSet.getString(i++), new Date(resultSet.getLong(i++)), new Date(resultSet.getLong(i++)));
                            if (contextToPub.containsKey(contextId)) {
                                // Known context
                                Map<Integer, List<PublicationUser>> pubToUser = contextToPub.get(contextId);
                                if (pubToUser.containsKey(publicationId)) {
                                    // New user in publication
                                    pubToUser.get(publicationId).add(publicationUser);
                                } else {
                                    // New publication
                                    ArrayList<PublicationUser> list = new ArrayList<>();
                                    list.add(publicationUser);
                                    pubToUser.put(publicationId, list);
                                }
                            } else {
                                // New context
                                ArrayList<PublicationUser> list = new ArrayList<>();
                                list.add(publicationUser);
                                Map<Integer, List<PublicationUser>> pubToUser = new HashMap<>();
                                pubToUser.put(publicationId, list);
                                contextToPub.put(contextId, pubToUser);
                            }
                        } while (resultSet.next());
                    });
                }
                Tools.dropTable(con, PUBLICATION_USERS);
            }

            Map<Integer, List<Integer>> confIds = new HashMap<>(contextToPub.size() + (int) (contextToPub.size() * 0.05), 0.99f);
            /*
             * Get all publications and print out the information that is going to be deleted
             */
            if (existsPublication) {
                executeQuery(con, "SELECT cid, id, configuration_id, user_id, entity, module, target_id, enabled, created, lastModified FROM publications", (ResultSet rs) -> {
                    do {
                        int i = 1;
                        Integer contextId = I(rs.getInt(i++));
                        Integer publicationId = I(rs.getInt(i++));
                        Integer configurationId = I(rs.getInt(i++));

                        //@formatter:off         
                        LOGGER.info(
                            "##################################################################\n" +
                            "Removing the publication with ID {} for user {} in context {}.\nThe publication has following attributes:\n" + 
                            "##################################################################\n" +
                            "entity:\t\t\t{}\nmodule:\t\t\t{}\nconfiguration_id:\t{}\ntarget_id:\t\t{}\nenabled:\t\t{}\ncreated:\t\t{}\nlastModified:\t\t{}\n" + 
                            "users:\t\t\t[{}]\n",
                            publicationId, 
                            rs.getString(i++), // user ID
                            contextId,
                            I(rs.getInt(i++)), // entity
                            rs.getString(i++), // module
                            configurationId,
                            rs.getString(i++), // target_id
                            0 == rs.getInt(i++) ? Boolean.FALSE : Boolean.TRUE, // enabled
                            new Date(rs.getLong(i++)), // created
                            new Date(rs.getLong(i++)), // lastModified
                            printPublicationUsers(contextToPub, contextId, publicationId)
                            );
                        //@formatter:on   
                        if (null != configurationId) {
                            if (false == confIds.containsKey(contextId)) {
                                confIds.put(contextId, new ArrayList<>());
                            }
                            confIds.get(contextId).add(configurationId);
                        }
                    } while (rs.next());
                });
                Tools.dropTable(con, PUBLICATIONS);
            }

            // Delete related data
            boolean existsAttributeStrings = Tools.tableExists(con, GENCONF_ATTRIBUTES_STRINGS);
            boolean existsAttributeBools = Tools.tableExists(con, GENCONF_ATTRIBUTES_BOOLS);
            for (Entry<Integer, List<Integer>> entry : confIds.entrySet()) {
                if (existsAttributeStrings) {
                    deleteFromTable(con, entry, GENCONF_ATTRIBUTES_STRINGS);
                }
                if (existsAttributeBools) {
                    deleteFromTable(con, entry, GENCONF_ATTRIBUTES_BOOLS);
                }
            }

            // Nothing to log, just remove the table
            if (Tools.tableExists(con, SEQUENCE_PUBLICATIONS)) {
                Tools.dropTable(con, SEQUENCE_PUBLICATIONS);
            }

            con.commit();
            rollback = false;

            LOGGER.info("Publications have been removed successfully! Tables \"sequence_publications\", \"publications\" and \"publication_users\" were dropped.");
        } catch (SQLException e) {
            throw UpdateExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw UpdateExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            if (rollback) {
                rollback(con);
            }
            autocommit(con);
        }
    }

    @Override
    public String[] getDependencies() {
        return new String[] {};
    }

    @Override
    public TaskAttributes getAttributes() {
        return new Attributes(UpdateConcurrency.BLOCKING, WorkingLevel.SCHEMA);
    }

    // -----------------------------------------------------------------------------------------------------------------------------

    /**
     * Pretty prints the publication users for a publication
     * 
     * @param contextToPub Holding the data
     * @param contextId The context ID
     * @param publicationId The ID of the publication
     * @return Pretty printed users or <code>none</code> if no data exist
     */
    private String printPublicationUsers(Map<Integer, Map<Integer, List<PublicationUser>>> contextToPub, Integer contextId, Integer publicationId) {
        StringBuilder sb = new StringBuilder();
        if (contextToPub.isEmpty() || false == contextToPub.containsKey(contextId) || false == contextToPub.get(contextId).containsKey(publicationId)) {
            return "none";
        }
        contextToPub.get(contextId).get(publicationId).forEach(u -> sb.append(u.toString()).append(","));
        return sb.substring(0, sb.length() - 1);
    }

    /**
     * Deletes entries from given table name
     * 
     * @param con The {@link Connection} to use
     * @param entry The context and its entries which shall be removed
     * @param tableName The name of the table
     * @throws SQLException In case data can't be removed
     */
    private void deleteFromTable(Connection con, Entry<Integer, List<Integer>> entry, String tableName) throws SQLException {
        PreparedStatement stmt = null;
        try {
            if (null != entry.getKey()) {
                stmt = con.prepareStatement(Databases.getIN("DELETE FROM " + tableName + " WHERE cid=? and id IN (", entry.getValue().size()));
                stmt.setInt(1, entry.getKey().intValue());
                int i = 2;
                for (Integer p : entry.getValue()) {
                    stmt.setInt(i++, p.intValue());
                }
                int result = stmt.executeUpdate();
                LOGGER.debug("The statement has been executed successfully. Result was {}", I(result));
            }
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     * Executes the query and logs
     * 
     * @param con The {@link Connection} to use
     * @param query The query to execute
     * @param logResults The logging to to
     * @throws SQLException If the query fails
     */
    private static void executeQuery(final Connection con, String query, SQLConsumer<ResultSet> logResults) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(query);

            rs = stmt.executeQuery();
            if (rs.next()) {
                logResults.accept(rs);
            } else {
                LOGGER.debug("No result. Skip processing.");
            }
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    @FunctionalInterface
    private interface SQLConsumer<T> {

        void accept(T t) throws SQLException;
    }

    private static class PublicationUser {

        String name;

        Date created;
        Date lastModified;

        public PublicationUser(String name, Date created, Date lastModified) {
            super();
            this.name = name;
            this.created = created;
            this.lastModified = lastModified;
        }

        @Override
        public String toString() {
            return new StringBuilder("name: ").append(name).append(", created: ").append(created).append(", lastModified: ").append(lastModified).toString();
        }
    }
}
