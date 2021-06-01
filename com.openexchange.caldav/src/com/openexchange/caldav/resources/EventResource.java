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

package com.openexchange.caldav.resources;

import static com.openexchange.dav.DAVProtocol.protocolException;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.dmfs.rfc5545.DateTime;
import com.google.common.escape.Escaper;
import com.google.common.xml.XmlEscapers;
import com.openexchange.caldav.CalDAVImport;
import com.openexchange.caldav.CaldavProtocol;
import com.openexchange.caldav.EventPatches;
import com.openexchange.caldav.GroupwareCaldavFactory;
import com.openexchange.caldav.PhantomMaster;
import com.openexchange.caldav.Tools;
import com.openexchange.caldav.mixins.ScheduleTag;
import com.openexchange.chronos.CalendarObjectResource;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.ical.CalendarExport;
import com.openexchange.chronos.ical.ICalParameters;
import com.openexchange.chronos.ical.ICalService;
import com.openexchange.chronos.provider.composition.IDBasedCalendarAccess;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.service.CalendarResult;
import com.openexchange.chronos.service.EventID;
import com.openexchange.dav.DAVProtocol;
import com.openexchange.dav.DAVUserAgent;
import com.openexchange.dav.PreconditionException;
import com.openexchange.dav.resources.DAVCollection;
import com.openexchange.dav.resources.DAVObjectResource;
import com.openexchange.dav.resources.FolderCollection;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.notify.hostname.HostData;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;
import com.openexchange.user.User;
import com.openexchange.user.UserService;
import com.openexchange.webdav.protocol.WebdavPath;
import com.openexchange.webdav.protocol.WebdavProperty;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;

