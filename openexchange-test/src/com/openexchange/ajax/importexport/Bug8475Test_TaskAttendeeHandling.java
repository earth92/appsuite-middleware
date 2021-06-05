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

package com.openexchange.ajax.importexport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.ByteArrayInputStream;
import org.junit.Test;
import com.openexchange.ajax.importexport.actions.ICalImportRequest;
import com.openexchange.ajax.importexport.actions.ICalImportResponse;
import com.openexchange.ajax.task.ManagedTaskTest;
import com.openexchange.groupware.container.Participant;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.groupware.tasks.Task;

/**
 * {@link Bug8475Test_TaskAttendeeHandling}
 *
 * @author <a href="mailto:Jan-Oliver.Huhn@open-xchange.com">Jan-Oliver Huhn</a>
 * @since v7.10.0
 */
public class Bug8475Test_TaskAttendeeHandling extends ManagedTaskTest {

    private final String task =
        "BEGIN:VCALENDAR\n"
        + "VERSION:2.0\n"
        + "PRODID:-//Apple Computer\\, Inc//iCal 1.5//EN\n"
        + "BEGIN:VTODO\n"
        + "ORGANIZER:MAILTO:tobias.friedrich@open-xchange.com\n"
        + "ATTENDEE:MAILTO:tobias.prinz@open-xchange.com\n"
        + "DTSTART:20070608T080000Z\n"
        + "STATUS:COMPLETED\n"
        + "SUMMARY:Test todo\n"
        + "UID:8D4FFA7A-ABC0-11D7-8200-00306571349C-RID\n"
        + "DUE:20070618T080000Z\n"
        + "END:VTODO\n"
        + "END:VCALENDAR";

    @Test
    public void testAttendeeNotFound() throws Exception {
        final ICalImportRequest request = new ICalImportRequest(folderID, new ByteArrayInputStream(task.toString().getBytes(com.openexchange.java.Charsets.UTF_8)), false);
        ICalImportResponse response = getClient().execute(request);
        assertEquals(1, response.getImports().length);

        String objectId = response.getImports()[0].getObjectId();
        Task task = ttm.getTaskFromServer(folderID, Integer.parseInt(objectId));
        assertNotNull(task);

        final Participant[] participants = task.getParticipants();
        assertEquals("One participant?", 1, participants.length);
        boolean found = false;
        for (final Participant p : participants) {
            if ("tobias.prinz@open-xchange.com".equals(p.getEmailAddress())) {
                found = true;
            }
        }
        assertTrue("The attendee tobias.prinz@open-xchange.com couldnt be found", found);
    }

    @Test
    public void testInternalAttendee() throws Exception {

        final String ical =
            "BEGIN:VCALENDAR\n"
            + "VERSION:2.0\n"
            + "PRODID:-//Apple Computer\\, Inc//iCal 1.5//EN\n"
            + "BEGIN:VTODO\n"
            + "ORGANIZER:MAILTO:tobias.friedrich@open-xchange.com\n"
            + "ATTENDEE:MAILTO:"
            + testUser.getLogin() + "\n"
            + "DTSTART:20070608T080000Z\n"
            + "STATUS:COMPLETED\n"
            + "SUMMARY:Test todo\n"
            + "UID:8D4FFA7A-ABC0-11D7-8200-00306571349C-RID\n"
            + "DUE:20070618T080000Z\n"
            + "END:VTODO\n" + "END:VCALENDAR";

        final ICalImportRequest request = new ICalImportRequest(folderID, new ByteArrayInputStream(ical.toString().getBytes(com.openexchange.java.Charsets.UTF_8)), false);
        ICalImportResponse response = getClient().execute(request);
        assertEquals(1, response.getImports().length);

        String objectId = response.getImports()[0].getObjectId();
        Task task = ttm.getTaskFromServer(folderID, Integer.parseInt(objectId));

        final Participant[] participants = task.getParticipants();
        assertEquals("One participant?", 1, participants.length);
        assertEquals(1, task.getUsers().length);
        UserParticipant internalParticipant = task.getUsers()[0];
        assertNotNull(internalParticipant);
        assertEquals(task.getCreatedBy(), internalParticipant.getIdentifier());

    }

}
