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

package com.openexchange.test;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.I2i;
import static com.openexchange.java.Autoboxing.i2I;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.appointment.action.AllRequest;
import com.openexchange.ajax.appointment.action.AppointmentInsertResponse;
import com.openexchange.ajax.appointment.action.AppointmentUpdatesResponse;
import com.openexchange.ajax.appointment.action.ConfirmRequest;
import com.openexchange.ajax.appointment.action.ConfirmResponse;
import com.openexchange.ajax.appointment.action.CopyRequest;
import com.openexchange.ajax.appointment.action.CopyResponse;
import com.openexchange.ajax.appointment.action.DeleteRequest;
import com.openexchange.ajax.appointment.action.FreeBusyRequest;
import com.openexchange.ajax.appointment.action.FreeBusyResponse;
import com.openexchange.ajax.appointment.action.GetChangeExceptionsRequest;
import com.openexchange.ajax.appointment.action.GetChangeExceptionsResponse;
import com.openexchange.ajax.appointment.action.GetRequest;
import com.openexchange.ajax.appointment.action.GetResponse;
import com.openexchange.ajax.appointment.action.HasRequest;
import com.openexchange.ajax.appointment.action.HasResponse;
import com.openexchange.ajax.appointment.action.InsertRequest;
import com.openexchange.ajax.appointment.action.ListRequest;
import com.openexchange.ajax.appointment.action.NewAppointmentSearchRequest;
import com.openexchange.ajax.appointment.action.NewAppointmentSearchResponse;
import com.openexchange.ajax.appointment.action.SearchRequest;
import com.openexchange.ajax.appointment.action.SearchResponse;
import com.openexchange.ajax.appointment.action.UpdateRequest;
import com.openexchange.ajax.appointment.action.UpdateResponse;
import com.openexchange.ajax.appointment.action.UpdatesRequest;
import com.openexchange.ajax.folder.actions.EnumAPI;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AJAXRequest;
import com.openexchange.ajax.framework.AbstractAJAXResponse;
import com.openexchange.ajax.framework.AbstractUpdatesRequest.Ignore;
import com.openexchange.ajax.framework.CommonAllResponse;
import com.openexchange.ajax.framework.CommonDeleteResponse;
import com.openexchange.ajax.framework.CommonListResponse;
import com.openexchange.ajax.framework.ListIDs;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Appointment;
import com.openexchange.groupware.container.CommonObject;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.server.impl.OCLPermission;

/**
 * {@link CalendarTestManager}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a> - basic design
 * @author <a href="mailto:tobias.prinz@open-xchange.com">Tobias Prinz</a> - extension
 */
public class CalendarTestManager implements TestManager {

    private AJAXClient client;

    private final List<Appointment> createdEntities = new ArrayList<Appointment>();

    private TimeZone timezone;

    private AbstractAJAXResponse lastResponse;

    private boolean failOnError;

    private Exception lastException;

    private Date lastModification;

    public TimeZone getTimezone() {
        return timezone;
    }

    public void setTimezone(TimeZone timezone) {
        this.timezone = timezone;
    }

    public CalendarTestManager(AJAXClient client) {
        this.setClient(client);

        try {
            timezone = client.getValues().getTimeZone();
        } catch (@SuppressWarnings("unused") OXException | IOException | JSONException e) {
            // wait for finally block
        } finally {
            if (timezone == null) {
                timezone = TimeZone.getTimeZone("Europe/Berlin");
            }
        }
    }

    public void setClient(AJAXClient client) {
        this.client = client;
    }

    public AJAXClient getClient() {
        return client;
    }

    public void setLastResponse(AbstractAJAXResponse lastResponse) {
        this.lastResponse = lastResponse;
    }

    @Override
    public AbstractAJAXResponse getLastResponse() {
        return lastResponse;
    }

    @Override
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    @Override
    public boolean getFailOnError() {
        return failOnError;
    }

    @Override
    public boolean doesFailOnError() {
        return getFailOnError();
    }

    public void setLastException(Exception lastException) {
        lastException.printStackTrace();
        this.lastException = lastException;
    }

    @Override
    public Exception getLastException() {
        return lastException;
    }

    @Override
    public boolean hasLastException() {
        return lastException != null;
    }

    public void setLastModification(Date lastModification) {
        this.lastModification = lastModification;
    }

