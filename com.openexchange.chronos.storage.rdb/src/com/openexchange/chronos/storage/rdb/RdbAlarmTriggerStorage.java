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

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.java.Autoboxing.i;
import static com.openexchange.java.Autoboxing.l;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import com.google.common.collect.Lists;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.AlarmTrigger;
import com.openexchange.chronos.AlarmTriggerField;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.common.AlarmUtils;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.exception.ProblemSeverity;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.service.RecurrenceService;
import com.openexchange.chronos.storage.AlarmTriggerStorage;
import com.openexchange.chronos.storage.rdb.osgi.Services;
import com.openexchange.database.Databases;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;


/**
 * {@link RdbAlarmTriggerStorage} is an implementation of the {@link AlarmTriggerStorage}
 * which uses a sql database to store alarm triggers.
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.0
 */
public class RdbAlarmTriggerStorage extends RdbStorage implements AlarmTriggerStorage {

    private static final AlarmTriggerDBMapper MAPPER = AlarmTriggerDBMapper.getInstance();
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final int DELETE_CHUNK_SIZE = 200;

    private final int accountId;
    private final RecurrenceService recurrenceService;
    private final EntityResolver resolver;

    private static final String FLOATING_TRIGGER_SQL = "SELECT alarm, triggerDate, floatingTimezone, relatedTime FROM calendar_alarm_trigger WHERE cid=? AND account=? AND user=? AND floatingTimezone IS NOT NULL;";
    private static final String UPDATE_TRIGGER_TIME_SQL = "UPDATE calendar_alarm_trigger SET triggerDate=?, floatingTimezone=? WHERE cid=? AND account=? AND alarm=?;";

    /**
     * Initializes a new {@link RdbAlarmTriggerStorage}.
     *
     * @param context The context id
     * @param accountId The account id
     * @param dbProvider A db provider
     * @param txPolicy The transaction policy
     * @throws OXException
     */
    protected RdbAlarmTriggerStorage(Context context, int accountId, DBProvider dbProvider, DBTransactionPolicy txPolicy, EntityResolver resolver) throws OXException {
        super(context, dbProvider, txPolicy);
        this.accountId = accountId;
        this.recurrenceService = Services.getService(RecurrenceService.class, true);
        this.resolver = resolver;
    }

    /**
     * Inserts the alarm trigger
     *
     * @param trigger The {@link AlarmTrigger}
     * @throws OXException
     */
    private void insertAlarmTrigger(AlarmTrigger trigger) throws OXException {
        Connection writeCon = dbProvider.getWriteConnection(context);
        int updated = 0;
        try {
            txPolicy.setAutoCommit(writeCon, false);
            updated = insertAlarmTrigger(trigger, writeCon);
            txPolicy.commit(writeCon);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(writeCon, updated);
        }
    };

    private int insertAlarmTrigger(AlarmTrigger trigger, Connection writeCon) throws OXException {
        try {
            AlarmTriggerField[] mappedFields = MAPPER.getMappedFields();
            String sql = new StringBuilder().append("INSERT INTO calendar_alarm_trigger (cid, account, ").append(MAPPER.getColumns(mappedFields)).append(") VALUES (?,?,").append(MAPPER.getParameters(mappedFields)).append(");").toString();
            try (PreparedStatement stmt = writeCon.prepareStatement(sql)) {
                int parameterIndex = 1;
                stmt.setInt(parameterIndex++, context.getContextId());
                stmt.setInt(parameterIndex++, accountId);
                parameterIndex = MAPPER.setParameters(stmt, parameterIndex, trigger, mappedFields);
                return logExecuteUpdate(stmt);
            }
        } catch (SQLException e) {
            throw CalendarExceptionCodes.DB_ERROR.create(e, e.getMessage());
        }
    }

    private AlarmTrigger getAlarmTrigger(int id, Connection con) throws OXException {
        try {
            AlarmTriggerField[] mappedFields = MAPPER.getMappedFields();
            StringBuilder stringBuilder = new StringBuilder().append("SELECT account,cid,").append(MAPPER.getColumns(mappedFields)).append(" FROM ").append("calendar_alarm_trigger").append(" WHERE cid=? AND account=? AND alarm=?");

            try (PreparedStatement stmt = con.prepareStatement(stringBuilder.toString())) {
                int parameterIndex = 1;
                stmt.setInt(parameterIndex++, context.getContextId());
                stmt.setInt(parameterIndex++, accountId);
                stmt.setInt(parameterIndex++, id);
                try (ResultSet resultSet = logExecuteQuery(stmt)) {
                    return resultSet.next() ? readTrigger(resultSet, mappedFields) : null;
                }
            }
        } catch (SQLException e) {
            throw CalendarExceptionCodes.DB_ERROR.create(e, e.getMessage());
        }
    }

