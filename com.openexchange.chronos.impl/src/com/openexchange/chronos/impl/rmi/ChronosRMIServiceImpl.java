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

package com.openexchange.chronos.impl.rmi;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.impl.Utils;
import com.openexchange.chronos.impl.osgi.Services;
import com.openexchange.chronos.impl.session.DefaultCalendarUtilities;
import com.openexchange.chronos.rmi.ChronosRMIService;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.chronos.storage.CalendarStorageFactory;
import com.openexchange.context.ContextService;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.database.provider.SimpleDBProvider;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.tools.oxfolder.OXFolderSQL;
import com.openexchange.user.UserExceptionCode;
import com.openexchange.user.UserService;

/**
 * {@link ChronosRMIServiceImpl}
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.com">Martin Herfurth</a>
 * @since v7.10.2
 */
public class ChronosRMIServiceImpl implements ChronosRMIService {

    Logger LOG = LoggerFactory.getLogger(ChronosRMIServiceImpl.class);

    private DefaultCalendarUtilities calendarUtilities;

    public ChronosRMIServiceImpl(DefaultCalendarUtilities calendarUtilities) {
        super();
        this.calendarUtilities = calendarUtilities;
    }

    @Override
    public void setEventOrganizer(int contextId, int eventId, int userId) throws RemoteException {
        DBTransactionPolicy txPolicy = DBTransactionPolicy.NORMAL_TRANSACTIONS;

        Connection readCon = null;
        Connection writeCon = null;
        DatabaseService databaseService = null;
        boolean backAfterRead = true;
        try {
            databaseService = Services.getService(DatabaseService.class, true);
            readCon = databaseService.getReadOnly(contextId);
            writeCon = databaseService.getWritable(contextId);
            txPolicy.setAutoCommit(writeCon, false);
            SimpleDBProvider dbProvider = new SimpleDBProvider(readCon, writeCon);

            ContextService contextService = Services.getService(ContextService.class, true);
            Context context = contextService.getContext(contextId);

            CalendarStorage storage = getStorage(contextId, dbProvider, context);
            EntityResolver entityResolver = calendarUtilities.getEntityResolver(contextId);
            Event event = loadEvent(eventId, storage);
            if (isNoop(event, userId)) {
                return;
            }
            backAfterRead = false;
            checkPreConditions(event, userId, context);

            int defFolder = OXFolderSQL.getUserDefaultFolder(userId, FolderObject.CALENDAR, FolderObject.PRIVATE, dbProvider.getReadConnection(context), context);
            
            if (CalendarUtils.isSeriesMaster(event)) {
                handleMaster(event.getId(), userId, storage, entityResolver, String.valueOf(defFolder));
            } else if (CalendarUtils.isSeriesException(event)) {
                handleMaster(event.getSeriesId(), userId, storage, entityResolver, String.valueOf(defFolder));
            } else {
                handleSingle(event, userId, storage, entityResolver, String.valueOf(defFolder));
            }
            txPolicy.commit(writeCon);
            txPolicy.setAutoCommit(writeCon, true);
        } catch (RemoteException re) {
            throw re;
        } catch (Exception e) {
            LOG.error("", e);
            String message = e.getMessage();
            throw new RemoteException(message, e);
        } finally {
            if (databaseService != null) {
                if (readCon != null) {
                    databaseService.backReadOnly(contextId, readCon);
                }
                if (writeCon != null) {
                    try {
                        if (!writeCon.getAutoCommit()) {
                            txPolicy.rollback(writeCon);
                            txPolicy.setAutoCommit(writeCon, true);
                        }
                    } catch (SQLException e) {
                        throw new RemoteException("SQL Error", e);
                    }
                    if (backAfterRead) {
                        databaseService.backWritableAfterReading(contextId, writeCon);
                    } else {
                        databaseService.backWritable(contextId, writeCon);
                    }
                }
            }
        }
    }

    /**
     * Handles the series master and all of it's exceptions.
     *
     * @param seriesId The master id
     * @param userId The user who should become organizer
     * @param storage The calendar storage
     * @param entityResolver The entity resolver
     * @throws OXException if an error occurs
     */
    private void handleMaster(String seriesId, int userId, CalendarStorage storage, EntityResolver entityResolver, String defFolder) throws OXException {
        List<Event> exceptions = storage.getEventStorage().loadExceptions(seriesId, null);
        for (Event exception : exceptions) {
            exception.setAttendees(storage.getAttendeeStorage().loadAttendees(exception.getId()));
            handleSingle(exception, userId, storage, entityResolver, defFolder);
        }

        Event master = storage.getEventStorage().loadEvent(seriesId, null);
        if (master != null) {
            master.setAttendees(storage.getAttendeeStorage().loadAttendees(master.getId()));
            handleSingle(master, userId, storage, entityResolver, defFolder);
        }
    }