    public Date getLastModification() {
        return lastModification;
    }

    public int getPrivateFolder() throws OXException, IOException, JSONException {
        return getClient().getValues().getPrivateAppointmentFolder();
    }

    @Override
    public void cleanUp() {
        boolean old = getFailOnError();
        setFailOnError(false); // switching off, because there are other ways to delete an appointment, for example creating enough delete
        // exceptions
        for (Appointment appointment : new ArrayList<Appointment>(createdEntities)) {
            delete(appointment, true);
            if (getLastResponse().hasError()) {
                org.slf4j.LoggerFactory.getLogger(CalendarTestManager.class).warn("Unable to delete the appointment with id {} in folder {} with name '{}': {}", I(appointment.getObjectID()), I(appointment.getParentFolderID()), appointment.getTitle(), getLastResponse().getException().getMessage());
            }
        }
        setFailOnError(old);
    }

    private <T extends AbstractAJAXResponse> T execute(final AJAXRequest<T> request) {
        try {
            return getClient().execute(request);
        } catch (OXException e) {
            setLastException(e);
            if (failOnError) {
                fail("AjaxException during task creation: " + e.getLocalizedMessage());
            }
        } catch (IOException e) {
            setLastException(e);
            if (failOnError) {
                fail("IOException during task creation: " + e.getLocalizedMessage());
            }
        } catch (JSONException e) {
            setLastException(e);
            if (failOnError) {
                fail("JsonException during task creation: " + e.getLocalizedMessage());
            }
        }
        return null;
    }

    /*
     * Requests
     */
    public Appointment insert(Appointment appointment) {
        InsertRequest insertRequest = new InsertRequest(appointment, timezone, getFailOnError());
        AppointmentInsertResponse insertResponse = execute(insertRequest);
        extractInfo(insertResponse);
        insertResponse.fillAppointment(appointment);
        if (doesFailOnError() || appointment.getObjectID() != 0) {
            createdEntities.add(appointment);
        }
        return appointment;
    }

    public Appointment get(int parentFolderID, int objectID) throws OXException {
        GetRequest get = new GetRequest(parentFolderID, objectID, getFailOnError());
        GetResponse response = execute(get);
        assertNotNull("Missing response object", response);
        extractInfo(response);
        return response.getAppointment(timezone);
    }

    public Appointment copy(int parentFolderID, int objectID, JSONObject body) throws OXException {
        return this.copy(parentFolderID, objectID, body, false);
    }

    public Appointment copy(int parentFolderID, int objectID, JSONObject body, boolean ignoreConflicts) throws OXException {
        CopyRequest request = new CopyRequest(parentFolderID, objectID, body, ignoreConflicts);
        CopyResponse response = execute(request);
        extractInfo(response);
        return response.getAppointment(timezone);
    }

    public Appointment get(Appointment appointment) throws OXException {
        try {
            GetRequest get = new GetRequest(appointment, getFailOnError());
            GetResponse response = execute(get);
            extractInfo(response);
            return response.getAppointment(timezone);
        } catch (OXException e) {
            if (failOnError) {
                throw e;
            }
            return null;
        }
    }

    public Appointment get(int parentFolderID, int objectID, boolean pleaseFailOnError) throws OXException {
        try {
            GetRequest get = new GetRequest(parentFolderID, objectID, pleaseFailOnError);
            GetResponse response = execute(get);
            extractInfo(response);
            return response.getAppointment(timezone);
        } catch (OXException e) {
            if (failOnError) {
                throw e;
            }
            return null;
        }
    }

    public Appointment get(int parentFolderID, int objectID, int recurrencePos) throws OXException {
        try {
            GetRequest get = new GetRequest(parentFolderID, objectID, recurrencePos, getFailOnError());
            GetResponse response = execute(get);
            extractInfo(response);
            return response.getAppointment(timezone);
        } catch (OXException e) {
            if (failOnError) {
                throw e;
            }
            return null;
        }
    }

    public void confirm(Appointment app, int status, String message, int occurrence) {
        confirm(app.getParentFolderID(), app.getObjectID(), app.getLastModified(), status, message, occurrence);
    }