    private List<AlarmTrigger> getAlarmTriggers(int user, Date from, Date until, Connection con) throws OXException {
        try {
            AlarmTriggerField[] mappedFields = MAPPER.getMappedFields();
            StringBuilder stringBuilder = new StringBuilder().append("SELECT account,cid,").append(MAPPER.getColumns(mappedFields)).append(" FROM ").append("calendar_alarm_trigger").append(" WHERE cid=? AND account=? AND user=?");

            if (until != null) {
                stringBuilder.append(" AND triggerDate<?");
            }
            if (null != from) {
                stringBuilder.append(" AND (recurrence IS NOT NULL OR triggerDate>=?)");
            }
            stringBuilder.append(" ORDER BY triggerDate");

            List<AlarmTrigger> alrmTriggers = new ArrayList<AlarmTrigger>();
            try (PreparedStatement stmt = con.prepareStatement(stringBuilder.toString())) {
                int parameterIndex = 1;
                stmt.setInt(parameterIndex++, context.getContextId());
                stmt.setInt(parameterIndex++, accountId);
                stmt.setInt(parameterIndex++, user);

                if (until != null) {
                    stmt.setLong(parameterIndex++, until.getTime());
                }
                if (null != from) {
                    stmt.setLong(parameterIndex++, from.getTime());
                }

                try (ResultSet resultSet = logExecuteQuery(stmt)) {
                    while (resultSet.next()) {
                        alrmTriggers.add(readTrigger(resultSet, mappedFields));
                    }
                }
            }
            return alrmTriggers;
        } catch (SQLException e) {
            throw CalendarExceptionCodes.DB_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Retrieves an {@link AlarmTrigger} by reading the given {@link AlarmTriggerField}s from the result set.
     *
     * @param resultSet The {@link ResultSet}
     * @param fields The fields to read
     * @return The {@link AlarmTrigger}
     * @throws SQLException
     * @throws OXException
     */
    private AlarmTrigger readTrigger(ResultSet resultSet, AlarmTriggerField... fields) throws SQLException, OXException {
        return MAPPER.fromResultSet(resultSet, fields);
    }

    @Override
    public void insertTriggers(Event event, Map<Integer, List<Alarm>> alarmsPerUserId) throws OXException {
        for (Map.Entry<Integer, List<Alarm>> entry : alarmsPerUserId.entrySet()) {
            List<Alarm> alarms = entry.getValue();
            if (alarms == null || alarms.isEmpty()) {
                // Skip user in case no alarms are available
                continue;
            }

            Integer userId = entry.getKey();
            for (Alarm alarm : alarms) {
                AlarmTrigger trigger = prepareTrigger(userId, alarm, event);
                if (trigger == null) {
                    // Skip invalid and past alarm triggers
                    continue;
                }
                insertAlarmTrigger(trigger);
            }
        }
    }

    private static String getFolderId(Event event, int userId) throws OXException {
        String folderId = CalendarUtils.getFolderView(event, userId);
        if (null == folderId) {
            throw CalendarExceptionCodes.UNEXPECTED_ERROR.create("No folder view for user " + userId);
        }
        return folderId;
    }

    private void addRelatedDate(Alarm alarm, Event event, AlarmTrigger trigger) {
        if (alarm.getTrigger().getDateTime() == null) {
            trigger.setRelatedTime(L(AlarmUtils.getRelatedDate(alarm.getTrigger().getRelated(), event).getTimestamp()));
        }
    }

    @Override
    public void deleteTriggers(String eventId) throws OXException {
        deleteTriggers(Collections.singletonList(eventId));
    }

    @Override
    public void deleteTriggers(List<String> eventIds) throws OXException {
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            for (List<String> chunk : Lists.partition(eventIds, DELETE_CHUNK_SIZE)) {
                updated += deleteTriggers(connection, chunk);
            }
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(connection, updated);
        }
    }

    @Override
    public void deleteTriggersById(List<Integer> alarmIds) throws OXException {
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            updated += deleteTriggersById(connection, alarmIds);
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(connection, updated);
        }
    }

