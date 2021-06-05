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

package com.openexchange.subscribe.xing.groupware;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.update.Attributes;
import com.openexchange.groupware.update.PerformParameters;
import com.openexchange.groupware.update.TaskAttributes;
import com.openexchange.groupware.update.UpdateConcurrency;
import com.openexchange.groupware.update.UpdateExceptionCodes;
import com.openexchange.groupware.update.UpdateTaskV2;


/**
 * {@link SubscriptionRemoverTask}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public abstract class SubscriptionRemoverTask implements UpdateTaskV2 {

    /** The subscription source identifier */
    protected final String subscriptionSourceId;

    /**
     * Initializes a new {@link SubscriptionRemoverTask}.
     * @param subscriptionSourceId
     */
    protected SubscriptionRemoverTask(final String subscriptionSourceId) {
        super();
        this.subscriptionSourceId = subscriptionSourceId;
    }

    @Override
    public TaskAttributes getAttributes() {
        return new Attributes(UpdateConcurrency.BACKGROUND);
    }

    @Override
    public String[] getDependencies() {
        return new String[0];
    }

    @Override
    public void perform(final PerformParameters params) throws OXException {
        Connection con = params.getConnection();
        PreparedStatement stmt = null;
        try {
            if (false == Databases.tablesExist(con, "subscriptions", "genconf_attributes_strings", "genconf_attributes_bools")) {
                return;
            }

            stmt = con.prepareStatement("DELETE subscriptions, genconf_attributes_strings, genconf_attributes_bools FROM subscriptions, genconf_attributes_strings, genconf_attributes_bools WHERE subscriptions.source_id = ? AND genconf_attributes_strings.id = subscriptions.configuration_id AND genconf_attributes_bools.id = subscriptions.configuration_id AND genconf_attributes_strings.cid = subscriptions.cid AND genconf_attributes_bools.cid = subscriptions.cid;");
            stmt.setString(1, subscriptionSourceId);
            stmt.executeUpdate();
        } catch (SQLException x) {
            throw UpdateExceptionCodes.SQL_PROBLEM.create(x, x.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }




    /* This is a SQL Test Case for the poor. Do this in an empty database to check the validity of the sql statement */
    /*
    CREATE TABLE `subscriptions` (
        `id` INT4 unsigned NOT NULL,
        `cid` INT4 unsigned NOT NULL,
        `user_id` INT4 unsigned NOT NULL,
        `configuration_id` INT4 unsigned NOT NULL,
        `source_id` varchar(255) collate utf8_unicode_ci NOT NULL,
        `folder_id` varchar(255) collate utf8_unicode_ci NOT NULL,
        `last_update` bigint(20) unsigned NOT NULL,
        `enabled` tinyint(1) NOT NULL default '1',
        `created` bigint(20) NOT NULL default '0',
        `lastModified` bigint(20) NOT NULL default '0',
        PRIMARY KEY  (`cid`,`id`)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

      CREATE TABLE `genconf_attributes_strings` (
        `cid` INT4 unsigned NOT NULL,
        `id` INT4 unsigned NOT NULL,
        `name` varchar(100) collate utf8_unicode_ci default NULL,
        `value` varchar(256) collate utf8_unicode_ci default NULL,
        `widget` varchar(256) collate utf8_unicode_ci default NULL,
        KEY `cid` (`cid`,`id`,`name`)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

      CREATE TABLE `genconf_attributes_bools` (
        `cid` INT4 unsigned NOT NULL,
        `id` INT4 unsigned NOT NULL,
        `name` varchar(100) collate utf8_unicode_ci default NULL,
        `value` tinyint(1) default NULL,
        `widget` varchar(256) collate utf8_unicode_ci default NULL,
        KEY `cid` (`cid`,`id`,`name`)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

      INSERT INTO subscriptions (id, cid, user_id, source_id, configuration_id, folder_id, last_update) VALUES (1,1,1,'com.openexchange.removeMe', 2, 12, 0);
      INSERT INTO subscriptions (id, cid, user_id, source_id, configuration_id, folder_id, last_update) VALUES (2,1,1,'com.openexchange.keepMe'  , 3, 12, 0);
      INSERT INTO subscriptions (id, cid, user_id, source_id, configuration_id, folder_id, last_update) VALUES (1,2,1,'com.openexchange.keepMe'  , 2, 12, 0);
      INSERT INTO subscriptions (id, cid, user_id, source_id, configuration_id, folder_id, last_update) VALUES (2,2,1,'com.openexchange.removeMe', 3, 12, 0);

      INSERT INTO genconf_attributes_strings (id, cid, name, value) VALUES (2, 1, "action", "remove");
      INSERT INTO genconf_attributes_strings (id, cid, name, value) VALUES (3, 1, "action", "keep");
      INSERT INTO genconf_attributes_strings (id, cid, name, value) VALUES (2, 2, "action", "keep");
      INSERT INTO genconf_attributes_strings (id, cid, name, value) VALUES (3, 2, "action", "remove");

      INSERT INTO genconf_attributes_bools (id, cid, name, value) VALUES (2, 1, "action", 0);
      INSERT INTO genconf_attributes_bools (id, cid, name, value) VALUES (3, 1, "action", 1);
      INSERT INTO genconf_attributes_bools (id, cid, name, value) VALUES (2, 2, "action", 1);
      INSERT INTO genconf_attributes_bools (id, cid, name, value) VALUES (3, 2, "action", 0);

      SELECT * FROM subscriptions;
      SELECT * FROM genconf_attributes_strings;
      SELECT * FROM genconf_attributes_bools;

      DELETE subscriptions, genconf_attributes_strings, genconf_attributes_bools FROM subscriptions, genconf_attributes_strings, genconf_attributes_bools WHERE subscriptions.source_id = 'com.openexchange.removeMe' AND genconf_attributes_strings.id = subscriptions.configuration_id AND genconf_attributes_bools.id = subscriptions.configuration_id AND genconf_attributes_strings.cid = subscriptions.cid AND genconf_attributes_bools.cid = subscriptions.cid;

      SELECT * FROM subscriptions;
      SELECT * FROM genconf_attributes_strings;
      SELECT * FROM genconf_attributes_bools;

      DROP TABLE subscriptions;
      DROP TABLE genconf_attributes_strings
      DROP TABLE genconf_attributes_bools
    */


}
