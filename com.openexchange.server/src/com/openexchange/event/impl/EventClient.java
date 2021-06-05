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

package com.openexchange.event.impl;

import static com.openexchange.java.Autoboxing.I;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import com.openexchange.context.ContextService;
import com.openexchange.event.CommonEvent;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXException.Generic;
import com.openexchange.file.storage.FileStorageEventConstants;
import com.openexchange.folder.FolderService;
import com.openexchange.group.Group;
import com.openexchange.group.GroupService;
import com.openexchange.groupware.Types;
import com.openexchange.groupware.container.CalendarObject;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.container.Participant;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.tasks.Task;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.tools.oxfolder.OXFolderAccess;

/**
 * Responsible for advertising {@link CommonEvent common Groupware events} to several notification/event mechanisms
 * <ul>
 * <li>Posting an {@link Event event} using <code>org.osgi.service.event.EventAdmin</code></li>
 * <li>Legacy <code>com.openexchange.event.impl.EventQueue</code></li>
 * <li><code>com.openexchange.pns.PushNotificationService</code></li>
 * </ul>
 *
 * @author <a href="mailto:sebastian.kauss@open-xchange.com">Sebastian Kauss</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a> JavaDoc
 */
public class EventClient {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EventClient.class);

    public static final int CREATED = 5;
    public static final int CHANGED = 6;
    public static final int DELETED = 7;
    public static final int MOVED = 8;
    public static final int CONFIRM_ACCEPTED = 9;
    public static final int CONFIRM_DECLINED = 10;
    public static final int CONFIRM_TENTATIVE = 11;
    public static final int CONFIRM_WAITING = 11;

    // -----------------------------------------------------------------------------------------

    private final Session session;
    private final int userId;
    private final int contextId;

    /**
     * Initializes a new {@link EventClient} using specified session.
     *
     * @param session The session
     */
    public EventClient(Session session) {
        this.session = session;
        userId = session.getUserId();
        contextId = session.getContextId();
    }

    public void create(final Task task, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new CalendarObject[] { task }, new FolderObject[] { folder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.INSERT, Types.TASK, task, null, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/task/insert", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(task, CREATED, session);
        EventQueue.add(eventObject);
    }

    public void modify(final Task oldTask, final Task newTask, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new CalendarObject[] { oldTask, newTask }, new FolderObject[] { folder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.UPDATE, Types.TASK, newTask, oldTask, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/task/update", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(oldTask, CHANGED, session);
        EventQueue.add(eventObject);
    }

    public void accept(final Task oldTask, final Task newTask) throws OXException {
        final Context ctx = ContextStorage.getInstance().getContext(contextId);

        final int folderId = newTask.getParentFolderID();
        if (folderId > 0) {
            final FolderObject folder = getFolder(folderId, ctx);
            accept(oldTask, newTask, folder);
        }
    }

    public void accept(final Task oldTask, final Task newTask, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new CalendarObject[] { oldTask, newTask }, new FolderObject[] { folder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.CONFIRM_ACCEPTED, Types.TASK, newTask, oldTask, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/task/accepted", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(oldTask, CONFIRM_ACCEPTED, session);
        EventQueue.add(eventObject);
    }

    public void declined(final Task oldTask, final Task newTask) throws OXException {
        final Context ctx = ContextStorage.getInstance().getContext(contextId);

        final int folderId = newTask.getParentFolderID();
        if (folderId > 0) {
            final FolderObject folder = getFolder(folderId, ctx);
            declined(oldTask, newTask, folder);
        }
    }

    public void declined(final Task oldTask, final Task newTask, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new CalendarObject[] { oldTask, newTask }, new FolderObject[] { folder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.CONFIRM_DECLINED, Types.TASK, newTask, oldTask, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/task/declined", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(oldTask, CONFIRM_DECLINED, session);
        EventQueue.add(eventObject);
    }

    public void tentative(final Task oldTask, final Task newTask) throws OXException {
        final Context ctx = ContextStorage.getInstance().getContext(contextId);

        final int folderId = newTask.getParentFolderID();
        if (folderId > 0) {
            final FolderObject folder = getFolder(folderId, ctx);
            tentative(oldTask, newTask, folder);
        }
    }

    public void tentative(final Task oldTask, final Task newTask, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new CalendarObject[] { oldTask, newTask }, new FolderObject[] { folder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.CONFIRM_TENTATIVE, Types.TASK, newTask, oldTask, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/task/tentative", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(oldTask, CONFIRM_TENTATIVE, session);
        EventQueue.add(eventObject);
    }

    public void delete(final Task task) throws OXException {
        final Context ctx = ContextStorage.getInstance().getContext(contextId);

        final int folderId = task.getParentFolderID();
        if (folderId > 0) {
            final FolderObject folder = getFolder(folderId, ctx);
            delete(task, folder);
        }
    }

    public void delete(final Task task, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new CalendarObject[] { task }, new FolderObject[] { folder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.DELETE, Types.TASK, task, null, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/task/delete", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(task, DELETED, session);
        EventQueue.add(eventObject);
    }

    public void move(final Task task, final FolderObject sourceFolder, final FolderObject destinationFolder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new CalendarObject[] { task }, new FolderObject[] { sourceFolder, destinationFolder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.MOVE, Types.TASK, task, null, sourceFolder, destinationFolder, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/task/move", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(task, DELETED, session);
        EventQueue.add(eventObject);
    }

    public void create(final Contact contact) throws OXException {
        final Context ctx = ContextStorage.getInstance().getContext(contextId);

        final int folderId = contact.getParentFolderID();
        if (folderId > 0) {
            final FolderObject folder = getFolder(folderId, ctx);
            create(contact, folder);
        }
    }

    public void create(final Contact contact, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new FolderObject[] { folder }, contact.getParentFolderID());
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.INSERT, Types.CONTACT, contact, null, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/contact/insert", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(contact, CREATED, session);
        EventQueue.add(eventObject);
    }

    public void modify(final Contact oldContact, final Contact newContact, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new FolderObject[] { folder }, oldContact.getParentFolderID(), newContact.getParentFolderID());
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.UPDATE, Types.CONTACT, newContact, oldContact, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/contact/update", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(newContact, CHANGED, session);
        EventQueue.add(eventObject);
    }

    public void delete(final Contact contact) throws OXException {
        final Context ctx = ContextStorage.getInstance().getContext(contextId);

        final int folderId = contact.getParentFolderID();
        if (folderId > 0) {
            final FolderObject folder = getFolder(folderId, ctx);
            delete(contact, folder);
        }
    }

    public void delete(final Contact contact, final FolderObject folder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new FolderObject[] { folder }, contact.getParentFolderID());
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.DELETE, Types.CONTACT, contact, null, folder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);


        final Event event = new Event("com/openexchange/groupware/contact/delete", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(contact, DELETED, session);
        EventQueue.add(eventObject);
    }

    public void move(final Contact contact, final FolderObject sourceFolder, final FolderObject destinationFolder) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new FolderObject[] { sourceFolder, destinationFolder }, contact.getParentFolderID());
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.MOVE, Types.CONTACT, contact, null, sourceFolder, destinationFolder, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/contact/move", ht);
        triggerEvent(event);

        final EventObject eventObject = new EventObject(contact, MOVED, session);
        EventQueue.add(eventObject);
    }

    /**
     * Raises a folder "create" event.
     *
     * @param folder The created folder
     */
    public void create(final FolderObject folder) throws OXException {
        final Context ctx = ContextStorage.getInstance().getContext(contextId);

        final int folderId = folder.getParentFolderID();
        if (folderId > 0) {
            final FolderObject parentFolderObj = getFolder(folderId, ctx);
            create(folder, parentFolderObj);
        }
    }

    /**
     * Raises a folder "create" event.
     *
     * @param folder The created folder
     * @param parentFolder The parent folder
     * @throws OXException
     */
    public void create(final FolderObject folder, final FolderObject parentFolder) throws OXException {
        create(folder, parentFolder, null);
    }

    /**
     * Raises a folder "create" event.
     *
     * @param folder The created folder
     * @param parentFolder The parent folder
     * @param folderPath The full path of the folder down to the root folder
     * @throws OXException
     */
    public void create(final FolderObject folder, final FolderObject parentFolder, String[] folderPath) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new FolderObject[] { folder, parentFolder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.INSERT, Types.FOLDER, folder, null, parentFolder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/folder/insert", ht);
        triggerEvent(event);
        if (null != folder && FolderObject.INFOSTORE == folder.getModule()) {
            triggerEvent(new Event(FileStorageEventConstants.CREATE_FOLDER_TOPIC, getEventProperties(folder, parentFolder, folderPath)));
        }

        final EventObject eventObject = new EventObject(folder, CREATED, session).setNoDelay(true);
        EventQueue.add(eventObject);
    }

    /**
     * Raises a folder "modify" event.
     *
     * @param oldFolder The old folder
     * @param newFolder The new folder
     * @param parentFolder The parent folder
     * @throws OXException
     */
    public void modify(final FolderObject oldFolder, final FolderObject newFolder, final FolderObject parentFolder) throws OXException {
        modify(oldFolder, newFolder, parentFolder, null);
    }

    /**
     * Raises a folder "modify" event.
     *
     * @param oldFolder The old folder
     * @param newFolder The new folder
     * @param parentFolder The parent folder
     * @param folderPath The full path of the folder down to the root folder
     * @throws OXException
     */
    public void modify(final FolderObject oldFolder, final FolderObject newFolder, final FolderObject parentFolder, String[] folderPath) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new FolderObject[] { oldFolder, newFolder, parentFolder }, oldFolder.getParentFolderID(), newFolder.getParentFolderID());
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.UPDATE, Types.FOLDER, newFolder, oldFolder, parentFolder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/folder/update", ht);
        triggerEvent(event);
        if (FolderObject.INFOSTORE == newFolder.getModule()) {
            Dictionary<String, Object> properties = getEventProperties(newFolder, parentFolder, folderPath);
            if (oldFolder.getParentFolderID() != newFolder.getParentFolderID()) {
                properties.put(FileStorageEventConstants.OLD_PARENT_FOLDER_ID, String.valueOf(oldFolder.getParentFolderID()));
            }
            triggerEvent(new Event(FileStorageEventConstants.UPDATE_FOLDER_TOPIC, properties));
        }

        final EventObject eventObject = new EventObject(newFolder, CHANGED, session).setNoDelay(true);
        EventQueue.add(eventObject);
    }

    /**
     * Raises a folder "delete" event.
     *
     * @param folder The folder
     * @throws OXException
     */
    public void delete(final FolderObject folder) throws OXException {
        final Context ctx = ContextStorage.getInstance().getContext(contextId);
        final int folderId = folder.getParentFolderID();
        if (folderId > 0) {
            FolderObject parentFolderObj = null;
            try {
                parentFolderObj = getFolder(folderId, ctx);
            } catch (OXException exc) {
                if (exc.isGeneric(Generic.NO_PERMISSION)) {
                    LOG.error("cannot load folder", exc);
                } else {
                    throw exc;
                }
            }
            delete(folder, parentFolderObj);
        }
    }

    /**
     * Raises a folder "delete" event.
     *
     * @param folder The folder
     * @param parentFolder The parent folder
     * @throws OXException
     */
    public void delete(final FolderObject folder, final FolderObject parentFolder) throws OXException {
        delete(folder, parentFolder, null);
    }

    /**
     * Raises a folder "delete" event.
     *
     * @param folder The folder
     * @param parentFolder The parent folder
     * @param folderPath The full path of the folder down to the root folder
     * @throws OXException
     */
    public void delete(final FolderObject folder, final FolderObject parentFolder, String[] folderPath) throws OXException {
        final Map<Integer, Set<Integer>> affectedUsers = getAffectedUsers(new FolderObject[] { folder, parentFolder });
        final CommonEvent genericEvent = new CommonEventImpl(contextId, userId, unmodifyable(affectedUsers), CommonEvent.DELETE, Types.FOLDER, folder, null, parentFolder, null, session);

        final Dictionary<String, CommonEvent> ht = new Hashtable<String, CommonEvent>(1);
        ht.put(CommonEvent.EVENT_KEY, genericEvent);

        final Event event = new Event("com/openexchange/groupware/folder/delete", ht);
        triggerEvent(event);
        if (null != parentFolder && FolderObject.INFOSTORE == parentFolder.getModule()) {
            triggerEvent(new Event(FileStorageEventConstants.DELETE_FOLDER_TOPIC, getEventProperties(folder, parentFolder, folderPath)));
        }

        final EventObject eventObject = new EventObject(folder, DELETED, session).setNoDelay(true);
        EventQueue.add(eventObject);
    }

    protected void triggerEvent(final Event event) throws OXException {
        final EventAdmin eventAdmin = ServerServiceRegistry.getInstance().getService(EventAdmin.class);
        if (eventAdmin == null) {
            throw new OXException().setLogMessage("event service not available");
        }
        eventAdmin.postEvent(event);
    }

    /**
     * Constructs the properties for a file storage folder event.
     *
     * @param folder The folder
     * @param parentFolder The parent folder
     * @param folderPath The folder path
     * @return The event properties
     */
    private Dictionary<String, Object> getEventProperties(FolderObject folder, FolderObject parentFolder, String[] folderPath) {
        Dictionary<String, Object> properties = new Hashtable<String, Object>(6);
        properties.put(FileStorageEventConstants.SESSION, session);
        properties.put(FileStorageEventConstants.FOLDER_ID, String.valueOf(folder.getObjectID()));
        properties.put(FileStorageEventConstants.ACCOUNT_ID, "infostore");
        properties.put(FileStorageEventConstants.SERVICE, "com.openexchange.infostore");
        if (null != parentFolder) {
            properties.put(FileStorageEventConstants.PARENT_FOLDER_ID, String.valueOf(parentFolder.getObjectID()));
        }
        if (null != folderPath) {
            properties.put(FileStorageEventConstants.FOLDER_PATH, folderPath);
        }
        return properties;
    }

    private FolderObject getFolder(final int folderId, final Context ctx) throws OXException {
        return new OXFolderAccess(ctx).getFolderObject(folderId);
    }

    private Map<Integer, Set<Integer>> getAffectedUsers(final FolderObject[] folders, final int... folderIds) throws OXException {
        final Map<Integer, Set<Integer>> retval = getAffectedUsers(folders);
        for (final int folderId : folderIds) {
            getFolderSet(retval, userId).add(I(folderId));
        }
        return retval;
    }

    private Map<Integer, Set<Integer>> getAffectedUsers(final FolderObject[] folders) throws OXException {
        final Map<Integer, Set<Integer>> retval = new HashMap<Integer, Set<Integer>>();
        retval.put(I(userId), new HashSet<Integer>());
        for (final FolderObject folder : folders) {
            addFolderToAffectedMap(retval, folder);
        }
        return retval;
    }

    private Map<Integer, Set<Integer>> getAffectedUsers(final CalendarObject[] objects, final FolderObject[] folders) throws OXException {
        final Map<Integer, Set<Integer>> retval = getAffectedUsers(folders);
        for (final CalendarObject object : objects) {
            if (null != object) {
                getFolderSet(retval, userId).add(I(object.getParentFolderID()));
                UserParticipant[] participants = object.getUsers();
                if (null != participants) {
                    for (final UserParticipant participant : participants) {
                        final int participantId = participant.getIdentifier();
                        if (Participant.NO_ID == participantId) {
                            continue;
                        }
                        getFolderSet(retval, participantId);
                        final int folderId = participant.getPersonalFolderId();
                        if (UserParticipant.NO_PFID == folderId || 0 == folderId) {
                            continue;
                        }
                        final FolderService folderService = ServerServiceRegistry.getInstance().getService(FolderService.class, true);
                        final FolderObject folder = folderService.getFolderObject(folderId, contextId);
                        addFolderToAffectedMap(retval, folder);
                    }
                }
            }
        }
        return retval;
    }

    private void addFolderToAffectedMap(final Map<Integer, Set<Integer>> retval, final FolderObject folder) throws OXException {
        for (final OCLPermission permission : folder.getPermissions()) {
            if (permission.isFolderVisible()) {
                if (permission.isGroupPermission()) {
                    final GroupService groupService = ServerServiceRegistry.getInstance().getService(GroupService.class, true);
                    final Group group = groupService.getGroup(getContext(contextId), permission.getEntity());
                    for (final int groupMember : group.getMember()) {
                        getFolderSet(retval, groupMember).add(I(folder.getObjectID()));
                    }
                } else {
                    getFolderSet(retval, permission.getEntity()).add(I(folder.getObjectID()));
                }
            }
        }
    }

    private static Set<Integer> getFolderSet(final Map<Integer, Set<Integer>> map, final int userId) {
        Set<Integer> retval = map.get(I(userId));
        if (null == retval) {
            retval = new HashSet<Integer>();
            map.put(I(userId), retval);
        }
        return retval;
    }

    private static Context getContext(final int contextId) throws OXException {
        final ContextService contextService = ServerServiceRegistry.getInstance().getService(ContextService.class, true);
        return contextService.getContext(contextId);
    }

    private static Map<Integer, Set<Integer>> unmodifyable(final Map<Integer, Set<Integer>> map) {
        for (final Entry<Integer, Set<Integer>> entry : map.entrySet()) {
            entry.setValue(Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }
}
