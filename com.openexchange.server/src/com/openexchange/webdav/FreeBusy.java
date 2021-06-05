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

package com.openexchange.webdav;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.dmfs.rfc5545.DateTime;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.FbType;
import com.openexchange.chronos.FreeBusyData;
import com.openexchange.chronos.FreeBusyTime;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.ical.CalendarExport;
import com.openexchange.chronos.ical.ICalService;
import com.openexchange.chronos.service.AdministrativeFreeBusyService;
import com.openexchange.chronos.service.FreeBusyResult;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.server.ServiceLookup;

/**
 * Servlet for requesting free busy data of a user.
 *
 * @author <a href="mailto:anna.ottersbach@open-xchange.com">Anna Ottersbach</a>
 * @since v7.10.4
 */
public class FreeBusy extends HttpServlet {

    private static final long serialVersionUID = 3320864434477270604L;

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FreeBusy.class);

    private static final int DEFAULT_WEEKS_PAST = 1;
    private static final int DEFAULT_WEEKS_FUTURE = 4;

    private static final String PARAMETER_CONTEXTID = "contextId";
    private static final String PARAMETER_SERVER = "server";
    private static final String PARAMETER_SIMPLE = "simple";
    private static final String PARAMETER_USERNAME = "userName";
    private static final String PARAMETER_WEEKS_INTO_FUTURE = "weeksIntoFuture";
    private static final String PARAMETER_WEEKS_INTO_PAST = "weeksIntoPast";

    private final transient ServiceLookup serviceLookup;

    /**
     * Initializes a new {@link FreeBusy}.
     *
     * @param service The service look-up to use
     */
    public FreeBusy(ServiceLookup service) {
        this.serviceLookup = service;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        try {
            int contextId = readContextId(request);
            String userMail = readMailAddress(request);
            if (isFreeBusyPublished(-1, contextId) == false) {
                LOGGER.debug("Free busy data is not published for in context {}.", I(contextId));
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            List<Attendee> inputAttendeeList = getAttendeeListWithUser(userMail);

            Date start = readStart(request, contextId);
            Date end = readEnd(request, contextId);

            AdministrativeFreeBusyService service = serviceLookup.getServiceSafe(AdministrativeFreeBusyService.class);
            Map<Attendee, FreeBusyResult> freeBusyResponse = service.getFreeBusy(contextId, inputAttendeeList, start, end, false);

            validateResponse(freeBusyResponse);
            Attendee outputAttendee = freeBusyResponse.keySet().stream().findFirst().get();

            if (CalendarUtils.isInternalUser(outputAttendee) && isFreeBusyPublished(outputAttendee.getEntity(), contextId) == false) {
                LOGGER.debug("Free busy data is not published for user {} in context {}.", userMail, I(contextId));
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            FreeBusyResult freeBusyResult = freeBusyResponse.get(outputAttendee);

            writeICalendar(response, freeBusyResult, outputAttendee, start, end, isSimple(request));
        } catch (OXException e) {
            LOGGER.debug(e.getMessage(), e);
            if (FreeBusyExceptionCode.INVALID_PARAMETER.equals(e)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            if (FreeBusyExceptionCode.MISSING_PARAMETER.equals(e) || FreeBusyExceptionCode.USER_NOT_FOUND.equals(e) ||
                CalendarExceptionCodes.INVALID_CALENDAR_USER.equals(e) || com.openexchange.groupware.contexts.impl.ContextExceptionCodes.NOT_FOUND.equals(e)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     *
     * Creates a list including an attendee with the mail address.
     *
     * @param userMail The userMail of the attendee.
     * @return A list with an attendee.
     */
    private List<Attendee> getAttendeeListWithUser(String userMail) {
        Attendee attendee = new Attendee();
        attendee.setUri(CalendarUtils.getURI(userMail));
        List<Attendee> attendeeList = new ArrayList<Attendee>();
        attendeeList.add(attendee);
        return attendeeList;
    }

    /**
     *
     * Reads the simple parameter from HTTP request.
     *
     * @param request The HTTP request.
     * @return <code>true</code> if the value is set to <code>true</code> in HTTP request, <code>false</code> otherwise.
     */
    private boolean isSimple(HttpServletRequest request) {
        final String simpleValue = request.getParameter(PARAMETER_SIMPLE);
        return Boolean.parseBoolean(simpleValue);
    }

    /**
     *
     * Checks if the free busy data is published for users the requested context and optional user.
     *
     * @param userId The user identifier, or <code>-1</code> to check the context only
     * @param contextId The context id.
     * @return <code>true</code> if the data is published, <code>false</code> otherwise.
     * @throws OXException If LeanConfigurationService is not available.
     */
    private boolean isFreeBusyPublished(int userId, int contextId) throws OXException {
        LeanConfigurationService leanConfigService = serviceLookup.getServiceSafe(LeanConfigurationService.class);
        return leanConfigService.getBooleanProperty(userId, contextId, FreeBusyProperty.PUBLISH_INTERNET_FREEBUSY);
    }

    /**
     *
     * Reads the context id from the HTTP request.
     *
     * @param request The HTTP request.
     * @return The context id.
     * @throws OXException If the parameter is missing in HTTP request of if the value is not a number, or the context doesn't exist
     */
    private int readContextId(HttpServletRequest request) throws OXException {
        String parameter = request.getParameter(PARAMETER_CONTEXTID);
        if (parameter == null) {
            throw FreeBusyExceptionCode.MISSING_PARAMETER.create(PARAMETER_CONTEXTID);
        }
        try {
            int contextId = Integer.parseInt(parameter);
            return serviceLookup.getServiceSafe(ContextService.class).getContext(contextId).getContextId();
        } catch (NumberFormatException e) {
            throw FreeBusyExceptionCode.INVALID_PARAMETER.create(e, PARAMETER_CONTEXTID, e.getMessage());
        }
    }

    /**
     *
     * Reads the weeks into future parameter from HTTP request and checks if the value is valid.
     *
     * @param request The HTTP request.
     * @param contextId The context id of the requested user.
     * @return The end date of the requested free busy data.
     * @throws OXException If the parameter is not a number, if the value is invalid or if the LeanConfigurationService is not available.
     */
    private Date readEnd(HttpServletRequest request, int contextId) throws OXException {
        String futureParameter = request.getParameter(PARAMETER_WEEKS_INTO_FUTURE);
        if (null == futureParameter) {
            return CalendarUtils.add(new Date(), Calendar.WEEK_OF_YEAR, DEFAULT_WEEKS_FUTURE);
        }

        int weeksFuture;
        try {
            weeksFuture = Integer.parseInt(futureParameter);
        } catch (NumberFormatException e) {
            throw FreeBusyExceptionCode.INVALID_PARAMETER.create(e, PARAMETER_WEEKS_INTO_FUTURE, "Not a valid number");
        }
        if (weeksFuture < 0) {
            throw FreeBusyExceptionCode.INVALID_PARAMETER.create(PARAMETER_WEEKS_INTO_FUTURE, "Value cannot be negative");
        }

        LeanConfigurationService leanConfigService = serviceLookup.getServiceSafe(LeanConfigurationService.class);
        int maxTimeRangeFuture = leanConfigService.getIntProperty(-1, contextId, FreeBusyProperty.INTERNET_FREEBUSY_MAXIMUM_TIMERANGE_FUTURE);
        if (maxTimeRangeFuture >= 0 && weeksFuture > maxTimeRangeFuture) {
            /*
             * In case the parameter is greater than the configured maximum,
             * only the time range to configured maximum will be requested (MWB-384)
             */
            return CalendarUtils.add(new Date(), Calendar.WEEK_OF_YEAR, maxTimeRangeFuture);
        }
        return CalendarUtils.add(new Date(), Calendar.WEEK_OF_YEAR, weeksFuture);
    }

    /**
     *
     * Reads the mail address from HTTP request.
     *
     * @param request The HTTP request.
     * @return The mail address of the requested user.
     * @throws OXException If parameter <code>userName</code> or <code>server</code> is missing.
     */
    private String readMailAddress(HttpServletRequest request) throws OXException {
        final String userName = request.getParameter(PARAMETER_USERNAME);
        if (Strings.isEmpty(userName)) {
            throw FreeBusyExceptionCode.MISSING_PARAMETER.create(PARAMETER_USERNAME);
        }
        final String serverName = request.getParameter(PARAMETER_SERVER);
        if (Strings.isEmpty(serverName)) {
            throw FreeBusyExceptionCode.MISSING_PARAMETER.create(PARAMETER_SERVER);
        }
        return userName + '@' + serverName;
    }

    /**
     *
     * Reads the weeks into past parameter from HTTP request and checks if the value is valid.
     *
     * @param request The HTTP request.
     * @param contextId The context id of the requested user.
     * @return The start date of the requested free busy data.
     * @throws OXException If the parameter is not a number, if the value is invalid or if the LeanConfigurationService is not available.
     */
    private Date readStart(HttpServletRequest request, int contextId) throws OXException {
        String pastParameter = request.getParameter(PARAMETER_WEEKS_INTO_PAST);
        if (null == pastParameter) {
            return CalendarUtils.add(new Date(), Calendar.WEEK_OF_YEAR, -DEFAULT_WEEKS_PAST);
        }

        int weeksPast;
        try {
            weeksPast = Integer.parseInt(pastParameter);
        } catch (NumberFormatException e) {
            throw FreeBusyExceptionCode.INVALID_PARAMETER.create(e, PARAMETER_WEEKS_INTO_PAST, "Not a valid number");
        }
        if (weeksPast < 0) {
            throw FreeBusyExceptionCode.INVALID_PARAMETER.create(PARAMETER_WEEKS_INTO_PAST, "Value cannot be negative");
        }

        LeanConfigurationService leanConfigService = serviceLookup.getServiceSafe(LeanConfigurationService.class);
        int maxTimeRangePast = leanConfigService.getIntProperty(-1, contextId, FreeBusyProperty.INTERNET_FREEBUSY_MAXIMUM_TIMERANGE_PAST);
        if (maxTimeRangePast >= 0 && weeksPast > maxTimeRangePast) {
            /*
             * In case the parameter is greater than the configured maximum,
             * only the time range to configured maximum will be requested (MWB-384)
             */
            return CalendarUtils.add(new Date(), Calendar.WEEK_OF_YEAR, -maxTimeRangePast);
        }
        return CalendarUtils.add(new Date(), Calendar.WEEK_OF_YEAR, -weeksPast);
    }

    /**
     *
     * Validates if the free busy response fulfills the following conditions:
     * response is not null, response contains exactly one entry, FreeBusyResult contains no warnings.
     * Otherwise an OXException is thrown.
     *
     * @param freeBusyResponse The response from AdministrativeFreeBusyService.
     * @throws OXException If one condition is not fulfilled.
     */
    private void validateResponse(Map<Attendee, FreeBusyResult> freeBusyResponse) throws OXException {
        if (freeBusyResponse == null || freeBusyResponse.size() != 1) {
            throw FreeBusyExceptionCode.UNEXPECTED_ERROR.create("The free-busy response is invalid");
        }
        for (FreeBusyResult result : freeBusyResponse.values()) {
            if (result.getWarnings() != null) {
                for (OXException exception : result.getWarnings()) {
                    throw exception;
                }
            }
        }
    }

    /**
     *
     * Writes the given data to the response in iCalendar format.
     *
     * @param response The HTTP response.
     * @param freeBusyResult The freeBusyResult with free busy data.
     * @param attendee The requested user.
     * @param from The beginning of the requested data.
     * @param until The end of the requested data.
     * @param simple <code>true</code>, if the VFREEBUSY data should not contain free busy type and free information, <code>false</code> otherwise.
     * @throws OXException If problems occur with calendar export or if ICalSerive is not available.
     * @throws IOException If the output stream cannot be read from the response.
     */
    private void writeICalendar(HttpServletResponse response, FreeBusyResult freeBusyResult, Attendee attendee, Date from, Date until, boolean simple) throws OXException, IOException {
        response.setContentType("text/calendar; charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Content-Disposition", "attachment;filename=\"freebusy.vfb\"");

        FreeBusyData freeBusyData = new FreeBusyData();
        List<Attendee> attendeeList = new ArrayList<>();
        attendeeList.add(attendee);
        freeBusyData.setAttendees(attendeeList);
        freeBusyData.setStartDate(new DateTime(from.getTime()));
        freeBusyData.setEndDate(new DateTime(until.getTime()));
        List<FreeBusyTime> fbTimes = new ArrayList<FreeBusyTime>();
        if (simple) {
            for (FreeBusyTime time : freeBusyResult.getFreeBusyTimes()) {
                if (FbType.FREE.equals(time.getFbType()) == false) {
                    FreeBusyTime newFreeBusy = new FreeBusyTime();
                    newFreeBusy.setStartTime(time.getStartTime());
                    newFreeBusy.setEndTime(time.getEndTime());
                    fbTimes.add(newFreeBusy);
                }
            }
        } else {
            fbTimes = freeBusyResult.getFreeBusyTimes();
        }
        freeBusyData.setFreeBusyTimes(fbTimes);
        freeBusyData.setTimestamp(new Date());

        ServletOutputStream outputStream = response.getOutputStream();
        ICalService icalService = serviceLookup.getServiceSafe(ICalService.class);
        CalendarExport exportICal = icalService.exportICal(icalService.initParameters());
        exportICal.add(freeBusyData);
        exportICal.writeVCalendar(outputStream);
    }
}