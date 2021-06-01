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

package com.openexchange.chronos.itip.generators;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.TimeZone;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.itip.ChronosTestTools;
import com.openexchange.chronos.itip.ITipIntegrationUtility;
import com.openexchange.chronos.itip.ITipRole;
import com.openexchange.chronos.itip.osgi.Services;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextImpl;
import com.openexchange.groupware.ldap.MockUser;
import com.openexchange.mail.usersetting.UserSettingMailStorage;
import com.openexchange.resource.ResourceService;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link DefaultNotificationParticipantResolverTest}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.0
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Services.class, UserSettingMailStorage.class, CalendarUtils.class })
public class DefaultNotificationParticipantResolverTest {

    private static final int CONTEXT_ID = 999999;

    // -------- MOCK OBJECTS --------
    @Mock
    private ServiceLookup services;

    @Mock
    private ITipIntegrationUtility util;

    @Mock
    private UserService userService;

    @Mock
    private ConfigurationService config;

    @Mock
    private ResourceService resources;

    @Mock
    private UserSettingMailStorage userSettingMailStorage;

    @Mock
    private Session session;

    // --------- PRIVATE OBJECTS ------------
    private ITipNotificationParticipantResolver resolver;

    private final Context context = new ContextImpl(CONTEXT_ID);

    @Before
    public void setUp() {
        // Init
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(Services.class);
        PowerMockito.mockStatic(UserSettingMailStorage.class);

        // Mock used service classes
        PowerMockito.when(Services.getService(UserService.class)).thenReturn(this.userService);
        PowerMockito.when(Services.getService(ResourceService.class)).thenReturn(this.resources);
        PowerMockito.when(Services.getService(ConfigurationService.class)).thenReturn(this.config);

        // Mock settings
        UserSettingMailStorage userSettingMailStorage = Mockito.mock(UserSettingMailStorage.class);
        PowerMockito.when(UserSettingMailStorage.getInstance()).thenReturn(userSettingMailStorage);
        PowerMockito.when(userSettingMailStorage.getUserSettingMail(ArgumentMatchers.anyInt(), ArgumentMatchers.eq(ArgumentMatchers.eq(context)))).thenReturn(null);

        // Create service to test
        resolver = new ITipNotificationParticipantResolver(util);
    }

    private void prepareServices(Event updated) throws OXException {
        List<MockUser> attendeesAsUser = ChronosTestTools.convertToUser(updated.getAttendees());
        PowerMockito.when(userService.getUser(ArgumentMatchers.any(Context.class), (int[]) ArgumentMatchers.any())).thenReturn(attendeesAsUser.toArray(new MockUser[] {}));
    }

