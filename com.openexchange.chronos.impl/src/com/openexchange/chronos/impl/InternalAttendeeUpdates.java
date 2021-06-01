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

package com.openexchange.chronos.impl;

import static com.openexchange.chronos.common.CalendarUtils.contains;
import static com.openexchange.chronos.common.CalendarUtils.filter;
import static com.openexchange.chronos.common.CalendarUtils.filterByMembership;
import static com.openexchange.chronos.common.CalendarUtils.find;
import static com.openexchange.chronos.common.CalendarUtils.getAttendeeUpdates;
import static com.openexchange.chronos.common.CalendarUtils.isInternal;
import static com.openexchange.chronos.common.CalendarUtils.isLastUserAttendee;
import static com.openexchange.chronos.common.CalendarUtils.matches;
import static com.openexchange.chronos.impl.Utils.getCalendarUserId;
import static com.openexchange.chronos.impl.Utils.getResolvableEntities;
import static com.openexchange.chronos.impl.Utils.isEnforceDefaultAttendee;
import static com.openexchange.chronos.impl.Utils.isSkipExternalAttendeeURIChecks;
import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.common.mapping.AttendeeMapper;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.type.PublicType;
import com.openexchange.groupware.tools.mappings.common.AbstractCollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.CollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.DefaultItemUpdate;
import com.openexchange.groupware.tools.mappings.common.ItemUpdate;

