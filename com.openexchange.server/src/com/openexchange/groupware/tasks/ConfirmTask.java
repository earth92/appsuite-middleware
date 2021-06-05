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

package com.openexchange.groupware.tasks;

import static com.openexchange.database.Databases.autocommit;
import static com.openexchange.database.Databases.rollback;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import com.openexchange.event.impl.EventClient;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.CalendarObject;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.container.Participant;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.server.impl.DBPool;
import com.openexchange.session.Session;

/**
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class ConfirmTask {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ConfirmTask.class);

    private static final TaskStorage storage = TaskStorage.getInstance();
    private static final FolderStorage foldStor = FolderStorage.getInstance();
    private static final ParticipantStorage partStor = ParticipantStorage.getInstance();

    private static final int[] CHANGED_ATTRIBUTES = new int[] { Task.LAST_MODIFIED, Task.MODIFIED_BY };

    private final Context ctx;
    private final int taskId;
    private final int userId;
    private final int confirm;
    private final String message;

    private Task origTask;
    private FolderObject folder;
    private Set<TaskParticipant> participants;
    private Set<Folder> folders;
    private Task changedTask;
    private InternalParticipant origParticipant;
    private InternalParticipant changedParticipant;

    /**
     * Default constructor.
     */
    ConfirmTask(final Context ctx, final int taskId, final int userId, final int confirm, final String message) {
        super();
        this.ctx = ctx;
        this.taskId = taskId;
        this.userId = userId;
        this.confirm = confirm;
        this.message = message;
        init();
    }

    private void init() {
        try {
            this.origTask = storage.selectTask(ctx, taskId, StorageType.ACTIVE);
            this.participants = partStor.selectParticipants(ctx, taskId, StorageType.ACTIVE);
            this.folders = foldStor.selectFolder(ctx, taskId, StorageType.ACTIVE);
        } catch (OXException e) {
            LOG.error("Unable to init task: {}", e.getMessage(), e);
        }
    }

    // ===================== API methods =======================================

    /**
     * This method loads all necessary data and prepares the objects for updating
     * the database.
     */
    void prepare() throws OXException {
        this.origParticipant = ParticipantStorage.getParticipant(ParticipantStorage.extractInternal(this.participants), userId);
        if (null == origParticipant) {
            throw TaskExceptionCode.PARTICIPANT_NOT_FOUND.create(I(userId), I(taskId));
        }
        // Load full task.
        fillParticipants();
        fillTask();
        // Load participant and set confirmation
        changedParticipant = this.origParticipant.deepClone();
        changedParticipant.setConfirm(confirm);
        changedParticipant.setConfirmMessage(message);
        // Prepare changed task attributes.
        changedTask = new Task();
        changedTask.setObjectID(taskId);
        changedTask.setModifiedBy(userId);
        changedTask.setLastModified(new Date());
    }

    /**
     * This method does all the changes in the database in a transaction.
     */
    void doConfirmation() throws OXException {
        final Connection con = DBPool.pickupWriteable(ctx);
        try {
            con.setAutoCommit(false);

            partStor.updateInternal(ctx, con, taskId, changedParticipant, StorageType.ACTIVE);
            UpdateData.updateTask(ctx, con, changedTask, this.origTask.getLastModified(), CHANGED_ATTRIBUTES, null, null, null, null);
            con.commit();
        } catch (SQLException e) {
            rollback(con);
            throw TaskExceptionCode.SQL_ERROR.create(e);
        } finally {
            autocommit(con);
            DBPool.closeWriterSilent(ctx, con);
        }
    }

    void sentEvent(final Session session) throws OXException {
        final Task orig = this.origTask;
        if (userId != orig.getCreatedBy() && null == ParticipantStorage.getParticipant(ParticipantStorage.extractInternal(this.participants), orig.getCreatedBy())) {
            // Delegator is not participant and participant changed task. Change parent folder of original task to delegators folder
            // identifier so we are able to use that for participant notification.
            Folder delegatorFolder = FolderStorage.extractFolderOfUser(this.folders, orig.getCreatedBy());
            if (null != delegatorFolder) {
                orig.setParentFolderID(delegatorFolder.getIdentifier());
            } else {
                // Another user created the task in a shared folder. Normally there can be only one user having the task in its folder who
                // is not participant. And this is the delegator of the task.
                Set<Folder> nonParticipantFolder = FolderStorage.extractNonParticipantFolder(this.folders, ParticipantStorage.extractInternal(this.participants));
                if (nonParticipantFolder.size() > 0) {
                    orig.setParentFolderID(nonParticipantFolder.iterator().next().getIdentifier());
                } else {
                    throw TaskExceptionCode.UNKNOWN_DELEGATOR.create(I(orig.getCreatedBy()));
                }
            }
        }
        //FIXME - uebergebene Tasks sind schon gleich. Kein Unterschied bei Participants - orig ist falsch
        final EventClient eventClient = new EventClient(session);
        switch (changedParticipant.getConfirm()) {
            case CalendarObject.ACCEPT:
                eventClient.accept(orig, getFilledChangedTask());
                break;
            case CalendarObject.DECLINE:
                eventClient.declined(orig, getFilledChangedTask());
                break;
            case CalendarObject.TENTATIVE:
                eventClient.tentative(orig, getFilledChangedTask());
                break;
        }
    }

    /**
     * Gives the new last modified attribute of the changed task. This can be
     * only requested after {@link #prepare()} has been called.
     * 
     * @return the new last modified of the changed task.
     */
    Date getLastModified() {
        return changedTask.getLastModified();
    }

    // =========================== internal helper methods =====================

    //    private Task getOrigTask() throws OXException {
    //        if (null == origTask) {
    //            origTask = storage.selectTask(ctx, taskId, StorageType.ACTIVE);
    //        }
    //        return origTask;
    //    }

    //    /**
    //     * @return the participant getting the confirmation applied.
    //     */
    //    private InternalParticipant getOrigParticipant() throws OXException {
    //        if (null == origParticipant) {
    //            origParticipant = ParticipantStorage.getParticipant(ParticipantStorage.extractInternal(getParticipants()), userId);
    //            if (null == origParticipant) {
    //                throw TaskExceptionCode.PARTICIPANT_NOT_FOUND.create(I(userId), I(taskId));
    //            }
    //        }
    //        return origParticipant;
    //    }

    /**
     * @return the folder of the confirming participant through that the task is seen.
     */
    private FolderObject getFolder() throws OXException {
        if (null == folder) {
            Folder tmpFolder = FolderStorage.extractFolderOfUser(this.folders, userId);
            if (null == tmpFolder) {
                if (this.folders.isEmpty()) {
                    throw TaskExceptionCode.MISSING_FOLDER.create(I(taskId));
                }
                tmpFolder = this.folders.iterator().next();
            }
            folder = Tools.getFolder(ctx, tmpFolder.getIdentifier());
        }
        return folder;
    }

    private boolean filledParts = false;

    private void fillParticipants() throws OXException {
        if (filledParts) {
            return;
        }
        if (!Tools.isFolderPublic(getFolder())) {
            Tools.fillStandardFolders(ctx.getContextId(), taskId, this.participants, this.folders, true);
        }
        filledParts = true;
    }

    private boolean filledTask = false;

    private void fillTask() throws OXException {
        if (filledTask) {
            return;
        }
        Task task = this.origTask;
        task.setParticipants(TaskLogic.createParticipants(this.participants));
        task.setUsers(TaskLogic.createUserParticipants(this.participants));
        task.setParentFolderID(getFolder().getObjectID());
        filledTask = true;
    }

    //    private Set<TaskParticipant> getParticipants() throws OXException {
    //        if (null == participants) {
    //            participants = partStor.selectParticipants(ctx, taskId, StorageType.ACTIVE);
    //        }
    //        return participants;
    //    }

    //    private Set<Folder> getFolders() throws OXException {
    //        if (null == folders) {
    //            folders = foldStor.selectFolder(ctx, taskId, StorageType.ACTIVE);
    //        }
    //        return folders;
    //    }

    private boolean filledChangedTask = false;

    private Task getFilledChangedTask() throws OXException {
        if (!filledChangedTask) {
            final Task oldTask = this.origTask;
            for (final Mapper mapper : Mapping.MAPPERS) {
                if (!mapper.isSet(changedTask) && mapper.isSet(oldTask)) {
                    mapper.set(changedTask, mapper.get(oldTask));
                }
            }

            List<Participant> newParticipants = new ArrayList();
            for (Participant p : origTask.getParticipants()) {
                try {
                    newParticipants.add(p.getClone());
                } catch (CloneNotSupportedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            changedTask.setParticipants(newParticipants);
            //            List<Participant> participants2 = Arrays.asList(origTask.getParticipants());
            for (Participant part : changedTask.getParticipants()) {
                if (part.getIdentifier() == changedParticipant.getIdentifier()) {
                    if (part instanceof UserParticipant) {
                        UserParticipant updatedParticipant = (UserParticipant) part;
                        updatedParticipant.setConfirm(changedParticipant.getConfirm());
                        updatedParticipant.setConfirmMessage(changedParticipant.getConfirmMessage());
                    }
                    break;
                }
            }
            changedTask.setUsers(origTask.getUsers());
            changedTask.setParentFolderID(oldTask.getParentFolderID());
        }
        return changedTask;
    }
}