    @Test
    public void testResolveAllRecipients_OnlyInternalAttendees_AllGood() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        prepareServices(updated);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        for (NotificationParticipant participant : participants) {
            Attendee attendee = updated.getAttendees().stream().filter(a -> a.getEntity() == participant.getIdentifier()).findFirst().get();
            Assert.assertThat("Participants mail should have been the user email", participant.getEmail(), is(attendee.getEMail())); //  ChronosTestTools.convertToUser mocks users mail with attendee.getEmail
            Assert.assertThat("Participants comment should have been the same as the attendees", participant.getComment(), is(attendee.getComment()));
            Assert.assertThat("Participants status should be 'accepted'", participant.getConfirmStatus(), is(ParticipationStatus.ACCEPTED));
        }
    }

    @Test
    public void testResolveAllRecipients_OnlyInternalAttendees_AliasAsParticipantMail() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Attendee aliasAttendee = ChronosTestTools.createAttendee(CONTEXT_ID, (AttendeeField[]) null);
        String aliasMail = "alias@example.org";
        aliasAttendee.setUri(CalendarUtils.getURI("mailto:" + aliasMail));
        aliasAttendee.setCn("AliasName, forUser");
        List<Attendee> extenedAttendees = new LinkedList<>(updated.getAttendees());
        extenedAttendees.add(aliasAttendee);
        updated.setAttendees(extenedAttendees);

        prepareServices(updated);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        NotificationParticipant participant = participants.stream().filter(p -> p.getIdentifier() == aliasAttendee.getEntity()).findFirst().get();
        Assert.assertThat("The participants mail should have been the alias the user was invited by", participant.getEmail(), is(aliasMail));
        Assert.assertThat("The common name should have been the name the user was invited by", participant.getDisplayName(), is(aliasAttendee.getCn()));
    }

    @Test
    public void testResolveAllRecipients_WithExternalAttendees_AliasAsParticipantMail() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Attendee external = ChronosTestTools.createExternalAttendee(CONTEXT_ID, (AttendeeField[]) null);
        List<Attendee> extenedAttendees = new LinkedList<>(updated.getAttendees());
        extenedAttendees.add(external);
        updated.setAttendees(extenedAttendees);

        prepareServices(updated);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        NotificationParticipant participant = participants.stream().filter(p -> p.isExternal()).findFirst().get();
        Assert.assertThat("EMail should not differ!", participant.getEmail(), is(external.getEMail())); //  ChronosTestTools.convertToUser mocks users mail with attendee.getEmail
        Assert.assertThat("Display name should be equal to the transmitted common name!", participant.getDisplayName(), is(external.getCn()));
        Assert.assertThat("TimeZone should not differ!", participant.getTimeZone(), is(updated.getStartDate().getTimeZone()));
        Assert.assertThat("Locale should not differ!", participant.getLocale(), is(Locale.CANADA_FRENCH));
        Assert.assertThat("Comment should not differ!", participant.getComment(), is(external.getComment()));
        Assert.assertThat("Confirm status should not differ!", participant.getConfirmStatus(), is(external.getPartStat()));
    }

    @Test(expected = NoSuchElementException.class)
    public void testResolveAllRecipients_WithGroupAttendees_GroupIsRemoved() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Attendee group = ChronosTestTools.createGroup(CONTEXT_ID, (AttendeeField[]) null);
        List<Attendee> extenedAttendees = new LinkedList<>(updated.getAttendees());
        extenedAttendees.add(group);
        updated.setAttendees(extenedAttendees);

        prepareServices(updated);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        // Should throw exception
        NotificationParticipant participant = participants.stream().filter(p -> p.isExternal()).findFirst().get();
        Assert.assertThat("Groups should not get mails..", participant, nullValue());
    }

    @Test
    public void testResolveAllRecipients_WithResourceAttendees_ResourceIsResolved() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Attendee resource = ChronosTestTools.createResource(CONTEXT_ID, (AttendeeField[]) null);
        List<Attendee> extendedAttendees = new LinkedList<>(updated.getAttendees());
        extendedAttendees.add(resource);
        updated.setAttendees(extendedAttendees);

        prepareServices(updated);
        PowerMockito.when(resources.getResource(ArgumentMatchers.anyInt(), ArgumentMatchers.any(Context.class))).thenReturn(ChronosTestTools.convertToResource(resource));

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        // Should throw exception
        NotificationParticipant participant = participants.stream().filter(p -> p.isResource()).findFirst().get();
        Assert.assertThat("EMail should not differ!", participant.getEmail(), is(resource.getEMail())); //  ChronosTestTools.convertToUser mocks users mail with attendee.getEmail
        Assert.assertThat("Display name should not differ.", participant.getDisplayName(), is(resource.getCn()));
        Assert.assertThat("TimeZone should not differ!", participant.getTimeZone(), is(TimeZone.getDefault()));
        Assert.assertThat("Locale should not differ!", participant.getLocale(), is(Locale.CANADA_FRENCH));
        Assert.assertThat("Comment should not be set!", participant.getComment(), nullValue());
        Assert.assertThat("Confirm status should not be set!", participant.getConfirmStatus(), is(ParticipationStatus.NEEDS_ACTION)); // NotificationParticipant default
        Assert.assertFalse("Shouldn't be external!", participant.isExternal());
    }

    @Test
    public void testResolveAllRecipients_OrganizerNotAttendee_OrganizerIsAdded() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Iterator<Attendee> iterator = updated.getAttendees().iterator();
        while (iterator.hasNext()) {
            Attendee next = iterator.next();
            if (next.getEntity() == updated.getOrganizer().getEntity()) {
                iterator.remove();
                break;
            }
        }

        prepareServices(updated);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        NotificationParticipant participant = participants.stream().filter(p -> p.getIdentifier() == updated.getOrganizer().getEntity()).findFirst().get();
        Assert.assertThat("Confirm status should not be set!", participant.getConfirmStatus(), is(ParticipationStatus.NEEDS_ACTION)); // NotificationParticipant default
        Assert.assertFalse("Shouldn't be external!", participant.isExternal());
    }

    @Test
    public void testResolveAllRecipients_OrganizerNotSet_OrganizerIsFoundInAttendeeList() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Organizer o = updated.getOrganizer();
        updated.removeOrganizer();
        updated.removeCreatedBy();

        Iterator<Attendee> iterator = updated.getAttendees().iterator();
        while (iterator.hasNext()) {
            Attendee next = iterator.next();
            if (next.getEntity() == o.getEntity()) {
                iterator.remove();
                break;
            }
        }

        prepareServices(updated);
        PowerMockito.when(userService.getUser(ArgumentMatchers.eq(session.getUserId()), ArgumentMatchers.any(Context.class))).thenReturn(user);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        NotificationParticipant participant = participants.stream().filter(p -> p.getIdentifier() == o.getEntity()).findFirst().get();
        Assert.assertThat("Confirm status should not be set!", participant.getConfirmStatus(), is(ParticipationStatus.NEEDS_ACTION)); // NotificationParticipant default
        Assert.assertFalse("Shouldn't be external!", participant.isExternal());
    }

    @Test
    public void testResolveAllRecipients_OrganizerNotSetAndUserNotAttendee_OrganizerIsFoundInAttendeeList() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Organizer o = updated.getOrganizer();
        updated.removeOrganizer();
        updated.removeCreatedBy();

        Iterator<Attendee> iterator = updated.getAttendees().iterator();
        while (iterator.hasNext()) {
            Attendee next = iterator.next();
            if (next.getEntity() == o.getEntity() || next.getEntity() == user.getId()) {
                iterator.remove();
            }
        }

        prepareServices(updated);
        PowerMockito.when(userService.getUser(ArgumentMatchers.eq(session.getUserId()), ArgumentMatchers.any(Context.class))).thenReturn(user);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        NotificationParticipant participant = participants.stream().filter(p -> p.getIdentifier() == o.getEntity()).findFirst().get();
        Assert.assertThat("Confirm status should not be set!", participant.getConfirmStatus(), is(ParticipationStatus.NEEDS_ACTION)); // NotificationParticipant default
        Assert.assertFalse("Shouldn't be external!", participant.isExternal());
    }

    @Test
    public void testResolveAllRecipients_ExternalOrganizer_OrganizerIsFound() throws OXException {

        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Attendee externalAttendee = ChronosTestTools.createExternalAttendee(CONTEXT_ID, (AttendeeField[]) null);

        Organizer o = new Organizer();
        o.setEntity(externalAttendee.getEntity());
        o.setEMail(externalAttendee.getEMail());
        o.setCn(externalAttendee.getCn());
        o.setUri(externalAttendee.getUri());
        updated.setOrganizer(o);
        updated.getAttendees().add(externalAttendee);

        prepareServices(updated);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        NotificationParticipant participant = participants.stream().filter(p -> p.hasRole(ITipRole.ORGANIZER)).findFirst().get();
        Assert.assertThat("Confirm status doesn't match!", participant.getConfirmStatus(), is(externalAttendee.getPartStat()));
        Assert.assertTrue("Should be external!", participant.isExternal());
    }

    @Test
    public void testResolveAllRecipients_ExternalOrganizerNotAttendee_OrganizerIsAdded() throws OXException {
        // See bug 58461
        // Setup test data
        Event updated = ChronosTestTools.createEvent(CONTEXT_ID, (EventField[]) null);
        User user;
        User onBehalfOf = user = ChronosTestTools.convertToUser(updated.getCreatedBy());

        Attendee externalAttendee = ChronosTestTools.createExternalAttendee(CONTEXT_ID, (AttendeeField[]) null);

        Organizer o = new Organizer();
        o.setEntity(externalAttendee.getEntity());
        o.setEMail(externalAttendee.getEMail());
        o.setCn(externalAttendee.getCn());
        o.setUri(externalAttendee.getUri());
        updated.setOrganizer(o);
        updated.getAttendees().add(ChronosTestTools.createExternalAttendee(CONTEXT_ID, (AttendeeField[]) null));

        prepareServices(updated);

        List<NotificationParticipant> participants = resolver.resolveAllRecipients(null, updated, user, onBehalfOf, context, session, null);
        Assert.assertFalse("No participants resolved", participants.isEmpty());
        NotificationParticipant participant = participants.stream().filter(p -> p.hasRole(ITipRole.ORGANIZER)).findFirst().get();
        Assert.assertThat("Confirm status doesn't match!", participant.getConfirmStatus(), is(ParticipationStatus.NEEDS_ACTION)); // Organizer is not an attendee, so he can't accept etc.
        Assert.assertTrue("Should be external!", participant.isExternal());
    }

}
