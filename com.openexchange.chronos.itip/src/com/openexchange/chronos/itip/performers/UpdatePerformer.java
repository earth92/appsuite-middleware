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

package com.openexchange.chronos.itip.performers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.SchedulingControl;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.mapping.AttendeeMapper;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.itip.ITipAction;
import com.openexchange.chronos.itip.ITipAnalysis;
import com.openexchange.chronos.itip.ITipAttributes;
import com.openexchange.chronos.itip.ITipChange;
import com.openexchange.chronos.itip.ITipIntegrationUtility;
import com.openexchange.chronos.itip.generators.ITipMailGeneratorFactory;
import com.openexchange.chronos.itip.sender.MailSenderService;
import com.openexchange.chronos.itip.tools.ITipEventUpdate;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.service.CalendarResult;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.EventID;
import com.openexchange.chronos.service.EventUpdate;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.type.PublicType;
import com.openexchange.groupware.tools.mappings.Mapping;
import com.openexchange.java.Strings;

/**
 *
 * {@link ITipChange}
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.com">Martin Herfurth</a>
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.0
 */
public class UpdatePerformer extends AbstractActionPerformer {

    private final static Logger LOGGER = LoggerFactory.getLogger(UpdatePerformer.class);

    public UpdatePerformer(ITipIntegrationUtility util, MailSenderService sender, ITipMailGeneratorFactory generators) {
        super(util, sender, generators);
    }

    @Override
    public Collection<ITipAction> getSupportedActions() {
        return EnumSet.of(ITipAction.ACCEPT, ITipAction.ACCEPT_AND_IGNORE_CONFLICTS, ITipAction.ACCEPT_PARTY_CRASHER, ITipAction.ACCEPT_AND_REPLACE, ITipAction.DECLINE, ITipAction.TENTATIVE, ITipAction.UPDATE, ITipAction.CREATE, ITipAction.COUNTER);
    }

    @Override
    public List<Event> perform(AJAXRequestData request, ITipAction action, ITipAnalysis analysis, CalendarSession session, ITipAttributes attributes) throws OXException {
        session.set(CalendarParameters.PARAMETER_SCHEDULING, SchedulingControl.NONE);
        List<ITipChange> changes = analysis.getChanges();
        List<Event> result = new ArrayList<Event>(changes.size());

        Map<String, Event> processed = new HashMap<String, Event>();

        NextChange: for (ITipChange change : changes) {

            Event event = change.getNewEvent();
            if (event == null) {
                LOGGER.debug("No event found to process.");
                continue NextChange;
            }

            // TODO: event.setNotification(true);
            final int owner = analysis.getMessage().getOwner() > 0 ? analysis.getMessage().getOwner() : session.getUserId();
            boolean exceptionCreate = isExceptionCreate(change);

            if (Strings.isEmpty(event.getFolderId()) || !PublicType.getInstance().equals(util.getFolderType(event, session))) {
                ensureAttendee(event, exceptionCreate ? change.getMasterEvent() : change.getCurrentEvent(), action, owner, attributes, session);
            }
            Event original = determineOriginalEvent(change, processed, session);
            Event updatedEvent;

            if (original != null) {
                ITipEventUpdate diff = change.getDiff();
                if (null != diff && false == diff.isEmpty()) {
                    adjusteAttendeesPartStats(action, original, event, diff, owner);
                    updatedEvent = updateEvent(original, event, session);
                } else {
                    continue NextChange;
                }
            } else if (exceptionCreate) {
                Event masterEvent = original = change.getMasterEvent();
                event.setSeriesId(masterEvent.getSeriesId());
                updatedEvent = updateEvent(masterEvent, event, session);
            } else {
                ensureFolderId(event, session);
                event.removeId();
                updatedEvent = createEvent(event, session);
            }

            // Check before continuing
            if (null == updatedEvent) {
                LOGGER.warn("No event found to process.");
                continue NextChange;
            }

            if (!change.isException()) {
                processed.put(updatedEvent.getUid(), updatedEvent);
            }

            writeMail(request, action, original, updatedEvent, session, owner);
            result.add(updatedEvent);
        }

        return result;
    }

