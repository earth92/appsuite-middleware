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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.update.PerformParameters;
import com.openexchange.groupware.update.UpdateExceptionCodes;
import com.openexchange.groupware.update.UpdateTaskAdapter;
import com.openexchange.tools.update.Tools;

/**
 * {@link CreateMissingPrimaryKeys} - Creates missing primary keys on various tables.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class CreateMissingPrimaryKeys extends UpdateTaskAdapter {

    /**
     * Initializes a new {@link CreateMissingPrimaryKeys}.
     */
    public CreateMissingPrimaryKeys() {
        super();
    }

    @Override
    public String[] getDependencies() {
        return new String[0];
    }

    private void initTasks(final Connection con, final List<Callable<Void>> tasks) {
        class Splitter {

            private final Pattern split = Pattern.compile(" *, *");

            private final Pattern repl = Pattern.compile("[\\(\\)`]");

            String[] split(final String keys) {
                return split.split(repl.matcher(keys).replaceAll(""), 0);
            }
        }
        final Splitter splitter = new Splitter();
        /*
         * PRIMARY KEY for "genconf_attributes_bools"
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                if (!Tools.existsPrimaryKey(con, "genconf_attributes_bools", splitter.split("(`cid`,`id`,`name`)"))) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE genconf_attributes_bools ADD PRIMARY KEY (`cid`,`id`,`name`), DROP KEY cid");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE genconf_attributes_bools ADD PRIMARY KEY (`cid`,`id`,`name`), DROP KEY cid";
            }
        });
        /*
         * PRIMARY KEY for "genconf_attributes_strings"
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                if (!Tools.existsPrimaryKey(con, "genconf_attributes_strings", splitter.split("(`cid`,`id`,`name`)"))) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE genconf_attributes_strings ADD PRIMARY KEY (`cid`,`id`,`name`), DROP KEY cid");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE genconf_attributes_strings ADD PRIMARY KEY (`cid`,`id`,`name`), DROP KEY cid";
            }
        });
        /*
         * PRIMARY KEY for "user_setting_server"
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                if (!Tools.existsPrimaryKey(con, "user_setting_server", splitter.split("(`cid`,`user`)"))) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE user_setting_server ADD PRIMARY KEY (`cid`,`user`), DROP KEY cid");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE user_setting_server ADD PRIMARY KEY (`cid`,`user`), DROP KEY cid";
            }
        });
        /*
         * PRIMARY KEY for "user_attribute" + DROP redundant KEY
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                if (!Tools.existsPrimaryKey(con, "user_attribute", splitter.split("(`cid`,`id`,`name`,`value`)"))) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE user_attribute ADD PRIMARY KEY (`cid`,`id`,`name`,`value`(128)), DROP KEY cid_2");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE user_attribute ADD PRIMARY KEY (`cid`,`id`,`name`,`value`(128)), DROP KEY cid_2";
            }
        });
        /*
         * PRIMARY KEY for "infostoreReservedPaths"
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                if (!Tools.existsPrimaryKey(con, "infostoreReservedPaths", splitter.split("(`cid`,`folder`)"))) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE infostoreReservedPaths ADD PRIMARY KEY (`cid`,`folder`)");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE infostoreReservedPaths ADD PRIMARY KEY (`cid`,`folder`)";
            }
        });
        /*
         * PRIMARY KEY for "updateTask"
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                if (!Tools.existsPrimaryKey(con, "updateTask", splitter.split("(`cid`,`taskName`)"))) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE updateTask ADD PRIMARY KEY (`cid`,`taskName`(255)), DROP KEY full");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE updateTask ADD PRIMARY KEY (`cid`,`taskName`(255)), DROP KEY full";
            }
        });
        /*
         * PRIMARY KEY for "prg_links"
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                if (!Tools.existsPrimaryKey(con, "prg_links", splitter.split("(`cid`,`firstid`,`firstmodule`,`firstfolder`,`secondid`,`secondmodule`,`secondfolder`)"))) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE prg_links ADD PRIMARY KEY (`cid`,`firstid`,`firstmodule`,`firstfolder`,`secondid`,`secondmodule`,`secondfolder`)");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE prg_links ADD PRIMARY KEY (`cid`,`firstid`,`firstmodule`,`firstfolder`,`secondid`,`secondmodule`,`secondfolder`)";
            }
        });
        /*
         * TODO: PRIMARY KEY for "aggregatingContacts"
         */

        /*-
         * ########################################################################################
         * ################################ TIDY UP; See Bug #21882 ###############################
         * ########################################################################################
         */

        /*-
         * cid is a left-prefix of reminder_unique
         * Key definitions:
         *   KEY `cid` (`cid`,`target_id`),
         *   UNIQUE KEY `reminder_unique` (`cid`,`target_id`,`module`,`userid`),
         * Column types:
         *     `cid` int(10) unsigned not null
         *     `target_id` varchar(255) collate utf8_unicode_ci not null
         *     `module` tinyint(3) unsigned not null
         *     `userid` int(10) unsigned not null
         * To remove this duplicate index, execute:
         * ALTER TABLE `oxdatabase_6`.`reminder` DROP INDEX `cid`;
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                final String name = Tools.existsIndex(con, "reminder", splitter.split("(`cid`,`target_id`)"));
                if (null != name) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE reminder DROP INDEX `" + name + "`");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE reminder DROP INDEX `cid`";
            }
        });
        /*-
         * cid is a left-prefix of PRIMARY
         * Key definitions:
         *   KEY `cid` (`cid`,`tree`,`user`,`folderId`)
         *   PRIMARY KEY (`cid`,`tree`,`user`,`folderId`,`entity`),
         * Column types:
         *     `cid` int(10) unsigned not null
         *     `tree` int(10) unsigned not null
         *     `user` int(10) unsigned not null
         *     `folderid` varchar(192) collate utf8_unicode_ci not null
         *     `entity` int(10) unsigned not null
         * To remove this duplicate index, execute:
         * ALTER TABLE `oxdatabase_6`.`virtualBackupPermission` DROP INDEX `cid`;
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                final String name = Tools.existsIndex(con, "virtualBackupPermission", splitter.split("(`cid`,`tree`,`user`,`folderId`)"));
                if (null != name) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE virtualBackupPermission DROP INDEX `" + name + "`");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE virtualBackupPermission DROP INDEX `cid`";
            }
        });
        /*-
         * cid is a left-prefix of PRIMARY
         * Key definitions:
         *   KEY `cid` (`cid`,`tree`,`user`,`folderId`)
         *   PRIMARY KEY (`cid`,`tree`,`user`,`folderId`,`entity`),
         * Column types:
         *     `cid` int(10) unsigned not null
         *     `tree` int(10) unsigned not null
         *     `user` int(10) unsigned not null
         *     `folderid` varchar(192) collate utf8_unicode_ci not null
         *     `entity` int(10) unsigned not null
         * To remove this duplicate index, execute:
         * ALTER TABLE `oxdatabase_6`.`virtualPermission` DROP INDEX `cid`;
         */
        tasks.add(new Callable<Void>() {

            @Override
            public Void call() throws SQLException {
                final String name = Tools.existsIndex(con, "virtualPermission", splitter.split("(`cid`,`tree`,`user`,`folderId`)"));
                if (null != name) {
                    Statement stmt = null;
                    try {
                        stmt = con.createStatement();
                        stmt.execute("ALTER TABLE virtualPermission DROP INDEX `" + name + "`");
                    } finally {
                        Databases.closeSQLStuff(stmt);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "ALTER TABLE virtualPermission DROP INDEX `cid`";
            }
        });
    }

    @Override
    public void perform(final PerformParameters params) throws OXException {
        Connection con = params.getConnection();
        int rollback = 0;
        try {
            Databases.startTransaction(con);
            rollback = 1;

            /*
             * Gather tasks to perform
             */
            final List<Callable<Void>> tasks = new LinkedList<Callable<Void>>();
            initTasks(con, tasks);
            final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CreateMissingPrimaryKeys.class);
            for (final Callable<Void> task : tasks) {
                try {
                    task.call();
                } catch (SQLException e) {
                    log.warn("ALTER TABLE failed with: >>{}<<\nStatement: >>{}<<", e.getMessage(), task);
                }
            }

            con.commit();
            rollback = 2;
        } catch (OXException e) {
            throw e;
        } catch (SQLException e) {
            throw UpdateExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw UpdateExceptionCodes.OTHER_PROBLEM.create(e, e.getMessage());
        } catch (Exception e) {
            throw UpdateExceptionCodes.OTHER_PROBLEM.create(e, e.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback==1) {
                    Databases.rollback(con);
                }
                Databases.autocommit(con);
            }
        }
    }
}
