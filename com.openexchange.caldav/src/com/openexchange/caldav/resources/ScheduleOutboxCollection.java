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

import static com.openexchange.dav.DAVProtocol.CAL_NS;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.jdom2.CDATA;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import com.openexchange.caldav.GroupwareCaldavFactory;
import com.openexchange.caldav.mixins.ScheduleOutboxURL;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.FreeBusyData;
import com.openexchange.chronos.FreeBusyTime;
import com.openexchange.chronos.ical.CalendarExport;
import com.openexchange.chronos.ical.ICalExceptionCodes;
import com.openexchange.chronos.ical.ICalParameters;
import com.openexchange.chronos.ical.ICalService;
import com.openexchange.chronos.ical.ImportedCalendar;
import com.openexchange.chronos.provider.composition.IDBasedCalendarAccess;
import com.openexchange.chronos.service.FreeBusyResult;
import com.openexchange.dav.DAVProtocol;
import com.openexchange.dav.resources.DAVCollection;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.BasicPermission;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.Permissions;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;
import com.openexchange.webdav.protocol.Protocol;
import com.openexchange.webdav.protocol.Protocol.Property;
import com.openexchange.webdav.protocol.WebdavPath;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;
import com.openexchange.webdav.protocol.helpers.AbstractResource;

/**
 * {@link ScheduleOutboxCollection} - A resource at which busy time
 * information requests are targeted.
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class ScheduleOutboxCollection extends DAVCollection {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ScheduleOutboxCollection.class);

    private final GroupwareCaldavFactory factory;

    private List<FreeBusyData> freeBusyRequest;

    /**
     * Initializes a new {@link ScheduleOutboxCollection}.
     *
     * @param factory The factory
     */
    public ScheduleOutboxCollection(GroupwareCaldavFactory factory) {
        super(factory, new WebdavPath(ScheduleOutboxURL.SCHEDULE_OUTBOX));
        this.factory = factory;
    }

    @Override
    public Permission[] getPermissions() {
        return new Permission[] {
            new BasicPermission(getFactory().getUser().getId(), false, Permissions.createPermissionBits(
                Permission.CREATE_OBJECTS_IN_FOLDER, Permission.READ_ALL_OBJECTS, Permission.WRITE_ALL_OBJECTS, Permission.DELETE_ALL_OBJECTS, false))
        };
    }

    @Override
    public String getResourceType() throws WebdavProtocolException {
        return super.getResourceType() + "<CAL:schedule-outbox />";
    }

    @Override
    public void putBody(InputStream body, boolean guessSize) throws WebdavProtocolException {
        freeBusyRequest = parseFreeBusyRequest(body);
    }

    @Override
    public boolean hasBody() {
        return true;
    }

    @Override
    public InputStream getBody() throws WebdavProtocolException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(getScheduleResponse());
        try {
            new XMLOutputter(Format.getPrettyFormat()).output(document, outputStream);
        } catch (IOException e) {
            throw WebdavProtocolException.Code.GENERAL_ERROR.create(getUrl(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private List<FreeBusyData> parseFreeBusyRequest(InputStream inputStream) throws WebdavProtocolException {
        try {
            ICalService iCalService = getFactory().requireService(ICalService.class);
            ICalParameters parameters = iCalService.initParameters();
            ImportedCalendar calendarImport = iCalService.importICal(inputStream, parameters);
            return calendarImport.getFreeBusyDatas();
        } catch (OXException e) {
            throw WebdavProtocolException.Code.GENERAL_ERROR.create(getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private Element getScheduleResponse() {
        Element scheduleResponse = new Element("schedule-response", CAL_NS);
        if (null != freeBusyRequest) {
            for (FreeBusyData freeBusyData : freeBusyRequest) {
                Map<Attendee, FreeBusyResult> freeBusyPerAttendee;
                try {
                    freeBusyPerAttendee = new CalendarAccessOperation<Map<Attendee, FreeBusyResult>>(factory) {

                        @Override
                        protected Map<Attendee, FreeBusyResult> perform(IDBasedCalendarAccess access) throws OXException {
                            return access.queryFreeBusy(freeBusyData.getAttendees(), new Date(freeBusyData.getStartDate().getTimestamp()), new Date(freeBusyData.getEndDate().getTimestamp()), true);
                        }
                    }.execute(factory.getSession());
                } catch (OXException e) {
                    LOG.error("error getting free/busy information", e);
                    continue;
                }
                for (Attendee attendee : freeBusyData.getAttendees()) {
                    scheduleResponse.addContent(getResponse(freeBusyData, attendee, freeBusyPerAttendee.get(attendee).getFreeBusyTimes()));
                }
            }
        }
        return scheduleResponse;
    }

    private Element getResponse(FreeBusyData requestedData, Attendee attendee, List<FreeBusyTime> freeBusyTimes) {
        Element response = new Element("response", CAL_NS);
        /*
         * prepare recipient
         */
        Element recipient = new Element("recipient", CAL_NS);
        response.addContent(recipient);
        Element href = new Element("href", Protocol.DAV_NS);
        href.addContent(attendee.getUri());
        recipient.addContent(href);
        /*
         * prepare request status
         */
        Element requestStatus = new Element("request-status", CAL_NS);
        response.addContent(requestStatus);
        if (null == freeBusyTimes) {
            /*
             * no info for this entity
             */
            requestStatus.addContent("3.7;Invalid calendar user");
        } else {
            /*
             * add freebusy info for this entity
             */
            Element calendarData = new Element("calendar-data", CAL_NS);
            response.addContent(calendarData);
            try {
                calendarData.addContent(new CDATA(getVFreeBusyReply(requestedData, attendee, freeBusyTimes)));
                requestStatus.addContent("2.0;Success");
            } catch (OXException e) {
                LOG.warn("error getting freebusy", e);
                requestStatus.addContent("5.1;Service unavailable");
            }
        }
        /*
         * add response description
         */
        Element responseDescription = new Element("responsedescription", Protocol.DAV_NS);
        responseDescription.addContent("OK");
        response.addContent(responseDescription);
        return response;
    }

    private String getVFreeBusyReply(FreeBusyData requestedData, Attendee attendee, List<FreeBusyTime> freeBusyTimes) throws OXException {
        /*
         * generate free busy data
         */
        FreeBusyData freeBusyData = new FreeBusyData();
        freeBusyData.setAttendees(Collections.singletonList(attendee));
        freeBusyData.setUid(requestedData.getUid());
        freeBusyData.setStartDate(requestedData.getStartDate());
        freeBusyData.setEndDate(requestedData.getEndDate());
        freeBusyData.setOrganizer(requestedData.getOrganizer());
        freeBusyData.setFreeBusyTimes(freeBusyTimes);
        /*
         * serialize as free/busy reply
         */
        InputStream inputStream = null;
        try {
            ICalService iCalService = getFactory().requireService(ICalService.class);
            ICalParameters parameters = iCalService.initParameters();
            CalendarExport calendarExport = iCalService.exportICal(parameters);
            calendarExport.setMethod("REPLY");
            calendarExport.add(freeBusyData);
            inputStream = calendarExport.getClosingStream();
            return Streams.stream2string(inputStream, Charsets.UTF_8_NAME);
        } catch (IOException e) {
            throw ICalExceptionCodes.IO_ERROR.create(e, e.getMessage());
        } finally {
            Streams.close(inputStream);
        }
    }

    @Override
    public String getContentType() throws WebdavProtocolException {
        return "text/xml; charset=UTF-8";
    }

    @Override
    public String getDisplayName() throws WebdavProtocolException {
        return "Schedule Outbox";
    }

    @Override
    protected boolean isset(Property p) {
        return true;
    }

    @Override
    public void delete() throws WebdavProtocolException {
        throw DAVProtocol.protocolException(getUrl(), HttpServletResponse.SC_FORBIDDEN);
    }

    @Override
    public void setLanguage(String language) throws WebdavProtocolException {
        // no-op
    }

    @Override
    public void setLength(Long length) throws WebdavProtocolException {
        // no-op
    }

    @Override
    public void setContentType(String type) throws WebdavProtocolException {
        // no-op
    }

    @Override
    public List<WebdavResource> getChildren() throws WebdavProtocolException {
        return Collections.emptyList();
    }

    @Override
    public Date getCreationDate() throws WebdavProtocolException {
        return null;
    }

    @Override
    public Date getLastModified() throws WebdavProtocolException {
        return null;
    }

    @Override
    public String getSyncToken() throws WebdavProtocolException {
        return "0";
    }

    @Override
    public AbstractResource getChild(String name) throws WebdavProtocolException {
        return null;
    }

}
