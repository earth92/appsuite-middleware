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

package com.openexchange.mailaccount.internal;

import com.openexchange.database.AbstractCreateTableImpl;

/**
 * {@link CreateMailAccountTables}
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public final class CreateMailAccountTables extends AbstractCreateTableImpl {

    public CreateMailAccountTables() {
        super();
    }

    @Override
    public String[] getCreateStatements() {
        return createStatements;
    }

    @Override
    public String[] requiredTables() {
        return requiredTables;
    }

    @Override
    public String[] tablesToCreate() {
        return createdTables;
    }

    public static final String getCreateMailAccount() {  // --> com.openexchange.mailaccount.internal.CreateMailAccountTables
        return "CREATE TABLE user_mail_account ("
            + "id INT4 UNSIGNED NOT NULL,"
            + "cid INT4 UNSIGNED NOT NULL,"
            + "user INT4 UNSIGNED NOT NULL,"
            + "name VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "url VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "login VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "password VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,"
            + "primary_addr VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "personal VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,"
            + "replyTo VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,"
            + "default_flag TINYINT UNSIGNED NOT NULL DEFAULT 0,"
            + "spam_handler VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "trash VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "sent VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "drafts VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "spam VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "confirmed_spam VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "confirmed_ham VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "archive VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',"
            + "unified_inbox TINYINT UNSIGNED DEFAULT 0,"
            + "trash_fullname VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "sent_fullname VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "drafts_fullname VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "spam_fullname VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "confirmed_spam_fullname VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "confirmed_ham_fullname VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,"
            + "archive_fullname VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',"
            + "starttls TINYINT UNSIGNED NOT NULL DEFAULT 0,"
            + "oauth INT(10) UNSIGNED DEFAULT NULL,"
            + "disabled TINYINT UNSIGNED NOT NULL DEFAULT 0,"
            + "failed_auth_count INT4 UNSIGNED NOT NULL DEFAULT 0,"
            + "failed_auth_date BIGINT(64) NOT NULL DEFAULT 0,"
            + "PRIMARY KEY (cid, id, user),"
            + "INDEX (cid, user)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    public static final String getCreateTransportAccount() {  // --> com.openexchange.mailaccount.internal.CreateMailAccountTables
        return "CREATE TABLE user_transport_account ("
            + "id INT4 UNSIGNED NOT NULL,"
            + "cid INT4 UNSIGNED NOT NULL,"
            + "user INT4 UNSIGNED NOT NULL,"
            + "name VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "url VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "login VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "password VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,"
            + "send_addr VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "personal VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,"
            + "replyTo VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,"
            + "default_flag TINYINT UNSIGNED NOT NULL DEFAULT 0,"
            + "unified_inbox TINYINT UNSIGNED DEFAULT 0,"
            + "starttls TINYINT UNSIGNED NOT NULL DEFAULT 0,"
            + "oauth INT(10) UNSIGNED DEFAULT NULL,"
            + "disabled TINYINT UNSIGNED NOT NULL DEFAULT 0,"
            + "failed_auth_count INT4 UNSIGNED NOT NULL DEFAULT 0,"
            + "failed_auth_date BIGINT(64) NOT NULL DEFAULT 0,"
            + "PRIMARY KEY (cid, id, user),"
            + "INDEX (cid, user)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    public static final String getCreateMailAccountProperties() {  // --> com.openexchange.mailaccount.internal.CreateMailAccountTables
        return "CREATE TABLE user_mail_account_properties ("
            + "id INT4 UNSIGNED NOT NULL,"
            + "cid INT4 UNSIGNED NOT NULL,"
            + "user INT4 UNSIGNED NOT NULL,"
            + "name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "value VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "PRIMARY KEY (cid, id, user, name)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    public static final String getCreateTransportAccountProperties() {  // --> com.openexchange.mailaccount.internal.CreateMailAccountTables
        return "CREATE TABLE user_transport_account_properties ("
            + "id INT4 UNSIGNED NOT NULL,"
            + "cid INT4 UNSIGNED NOT NULL,"
            + "user INT4 UNSIGNED NOT NULL,"
            + "name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "value VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
            + "PRIMARY KEY (cid, id, user, name)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static final String getCreateSequence() {
        return "CREATE TABLE sequence_mail_service (" +
            "cid INT4 unsigned NOT NULL," +
            "id INT4 unsigned NOT NULL," +
            "PRIMARY KEY (cid)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static final String[] requiredTables = { "user" };

    private static final String[] createdTables = { "user_mail_account", "user_mail_account_properties", "user_transport_account", "user_transport_account_properties", "sequence_mail_service", "pop3_storage_ids", "pop3_storage_deleted" };

    private static final String[] createStatements = {

        getCreateMailAccount(),

        getCreateMailAccountProperties(),

        getCreateTransportAccount(),

        getCreateTransportAccountProperties(),

        getCreateSequence(),

        "CREATE TABLE pop3_storage_ids ("
        + "cid INT4 UNSIGNED NOT NULL,"
        + "user INT4 UNSIGNED NOT NULL,"
        + "id INT4 UNSIGNED NOT NULL,"
        + "uidl VARCHAR(128) CHARACTER SET latin1 NOT NULL,"
        + "fullname VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
        + "uid VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
        + "PRIMARY KEY (cid, user, id, uidl)"
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE pop3_storage_deleted ("
        + "cid INT4 UNSIGNED NOT NULL,"
        + "user INT4 UNSIGNED NOT NULL,"
        + "id INT4 UNSIGNED NOT NULL,"
        + "uidl VARCHAR(128) CHARACTER SET latin1 NOT NULL,"
        + "PRIMARY KEY (cid, user, id, uidl)"
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    };
}
