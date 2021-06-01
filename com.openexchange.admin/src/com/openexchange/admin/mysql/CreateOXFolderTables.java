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

package com.openexchange.admin.mysql;

import com.openexchange.database.AbstractCreateTableImpl;


/**
 * {@link CreateOXFolderTables}
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 */
public class CreateOXFolderTables extends AbstractCreateTableImpl {

    private static final String oxfolderTreeTableName = "oxfolder_tree";
    private static final String oxfolderPermissionsTableName = "oxfolder_permissions";
    private static final String oxfolderSpecialfoldersTableName = "oxfolder_specialfolders";
    private static final String oxfolderUserfoldersTableName = "oxfolder_userfolders";
    private static final String oxfolderUserfoldersStandardfoldersTableName = "oxfolder_userfolders_standardfolders";
    private static final String delOxfolderTreeTableName = "del_oxfolder_tree";
    private static final String delOxfolderPermissionsTableName = "del_oxfolder_permissions";
    private static final String oxfolderLockTableName = "oxfolder_lock";
    private static final String oxfolderPropertyTableName = "oxfolder_property";

    private static final String createOxfolderTreeTable = "CREATE TABLE `oxfolder_tree` ("
       + "`fuid` INT4 UNSIGNED NOT NULL,"
       + "`cid` INT4 UNSIGNED NOT NULL,"
       + "`parent` INT4 UNSIGNED NOT NULL,"
       + "`fname` VARCHAR(767) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
       + "`module` TINYINT UNSIGNED NOT NULL,"
       + "`type` TINYINT UNSIGNED NOT NULL,"
       + "`creating_date` BIGINT(64) NOT NULL,"
       + "`created_from` INT4 UNSIGNED NOT NULL,"
       + "`changing_date` BIGINT(64) NOT NULL,"
       + "`changed_from` INT4 UNSIGNED NOT NULL,"
       + "`permission_flag` TINYINT UNSIGNED NOT NULL,"
       + "`subfolder_flag` TINYINT UNSIGNED NOT NULL,"
       + "`default_flag` TINYINT UNSIGNED NOT NULL default '0',"
       + "`meta` BLOB default NULL,"
       + "`origin` varchar(255) DEFAULT NULL,"
       + "PRIMARY KEY (`cid`, `fuid`),"
       + "INDEX `parentIndex` (`cid`, `parent`),"
       + "INDEX `typeIndex` (`cid`, `type`),"
       + "INDEX `moduleIndex` (`cid`, `module`),"
       + "INDEX `lastModifiedIndex` (`cid`, `changing_date`, `module`),"
       + "FOREIGN KEY (`cid`, `created_from`) REFERENCES user (`cid`, `id`),"
       + "FOREIGN KEY (`cid`, `changed_from`) REFERENCES user (`cid`, `id`)"
     + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String createOxfolderPermissionsTable = "CREATE TABLE `oxfolder_permissions` ("
       + "`cid` INT4 UNSIGNED NOT NULL,"
       + "`fuid` INT4 UNSIGNED NOT NULL,"
       + "`permission_id` INT4 UNSIGNED NOT NULL,"
       + "`fp` TINYINT UNSIGNED NOT NULL,"
       + "`orp` TINYINT UNSIGNED NOT NULL,"
       + "`owp` TINYINT UNSIGNED NOT NULL,"
       + "`odp` TINYINT UNSIGNED NOT NULL,"
       + "`admin_flag` TINYINT UNSIGNED NOT NULL,"
       + "`group_flag` TINYINT UNSIGNED NOT NULL,"
       + "`system` TINYINT UNSIGNED NOT NULL default '0',"
       + "`type` INT4 UNSIGNED NOT NULL default '0',"
       + "`sharedParentFolder` INT4 UNSIGNED,"
       + "PRIMARY KEY  (`cid`, `fuid`, `permission_id`, `system`),"
       + "INDEX `principal` (`cid`, `permission_id`, `fuid`),"
       + "FOREIGN KEY (`cid`, `fuid`) REFERENCES oxfolder_tree (`cid`, `fuid`)"
     + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String createOxfolderSpecialfoldersTable = "CREATE TABLE `oxfolder_specialfolders` ("
        + "`tag` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
        + "`cid` INT4 UNSIGNED NOT NULL,"
        + "`fuid` INT4 UNSIGNED NOT NULL,"
        + "PRIMARY KEY (`cid`,`fuid`,`tag`),"
        + "FOREIGN KEY (`cid`, `fuid`) REFERENCES oxfolder_tree (`cid`, `fuid`)"
      + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String createOxfolderUserfoldersTable = "CREATE TABLE `oxfolder_userfolders` ("
       + "`module` TINYINT UNSIGNED NOT NULL,"
       + "`cid` INT4 UNSIGNED NOT NULL,"
       + "`linksite` VARCHAR(32) NOT NULL,"
       + "`target` VARCHAR(32) NOT NULL,"
       + "`img` VARCHAR(32) NOT NULL,"
       + "PRIMARY KEY (`cid`,`module`)"
     + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String createOxfolderUserfoldersStandardfoldersTable = "CREATE TABLE `oxfolder_userfolders_standardfolders` ("
       + "`owner` INT4 UNSIGNED NOT NULL,"
       + "`cid` INT4 UNSIGNED NOT NULL,"
       + "`module` TINYINT UNSIGNED NOT NULL,"
       + "`fuid` INT4 UNSIGNED NOT NULL,"
       + "PRIMARY KEY (`owner`, `cid`, `module`, `fuid`),"
       + "FOREIGN KEY (`cid`, `fuid`) REFERENCES oxfolder_tree (`cid`, `fuid`)"
     + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String createDelOxfolderTreeTable = "CREATE TABLE `del_oxfolder_tree` ("
       + "`fuid` INT4 UNSIGNED NOT NULL,"
       + "`cid` INT4 UNSIGNED NOT NULL,"
       + "`parent` INT4 UNSIGNED NOT NULL,"
       + "`fname` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',"
       + "`module` TINYINT UNSIGNED NOT NULL,"
       + "`type` TINYINT UNSIGNED NOT NULL,"
       + "`creating_date` BIGINT(64) NOT NULL,"
       + "`created_from` INT4 UNSIGNED NOT NULL,"
       + "`changing_date` BIGINT(64) NOT NULL,"
       + "`changed_from` INT4 UNSIGNED NOT NULL,"
       + "`permission_flag` TINYINT UNSIGNED NOT NULL,"
       + "`subfolder_flag` TINYINT UNSIGNED NOT NULL,"
       + "`default_flag` TINYINT UNSIGNED NOT NULL default '0',"
       + "`meta` BLOB default NULL,"
       + "`origin` varchar(255) DEFAULT NULL,"
       + "PRIMARY KEY (`cid`, `fuid`),"
       + "INDEX `parentIndex` (`cid`, `parent`),"
       + "INDEX `typeIndex` (`cid`, `type`),"
       + "INDEX `moduleIndex` (`cid`, `module`),"
       + "INDEX `lastModifiedIndex` (`cid`, `changing_date`, `module`),"
       + "FOREIGN KEY (`cid`, `created_from`) REFERENCES user (`cid`, `id`),"
       + "FOREIGN KEY (`cid`, `changed_from`) REFERENCES user (`cid`, `id`)"
     + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String createDelOxfolderPermissionsTable = "CREATE TABLE `del_oxfolder_permissions` ("
       + "`cid` INT4 UNSIGNED NOT NULL,"
       + "`fuid` INT4 UNSIGNED NOT NULL,"
       + "`permission_id` INT4 UNSIGNED NOT NULL,"
       + "`fp` TINYINT UNSIGNED NOT NULL,"
       + "`orp` TINYINT UNSIGNED NOT NULL,"
       + "`owp` TINYINT UNSIGNED NOT NULL,"
       + "`odp` TINYINT UNSIGNED NOT NULL,"
       + "`admin_flag` TINYINT UNSIGNED NOT NULL,"
       + "`group_flag` TINYINT UNSIGNED NOT NULL,"
       + "`system` TINYINT UNSIGNED NOT NULL default '0',"
       + "`type` INT4 UNSIGNED NOT NULL default '0',"
       + "`sharedParentFolder` INT4 UNSIGNED,"
       + "PRIMARY KEY  (`cid`,`fuid`,`permission_id`,`system`),"
       + "INDEX `principal` (`cid`, `permission_id`, `fuid`),"
       + "FOREIGN KEY (`cid`, `fuid`) REFERENCES del_oxfolder_tree (`cid`, `fuid`)"
     + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String createOxfolderLockTable = "CREATE TABLE `oxfolder_lock` ("
       + "`cid` INT4 UNSIGNED NOT NULL,"
       + "`id` INT4 UNSIGNED NOT NULL,"
       + "`userid` INT4 UNSIGNED NOT NULL,"
       + "`entity` INT4 UNSIGNED default NULL,"
       + "`timeout` BIGINT(64) UNSIGNED NOT NULL,"
       + "`depth` TINYINT default NULL,"
       + "`type` TINYINT UNSIGNED NOT NULL,"
       + "`scope` TINYINT UNSIGNED NOT NULL,"
       + "`ownerDesc` VARCHAR(128) default NULL,"
       + "PRIMARY KEY (cid, id)"
     + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String createOxfolderPropertyTable = "CREATE TABLE `oxfolder_property` ("
       + "`cid` INT4 UNSIGNED NOT NULL,"
       + "`id` INT4 UNSIGNED NOT NULL,"
       + "`name` VARCHAR(128) COLLATE utf8mb4_unicode_ci NOT NULL,"
       + "`namespace` VARCHAR(128) COLLATE utf8mb4_unicode_ci NOT NULL,"
       + "`value` VARCHAR(255) COLLATE utf8mb4_unicode_ci default NULL,"
       + "`language` VARCHAR(128) COLLATE utf8mb4_unicode_ci default NULL,"
       + "`xml` BOOLEAN default NULL,"
       + "PRIMARY KEY (cid, id, name, namespace)"
     + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    /**
     * Initializes a new {@link CreateOXFolderTables}.
     */
    public CreateOXFolderTables() {
        super();
    }

    @Override
    public String[] requiredTables() {
        return new String[] { "user" };
    }

    @Override
    public String[] tablesToCreate() {
        return new String[] { oxfolderTreeTableName, oxfolderPermissionsTableName, oxfolderSpecialfoldersTableName,
            oxfolderUserfoldersTableName, oxfolderUserfoldersStandardfoldersTableName, delOxfolderTreeTableName,
            delOxfolderPermissionsTableName, oxfolderLockTableName, oxfolderPropertyTableName };
    }

    @Override
    protected String[] getCreateStatements() {
        return new String[] { createOxfolderTreeTable, createOxfolderPermissionsTable, createOxfolderSpecialfoldersTable,
            createOxfolderUserfoldersTable, createOxfolderUserfoldersStandardfoldersTable, createDelOxfolderTreeTable,
            createDelOxfolderPermissionsTable, createOxfolderLockTable, createOxfolderPropertyTable };
    }

}