    public void confirm(int folderId, int objectId, Date timestamp, int status, String message, int occurrence) {
        ConfirmRequest confirmRequest = new ConfirmRequest(folderId, objectId, occurrence, status, message, 0, timestamp, getFailOnError());
        ConfirmResponse resp = execute(confirmRequest);
        setLastResponse(resp);
        setLastModification(resp.getTimestamp());
    }

    public void confirm(Appointment app, int status, String message) {
        confirm(app.getParentFolderID(), app.getObjectID(), app.getLastModified(), status, message);
    }

    public void confirm(int folderId, int objectId, Date timestamp, int status, String message) {
        ConfirmRequest confirmRequest = new ConfirmRequest(folderId, objectId, status, message, 0, timestamp, getFailOnError());
        ConfirmResponse resp = execute(confirmRequest);
        setLastResponse(resp);
        setLastModification(resp.getTimestamp());
    }

    public void confirm(Appointment app, int user, int status, String message) {
        confirm(app.getParentFolderID(), app.getObjectID(), app.getLastModified(), user, status, message);
    }

    public void confirm(int folderId, int objectId, Date timestamp, int user, int status, String message) {
        ConfirmRequest confirmRequest = new ConfirmRequest(folderId, objectId, status, message, user, timestamp, getFailOnError());
        ConfirmResponse resp = execute(confirmRequest);
        setLastResponse(resp);
        setLastModification(resp.getTimestamp());
    }

    public void confirmExternal(Appointment app, String mail, int status, String message, int occurrence) {
        confirmExternal(app.getParentFolderID(), app.getObjectID(), app.getLastModified(), mail, status, message, occurrence);
    }

    public void confirmExternal(int folderId, int objectId, Date timestamp, String mail, int status, String message, int occurrence) {
        ConfirmRequest confirmRequest = new ConfirmRequest(folderId, objectId, occurrence, status, message, mail, timestamp, getFailOnError());
        ConfirmResponse resp = execute(confirmRequest);
        setLastResponse(resp);
        setLastModification(resp.getTimestamp());
    }

    public void confirmExternal(Appointment app, String mail, int status, String message) {
        confirmExternal(app.getParentFolderID(), app.getObjectID(), app.getLastModified(), mail, status, message);
    }

    public void confirmExternal(int folderId, int objectId, Date timestamp, String mail, int status, String message) {
        ConfirmRequest confirmRequest = new ConfirmRequest(folderId, objectId, status, message, mail, timestamp, getFailOnError());
        ConfirmResponse resp = execute(confirmRequest);
        setLastResponse(resp);
        setLastModification(resp.getTimestamp());
    }

    public List<Appointment> updates(final int folderId, final Date timestamp, final boolean recurrenceMaster) {
        return updates(folderId, Appointment.ALL_COLUMNS, timestamp, recurrenceMaster);
    }

