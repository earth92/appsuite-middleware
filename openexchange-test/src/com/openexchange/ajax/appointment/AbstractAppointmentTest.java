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

import static com.openexchange.server.impl.OCLPermission.CREATE_SUB_FOLDERS;
import static com.openexchange.server.impl.OCLPermission.DELETE_ALL_OBJECTS;
import static com.openexchange.server.impl.OCLPermission.NO_PERMISSIONS;
import static com.openexchange.server.impl.OCLPermission.READ_ALL_OBJECTS;
import static com.openexchange.server.impl.OCLPermission.READ_FOLDER;
import static com.openexchange.server.impl.OCLPermission.WRITE_ALL_OBJECTS;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import com.google.common.collect.Lists;
import com.openexchange.ajax.appointment.AppointmentRangeGenerator.AppointmentRange;
import com.openexchange.ajax.appointment.action.AppointmentInsertResponse;
import com.openexchange.ajax.appointment.action.AppointmentUpdatesResponse;
import com.openexchange.ajax.appointment.action.DeleteRequest;
import com.openexchange.ajax.appointment.action.GetRequest;
import com.openexchange.ajax.appointment.action.GetResponse;
import com.openexchange.ajax.appointment.action.InsertRequest;
import com.openexchange.ajax.appointment.action.UpdateRequest;
import com.openexchange.ajax.appointment.action.UpdateResponse;
import com.openexchange.ajax.appointment.action.UpdatesRequest;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.folder.Create;
import com.openexchange.ajax.folder.actions.EnumAPI;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AbstractAJAXSession;
import com.openexchange.ajax.framework.AbstractUpdatesRequest.Ignore;
import com.openexchange.ajax.framework.CommonDeleteResponse;
import com.openexchange.ajax.framework.MultipleRequest;
import com.openexchange.ajax.framework.MultipleResponse;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Appointment;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.java.util.Pair;
import com.openexchange.server.impl.OCLPermission;

/**
 * {@link AbstractAppointmentTest} - Common CRUD functionality for AppointmentTests extending the AbstractAJAXSession.
 * 
 * @author <a href="mailto:marc.arens@open-xchange.com">Marc Arens</a>
 */
public class AbstractAppointmentTest extends AbstractAJAXSession {

    /**
     * Initializes a new {@link AbstractAppointmentTest}.
     * 
     * @param name The name of the test
     */
    protected AbstractAppointmentTest() {
        super();
    }

    protected int appointmentFolderId = -1;

    protected long dateTime = 0;

    protected int userId = 0;

    protected TimeZone timezone;

    protected AppointmentRangeGenerator appointmentRangeGenerator;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        appointmentFolderId = getClient().getValues().getPrivateAppointmentFolder();
        userId = getClient().getValues().getUserId();
        timezone = getClient().getValues().getTimeZone();

        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        dateTime = calendar.getTimeInMillis();