    @Override
    public void deleteTriggers(List<String> eventIds, int userId) throws OXException {
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            for (List<String> chunk : Lists.partition(eventIds, DELETE_CHUNK_SIZE)) {
                updated += deleteTriggers(connection, chunk, userId);
            }
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(connection, updated);
        }
    }

    @Override
    public void deleteTriggers(int userId) throws OXException {
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            updated = deleteTriggers(connection, userId);
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(connection, updated);
        }
    }

    @Override
    public boolean deleteAllTriggers() throws OXException {
        int updated = 0;
        Connection connection = null;
        try {
            connection = dbProvider.getWriteConnection(context);
            txPolicy.setAutoCommit(connection, false);
            updated = deleteTriggers(connection);
            txPolicy.commit(connection);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(connection, updated);
        }
        return 0 < updated;
    }

    @Override
    public Set<String> hasTriggers(int userId, String[] eventIds) throws OXException {
        Connection connection = null;
        try {
            connection = dbProvider.getReadConnection(context);
            return selectHasTriggers(connection, userId, eventIds);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            dbProvider.releaseReadConnection(context, connection);
        }
    }

    private Set<String> selectHasTriggers(Connection connection, int userId, String[] eventIds) throws SQLException {
        if (null == eventIds || 0 == eventIds.length) {
            return Collections.emptySet();
        }
        StringBuilder stringBuilder = new StringBuilder()
            .append("SELECT DISTINCT(eventId) FROM calendar_alarm_trigger WHERE cid=? AND account=? AND user=? AND eventId")
            .append(Databases.getPlaceholders(eventIds.length)).append(';')
        ;
        Set<String> eventIdsWithTrigger = new HashSet<String>();
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            stmt.setInt(parameterIndex++, userId);
            for (String id : eventIds) {
                stmt.setString(parameterIndex++, id);
            }
            try (ResultSet resultSet = logExecuteQuery(stmt)) {
                while (resultSet.next()) {
                    eventIdsWithTrigger.add(resultSet.getString("eventId"));
                }
            }
            return eventIdsWithTrigger;
        }
    }

    private int deleteTriggers(Connection connection) throws SQLException {
        String sql = "DELETE FROM calendar_alarm_trigger WHERE cid=? AND account=?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            return logExecuteUpdate(stmt);
        }
    }

    private int deleteTriggers(Connection connection, List<String> ids) throws SQLException {
        if (null == ids || 0 == ids.size()) {
            return 0;
        }
        StringBuilder stringBuilder = new StringBuilder().append("DELETE FROM calendar_alarm_trigger WHERE cid=? AND account=? AND eventId").append(Databases.getPlaceholders(ids.size())).append(';');
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            for (String id : ids) {
                stmt.setString(parameterIndex++, id);
            }
            return logExecuteUpdate(stmt);
        }
    }

    private int deleteTriggersById(Connection connection, List<Integer> alarmIds) throws SQLException {
        if (null == alarmIds || 0 == alarmIds.size()) {
            return 0;
        }
        StringBuilder stringBuilder = new StringBuilder().append("DELETE FROM calendar_alarm_trigger WHERE cid=? AND account=? AND alarm").append(Databases.getPlaceholders(alarmIds.size())).append(';');
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            for (Integer id : alarmIds) {
                stmt.setInt(parameterIndex++, i(id));
            }
            return logExecuteUpdate(stmt);
        }
    }

    private int deleteTriggers(Connection connection, List<String> ids, int userId) throws SQLException {
        if (null == ids || 0 == ids.size()) {
            return 0;
        }
        StringBuilder stringBuilder = new StringBuilder().append("DELETE FROM calendar_alarm_trigger WHERE cid=? AND account=? AND user=? AND eventId").append(Databases.getPlaceholders(ids.size())).append(';');
        try (PreparedStatement stmt = connection.prepareStatement(stringBuilder.toString())) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            stmt.setInt(parameterIndex++, userId);
            for (String id : ids) {
                stmt.setString(parameterIndex++, id);
            }
            return logExecuteUpdate(stmt);
        }
    }

    private int deleteTriggers(Connection connection, int userId) throws SQLException {
        String sql = "DELETE FROM calendar_alarm_trigger WHERE cid=? AND account=? AND user=?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            stmt.setInt(parameterIndex++, userId);
            return logExecuteUpdate(stmt);
        }
    }

    @Override
    public List<AlarmTrigger> loadTriggers(int userId, Date from, Date until) throws OXException {
        Connection con = dbProvider.getReadConnection(context);
        try {
            return getAlarmTriggers(userId, from, until, con);
        } finally {
            dbProvider.releaseReadConnection(context, con);
        }
    }

    @Override
    public AlarmTrigger loadTrigger(int id) throws OXException {
        Connection con = dbProvider.getReadConnection(context);
        try {
            return getAlarmTrigger(id, con);
        } finally {
            dbProvider.releaseReadConnection(context, con);
        }
    }

    @Override
    public Integer recalculateFloatingAlarmTriggers(int userId) throws OXException {
        Connection writeCon = dbProvider.getWriteConnection(context);
        int updated = 0;
        try {
            txPolicy.setAutoCommit(writeCon, false);
            updated = recalculateFloatingAlarmTriggers(userId, writeCon);
            txPolicy.commit(writeCon);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(writeCon, updated);
        }
        return I(updated);

    }

    private int recalculateFloatingAlarmTriggers(int userId, Connection writeCon) throws OXException, SQLException {
        ResultSet resultSet = null;
        PreparedStatement stmt = null;
        List<AlarmTrigger> triggers = new ArrayList<>();
        try {
            stmt = writeCon.prepareStatement(FLOATING_TRIGGER_SQL);
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            stmt.setInt(parameterIndex++, userId);
            resultSet = logExecuteQuery(stmt);
            while (resultSet.next()) {
                triggers.add(readTrigger(resultSet, AlarmTriggerField.ALARM_ID, AlarmTriggerField.TIME, AlarmTriggerField.FLOATING_TIMEZONE, AlarmTriggerField.RELATED_TIME));
            }

            if (triggers.isEmpty()) {
                // recalculation isn't needed
                return 0;
            }

        } finally {
            Databases.closeSQLStuff(resultSet, stmt);
        }

        try {
            stmt = writeCon.prepareCall(UPDATE_TRIGGER_TIME_SQL);

            for (AlarmTrigger trigger : triggers) {
                int index = 1;
                TimeZone oldTimeZone = trigger.getTimezone();
                TimeZone newTimeZone = resolver.getTimeZone(userId);
                long relatedTime = l(trigger.getRelatedTime() == null ? trigger.getTime() : trigger.getRelatedTime());
                int offsetOld = oldTimeZone.getOffset(relatedTime);
                int offsetNew = newTimeZone.getOffset(relatedTime);
                int dif = offsetOld - offsetNew;
                long newTriggerTime = l(trigger.getTime()) + dif;
                stmt.setLong(index++, newTriggerTime);
                stmt.setString(index++, newTimeZone.getID());
                stmt.setInt(index++, context.getContextId());
                stmt.setInt(index++, accountId);
                stmt.setInt(index++, i(trigger.getAlarm()));
                stmt.addBatch();
            }

            int[] executeBatch = stmt.executeBatch();
            int result = 0;
            for (int x : executeBatch) {
                result += x;
            }
            return result;
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    @Override
    public void insertTriggers(Map<String, Map<Integer, List<Alarm>>> alarmMap, List<Event> events) throws OXException {

        Connection writeCon = dbProvider.getWriteConnection(context);
        int updated = 0;
        try {
            txPolicy.setAutoCommit(writeCon, false);
            updated = insertTrigger(alarmMap, events, writeCon);
            txPolicy.commit(writeCon);
        } catch (SQLException e) {
            throw asOXException(e);
        } finally {
            release(writeCon, updated);
        }

    }

    private int insertTrigger(Map<String, Map<Integer, List<Alarm>>> alarmMap, List<Event> events, Connection writeCon) throws OXException, SQLException {
        try (PreparedStatement stmt = getInsertStatementForBatch(writeCon)) {
            for (Event event : events) {

                Map<Integer, List<Alarm>> alarmsPerAttendee = alarmMap.get(event.getId());
                if (alarmsPerAttendee == null) {
                    continue;
                }

                for (Entry<Integer, List<Alarm>> user : alarmsPerAttendee.entrySet()) {

                    List<Alarm> alarms = user.getValue();
                    if (alarms == null || alarms.isEmpty()) {
                        // Skip user in case no alarms available
                        continue;
                    }
                    for (Alarm alarm : alarms) {

                        AlarmTrigger trigger = prepareTrigger(user.getKey(), alarm, event);
                        if (trigger == null) {
                            // Skip past and invalid triggers
                            continue;
                        }
                        addBatch(trigger, stmt);
                    }
                }
            }
            int[] executeBatch = stmt.executeBatch();
            int result = 0;
            for (int i : executeBatch) {
                result += i;
            }
            return result;
        }
    }

    /**
     * Prepares an {@link AlarmTrigger} object
     *
     * @param userId The user id
     * @param alarm The corresponding alarm
     * @param event The corresponding event
     * @return The prepared {@link AlarmTrigger} or null in case the alarm is in the past or is invalid
     * @throws OXException
     */
    private AlarmTrigger prepareTrigger(Integer userId, Alarm alarm, Event event) throws OXException {
        AlarmTrigger trigger = new AlarmTrigger();
        trigger.setUserId(userId);
        trigger.setAction(alarm.getAction().getValue());
        trigger.setPushed(Boolean.FALSE);
        trigger.setAlarm(I(alarm.getId()));
        trigger.setEventId(event.getId());

        if (CalendarUtils.isFloating(event) && alarm.getTrigger().getDateTime()==null) {
            try {
                trigger.setTimezone(resolver.getTimeZone(i(userId)));
            } catch (OXException e) {
                addInvalidDataWarning(event.getId(), EventField.ALARMS, ProblemSeverity.MINOR, "Unable to determine timezone for user \"" + userId + "\", skipping insertion of alarm triggers", e);
                return null;
            }
        }
        TimeZone tz = trigger.containsTimezone() ? trigger.getTimezone() : UTC;
        Date now = new Date();
        if (event.containsRecurrenceRule() && event.getRecurrenceRule() != null && event.getRecurrenceId() == null && event.getId().equals(event.getSeriesId())) {
            Event nextTriggerEvent = AlarmUtils.getNextTriggerEvent(event, alarm, now, tz, recurrenceService);
            Date triggerTime = nextTriggerEvent == null ? null : AlarmUtils.getTriggerTime(alarm.getTrigger(), nextTriggerEvent, tz);
            if (nextTriggerEvent == null || triggerTime == null || triggerTime.before(now)) {
                return null;
            }
            addRelatedDate(alarm, event, trigger);
            trigger.setRecurrenceId(nextTriggerEvent.getRecurrenceId());
            trigger.setTime(L(triggerTime.getTime()));
        } else {
            Date triggerTime = AlarmUtils.getTriggerTime(alarm.getTrigger(), event, tz);
            if (triggerTime == null || triggerTime.before(now) || (alarm.containsAcknowledged() && !alarm.getAcknowledged().before(triggerTime))) {
                return null;
            }
            trigger.setTime(L(triggerTime.getTime()));
            if (event.getRecurrenceId() != null) {
                trigger.setRecurrenceId(event.getRecurrenceId());
            }
            addRelatedDate(alarm, event, trigger);
        }

        // Set proper folder id
        try {
            trigger.setFolder(getFolderId(event, i(userId)));
        } catch (OXException e) {
            addInvalidDataWarning(event.getId(), EventField.ALARMS, ProblemSeverity.MINOR, "Unable to determine parent folder for user \"" + userId + "\", skipping insertion of alarm triggers", e);
            return null;
        }

        trigger.setProcessed(L(0L));

        return trigger;
    }

    /**
     * Retrieves an {@link PreparedStatement} for batch operations.
     *
     * @param writeCon The write {@link Connection}
     * @return The {@link PreparedStatement}
     * @throws OXException
     */
    private PreparedStatement getInsertStatementForBatch(Connection writeCon) throws OXException {
        try {
            AlarmTriggerField[] mappedFields = MAPPER.getMappedFields();
            String sql = new StringBuilder().append("INSERT INTO calendar_alarm_trigger (cid, account, ").append(MAPPER.getColumns(mappedFields)).append(") VALUES (?,?,").append(MAPPER.getParameters(mappedFields)).append(");").toString();
            return writeCon.prepareStatement(sql);
        } catch (SQLException e) {
            throw CalendarExceptionCodes.DB_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Adds another batch to the given statement
     *
     * @param trigger The trigger to add
     * @param stmt The statement
     * @throws OXException
     */
    private void addBatch(AlarmTrigger trigger, PreparedStatement stmt) throws OXException {
        try {
            int parameterIndex = 1;
            stmt.setInt(parameterIndex++, context.getContextId());
            stmt.setInt(parameterIndex++, accountId);
            parameterIndex = MAPPER.setParameters(stmt, parameterIndex, trigger, MAPPER.getMappedFields());
            stmt.addBatch();
        } catch (SQLException e) {
            throw CalendarExceptionCodes.DB_ERROR.create(e, e.getMessage());
        }
    }

}
