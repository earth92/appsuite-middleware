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

package com.openexchange.chronos.itip.analyzers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.itip.ITipAction;
import com.openexchange.chronos.itip.ITipAnalysis;
import com.openexchange.chronos.itip.ITipAnnotation;
import com.openexchange.chronos.itip.ITipChange;
import com.openexchange.chronos.itip.ITipChange.Type;
import com.openexchange.chronos.itip.ITipIntegrationUtility;
import com.openexchange.chronos.itip.ITipMessage;
import com.openexchange.chronos.itip.ITipMethod;
import com.openexchange.chronos.itip.ITipSpecialHandling;
import com.openexchange.chronos.itip.LegacyAnalyzing;
import com.openexchange.chronos.itip.Messages;
import com.openexchange.chronos.itip.generators.TypeWrapper;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.userconfiguration.UserConfigurationStorage;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.user.User;

/**
 * {@link UpdateITipAnalyzer}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class UpdateITipAnalyzer extends AbstractITipAnalyzer implements LegacyAnalyzing {

    private final static Logger LOGGER = LoggerFactory.getLogger(UpdateITipAnalyzer.class);

    public UpdateITipAnalyzer(final ITipIntegrationUtility util) {
        super(util);
    }

    @Override
    public List<ITipMethod> getMethods() {
        return Arrays.asList(ITipMethod.REQUEST, ITipMethod.COUNTER, ITipMethod.PUBLISH);
    }

    @Override
    public ITipAnalysis analyze(final ITipMessage message, final Map<String, String> header, final TypeWrapper wrapper, final Locale locale, final User user, final Context ctx, final CalendarSession session) throws OXException {
        final ITipAnalysis analysis = new ITipAnalysis();
        analysis.setMessage(message);

        ITipChange change = new ITipChange();

        Event update = message.getEvent();
        String uid = null;
        if (update != null) {
            uid = update.getUid();
        } else if (message.exceptions().iterator().hasNext()) {
            uid = message.exceptions().iterator().next().getUid();
        }
        Event original;
        try {
            original = util.resolveUid(uid, session);
        } catch (OXException e) {
            if (CalendarExceptionCodes.UID_CONFLICT.equals(e)) {
                /*
                 * UID resolved to multiple events, assume foreign copy exists & handle as 'old update'
                 */
                analysis.addAnnotation(new ITipAnnotation(Messages.OLD_UPDATE, locale));
                analysis.recommendAction(ITipAction.IGNORE);
                change.setType(ITipChange.Type.UPDATE);
                analysis.addChange(change);
                return analysis;
            }
            throw e;
        }

        if (null == update) {
            if (null == original) {
                if (message.numberOfExceptions() > 0) {
                    /*
                     * Add to single orphaned series exception
                     */
                    analysis.setUid(uid);
                } else {
                    throw new OXException(new IllegalArgumentException("No appointment instance given"));
                }
            } else {
                update = original;
                analysis.setUid(original.getUid());
            }
        } else {
            analysis.setUid(update.getUid());
        }

        List<Event> exceptions = Collections.emptyList();
        boolean differ = true;

        if (original != null) {
            // TODO: Needs to be removed, when we handle external resources.
            addResourcesToUpdate(original, update);
            update = adjustOrganizer(message, original, update);
            if (isOutdated(update, original) || isForeignCopy(session, original)) {
                analysis.addAnnotation(new ITipAnnotation(Messages.OLD_UPDATE, locale));
                analysis.recommendAction(ITipAction.IGNORE);
                change.setCurrentEvent(original);
                change.setType(ITipChange.Type.UPDATE);
                analysis.addChange(change);
                return analysis;
            }
            if (isOrganizerChange(original, update)) {
                analysis.addAnnotation(new ITipAnnotation(Messages.UNALLOWED_ORGANIZER_CHANGE, locale));
                analysis.recommendAction(ITipAction.IGNORE);
                change.setType(ITipChange.Type.UPDATE);
                analysis.addChange(change);
                return analysis;
            }
            change.setType(ITipChange.Type.UPDATE);
            change.setCurrentEvent(original);
            differ = doAppointmentsDiffer(update, original, session);
            exceptions = new ArrayList<Event>(util.getExceptions(original, session));
        } else {
            if (message.getMethod() == ITipMethod.COUNTER) {
                analysis.addAnnotation(new ITipAnnotation(Messages.COUNTER_UNKNOWN_APPOINTMENT, locale));
                analysis.recommendAction(ITipAction.IGNORE);
                return analysis;
            }
            change.setType(ITipChange.Type.CREATE);
        }
        int owner = session.getUserId();
        if (message.getOwner() > 0 && message.getOwner() != session.getUserId()) {
            owner = message.getOwner();

            OXFolderAccess oxfs = new OXFolderAccess(ctx);
            FolderObject defaultFolder = oxfs.getDefaultFolder(owner, FolderObject.CALENDAR);
            EffectivePermission permission = oxfs.getFolderPermission(defaultFolder.getObjectID(), session.getUserId(), UserConfigurationStorage.getInstance().getUserConfigurationSafe(session.getUserId(), ctx));
            if (permission.canCreateObjects() && original != null) {
                original.setFolderId(Integer.toString(defaultFolder.getObjectID()));
            } else {
                analysis.addAnnotation(new ITipAnnotation(Messages.SHARED_FOLDER, locale));
                return analysis;
            }
        }

        if (differ && message.getEvent() != null) {
            Event event = session.getUtilities().copyEvent(message.getEvent(), (EventField[]) null);
            event = handleMicrosoft(message, analysis, original, event);
            event = restoreAttachments(original, event);
            ensureParticipant(original, event, session, owner);
            if (original != null) {
                event.setFolderId(original.getFolderId());
            }
            session.getUtilities().adjustTimeZones(session.getSession(), owner, event, original);
            change.setNewEvent(event);
            change.setConflicts(util.getConflicts(message.getEvent(), session));
            describeDiff(change, wrapper, session, message);
            analysis.addChange(change);
        }

        Event master = null;
        if (null != original && original.containsSeriesId()) {
            master = new Event();
            master.setId(original.getSeriesId());
            master = util.loadEvent(master, session);
        }

        for (Event exception : message.exceptions()) {
            exception = session.getUtilities().copyEvent(exception, (EventField[]) null);

            final Event matchingException = findAndRemoveMatchingException(exception, exceptions);
            change = new ITipChange();
            change.setException(true);
            change.setMaster(master);
            exception = handleMicrosoft(message, analysis, matchingException, exception);

            differ = true;
            if (matchingException != null) {
                exception.setSeriesId(matchingException.getSeriesId());
                session.getUtilities().adjustTimeZones(session.getSession(), owner, exception, matchingException);
                change.setType(ITipChange.Type.UPDATE);
                change.setCurrentEvent(matchingException);
                ensureParticipant(matchingException, exception, session, owner);
                differ = doAppointmentsDiffer(exception, matchingException, session);
            } else {
                if (null != master) {
                    exception.setSeriesId(master.getSeriesId());
                }
                if (isDeleteException(original, exception)) {
                    analysis.addAnnotation(new ITipAnnotation(Messages.CHANGE_PARTICIPANT_STATE_IN_DELETED_APPOINTMENT, locale));
                    analysis.recommendAction(ITipAction.IGNORE);
                    return analysis;
                }
                // Exception is not yet created
                session.getUtilities().adjustTimeZones(session.getSession(), owner, exception, master);
                exception.removeUid();
                ensureParticipant(original, exception, session, owner);
                change.setType(ITipChange.Type.CREATE);
            }
            if (master == null) {
                change.setNewEvent(exception);
                if (CalendarUtils.hasExternalOrganizer(exception)) {
                    Event savedEventException = util.resolveUid(uid, exception.getRecurrenceId(), session);
                    if (null != savedEventException) {
                        /*
                         * Update of orphaned exception
                         */
                        exception.setSeriesId(savedEventException.getSeriesId());
                        session.getUtilities().adjustTimeZones(session.getSession(), owner, exception, savedEventException);
                        change.setType(ITipChange.Type.UPDATE);
                        change.setCurrentEvent(savedEventException);
                        ensureParticipant(savedEventException, exception, session, owner);
                        differ = doAppointmentsDiffer(exception, savedEventException, session);
                    } else {
                        /*
                         * Orphaned exception is not yet created
                         */
                        session.getUtilities().adjustTimeZones(session.getSession(), owner, exception, null);
                        ensureParticipant(null, exception, session, owner);
                        change.setType(ITipChange.Type.CREATE);
                    }
                } else {
                    // FIXME: Choose better message once we can introduce new sentences again.
                    analysis.addAnnotation(new ITipAnnotation(Messages.COUNTER_UNKNOWN_APPOINTMENT, locale));
                    break;
                }
            } else if (differ) {
                if (original != null) {
                    exception.setFolderId(original.getFolderId());
                }
                change.setNewEvent(exception);
                change.setConflicts(util.getConflicts(exception, session));

                describeDiff(change, wrapper, session, message);
                analysis.addChange(change);
            }
        }

        // Purge conflicts of irrelevant conflicts
        purgeConflicts(analysis);
        if (updateOrNew(analysis)) {
            if (message.getMethod() == ITipMethod.COUNTER) {
                analysis.recommendActions(ITipAction.UPDATE, ITipAction.DECLINECOUNTER);
            } else if (rescheduling(analysis)) {
                analysis.recommendActions(ITipAction.DECLINE, ITipAction.TENTATIVE, ITipAction.DELEGATE, ITipAction.COUNTER);
                if (hasConflicts(analysis)) {
                    analysis.recommendAction(ITipAction.ACCEPT_AND_IGNORE_CONFLICTS);
                } else {
                    analysis.recommendAction(ITipAction.ACCEPT);
                }
            } else {
                if (isCreate(analysis)) {
                    if (message.getMethod() == ITipMethod.COUNTER) {
                        analysis.recommendActions(ITipAction.CREATE);
                    } else {
                        if (change.isException()) {
                            analysis.recommendActions(ITipAction.ACCEPT, ITipAction.DECLINE, ITipAction.TENTATIVE, ITipAction.UPDATE);
                        } else {
                            analysis.recommendActions(ITipAction.ACCEPT, ITipAction.DECLINE, ITipAction.TENTATIVE, ITipAction.DELEGATE, ITipAction.COUNTER);
                        }
                    }
                } else {
                    if (message.getMethod() == ITipMethod.COUNTER) {
                        analysis.recommendActions(ITipAction.UPDATE);
                    } else {
                        analysis.recommendActions(ITipAction.ACCEPT, ITipAction.DECLINE, ITipAction.TENTATIVE, ITipAction.DELEGATE, ITipAction.COUNTER);
                        if (false == (change.getDiff().isEmpty() || change.getDiff().isAboutStateChangesOnly())) {
                            analysis.recommendAction(ITipAction.UPDATE);
                        }
                    }
                }
            }
        }
        if (analysis.getChanges().isEmpty() && analysis.getAnnotations().isEmpty()) {
            change = new ITipChange();
            if (null == original) {
                session.getUtilities().adjustTimeZones(session.getSession(), owner, update, null);
                change.setNewEvent(update);
            } else {
                change.setNewEvent(session.getUtilities().copyEvent(original, (EventField[]) null));
                change.setCurrentEvent(original);
            }
            change.setType(ITipChange.Type.UPDATE);
            analysis.addChange(change);
        }

        return analysis;
    }

    /**
     * Adds all existing Resources to the participant list of the update.
     *
     * @param original The original event to get the resources from
     * @param update The update to add resource to
     */
    protected void addResourcesToUpdate(Event original, Event update) {
        if (original == null || update == null || original.getAttendees() == null || original.getAttendees().size() == 0) {
            return;
        }

        List<Attendee> toAdd = new ArrayList<>();
        for (Attendee a : original.getAttendees()) {
            if (CalendarUserType.RESOURCE.equals(a.getCuType())) {
                toAdd.add(a);
            }
        }
        if (!toAdd.isEmpty()) {
            if (update.getAttendees() == null) {
                update.setAttendees(toAdd);
            } else {
                update.getAttendees().addAll(toAdd);
            }
        }
    }

    // TODO: redesign
    private boolean isOutdated(Event update, Event original) {
        if (original.containsSequence() && update.containsSequence()) {
            if (original.getSequence() > update.getSequence()) {
                return true;
            }
            if (original.getSequence() <= update.getSequence()) {
                return false;
            }
        }
        Calendar originalLastTouched = null;
        if (original.containsLastModified() && original.getLastModified() != null) {
            originalLastTouched = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            originalLastTouched.setTime(original.getLastModified());
        } else if (original.containsCreated() && original.getCreated() != null) {
            originalLastTouched = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            originalLastTouched.setTime(original.getCreated());
        }
        Calendar updateLastTouched = null;
        if (update.containsLastModified() && update.getLastModified() != null) {
            updateLastTouched = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            updateLastTouched.setTime(update.getLastModified());
        } else if (update.containsCreated() && update.getCreated() != null) {
            updateLastTouched = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            updateLastTouched.setTime(update.getCreated());
        }

        if (originalLastTouched != null && updateLastTouched != null) {
            if (timeInMillisWithoutMillis(originalLastTouched) > timeInMillisWithoutMillis(updateLastTouched)) { //Remove millis, since ical accuracy is just of seconds.
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a value indicating whether a specific event represents the copy of another internal user, and therefore shouldn't be touched.
     * <p/>
     * The check is performed on the session user being present in the attendee list, but in an <i>unresolved</i> state, i.e. without his
     * entity identifier being applied yet.
     * 
     * @param session The calendar session
     * @param event The event to check
     * @return <code>true</code> if the supplied event represents a foreign event copy, <code>false</code>, otherwise
     */
    private boolean isForeignCopy(CalendarSession session, Event event) throws OXException {
        Attendee userAttendee = session.getEntityResolver().prepareUserAttendee(session.getUserId());
        Attendee originalAttendee = CalendarUtils.find(event.getAttendees(), userAttendee);
        return null != originalAttendee && false == CalendarUtils.isInternal(originalAttendee);
    }

    private long timeInMillisWithoutMillis(Calendar cal) {
        return cal.getTimeInMillis() - cal.get(Calendar.MILLISECOND);
    }

    private boolean updateOrNew(final ITipAnalysis analysis) {
        for (final ITipChange change : analysis.getChanges()) {
            if (change.getType() == Type.UPDATE || change.getType() == Type.CREATE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a value indicating whether the message contains a {@link ITipMethod#COUNTER}
     * send by a Microsoft made client, see {@link ITipSpecialHandling#MICROSOFT}
     *
     * @param message The {@link ITipMessage}
     * @return <code>true</code> if the message is about a counter from a Microsoft client, <code>false</code> otherwise
     */
    private boolean isMicrosoftCounter(ITipMessage message) {
        return message.getMethod() == ITipMethod.COUNTER && message.hasFeature(ITipSpecialHandling.MICROSOFT);
    }

    /**
     * Fields that are adopted on a COUNTER of a Microsoft client. See {@link #handleMicrosoft(ITipMessage, ITipAnalysis, Event, Event)} for further details
     */
    private final static EventField[] MICROSOFT_COUNTER_FIELDS = new EventField[] { EventField.START_DATE, EventField.END_DATE, EventField.EXTENDED_PROPERTIES };

    /**
     * Handles Microsoft special COUNTER method..
     * <p>
     * Microsoft doesn't allow attendees to add or remove other attendees. Therefore their counter method
     * only contains the MS attendee. This normally is a indicator that the COUNTER is about a removal of
     * an attendee.
     * <p>
     * Nevertheless we got to work around this.. To do so we base the attendees of the updated event on the
     * attendees of the original event and then overwrite the MS attendee object.
     * <p>
     * Moreover MS users can only modify the start date and the end data of an event. Changes made to other
     * properties are done automatically by the client. E.g.: Office365 changes the title of the event to
     * "<code>Appointment changed: The original title</code>". Thus we ignore all other properties changed by
     * the MS clients (expect adding extended properties).
     * 
     * @param message The {@link ITipMessage}
     * @param analysis The {@link ITipChange}
     * @param original The original {@link Event} containing all other attendees
     * @param update The updated {@link Event} to add the original attendees to
     * @return The updated {@link Event}
     * @throws OXException If original event can't be copied
     */
    private Event handleMicrosoft(ITipMessage message, ITipAnalysis analysis, Event original, Event update) throws OXException {
        if (isMicrosoftCounter(message)) {
            if (null == original || null == update || null == original.getAttendees() || null == update.getAttendees() || update.getAttendees().size() != 1) {
                LOGGER.debug("Microsoft special handling unnecessary");
                return update;
            }
            Event copy = EventMapper.getInstance().copy(original, new Event(), (EventField[]) null);
            List<Attendee> attendees = new LinkedList<>(copy.getAttendees());
            Attendee microsoftAttendee = update.getAttendees().get(0);
            boolean isPartyCrasher = true;
            for (Iterator<Attendee> iterator = attendees.iterator(); iterator.hasNext();) {
                Attendee a = iterator.next();
                if (CalendarUtils.extractEMailAddress(microsoftAttendee.getUri()).equals(CalendarUtils.extractEMailAddress(a.getUri()))) {
                    iterator.remove();
                    isPartyCrasher = false;
                    break;
                }
            }
            if (isPartyCrasher) {
                // Party crasher on a COUNTER ..
                LOGGER.debug("Party crasher on a COUNTER ..");
                analysis.recommendAction(ITipAction.ACCEPT_PARTY_CRASHER);
            }
            // Add Microsoft attendee
            attendees.add(microsoftAttendee);
            copy.setAttendees(attendees);

            // Copy start, end date and extended properties
            copy = EventMapper.getInstance().copy(update, copy, MICROSOFT_COUNTER_FIELDS);

            return copy;
        }
        return update;
    }

    /**
     * Adjusts organizer for Microsoft special COUNTER method..
     *
     * @param message The {@link ITipMessage}
     * @param original The original {@link Event}
     * @param update The {@link Event} with the updates
     * @return An updated event that ensures that the organizer is set if {@link #isMicrosoftCounter(ITipMessage)} is <code>true</code>
     * @throws OXException
     */
    private Event adjustOrganizer(ITipMessage message, Event original, Event update) throws OXException {
        if (isMicrosoftCounter(message)) {
            if (null == update.getOrganizer() && null != original.getOrganizer()) {
                Event copy = EventMapper.getInstance().copy(update, null, (EventField[]) null);
                copy = EventMapper.getInstance().copy(original, copy, EventField.ORGANIZER);
                return copy;
            }
        }
        return update;
    }

    /**
     * Check if the organizer is changed. If so stop processing.
     * 
     * @param analysis The {@link ITipAnalysis}
     * @param locale The users {@link Locale}
     * @return <code>true</code> when the organizer is changed, <code>false</code> otherwise
     */
    private boolean isOrganizerChange(Event originalEvent, Event updatedEvent) {
        Organizer original = originalEvent.getOrganizer();
        if (null != original) {
            return false == CalendarUtils.matches(original, updatedEvent.getOrganizer());
        }
        return false;
    }

}