        appointmentRangeGenerator = new AppointmentRangeGenerator(calendar);
    }

    /**
     * Create several simple (non-conflicting, free, 1 hour long) appointments for the current month and persist them on the server
     * 
     * @param title The prefix of the appointment's title, final title will be title-{0..amount-1}
     * @param amount The amount of appointments to create
     * @return a list of created appointments
     * @throws Exception
     */
    protected List<Appointment> createAndPersistSeveral(final String title, int amount) throws Exception {
        List<Appointment> severalAppointments = createSeveral(title, amount);
        severalAppointments = persistSeveral(severalAppointments);
        return severalAppointments;
    }

    /**
     * Create several simple (non-conflicting, absent) new appointments for the current month and persist them on the server
     * 
     * @param title The prefix of the appointment's title, final title will be title-{0..amount-1}
     * @param amount The amount of appointments to create
     * @return a list of created appointments
     */
    protected List<Appointment> createSeveral(final String title, final int amount) {
        List<Appointment> newAppointments = new ArrayList<Appointment>(amount);
        for (int i = 0; i < amount; i++) {
            String serialTitle = title + "-" + i;
            AppointmentRange dateRange = appointmentRangeGenerator.getDateRange();
            Appointment newAppointment = createSingle(dateRange.startDate, dateRange.endDate, serialTitle);
            newAppointments.add(newAppointment);
        }
        return newAppointments;
    }

    /**
     * Create a single appointment in the client's appointment folder. Ignores conflicts, shown as free.
     * 
     * @param startDate The start date of the appointment
     * @param endDate The start date of the appointment
     * @param title The title of the appointment
     * @return The new appointment
     */
    protected Appointment createSingle(final Date startDate, final Date endDate, final String title) {
        final Appointment newAppointment = new Appointment();
        newAppointment.setParentFolderID(appointmentFolderId);
        newAppointment.setShownAs(Appointment.FREE);
        newAppointment.setIgnoreConflicts(true);
        newAppointment.setTitle(title);
        newAppointment.setStartDate(startDate);
        newAppointment.setEndDate(endDate);
        return newAppointment;
    }

    /**
     * Persist a single appointment
     * 
     * @param appointment the appointment to persist
     * @return the persisted appointment
     * @throws Exception
     */
    public Appointment persistAppointment(Appointment appointment) throws Exception {
        return persistAppointment(null, appointment);
    }

    /**
     * Persist a single appointment
     * 
     * @param selectedClient selectedClient The client to use. If the client is null the default client for User1 will be used
     * @param appointment the appointment to persist
     * @return the persisted appointment
     * @throws Exception
     */
    public Appointment persistAppointment(AJAXClient selectedClient, Appointment appointment) throws Exception {
        if (selectedClient == null) {
            selectedClient = getClient();
        }
        return persistSeveral(Collections.singletonList(appointment)).get(0);
    }

    public List<Appointment> persistAppointments(Appointment... appointments) throws Exception {
        return persistSeveral(Lists.newArrayList(appointments));
    }

    /**
     * Persist several appointments on the server.
     * 
     * @param newAppointments the appointments to persist
     * @return the persisted appointments with updated id and lastmodified infos
     * @throws Exception
     */
    public List<Appointment> persistSeveral(List<Appointment> newAppointments) throws Exception {
        return persistSeveral(null, newAppointments);
    }

    /**
     * Persist several appointments on the server.
     *
     * @param selectedClient selectedClient The client to use. If the client is null the default client for User1 will be used
     * @param newAppointments the appointments to persist
     * @return the persisted appointments with updated id and lastmodified infos
     * @throws Exception
     */
    public List<Appointment> persistSeveral(AJAXClient selectedClient, List<Appointment> newAppointments) throws Exception {
        if (selectedClient == null) {
            selectedClient = getClient();
        }
        int numberOfAppointments = newAppointments.size();
        List<InsertRequest> insertAppointmentRequests = new ArrayList<InsertRequest>(numberOfAppointments);
        for (Appointment appointment : newAppointments) {
            insertAppointmentRequests.add(new InsertRequest(appointment, timezone));
        }
        MultipleRequest<AppointmentInsertResponse> multipleRequest = MultipleRequest.create(insertAppointmentRequests.toArray(new InsertRequest[numberOfAppointments]));
        MultipleResponse<AppointmentInsertResponse> multipleResponse = getClient().execute(multipleRequest);
        return updateAppointmentsWithTimeAndId(newAppointments, multipleResponse);
    }

    /**
     * Update one or several appointments on the server and additionally update the lastmodified infos.
     * 
     * @param appointments The appointments to update
     */
    public void updateAppointments(List<Appointment> appointments) {
        updateAppointments(appointments.toArray(new Appointment[appointments.size()]));
    }

    /**
     * Update one or several appointments on the server and additionally update the lastmodified infos.
     * 
     * @param appointments The appointments to update
     */
    public void updateAppointments(Appointment... appointments) {
        int numAppointments = appointments.length;
        UpdateRequest[] updateRequests = new UpdateRequest[numAppointments];
        for (int i = 0; i < numAppointments; i++) {
            Appointment appointment = appointments[i];
            appointment.setTitle(appointment.getTitle() + " was updated");
            updateRequests[i] = new UpdateRequest(appointment, timezone);
        }
        MultipleRequest<UpdateResponse> multipleUpdate = MultipleRequest.create(updateRequests);
        MultipleResponse<UpdateResponse> multipleResponse = getClient().executeSafe(multipleUpdate);
        for (int i = 0; i < numAppointments; i++) {
            appointments[i].setLastModified(multipleResponse.getResponse(i).getTimestamp());
        }
    }

    /**
     * Update and move one or several appointments on the server and additionally update the lastmodified infos.
     * 
     * @param appointments The Pairs of appointment and origin folder that should be updated
     */
    public void updateAppointmentsWithOrigin(List<Pair<Appointment, FolderObject>> appointments) {
        updateAppointmentsWithOrigin(null, appointments);
    }

    /**
     * Update and move one or several appointments on the server and additionally update the lastmodified infos.
     * 
     * @param selectedClient The client to use for the update, uses default client for User1 when the parameter is null
     * @param appointments The Pairs of appointment and origin folder that should be updated
     */
    public void updateAppointmentsWithOrigin(AJAXClient selectedClient, List<Pair<Appointment, FolderObject>> appointments) {
        if (selectedClient == null) {
            selectedClient = getClient();
        }
        int numAppointments = appointments.size();
        UpdateRequest[] updateRequests = new UpdateRequest[numAppointments];
        for (int i = 0; i < numAppointments; i++) {
            Appointment appointment = appointments.get(i).getFirst();
            FolderObject originFolder = appointments.get(i).getSecond();
            updateRequests[i] = new UpdateRequest(originFolder.getObjectID(), appointment, timezone, true);
        }
        MultipleRequest<UpdateResponse> multipleUpdate = MultipleRequest.create(updateRequests);
        MultipleResponse<UpdateResponse> multipleResponse = selectedClient.executeSafe(multipleUpdate);
        for (int i = 0; i < numAppointments; i++) {
            appointments.get(i).getFirst().setLastModified(multipleResponse.getResponse(i).getTimestamp());
        }
    }

    /**
     * Update one or several appointments on the server and additionally update the lastmodified infos.
     * 
     * @param appointments The appointments to update
     */
    public void updateAppointments(AJAXClient selectedClient, Appointment... appointments) {
        if (selectedClient == null) {
            selectedClient = getClient();
        }
        int numAppointments = appointments.length;
        UpdateRequest[] updateRequests = new UpdateRequest[numAppointments];
        for (int i = 0; i < numAppointments; i++) {
            Appointment appointment = appointments[i];
            appointment.setTitle(appointment.getTitle() + " was updated");
            updateRequests[i] = new UpdateRequest(appointment, timezone);
        }
        MultipleRequest<UpdateResponse> multipleUpdate = MultipleRequest.create(updateRequests);
        MultipleResponse<UpdateResponse> multipleResponse = selectedClient.executeSafe(multipleUpdate);
        for (int i = 0; i < numAppointments; i++) {
            appointments[i].setLastModified(multipleResponse.getResponse(i).getTimestamp());
        }
    }

    /**
     * Delete one or several appointments on the server.
     * 
     * @param appointments The appointments to delete
     */
    public void deleteAppointments(List<Appointment> appointments) {
        deleteAppointments(appointments.toArray(new Appointment[appointments.size()]));
    }

    /**
     * Delete one or several appointments on the server.
     * 
     * @param appointments The appointments to delete
     */
    public void deleteAppointments(Appointment... appointments) {
        deleteAppointments(getClient(), appointments);
    }

    /**
     * Delete one or several appointments on the server.
     * 
     * @param selectedClient The client to use. If the client is null the default client for User1 will be used
     * @param appointments The appointments to delete
     */
    public void deleteAppointments(AJAXClient selectedClient, Appointment... appointments) {
        if (selectedClient == null) {
            selectedClient = getClient();
        }

        int numContacts = appointments.length;
        DeleteRequest[] deleteRequests = new DeleteRequest[numContacts];
        for (int i = 0; i < numContacts; i++) {
            Appointment appointment = appointments[i];
            deleteRequests[i] = new DeleteRequest(appointment);
        }
        MultipleRequest<CommonDeleteResponse> multipleDelete = MultipleRequest.create(deleteRequests);
        getClient().executeSafe(multipleDelete);
    }

    /**
     * Update the appointments with the infos from the MultipleResponse
     * 
     * @param appointments the appointments
     * @param insertResponses the MultipleResponse
     * @return the appointments with updated id and lastmodified infos
     * @throws Exception
     */
    private List<Appointment> updateAppointmentsWithTimeAndId(List<Appointment> appointments, MultipleResponse<AppointmentInsertResponse> insertResponses) throws Exception {

        for (int i = 0; i < appointments.size(); i++) {
            Appointment currentAppointment = appointments.get(i);
            Response currentResponse = insertResponses.getResponse(i).getResponse();
            Date timestamp = currentResponse.getTimestamp();
            JSONObject responseData = (JSONObject) currentResponse.getData();
            currentAppointment.setLastModified(timestamp);
            currentAppointment.setObjectID(responseData.getInt("id"));
        }
        return appointments;
    }

    /**
     * @param inFolder Folder id to use for the request
     * @param cols Columns to use for the request
     * @param lastModified The timestamp of the last update of the requested appointments
     * @return The UpdatesResponse containg new, modified and deleted appointments
     * @throws Exception
     */
    public AppointmentUpdatesResponse listModifiedAppointments(final int inFolder, int[] cols, final Date lastModified) throws Exception {
        return listModifiedAppointments(null, inFolder, cols, lastModified);
    }

    /**
     * @param selectedClient The client to use for executing the request
     * @param inFolder Folder id to use for the request
     * @param cols Columns to use for the request
     * @param lastModified The timestamp of the last update of the requested appointments
     * @return The UpdatesResponse containg new, modified and deleted appointments
     * @throws Exception
     */
    public AppointmentUpdatesResponse listModifiedAppointments(AJAXClient selectedClient, final int inFolder, int[] cols, final Date lastModified) throws Exception {
        if (selectedClient == null) {
            selectedClient = getClient();
        }
        final UpdatesRequest request = new UpdatesRequest(inFolder, cols, lastModified, false, Ignore.NONE);
        final AppointmentUpdatesResponse response = selectedClient.execute(request);
        return response;
    }

    /**
     * Create a new calendar subfolder
     * 
     * @param user The user that creates the new subfolder
     * @param folderName The name of the new Folder
     * @param permission
     * @throws JSONException
     * @throws IOException
     * @throws OXException
     */
    public FolderObject createCalendarSubFolder(AJAXClient selectedClient, String folderName, OCLPermission... folderPermissions) throws OXException, IOException, JSONException {
        FolderObject folderObject = Create.folder(selectedClient.getValues().getPrivateAppointmentFolder(), folderName, FolderObject.CALENDAR, FolderObject.PRIVATE, folderPermissions);
        com.openexchange.ajax.folder.actions.InsertRequest insFolder = new com.openexchange.ajax.folder.actions.InsertRequest(EnumAPI.OX_OLD, folderObject);
        com.openexchange.ajax.folder.actions.InsertResponse folderInsertResponse = selectedClient.execute(insFolder);
        folderObject.setObjectID(folderInsertResponse.getId());
        folderObject.setLastModified(selectedClient.execute(new com.openexchange.ajax.folder.actions.GetRequest(EnumAPI.OX_OLD, folderObject.getObjectID())).getTimestamp());
        return folderObject;
    }

    /**
     * @param selectedClient the client that is used for executing the delete request, if null client for default user User1 will be used
     * @param folder The folder to delete
     * @throws Exception
     */
    protected void deleteCalendarFolder(AJAXClient selectedClient, FolderObject folder) throws Exception {
        if (selectedClient == null) {
            selectedClient = getClient();
        }
        selectedClient.execute(new com.openexchange.ajax.folder.actions.DeleteRequest(EnumAPI.OX_OLD, folder));

    }

    /**
     * Creates the following OCLPermission: view folder, read all objects, no edit permissions, no delete permissions, no admin
     * 
     * @param entity The entity to associate with this permission
     * @return new the OCLPermission
     */
    public static OCLPermission createGuestPermission(int entity) {
        return Create.ocl(entity, false, false, READ_FOLDER, READ_ALL_OBJECTS, NO_PERMISSIONS, NO_PERMISSIONS);
    };

    /**
     * Creates the following OCLPermission: create objects and subfolders, read all objects, edit all objects, delete all objects, no admin
     * 
     * @param entity The entity to associate with this permission
     * @return new the OCLPermission
     */
    public static OCLPermission createAuthorPermission(int entity) {
        return Create.ocl(entity, false, false, CREATE_SUB_FOLDERS, READ_ALL_OBJECTS, WRITE_ALL_OBJECTS, DELETE_ALL_OBJECTS);
    };

    /**
     * Creates the following OCLPermission: create objects and subfolders, read all objects, edit all objects, delete all objects, admin
     * 
     * @param entity The entity to associate with this permission
     * @return new the OCLPermission
     */
    public static OCLPermission createOwnerPermission(int entity) {
        return Create.ocl(entity, false, true, CREATE_SUB_FOLDERS, READ_ALL_OBJECTS, WRITE_ALL_OBJECTS, DELETE_ALL_OBJECTS);
    };

    /**
     * Get a single appointment from a given folder
     * 
     * @param folderId The folder where the appointment is located
     * @param appointmentId the appointment to get
     * @return The found appointment
     * @throws Exception when the resulting appointment couldn't be read
     */
    public Appointment getAppointment(int folderId, int appointmentId) throws Exception {
        return getAppointment(null, folderId, appointmentId);
    }

    /**
     * Get a single appointment from a given folder
     * 
     * @param selectedClient The cliet to use for executing the request
     * @param folderId The folder where the appointment is located
     * @param appointmentId the appointment to get
     * @return The found appointment
     * @throws Exception when the resulting appointment couldn't be read
     */
    public Appointment getAppointment(AJAXClient selectedClient, int folderId, int appointmentId) throws Exception {
        if (selectedClient == null) {
            selectedClient = getClient();
        }
        final GetRequest getRequest = new GetRequest(folderId, appointmentId);
        final GetResponse getResponse = selectedClient.execute(getRequest);
        return getResponse.getAppointment(selectedClient.getValues().getTimeZone());
    }

}