    /**
     * Handles a single event (either a series master, an exception or just a plain single event).
     *
     * @param event The event to handle
     * @param userId The user who should become organizer
     * @param storage The calendar storage
     * @param entityResolver The entity resolver
     * @throws OXException if an error occurs
     */
    private void handleSingle(Event event, int userId, CalendarStorage storage, EntityResolver entityResolver, String defFolder) throws OXException {
        Attendee newOrganizerAttendee = modifyEventObject(event, userId, entityResolver, defFolder);
        storage.getEventStorage().updateEvent(event);
        if (newOrganizerAttendee != null) {
            storage.getAttendeeStorage().insertAttendees(event.getId(), Collections.singletonList(newOrganizerAttendee));
        }
    }

    /**
     * Modifies the given event by adding the new Organizer (also as attendee if missing).
     * 
     * @param event The event to modify
     * @param newOrganizer The user who should become organizer
     * @param entityResolver The entity resolver
     * @return An attendee if adding is necessary
     * @throws OXException if an error occurs
     */
    private Attendee modifyEventObject(Event event, int newOrganizer, EntityResolver entityResolver, String defFolder) throws OXException {
        Attendee newOrganizerAttendee = null;
        if (!CalendarUtils.isOrganizer(event, newOrganizer)) {
            event.setOrganizer(entityResolver.applyEntityData(new Organizer(), newOrganizer));
            LOG.info("Changed organizer for event {} to {}.", event.getId(), event.getOrganizer().toString());
        }
        if (event.getAttendees().stream().noneMatch(a -> a.getEntity() == newOrganizer)) {
            Attendee organizer = entityResolver.applyEntityData(new Attendee(), newOrganizer);
            organizer.setFolderId(defFolder);
            
            event.getAttendees().add(organizer);
            newOrganizerAttendee = organizer;
            LOG.info("Added organizer {} to attendees for event {}.", organizer.toString(), event.getId());
        }
        event.setTimestamp(System.currentTimeMillis());
        return newOrganizerAttendee;
    }

    /**
     * Checks if any changes need to be performed or if this is a no-op.
     *
     * @param event The event to check
     * @param newOrganizer The potential new organizer
     * @return true if this is a no-op, false otherwise
     */
    private boolean isNoop(Event event, int newOrganizer) {
        // Check if new organizer is different from the current one.
        if (!CalendarUtils.isOrganizer(event, newOrganizer)) {
            return false;
        }

        // Check if the new organizer is already an attendee.
        if (event.getAttendees().stream().noneMatch(a -> a.getEntity() == newOrganizer)) {
            return false;
        }

        // Fall through, nothing to do.
        return true;
    }

    /**
     * Checks if the current organizer is an internal user and if the new organizer is an existing internal user.
     *
     * @param event The event to handle
     * @param newOrganizer The id of the new organizer
     * @param context The context
     * @throws RemoteException if an error occurs
     * @throws OXException if an error occurs
     */
    private void checkPreConditions(Event event, int newOrganizer, Context context) throws RemoteException, OXException {
        if (CalendarUtils.hasExternalOrganizer(event)) {
            throw new RemoteException("Current organizer '" + event.getOrganizer().toString() + "' is external.");
        }

        try {
            Services.getService(UserService.class).getUser(newOrganizer, context);
        } catch (OXException e) {
            if (UserExceptionCode.USER_NOT_FOUND.equals(e)) {
                throw new RemoteException("Invalid user: " + newOrganizer, e);
            }
            throw e;
        }
    }

    private Event loadEvent(int eventId, CalendarStorage storage) throws OXException, RemoteException {
        Event event = storage.getEventStorage().loadEvent(Integer.toString(eventId), null);
        if (event == null) {
            throw new RemoteException("Invalid event id: " + eventId);
        }
        event.setAttendees(storage.getAttendeeStorage().loadAttendees(event.getId()));
        return event;
    }

    private CalendarStorage getStorage(int contextId, DBProvider dbProvider, Context context) throws OXException {
        EntityResolver entityResolver = calendarUtilities.getEntityResolver(contextId);
        return Services.getService(CalendarStorageFactory.class).create(context, Utils.ACCOUNT_ID, entityResolver, dbProvider, DBTransactionPolicy.NO_TRANSACTIONS);
    }

}
