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

package com.openexchange.chronos.storage.rdb;

import static com.openexchange.chronos.common.CalendarUtils.isInternal;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;
import java.util.Set;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.exception.ProblemSeverity;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.storage.AttendeeStorage;
import com.openexchange.database.Databases;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.Strings;
import com.openexchange.tools.arrays.Collections;

/**
 * {@link RdbAttendeeStorage}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class RdbAttendeeStorage extends RdbStorage implements AttendeeStorage {

    private static final Logger RdbAttendeeStorage_LOG = LoggerFactory.getLogger(RdbAttendeeStorage.class);

    private static final int INSERT_CHUNK_SIZE = 200;
    private static final int DELETE_CHUNK_SIZE = 200;
    private static final AttendeeMapper MAPPER = AttendeeMapper.getInstance();

    private final int accountId;
    private final EntityProcessor entityProcessor;

    /**
     * Initializes a new {@link RdbAttendeeStorage}.
     *
     * @param context The context
     * @param accountId The account identifier
     * @param entityResolver The entity resolver to use, or <code>null</code> if not available
     * @param dbProvider The database provider to use
     * @param txPolicy The transaction policy
     */
    public RdbAttendeeStorage(Context context, int accountId, EntityResolver entityResolver, DBProvider dbProvider, DBTransactionPolicy txPolicy) {
        super(context, dbProvider, txPolicy);
        this.accountId = accountId;
        this.entityProcessor = new EntityProcessor(context.getContextId(), entityResolver);
    }

    @Override
    public List<Attendee> loadAttendees(String eventId) throws OXException {
        return loadAttendees(new String[] { eventId }).get(eventId);
    }

    @Override
    public Map<String, List<Attendee>> loadAttendees(String[] eventIds) throws OXException {
        return loadAttendees(eventIds, null);
    }

    @Override
    public Map<String, List<Attendee>> loadAttendees(String[] eventIds, Boolean internal) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectAttendees(connection, eventIds, internal, false, null);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public Map<String, Integer> loadAttendeeCounts(String[] eventIds, Boolean internal) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectAttendeeCounts(connection, eventIds, internal, false);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public Map<String, Attendee> loadAttendee(String[] eventIds, Attendee attendee, AttendeeField[] fields) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectAttendee(connection, eventIds, attendee, fields);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public Map<String, List<Attendee>> loadAttendeeTombstones(String[] eventIds) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectAttendees(connection, eventIds, null, true, null);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public void insertAttendees(String eventId, List<Attendee> attendees) throws OXException {
        if (null != attendees) {
            insertAttendees(java.util.Collections.singletonMap(eventId, attendees), false);
        }
    }

    @Override
    public void insertAttendees(Map<String, List<Attendee>> attendeesByEventId) throws OXException {
        insertAttendees(attendeesByEventId, false);
    }

    @Override
    public void deleteAttendees(String eventId) throws OXException {
        deleteAttendees(java.util.Collections.singletonList(eventId));
    }

    @Override
    public void deleteAttendees(List<String> eventIds) throws OXException {
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            for (List<String> chunk : Lists.partition(eventIds, DELETE_CHUNK_SIZE)) {
                updated += deleteAttendees(connection, chunk);
            }
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e, MAPPER, (Attendee) null, connection, "calendar_attendee");
        } finally {
            release(connection, updated);
        }
    }

    @Override
    public void deleteAttendees(String eventId, List<Attendee> attendees) throws OXException {
        if (null == attendees || 0 == attendees.size()) {
            return;
        }
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            updated += deleteAttendees(connection, eventId, attendees);
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(connection, updated);
        }
    }

    @Override
    public boolean deleteAllAttendees() throws OXException {
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            updated += deleteAttendees(connection);
            updated += deleteAttendeesTombstones(connection);
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(connection, updated);
        }
        return 0 < updated;
    }

    private int deleteAttendees(Connection connection) throws SQLException {
        int updated = 0;
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM calendar_attendee WHERE cid=? AND account=?;")) {
            stmt.setInt(1, context.getContextId());
            stmt.setInt(2, accountId);
            updated += logExecuteUpdate(stmt);
        }
        return updated;
    }

    private int deleteAttendeesTombstones(Connection connection) throws SQLException {
        int updated = 0;
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM calendar_attendee_tombstone WHERE cid=? AND account=?;")) {
            stmt.setInt(1, context.getContextId());
            stmt.setInt(2, accountId);
            updated += logExecuteUpdate(stmt);
        }
        return updated;
    }

    @Override
    public void updateAttendee(String eventId, Attendee attendee) throws OXException {
        updateAttendees(eventId, java.util.Collections.singletonList(attendee));
    }

    @Override
    public void updateAttendees(String eventId, List<Attendee> attendees) throws OXException {
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            updated += updateAttendees(connection, eventId, attendees);
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(connection, updated);
        }
    }

    @Override
    public void insertAttendeeTombstone(String eventId, Attendee attendee) throws OXException {
        insertAttendeeTombstones(eventId, java.util.Collections.singletonList(attendee));
    }

    @Override
    public void insertAttendeeTombstones(String eventId, List<Attendee> attendees) throws OXException {
        if (null != attendees && 0 < attendees.size()) {
            insertAttendees(java.util.Collections.singletonMap(eventId, attendees), true);
        }
    }

    @Override
    public void insertAttendeeTombstones(Map<String, List<Attendee>> attendeesByEventId) throws OXException {
        insertAttendees(attendeesByEventId, true);
    }

    private void insertAttendees(Map<String, List<Attendee>> attendeesByEventId, boolean tombstones) throws OXException {
        if (null == attendeesByEventId || 0 == attendeesByEventId.size()) {
            return;
        }
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            if (1 == attendeesByEventId.size()) {
                updated = insertAttendees(connection, attendeesByEventId, tombstones);
            } else {
                updated = insertAttendees(connection, attendeesByEventId, tombstones, INSERT_CHUNK_SIZE);
            }
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e, MAPPER, Lists.newArrayList(Iterables.concat(attendeesByEventId.values())), connection, "calendar_attendee");
        } finally {
            release(connection, updated);
        }
    }

    private int updateAttendees(Connection connection, String eventId, List<Attendee> attendees) throws OXException {
        int updated = 0;
        for (Attendee attendee : attendees) {
            AttendeeField[] fields = MAPPER.getMappedFields(MAPPER.getAssignedFields(attendee));
            String sql = new StringBuilder()
                .append("UPDATE calendar_attendee SET ").append(MAPPER.getAssignments(fields))
                .append(" WHERE cid=? AND account=? AND event=? AND ")
                .append(isInternal(attendee) ? "entity" : "uri").append("=?;")
            .toString();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                int parameterIndex = 1;
                MAPPER.validateAll(attendee);
                parameterIndex = MAPPER.setParameters(stmt, parameterIndex, attendee, fields);
                stmt.setInt(parameterIndex++, context.getContextId());
                stmt.setInt(parameterIndex++, accountId);
                stmt.setInt(parameterIndex++, asInt(eventId));
                if (isInternal(attendee)) {
                    stmt.setInt(parameterIndex++, attendee.getEntity());
                } else {
                    stmt.setString(parameterIndex++, attendee.getUri());
                }
                updated += logExecuteUpdate(stmt);
            } catch (SQLException e) {
                throw asOXException(e, MAPPER, attendee, connection, "calendar_attendee");
            }
        }
        return updated;
    }

    private int deleteAttendees(Connection connection, List<String> eventIds) throws SQLException {
        if (null == eventIds || 0 == eventIds.size()) {
            return 0;
        }
        StringBuilder stringBuilder = new StringBuilder()
            .append("DELETE FROM calendar_attendee WHERE cid=? AND account=? AND event")
            .append(Databases.getPlaceholders(eventIds.size())).append(';');
        ;
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            for (String id : eventIds) {
                stmt.setInt(parameterIndex++, asInt(id));
            }
            return logExecuteUpdate(stmt);
        }
    }

    private int deleteAttendees(Connection connection, String eventId, List<Attendee> attendees) throws SQLException {
        int updated = 0;
        for (Attendee attendee : attendees) {
            String sql = new StringBuilder()
                .append("DELETE FROM calendar_attendee WHERE cid=? AND account=? AND event=? AND ")
                .append(isInternal(attendee) ? "entity" : "uri").append("=?;")
            .toString();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, context.getContextId());
                stmt.setInt(2, accountId);
                stmt.setInt(3, asInt(eventId));
                if (isInternal(attendee)) {
                    stmt.setInt(4, attendee.getEntity());
                } else {
                    stmt.setString(4, attendee.getUri());
                }
                updated += logExecuteUpdate(stmt);
            }
        }
        return updated;
    }

    private int insertAttendees(Connection connection, Map<String, List<Attendee>> attendeesByEventId, boolean tombstones, int chunkSize) throws SQLException, OXException {
        int updated = 0;
        Map<String, List<Attendee>> currentChunk = new HashMap<String, List<Attendee>>();
        int currentSize = 0;
        for (Entry<String, List<Attendee>> entry : attendeesByEventId.entrySet()) {
            /*
             * add to current chunk
             */
            currentChunk.put(entry.getKey(), entry.getValue());
            currentSize += entry.getValue().size();
            if (currentSize >= chunkSize) {
                /*
                 * insert & reset current chunk
                 */
                updated += insertAttendees(connection, currentChunk, tombstones);
                currentChunk.clear();
                currentSize = 0;
            }
        }
        /*
         * finally insert remaining chunk
         */
        if (0 < currentSize) {
            updated += insertAttendees(connection, currentChunk, tombstones);
        }
        return updated;
    }

    private static final int MAX_RETRY = 5;

    private int insertAttendees(Connection connection, Map<String, List<Attendee>> attendeesByEventId, boolean tombstones) throws SQLException, OXException {
        if (null == attendeesByEventId || 0 == attendeesByEventId.size()) {
            return 0;
        }
        AttendeeField[] mappedFields = MAPPER.getMappedFields();
        StringBuilder stringBuilder = new StringBuilder()
            .append(tombstones ? "REPLACE INTO calendar_attendee_tombstone " : "INSERT INTO calendar_attendee ")
            .append("(cid,account,event,").append(MAPPER.getColumns(mappedFields)).append(") VALUES ")
        ;
        for (List<Attendee> attendees : attendeesByEventId.values()) {
            for (int i = 0; i < attendees.size(); i++) {
                stringBuilder.append("(?,?,?,").append(MAPPER.getParameters(mappedFields)).append("),");
            }
        }
        stringBuilder.setLength(stringBuilder.length() - 1);
        stringBuilder.append(';');
        int retry = 0;
        Random random = new Random();
        while (retry < MAX_RETRY) {
            try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
                int parameterIndex = 1;
                boolean attendeesToStore = false;
                for (Entry<String, List<Attendee>> entry : attendeesByEventId.entrySet()) {
                    Set<Integer> usedEntities = new HashSet<Integer>(entry.getValue().size());
                    int entitySalt = random.nextInt();
                    int eventId = asInt(entry.getKey());
                    List<Attendee> attendeeList = entry.getValue();
                    if (attendeeList != null && attendeeList.size() > 0) {
                        attendeesToStore = true;
                        for (Attendee attendee : entry.getValue()) {
                            MAPPER.validateAll(attendee);
                            attendee = entityProcessor.adjustPriorInsert(attendee, usedEntities, entitySalt);
                            stmt.setInt(parameterIndex++, context.getContextId());
                            stmt.setInt(parameterIndex++, accountId);
                            stmt.setInt(parameterIndex++, eventId);
                            parameterIndex = MAPPER.setParameters(stmt, parameterIndex, attendee, mappedFields);
                        }
                    }
                }
                return attendeesToStore ? logExecuteUpdate(stmt) : 0;
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062 && retry < MAX_RETRY) { // Duplicate entry '%s' for key %d
                    retry++;
                    RdbAttendeeStorage_LOG.info("Primary key violation. Message: {}. Retry ({}).", e.getMessage(), Autoboxing.I(retry));
                } else {
                    throw e;
                }
            }
        }
        return 0;
    }

    private Map<String, List<Attendee>> selectAttendees(Connection connection, String[] eventIds, Boolean internal, boolean tombstones, AttendeeField[] fields) throws SQLException, OXException {
        if (null == eventIds || 0 == eventIds.length) {
            return java.util.Collections.emptyMap();
        }
        AttendeeField[] mappedFields = MAPPER.getMappedFields(fields);
        StringBuilder stringBuilder = new StringBuilder()
            .append("SELECT event,").append(MAPPER.getColumns(mappedFields))
            .append(" FROM ").append(tombstones ? "calendar_attendee_tombstone" : "calendar_attendee")
            .append(" WHERE cid=? AND account=? AND event").append(Databases.getPlaceholders(eventIds.length))
        ;
        if (null != internal) {
            stringBuilder.append(" AND entity").append(internal.booleanValue() ? ">=0" : "<0");
        }
        stringBuilder.append(';');
        Map<String, List<Attendee>> attendeesByEventId = new HashMap<String, List<Attendee>>(eventIds.length);
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            for (String eventId : eventIds) {
                stmt.setInt(parameterIndex++, Integer.parseInt(eventId));
            }
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                while (resultSet.next()) {
                    String eventId = resultSet.getString(1);
                    Collections.put(attendeesByEventId, eventId, readAttendee(eventId, resultSet, mappedFields));
                }
            }
        }
        return attendeesByEventId;
    }

    private Map<String, Integer> selectAttendeeCounts(Connection connection, String[] eventIds, Boolean internal, boolean tombstones) throws SQLException {
        if (null == eventIds || 0 == eventIds.length) {
            return java.util.Collections.emptyMap();
        }
        StringBuilder stringBuilder = new StringBuilder()
            .append("SELECT event,COUNT(*) FROM ").append(tombstones ? "calendar_attendee_tombstone" : "calendar_attendee")
            .append(" WHERE cid=? AND account=? AND event").append(Databases.getPlaceholders(eventIds.length))
        ;
        if (null != internal) {
            stringBuilder.append(" AND entity").append(internal.booleanValue() ? ">=0" : "<0");
        }
        stringBuilder.append(" GROUP BY event;");
        Map<String, Integer> attendeeCountsByEventId = new HashMap<String, Integer>(eventIds.length);
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            for (String eventId : eventIds) {
                stmt.setInt(parameterIndex++, Integer.parseInt(eventId));
            }
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                while (resultSet.next()) {
                    attendeeCountsByEventId.put(resultSet.getString(1), I(resultSet.getInt(2)));
                }
            }
        }
        return attendeeCountsByEventId;
    }

    private Map<String, Attendee> selectAttendee(Connection connection, String[] eventIds, Attendee attendee, AttendeeField[] fields) throws SQLException, OXException {
        if (null == eventIds || 0 == eventIds.length) {
            return java.util.Collections.emptyMap();
        }
        AttendeeField[] mappedFields = MAPPER.getMappedFields(fields);
        StringBuilder stringBuilder = new StringBuilder()
            .append("SELECT event,").append(MAPPER.getColumns(mappedFields))
            .append(" FROM calendar_attendee WHERE cid=? AND account=? AND ")
            .append(isInternal(attendee) ? "entity" : "uri").append("=?")
            .append(" AND event").append(Databases.getPlaceholders(eventIds.length)).append(';')
        ;
        Map<String, Attendee> attendeeByEventId = new HashMap<String, Attendee>(eventIds.length);
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            if (isInternal(attendee)) {
                stmt.setInt(parameterIndex++, attendee.getEntity());
            } else {
                stmt.setString(parameterIndex++, attendee.getUri());
            }
            for (String eventId : eventIds) {
                stmt.setInt(parameterIndex++, Integer.parseInt(eventId));
            }
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                while (resultSet.next()) {
                    String eventId = resultSet.getString(1);
                    attendeeByEventId.put(eventId, readAttendee(eventId, resultSet, mappedFields));
                }
            }
        }
        return attendeeByEventId;
    }

    private Attendee readAttendee(String eventId, ResultSet resultSet, AttendeeField[] fields) throws SQLException, OXException {
        Attendee attendee = MAPPER.fromResultSet(resultSet, fields);
        try {
            return entityProcessor.adjustAfterLoad(attendee);
        } catch (OXException e) {
            if (CalendarExceptionCodes.INVALID_CALENDAR_USER.equals(e) && isInternal(attendee) && Strings.isNotEmpty(attendee.getUri())) {
                /*
                 * try to recover by removing the stored URI & assign the default URI for this internal entity
                 */
                Attendee fallback = AttendeeMapper.getInstance().copy(attendee, null, (AttendeeField[]) null);
                fallback.removeUri();
                try {
                    fallback = entityProcessor.adjustAfterLoad(fallback);
                } catch (OXException e2) {
                    fallback = fallBackNotFound(e2, eventId, attendee);
                    if (fallback == null) {
                        addInvalidDataWarning(eventId, EventField.ATTENDEES, ProblemSeverity.NORMAL, "Skipping non-existent user " + attendee, e);
                        return null;
                    }
                }
                String message = "Invalid stored calendar user address \"" + attendee.getUri() + "\" for entity " + attendee.getEntity() + ", falling back to default address \"" + fallback.getUri() + "\"";
                addInvalidDataWarning(eventId, EventField.ATTENDEES, ProblemSeverity.NORMAL, message, e);
                return fallback;
            }
            throw e;
        }
    }

    private Attendee fallBackNotFound(OXException e, String eventId, Attendee attendee) throws OXException {
        if (CalendarExceptionCodes.INVALID_CALENDAR_USER.equals(e)) {
            /*
             * invalid calendar user; possibly a no longer existing user - add as external attendee as fallback if possible
             */
            Attendee externalAttendee = CalendarUtils.asExternal(attendee, AttendeeMapper.getInstance().getMappedFields());
            if (externalAttendee != null) {
                String message = "Falling back to external attendee representation for non-existent user " + attendee;
                addInvalidDataWarning(eventId, EventField.ATTENDEES, ProblemSeverity.MINOR, message, e);
                return (entityProcessor.getEntityResolver().applyEntityData(externalAttendee));
            }
        }
        return null;
    }

}
