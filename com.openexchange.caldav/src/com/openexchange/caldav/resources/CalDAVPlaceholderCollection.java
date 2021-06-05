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

import static com.openexchange.dav.DAVProtocol.protocolException;
import static com.openexchange.folderstorage.CalendarFolderConverter.CALENDAR_PROVIDER_FIELD;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import org.jdom2.Element;
import com.openexchange.caldav.CaldavProtocol;
import com.openexchange.caldav.GroupwareCaldavFactory;
import com.openexchange.caldav.mixins.CalendarOrder;
import com.openexchange.chronos.provider.CalendarProviders;
import com.openexchange.dav.DAVProperty;
import com.openexchange.dav.DAVProtocol;
import com.openexchange.dav.PreconditionException;
import com.openexchange.dav.mixins.SupportedCalendarComponentSet;
import com.openexchange.dav.reports.SyncStatus;
import com.openexchange.dav.resources.PlaceholderCollection;
import com.openexchange.dav.resources.SyncToken;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXException.IncorrectString;
import com.openexchange.exception.OXException.ProblematicAttribute;
import com.openexchange.folderstorage.BasicPermission;
import com.openexchange.folderstorage.CalendarFolderConverter;
import com.openexchange.folderstorage.ContentType;
import com.openexchange.folderstorage.FolderService;
import com.openexchange.folderstorage.ParameterizedFolder;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.UserizedFolder;
import com.openexchange.folderstorage.calendar.contentType.CalendarContentType;
import com.openexchange.folderstorage.database.contentType.TaskContentType;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.webdav.protocol.Protocol;
import com.openexchange.webdav.protocol.WebdavPath;
import com.openexchange.webdav.protocol.WebdavProperty;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;
import com.openexchange.webdav.protocol.helpers.AbstractResource;

/**
 * {@link CalDAVPlaceholderCollection}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.2
 */
public class CalDAVPlaceholderCollection<T> extends CalDAVFolderCollection<T> {

    private final ParameterizedFolder folderToCreate;

    /**
     * Initializes a new {@link PlaceholderCollection}.
     *
     * @param factory The underlying factory
     * @param url The target WebDAV path
     * @param contentType The default content type to use
     * @param treeID The tree identifier to use
     */
    public CalDAVPlaceholderCollection(GroupwareCaldavFactory factory, WebdavPath url, @SuppressWarnings("unused") ContentType contentType, String treeID) throws OXException {
        super(factory, url, null, CalendarOrder.NO_ORDER);
        /*
         * prepare placeholder folder and apply defaults
         */
        folderToCreate = getFolderToUpdate();
        folderToCreate.setContentType(CalendarContentType.getInstance());
        folderToCreate.setName(url.name());
        folderToCreate.getMeta().put("resourceName", getUrl().name());
        folderToCreate.setTreeID(treeID);
        folderToCreate.setSubscribed(true);
    }

    @Override
    public void setDisplayName(String displayName) throws WebdavProtocolException {
        folderToCreate.setName(displayName);
    }

    @Override
    protected boolean handleSpecialPut(WebdavProperty prop) throws WebdavProtocolException {
        if (Protocol.RESOURCETYPE_LITERAL.getNamespace().equals(prop.getNamespace()) && Protocol.RESOURCETYPE_LITERAL.getName().equals(prop.getName())) {
            /*
             * apply ical calendar provider in case the new collection should be a "subscribed" one
             */
            for (Element element : prop.getChildren()) {
                if (CaldavProtocol.CALENDARSERVER_NS.equals(element.getNamespace()) && "subscribed".equals(element.getName())) {
                    folderToCreate.setProperty(CALENDAR_PROVIDER_FIELD, CalendarProviders.ID_ICAL);
                }
            }
            return true;
        }
        return super.handleSpecialPut(prop);
    }

