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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import com.openexchange.ajax.fields.TaskFields;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AbstractAJAXSession;
import com.openexchange.ajax.task.actions.DeleteRequest;
import com.openexchange.ajax.task.actions.GetRequest;
import com.openexchange.ajax.task.actions.GetResponse;
import com.openexchange.ajax.task.actions.InsertRequest;
import com.openexchange.ajax.task.actions.InsertResponse;
import com.openexchange.ajax.task.actions.UpdateRequest;
import com.openexchange.ajax.task.actions.UpdateResponse;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.tasks.Task;

public class Bug11190Test extends AbstractAJAXSession {

    public Bug11190Test() {
        super();
    }

    /*
     * Changing a monthly recurring appointment in the GUI from one
     * style (every Xth day) to another (every Monday/Tuesday...)
     * results in a broken recurrence.
     */
    @Test
    public void testSwitchingBetweenMonthlyRecurrencePatternsShouldNotBreakRecurrence() throws OXException, IOException, JSONException {
        AJAXClient ajaxClient = getClient();
        final TimeZone timezone = ajaxClient.getValues().getTimeZone();
        final int folderId = ajaxClient.getValues().getPrivateTaskFolder();

        //new task
        Task taskWithRecurrence = new Task();
        taskWithRecurrence.setTitle("Reproducing bug 11190");
        taskWithRecurrence.setParentFolderID(folderId);
        taskWithRecurrence.setStartDate(new Date());
        taskWithRecurrence.setEndDate(new Date());

        //...every three months:
        taskWithRecurrence.setRecurrenceType(Task.MONTHLY);
        taskWithRecurrence.setInterval(3);
        //...every second Monday
        taskWithRecurrence.setDays(Task.MONDAY);
        taskWithRecurrence.setDayInMonth(2);
        //send
        InsertRequest insertRequest = new InsertRequest(taskWithRecurrence, timezone);
        InsertResponse insertResponse = ajaxClient.execute(insertRequest);
        taskWithRecurrence.setLastModified(insertResponse.getTimestamp());

        try {
            //refresh task
            insertResponse.fillTask(taskWithRecurrence);
            //update task with new pattern for recurrence
            //...every two months
            taskWithRecurrence.setRecurrenceType(Task.MONTHLY);
            taskWithRecurrence.setInterval(2);
            //...every twelfth day
            taskWithRecurrence.setDayInMonth(12);
            // TODO The remove method clears the value in the object. If the value should be cleared over the AJAX interface it must be set to null. This is currently not possible with the int primitive type.
            taskWithRecurrence.removeDays(); //otherwise, the old value (Monday) will be kept, which then means "the twelfth Monday every two months" (which then is reduced to every 5th Monday)
            //send
            UpdateRequest updateRequest = new IntToNullSettingUpdateRequest(taskWithRecurrence, timezone);
            UpdateResponse updateResponse = ajaxClient.execute(updateRequest);
            taskWithRecurrence.setLastModified(updateResponse.getTimestamp());
            //get data
            GetRequest getRequest = new GetRequest(folderId, taskWithRecurrence.getObjectID());
            GetResponse getResponse = ajaxClient.execute(getRequest);
            Task resultingTask = getResponse.getTask(timezone);
            //compare
            assertEquals("Recurrence type does not match", Task.MONTHLY, resultingTask.getRecurrenceType());
            assertEquals("Recurrence interval does not match", 2, resultingTask.getInterval());
            assertEquals("Recurring day in month does not match", 12, resultingTask.getDayInMonth());
            assertFalse("Recurring days should not be set anymore", resultingTask.containsDays());
        } finally {
            DeleteRequest cleanUp = new DeleteRequest(taskWithRecurrence);
            ajaxClient.execute(cleanUp);
        }
    }

    private class IntToNullSettingUpdateRequest extends UpdateRequest {

        public IntToNullSettingUpdateRequest(Task task, TimeZone timeZone) {
            super(task, timeZone);
        }

        @Override
        public JSONObject getBody() throws JSONException {
            JSONObject obj = super.getBody();
            if (!getTask().containsDays() && getTask().containsDayInMonth()) {
                obj.put(TaskFields.DAYS, JSONObject.NULL);
            }
            return obj;
        }
    }
}
