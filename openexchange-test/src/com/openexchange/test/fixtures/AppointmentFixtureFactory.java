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

package com.openexchange.test.fixtures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Appointment;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.GroupParticipant;
import com.openexchange.groupware.container.Participant;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.test.fixtures.transformators.BooleanTransformator;
import com.openexchange.test.fixtures.transformators.JChronicLongTransformator;
import com.openexchange.test.fixtures.transformators.ParticipantTransformator;
import com.openexchange.test.fixtures.transformators.RecurrenceTypeTransformator;
import com.openexchange.test.fixtures.transformators.ShowAsTransformator;

/**
 * @author Francisco Laguna <francisco.laguna@open-xchange.com>
 * @author Markus Wagner <markus.wagner@open-xchange.com>
 */
public class AppointmentFixtureFactory implements FixtureFactory<Appointment> {

    private final FixtureLoader fixtureLoader;
    private final GroupResolver groupResolver;

    public AppointmentFixtureFactory(GroupResolver groupResolver, FixtureLoader fixtureLoader) {
        super();
        this.fixtureLoader = fixtureLoader;
        this.groupResolver = groupResolver;
    }

    @Override
    public Fixtures<Appointment> createFixture(final Map<String, Map<String, String>> entries) {
        return new AppointmentFixtures(entries, fixtureLoader, groupResolver);
    }

    private class AppointmentFixtures extends DefaultFixtures<Appointment> implements Fixtures<Appointment> {

        private final Map<String, Map<String, String>> entries;

        private final Map<String, Fixture<Appointment>> appointments = new HashMap<String, Fixture<Appointment>>();

        @SuppressWarnings("hiding")
        private final GroupResolver groupResolver;

        public AppointmentFixtures(final Map<String, Map<String, String>> entries, FixtureLoader fixtureLoader, GroupResolver groupResolver) {
            super(Appointment.class, entries, fixtureLoader);
            this.entries = entries;
            this.groupResolver = groupResolver;

            addTransformator(new ShowAsTransformator(), "shown_as");
            addTransformator(new BooleanTransformator(), "full_time");
            addTransformator(new ParticipantTransformator(fixtureLoader), "participants");
            addTransformator(new BooleanTransformator(), "private_flag");
            addTransformator(new JChronicLongTransformator(fixtureLoader), "recurring_start");
            addTransformator(new RecurrenceTypeTransformator(), "recurrence_type");
        }

        @Override
        public Fixture<Appointment> getEntry(final String entryName) throws OXException {
            if (appointments.containsKey(entryName)) {
                return appointments.get(entryName);
            }
            final Map<String, String> values = entries.get(entryName);
            if (null == values) {
                throw new FixtureException("Entry with name " + entryName + " not found");
            }
            final Appointment appointment = new Appointment();
            apply(appointment, values);
            applyUsers(appointment, groupResolver);
            final Fixture<Appointment> fixture = new Fixture<Appointment>(appointment, values.keySet().toArray(new String[values.size()]), values);
            appointments.put(entryName, fixture);
            return fixture;
        }

        private void applyUsers(final Appointment appointment, GroupResolver groupResolver) {
            if (null != appointment) {
                final Participant[] participants = appointment.getParticipants();
                if (null != participants) {
                    final List<UserParticipant> users = new ArrayList<UserParticipant>();
                    for (Participant participant : participants) {
                        if (Participant.USER == participant.getType()) {
                            users.add((UserParticipant) participant);
                        } else if (Participant.GROUP == participant.getType()) {
                            final GroupParticipant group = (GroupParticipant) participant;
                            final Contact[] groupMembers = groupResolver.resolveGroup(group.getIdentifier());
                            if (null != groupMembers) {
                                for (Contact groupMember : groupMembers) {
                                    final UserParticipant userParticipant = new UserParticipant(groupMember.getObjectID());
                                    userParticipant.setDisplayName(groupMember.getDisplayName());
                                    userParticipant.setEmailAddress(groupMember.getEmail1());
                                    users.add(userParticipant);
                                }
                            }
                        }
                    }
                    appointment.setUsers(users);
                }
            }
        }
    }
}