    @Override
    protected void internalPutProperty(WebdavProperty property) throws WebdavProtocolException {
        Element element = DAVProperty.class.isInstance(property) ? ((DAVProperty) property).getElement() : null;
        if (DAVProtocol.CAL_NS.getURI().equals(property.getNamespace()) && "supported-calendar-component-set".equals(property.getName())) {
            String value = null;
            if (null != element) {
                for (Element compElement : element.getChildren("comp", DAVProtocol.CAL_NS)) {
                    String name = null != compElement.getAttribute("name") ? compElement.getAttribute("name").getValue() : null;
                    if (SupportedCalendarComponentSet.VTODO.equalsIgnoreCase(name) || SupportedCalendarComponentSet.VEVENT.equalsIgnoreCase(name)) {
                        value = name;
                    }
                }
            } else {
                value = property.getValue();
                if (property.isXML()) {
                    // try to extract comp attribute from xml fragment
                    Pattern compNameRegex = Pattern.compile("name=\\\"(.+?)\\\"", Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
                    Matcher regexMatcher = compNameRegex.matcher(value);
                    if (regexMatcher.find()) {
                        value = regexMatcher.group(1);
                    }
                }
            }
            if (SupportedCalendarComponentSet.VTODO.equalsIgnoreCase(value)) {
                folderToCreate.setContentType(TaskContentType.getInstance());
            } else if (SupportedCalendarComponentSet.VEVENT.equalsIgnoreCase(value)) {
                folderToCreate.setContentType(CalendarContentType.getInstance());
            } else {
                throw protocolException(getUrl(), HttpServletResponse.SC_BAD_REQUEST);
            }
        } else if (DAVProtocol.DAV_NS.getURI().equals(property.getNamespace()) && "resourcetype".equals(property.getName()) && null != element) {
            if (null != element.getChild("calendar", DAVProtocol.CAL_NS)) {
                folderToCreate.setContentType(CalendarContentType.getInstance());
                if (null != element.getChild("subscribed", DAVProtocol.CALENDARSERVER_NS)) {
                    folderToCreate.setProperty(CalendarFolderConverter.CALENDAR_PROVIDER_FIELD, CalendarProviders.ID_ICAL);
                }
            } else {
                throw new PreconditionException(DAVProtocol.DAV_NS.getURI(), "valid-resourcetype", getUrl(), HttpServletResponse.SC_CONFLICT);
            }
        } else {
            super.internalPutProperty(property);
        }
    }

    @Override
    public void create() throws WebdavProtocolException {
        try {
            FolderService folderService = factory.requireService(FolderService.class);
            UserizedFolder parentFolder = folderService.getDefaultFolder(factory.getUser(), folderToCreate.getTreeID(), folderToCreate.getContentType(), factory.getSession(), null);
            folderToCreate.setParentID(parentFolder.getID());
            folderToCreate.setType(parentFolder.getType());
            folderToCreate.setPermissions(new Permission[] { getDefaultAdminPermissions(factory.getUser().getId()) });
            folderService.createFolder(folderToCreate, factory.getSession(), null);
        } catch (OXException e) {
            if ("FLD-0092".equals(e.getErrorCode())) {
                /*
                 * 'Unsupported character "..." in field "Folder name".
                 */
                ProblematicAttribute[] problematics = e.getProblematics();
                if (null != problematics && 0 < problematics.length && null != problematics[0] && IncorrectString.class.isInstance(problematics[0])) {
                    IncorrectString incorrectString = ((IncorrectString) problematics[0]);
                    if (FolderObject.FOLDER_NAME == incorrectString.getId()) {
                        String name = folderToCreate.getName();
                        String correctedDisplayName = name.replace(incorrectString.getIncorrectString(), "");
                        if (false == correctedDisplayName.equals(name)) {
                            folderToCreate.setName(correctedDisplayName);
                            create();
                            return;
                        }
                    }
                }
            }
            throw protocolException(getUrl(), e);
        }
    }

    @Override
    public void save() throws WebdavProtocolException {
        throw protocolException(getUrl(), HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    public void delete() throws WebdavProtocolException {
        throw protocolException(getUrl(), HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    public boolean exists() throws WebdavProtocolException {
        return false;
    }

    @Override
    protected Collection<T> getObjects(Date rangeStart, Date rangeEnd) throws OXException {
        return Collections.emptyList();
    }

    @Override
    protected Collection<T> getObjects() throws OXException {
        return Collections.emptyList();
    }

    @Override
    protected T getObject(String resourceName) throws OXException {
        return null;
    }

    @Override
    protected AbstractResource createResource(T object, WebdavPath url) throws OXException {
        throw protocolException(getUrl(), HttpServletResponse.SC_CONFLICT); // https://tools.ietf.org/html/rfc2518#section-8.7.1
    }

    @Override
    protected WebdavPath constructPathForChildResource(T object) {
        return null;
    }

    @Override
    protected SyncStatus<WebdavResource> getSyncStatus(SyncToken syncToken) throws OXException {
        return null;
    }

    @Override
    public String getSyncToken() throws WebdavProtocolException {
        Date lastModified = getLastModified();
        return null == lastModified ? "0" : String.valueOf(lastModified.getTime());
    }

    private Permission getDefaultAdminPermissions(int entity) {
        BasicPermission permission = new BasicPermission();
        permission.setMaxPermissions();
        permission.setEntity(entity);
        return permission;
    }
}