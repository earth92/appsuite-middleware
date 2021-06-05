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

package com.openexchange.share.core.tools;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.I2i;
import static com.openexchange.java.Autoboxing.i;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.cache.impl.FolderCacheManager;
import com.openexchange.contact.ContactService;
import com.openexchange.contact.storage.ContactUserStorage;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageObjectPermission;
import com.openexchange.file.storage.FileStoragePermission;
import com.openexchange.folderstorage.Folder;
import com.openexchange.folderstorage.Permission;
import com.openexchange.group.Group;
import com.openexchange.group.GroupService;
import com.openexchange.groupware.contact.ContactExceptionCodes;
import com.openexchange.groupware.contact.ContactUtil;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.ldap.LdapExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.share.GuestInfo;
import com.openexchange.share.ShareInfo;
import com.openexchange.share.ShareService;
import com.openexchange.share.ShareTarget;
import com.openexchange.share.groupware.ModuleSupport;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import com.openexchange.user.UserExceptionCode;
import com.openexchange.user.UserService;

/**
 * {@link PermissionResolver}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class PermissionResolver {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PermissionResolver.class);

    private static final ContactField[] CONTACT_FIELDS = {
        ContactField.INTERNAL_USERID, ContactField.OBJECT_ID, ContactField.FOLDER_ID, ContactField.LAST_MODIFIED,
        ContactField.EMAIL1, ContactField.DISPLAY_NAME, ContactField.TITLE, ContactField.SUR_NAME, ContactField.GIVEN_NAME,
        ContactField.NUMBER_OF_IMAGES, ContactField.IMAGE1_CONTENT_TYPE, ContactField.IMAGE_LAST_MODIFIED
    };

    private final ServiceLookup services;
    private final ServerSession session;
    private final Map<Integer, User> knownUsers;
    private final Map<Integer, GuestInfo> knownGuests;
    private final Map<Integer, Contact> knownUserContacts;
    private final Map<Integer, Group> knownGroups;

    /**
     * Initializes a new {@link PermissionResolver}.
     * <p/>
     * <b>Note: </b> The service lookup reference should provide access to the following services:
     * <ul>
     * <li>{@link ModuleSupport}</li>
     * <li>{@link ShareService}</li>
     * <li>{@link ContactUserStorage}</li>
     * <li>{@link GroupService}</li>
     * <li>{@link UserService}</li>
     * <li>{@link GroupService}</li>
     * <li>{@link ContactService}</li>
     * </ul>
     *
     * @param services The service lookup reference
     * @param session The server session
     */
    public PermissionResolver(ServiceLookup services, ServerSession session) {
        super();
        this.services = services;
        this.session = session;
        knownGroups = new HashMap<Integer, Group>();
        knownGuests = new HashMap<Integer, GuestInfo>();
        knownUserContacts = new HashMap<Integer, Contact>();
        knownUsers = new HashMap<Integer, User>();
    }

    /**
     * Gets information about the share behind a guest permission entity of a specific folder.
     *
     * @param folder The folder to get the share for
     * @param guestID The guest entity to get the share for
     * @return The share, or <code>null</code> if not found
     */
    public ShareInfo getShare(Folder folder, int guestID) {
        if (null == folder.getContentType()) {
            throw new UnsupportedOperationException("no content type available");
        }
        return getLink(folder.getContentType().getModule(), folder.getID(), null, guestID);
    }

    /**
     * Gets information about the share behind a guest permission entity of a specific folder.
     *
     * @param folder The folder to get the share for
     * @param guestID The guest entity to get the share for
     * @return The share, or <code>null</code> if not found
     */
    public ShareInfo getShare(FileStorageFolder folder, int guestID) {
        return getLink(FolderObject.INFOSTORE, folder.getId(), null, guestID);
    }

    /**
     * Gets information about the share behind a guest permission entity of a specific folder.
     *
     * @param file The file to get the share for
     * @param guestID The guest entity to get the share for
     * @return The share, or <code>null</code> if not found
     */
    public ShareInfo getLink(File file, int guestID) {
        return getLink(FolderObject.INFOSTORE, file.getFolderId(), file.getId(), guestID);
    }

    /**
     * Gets information about the share behind a guest permission entity of a specific folder or file.
     *
     * @param moduleID The module identifier
     * @param folder The folder
     * @param item The item, or <code>null</code> if not applicable
     * @param guestID The guest entity to get the share for
     * @return The share, or <code>null</code> if not found
     */
    private ShareInfo getLink(int moduleID, String folder, String item, int guestID) {
        String module = services.getService(ModuleSupport.class).getShareModule(moduleID);
        ShareTarget target = new ShareTarget(moduleID, folder, item);
        try {
            return services.getService(ShareService.class).optLink(session, target);
        } catch (OXException e) {
            LOGGER.error("Error getting share link for folder {}, item {} in module {}", folder, item, module, e);
        }
        return null;
    }

    /**
     * Gets a specific guest.
     *
     * @param guestID The identifier of the guest to get
     * @return The guest, or <code>null</code> if it can't be resolved
     */
    public GuestInfo getGuest(int guestID) {
        Integer key = I(guestID);
        GuestInfo guest = knownGuests.get(key);
        if (null == guest && 0 < guestID) {
            try {
                guest = services.getService(ShareService.class).getGuestInfo(session, guestID);
                if (guest != null) {
                    knownGuests.put(key, guest);
                }
            } catch (OXException e) {
                LOGGER.error("Error getting guest {}", key, e);
            }
        }
        return guest;
    }

    /**
     * Gets the URL for a user's contact image.
     *
     * @param userID The user to get the image URL for
     * @return The image URL, or <code>null</code> if not available
     */
    public String getImageURL(int userID) {
        Contact userContact = getUserContact(userID);
        if (null != userContact && 0 < userContact.getNumberOfImages()) {
            try {
                return ContactUtil.generateImageUrl(session, userContact);
            } catch (OXException e) {
                LOGGER.error("Error generating image URL for user {}", I(userID), e);
            }
        }
        return null;
    }

    /**
     * Gets a specific group. Group members are not resolved.
     *
     * @param groupID The identifier of the group to get
     * @return The group, or <code>null</code> if it can't be resolved
     */
    public Group getGroup(int groupID) {
        Integer key = I(groupID);
        Group group = knownGroups.get(key);
        if (null == group && 0 <= groupID) {
            try {
                group = services.getService(GroupService.class).getGroup(session.getContext(), groupID, false);
                knownGroups.put(key, group);
            } catch (OXException e) {
                LOGGER.error("Error getting group {}", key, e);
            }
        }
        return group;
    }

    /**
     * Gets a specific user.
     *
     * @param userID The identifier of the user to get
     * @return The user, or <code>null</code> if it can't be resolved
     */
    public User getUser(int userID) {
        Integer key = I(userID);
        User user = knownUsers.get(key);
        if (null == user && 0 < userID) {
            try {
                user = services.getService(UserService.class).getUser(userID, session.getContext());
                knownUsers.put(key, user);
            } catch (OXException e) {
                if (UserExceptionCode.USER_NOT_FOUND.equals(e)) {
                    LOGGER.debug("Error getting user {}.", key, e);
                } else {
                    LOGGER.error("Error getting user {}.", key, e);
                }
            }
        }
        return user;
    }

    /**
     * Gets a specific user contact.
     *
     * @param userID The identifier of the user contact to get
     * @return The user contact, or <code>null</code> if it can't be resolved
     */
    public Contact getUserContact(int userID) {
        Integer key = I(userID);
        Contact userContact = knownUserContacts.get(key);
        if (null == userContact && 0 < userID) {
            User user = getUser(userID);
            if (null != user && user.isGuest()) {
                try {
                    userContact = services.getService(ContactUserStorage.class).getGuestContact(session.getContextId(), userID, CONTACT_FIELDS);
                    knownUserContacts.put(key, userContact);
                } catch (OXException e) {
                    if (ContactExceptionCodes.CONTACT_NOT_FOUND.equals(e)) {
                        LOGGER.debug("Error getting guest user contact {}", key, e);
                    } else {
                        LOGGER.error("Error getting guest user contact {}", key, e);
                    }
                }
            } else {
                try {
                    userContact = services.getService(ContactService.class).getUser(session, userID, CONTACT_FIELDS);
                    knownUserContacts.put(key, userContact);
                } catch (OXException e) {
                    if (ContactExceptionCodes.CONTACT_NOT_FOUND.equals(e)) {
                        LOGGER.debug("Error getting user contact {}", key, e);
                    } else {
                        LOGGER.error("Error getting user contact {}", key, e);
                    }
                }
            }
        }
        return userContact;
    }

    /**
     * Caches the permission entities found in the supplied list of folders.
     *
     * @param folders The folders to cache the permission entities for
     */
    public void cacheFileStorageFolderPermissionEntities(List<FileStorageFolder> folders) {
        /*
         * collect user- and group identifiers
         */
        Set<Integer> userIDs = new HashSet<Integer>();
        Set<Integer> groupIDs = new HashSet<Integer>();
        for (FileStorageFolder folder : folders) {
            List<FileStoragePermission> permissions = folder.getPermissions();
            if (null == permissions ) {
                continue;
            }
            for (FileStoragePermission permission : permissions) {
                if (0 > permission.getEntity()) {
                    LOGGER.debug("Skipping invalid entity {} in permissions of folder {}.", I(permission.getEntity()), folder.getId());
                } else if (permission.isGroup()) {
                    groupIDs.add(I(permission.getEntity()));
                } else {
                    userIDs.add(I(permission.getEntity()));
                }
            }
        }
        cachePermissionEntities(userIDs, groupIDs);
    }

    /**
     * Caches the permission entities found in the supplied list of folders.
     *
     * @param folders The folders to cache the permission entities for
     */
    public void cacheFolderPermissionEntities(List<Folder> folders) {
        /*
         * collect user- and group identifiers
         */
        Set<Integer> userIDs = new HashSet<Integer>();
        Set<Integer> groupIDs = new HashSet<Integer>();
        for (Folder folder : folders) {
            Permission[] oclPermissions = folder.getPermissions();
            if (null == oclPermissions ) {
                continue;
            }
            for (Permission oclPermission : oclPermissions) {
                if (0 > oclPermission.getEntity()) {
                    LOGGER.debug("Skipping invalid entity {} in permissions of folder {}.", I(oclPermission.getEntity()), folder.getID());
                } else if (oclPermission.isGroup()) {
                    groupIDs.add(I(oclPermission.getEntity()));
                } else {
                    userIDs.add(I(oclPermission.getEntity()));
                }
            }
        }
        boolean allEntitiesFound = cachePermissionEntities(userIDs, groupIDs);
        if (false == allEntitiesFound) {
            // Invalidate cache
            for (Folder folder : folders) {
                try {
                    FolderCacheManager.getInstance().removeFolderObject(Integer.parseInt(folder.getID()), session.getContext());
                } catch (NumberFormatException | OXException e) {
                    LOGGER.debug("Failed to drop folder cache entry", e);
                }
            }
        }
    }

    /**
     * Caches the permission entities found in the supplied list of files.
     *
     * @param files The files to cache the permission entities for
     */
    public void cacheFilePermissionEntities(List<File> files) {
        /*
         * collect user- and group identifiers
         */
        Set<Integer> userIDs = new HashSet<Integer>();
        Set<Integer> groupIDs = new HashSet<Integer>();
        for (File file : files) {
            List<FileStorageObjectPermission> objectPermissions = file.getObjectPermissions();
            if (null == objectPermissions ) {
                continue;
            }
            for (FileStorageObjectPermission objectPermission : objectPermissions) {
                if (0 > objectPermission.getEntity()) {
                    LOGGER.debug("Skipping invalid entity {} in permissions of file {}.", I(objectPermission.getEntity()), file.getId());
                } else if (objectPermission.isGroup()) {
                    groupIDs.add(I(objectPermission.getEntity()));
                } else {
                    userIDs.add(I(objectPermission.getEntity()));
                }
            }
        }
        cachePermissionEntities(userIDs, groupIDs);
    }

    /**
     * Caches the permission entities found in the supplied list of folders.
     *
     * @param userIDs The identifiers of the users to cache
     * @param groupIDs The identifiers of the groups to cache
     * @return <code>true</code> if all users/groups were found; otherwise <code>false</code>
     */
    private boolean cachePermissionEntities(Set<Integer> userIDs, Set<Integer> groupIDs) {
        /*
         * fetch users & user contacts
         */
        boolean allEntitiesFound = true;
        if (0 < userIDs.size()) {
            UserService userService = services.getService(UserService.class);
            List<Integer> guestUserIDs = new ArrayList<Integer>();
            List<Integer> regularUserIDs = new ArrayList<Integer>();
            try {
                for (User user : userService.getUser(session.getContext(), I2i(userIDs))) {
                    Integer id = I(user.getId());
                    knownUsers.put(id, user);
                    if (user.isGuest()) {
                        guestUserIDs.add(id);
                    } else {
                        regularUserIDs.add(id);
                    }
                }
            } catch (OXException e) {
                if (UserExceptionCode.USER_NOT_FOUND.equals(e)) {
                    // Retry one-by-one...
                    for (Integer userID : userIDs) {
                        try {
                            User user = userService.getUser(userID.intValue(), session.getContext());
                            knownUsers.put(userID, user);
                            if (user.isGuest()) {
                                guestUserIDs.add(userID);
                            } else {
                                regularUserIDs.add(userID);
                            }
                        } catch (OXException x) {
                            if (!UserExceptionCode.USER_NOT_FOUND.equals(e)) {
                                LOGGER.error("Error getting users for permission entities", x);
                            }
                            // Apparently no such user exists. Ignore
                            allEntitiesFound = false;
                        }
                    }
                } else {
                    LOGGER.error("Error getting users for permission entities", e);
                }
            }
            if (0 < regularUserIDs.size()) {
                SearchIterator<Contact> searchIterator = null;
                try {
                    searchIterator = services.getService(ContactService.class).getUsers(session, I2i(regularUserIDs), CONTACT_FIELDS);
                    while (searchIterator.hasNext()) {
                        Contact userContact = searchIterator.next();
                        knownUserContacts.put(I(userContact.getInternalUserId()), userContact);
                    }
                } catch (OXException e) {
                    LOGGER.error("Error getting user contacts for permission entities", e);
                } finally {
                    SearchIterators.close(searchIterator);
                }
            }
            if (0 < guestUserIDs.size()) {
                ContactUserStorage contactUserStorage = services.getService(ContactUserStorage.class);
                for (Integer guestID : guestUserIDs) {
                    try {
                        Contact guestContact = contactUserStorage.getGuestContact(session.getContextId(), i(guestID), CONTACT_FIELDS);
                        knownUserContacts.put(guestID, guestContact);
                    } catch (OXException e) {
                        LOGGER.error("Error getting user contact for guest permission entity", e);
                    }
                }
            }
        }
        /*
         * fetch groups
         */
        if (0 < groupIDs.size()) {
            GroupService groupService = services.getService(GroupService.class);
            for (Integer groupID : groupIDs) {
                try {
                    knownGroups.put(groupID, groupService.getGroup(session.getContext(), i(groupID), false));
                } catch (OXException e) {
                    if (LdapExceptionCode.GROUP_NOT_FOUND.equals(e)) {
                        // Apparently no such group exists. Ignore
                        allEntitiesFound = false;
                    } else {
                        LOGGER.error("Error getting groups for permission entities", e);
                    }
                }
            }
        }
        return allEntitiesFound;
    }

}
