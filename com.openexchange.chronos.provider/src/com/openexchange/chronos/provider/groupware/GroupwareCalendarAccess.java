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

package com.openexchange.chronos.provider.groupware;

import java.util.ArrayList;
import java.util.List;
import org.dmfs.rfc5545.DateTime;
import com.openexchange.ajax.fileholder.IFileHolder;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.Attachment;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.CalendarObjectResource;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.SchedulingControl;
import com.openexchange.chronos.provider.CalendarFolder;
import com.openexchange.chronos.provider.extensions.PermissionAware;
import com.openexchange.chronos.provider.folder.FolderCalendarAccess;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.service.CalendarResult;
import com.openexchange.chronos.service.EventID;
import com.openexchange.chronos.service.ImportResult;
import com.openexchange.exception.OXException;

/**
 * {@link GroupwareCalendarAccess}
 * 
 * Extends {@link FolderCalendarAccess} by certain groupware-specific functionality.
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public interface GroupwareCalendarAccess extends FolderCalendarAccess, PermissionAware {

    /**
     * Default implementation delegating to {@link #getVisibleFolders(GroupwareFolderType)} for all types. Override if applicable.
     */
    @Override
    default List<CalendarFolder> getVisibleFolders() throws OXException {
        List<CalendarFolder> folders = new ArrayList<CalendarFolder>();
        for (GroupwareFolderType type : GroupwareFolderType.values()) {
            folders.addAll(getVisibleFolders(type));
        }
        return folders;
    }

    /**
     * Gets a list of all visible calendar folders.
     *
     * @param type The type to get the visible folders for
     * @return A list of all visible calendar folders of the type
     */
    List<GroupwareCalendarFolder> getVisibleFolders(GroupwareFolderType type) throws OXException;

    /**
     * Creates a new event.
     * <p/>
     * The following calendar parameters are evaluated:
     * <ul>
     * <li>{@link CalendarParameters#PARAMETER_CHECK_CONFLICTS}</li>
     * <li>{@link CalendarParameters#PARAMETER_SCHEDULING}</li>
     * <li>{@link CalendarParameters#PARAMETER_TRACK_ATTENDEE_USAGE}</li>
     * </ul>
     *
     * @param folderId The identifier of the folder to create the event in
     * @param event The event data to create
     * @return The create result
     */
    CalendarResult createEvent(String folderId, Event event) throws OXException;

    /**
     * Puts a new or updated <i>calendar object resource</i>, i.e. an event and/or its change exceptions, to the calendar. In case the
     * calendar resource already exists under the perspective of the parent folder, it is updated implicitly: No longer indicated events
     * are removed, new ones are added, and existing ones are updated.
     * <p/>
     * The following calendar parameters are evaluated:
     * <ul>
     * <li>{@link CalendarParameters#PARAMETER_CHECK_CONFLICTS}</li>
     * <li>{@link CalendarParameters#PARAMETER_SCHEDULING}</li>
     * <li>{@link CalendarParameters#PARAMETER_TRACK_ATTENDEE_USAGE}</li>
     * <li>{@link CalendarParameters#PARAMETER_IGNORE_FORBIDDEN_ATTENDEE_CHANGES}</li>
     * </ul>
     *
     * @param folderId The identifier of the folder to add/update the calendar object resource in
     * @param resource The calendar object resource to store
     * @param replace <code>true</code> to automatically remove stored events that are no longer present in the supplied resource, <code>false</code>, otherwise
     * @return The calendar result
     * @see <a href="https://tools.ietf.org/html/rfc4791#section-4.1">RFC 4791, section 4.1</a><br/>
     *      <a href="https://tools.ietf.org/html/rfc6638#section-3.1">RFC 6638, section 3.1</a>
     */
    CalendarResult putResource(String folderId, CalendarObjectResource resource, boolean replace) throws OXException;

    /**
     * Updates an existing event.
     * <p/>
     * The following calendar parameters are evaluated:
     * <ul>
     * <li>{@link CalendarParameters#PARAMETER_CHECK_CONFLICTS}</li>
     * <li>{@link CalendarParameters#PARAMETER_SCHEDULING}</li>
     * <li>{@link CalendarParameters#PARAMETER_TRACK_ATTENDEE_USAGE}</li>
     * <li>{@link CalendarParameters#PARAMETER_IGNORE_FORBIDDEN_ATTENDEE_CHANGES}</li>
     * </ul>
     *
     * @param eventID The identifier of the event to update
     * @param event The event data to update
     * @param clientTimestamp The last timestamp / sequence number known by the client to catch concurrent updates
     * @return The update result
     */
    CalendarResult updateEvent(EventID eventID, Event event, long clientTimestamp) throws OXException;

    /**
     * Moves an existing event into another folder.
     * <p/>
     * The following calendar parameters are evaluated:
     * <ul>
     * <li>{@link CalendarParameters#PARAMETER_CHECK_CONFLICTS}</li>
     * <li>{@link CalendarParameters#PARAMETER_SCHEDULING}</li>
     * </ul>
     *
     * @param eventID The identifier of the event to update
     * @param folderId The identifier of the folder to move the event to
     * @param clientTimestamp The last timestamp / sequence number known by the client to catch concurrent updates
     * @return The move result
     */
    CalendarResult moveEvent(EventID eventID, String folderId, long clientTimestamp) throws OXException;

    /**
     * Updates a specific attendee of an existing event.
     *
     * @param eventID The identifier of the event to update
     * @param attendee The attendee to update
     * @param alarms The alarms to update, or <code>null</code> to not change alarms, or an empty array to delete any existing alarms
     * @param clientTimestamp The last timestamp / sequence number known by the client to catch concurrent updates
     * @return The update result
     */
    CalendarResult updateAttendee(EventID eventID, Attendee attendee, List<Alarm> alarms, long clientTimestamp) throws OXException;

    /**
     * Deletes an existing event.
     *
     * @param eventID The identifier of the event to delete
     * @param clientTimestamp The last timestamp / sequence number known by the client to catch concurrent updates
     * @return The delete result
     */
    CalendarResult deleteEvent(EventID eventID, long clientTimestamp) throws OXException;

    /**
     * Splits an existing event series into two separate event series.
     *
     * @param eventID The identifier of the event series to split
     * @param splitPoint The date or date-time where the split is to occur
     * @param uid A new unique identifier to assign to the new part of the series, or <code>null</code> if not set
     * @param clientTimestamp The last timestamp / sequence number known by the client to catch concurrent updates
     * @return The split result
     */
    CalendarResult splitSeries(EventID eventID, DateTime splitPoint, String uid, long clientTimestamp) throws OXException;

    /**
     * Imports a list of events into a specific folder.
     * <p/>
     * <b>Note:</b> Only available for the internal <i>groupware</i> calendar provider.
     * <p/>
     * The following calendar parameters are evaluated:
     * <ul>
     * <li>{@link CalendarParameters#PARAMETER_CHECK_CONFLICTS}, defaulting to {@link Boolean#FALSE} unless overridden</li>
     * <li>{@link CalendarParameters#PARAMETER_SCHEDULING}, defaulting to {@link SchedulingControl#NONE} unless overridden</li>
     * <li>{@link CalendarParameters#PARAMETER_IGNORE_STORAGE_WARNINGS}, defaulting to {@link Boolean#TRUE} unless overridden</li>
     * <li>{@link CalendarParameters#UID_CONFLICT_STRATEGY}</li>
     * </ul>
     *
     * @param folderId The identifier of the target folder
     * @param events The events to import
     * @return A list of results holding further information about each imported event
     */
    List<ImportResult> importEvents(String folderId, List<Event> events) throws OXException;

    /**
     * Retrieves the {@link IFileHolder} with the specified managed identifier from the {@link Event}
     * with the specified {@link EventID}
     *
     * @param eventID The {@link Event} identifier
     * @param managedId The managed identifier of the {@link Attachment}
     * @return The {@link IFileHolder}
     * @throws OXException if an error is occurred
     */
    IFileHolder getAttachment(EventID eventID, int managedId) throws OXException;

    /**
     * Updates the event's organizer to the new one.
     * <p>
     * Current restrictions are:
     * 
     * <li>The event has to be a group scheduled event</li>
     * <li>All attendees of the event have to be internal</li>
     * <li>The new organizer must be an internal user</li>
     * <li>The change has to be performed for one of these:
     * <ul> a single event</ul>
     * <ul> a series master, efficiently updating for the complete series</ul>
     * <ul> a specific recurrence of the series, efficiently performing a series split. Only allowed if {@link com.openexchange.chronos.RecurrenceRange#THISANDFUTURE} is set</ul>
     * </li>
     * 
     * @param eventID The {@link EventID} of the event to change. Optional having a recurrence ID set to perform a series split.
     * @param organizer The new organizer
     * @param clientTimestamp The last timestamp / sequence number known by the client to catch concurrent updates
     * @return The updated event
     * @throws OXException In case the organizer change is not allowed
     */
    CalendarResult changeOrganizer(EventID eventID, Organizer organizer, long clientTimestamp) throws OXException;

}