    public List<Appointment> updates(final int folderId, final int[] columns, final Date timestamp, final boolean recurrenceMaster) {
        UpdatesRequest req = new UpdatesRequest(folderId, columns, timestamp, recurrenceMaster);
        AppointmentUpdatesResponse resp = execute(req);
        extractInfo(resp);
        try {
            return resp.getAppointments(timezone);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Appointment> updates(final int folderId, final int[] columns, final Date timestamp, final boolean recurrenceMaster, Ignore ignore, Date start, Date end) {
        UpdatesRequest req = new UpdatesRequest(folderId, columns, timestamp, recurrenceMaster, ignore, start, end);
        AppointmentUpdatesResponse resp = execute(req);
        extractInfo(resp);
        try {
            return resp.getAppointments(timezone);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Appointment> getChangeExceptions(int folderId, int objectId, int[] columns) {
        GetChangeExceptionsRequest request = new GetChangeExceptionsRequest(folderId, objectId, columns);
        GetChangeExceptionsResponse response = execute(request);
        extractInfo(response);
        try {
            return response.getAppointments(timezone);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public void update(int inFolder, Appointment updatedAppointment) {
        UpdateRequest updateRequest = new UpdateRequest(inFolder, updatedAppointment, timezone, getFailOnError());
        UpdateResponse updateResponse = execute(updateRequest);
        extractInfo(updateResponse);
        updatedAppointment.setLastModified(updateResponse.getTimestamp());
        if (updateResponse.getId() != 0) {
            updatedAppointment.setObjectID(updateResponse.getId());
        }
        for (Appointment createdAppoinment : createdEntities) {
            if (createdAppoinment.getObjectID() == updatedAppointment.getObjectID()) {
                createdAppoinment.setLastModified(updatedAppointment.getLastModified());
                continue;
            }
        }

    }

    public void update(Appointment updatedAppointment) {
        update(updatedAppointment.getParentFolderID(), updatedAppointment);
    }

    public List<Appointment> list(ListIDs foldersAndIds, int[] columns) {
        int[] enhancedColumns = addNecessaryColumns(columns);
        ListRequest req = new ListRequest(foldersAndIds, enhancedColumns, getFailOnError());
        CommonListResponse resp = execute(req);
        extractInfo(resp);

        try {
            Appointment[] appointmentArray = CTMUtils.jsonArray2AppointmentArray((JSONArray) resp.getData(), enhancedColumns, timezone);
            return Arrays.asList(appointmentArray);
        } catch (Exception e) {
            // TODO: handle exception
        }
        return Collections.emptyList();
    }

    public List<Appointment> newappointments(Date start, Date end, int limit, int[] columns) {
        NewAppointmentSearchRequest req = new NewAppointmentSearchRequest(start, end, limit, timezone, columns);
        NewAppointmentSearchResponse resp = execute(req);
        extractInfo(resp);
        try {
            Appointment[] appointments = resp.getAppointments();
            createdEntities.addAll(Arrays.asList(appointments));
            return Arrays.asList(appointments);
        } catch (Exception e) {
            lastException = e;
            return null;
        }
    }

    private int[] addNecessaryColumns(int[] columns) {
        List<Integer> cols = new LinkedList<Integer>(Arrays.asList(i2I(columns)));
        if (!cols.contains(I(CommonObject.FOLDER_ID))) {
            cols.add(I(CommonObject.FOLDER_ID));
        }
        if (!cols.contains(I(CommonObject.OBJECT_ID))) {
            cols.add(I(CommonObject.OBJECT_ID));
        }
        return I2i(cols);
    }

    public Appointment[] all(int parentFolderID, Date start, Date end, int[] columns, boolean recurrenceMaster) {
        return this.all(parentFolderID, start, end, columns, recurrenceMaster, null);
    }

    public Appointment[] all(int parentFolderID, Date start, Date end, int[] columns, boolean recurrenceMaster, String timeZoneId) {
        AllRequest request = new AllRequest(parentFolderID, columns, start, end, timezone, recurrenceMaster);
        if (timeZoneId != null) {
            request.setTimeZoneId(timeZoneId);
        }
        CommonAllResponse response = execute(request);
        extractInfo(response);
        try {
            return CTMUtils.jsonArray2AppointmentArray((JSONArray) response.getData(), response.getColumns(), timezone);
        } catch (JSONException | OXException e) {
            fail(e.getMessage());
            return null;
        }
    }

    public Appointment[] all(int parentFolderID, Date start, Date end, int[] columns) {
        return all(parentFolderID, start, end, columns, true);
    }

    public Appointment[] all(int parentFolderID, Date start, Date end) {
        return all(parentFolderID, start, end, Appointment.ALL_COLUMNS);
    }

    public void delete(Appointment appointment, Date recurrenceDatePosition) {
        final DeleteRequest deleteRequest = new DeleteRequest(appointment.getObjectID(), appointment.getParentFolderID(), recurrenceDatePosition, new Date(Long.MAX_VALUE));
        deleteRequest.setFailOnError(false);
        CommonDeleteResponse response = execute(deleteRequest);

        if (response != null) {
            extractInfo(response);
        }
    }

    public void delete(Appointment appointment, boolean failOnErrorOverride, boolean deleteFromCreatedEntities) {
        if (deleteFromCreatedEntities) {
            createdEntities.remove(appointment); // TODO: Does this remove the right object or does equals() suck?
        }
        DeleteRequest deleteRequest;
        if (appointment.containsRecurrencePosition()) {
            deleteRequest = new DeleteRequest(appointment.getObjectID(), appointment.getParentFolderID(), appointment.getRecurrencePosition(), new Date(Long.MAX_VALUE), failOnErrorOverride);
        } else {
            deleteRequest = new DeleteRequest(appointment.getObjectID(), appointment.getParentFolderID(), new Date(Long.MAX_VALUE), failOnErrorOverride);
        }
        CommonDeleteResponse response = execute(deleteRequest);
        if (response != null) {
            extractInfo(response);
        }
    }

    public void delete(Appointment appointment) {
        delete(appointment, getFailOnError(), false);
    }

    public void delete(Appointment appointment, boolean deleteFromCreatedEntities) {
        delete(appointment, getFailOnError(), deleteFromCreatedEntities);
    }

    public void createDeleteException(int folder, int seriesId, int recurrencePos) {
        DeleteRequest deleteRequest = new DeleteRequest(seriesId, folder, recurrencePos, new Date(Long.MAX_VALUE), getFailOnError());
        extractInfo(execute(deleteRequest));
    }

    public void createDeleteException(Appointment master, int recurrencePos) {
        createDeleteException(master.getParentFolderID(), master.getObjectID(), recurrencePos);
        master.setLastModified(getLastModification());
    }

    public boolean[] has(Date startInclusive, Date endExclusive) {
        HasResponse response = execute(new HasRequest(startInclusive, endExclusive, getTimezone()));
        lastResponse = response;
        try {
            return response.getValues();
        } catch (JSONException e) {
            lastException = e;
            return null;
        }
    }

    public List<Appointment> getCreatedEntities() {
        return this.createdEntities;
    }

    /*
     * Helper methods
     */
    public Appointment createIdentifyingCopy(Appointment appointment) {
        Appointment copy = new Appointment();
        copy.setObjectID(appointment.getObjectID());
        copy.setParentFolderID(appointment.getParentFolderID());
        copy.setLastModified(appointment.getLastModified());
        return copy;
    }

    public void clearFolder(int folderId, Date start, Date end) {
        for (Appointment app : all(folderId, start, end)) {
            delete(app, true);
        }
    }

    protected void extractInfo(AbstractAJAXResponse response) {
        setLastResponse(response);
        setLastModification(response.getTimestamp());
        if (response.hasError()) {
            setLastException(response.getException());
        }
    }

    public Appointment[] searchAppointment(String pattern, int folderId, Date start, Date end, int[] cols) throws OXException, IOException, JSONException {
        SearchRequest searchRequest = new SearchRequest(pattern, folderId, start, end, cols, -1, null, false, failOnError);
        SearchResponse response = client.execute(searchRequest);
        final JSONArray arr = (JSONArray) response.getResponse().getData();
        return CTMUtils.jsonArray2AppointmentArray(arr, cols, client.getValues().getTimeZone());
    }

    public Appointment[] freeBusy(int userId, int type, Date start, Date end) throws Exception {
        FreeBusyRequest freeBusyRequest = new FreeBusyRequest(userId, type, start, end);
        FreeBusyResponse response = client.execute(freeBusyRequest);

        final JSONArray arr = (JSONArray) response.getResponse().getData();
        return CTMUtils.jsonArray2AppointmentArray(arr, client.getValues().getTimeZone());
    }

    /**
     * Resets the permissions of the user's personal calendar folder to their defaults in case there are currently two or more permission
     * entities defined.
     */
    public void resetDefaultFolderPermissions() throws Exception {
        FolderObject folder = getClient().execute(new com.openexchange.ajax.folder.actions.GetRequest(EnumAPI.OX_OLD, getPrivateFolder())).getFolder();
        if (1 < folder.getPermissions().size()) {
            FolderObject folderUpdate = new FolderObject(getPrivateFolder());
            folderUpdate.setPermissionsAsArray(new OCLPermission[] { com.openexchange.ajax.folder.Create.ocl(
                getClient().getValues().getUserId(), false, true,
                OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION, OCLPermission.ADMIN_PERMISSION) });
            folderUpdate.setLastModified(new Date(Long.MAX_VALUE));
            getClient().execute(new com.openexchange.ajax.folder.actions.UpdateRequest(EnumAPI.OX_OLD, folderUpdate));
        }
    }

    public static Appointment createAppointmentObject(int parentFolderId, String title, Date start, Date end) {
        Appointment obj = new Appointment();
        obj.setTitle(title);
        obj.setStartDate(start);
        obj.setEndDate(end);
        obj.setParentFolderID(parentFolderId);
        obj.setShownAs(Appointment.ABSENT);
        return obj;
    }
}