/**
 * {@link EventResource}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class EventResource extends DAVObjectResource<Event> {

    private static final String CONTENT_TYPE = "text/calendar; charset=UTF-8";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EventResource.class);

    private final EventCollection parent;
    private final Event object;

    private byte[] iCalFile;
    private CalDAVImport caldavImport;

    /**
     * Initializes a new {@link EventResource}.
     *
     * @param parent The parent event collection
     * @param event The represented event
     * @param url The WebDAV path to the resource
     */
    public EventResource(EventCollection parent, Event event, WebdavPath url) {
        super(parent, event, url);
        this.parent = parent;
        this.object = event;
        includeProperties(new ScheduleTag(this));
    }

    /**
     * Gets the parent event collection of this resource.
     *
     * @return The parent event collection
     */
    public EventCollection getParent() {
        return parent;
    }

    /**
     * Gets the underlying event represented through this resource.
     *
     * @return The event, or <code>null</code> if not existent
     */
    public Event getEvent() {
        return object;
    }

    @Override
    protected String getFileExtension() {
        return parent.getFileExtension();
    }

    @Override
    protected Date getCreationDate(Event object) {
        return null != object ? object.getCreated() : null;
    }

    @Override
    protected Date getLastModified(Event object) {
        return null != object ? new Date(object.getTimestamp()) : null;
    }

    @Override
    protected String getId(Event object) {
        return object == null ? null : object.getId();
    }

    @Override
    protected int getId(FolderCollection<Event> collection) throws OXException {
        return null != collection ? Tools.parse(collection.getFolder().getID()) : 0;
    }

    @Override
    public HostData getHostData() throws WebdavProtocolException {
        return super.getHostData();
    }

    @Override
    public DAVUserAgent getUserAgent() {
        return super.getUserAgent();
    }

    @Override
    public GroupwareCaldavFactory getFactory() {
        return parent.getFactory();
    }

    @Override
    public void putBody(InputStream body, boolean guessSize) throws WebdavProtocolException {
        try {
            caldavImport = new CalDAVImport(this, body);
        } catch (OXException e) {
            throw getProtocolException(e);
        }
    }

    /**
     * Gets the schedule-tag, a value indicating whether the scheduling object resource has had a
     * <i>consequential</i> change made to it.
     *
     * @return The event resource's schedule-tag
     */
    public String getScheduleTag() {
        if (null == object || false == object.containsSequence()) {
            return null;
        }
        return String.valueOf(object.getSequence());
    }

    /**
     * Splits a recurring event series on a certain split point.
     *
     * @param rid The date or date-time where the split is to occur
     * @param uid A new unique identifier to assign to the new part of the series, or <code>null</code> if not set
     * @return The url of the newly created, detached recurring event
     */
    public WebdavPath split(String rid, String uid) throws WebdavProtocolException {
        DateTime splitPoint;
        try {
            splitPoint = DateTime.parse(rid);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new PreconditionException(DAVProtocol.CAL_NS.getURI(), "valid-rid-parameter", getUrl(), HttpServletResponse.SC_FORBIDDEN);
        }
        try {
            return parent.constructPathForChildResource(new CalendarAccessOperation<Event>(factory) {

                @Override
                protected Event perform(IDBasedCalendarAccess access) throws OXException {
                    EventID eventID = new EventID(parent.folderID, object.getId());
                    CalendarResult result = access.splitSeries(eventID, splitPoint, uid, object.getTimestamp());
                    if (result.getCreations().isEmpty()) {
                        LOG.warn("{}: No event created.", getUrl());
                        throw new PreconditionException(DAVProtocol.CAL_NS.getURI(), "valid-calendar-object-resource", url, HttpServletResponse.SC_FORBIDDEN);
                    }
                    return result.getCreations().get(0).getCreatedEvent();
                }
            }.execute(factory.getSession()));
        } catch (OXException e) {
            throw getProtocolException(e);
        }
    }

    private byte[] getICalFile() throws WebdavProtocolException {
        if (null == iCalFile) {
            try {
                iCalFile = generateICal();
            } catch (OXException e) {
                throw getProtocolException(e);
            }
        }
        return iCalFile;
    }

    private byte[] generateICal() throws OXException {
        InputStream inputStream = null;
        try {
            /*
             * load event data for export
             */
            List<Event> exportedEvents = new CalendarAccessOperation<List<Event>>(factory) {

                @Override
                protected List<Event> perform(IDBasedCalendarAccess access) throws OXException {
                    access.set(CalendarParameters.PARAMETER_FIELDS, null);
                    List<Event> exportedEvents = new ArrayList<Event>();
                    if (PhantomMaster.class.isInstance(object)) {
                        /*
                         * no access to parent recurring master, use detached occurrences as exceptions
                         */
                        exportedEvents.addAll(access.getChangeExceptions(object.getFolderId(), object.getSeriesId()));
                    } else {
                        /*
                         * add (master) event and any overridden instances
                         */
                        Event event = access.getEvent(new EventID(object.getFolderId(), object.getId(), object.getRecurrenceId()));
                        exportedEvents.add(event);
                        if (CalendarUtils.isSeriesMaster(object)) {
                            exportedEvents.addAll(access.getChangeExceptions(object.getFolderId(), object.getSeriesId()));
                        }
                    }
                    return exportedEvents;
                }
            }.execute(factory.getSession());
            /*
             * init export & add events
             */
            ICalService iCalService = getFactory().requireService(ICalService.class);
            ICalParameters iCalParameters = EventPatches.applyIgnoredProperties(this, iCalService.initParameters());
            CalendarExport calendarExport = iCalService.exportICal(iCalParameters);
            for (Event exportedEvent : exportedEvents) {
                calendarExport.add(EventPatches.Outgoing(parent.getFactory()).applyAll(this, exportedEvent));
            }
            /*
             * add any extended properties & serialize ical
             */
            EventPatches.Outgoing.applyExport(this, calendarExport, exportedEvents);
            inputStream = calendarExport.getClosingStream();
            return Streams.stream2bytes(inputStream);
        } catch (IOException e) {
            throw protocolException(getUrl(), e);
        } finally {
            Streams.close(inputStream);
        }
    }

    @Override
    public Long getLength() throws WebdavProtocolException {
        byte[] iCalFile = getICalFile();
        return new Long(null != iCalFile ? iCalFile.length : 0);
    }

    @Override
    public String getContentType() throws WebdavProtocolException {
        return CONTENT_TYPE;
    }

    @Override
    public InputStream getBody() throws WebdavProtocolException {
        byte[] iCalFile = getICalFile();
        if (null != iCalFile) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("iCal file: {}", new String(iCalFile, Charsets.UTF_8));
            }
            return Streams.newByteArrayInputStream(iCalFile);
        }
        return null;
    }

    @Override
    protected WebdavProperty internalGetProperty(String namespace, String name) throws WebdavProtocolException {
        if (CaldavProtocol.CAL_NS.getURI().equals(namespace) && "calendar-data".equals(name)) {
            WebdavProperty property = new WebdavProperty(namespace, name);
            byte[] iCalFile = getICalFile();
            if (null != iCalFile) {
                property.setXML(true);
                property.setValue(XmlEscapers.xmlContentEscaper().escape(new String(iCalFile, Charsets.UTF_8)));
            }
            return property;
        }
        if (CaldavProtocol.CALENDARSERVER_NS.getURI().equals(namespace) && ("created-by".equals(name) || "updated-by".equals(name))) {
            WebdavProperty property = new WebdavProperty(namespace, name);
            if (null != object) {
                CalendarUser calendarUser;
                Date timestamp;
                if ("created-by".equals(name)) {
                    calendarUser = object.getCreatedBy();
                    timestamp = object.getCreated();
                } else {
                    calendarUser = object.getModifiedBy();
                    timestamp = object.getLastModified();
                }
                Escaper escaper = XmlEscapers.xmlContentEscaper();
                StringBuilder stringBuilder = new StringBuilder();
                if (null != calendarUser) {
                    stringBuilder.append("<D:href>").append(escaper.escape(calendarUser.getUri())).append("</D:href>");
                    if (CalendarUtils.isInternal(calendarUser, CalendarUserType.INDIVIDUAL)) {
                        try {
                            User user = factory.getService(UserService.class).getUser(calendarUser.getEntity(), factory.getContext());
                            stringBuilder.append("<CS:first-name>").append(escaper.escape(user.getGivenName())).append("</CS:first-name>")
                                .append("<CS:last-name>").append(escaper.escape(user.getSurname())).append("</CS:last-name>");
                        } catch (OXException e) {
                            LOG.warn("error resolving user '{}'", calendarUser, e);
                        }
                    }
                }
                if (null != timestamp) {
                    stringBuilder.append("<CS:dtstamp>").append(Tools.formatAsUTC(timestamp)).append("</CS:dtstamp>");
                }
                property.setXML(true);
                property.setValue(stringBuilder.toString());
            }
            return property;
        }
        return null;
    }

    @Override
    public EventResource move(WebdavPath dest, boolean noroot, boolean overwrite) throws WebdavProtocolException {
        WebdavResource destinationResource = factory.resolveResource(dest);
        DAVCollection destinationCollection = destinationResource.isCollection() ? (DAVCollection) destinationResource : getFactory().resolveCollection(dest.parent());
        if (false == parent.getClass().isInstance(destinationCollection)) {
            throw protocolException(getUrl(), HttpServletResponse.SC_FORBIDDEN);
        }
        EventCollection targetCollection;
        try {
            targetCollection = (EventCollection) destinationCollection;
        } catch (ClassCastException e) {
            throw protocolException(getUrl(), e, HttpServletResponse.SC_FORBIDDEN);
        }
        EventID eventID = new EventID(parent.folderID, object.getId());
        try {
            new CalendarAccessOperation<Void>(factory) {

                @Override
                protected Void perform(IDBasedCalendarAccess access) throws OXException {
                    access.moveEvent(eventID, targetCollection.folderID, object.getTimestamp());
                    return null;
                }
            }.execute(factory.getSession());
        } catch (OXException e) {
            throw getProtocolException(e);
        }
        return this;
    }

    @Override
    public void create() throws WebdavProtocolException {
        if (exists()) {
            throw protocolException(getUrl(), HttpServletResponse.SC_CONFLICT);
        }
        try {
            putEvents(caldavImport);
        } catch (OXException e) {
            throw getProtocolException(e);
        }
    }

    @Override
    public void delete() throws WebdavProtocolException {
        try {
            deleteEvent(object);
        } catch (OXException e) {
            throw getProtocolException(e);
        }
    }

    @Override
    public void save() throws WebdavProtocolException {
        if (false == exists()) {
            throw protocolException(getUrl(), HttpServletResponse.SC_NOT_FOUND);
        }
        try {
            putEvents(caldavImport);
        } catch (OXException e) {
            throw getProtocolException(e);
        }
    }

    private void deleteEvent(Event event) throws OXException {
        if (false == exists()) {
            throw protocolException(getUrl(), HttpServletResponse.SC_NOT_FOUND);
        }
        List<EventID> eventIDs;
        if (PhantomMaster.class.isInstance(event)) {
            List<Event> detachedOccurrences = ((PhantomMaster) event).getDetachedOccurrences();
            eventIDs = new ArrayList<EventID>(detachedOccurrences.size());
            for (Event detachedOccurrence : detachedOccurrences) {
                eventIDs.add(new EventID(detachedOccurrence.getFolderId(), detachedOccurrence.getId()));
            }
        } else {
            eventIDs = Collections.singletonList(new EventID(event.getFolderId(), event.getId()));
        }
        new CalendarAccessOperation<Void>(factory) {

            @Override
            protected Void perform(IDBasedCalendarAccess access) throws OXException {
                for (EventID id : eventIDs) {
                    access.deleteEvent(id, event.getTimestamp());
                }
                return null;
            }
        }.execute(factory.getSession());
    }

    private CalendarResult putEvents(CalDAVImport caldavImport) throws OXException {
        /*
         * add imported event(s) to calendar
         */
        if (null == caldavImport.getEvent() && caldavImport.getChangeExceptions().isEmpty()) {
            throw new PreconditionException(DAVProtocol.CAL_NS.getURI(), "valid-calendar-object-resource", url, SC_FORBIDDEN);
        }
        CalDAVImport patchedImport = EventPatches.Incoming(parent.getFactory()).applyAll(this, caldavImport);
        CalendarObjectResource resource = patchedImport.asCalendarObjectResource();
        return new CalendarAccessOperation<CalendarResult>(factory) {

            @Override
            protected CalendarResult perform(IDBasedCalendarAccess access) throws OXException {
                return access.putResource(parent.folderID, resource, true);
            }
        }.execute(factory.getSession());
    }

    /**
     * Gets an appropriate WebDAV protocol exception for the supplied OX exception.
     *
     * @param e The OX exception to get the protocol exception for
     * @return The protocol exception
     */
    private WebdavProtocolException getProtocolException(OXException e) {
        switch (e.getErrorCode()) {
            case "CAL-4038":
                return new PreconditionException(e, DAVProtocol.CAL_NS.getURI(), "allowed-attendee-scheduling-object-change", getUrl(), HttpServletResponse.SC_FORBIDDEN);
            case "CAL-4091":
            case "CAL-4092":
                LOG.info("{}: PUT operation failed due to non-ignorable conflicts.", getUrl());
                return new PreconditionException(e, DAVProtocol.CAL_NS.getURI(), "allowed-organizer-scheduling-object-change", getUrl(), HttpServletResponse.SC_FORBIDDEN);
            case "CAL-4120":
                return DAVProtocol.protocolException(getUrl(), e, HttpServletResponse.SC_PRECONDITION_FAILED);
            case "ICAL-0003":
            case "ICAL-0004":
            case "CAL-4221":
            case "CAL-4229":
                return new PreconditionException(e, DAVProtocol.CAL_NS.getURI(), "valid-calendar-data", getUrl(), HttpServletResponse.SC_FORBIDDEN);
            case "CAL-4063":
                return new PreconditionException(e, DAVProtocol.CALENDARSERVER_NS.getURI(), "invalid-split", getUrl(), HttpServletResponse.SC_FORBIDDEN);
            case "CAL-4222":
            case "CAL-4226":
            case "CAL-4227":
            case "CAL-42210":
                return new PreconditionException(e, DAVProtocol.CALENDARSERVER_NS.getURI(), "valid-access-restriction", getUrl(), HttpServletResponse.SC_FORBIDDEN);
            case "CAL-40310":
                return new PreconditionException(e, DAVProtocol.CAL_NS.getURI(), "same-organizer-in-all-components", getUrl(), HttpServletResponse.SC_FORBIDDEN);
            case "CAL-4090":
                return new PreconditionException(e, DAVProtocol.CAL_NS.getURI(), "no-uid-conflict", getUrl(), HttpServletResponse.SC_FORBIDDEN);
            default:
                return DAVProtocol.protocolException(getUrl(), e);
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(getUrl());
        if (null != object) {
            stringBuilder.append(" [").append(object).append(']');
        }
        if (null != caldavImport) {
            stringBuilder.append(" [").append(caldavImport).append(']');
        }
        return stringBuilder.toString();
    }

}
