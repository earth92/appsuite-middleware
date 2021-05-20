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
 *    trademarks of the OX Software GmbH. group of companies.
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

package com.openexchange.ajax.chronos.itip;

import static com.openexchange.java.Autoboxing.I;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javax.jms.IllegalStateException;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.api.services.calendar.model.Event.Organizer;
import com.openexchange.ajax.chronos.factory.AttendeeFactory;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.ical.ImportedCalendar;
import com.openexchange.chronos.ical.ical4j.mapping.ICalMapper;
import com.openexchange.chronos.ical.impl.ICalUtils;
import com.openexchange.chronos.itip.Messages;
import com.openexchange.chronos.scheduling.SchedulingMethod;
import com.openexchange.exception.OXException;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.test.common.test.pool.TestUser;
import com.openexchange.testing.httpclient.invoker.ApiClient;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.Attendee;
import com.openexchange.testing.httpclient.models.Attendee.CuTypeEnum;
import com.openexchange.testing.httpclient.models.AttendeeAndAlarm;
import com.openexchange.testing.httpclient.models.CommonResponse;
import com.openexchange.testing.httpclient.models.ConversionDataSource;
import com.openexchange.testing.httpclient.models.EventData;
import com.openexchange.testing.httpclient.models.JSlobData;
import com.openexchange.testing.httpclient.models.JSlobsResponse;
import com.openexchange.testing.httpclient.models.MailAttachment;
import com.openexchange.testing.httpclient.models.MailData;
import com.openexchange.testing.httpclient.models.MailDestinationData;
import com.openexchange.testing.httpclient.models.MailImportResponse;
import com.openexchange.testing.httpclient.models.MailResponse;
import com.openexchange.testing.httpclient.models.MailsResponse;
import com.openexchange.testing.httpclient.modules.JSlobApi;
import com.openexchange.testing.httpclient.modules.MailApi;
import net.fortuna.ical4j.util.CompatibilityHints;

/**
 * {@link ITipUtil}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.3
 */