    private Event updateEvent(Event original, Event event, CalendarSession session) throws OXException {
        EventUpdate diff = session.getUtilities().compare(original, event, true, (EventField[]) null);

        Event update = new Event();
        if (!diff.getUpdatedFields().isEmpty()) {
            EventMapper.getInstance().copy(diff.getUpdate(), update, diff.getUpdatedFields().toArray(new EventField[diff.getUpdatedFields().size()]));
            update.setFolderId(original.getFolderId());
            update.setId(original.getId());

            if (!update.containsSequence()) {
                update.setSequence(original.getSequence());
            }
            if (!update.containsUid()) {
                update.setUid(original.getUid());
            }
            if (!update.containsOrganizer()) {
                update.setOrganizer(original.getOrganizer());
            }
            if (original.containsSeriesId()) {
                update.setSeriesId(original.getSeriesId());
            }
            if (!original.containsRecurrenceId() && event.containsRecurrenceId()) {
                update.setRecurrenceId(event.getRecurrenceId());
            } else if (original.containsRecurrenceId()) {
                update.setRecurrenceId(original.getRecurrenceId());
            }

            CalendarResult calendarResult = session.getCalendarService().updateEventAsOrganizer(session, new EventID(update.getFolderId(), update.getId()), update, original.getLastModified().getTime());
            /*
             * Check creations first because;
             * + Party crasher creates event in own folder
             * + To create exceptions master needs to be updated. Nevertheless the created exception must be returned
             */
            if (false == calendarResult.getCreations().isEmpty()) {
                return calendarResult.getCreations().get(0).getCreatedEvent();
            }
            if (false == calendarResult.getUpdates().isEmpty()) {
                return calendarResult.getUpdates().get(0).getUpdate();
            }
            LOGGER.debug("Did write data but unable to find correct element to return.");
            return null;
        }

        LOGGER.debug("Did not write any data.");
        return null;
    }

    /**
     * Creates a new event based on the given event.
     *
     * @param event The event to create
     * @param session The {@link CalendarSession}
     * @return The newly created event
     * @throws OXException In case event can't be created
     */
    private Event createEvent(Event event, CalendarSession session) throws OXException {
        CalendarResult createResult = session.getCalendarService().createEvent(session, event.getFolderId(), event);
        return createResult.getCreations().get(0).getCreatedEvent();
    }

    /*
     * ==============================================================================
     * =============================== HELPERS ======================================
     * ==============================================================================
     */

    /**
     * Ensures that the given user is attendee of the event
     *
     * @param event The event to check if the user is in
     * @param currentEvent The original event
     * @param action The {@link ITipAction} to be performed
     * @param owner The user identifier
     * @param attributes The update {@link ITipAttributes}
     * @param session The {@link CalendarSession}
     */
    private void ensureAttendee(Event event, Event currentEvent, ITipAction action, int owner, ITipAttributes attributes, CalendarSession session) {
        ParticipationStatus confirm = getParticipantStatus(currentEvent, action, owner);
        String message = null;
        if (attributes != null && attributes.getConfirmationMessage() != null && Strings.isNotEmpty(attributes.getConfirmationMessage().trim())) {
            message = attributes.getConfirmationMessage();
        }

        try {
            // Trust analyze to provide accurate set of attendees and their status
            List<Attendee> attendees = new LinkedList<>(event.getAttendees());

            // Get attendee to add
            Attendee attendee = CalendarUtils.find(attendees, session.getEntityResolver().prepareUserAttendee(owner));
            if (null == attendee) {
                attendee = loadAttendee(session, owner);
            } else {
                attendees.remove(attendee);
            }

            // Update from attributes
            if (null != confirm) {
                attendee.setPartStat(confirm);
                attendee.setRsvp(Boolean.FALSE);
            }
            if (Strings.isNotEmpty(message)) {
                attendee.setComment(message);
            }
            attendees.add(attendee);
            event.setAttendees(attendees);
        } catch (OXException e) {
            LOGGER.error("Could not resolve user with identifier {}", Integer.valueOf(owner), e);
        }
    }

