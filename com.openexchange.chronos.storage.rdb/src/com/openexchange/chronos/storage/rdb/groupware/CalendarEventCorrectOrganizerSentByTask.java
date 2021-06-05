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

import static com.openexchange.database.Databases.autocommit;
import static com.openexchange.database.Databases.rollback;
import static com.openexchange.database.Databases.tableExists;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;
import com.google.common.collect.Lists;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.compat.Appointment2Event;
import com.openexchange.chronos.storage.rdb.EntityProcessor;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.update.PerformParameters;
import com.openexchange.groupware.update.UpdateExceptionCodes;
import com.openexchange.groupware.update.UpdateTaskAdapter;
import com.openexchange.tools.arrays.Collections;

/**
 * {@link CalendarEventCorrectOrganizerSentByTask}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.3
 */
public class CalendarEventCorrectOrganizerSentByTask extends UpdateTaskAdapter {

    @Override
    public String[] getDependencies() {
        return new String[] { "com.openexchange.chronos.storage.rdb.migration.ChronosStorageMigrationTask" };
    }

    @Override
    public void perform(PerformParameters params) throws OXException {
        Connection connection = params.getConnection();
        int rollback = 0;
        try {
            /*
             * check if legacy storage still exists
             */
            if (false == tableExists(connection, "prg_dates")) {
                org.slf4j.LoggerFactory.getLogger(CalendarEventCorrectOrganizerSentByTask.class).debug(
                    "Legacy calendar storage no longer exists, nothing to do.");
                return;
            }
            connection.setAutoCommit(false);
            rollback = 1;
            /*
             * derive corrected organizer from legacy storage and update corresponding entries in default storage
             */
            for (Entry<Integer, List<Event>> entry : getEventsWithCorrectedOrganizerPerContext(connection).entrySet()) {
                int updated = correctOrganizers(connection, i(entry.getKey()), entry.getValue());
                if (0 < updated) {
                    org.slf4j.LoggerFactory.getLogger(CalendarEventCorrectOrganizerSentByTask.class).info(
                        "Corrected {} malformed organizer values in context {}.", I(updated), entry.getKey());
                }
            }
            connection.commit();
            rollback = 2;
        } catch (SQLException e) {
            throw UpdateExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw UpdateExceptionCodes.OTHER_PROBLEM.create(e, e.getMessage());
        } finally {
            if (0 < rollback) {
                if (1 == rollback) {
                    rollback(connection);
                }
                autocommit(connection);
            }
        }
    }

    private static Map<Integer, List<Event>> getEventsWithCorrectedOrganizerPerContext(Connection connection) throws SQLException {
        Map<Integer, List<Event>> eventsWithCorrectedOrganizerPerContext = new HashMap<Integer, List<Event>>();
        String sql = "SELECT cid,intfield01,organizer,organizerId FROM prg_dates WHERE principal=organizer;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = stmt.executeQuery()) {
                while (resultSet.next()) {
                    Event event = new Event();
                    event.setId(Appointment2Event.asString(resultSet.getInt("intfield01")));
                    Organizer organizer = new Organizer();
                    organizer.setEntity(resultSet.getInt("organizerId"));
                    organizer.setUri(Appointment2Event.getURI(resultSet.getString("organizer")));
                    event.setOrganizer(organizer);
                    Collections.put(eventsWithCorrectedOrganizerPerContext, I(resultSet.getInt("cid")), event);
                }
            }
        }
        return eventsWithCorrectedOrganizerPerContext;
    }

    private static int correctOrganizers(Connection connection, int cid, List<Event> eventsWithCorrectedOrganizer) throws SQLException, OXException {
        if (null == eventsWithCorrectedOrganizer || eventsWithCorrectedOrganizer.isEmpty()) {
            return 0;
        }
        int updated = 0;
        EntityProcessor entityProcessor = new EntityProcessor(cid, null);
        String sql = "UPDATE calendar_event SET organizer=? WHERE cid=? AND account=0 AND id=?;";
        for (List<Event> chunk : Lists.partition(new ArrayList<Event>(eventsWithCorrectedOrganizer), 500)) {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(2, cid);
                for (Event event : chunk) {
                    stmt.setString(1, entityProcessor.adjustPriorSave(event).getOrganizer().getUri());
                    stmt.setInt(3, Integer.parseInt(event.getId()));
                    stmt.addBatch();
                }
                updated += IntStream.of(stmt.executeBatch()).sum();
            }
        }
        return updated;
    }

}