public class ITipUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ITipUtil.class);

    private static final String NOTIFY_ACCEPTED_DECLINED_AS_CREATOR = "notifyAcceptedDeclinedAsCreator";
    private static final String NOTIFY_ACCEPTED_DECLINED_AS_PARTICIPANT = "notifyAcceptedDeclinedAsParticipant";
    private static final String NOTIFY_NEW_MODIFIED_DELETED = "notifyNewModifiedDeleted";
    private static final String DELETE_INVITATION_MAIL_AFTER_ACTION = "deleteInvitationMailAfterAction";

    /** Machine readable folder name of the INBOX */
    public static final String FOLDER_MACHINE_READABLE = "default0%2FINBOX";
    /** Human readable folder name of the INBOX */
    public static final String FOLDER_HUMAN_READABLE = "default0/INBOX";

    /**
     * Initializes a new {@link ITipUtil}.
     */
    private ITipUtil() {}

    /**
     * Uploads a mail to the INBOX
     *
     * @param apiClient The {@link ApiClient}
     * @param eml The mail to upload
     * @return {@link MailDestinationData} with set mail ID and folder ID
     * @throws Exception In case of error
     */
    public static MailDestinationData createMailInInbox(ApiClient apiClient, String eml) throws Exception {
        File tmpFile = File.createTempFile("test", ".eml");
        FileWriterWithEncoding writer = new FileWriterWithEncoding(tmpFile, "ASCII");
        writer.write(eml);
        writer.close();

        MailApi mailApi = new MailApi(apiClient);
        MailImportResponse importMail = mailApi.importMail(FOLDER_MACHINE_READABLE, tmpFile, null, Boolean.TRUE);
        return importMail.getData().get(0);
    }

    /**
     * Converts a test user to an attendee
     *
     * @param convertee The user to convert
     * @param userId The user identifier
     * @return An {@link Attendee}
     */
    public static Attendee convertToAttendee(TestUser convertee, Integer userId) {
        Attendee attendee = AttendeeFactory.createAttendee(userId, CuTypeEnum.INDIVIDUAL);
        attendee.cn(convertee.getUser());
        attendee.email(convertee.getLogin());
        attendee.setUri("mailto:" + convertee.getLogin());
        return attendee;
    }

    public static Organizer convertToOrganizer(Attendee attendee) {
        Organizer organizer = new Organizer();
        organizer.setDisplayName(attendee.getCn());
        organizer.setEmail(attendee.getEmail());
        organizer.setId(attendee.getEntity().toString());
        return organizer;
    }

    /**
     * Constructs a body
     *
     * @param mailId The mail identifier
     * @return A {@link ConversionDataSource} body
     */
    public static ConversionDataSource constructBody(String mailId) {
        return constructBody(mailId, "1.3");
    }

    /**
     * Constructs a body
     *
     * @param mailId The mail identifier
     * @param sequenceId The identifier of the attachment sequence
     * @return A {@link ConversionDataSource} body
     */
    public static ConversionDataSource constructBody(String mailId, String sequenceId) {
        return constructBody(mailId, sequenceId, FOLDER_HUMAN_READABLE);
    }

    /**
     * Constructs a body
     *
     * @param mailData The {@link MailData}
     * @return A {@link ConversionDataSource} body
     * @throws Exception
     */
    public static ConversionDataSource constructBody(MailData mailData) throws Exception {
        assertNotNull(mailData);
        return constructBody(mailData.getId(), extractITipAttachmentId(mailData, null), mailData.getFolderId());
    }

    /**
     * Constructs a body
     *
     * @param mailId The mail identifier
     * @param sequenceId The identifier of the attachment sequence
     * @param folderName The folder name of the mail
     * @return A {@link ConversionDataSource} body
     */
    public static ConversionDataSource constructBody(String mailId, String sequenceId, String folderName) {
        ConversionDataSource body = new ConversionDataSource();
        body.setComOpenexchangeMailConversionFullname(folderName);
        body.setComOpenexchangeMailConversionMailid(mailId);
        body.setComOpenexchangeMailConversionSequenceid(sequenceId);
        return body;
    }

    /**
     * Constructs the mail subject of an iMIP message where the attendee
     * has accepted an event (series)
     * <p>
     * <code>anton accepted the invitation: Foo</code>
     *
     * @param from The attendee replying
     * @param summary The summary of the event
     * @return The mail subject
     */
    public static String acceptSummary(String from, String summary) {
        return constructActionSummary("accepted", from, summary);
    }

    /**
     * Constructs the mail subject of an iMIP message where the attendee
     * has tentatively accepted an event (series)
     * <p>
     * <code>anton tentatively accepted the invitation: Foo</code>
     *
     * @param from The attendee replying
     * @param summary The summary of the event
     * @return The mail subject
     */
    public static String tentativeSummary(String from, String summary) {
        return constructActionSummary("tentatively accepted", from, summary);
    }

    /**
     * Constructs the mail subject of an iMIP message where the attendee
     * has declined an event (series)
     * <p>
     * <code>anton declined the invitation: Foo</code>
     *
     * @param from The attendee replying
     * @param summary The summary of the event
     * @return The mail subject
     */
    public static String declineSummary(String from, String summary) {
        return constructActionSummary("declined", from, summary);
    }

    private static String constructActionSummary(String action, String from, String summary) {
        return String.format(Messages.SUBJECT_STATE_CHANGED, from, action, summary);
    }

    /**
     * Constructs the mail subject of an iMIP message where the
     * event generically has changed
     *
     * @param summary The summary of the event
     * @return The mail subject
     */
    public static String changedSummary(String summary) {
        return String.format(Messages.SUBJECT_CHANGED_APPOINTMENT, summary);
    }

    /**
     * Receive a calendar notification from the inbox
     *
     * @param apiClient The {@link ApiClient} to use
     * @param fromToMatch The mail of the originator of the message
     * @param subjectToMatch The summary of the event
     * @return The mail as {@link MailData}
     * @throws Exception If the mail can't be found or something mismatches
     */
    public static MailData receiveNotification(ApiClient apiClient, String fromToMatch, String subjectToMatch) throws Exception {
        return receiveIMip(apiClient, fromToMatch, subjectToMatch, -1, null);
    }

    /**
     * Receive the iMIP message from the inbox
     *
     * @param apiClient The {@link ApiClient} to use
     * @param fromToMatch The mail of the originator of the message
     * @param subjectToMatch The summary of the event
     * @param sequenceToMatch The sequence identifier of event to match, or <code>-1</code> if not applicable
     * @param method The iTIP method that the mail must contain, or <code>null</code> to skip checking for the event data
     * @return The mail as {@link MailData}
     * @throws Exception If the mail can't be found or something mismatches
     */
    public static MailData receiveIMip(ApiClient apiClient, String fromToMatch, String subjectToMatch, int sequenceToMatch, SchedulingMethod method) throws Exception {
        return receiveIMip(apiClient, fromToMatch, subjectToMatch, sequenceToMatch, null, method);
    }

    /**
     * Receive the iMIP message from the inbox
     *
     * @param apiClient The {@link ApiClient} to use
     * @param fromToMatch The mail of the originator of the message
     * @param subjectToMatch The summary of the event
     * @param sequenceToMatch The sequence identifier of the first event to match, or <code>-1</code> if not applicable
     * @param uidToMatch The UID of the calendar object resource to match, or <code>null</code> if not applicable
     * @param method The iTIP method that the mail must contain, or <code>null</code> to skip checking for the event data
     * @return The mail as {@link MailData}
     * @throws Exception If the mail can't be found or something mismatches
     */
    public static MailData receiveIMip(ApiClient apiClient, String fromToMatch, String subjectToMatch, int sequenceToMatch, String uidToMatch, SchedulingMethod method) throws Exception {
        for (int i = 0; i < 10; i++) {
            MailData mailData = lookupMail(apiClient, FOLDER_MACHINE_READABLE, fromToMatch, subjectToMatch, sequenceToMatch, uidToMatch, method);
            if (null != mailData) {
                return mailData;
            }
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        }
        throw new AssertionError("No mail with " + subjectToMatch + " from " + fromToMatch + " received");
    }

    private static MailData lookupMail(ApiClient apiClient, String folder, String fromToMatch, String subjectToMatch, int sequenceToMatch, String uidToMatch, SchedulingMethod method) throws Exception {
        MailApi mailApi = new MailApi(apiClient);
        MailsResponse mailsResponse = mailApi.getAllMails(folder, "600,601,607,610", null, null, null, "610", "desc", null, null, I(10), null);
        assertNull(mailsResponse.getError(), mailsResponse.getError());
        assertNotNull(mailsResponse.getData());
        for (List<String> mail : mailsResponse.getData()) {
            String subject = mail.get(2);
            if (Strings.isEmpty(subject) || false == subject.contains(subjectToMatch)) {
                continue;
            }
            MailResponse mailResponse = mailApi.getMail(mail.get(1), mail.get(0), null, null, "noimg", Boolean.FALSE, Boolean.TRUE, null, null, null, null, null, null, null);
            assertNull(mailResponse.getError(), mailsResponse.getError());
            assertNotNull(mailResponse.getData());
            MailData mailData = mailResponse.getData();
            if (null == extractMatchingAddress(mailData.getFrom(), fromToMatch)) {
                continue;
            }
            if (null == method) {
                return mailData;
            }
            ImportedCalendar calendar = parseICalAttachment(apiClient, mailData, method);
            if (null == calendar) {
                continue;
            }
            if (null == extractMatchingEvent(calendar.getEvents(), sequenceToMatch, uidToMatch)) {
                continue;
            }
            return mailData;
        }
        return null;
    }

    private static Event extractMatchingEvent(List<Event> events, int sequence, String uidToMatch) {
        if (null != events) {
            for (Event event : events) {
                if (-1 != sequence && event.getSequence() != sequence) {
                    continue;
                }
                if (null != uidToMatch && false == uidToMatch.equals(event.getUid())) {
                    continue;
                }
                return event;
            }
        }
        return null;
    }

    private static List<String> extractMatchingAddress(List<List<String>> addresses, String email) {
        if (null != addresses) {
            for (List<String> address : addresses) {
                assertEquals(2, address.size());
                if (null != address.get(1) && address.get(1).contains(email)) {
                    return address;
                }
            }
        }
        return null;
    }

    private static String extractITipAttachmentId(MailData mailData, SchedulingMethod expectedMethod) throws OXException {
        assertNotNull(mailData.getAttachments());
        for (MailAttachment attachment : mailData.getAttachments()) {
            if (ContentType.isMimeType(attachment.getContentType(), "text/calendar")) {
                if (null != expectedMethod && false == attachment.getContentType().contains(expectedMethod.name())) {
                    continue;
                }
                return attachment.getId();
            }
        }
        throw new AssertionError("no itip attachment found");
    }

    /**
     * Checks that there is no REPLY mail received from given attendee
     *
     * @param client The client to use
     * @param replyingAttendee The attendee that replies
     * @param summary The summary
     * @throws Exception Error while fetching mail
     */
    public static void checkNoReplyMailReceived(ApiClient client, Attendee replyingAttendee, String summary) throws Exception {
        Error error = null;
        try {
            MailData mail = receiveIMip(client, replyingAttendee.getEmail(), summary, 1, SchedulingMethod.REPLY);
            LOGGER.error("Found reply mail: {}", mail.toString());
        } catch (AssertionError ae) {
            error = ae;
        }
        Assert.assertNotNull("Excpected an error", error);
    }

    /**
     * Extracts the iCalendar file from the given mail into an readable object
     *
     * @param apiClient The {@link ApiClient} to receive the iCAlndar file with
     * @param mailData The {@link MailData} to get the attachment from
     * @return The calendar information from the file as {@link ImportedCalendar}
     * @throws Exception In case of error
     */
    public static ImportedCalendar parseICalAttachment(ApiClient apiClient, MailData mailData) throws Exception {
        return parseICalAttachment(apiClient, mailData.getFolderId(), mailData.getId(), extractITipAttachmentId(mailData, null));
    }

    /**
     * Extracts the iCalendar file from the given mail into an readable object
     *
     * @param apiClient The {@link ApiClient} to receive the iCAlndar file with
     * @param mailData The {@link MailData} to get the attachment from
     * @param expectedMethod The expected iTIP method
     * @return The calendar information from the file as {@link ImportedCalendar}
     * @throws Exception In case of error
     */
    public static ImportedCalendar parseICalAttachment(ApiClient apiClient, MailData mailData, SchedulingMethod expectedMethod) throws Exception {
        return parseICalAttachment(apiClient, mailData.getFolderId(), mailData.getId(), extractITipAttachmentId(mailData, expectedMethod));
    }

    private static ImportedCalendar parseICalAttachment(ApiClient apiClient, String folder, String id, String attachmentId) throws Exception {
        MailApi mailApi = new MailApi(apiClient);
        byte[] attachment = mailApi.getMailAttachment(folder, id, attachmentId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
        return ICalUtils.importCalendar(Streams.newByteArrayInputStream(attachment), new ICalMapper(), null);
    }

    /**
     *
     * Prepares a JSON object for the upload of an JPG attachment with the chronos API.
     *
     * @param id The event id
     * @param folder The folder of the event
     * @param fileName The file name
     * @return A JSON as {@link String}
     * @throws Exception
     */
    public static String prepareJsonForFileUpload(String id, String folder, String fileName) throws Exception {
        return prepareJsonForFileUpload(id, folder, fileName, "image/jpeg");
    }

    /**
     *
     * Prepares a JSON object for the upload of an event.
     *
     * @param id The event id
     * @param folder The folder of the event
     * @param fileName The file name
     * @param fileType The file type
     * @return A JSON as {@link String}
     * @throws Exception
     */
    public static String prepareJsonForFileUpload(String id, String folder, String fileName, String fileType) throws Exception {
        JSONObject json = new JSONObject();
        JSONObject event = new JSONObject();

        event.put("id", id);
        event.put("folder", folder);
        event.put("timestamp", Long.valueOf(System.currentTimeMillis()));

        JSONArray array = new JSONArray();
        JSONObject attachment = new JSONObject();
        attachment.put("filename", fileName);
        attachment.put("fmtType", fileType);
        attachment.put("uri", "cid:file_0");
        array.add(0, attachment);

        event.put("attachments", array);
        json.put("event", event);

        return json.toString();
    }

    /**
     * prepares the given attendee with the given participant status for an update via the updateAttendee action
     *
     * @param event The event to get the attendee from
     * @param mailAddress The mail address of the desired attendee
     * @param participantStatus The participant status to set
     * @param comment The comment to set to the updated attendee
     * @return The attendee prepared for a update vie updateAttendee action
     * @throws IllegalStateException If the attendee can't be found
     */
    public static AttendeeAndAlarm prepareForAttendeeUpdate(EventData event, String mailAddress, String participantStatus, String comment) throws IllegalStateException {
        Optional<Attendee> matchingAttendee = event.getAttendees().stream().filter(a -> a.getEmail().equals(mailAddress)).findFirst();
        Attendee originalAttendee = matchingAttendee.orElseThrow(() -> new IllegalStateException("Attendee not found"));

        Attendee attendee = copyAtttendee(originalAttendee);
        attendee.setPartStat(participantStatus);
        attendee.setMember(null); // Hack to avoid this being recognized as change
        attendee.setComment(comment);

        AttendeeAndAlarm attendeeAndAlarm = new AttendeeAndAlarm();
        attendeeAndAlarm.attendee(attendee);
        return attendeeAndAlarm;
    }

    /**
     * Copies all values from the original attendee to a new attendee object
     *
     * @param originalAttendee The attendee to copy
     * @return A new Attendee object with the same values
     */
    public static Attendee copyAtttendee(Attendee originalAttendee) {
        Attendee attendee = new Attendee();
        attendee.cn(originalAttendee.getCn());
        attendee.comment(originalAttendee.getComment());
        attendee.email(originalAttendee.getEmail());
        attendee.setUri(originalAttendee.getUri());
        attendee.setEntity(originalAttendee.getEntity());
        attendee.setPartStat(originalAttendee.getPartStat());
        attendee.setMember(originalAttendee.getMember());
        return attendee;
    }

    /**
     * Changes the calendar settings
     *
     * @param jslobApi The API client to use
     * @param notifyAcceptedDeclinedAsCreator <code>true</code> to receive notifications for participant changes as <b>ORGANIZER</b>
     * @param notifyAcceptedDeclinedAsParticipant <code>true</code> to receive notifications for participant changes as <b>ATTENDEE</b>
     * @param notifyNewModifiedDeleted <code>true</code> to receive notifications for new or deleted events
     * @param deleteInvitationMailAfterAction <code>true</code> to delete iMIP or notification mails after processing
     * @throws JSONException In case of error
     * @throws ApiException In case settings can't be set
     */
    public static void changeCalendarSettings(JSlobApi jslobApi, boolean notifyAcceptedDeclinedAsCreator, boolean notifyAcceptedDeclinedAsParticipant, boolean notifyNewModifiedDeleted, boolean deleteInvitationMailAfterAction) throws JSONException, ApiException {
        JSONObject jsonObject = new JSONObject(2);
        jsonObject.put(NOTIFY_ACCEPTED_DECLINED_AS_CREATOR, Boolean.toString(notifyAcceptedDeclinedAsCreator));
        jsonObject.put(NOTIFY_ACCEPTED_DECLINED_AS_PARTICIPANT, Boolean.toString(notifyAcceptedDeclinedAsParticipant));
        jsonObject.put(NOTIFY_NEW_MODIFIED_DELETED, Boolean.toString(notifyNewModifiedDeleted));
        jsonObject.put(DELETE_INVITATION_MAIL_AFTER_ACTION, Boolean.toString(deleteInvitationMailAfterAction));
        CommonResponse response = jslobApi.setJSlob(jsonObject, "io.ox/calendar", null);
        assertNotNull("Response missing!", response);
        assertNull(response.getError());
    }

    /**
     * Changes the calendar settings
     *
     * @param jslobApi The API client to use
     * @param values The original values
     * @throws JSONException In case of error
     * @throws ApiException In case settings can't be set
     */
    public static void restoreCalendarSettings(JSlobApi jslobApi, Map<Object, Object> values) throws JSONException, ApiException {
        JSONObject jsonObject = new JSONObject(2);
        jsonObject.put(NOTIFY_ACCEPTED_DECLINED_AS_CREATOR, values.get(NOTIFY_ACCEPTED_DECLINED_AS_CREATOR).toString());
        jsonObject.put(NOTIFY_ACCEPTED_DECLINED_AS_PARTICIPANT, values.get(NOTIFY_ACCEPTED_DECLINED_AS_PARTICIPANT).toString());
        jsonObject.put(NOTIFY_NEW_MODIFIED_DELETED, values.get(NOTIFY_NEW_MODIFIED_DELETED).toString());
        jsonObject.put(DELETE_INVITATION_MAIL_AFTER_ACTION, values.get(DELETE_INVITATION_MAIL_AFTER_ACTION));
        CommonResponse response = jslobApi.setJSlob(jsonObject, "io.ox/calendar", null);
        assertNotNull("Response missing!", response);
        assertNull(response.getError());
    }

    /**
     * Get the JSLob for <code>io.ox/calendar</code>
     *
     * @param jSlobApi The API client to use
     * @return The JSLob as {@link Map}
     * @throws ApiException In case of error
     */
    @SuppressWarnings("unchecked")
    public static Map<Object, Object> getJSLoabForCalendar(JSlobApi jSlobApi) throws ApiException {
        JSlobsResponse jSlobsResponse = jSlobApi.getJSlobList(Collections.singletonList("io.ox/calendar"), null);
        assertNotNull(jSlobsResponse);
        assertThat("No error expected!", jSlobsResponse.getError(), nullValue());
        JSlobData data = jSlobsResponse.getData().get(0);
        assertNotNull(data);
        return ((Map<Object, Object>) data.getTree());
    }

}
