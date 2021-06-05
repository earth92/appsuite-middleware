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

package com.openexchange.ajax.appointment;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;
import com.openexchange.ajax.appointment.action.AllRequest;
import com.openexchange.ajax.appointment.action.AppointmentInsertResponse;
import com.openexchange.ajax.appointment.action.DeleteRequest;
import com.openexchange.ajax.appointment.action.InsertRequest;
import com.openexchange.ajax.appointment.action.ListRequest;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AbstractAJAXSession;
import com.openexchange.ajax.framework.CommonAllResponse;
import com.openexchange.ajax.framework.CommonInsertResponse;
import com.openexchange.ajax.framework.CommonListResponse;
import com.openexchange.ajax.framework.MultipleRequest;
import com.openexchange.ajax.framework.MultipleResponse;
import com.openexchange.groupware.container.Appointment;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.test.common.groupware.calendar.TimeTools;

/**
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public class NewListTest extends AbstractAJAXSession {

    private static final int NUMBER = 10;

    private static final int DELETES = 2;

    /**
     * Default constructor.
     */
    public NewListTest() {
        super();
    }

    /**
     * This method tests the new handling of not more available objects for LIST requests.
     */
    @Test
    public void testRemovedObjectHandling() throws Throwable {
        final AJAXClient clientA = getClient();
        final TimeZone tzA = clientA.getValues().getTimeZone();
        final int folderA = clientA.getValues().getPrivateAppointmentFolder();

        // Create some appointments.
        final Date appStart = new Date(TimeTools.getHour(0, tzA));
        final Date appEnd = new Date(TimeTools.getHour(1, tzA));
        final Date listStart = TimeTools.getAPIDate(tzA, appStart, 0);
        final Date listEnd = TimeTools.getAPIDate(tzA, appEnd, 1);
        final InsertRequest[] inserts = new InsertRequest[NUMBER];
        for (int i = 0; i < inserts.length; i++) {
            final Appointment app = new Appointment();
            app.setTitle("New List Test " + (i + 1));
            app.setParentFolderID(folderA);
            app.setStartDate(appStart);
            app.setEndDate(appEnd);
            app.setIgnoreConflicts(true);
            app.addParticipant(new UserParticipant(clientA.getValues().getUserId()));
            inserts[i] = new InsertRequest(app, tzA);
        }
        final MultipleResponse<AppointmentInsertResponse> mInsert = clientA.execute(MultipleRequest.create(inserts));
        final List<CommonInsertResponse> toDelete = new ArrayList<CommonInsertResponse>(NUMBER);
        final Iterator<AppointmentInsertResponse> iter = mInsert.iterator();
        while (iter.hasNext()) {
            toDelete.add(iter.next());
        }

        // A now gets all of the folder.
        final int[] columns = new int[] { Appointment.TITLE, Appointment.OBJECT_ID, Appointment.FOLDER_ID };
        final CommonAllResponse allR = clientA.execute(new AllRequest(folderA, columns, listStart, listEnd, tzA));

        // TODO This delete of B does not remove the appointments but only the
        // participant.
        // Now B deletes some of them.
        final DeleteRequest[] deletes1 = new DeleteRequest[DELETES];
        for (int i = 0; i < deletes1.length; i++) {
            final CommonInsertResponse insertR = toDelete.remove((NUMBER - DELETES) / 2 + i);
            deletes1[i] = new DeleteRequest(insertR.getId(), folderA, allR.getTimestamp());
        }
        clientA.execute(MultipleRequest.create(deletes1));

        // List request of A must now not contain the deleted objects and give
        // no error.
        final CommonListResponse listR = clientA.execute(new ListRequest(allR.getListIDs(), columns, true));

        final DeleteRequest[] deletes2 = new DeleteRequest[toDelete.size()];
        for (int i = 0; i < deletes2.length; i++) {
            final CommonInsertResponse insertR = toDelete.get(i);
            deletes2[i] = new DeleteRequest(insertR.getId(), folderA, listR.getTimestamp());
        }
        clientA.execute(MultipleRequest.create(deletes2));
    }
}
