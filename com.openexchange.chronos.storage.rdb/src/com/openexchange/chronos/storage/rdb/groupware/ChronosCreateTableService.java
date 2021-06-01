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

package com.openexchange.chronos.storage.rdb.groupware;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.openexchange.database.AbstractCreateTableImpl;

/**
 * {@link ChronosCreateTableService}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class ChronosCreateTableService extends AbstractCreateTableImpl {

    /**
     * Gets the <code>CREATE TABLE</code> statement for a specific <i>chronos</i> table.
     *
     * @return The <code>CREATE TABLE</code> statement, or <code>null</code> if table name unknown
     */
    static String getCreateTableStmt(String tableName) {
        return getTablesByName().get(tableName);
    }

    /**
     * Gets the <code>CREATE TABLE</code> statements for the <i>chronos</i> tables, mapped by their table names.
     *
     * @return The <code>CREATE TABLE</code> statements mapped by their table name
     */
    static Map<String, String> getTablesByName() {
        Map<String, String> tablesByName = new HashMap<String, String>(11); //@formatter:off
        tablesByName.put("calendar_account",
            "CREATE TABLE calendar_account (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "id INT4 UNSIGNED NOT NULL," +
                "user INT4 UNSIGNED NOT NULL," +
                "provider VARCHAR(64) COLLATE utf8mb4_bin NOT NULL," +
                "modified BIGINT(20) NOT NULL," +
                "internalConfig BLOB," +
                "userConfig BLOB," +
                "PRIMARY KEY (cid,id,user)," +
                "KEY user (cid,user,provider)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_event_sequence",
            "CREATE TABLE calendar_event_sequence (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "id INT4 UNSIGNED NOT NULL," +
                "PRIMARY KEY (cid,account)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_alarm_sequence",
            "CREATE TABLE calendar_alarm_sequence (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "id INT4 UNSIGNED NOT NULL," +
                "PRIMARY KEY (cid,account)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_conference_sequence",
            "CREATE TABLE calendar_conference_sequence (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "id INT4 UNSIGNED NOT NULL," +
                "PRIMARY KEY (cid,account)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_event",
            "CREATE TABLE calendar_event (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "id INT4 UNSIGNED NOT NULL," +
                "user INT4 UNSIGNED DEFAULT NULL," +
                "folder VARCHAR(255) COLLATE utf8mb4_bin DEFAULT NULL," +
                "series INT4 UNSIGNED DEFAULT NULL," +
                "uid VARCHAR(1024) COLLATE utf8mb4_bin DEFAULT NULL," +
                "relatedTo VARCHAR(767) COLLATE utf8mb4_bin DEFAULT NULL," +
                "timestamp BIGINT(20) NOT NULL," +
                "created BIGINT(20)," +
                "createdBy INT4 UNSIGNED," +
                "modified BIGINT(20)," +
                "modifiedBy INT4 UNSIGNED," +
                "start datetime NOT NULL," +
                "startTimezone VARCHAR(255) DEFAULT NULL," +
                "end datetime DEFAULT NULL," +
                "endTimezone VARCHAR(255) DEFAULT NULL," +
                "allDay BOOLEAN DEFAULT NULL," +
                "rrule VARCHAR(255) DEFAULT NULL," +
                "rDate TEXT DEFAULT NULL," +
                "exDate TEXT DEFAULT NULL," +
                "overriddenDate TEXT DEFAULT NULL," +
                "recurrence VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL," +
                "sequence INT4 UNSIGNED DEFAULT NULL," +
                "transp INT4 UNSIGNED DEFAULT NULL," +
                "class VARCHAR(64) DEFAULT NULL," +
                "status VARCHAR(64) DEFAULT NULL," +
                "organizer VARCHAR(767) DEFAULT NULL," +
                "summary VARCHAR(255) DEFAULT NULL," +
                "location VARCHAR(255) DEFAULT NULL," +
                "description TEXT DEFAULT NULL," +
                "categories VARCHAR(1024) DEFAULT NULL," +
                "color VARCHAR(32) DEFAULT NULL," +
                "url VARCHAR(767) DEFAULT NULL," +
                "geo POINT DEFAULT NULL," +
                "attendeePrivileges INT4 UNSIGNED DEFAULT NULL,"+
                "rangeFrom BIGINT(20) NOT NULL," +
                "rangeUntil BIGINT(20) NOT NULL," +
                "filename VARCHAR(1024) COLLATE utf8mb4_bin DEFAULT NULL," +
                "extendedProperties BLOB DEFAULT NULL," +
                "PRIMARY KEY (cid,account,id)," +
                "KEY `range` (cid,account,rangeFrom,rangeUntil)," +
                "KEY series (cid,account,series)," +
                "KEY timestamp (cid,account,timestamp)," +
                "KEY user (cid,account,user)," +
                "KEY folder (cid,account,folder(191))," +
                "KEY uid (cid,account,uid(191))," +
                "KEY filename (cid,account,filename(191))" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_event_tombstone",
            "CREATE TABLE calendar_event_tombstone (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "id INT4 UNSIGNED NOT NULL," +
                "user INT4 UNSIGNED DEFAULT NULL," +
                "folder VARCHAR(255) COLLATE utf8mb4_bin DEFAULT NULL," +
                "series INT4 UNSIGNED DEFAULT NULL," +
                "uid VARCHAR(1024) COLLATE utf8mb4_bin DEFAULT NULL," +
                "relatedTo VARCHAR(767) COLLATE utf8mb4_bin DEFAULT NULL," +
                "timestamp BIGINT(20) NOT NULL," +
                "created BIGINT(20)," +
                "createdBy INT4 UNSIGNED," +
                "modified BIGINT(20)," +
                "modifiedBy INT4 UNSIGNED," +
                "start datetime DEFAULT NULL," +
                "startTimezone VARCHAR(255) DEFAULT NULL," +
                "end datetime DEFAULT NULL," +
                "endTimezone VARCHAR(255) DEFAULT NULL," +
                "allDay BOOLEAN DEFAULT NULL," +
                "rrule VARCHAR(255) DEFAULT NULL," +
                "rDate TEXT DEFAULT NULL," +
                "exDate TEXT DEFAULT NULL," +
                "overriddenDate TEXT DEFAULT NULL," +
                "recurrence VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL," +
                "sequence INT4 UNSIGNED DEFAULT NULL," +
                "transp INT4 UNSIGNED DEFAULT NULL," +
                "class VARCHAR(64) DEFAULT NULL," +
                "status VARCHAR(64) DEFAULT NULL," +
                "organizer VARCHAR(767) DEFAULT NULL," +
                "summary VARCHAR(255) DEFAULT NULL," +
                "location VARCHAR(255) DEFAULT NULL," +
                "description TEXT DEFAULT NULL," +
                "categories VARCHAR(1024) DEFAULT NULL," +
                "color VARCHAR(32) DEFAULT NULL," +
                "url VARCHAR(767) DEFAULT NULL," +
                "geo POINT DEFAULT NULL," +
                "attendeePrivileges INT4 UNSIGNED DEFAULT NULL,"+
                "rangeFrom BIGINT(20) NOT NULL," +
                "rangeUntil BIGINT(20) NOT NULL," +
                "filename VARCHAR(1024) COLLATE utf8mb4_bin DEFAULT NULL," +
                "extendedProperties BLOB DEFAULT NULL," +
                "PRIMARY KEY (cid,account,id)," +
                "KEY `range` (cid,account,rangeFrom,rangeUntil)," +
                "KEY series (cid,account,series)," +
                "KEY timestamp (cid,account,timestamp)," +
                "KEY user (cid,account,user)," +
                "KEY folder (cid,account,folder(191))," +
                "KEY uid (cid,account,uid(191))," +
                "KEY filename (cid,account,filename(191))" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_attendee",
            "CREATE TABLE calendar_attendee (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "event INT4 UNSIGNED NOT NULL," +
                "entity INT4 NOT NULL," +
                "uri VARCHAR(512) COLLATE utf8mb4_bin NOT NULL," +
                "cn VARCHAR(512) DEFAULT NULL," +
                "folder VARCHAR(255) COLLATE utf8mb4_bin DEFAULT NULL," +
                "hidden BOOLEAN DEFAULT NULL," +
                "cuType VARCHAR(255) DEFAULT NULL," +
                "role VARCHAR(255) DEFAULT NULL," +
                "partStat VARCHAR(255) DEFAULT NULL," +
                "rsvp BOOLEAN DEFAULT NULL," +
                "comment TEXT DEFAULT NULL," +
                "member VARCHAR(1024) DEFAULT NULL," +
                "transp INT4 UNSIGNED DEFAULT NULL," +
                "timestamp BIGINT UNSIGNED DEFAULT NULL," +
                "extendedParameters BLOB DEFAULT NULL," +
                "PRIMARY KEY (cid,account,event,entity)," +
                "KEY uri (cid,account,event,uri(191))," +
                "KEY folder (cid,account,entity,folder(191))" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_attendee_tombstone",
            "CREATE TABLE calendar_attendee_tombstone (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "event INT4 UNSIGNED NOT NULL," +
                "entity INT4 NOT NULL," +
                "uri VARCHAR(512) COLLATE utf8mb4_bin NOT NULL," +
                "cn VARCHAR(512) DEFAULT NULL," +
                "folder VARCHAR(255) COLLATE utf8mb4_bin DEFAULT NULL," +
                "hidden BOOLEAN DEFAULT NULL," +
                "cuType VARCHAR(255) DEFAULT NULL," +
                "role VARCHAR(255) DEFAULT NULL," +
                "partStat VARCHAR(255) DEFAULT NULL," +
                "rsvp BOOLEAN DEFAULT NULL," +
                "comment TEXT DEFAULT NULL," +
                "member VARCHAR(1024) DEFAULT NULL," +
                "transp INT4 UNSIGNED DEFAULT NULL," +
                "timestamp BIGINT UNSIGNED DEFAULT NULL," +
                "extendedParameters BLOB DEFAULT NULL," +
                "PRIMARY KEY (cid,account,event,entity)," +
                "KEY uri (cid,account,event,uri(191))," +
                "KEY folder (cid,account,entity,folder(191))" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_alarm",
            "CREATE TABLE calendar_alarm (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "id INT4 UNSIGNED NOT NULL," +
                "event VARCHAR(128) COLLATE utf8mb4_bin NOT NULL," +
                "user INT4 UNSIGNED NOT NULL," +
                "uid VARCHAR(767) COLLATE utf8mb4_bin DEFAULT NULL," +
                "relatedTo VARCHAR(767) COLLATE utf8mb4_bin DEFAULT NULL," +
                "acknowledged BIGINT(20) DEFAULT NULL," +
                "action VARCHAR(32) NOT NULL," +
                "repetition VARCHAR(64) DEFAULT NULL," +
                "triggerRelated VARCHAR(32) DEFAULT NULL," +
                "triggerDuration VARCHAR(32) DEFAULT NULL," +
                "triggerDate BIGINT(20) DEFAULT NULL," +
                "timestamp BIGINT(20) NOT NULL DEFAULT 0," +
                "extendedProperties BLOB DEFAULT NULL," +
                "PRIMARY KEY (cid,account,id)," +
                "KEY event_user (cid,account,event,user)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_alarm_trigger",
            "CREATE TABLE calendar_alarm_trigger (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "alarm INT4 UNSIGNED NOT NULL," +
                "user INT4 UNSIGNED NOT NULL," +
                "eventId VARCHAR(128) COLLATE utf8mb4_bin NOT NULL," +
                "folder VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,"+
                "triggerDate BIGINT(20) NOT NULL," +
                "action VARCHAR(32) NOT NULL," +
                "recurrence VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL," +
                "floatingTimezone VARCHAR(255) DEFAULT NULL," +
                "relatedTime BIGINT(20) DEFAULT NULL," +
                "pushed BOOL DEFAULT FALSE," +
                "processed BIGINT(20) NOT NULL DEFAULT 0," +
                "PRIMARY KEY (cid,account,alarm)," +
                "KEY triggerDate (cid,account,user,triggerDate)," +
                "KEY event (cid,account,user,eventId)," +
                "KEY action (action, triggerDate)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );
        tablesByName.put("calendar_conference",
            "CREATE TABLE calendar_conference (" +
                "cid INT4 UNSIGNED NOT NULL," +
                "account INT4 UNSIGNED NOT NULL," +
                "id INT4 UNSIGNED NOT NULL," +
                "event INT4 UNSIGNED NOT NULL," +
                "uri VARCHAR(767) COLLATE utf8mb4_bin NOT NULL," +
                "label VARCHAR(1024) DEFAULT NULL," +
                "features VARCHAR(767) DEFAULT NULL," +
                "extendedParameters BLOB DEFAULT NULL," +
                "PRIMARY KEY (cid,account,id)," +
                "KEY event (cid,account,event)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        );

        // Disabled until further notice
//        tablesByName.put("calendar_available",
//            "CREATE TABLE calendar_available (" +
//                "cid INT4 UNSIGNED NOT NULL," +
//                "id INT4 UNSIGNED NOT NULL," +
//                "user INT4 UNSIGNED NOT NULL," +
//                "uid VARCHAR(767) DEFAULT NULL," +
//                "start datetime NOT NULL," +
//                "end datetime DEFAULT NULL," +
//                "startTimezone VARCHAR(255) DEFAULT NULL," +
//                "endTimezone VARCHAR(255) DEFAULT NULL," +
//                "allDay BOOLEAN DEFAULT NULL," +
//                "created BIGINT(20) NOT NULL," +
//                "description TEXT DEFAULT NULL," +
//                "modified BIGINT(20) NOT NULL," +
//                "location VARCHAR(255) DEFAULT NULL," +
//                "exDate TEXT DEFAULT NULL," +
//                "recurrence BIGINT(20) DEFAULT NULL," +
//                "rrule VARCHAR(255) DEFAULT NULL," +
//                "summary VARCHAR(255) DEFAULT NULL," +
//                "categories VARCHAR(1024) DEFAULT NULL, " +
//                "comment VARCHAR(512) DEFAULT NULL," +
//                "extendedProperties BLOB DEFAULT NULL," +
//                "PRIMARY KEY (cid,user,id)," +
//                "KEY uid (cid,user,uid(191))" +
//            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
//        );
//        tablesByName.put("calendar_available_sequence",
//            "CREATE TABLE calendar_available_sequence (" +
//                "cid INT4 UNSIGNED NOT NULL," +
//                "id INT4 UNSIGNED NOT NULL," +
//                "PRIMARY KEY (cid)" +
//            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
//        );
        return tablesByName; //@formatter:on
    }

    /**
     * Initializes a new {@link ChronosCreateTableService}.
     */
    public ChronosCreateTableService() {
        super();
    }

    @Override
    public String[] getCreateStatements() {
        Collection<String> createStatements = getTablesByName().values();
        return createStatements.toArray(new String[createStatements.size()]);
    }

    @Override
    public String[] requiredTables() {
        return new String[0];
    }

    @Override
    public String[] tablesToCreate() {
        Set<String> tableNames = getTablesByName().keySet();
        return tableNames.toArray(new String[tableNames.size()]);
    }

}
