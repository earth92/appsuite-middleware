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

package com.openexchange.caldav.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.openexchange.caldav.GroupwareCaldavFactory;
import com.openexchange.caldav.Tools;
import com.openexchange.caldav.mixins.CalendarOrder;
import com.openexchange.caldav.mixins.ScheduleDefaultCalendarURL;
import com.openexchange.caldav.mixins.ScheduleDefaultTasksURL;
import com.openexchange.caldav.mixins.ScheduleInboxURL;
import com.openexchange.caldav.mixins.ScheduleOutboxURL;
import com.openexchange.caldav.mixins.SupportedCalendarComponentSets;
import com.openexchange.caldav.mixins.SupportedReportSet;
import com.openexchange.chronos.provider.CalendarCapability;
import com.openexchange.dav.DAVProtocol;
import com.openexchange.dav.DAVUserAgent;
import com.openexchange.dav.Privilege;
import com.openexchange.dav.mixins.CurrentUserPrivilegeSet;
import com.openexchange.dav.resources.DAVCollection;
import com.openexchange.dav.resources.DAVRootCollection;
import com.openexchange.dav.resources.FolderCollection;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.ContentType;
import com.openexchange.folderstorage.FolderResponse;
import com.openexchange.folderstorage.FolderService;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.Type;
import com.openexchange.folderstorage.UserizedFolder;
import com.openexchange.folderstorage.calendar.contentType.CalendarContentType;
import com.openexchange.folderstorage.database.contentType.TaskContentType;
import com.openexchange.folderstorage.type.PrivateType;
import com.openexchange.folderstorage.type.PublicType;
import com.openexchange.folderstorage.type.SharedType;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.login.Interface;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.webdav.protocol.WebdavPath;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;

