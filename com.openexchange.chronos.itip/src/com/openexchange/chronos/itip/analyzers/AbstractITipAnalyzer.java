/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.chronos.itip.analyzers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TimeZone;
import com.openexchange.chronos.Attachment;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.itip.ITipAnalysis;
import com.openexchange.chronos.itip.ITipAnalyzer;
import com.openexchange.chronos.itip.ITipChange;
import com.openexchange.chronos.itip.ITipChange.Type;
import com.openexchange.chronos.itip.ITipIntegrationUtility;
import com.openexchange.chronos.itip.ITipMessage;
import com.openexchange.chronos.itip.ITipMethod;
import com.openexchange.chronos.itip.Messages;
import com.openexchange.chronos.itip.generators.ArgumentType;
import com.openexchange.chronos.itip.generators.HTMLWrapper;
import com.openexchange.chronos.itip.generators.PassthroughWrapper;
import com.openexchange.chronos.itip.generators.Sentence;
import com.openexchange.chronos.itip.generators.TypeWrapper;
import com.openexchange.chronos.itip.osgi.Services;
import com.openexchange.chronos.itip.tools.ITipEventUpdate;
import com.openexchange.chronos.scheduling.changes.Description;
import com.openexchange.chronos.scheduling.changes.DescriptionService;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.EventConflict;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.tools.mappings.common.AbstractSimpleCollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.CollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.ItemUpdate;
import com.openexchange.regional.RegionalSettings;
import com.openexchange.regional.RegionalSettingsService;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link AbstractITipAnalyzer}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public abstract class AbstractITipAnalyzer implements ITipAnalyzer {

    public static final EventField[] SKIP = new EventField[] { EventField.FOLDER_ID, EventField.ID, EventField.SERIES_ID, EventField.CREATED_BY, EventField.CREATED, EventField.TIMESTAMP, EventField.LAST_MODIFIED, EventField.MODIFIED_BY, EventField.SEQUENCE,
        EventField.ALARMS, EventField.FLAGS, EventField.ATTENDEE_PRIVILEGES };
    
    protected ITipIntegrationUtility util;

    @Override
    public ITipAnalysis analyze(final ITipMessage message, Map<String, String> header, final String style, final CalendarSession session) throws OXException {

        final ContextService contexts = Services.getService(ContextService.class);
        final UserService users = Services.getService(UserService.class);

        final Context ctx = contexts.getContext(session.getContextId());
        final User user = users.getUser(session.getUserId(), ctx);

        if (null == header) {
            return analyze(message, new HashMap<String, String>(), wrapperFor(style), user.getLocale(), user, ctx, session);
        }
        return analyze(message, lowercase(header), wrapperFor(style), user.getLocale(), user, ctx, session);
    }

    private Map<String, String> lowercase(final Map<String, String> header) {
        final Map<String, String> copy = new HashMap<String, String>();
        for (final Map.Entry<String, String> entry : header.entrySet()) {
            copy.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        return copy;
    }

    protected abstract ITipAnalysis analyze(ITipMessage message, Map<String, String> header, TypeWrapper wrapper, Locale locale, User user, Context ctx, CalendarSession session) throws OXException;

    public AbstractITipAnalyzer(final ITipIntegrationUtility util) {
        this.util = util;
    }

    protected TypeWrapper wrapperFor(final String style) {
        TypeWrapper w = new PassthroughWrapper();
        if (style != null && style.equalsIgnoreCase("html")) {
            w = new HTMLWrapper();
        }
        return w;
    }

    public void describeDiff(final ITipChange change, final TypeWrapper wrapper, final CalendarSession session, ITipMessage message) throws OXException {
        final ContextService contexts = Services.getService(ContextService.class);
        final UserService users = Services.getService(UserService.class);
        RegionalSettingsService regionalSettingsService = Services.getService(RegionalSettingsService.class);

        final Context ctx = contexts.getContext(session.getContextId());
        final User user = users.getUser(session.getUserId(), ctx);
        RegionalSettings regionalSettings = regionalSettingsService.get(session.getContextId(), session.getUserId());

        switch (change.getType()) {
            case CREATE:
                createIntro(change, wrapper, user.getLocale());
                break;
            case UPDATE:
                updateIntro(change, wrapper, user.getLocale(), message);
                break;
            case CREATE_DELETE_EXCEPTION:
            case DELETE:
                deleteIntro(change, wrapper, user.getLocale());
                break;
            default:
                // Nothing to do
                break;
        }

        final Event currentEvent = change.getCurrentEvent();
        final Event newEvent = change.getNewEvent();

        if (currentEvent == null || newEvent == null) {
            change.setDiffDescription(new ArrayList<String>());
            return;
        }
        
        final List<String> descriptions = new LinkedList<String>();
        DescriptionService descriptionService = Services.getOptionalService(DescriptionService.class);
        if (null != descriptionService) {
            List<Description> descs;
            if (ITipMethod.REPLY.equals(message.getMethod())) {
                descs = descriptionService.describeOnly(change.getDiff(), EventField.ATTENDEES);
            } else {
                descs = descriptionService.describe(change.getDiff());
            }
            for (Description desc : descs) {
                for (com.openexchange.chronos.scheduling.changes.Sentence sentence : desc.getSentences()) {
                    descriptions.add(sentence.getMessage(wrapper.getFormat(), user.getLocale(), TimeZone.getTimeZone(user.getTimeZone()), regionalSettings));
                }
            }
        }

        change.setDiffDescription(descriptions);

        // Now let's choose an introduction sentence
        switch (change.getType()) {
            case CREATE:
                if (!change.isException()) {
                    createIntro(change, wrapper, user.getLocale());
                    break;
                } // $FALL-THROUGH$ Else Fall Through, creating change exceptions is more similar to updates
            case UPDATE:
                updateIntro(change, wrapper, user.getLocale(), message);
                break;
            case CREATE_DELETE_EXCEPTION:
            case DELETE:
                deleteIntro(change, wrapper, user.getLocale());
                break;
            default:
                // Nothing to do
                break;
        }
    }

    private void deleteIntro(final ITipChange change, final TypeWrapper wrapper, final Locale locale) {
        final String displayName = displayNameForOrganizer(change.getDeletedEvent());
        change.setIntroduction(new Sentence(Messages.DELETE_INTRO).add(displayName, ArgumentType.PARTICIPANT).getMessage(wrapper, locale));

    }

    private void updateIntro(final ITipChange change, final TypeWrapper wrapper, final Locale locale, ITipMessage message) throws OXException {
        String displayName = displayNameForOrganizer(change.getCurrentEvent());
        if (onlyStateChanged(change.getDiff())) {
            // External Participant

            ParticipationStatus newStatus = null;

            outer: for (ItemUpdate<Attendee, AttendeeField> attendeeUpdate : change.getDiff().getAttendeeUpdates().getUpdatedItems()) {
                for (AttendeeField attendeeField : attendeeUpdate.getUpdatedFields()) {
                    if (attendeeField.equals(AttendeeField.PARTSTAT)) {
                        newStatus = attendeeUpdate.getUpdate().getPartStat();
                        displayName = attendeeUpdate.getOriginal().getCn() == null ? attendeeUpdate.getOriginal().getEMail() : attendeeUpdate.getOriginal().getCn();
                        break outer;
                    }
                }
            }

            if (ParticipationStatus.ACCEPTED.equals(newStatus) || ParticipationStatus.TENTATIVE.equals(newStatus) || ParticipationStatus.DECLINED.equals(newStatus)) {
                change.setIntroduction(new Sentence(Messages.STATUS_CHANGED_INTRO).add(displayName, ArgumentType.PARTICIPANT).add("", ArgumentType.STATUS, newStatus).getMessage(wrapper, locale));
            }
        } else {
            if (message.getMethod() != ITipMethod.COUNTER) {
                change.setIntroduction(new Sentence(Messages.UPDATE_INTRO).add(displayName, ArgumentType.PARTICIPANT).getMessage(wrapper, locale));
            }
        }
    }

    AttendeeField[] ALL_BUT_CONFIRMATION = new AttendeeField[] { AttendeeField.CN, AttendeeField.CU_TYPE, AttendeeField.EMAIL, AttendeeField.ENTITY, AttendeeField.FOLDER_ID, AttendeeField.MEMBER, AttendeeField.ROLE, AttendeeField.RSVP, AttendeeField.SENT_BY, AttendeeField.URI };

    private boolean onlyStateChanged(ITipEventUpdate diff) {
        if (null == diff) {
            return false;
        }
        if (diff.containsAnyChangesBeside(new EventField[] { EventField.ATTENDEES })) {
            return false;
        }

        CollectionUpdate<Attendee, AttendeeField> attendeeUpdates = diff.getAttendeeUpdates();

        if (attendeeUpdates == null || attendeeUpdates.isEmpty()) {
            return true;
        }

        if (attendeeUpdates.getAddedItems() != null && !attendeeUpdates.getAddedItems().isEmpty()) {
            return false;
        }

        if (attendeeUpdates.getRemovedItems() != null && !attendeeUpdates.getRemovedItems().isEmpty()) {
            return false;
        }

        List<? extends ItemUpdate<Attendee, AttendeeField>> updatedItems = attendeeUpdates.getUpdatedItems();
        for (ItemUpdate<Attendee, AttendeeField> attendeeUpdate : updatedItems) {
            if (attendeeUpdate.containsAnyChangeOf(ALL_BUT_CONFIRMATION)) {
                return false;
            }
        }

        return true;
    }

    private void createIntro(final ITipChange change, final TypeWrapper wrapper, final Locale locale) {
        final String displayName = displayNameForOrganizer(change.getNewEvent());
        change.setIntroduction(new Sentence(Messages.CREATE_INTRO).add(displayName, ArgumentType.PARTICIPANT).getMessage(wrapper, locale));
    }

    protected String displayNameForOrganizer(Event event) {
        Organizer organizer;
        if (null == event || (organizer = event.getOrganizer()) == null) {
            return "unknown";
        }

        if (organizer.getCn() != null) {
            return organizer.getCn();
        }

        if (organizer.getEMail() != null) {
            return organizer.getEMail();
        }

        return "unknown";
    }

    protected Event findAndRemoveMatchingException(final Event exception, final List<Event> exceptions) {
        for (Iterator<Event> iterator = exceptions.iterator(); iterator.hasNext();) {
            Event existingException = iterator.next();
            if (existingException.getRecurrenceId().compareTo(exception.getRecurrenceId()) == 0) {
                iterator.remove();
                return existingException;
            }
        }
        return null;
    }

    public boolean doAppointmentsDiffer(final Event update, final Event original, final CalendarSession session) {
        if (original == update) {
            // Can be the same object .. so avoid roundtrip of diff
            return false;
        }
        final ITipEventUpdate diff = new ITipEventUpdate(original, update, true, AbstractITipAnalyzer.SKIP);
        if (diff.getUpdatedFields().isEmpty()) {
            return false;
        }
        /*
         * Check if they do only differ because of the participant status of the user, that has already been updated
         */
        if (diff.isAboutCertainParticipantsStateChangeOnly(String.valueOf(session.getUserId())) && original.getTimestamp() > update.getTimestamp()) {
            return false;
        }

        return true;
    }

    public boolean hasConflicts(final ITipAnalysis analysis) {
        for (final ITipChange change : analysis.getChanges()) {
            if (change.getConflicts() != null && !change.getConflicts().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void purgeConflicts(final ITipAnalysis analysis) throws OXException {
        final Map<String, ITipChange> knownEvents = new HashMap<>();
        for (final ITipChange change : analysis.getChanges()) {
            final Event currentAppointment = change.getCurrentEvent();
            if (currentAppointment != null) {
                knownEvents.put(currentAppointment.getId(), change);
            }
            final Event deletedAppointment = change.getDeletedEvent();
            if (deletedAppointment != null) {
                knownEvents.put(deletedAppointment.getId(), change);
            }
        }

        for (final ITipChange change : analysis.getChanges()) {
            final List<EventConflict> conflicts = change.getConflicts();
            if (conflicts == null) {
                continue;
            }
            final Event newEvent = change.getNewEvent();
            if (newEvent == null) {
                continue;
            }
            final Event currentEvent = change.getCurrentEvent();
            final Event masterEvent = change.getMasterEvent();
            for (final Iterator<EventConflict> iterator = conflicts.iterator(); iterator.hasNext();) {
                final EventConflict conflict = iterator.next();
                if (null != conflict.getConflictingEvent()) {
                    continue;
                }
                if (currentEvent != null && currentEvent.getId() != null && (currentEvent.getId().equals(conflict.getConflictingEvent().getId()))) {
                    iterator.remove();
                    continue;
                }
                if (masterEvent != null && masterEvent.getId() != null && (masterEvent.getId().equals(conflict.getConflictingEvent().getId()))) {
                    iterator.remove();
                    continue;
                }
                final ITipChange changeToConflict = knownEvents.get(conflict.getConflictingEvent().getId());
                if (changeToConflict == null) {
                    continue;
                }
                if (changeToConflict.getType() == ITipChange.Type.DELETE) {
                    iterator.remove();
                } else {
                    final Event changedAppointment = changeToConflict.getNewEvent();
                    if (changedAppointment == null) {
                        continue;
                    }
                    if (!overlaps(changedAppointment, newEvent)) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    public boolean overlaps(final Event event1, final Event event2) {
        if (event2.getStartDate().after(event1.getEndDate())) {
            return false;
        }

        if (event1.getStartDate().after(event2.getEndDate())) {
            return false;
        }

        return true;
    }

    public boolean isCreate(final ITipAnalysis analysis) {
        for (final ITipChange change : analysis.getChanges()) {
            if (change.getType() == Type.CREATE) {
                return true;
            }
        }
        return false;
    }

    public boolean rescheduling(final ITipAnalysis analysis) throws OXException {
        for (final ITipChange change : analysis.getChanges()) {
            if (change.getType() == Type.CREATE && change.isException()) {
                final ITipEventUpdate diff = new ITipEventUpdate(change.getCurrentEvent(), change.getNewEvent(), true, (EventField[]) null);
                if (diff.containsAnyChangeOf(new EventField[] { EventField.START_DATE, EventField.END_DATE })) {
                    return true;
                }
                return false;
            }
            if (change.getType() != Type.UPDATE) {
                return true;
            }
            final ITipEventUpdate diff = change.getDiff();
            if (diff != null && diff.containsAnyChangeOf(new EventField[] { EventField.START_DATE, EventField.END_DATE })) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to restore (obviously) unchanged attachments in an incoming updated event compared to its stored representation. Matching
     * is performed based on attachment metadata (filesize, format-type, filename).
     * 
     * @param original The original event
     * @param update The updated event
     * @return The updated event, possibly with restored attachments
     */
    protected static Event restoreAttachments(Event original, Event update) {
        if (null == original) {
            /*
             * New event, skip processing
             */
            return update;
        }
        AbstractSimpleCollectionUpdate<Attachment> attachmentUpdates = new AbstractSimpleCollectionUpdate<Attachment>(original.getAttachments(), update.getAttachments()) {

            @Override
            protected boolean matches(Attachment item1, Attachment item2) {
                /*
                 * match via managed id, URI or checksum
                 */
                if (0 < item1.getManagedId() && 0 < item2.getManagedId()) {
                    return item1.getManagedId() == item2.getManagedId();
                }
                if (null != item1.getUri() && null != item2.getUri()) {
                    return item1.getUri().equals(item2.getUri());
                }
                if (null != item1.getChecksum() && null != item2.getChecksum()) {
                    return item1.getChecksum().equals(item2.getChecksum());
                }
                /*
                 * match via metadata
                 */
                if (Objects.equals(item1.getFilename(), item2.getFilename()) && item1.getSize() == item2.getSize() && 
                    Objects.equals(item1.getFormatType(), item2.getFormatType())) {
                    return true;
                }
                return false;
            }
        };
        List<Attachment> newAttachments = new ArrayList<Attachment>();
        if (null != original.getAttachments()) {
            newAttachments.addAll(original.getAttachments());
        }
        newAttachments.removeAll(attachmentUpdates.getRemovedItems());
        newAttachments.addAll(attachmentUpdates.getAddedItems());
        update.setAttachments(newAttachments);
        return update;
    }

    protected void ensureParticipant(final Event original, final Event event, final CalendarSession session, int owner) throws OXException {
        if (null == CalendarUtils.find(event.getAttendees(), session.getEntityResolver().prepareUserAttendee(owner))) {
            // Owner is a party crasher..
            Attendee attendee = new Attendee();
            attendee.setEntity(owner);
            attendee.setPartStat(ParticipationStatus.NEEDS_ACTION);
            attendee.setCuType(CalendarUserType.INDIVIDUAL);

            List<Attendee> attendees;
            if (null != original && original.containsAttendees() && null != original.getAttendees()) {
                attendees = new LinkedList<>(original.getAttendees());
            } else if (event.containsAttendees() && null != event.getAttendees()) {
                attendees = new LinkedList<>(event.getAttendees());
            } else {
                attendees = new LinkedList<>();
            }
            attendees.add(attendee);
            event.setAttendees(attendees);
        }
    }

    /**
     * Checks if a exception was already deleted
     *
     * @param original The original or rather the master event
     * @param exception The exception to check
     * @return <code>true</code> if the given exception was already deleted
     */
    protected boolean isDeleteException(Event original, Event exception) {
        if (null != original && original.containsDeleteExceptionDates()) {
            SortedSet<RecurrenceId> deleteExceptionDates = original.getDeleteExceptionDates();
            if (null != deleteExceptionDates) {
                return deleteExceptionDates.stream().anyMatch(r -> 0 == r.compareTo(exception.getRecurrenceId()));
            }
        }
        return false;
    }
}
