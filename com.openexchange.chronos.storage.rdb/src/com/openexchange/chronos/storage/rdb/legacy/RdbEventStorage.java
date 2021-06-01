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

package com.openexchange.chronos.storage.rdb.legacy;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.I2i;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.DefaultRecurrenceData;
import com.openexchange.chronos.compat.Event2Appointment;
import com.openexchange.chronos.compat.PositionAwareRecurrenceId;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.service.RecurrenceData;
import com.openexchange.chronos.service.SearchFilter;
import com.openexchange.chronos.service.SearchOptions;
import com.openexchange.chronos.storage.EventStorage;
import com.openexchange.chronos.storage.rdb.RdbStorage;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.search.SearchTerm;

/**
 * {@link RdbEventStorage}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class RdbEventStorage extends RdbStorage implements EventStorage {

    private static final EventMapper MAPPER = EventMapper.getInstance();
    private static final EventField[] SERIES_PATTERN_FIELDS = new EventField[] {
        EventField.ID, EventField.SERIES_ID, EventField.RECURRENCE_RULE, EventField.START_DATE, EventField.END_DATE
    };

    private final EntityResolver entityResolver;

    /**
     * Initializes a new {@link RdbEventStorage}.
     *
     * @param context The context
     * @param entityResolver The entity resolver to use
     * @param dbProvider The database provider to use
     * @param txPolicy The transaction policy
     */
    public RdbEventStorage(Context context, EntityResolver entityResolver, DBProvider dbProvider, DBTransactionPolicy txPolicy) {
        super(context, dbProvider, txPolicy);
        this.entityResolver = entityResolver;
    }

    @Override
    public String nextId() throws OXException {
        throw CalendarExceptionCodes.STORAGE_NOT_AVAILABLE.create("'Legacy' storage is operating in read-only mode.");
    }

    @Override
    public long countEvents() throws OXException {
        return countEvents(null);
    }

    @Override
    public long countEvents(SearchTerm<?> searchTerm) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return countEvents(connection, false, context.getContextId(), searchTerm);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public long countEventTombstones(SearchTerm<?> searchTerm) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return countEvents(connection, true, context.getContextId(), searchTerm);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public List<Event> searchEvents(SearchTerm<?> searchTerm, SearchOptions sortOptions, EventField[] fields) throws OXException {
        return searchEvents(searchTerm, null, sortOptions, fields);
    }

    @Override
    public List<Event> searchEvents(SearchTerm<?> searchTerm, List<SearchFilter> filters, SearchOptions sortOptions, EventField[] fields) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectEvents(connection, false, context.getContextId(), searchTerm, filters, sortOptions, fields);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public List<Event> searchOverlappingEvents(List<Attendee> attendees, boolean includeTransparent, SearchOptions searchOptions, EventField[] fields) throws OXException {
        Set<Integer> userIDs = new HashSet<Integer>();
        Set<Integer> otherEntityIDs = new HashSet<Integer>();
        for (Attendee attendee : attendees) {
            if (null == attendee.getCuType() || false == CalendarUtils.isInternal(attendee)) {
                continue;
            }
            if (CalendarUserType.INDIVIDUAL.equals(attendee.getCuType())) {
                userIDs.add(I(attendee.getEntity()));
            } else {
                otherEntityIDs.add(I(attendee.getEntity()));
            }
        }
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectOverlappingEvents(connection, context.getContextId(), I2i(userIDs), I2i(otherEntityIDs), includeTransparent, searchOptions, fields);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public List<Event> searchEventTombstones(SearchTerm<?> searchTerm, SearchOptions searchOptions, EventField[] fields) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectEvents(connection, true, context.getContextId(), searchTerm, null, searchOptions, fields);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public Event loadEvent(String objectID, EventField[] fields) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectEvent(connection, context.getContextId(), asInt(objectID), fields);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public List<Event> loadEvents(List<String> eventIds, EventField[] fields) throws OXException {
        if (null == eventIds || eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Event> events = new ArrayList<Event>(eventIds.size());
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            for (String id : eventIds) {
                Event event = selectEvent(connection, context.getContextId(), asInt(id), fields);
                if (null != event) {
                    events.add(event);
                }
            }
            return events;
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public Event loadException(String seriesID, RecurrenceId recurrenceID, EventField[] fields) throws OXException {
        long recurrenceDatePosition;
        if (PositionAwareRecurrenceId.class.isInstance(recurrenceID)) {
            recurrenceDatePosition = ((PositionAwareRecurrenceId) recurrenceID).getRecurrenceDatePosition().getTime();
        } else {
            recurrenceDatePosition = Event2Appointment.getRecurrenceDatePosition(recurrenceID).getTime();
        }
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectException(connection, context.getContextId(), asInt(seriesID), recurrenceDatePosition, fields);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public List<Event> loadExceptions(String seriesID, EventField[] fields) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectExceptions(connection, context.getContextId(), asInt(seriesID), fields);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public void insertEvent(Event event) throws OXException {
        insertEvents(Collections.singletonList(event));
    }

    @Override
    public void insertEvents(List<Event> events) throws OXException {
        throw CalendarExceptionCodes.STORAGE_NOT_AVAILABLE.create("'Legacy' storage is operating in read-only mode.");
    }

    @Override
    public void updateEvent(Event event) throws OXException {
        throw CalendarExceptionCodes.STORAGE_NOT_AVAILABLE.create("'Legacy' storage is operating in read-only mode.");
    }

    @Override
    public void insertEventTombstone(Event event) throws OXException {
        throw CalendarExceptionCodes.STORAGE_NOT_AVAILABLE.create("'Legacy' storage is operating in read-only mode.");
    }

    @Override
    public void insertEventTombstones(List<Event> events) throws OXException {
        throw CalendarExceptionCodes.STORAGE_NOT_AVAILABLE.create("'Legacy' storage is operating in read-only mode.");
    }

    @Override
    public void deleteEvent(String objectID) throws OXException {
        throw CalendarExceptionCodes.STORAGE_NOT_AVAILABLE.create("'Legacy' storage is operating in read-only mode.");
    }

    @Override
    public void deleteEvents(List<String> eventIds) throws OXException {
        throw CalendarExceptionCodes.STORAGE_NOT_AVAILABLE.create("'Legacy' storage is operating in read-only mode.");
    }

    @Override
    public boolean deleteAllEvents() throws OXException {
        throw CalendarExceptionCodes.STORAGE_NOT_AVAILABLE.create("'Legacy' storage is operating in read-only mode.");
    }

    private Event selectEvent(Connection connection, int contextID, int objectID, EventField[] fields) throws SQLException, OXException {
        EventField[] mappedFields = MAPPER.getMappedFields(fields);
        String sql = new StringBuilder()
            .append("SELECT ").append(MAPPER.getColumns(mappedFields)).append(" FROM prg_dates ")
            .append("WHERE cid=? AND intfield01=?;")
        .toString();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, contextID);
            stmt.setInt(2, objectID);
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                return resultSet.next() ? readEvent(connection, resultSet, mappedFields, null) : null;
            }
        }
    }

    private Event selectException(Connection connection, int contextID, int seriesID, long recurrenceDatePosition, EventField[] fields) throws SQLException, OXException {
        EventField[] mappedFields = MAPPER.getMappedFields(fields);
        String sql = new StringBuilder()
            .append("SELECT ").append(MAPPER.getColumns(mappedFields)).append(" FROM prg_dates ")
            .append("WHERE cid=? AND intfield02=? AND field08=? AND intfield01<>intfield02;")
        .toString();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, contextID);
            stmt.setInt(2, seriesID);
            stmt.setString(3, String.valueOf(recurrenceDatePosition));
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                return resultSet.next() ? readEvent(connection, resultSet, mappedFields, null) : null;
            }
        }
    }

    private List<Event> selectExceptions(Connection connection, int contextID, int seriesID, EventField[] fields) throws SQLException, OXException {
        EventField[] mappedFields = MAPPER.getMappedFields(fields);
        String sql = new StringBuilder()
            .append("SELECT ").append(MAPPER.getColumns(mappedFields)).append(" FROM prg_dates ")
            .append("WHERE cid=? AND intfield02=? AND intfield01<>intfield02;")
        .toString();
        List<Event> events = new ArrayList<Event>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, contextID);
            stmt.setInt(2, seriesID);
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                while (resultSet.next()) {
                    events.add(readEvent(connection, resultSet, mappedFields, null));
                }
            }
        }
        return events;
    }

    private Event readEvent(Connection connection, ResultSet resultSet, EventField[] fields, String columnLabelPrefix) throws SQLException, OXException {
        return Compat.adjustAfterLoad(this, connection, MAPPER.fromResultSet(resultSet, fields, columnLabelPrefix));
    }

    private List<Event> selectEvents(Connection connection, boolean deleted, int contextID, SearchTerm<?> searchTerm, List<SearchFilter> filters, SearchOptions searchOptions, EventField[] fields) throws SQLException, OXException {
        EventField[] mappedFields = MAPPER.getMappedFields(fields);
        SearchAdapter adapter = new SearchAdapter(contextID, null, "d.", "m.", "e.").append(searchTerm).append(filters);
        StringBuilder stringBuilder = new StringBuilder()
            .append("SELECT DISTINCT ").append(MAPPER.getColumns(mappedFields, "d.")).append(' ')
            .append("FROM ").append(deleted ? "del_dates" : "prg_dates").append(" AS d ")
        ;
        if (adapter.usesInternalAttendees()) {
            stringBuilder.append("LEFT JOIN ").append(deleted ? "del_dates_members" : "prg_dates_members").append(" AS m ")
            .append("ON d.cid=m.cid AND d.intfield01=m.object_id ");
        }
        if (adapter.usesExternalAttendees()) {
            stringBuilder.append("LEFT JOIN ").append(deleted ? "delDateExternal" : "dateExternal").append(" AS e ")
            .append("ON d.cid=e.cid AND d.intfield01=e.objectId ");
        }
        stringBuilder.append("WHERE d.cid=? ");
        if (null != searchOptions) {
            if (false == deleted && null != searchOptions.getFrom()) {
                stringBuilder.append("AND d.timestampfield02>? ");
            }
            if (false == deleted && null != searchOptions.getUntil()) {
                stringBuilder.append("AND d.timestampfield01<? ");
            }
        }
        stringBuilder.append("AND ").append(adapter.getClause()).append(getSortOptions(MAPPER, searchOptions, "d.")).append(';');
        List<Event> events = new ArrayList<Event>();
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, contextID);
            if (false == deleted && null != searchOptions && null != searchOptions.getFrom()) {
                stmt.setTimestamp(parameterIndex++, new Timestamp(searchOptions.getFrom().getTime()));
            }
            if (false == deleted && null != searchOptions && null != searchOptions.getUntil()) {
                stmt.setTimestamp(parameterIndex++, new Timestamp(searchOptions.getUntil().getTime()));
            }
            adapter.setParameters(stmt, parameterIndex);
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                while (resultSet.next()) {
                    events.add(readEvent(connection, resultSet, mappedFields, "d."));
                }
            }
        }
        return events;
    }

    private long countEvents(Connection connection, boolean deleted, int contextID, SearchTerm<?> searchTerm) throws SQLException, OXException {
        SearchAdapter adapter = new SearchAdapter(contextID, null, "d.", "m.", "e.").append(searchTerm);
        StringBuilder stringBuilder = new StringBuilder()
            .append("SELECT COUNT(DISTINCT d.intfield01) FROM ").append(deleted ? "del_dates" : "prg_dates").append(" AS d ")
        ;
        if (adapter.usesInternalAttendees()) {
            stringBuilder.append("LEFT JOIN ").append(deleted ? "del_dates_members" : "prg_dates_members").append(" AS m ")
                .append("ON d.cid=m.cid AND d.intfield01=m.object_id ");
        }
        if (adapter.usesExternalAttendees()) {
            stringBuilder.append("LEFT JOIN ").append(deleted ? "delDateExternal" : "dateExternal").append(" AS e ")
                .append("ON d.cid=e.cid AND d.intfield01=e.objectId ");
        }
        stringBuilder.append("WHERE d.cid=? ").append("AND ").append(adapter.getClause()).append(';');
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, contextID);
            adapter.setParameters(stmt, parameterIndex);
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        }
    }

    private List<Event> selectOverlappingEvents(Connection connection, int contextID, int[] userIDs, int[] otherEntityIDs, boolean includeTransparent, SearchOptions searchOptions, EventField[] fields) throws SQLException, OXException {
        EventField[] mappedFields = MAPPER.getMappedFields(fields);
        StringBuilder stringBuilder = new StringBuilder()
            .append("SELECT DISTINCT ").append(MAPPER.getColumns(mappedFields, "d.")).append(" FROM prg_dates AS d");
        if (null != userIDs && 0 < userIDs.length) {
            stringBuilder.append(" LEFT JOIN prg_dates_members AS m ON d.cid=m.cid AND d.intfield01=m.object_id");
        }
        if (null != otherEntityIDs && 0 < otherEntityIDs.length) {
            stringBuilder.append(" LEFT JOIN prg_date_rights AS r ON d.cid=r.cid AND d.intfield01=r.object_id");
        }
        stringBuilder.append(" WHERE d.cid=?");
        if (false == includeTransparent) {
            stringBuilder.append(" AND d.intfield06<>4");
        }
        if (null != searchOptions && null != searchOptions.getFrom()) {
            stringBuilder.append(" AND d.timestampfield02>=?");
        }
        if (null != searchOptions && null != searchOptions.getUntil()) {
            stringBuilder.append(" AND d.timestampfield01<=?");
        }
        if (null != userIDs && 0 < userIDs.length || null != otherEntityIDs && 0 < otherEntityIDs.length) {
            stringBuilder.append(" AND (");
            if (null != userIDs && 0 < userIDs.length) {
                if (1 == userIDs.length) {
                    stringBuilder.append("m.member_uid=?");
                } else {
                    stringBuilder.append("m.member_uid IN (").append(EventMapper.getParameters(userIDs.length)).append(')');
                }
                if (null != otherEntityIDs && 0 < otherEntityIDs.length) {
                    stringBuilder.append(" OR ");
                }
            }
            if (null != otherEntityIDs && 0 < otherEntityIDs.length) {
                if (1 == otherEntityIDs.length) {
                    stringBuilder.append("r.id=?");
                } else {
                    stringBuilder.append("r.id IN (").append(EventMapper.getParameters(otherEntityIDs.length)).append(')');
                }
            }
            stringBuilder.append(')');
        }
        stringBuilder.append(getSortOptions(MAPPER, searchOptions, "d.")).append(';');
        List<Event> events = new ArrayList<Event>();
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, contextID);
            if (null != searchOptions && null != searchOptions.getFrom()) {
                stmt.setTimestamp(parameterIndex++, new Timestamp(searchOptions.getFrom().getTime()));
            }
            if (null != searchOptions && null != searchOptions.getUntil()) {
                stmt.setTimestamp(parameterIndex++, new Timestamp(searchOptions.getUntil().getTime()));
            }
            if (null != userIDs && 0 < userIDs.length) {
                for (int id : userIDs) {
                    stmt.setInt(parameterIndex++, id);
                }
            }
            if (null != otherEntityIDs && 0 < otherEntityIDs.length) {
                for (int id : otherEntityIDs) {
                    stmt.setInt(parameterIndex++, id);
                }
            }
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                while (resultSet.next()) {
                    events.add(readEvent(connection, resultSet, mappedFields, "d."));
                }
            }
        }
        return events;
    }

    /**
     * Selects recurrence data for a specific series.
     *
     * @param connection The database connection to use
     * @param seriesID The series identifier to load the recurrence data for
     * @param deleted <code>true</code> to read from the <i>tombstone</i> tables, <code>false</code>, otherwise
     * @return The recurrence data, or <code>null</code> if not found
     */
    RecurrenceData selectRecurrenceData(Connection connection, int seriesID, boolean deleted) throws SQLException, OXException {
        EventField[] fields = SERIES_PATTERN_FIELDS;
        String sql = new StringBuilder()
            .append("SELECT ").append(MAPPER.getColumns(fields))
            .append(" FROM ").append(deleted ? "del_dates" : "prg_dates")
            .append(" WHERE cid=? AND intfield01=? AND intfield02=?;")
        .toString();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, context.getContextId());
            stmt.setInt(2, seriesID);
            stmt.setInt(3, seriesID);
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                if (resultSet.next()) {
                    Event event = readEvent(connection, resultSet, fields, null);
                    return new DefaultRecurrenceData(event.getRecurrenceRule(), event.getStartDate(), null);
                }
                return null;
            }
        }
    }

    /**
     * Gets the used entity resolver.
     *
     * @return The entity resolver, or <code>null</code> if not set
     */
    EntityResolver getEntityResolver() {
        return entityResolver;
    }

}