/**
 * {@link CalDAVRootCollection}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class CalDAVRootCollection extends DAVRootCollection {

    private final GroupwareCaldavFactory factory;

    private List<UserizedFolder> subfolders;
    private String treeID;

    /**
     * Initializes a new {@link CalDAVRootCollection}.
     *
     * @param factory The factory
     */
    public CalDAVRootCollection(GroupwareCaldavFactory factory) {
        super(factory, "Calendars");
        this.factory = factory;
        includeProperties(
            new SupportedCalendarComponentSets(SupportedCalendarComponentSets.VEVENT, SupportedCalendarComponentSets.VTODO),
            new ScheduleDefaultCalendarURL(factory),
            new ScheduleDefaultTasksURL(factory),
            new SupportedReportSet(SupportedReportSet.CALENDAR_MULTIGET, SupportedReportSet.ACL_PRINCIPAL_PROP_SET, SupportedReportSet.PRINCIPAL_MATCH, SupportedReportSet.PRINCIPAL_PROPERTY_SEARCH, SupportedReportSet.EXPAND_PROPERTY, SupportedReportSet.CALENDARSERVER_PRINCIPAL_SEARCH, SupportedReportSet.SYNC_COLLECTION),
            new CurrentUserPrivilegeSet(Privilege.READ, Privilege.READ_ACL, Privilege.READ_CURRENT_USER_PRIVILEGE_SET, Privilege.BIND, Privilege.UNBIND)
        );
    }

    @Override
    public String getPushTopic() {
        return "ox:" + Interface.CALDAV.toString().toLowerCase();
    }

    protected FolderService getFolderService() {
        return factory.getFolderService();
    }

    protected List<UserizedFolder> getSubfolders() throws OXException {
        if (null == this.subfolders) {
            this.subfolders = getVisibleFolders();
        }
        return subfolders;
    }

    @Override
    public DAVCollection getChild(String name) throws WebdavProtocolException {
        /*
         * check static paths
         */
        if (ScheduleOutboxURL.SCHEDULE_OUTBOX.equals(name)) {
            return factory.mixin(new ScheduleOutboxCollection(factory));
        }
        if (ScheduleInboxURL.SCHEDULE_INBOX.equals(name)) {
            return factory.mixin(new ScheduleInboxCollection(factory));
        }
        /*
         * check folder via decoded identifier
         */
        try {
            UserizedFolder folder = getFolder(Tools.decodeFolderId(name));
            if (matches(name, folder)) {
                return createCollection(folder);
            }
        } catch (OXException | IllegalArgumentException e) {
            LOG.debug("Unable to get folder by resource name \"{}\", matching against all visible folders as fallback.", name, e);
        }
        /*
         * match against all visible folders; then fall-back to placeholder collection
         */
        try {
            for (UserizedFolder folder : getSubfolders()) {
                if (matches(name, folder)) {
                    return createCollection(folder);
                }
                if (DAVUserAgent.THUNDERBIRD_LIGHTNING.equals(getUserAgent()) && matchesLegacy(name, folder)) {
                    LOG.debug("Resolving legacy resource name {} to folder {} for Thunderbird/Lightning.", name, folder.getID());
                    return createCollection(folder);
                }
            }
            LOG.debug("{}: child collection '{}' not found, creating placeholder collection", getUrl(), name);
            return new CalDAVPlaceholderCollection<>(factory, constructPathForChildResource(name), CalendarContentType.getInstance(), getTreeID());
        } catch (OXException e) {
            throw DAVProtocol.protocolException(getUrl(), e);
        }
    }

    private FolderCollection<?> createCollection(UserizedFolder folder) throws OXException {
        return createCollection(folder, CalendarOrder.NO_ORDER);
    }

    private FolderCollection<?> createCollection(UserizedFolder folder, int order) throws OXException {
        if (TaskContentType.getInstance().equals(folder.getContentType())) {
            return factory.mixin(new TaskCollection(factory, constructPathForChildResource(folder), folder, order));
        } else if (CalendarContentType.getInstance().equals(folder.getContentType())) {
            if (folder.getSupportedCapabilities().contains(CalendarCapability.SYNC.getName())) {
                return factory.mixin(new EventCollection(factory, constructPathForChildResource(folder), folder, order));
            }
            if (folder.getSupportedCapabilities().contains(CalendarCapability.CTAG.getName())) {
                return factory.mixin(new CTagEventCollection(factory, constructPathForChildResource(folder), folder, order));
            }
            throw new UnsupportedOperationException("Folder " + folder.getID() + " is not available via CalDAV.");
        }
        throw new UnsupportedOperationException("content type " + folder.getContentType() + " not supported");
    }

    @Override
    public List<WebdavResource> getChildren() throws WebdavProtocolException {
        List<WebdavResource> children = new ArrayList<WebdavResource>();
        int calendarOrder = 0;
        try {
            for (UserizedFolder folder : getSubfolders()) {
                children.add(createCollection(folder, ++calendarOrder));
                LOG.debug("{}: adding folder collection for folder '{}' as child resource.", getUrl(), folder.getName());
            }
        } catch (OXException e) {
            throw DAVProtocol.protocolException(getUrl(), e);
        }
        children.add(new ScheduleOutboxCollection(factory));
        children.add(new ScheduleInboxCollection(factory));
        LOG.debug("{}: got {} child resources.", getUrl(), Integer.valueOf(children.size()));
        return children;
    }

    /**
     * Constructs a string representing the WebDAV name for a folder resource.
     *
     * @param folder The folder to construct the name for
     * @return The resource name
     */
    private String constructNameForChildResource(UserizedFolder folder) {
        return Tools.encodeFolderId(folder.getID());
    }

    private WebdavPath constructPathForChildResource(UserizedFolder folder) {
        return constructPathForChildResource(constructNameForChildResource(folder));
    }

    /**
     * Gets a list of all visible and subscribed task- and calendar-folders in the configured folder tree.
     *
     * @return The visible folders
     */
    private List<UserizedFolder> getVisibleFolders() throws OXException {
        UserPermissionBits permissionBits = ServerSessionAdapter.valueOf(factory.getSession()).getUserPermissionBits();
        List<UserizedFolder> folders = new ArrayList<UserizedFolder>();
        if (permissionBits.hasCalendar()) {
            folders.addAll(getSynchronizedFolders(PrivateType.getInstance(), CalendarContentType.getInstance()));
            if (permissionBits.hasFullPublicFolderAccess()) {
                folders.addAll(getSynchronizedFolders(PublicType.getInstance(), CalendarContentType.getInstance()));
            }
            if (permissionBits.hasFullSharedFolderAccess()) {
                folders.addAll(getSynchronizedFolders(SharedType.getInstance(), CalendarContentType.getInstance()));
            }
        }
        if (permissionBits.hasTask()) {
            folders.addAll(getSynchronizedFolders(PrivateType.getInstance(), TaskContentType.getInstance()));
        }
        return folders;
    }

    private UserizedFolder getFolder(String folderId) throws OXException {
        UserizedFolder folder = getFolderService().getFolder(getTreeID(), folderId, factory.getSession(), null);
        if (false == isUsedForSync(folder)) {
            throw OXException.notFound(folderId);
        }
        return folder;
    }

    /**
     * Gets a list containing all visible and synchronizable folders of the given {@link Type}.
     *
     * @param type The type to get the folders for
     * @param contentType The content type to get the folders for
     * @return The synchronizable folders, or an empty list if there are none
     */
    private List<UserizedFolder> getSynchronizedFolders(Type type, ContentType contentType) throws OXException {
        List<UserizedFolder> folders = new ArrayList<UserizedFolder>();
        FolderResponse<UserizedFolder[]> visibleFoldersResponse = getFolderService().getVisibleFolders(getTreeID(), contentType, type, false, factory.getSession(), null);
        UserizedFolder[] response = visibleFoldersResponse.getResponse();
        for (UserizedFolder folder : response) {
            if (isUsedForSync(folder)) {
                folders.add(folder);
            }
        }
        return folders;
    }

    /**
     * Gets a value indicating whether the folder should be considered for synchronization with external clients or not.
     *
     * @param folder The folder to check
     * @return <code>true</code> if the folder should be considered for synchronization, <code>false</code>, otherwise
     */
    private boolean isUsedForSync(UserizedFolder folder) {
        if (Permission.READ_OWN_OBJECTS > folder.getOwnPermission().getReadPermission()) {
            return false;
        }
        if (CalendarContentType.getInstance().equals(folder.getContentType())) {
            Set<String> caps = folder.getSupportedCapabilities();
            if (!caps.contains(CalendarCapability.SYNC.getName()) && !caps.contains(CalendarCapability.CTAG.getName())) {
                return false;
            }
        }

        return folder.isSubscribed() && folder.getUsedForSync().isUsedForSync();
    }

    /**
     * Gets the used folder tree identifier.
     *
     * @return the folder tree ID
     */
    private String getTreeID() {
        if (null == this.treeID) {
            try {
                treeID = factory.getConfigValue("com.openexchange.caldav.tree", FolderStorage.REAL_TREE_ID);
            } catch (OXException e) {
                LOG.warn("falling back to tree id ''{}''.", FolderStorage.REAL_TREE_ID, e);
                treeID = FolderStorage.REAL_TREE_ID;
            }
        }
        return treeID;
    }

    private static boolean matchesLegacy(String resourceName, UserizedFolder folder) {
        if (null == resourceName || null == folder || null == folder.getID()) {
            return false;
        }
        /*
         * try constructed composite identifier
         */
        if ((Tools.DEFAULT_ACCOUNT_PREFIX + resourceName).equals(folder.getID())) {
            return true;
        }
        /*
         * try relative folder identifier as-is as additional fallback
         */
        if (resourceName.equals(folder.getID())) {
            return true;
        }
        return false;
    }

    private static boolean matches(String resourceName, UserizedFolder folder) {
        if (null == resourceName || null == folder || null == folder.getID()) {
            return false;
        }
        /*
         * match decoded name first
         */
        try {
            if (Tools.decodeFolderId(resourceName).equals(folder.getID())) {
                return true;
            }
        } catch (IllegalArgumentException e) {
            LOG.debug("Error decoding child resource name {}, assuming legacy or stored resource name.", resourceName, e);
        }
        /*
         * try via stored resource name, too
         */
        if (null != folder.getMeta() && folder.getMeta().containsKey("resourceName") && resourceName.equals(folder.getMeta().get("resourceName"))) {
            return true;
        }
        return false;
    }

}
