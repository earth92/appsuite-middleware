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

package com.openexchange.caldav;

import static com.openexchange.chronos.common.CalendarUtils.getEventsByUID;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesException;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesMaster;
import static com.openexchange.chronos.common.CalendarUtils.sortSeriesMasterFirst;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import com.google.common.io.BaseEncoding;
import com.openexchange.caldav.resources.EventResource;
import com.openexchange.chronos.Attachment;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.ExtendedProperties;
import com.openexchange.chronos.ExtendedProperty;
import com.openexchange.dav.AttachmentUtils;
import com.openexchange.dav.DAVOAuthScope;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.UserizedFolder;
import com.openexchange.groupware.attach.AttachmentMetadata;
import com.openexchange.groupware.container.CommonObject;
import com.openexchange.groupware.tasks.TaskExceptionCode;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.webdav.protocol.WebdavPath;


/**
 * {@link Tools}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class Tools {

    public static final String DEFAULT_ACCOUNT_PREFIX = "cal://0/";

    public static final String EXTENSION_ICS = ".ics";

    /**
     * The OAuth scope token for CalDAV
     */
    public static final String OAUTH_SCOPE = DAVOAuthScope.CALDAV.getScope();

    /**
     * Gets the <i>significant</i> events for a collection of arbitrary events from multiple calendar object resources.
     *
     * @param events The events get the significant events from
     * @return The significant events, or an empty list if passed event collection was null or empty
     */
    public static List<Event> getSignificantEvents(List<Event> events) {
        if (null == events || events.isEmpty()) {
            return Collections.emptyList();
        }
        List<Event> significantEvents = new ArrayList<Event>(events.size());
        for (List<Event> eventGroup : getEventsByUID(events, false).values()) {
            Event event = getSignificantEvent(eventGroup);
            if (null != event) {
                significantEvents.add(event);
            }
        }
        return significantEvents;
    }

    /**
     * Gets the <i>significant</i> event of multiple events within the same calendar object resource (with the same unique identifier).
     * This is the event itself for non-recurring events, or the series master event for a recurring event series with change exceptions.
     * In case only change exceptions are contained in the collection, a <i>phantom master</i> event is used implicitly.
     *
     * @param events The events of a calendar object resource to get the significant event from
     * @return The significant event, or <code>null</code> if passed event collection was null or empty
     */
    public static Event getSignificantEvent(List<Event> events) {
        if (null == events || events.isEmpty()) {
            return null;
        }
        if (1 == events.size()) {
            return isSeriesException(events.get(0)) ? new PhantomMaster(events) : events.get(0);
        }
        List<Event> eventGroup = sortSeriesMasterFirst(events);
        return isSeriesMaster(eventGroup.get(0)) ? eventGroup.get(0) : new PhantomMaster(eventGroup);
    }

    public static String encodeFolderId(String folderId) {
        return null == folderId ? null : BaseEncoding.base64Url().omitPadding().encode(folderId.getBytes(Charsets.US_ASCII));
    }

    public static String decodeFolderId(String encodedFolderId) throws IllegalArgumentException {
        return new String(BaseEncoding.base64Url().omitPadding().decode(encodedFolderId), Charsets.US_ASCII);
    }

    /**
     * Gets a value indicating whether the supplied event represents a <i>phantom master</i>, i.e. a recurring event master the
     * user has no access for that serves as container for detached occurrences.
     *
     * @param event The event to check
     * @return <code>true</code> if the event is a phantom master, <code>false</code>, otherwise
     */
    public static boolean isPhantomMaster(Event event) {
        return PhantomMaster.class.isInstance(event);
    }

    /**
     * Optionally gets an extended property date value from a previously imported iCal component.
     *
     * @param extendedProperties The extended properties to get the extended date property value from
     * @param propertyName The extended property's name
     * @return The extended property's value parsed as UTC date, or <code>null</code> if not set
     */
    public static Date optExtendedPropertyAsDate(ExtendedProperties extendedProperties, String propertyName) {
        ExtendedProperty extendedProperty = optExtendedProperty(extendedProperties, propertyName);
        if (null != extendedProperty && String.class.isInstance(extendedProperty.getValue()) && Strings.isNotEmpty((String) extendedProperty.getValue())) {
            try {
                return parseUTC((String) extendedProperty.getValue());
            } catch (ParseException e) {
                LoggerFactory.getLogger(Tools.class).warn("Error parsing UTC date from iCal property", e);
            }
        }
        return null;
    }

    /**
     * Optionally gets an extended iCal property from a previously imported event component.
     *
     * @param extendedProperties The extended properties to get the extended property from
     * @param propertyName The extended property's name
     * @return The extended property, or <code>null</code> if not set
     */
    public static ExtendedProperty optExtendedProperty(ExtendedProperties extendedProperties, String propertyName) {
        if (null != extendedProperties && 0 < extendedProperties.size()) {
            if (-1 != propertyName.indexOf('*')) {
                Pattern pattern = Pattern.compile(Strings.wildcardToRegex(propertyName));
                for (ExtendedProperty extraProperty : extendedProperties) {
                    if (pattern.matcher(extraProperty.getName()).matches()) {
                        return extraProperty;
                    }
                }
            } else {
                for (ExtendedProperty extraProperty : extendedProperties) {
                    if (propertyName.equals(extraProperty.getName())) {
                        return extraProperty;
                    }
                }
            }
        }
        return null;
    }

    public static AttachmentMetadata getAttachmentMetadata(Attachment attachment, EventResource eventResource, Event event) throws OXException {
        AttachmentMetadata metadata = AttachmentUtils.newAttachmentMetadata();
        metadata.setId(attachment.getManagedId());
        if (null != eventResource) {
            metadata.setModuleId(AttachmentUtils.getModuleId(eventResource.getParent().getFolder().getContentType()));
            metadata.setFolderId(parse(eventResource.getParent().getFolder().getID()));
        }
        if (null != event) {
            metadata.setAttachedId(parse(event.getId()));
        }
        if (null != attachment.getFormatType()) {
            metadata.setFileMIMEType(attachment.getFormatType());
        } else if (null != attachment.getData()) {
            metadata.setFileMIMEType(attachment.getData().getContentType());
        }
        if (null != attachment.getFilename()) {
            metadata.setFilename(attachment.getFilename());
        } else if (null != attachment.getData()) {
            metadata.setFilename(attachment.getData().getName());
        }
        if (0 < attachment.getSize()) {
            metadata.setFilesize(attachment.getSize());
        } else if (null != attachment.getData()) {
            metadata.setFilesize(attachment.getData().getLength());
        }
        return metadata;
    }

    public static Date getLatestModified(Date lastModified1, Date lastModified2) {
        if (null == lastModified1) {
            return lastModified2;
        }
        if (null == lastModified2) {
            return lastModified1;
        }
        return lastModified1.after(lastModified2) ? lastModified1 : lastModified2;
    }

    public static Date getLatestModified(Date lastModified, CommonObject object) {
        return getLatestModified(lastModified, object.getLastModified());
    }

    public static Date getLatestModified(Date lastModified, UserizedFolder folder) {
        return getLatestModified(lastModified, folder.getLastModifiedUTC());
    }

    public static Date getLatestModified(Date lastModified, Event event) {
        return getLatestModified(lastModified, new Date(event.getTimestamp()));
    }

    /**
     * Parses a numerical identifier from a string, wrapping a possible
     * NumberFormatException into an OXException.
     *
     * @param id the id string
     * @return the parsed identifier
     * @throws OXException
     */
    public static int parse(String id) throws OXException {
        if (null == id) {
            throw new OXException(new IllegalArgumentException("id must not be null"));
        }
        if (id.startsWith(DEFAULT_ACCOUNT_PREFIX)) {
            return parse(id.substring(DEFAULT_ACCOUNT_PREFIX.length()));
        }
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new OXException(e);
        }
    }

    /**
     * Gets the resource name from an url, i.e. the path's name without the
     * filename extension.
     *
     * @param url the webdav path
     * @param fileExtension the extension
     * @return the resource name
     */
    public static String extractResourceName(WebdavPath url, String fileExtension) {
        return null != url ? extractResourceName(url.name(), fileExtension) : null;
    }

    /**
     * Gets the resource name from a filename, i.e. the resource name without the
     * filename extension.
     *
     * @param filename the filename
     * @param fileExtension the extension
     * @return the resource name
     */
    public static String extractResourceName(String filename, String fileExtension) {
        String name = filename;
        if (null != fileExtension) {
            String extension = fileExtension.toLowerCase();
            if (false == extension.startsWith(".")) {
                extension = "." + extension;
            }
            if (null != name && extension.length() < name.length() && name.toLowerCase().endsWith(extension)) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name;
    }

    public static boolean isDataTruncation(final OXException e) {
        return TaskExceptionCode.TRUNCATED.equals(e);
    }

    public static boolean isIncorrectString(OXException e) {
        return TaskExceptionCode.INCORRECT_STRING.equals(e);
    }

    public static String formatAsUTC(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    public static Date parseUTC(String value) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.parse(value);
    }

    private Tools() {
    	// prevent instantiation
    }

}
