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

package com.openexchange.ajax.task;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.junit.Test;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AbstractAJAXSession;
import com.openexchange.ajax.framework.CommonAllResponse;
import com.openexchange.ajax.framework.ListID;
import com.openexchange.ajax.framework.MultipleRequest;
import com.openexchange.ajax.framework.MultipleResponse;
import com.openexchange.ajax.task.actions.AllRequest;
import com.openexchange.ajax.task.actions.DeleteRequest;
import com.openexchange.ajax.task.actions.InsertRequest;
import com.openexchange.ajax.task.actions.InsertResponse;
import com.openexchange.groupware.search.Order;
import com.openexchange.groupware.tasks.Task;

/**
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class Bug11848Test extends AbstractAJAXSession {

    private static final int NUMBER = 100;

    /**
     * Default constructor.
     */
    public Bug11848Test() {
        super();
    }

    @Test
    public void testSorting() throws Throwable {
        final AJAXClient client = getClient();
        final int folder = client.getValues().getPrivateTaskFolder();
        final TimeZone tz = client.getValues().getTimeZone();
        final Task[] tasks = new Task[NUMBER];
        {
            final Calendar calendar = new GregorianCalendar(tz);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            for (int i = 0; i < NUMBER; i++) {
                final Task task = new Task();
                task.setTitle("test for bug 11848 " + i);
                task.setParentFolderID(folder);
                task.setStartDate(calendar.getTime());
                task.setEndDate(calendar.getTime());
                calendar.add(Calendar.DATE, 1);
                tasks[i] = task;
            }
        }
        final MultipleResponse<InsertResponse> mInsert;
        {
            final InsertRequest[] requests = new InsertRequest[NUMBER];
            for (int i = 0; i < NUMBER; i++) {
                requests[i] = new InsertRequest(tasks[i], tz);
            }
            mInsert = client.execute(MultipleRequest.create(requests));
            for (int i = 0; i < NUMBER; i++) {
                mInsert.getResponse(i).fillTask(tasks[i]);
            }
        }
        try {
            final AllRequest request = new AllRequest(folder, new int[] { Task.OBJECT_ID }, Task.END_DATE, Order.ASCENDING);
            final CommonAllResponse response = client.execute(request);
            int pos = 0;
            for (final ListID identifier : response.getListIDs()) {
                final Task task = tasks[pos];
                if (identifier.getObject().equals(String.valueOf(task.getObjectID()))) {
                    pos++;
                    if (pos >= NUMBER) {
                        break;
                    }
                }
            }
        } finally {
            final DeleteRequest[] requests = new DeleteRequest[NUMBER];
            for (int i = 0; i < NUMBER; i++) {
                requests[i] = new DeleteRequest(mInsert.getResponse(i));
            }
            client.execute(MultipleRequest.create(requests));
        }
    }
}