    private ParticipationStatus getParticipantStatus(Event currentEvent, ITipAction action, int owner) {
        switch (action) {
            case ACCEPT:
            case ACCEPT_AND_IGNORE_CONFLICTS:
            case CREATE:
                return ParticipationStatus.ACCEPTED;
            case DECLINE:
                return ParticipationStatus.DECLINED;
            case TENTATIVE:
                return ParticipationStatus.TENTATIVE;
            case UPDATE:
                // Might return null
                return getFieldValue(currentEvent, owner, AttendeeField.PARTSTAT, ParticipationStatus.class);
            default:
                // Fall through
        }
        return null;
    }

    /**
     * Loads a specific user
     *
     * @param session The {@link CalendarSession}
     * @param userId The user to load
     * @return The user as {@link Attendee}
     * @throws OXException If the user can't be found
     */
    private Attendee loadAttendee(CalendarSession session, int userId) throws OXException {
        return session.getEntityResolver().prepareUserAttendee(userId);
    }

    /**
     * Get a specific value from a specific attendee
     *
     * @param event The event containing the attendees
     * @param userId The identifier of the attendee
     * @param field The {@link AttendeeField} to get the value from
     * @param clazz The class to cast the value to
     * @return The value of the field or the default value if the field is <code>null</code>, an error occurs or the attendee is not set
     */
    private <T> T getFieldValue(Event event, int userId, AttendeeField field, Class<T> clazz) {
        try {
            if (containsAttendees(event)) {
                Attendee attendee = CalendarUtils.find(event.getAttendees(), userId);
                if (null != attendee) {
                    Mapping<? extends Object, Attendee> mapping = AttendeeMapper.getInstance().get(field);
                    return clazz.cast(mapping.get(attendee));
                }
            }
        } catch (OXException | ClassCastException e) {
            // Fall through
            LOGGER.debug("Could not get value for field {} of attendee with id {}", field, Integer.valueOf(userId), e);
        }
        return null;
    }

    private boolean containsAttendees(Event event) {
        return event != null && event.getAttendees() != null && false == event.getAttendees().isEmpty();
    }

    private boolean isExceptionCreate(ITipChange change) {
        return change.isException() && CalendarUtils.isSeriesMaster(change.getMasterEvent()) && null == change.getNewEvent().getId();
    }

    private final static Collection<ITipAction> OWN_CHANGE = EnumSet.of(ITipAction.ACCEPT, ITipAction.ACCEPT_AND_IGNORE_CONFLICTS, ITipAction.ACCEPT_AND_REPLACE, ITipAction.DECLINE, ITipAction.TENTATIVE);

    /**
     * Adjusted attendees status to avoid over right.
     * Only call for updates on existing events
     * 
     * @param action The {@link ITipAction}
     * @param original The original {@link Event}
     * @param event The updated {@link Event}
     * @param owner The acting user
     */
    private void adjusteAttendeesPartStats(ITipAction action, Event original, Event event, ITipEventUpdate diff, int owner) {
        if (OWN_CHANGE.contains(action) && diff.isAboutStateChangesOnly() && false == diff.isAboutCertainParticipantsStateChangeOnly(String.valueOf(owner))) {
            // Changed more than one PartStat with a action that should only update the current users status?!
            List<Attendee> attendees = event.getAttendees();
            for (Attendee o : original.getAttendees()) {
                if (CalendarUtils.isInternal(o) && false == ParticipationStatus.NEEDS_ACTION.equals(o.getPartStat())) {
                    Attendee find = CalendarUtils.find(attendees, o.getEntity());
                    if (null != find && ParticipationStatus.NEEDS_ACTION.equals(find.getPartStat())) {
                        // Copy from DB event
                        find.setPartStat(o.getPartStat());
                        if (o.containsComment()) {
                            find.setComment(o.getComment());
                        }
                    }
                }
            }
        }
    }
}