/**
 * {@link InternalAttendeeUpdates}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class InternalAttendeeUpdates implements CollectionUpdate<Attendee, AttendeeField> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(InternalAttendeeUpdates.class);

    private final CalendarSession session;
    private final CalendarFolder folder;
    private final List<Attendee> originalAttendees;
    private final List<Attendee> attendeesToInsert;
    private final List<Attendee> attendeesToDelete;
    private final List<ItemUpdate<Attendee, AttendeeField>> attendeesToUpdate;
    private final Date timestamp;

    /**
     * Initializes a new {@link InternalAttendeeUpdates} for a new event.
     *
     * @param session The calendar session
     * @param folder The parent folder of the event being processed
     * @param event The event data holding the list of attendees, as supplied by the client
     * @param timestamp The timestamp to apply in the updated event data
     */
    public static InternalAttendeeUpdates onNewEvent(CalendarSession session, CalendarFolder folder, Event event, Date timestamp) throws OXException {
        InternalAttendeeUpdates attendeeHelper = new InternalAttendeeUpdates(session, folder, null, timestamp);
        attendeeHelper.processNewEvent(emptyForNull(event.getAttendees()), getResolvableEntities(session, folder, event));
        return attendeeHelper;
    }

    /**
     * Initializes a new {@link InternalAttendeeUpdates} for an updated event.
     *
     * @param session The calendar session
     * @param folder The parent folder of the event being processed
     * @param originalEvent The original event holding the original attendees
     * @param updatedEvent The updated event holding the new/updated list of attendees, as supplied by the client
     * @param timestamp The timestamp to apply in the updated event data
     */
    public static InternalAttendeeUpdates onUpdatedEvent(CalendarSession session, CalendarFolder folder, Event originalEvent, Event updatedEvent, Date timestamp) throws OXException {
        InternalAttendeeUpdates attendeeHelper = new InternalAttendeeUpdates(session, folder, originalEvent.getAttendees(), timestamp);
        attendeeHelper.processUpdatedEvent(emptyForNull(updatedEvent.getAttendees()), getResolvableEntities(session, folder, originalEvent));
        return attendeeHelper;
    }

    /**
     * Initializes a new {@link InternalAttendeeUpdates} for a deleted event.
     *
     * @param session The calendar session
     * @param folder The parent folder of the event being processed
     * @param originalAttendees The original list of attendees
     * @param timestamp The timestamp to apply in the deleted event data
     */
    public static InternalAttendeeUpdates onDeletedEvent(CalendarSession session, CalendarFolder folder, List<Attendee> originalAttendees, Date timestamp) throws OXException {
        InternalAttendeeUpdates attendeeHelper = new InternalAttendeeUpdates(session, folder, originalAttendees, timestamp);
        attendeeHelper.processDeletedEvent();
        return attendeeHelper;
    }

    /**
     * Initializes a new {@link InternalAttendeeUpdates}.
     *
     * @param session The calendar session
     * @param folder The parent folder of the event being processed
     * @param originalAttendees The original attendees of the event, or <code>null</code> for new event creations
     * @param timestamp The timestamp to apply in the updated event data
     */
    private InternalAttendeeUpdates(CalendarSession session, CalendarFolder folder, List<Attendee> originalAttendees, Date timestamp) {
        super();
        this.session = session;
        this.folder = folder;
        this.timestamp = timestamp;
        this.originalAttendees = emptyForNull(originalAttendees);
        this.attendeesToInsert = new ArrayList<Attendee>();
        this.attendeesToDelete = new ArrayList<Attendee>();
        this.attendeesToUpdate = new ArrayList<ItemUpdate<Attendee, AttendeeField>>();
    }

    @Override
    public List<Attendee> getAddedItems() {
        return attendeesToInsert;
    }

    @Override
    public List<Attendee> getRemovedItems() {
        return attendeesToDelete;
    }

    @Override
    public boolean isEmpty() {
        return attendeesToInsert.isEmpty() && attendeesToDelete.isEmpty() && attendeesToUpdate.isEmpty();
    }

    @Override
    public List<? extends ItemUpdate<Attendee, AttendeeField>> getUpdatedItems() {
        return attendeesToUpdate;
    }

    @Override
    public String toString() {
        return "InternalAttendeeUpdates [" + attendeesToDelete.size() + " removed, " + attendeesToInsert.size() + " added, " + attendeesToUpdate.size() + " updated]";
    }

    /**
     * Gets a value indicating whether the applied changes represent an attendee reply of a specific calendar user for the associated
     * calendar object resource or not, depending on the modified attendee fields.
     * 
     * @return <code>true</code> if the underlying calendar resource is replied to along with the update, <code>false</code>, otherwise
     */
    public boolean isReply(CalendarUser calendarUser) {
        return Utils.isReply(this, calendarUser);
    }

    /**
     * Gets a list of attendees that neither have been added, nor removed along with the update. I.e. the resulting list is constructed
     * based on the list of original attendees, minus the removed, updates of updated attendees being applied, minus the newly added
     * attendees.
     * 
     * @return The retained attendees, or an empty list if there are none
     */
    public List<Attendee> getRetainedItems() throws OXException {
        List<Attendee> retainedAttendees = new ArrayList<Attendee>(originalAttendees);
        retainedAttendees.removeAll(attendeesToDelete);
        for (ItemUpdate<Attendee, AttendeeField> attendeeToUpdate : attendeesToUpdate) {
            retainedAttendees.remove(attendeeToUpdate.getOriginal());
            retainedAttendees.add(apply(attendeeToUpdate));
        }
        retainedAttendees.removeAll(attendeesToInsert);
        return retainedAttendees;
    }

    /**
     * Gets a "forecast" of the resulting attendee list after all changes are applied to the original list of attendees. No data is
     * actually changed, i.e. the internal list of attendees to insert, update and delete are still intact.
     *
     * @return The changed list of attendees
     */
    public List<Attendee> previewChanges() throws OXException {
        List<Attendee> newAttendees = new ArrayList<Attendee>(originalAttendees);
        newAttendees.removeAll(attendeesToDelete);
        for (ItemUpdate<Attendee, AttendeeField> attendeeToUpdate : attendeesToUpdate) {
            newAttendees.remove(attendeeToUpdate.getOriginal());
            newAttendees.add(apply(attendeeToUpdate));
        }
        newAttendees.addAll(attendeesToInsert);
        return newAttendees;
    }

    private void processNewEvent(List<Attendee> requestedAttendees, int[] resolvableEntities) throws OXException {
        session.getEntityResolver().prefetch(requestedAttendees);
        requestedAttendees = session.getEntityResolver().prepare(requestedAttendees, resolvableEntities);
        /*
         * always start with attendee for default calendar user in folder
         */
        Attendee defaultAttendee = null;
        if (!PublicType.getInstance().equals(folder.getType())) {
            defaultAttendee = getDefaultAttendee(session, folder, requestedAttendees, timestamp);
            attendeesToInsert.add(defaultAttendee);
        }
        if (null != requestedAttendees && 0 < requestedAttendees.size()) {
            /*
             * prepare & add all further attendees
             */
            List<Attendee> attendeeList = new ArrayList<Attendee>();
            if (defaultAttendee != null) {
                attendeeList.add(defaultAttendee);
            }
            attendeesToInsert.addAll(prepareNewAttendees(attendeeList, requestedAttendees));
        }
        /*
         * apply proper default attendee handling afterwards
         */
        handleDefaultAttendee(isEnforceDefaultAttendee(session));
    }

    private void processUpdatedEvent(List<Attendee> updatedAttendees, int[] resolvableEntities) throws OXException {
        session.getEntityResolver().prefetch(updatedAttendees);
        updatedAttendees = session.getEntityResolver().prepare(updatedAttendees, resolvableEntities);
        AbstractCollectionUpdate<Attendee, AttendeeField> attendeeDiff = getAttendeeUpdates(originalAttendees, updatedAttendees);
        List<Attendee> attendeeList = new ArrayList<Attendee>(originalAttendees);
        /*
         * delete removed attendees
         */
        List<Attendee> removedAttendees = attendeeDiff.getRemovedItems();
        for (Attendee removedAttendee : removedAttendees) {
            if (isEnforceDefaultAttendee(session) && false == PublicType.getInstance().equals(folder.getType()) && removedAttendee.getEntity() == folder.getCreatedBy()) {
                /*
                 * preserve default calendar user in personal folders
                 */
                LOG.info("Implicitly preserving default calendar user {} in personal folder {}.", I(removedAttendee.getEntity()), folder);
                continue;
            }
            if (CalendarUserType.GROUP.equals(removedAttendee.getCuType())) {
                /*
                 * only remove group attendee in case all originally participating members are also removed
                 */
                if (hasAttendingGroupMembers(removedAttendee.getUri(), originalAttendees, removedAttendees)) {
                    // preserve group attendee
                    LOG.debug("Ignoring removal of group {} as long as there are attending members.", I(removedAttendee.getEntity()));
                    continue;
                }
            }
            if (null != removedAttendee.getMember() && 0 < removedAttendee.getMember().size()) {
                /*
                 * only remove group member attendee in case either the corresponding group attendee itself, or *not all* originally participating members are also removed
                 */
                if (false == containsAllUris(removedAttendees, removedAttendee.getMember())) {
                    boolean attendingOtherMembers = false;
                    for (String groupUri : removedAttendee.getMember()) {
                        if (hasAttendingGroupMembers(groupUri, originalAttendees, removedAttendees)) {
                            attendingOtherMembers = true;
                            break;
                        }
                    }
                    if (false == attendingOtherMembers) {
                        // preserve group member attendee
                        LOG.debug("Ignoring removal of group member attendee {} as long as group unchanged.", I(removedAttendee.getEntity()));
                        continue;
                    }
                }
            }
            /*
             * Track time of attendee deletion 
             */
            removedAttendee.setTimestamp(timestamp.getTime());
            
            attendeeList.remove(removedAttendee);
            attendeesToDelete.add(removedAttendee);
        }
        /*
         * apply updated attendee data
         */
        for (ItemUpdate<Attendee, AttendeeField> attendeeUpdate : attendeeDiff.getUpdatedItems()) {
            Check.requireUpToDateTimestamp(attendeeUpdate.getOriginal(), attendeeUpdate.getUpdate());
            Attendee attendee = apply(attendeeUpdate);
            if (attendeeUpdate.getUpdatedFields().contains(AttendeeField.URI)) {
                if (false == isInternal(attendee) && false == isSkipExternalAttendeeURIChecks(session)) {
                    attendee = Check.requireValidEMail(attendee);
                }
            }
            if (attendeeUpdate.getUpdatedFields().contains(AttendeeField.PARTSTAT)) {
                /*
                 * ensure to reset RSVP expectation along with change of participation status
                 * ensure to update the timestamp
                 */
                attendee.setRsvp(null);
                attendee.setTimestamp(timestamp.getTime());
            }
            if (attendeeUpdate.getUpdatedFields().contains(AttendeeField.COMMENT)) {
                /*
                 * ensure to update the timestamp
                 */
                attendee.setTimestamp(timestamp.getTime());
            }
            attendeesToUpdate.add(new DefaultItemUpdate<Attendee, AttendeeField>(AttendeeMapper.getInstance(), attendeeUpdate.getOriginal(), attendee));
        }
        /*
         * prepare & add all new attendees
         */
        attendeesToInsert.addAll(prepareNewAttendees(attendeeList, attendeeDiff.getAddedItems()));
        /*
         * apply proper default attendee handling afterwards
         */
        handleDefaultAttendee(isEnforceDefaultAttendee(session));
    }

    private void processDeletedEvent() throws OXException {
        for (Attendee attendee : originalAttendees) {
            Attendee removed = AttendeeMapper.getInstance().copy(attendee, null, (AttendeeField[]) null);
            removed.setTimestamp(timestamp.getTime());
            attendeesToDelete.add(removed);
        }
    }

    private List<Attendee> prepareNewAttendees(List<Attendee> existingAttendees, List<Attendee> newAttendees) throws OXException {
        List<Attendee> attendees = new ArrayList<Attendee>(newAttendees.size());
        /*
         * add internal user attendees
         */
        boolean inPublicFolder = PublicType.getInstance().equals(folder.getType());
        for (Attendee userAttendee : filter(newAttendees, Boolean.TRUE, CalendarUserType.INDIVIDUAL)) {
            if (contains(existingAttendees, userAttendee) || contains(attendees, userAttendee)) {
                LOG.debug("Skipping duplicate user attendee {}", userAttendee);
                continue;
            }
            userAttendee = session.getEntityResolver().applyEntityData(userAttendee);
            userAttendee.setFolderId(inPublicFolder ? null : session.getConfig().getDefaultFolderId(userAttendee.getEntity()));
            if (false == userAttendee.containsPartStat() || null == userAttendee.getPartStat()) {
                userAttendee.setPartStat(session.getConfig().getInitialPartStat(userAttendee.getEntity(), inPublicFolder));
            }
            userAttendee.setTimestamp(timestamp.getTime());
            attendees.add(userAttendee);
        }
        /*
         * resolve & add any internal group attendees
         */
        boolean resolveGroupAttendees = session.getConfig().isResolveGroupAttendees();
        for (Attendee groupAttendee : filter(newAttendees, Boolean.TRUE, CalendarUserType.GROUP)) {
            if (contains(existingAttendees, groupAttendee) || contains(attendees, groupAttendee)) {
                LOG.debug("Skipping duplicate group attendee {}", groupAttendee);
                continue;
            }
            groupAttendee = session.getEntityResolver().applyEntityData(groupAttendee);
            if (false == resolveGroupAttendees) {
                attendees.add(groupAttendee);
            } else {
                LOG.debug("Skipping group attendee {}; only resolving group members.", groupAttendee);
            }
            for (int memberID : session.getEntityResolver().getGroupMembers(groupAttendee.getEntity())) {
                if (contains(existingAttendees, memberID) || contains(attendees, memberID)) {
                    LOG.debug("Skipping explicitly added group member {}", I(memberID));
                    continue;
                }
                Attendee memberAttendee = session.getEntityResolver().prepareUserAttendee(memberID);
                memberAttendee.setFolderId(PublicType.getInstance().equals(folder.getType()) ? null : session.getConfig().getDefaultFolderId(memberID));
                memberAttendee.setPartStat(session.getConfig().getInitialPartStat(memberID, inPublicFolder));
                memberAttendee.setTimestamp(timestamp.getTime());
                if (false == resolveGroupAttendees) {
                    memberAttendee.setMember(Collections.singletonList(groupAttendee.getUri()));
                }
                attendees.add(memberAttendee);
            }
        }
        /*
         * resolve & add any internal resource attendees
         */
        for (Attendee resourceAttendee : filter(newAttendees, Boolean.TRUE, CalendarUserType.RESOURCE)) {
            if (contains(existingAttendees, resourceAttendee) || contains(attendees, resourceAttendee)) {
                LOG.debug("Skipping duplicate resource attendee {}", resourceAttendee);
                continue;
            }
            attendees.add(session.getEntityResolver().applyEntityData(resourceAttendee));
        }
        /*
         * take over any external attendees
         */
        for (Attendee attendee : filter(newAttendees, Boolean.FALSE, (CalendarUserType[]) null)) {
            attendee = session.getEntityResolver().applyEntityData(attendee);
            if (contains(existingAttendees, attendee) || contains(attendees, attendee)) {
                LOG.debug("Skipping duplicate external attendee {}", attendee);
                continue;
            }
            attendees.add(isSkipExternalAttendeeURIChecks(session) ? attendee : Check.requireValidEMail(attendee));
        }
        return attendees;
    }

    /**
     * Gets the <i>default</i> attendee that is always added to a newly inserted event, based on the target folder type.<p/>
     * For <i>public</i> folders, this is an attendee for the current session's user, otherwise (<i>private</i> or <i>shared</i>, an
     * attendee for the folder owner (i.e. the calendar user) is prepared.
     *
     * @param session The calendar session
     * @param folder The folder to get the default attendee for
     * @param requestedAttendees The attendees as supplied by the client, or <code>null</code> if not available
     * @param timestamp The timestamp to apply for the default attendee, or <code>null</code> to use the systems current time
     * @return The default attendee
     * @throws OXException 
     */
    public static Attendee getDefaultAttendee(CalendarSession session, CalendarFolder folder, List<Attendee> requestedAttendees, Date timestamp) throws OXException {
        /*
         * prepare attendee for default calendar user in folder
         */
        int calendarUserId = getCalendarUserId(folder);
        Attendee defaultAttendee = session.getEntityResolver().prepareUserAttendee(calendarUserId);
        defaultAttendee.setPartStat(ParticipationStatus.ACCEPTED);
        defaultAttendee.setTimestamp(null == timestamp ? System.currentTimeMillis() : timestamp.getTime());
        defaultAttendee.setFolderId(PublicType.getInstance().equals(folder.getType()) ? null : folder.getId());
        if (session.getUserId() != calendarUserId) {
            defaultAttendee.setSentBy(session.getEntityResolver().applyEntityData(new CalendarUser(), session.getUserId()));
        }
        /*
         * take over additional properties from corresponding requested attendee
         */
        Attendee requestedAttendee = find(requestedAttendees, defaultAttendee);
        if (null != requestedAttendee) {
            AttendeeMapper.getInstance().copy(requestedAttendee, defaultAttendee,
                AttendeeField.RSVP, AttendeeField.COMMENT, AttendeeField.PARTSTAT, AttendeeField.ROLE, 
                AttendeeField.PARTSTAT, AttendeeField.CN, AttendeeField.URI, AttendeeField.TIMESTAMP);
        }
        return defaultAttendee;
    }

    private static List<Attendee> emptyForNull(List<Attendee> attendees) {
        return null == attendees ? Collections.<Attendee> emptyList() : attendees;
    }

    private static ItemUpdate<Attendee, AttendeeField> findUpdate(List<ItemUpdate<Attendee, AttendeeField>> attendeeUpdates, int entity) {
        if (null != attendeeUpdates) {
            for (ItemUpdate<Attendee, AttendeeField> attendeeUpdate : attendeeUpdates) {
                if (matches(attendeeUpdate.getOriginal(), entity)) {
                    return attendeeUpdate;
                }
            }
        }
        return null;
    }

    /**
     * Processes the lists of attendees to update/delete/insert in terms of the configured handling of the implicit attendee for the
     * actual calendar user.
     * <p/>
     * If the default attendee is enforced, this method ensures that the calendar user attendee is always present in
     * personal calendar folders, and there is at least one attendee present for events in public folders. Otherwise, if the actual
     * calendar user would be the last one in the resulting attendee list, this attendee is removed.
     *
     * @param enforceDefaultAttendee <code>true</code> the current calendar user should be added as default attendee to events implicitly,
     *            <code>false</code>, otherwise
     * @param timestamp The timestamp to apply in the updated event data
     */
    private void handleDefaultAttendee(boolean enforceDefaultAttendee) throws OXException {
        int calendarUserId = getCalendarUserId(folder);
        List<Attendee> attendees = previewChanges();
        /*
         * check if resulting attendees would lead to a "group-scheduled" event or not
         */
        if (false == enforceDefaultAttendee && (attendees.isEmpty() || isLastUserAttendee(attendees, calendarUserId))) {
            /*
             * event is not (or no longer) a group-scheduled one, remove default attendee
             */
            Attendee defaultAttendee = find(attendeesToInsert, calendarUserId);
            if (null != defaultAttendee) {
                attendeesToInsert.remove(defaultAttendee);
            }
            ItemUpdate<Attendee, AttendeeField> defaultAttendeeUpdate = findUpdate(attendeesToUpdate, calendarUserId);
            if (null != defaultAttendeeUpdate) {
                attendeesToUpdate.remove(defaultAttendeeUpdate);
                attendeesToDelete.add(defaultAttendeeUpdate.getOriginal());
            } else {
                defaultAttendee = find(originalAttendees, calendarUserId);
                if (null != defaultAttendee) {
                    attendeesToDelete.add(defaultAttendee);
                }
            }
        } else {
            /*
             * enforce at least the calendar user to be present in public folders
             */
            if (PublicType.getInstance().equals(folder.getType())) {
                if (attendees.isEmpty()) {
                    Attendee defaultAttendee = find(attendeesToDelete, calendarUserId);
                    if (null != defaultAttendee) {
                        LOG.info("Implicitly preserving default calendar user {} in public folder {}.", I(calendarUserId), folder);
                        attendeesToDelete.remove(defaultAttendee);
                    } else {
                        LOG.info("Implicitly adding default calendar user {} in public folder {}.", I(calendarUserId), folder);
                        attendeesToInsert.add(getDefaultAttendee(session, folder, null, timestamp));
                    }
                }
            } else if (false == contains(attendees, calendarUserId)) {
                /*
                 * ensure the calendar user is always present in personal calendar folders
                 */
                Attendee defaultAttendee = find(attendeesToDelete, calendarUserId);
                if (null != defaultAttendee) {
                    LOG.info("Implicitly preserving default calendar user {} in personal folder {}.", I(calendarUserId), folder);
                    attendeesToDelete.remove(defaultAttendee);
                } else {
                    LOG.info("Implicitly adding default calendar user {} in personal folder {}.", I(calendarUserId), folder);
                    attendeesToInsert.add(getDefaultAttendee(session, folder, null, timestamp));
                }
            }
        }
    }

    /**
     * Gets a value indicating whether a resulting attendee list is going to contain at least one member of a specific group or not.
     *
     * @param groupUri The uri of the group to check
     * @param originalAttendees The list of original attendees
     * @param removedAttendees The list of removed attendees
     * @return <code>true</code> if there'll be at least one group member afterwards, <code>false</code>, otherwise
     */
    private static boolean hasAttendingGroupMembers(String groupUri, List<Attendee> originalAttendees, List<Attendee> removedAttendees) {
        for (Attendee originalMemberAttendee : filterByMembership(originalAttendees, groupUri)) {
            if (null == find(removedAttendees, originalMemberAttendee)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Instantiates a new attendee, takes over all <i>set</i> properties from the updated item in the supplied attendee update, then
     * copies over any <i>static</i> property values from the original - so that the resulting attendee represents the attendee after
     * the update.
     * 
     * @param attendeeUpdate The attendee update to apply
     * @return A new attendee instance representing the updated attendee
     */
    private static Attendee apply(ItemUpdate<Attendee, AttendeeField> attendeeUpdate) throws OXException {
        Attendee newAttendee = AttendeeMapper.getInstance().copy(attendeeUpdate.getUpdate(), null, (AttendeeField[]) null);
        return AttendeeMapper.getInstance().copy(attendeeUpdate.getOriginal(), newAttendee, AttendeeField.ENTITY, AttendeeField.MEMBER, AttendeeField.CU_TYPE, AttendeeField.URI);
    }

    private static boolean containsAllUris(List<Attendee> attendees, List<String> uris) {
        if (null == uris || 0 == uris.size()) {
            return true;
        }
        for (String uri : uris) {
            if (false == containsUri(attendees, uri)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsUri(List<Attendee> attendees, String uri) {
        if (null == attendees || 0 == attendees.size()) {
            return false;
        }
        for (Attendee attendee : attendees) {
            if (uri.equalsIgnoreCase(attendee.getUri())) {
                return true;
            }
        }
        return false;
    }

}
