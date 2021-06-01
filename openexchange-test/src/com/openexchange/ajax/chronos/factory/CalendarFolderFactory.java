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

package com.openexchange.ajax.chronos.factory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.json.JSONObject;
import com.openexchange.testing.httpclient.models.FolderCalendarConfig;
import com.openexchange.testing.httpclient.models.FolderCalendarExtendedProperties;
import com.openexchange.testing.httpclient.models.FolderCalendarExtendedPropertiesColor;
import com.openexchange.testing.httpclient.models.FolderCalendarExtendedPropertiesDescription;
import com.openexchange.testing.httpclient.models.FolderCalendarExtendedPropertiesScheduleTransp;
import com.openexchange.testing.httpclient.models.NewFolderBody;
import com.openexchange.testing.httpclient.models.NewFolderBodyFolder;

/**
 * {@link CalendarFolderFactory}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public final class CalendarFolderFactory {

    /**
     * The extended properties mappers
     */
    private static final Map<CalendarFolderExtendedProperty, BiConsumer<FolderCalendarExtendedProperties, String>> extPropertyMappers = new HashMap<>();
    static {
        extPropertyMappers.put(CalendarFolderExtendedProperty.COLOR, (extendedProperties, value) -> {
            FolderCalendarExtendedPropertiesColor color = new FolderCalendarExtendedPropertiesColor();
            color.setValue(value);
            extendedProperties.setColor(color);
        });
        extPropertyMappers.put(CalendarFolderExtendedProperty.DESCRIPTION, (extendedProperties, value) -> {
            FolderCalendarExtendedPropertiesDescription description = new FolderCalendarExtendedPropertiesDescription();
            description.setValue(value);
            extendedProperties.setDescription(description);
        });
        extPropertyMappers.put(CalendarFolderExtendedProperty.SCHEDULE_TRANSP, (extendedProperties, value) -> {
            FolderCalendarExtendedPropertiesScheduleTransp scheduleTransp = new FolderCalendarExtendedPropertiesScheduleTransp();
            scheduleTransp.setValue(value);
            extendedProperties.setScheduleTransp(scheduleTransp);
        });
    }

    /**
     * The calendar configuration mappers
     */
    private static final Map<CalendarFolderConfig, BiConsumer<FolderCalendarConfig, Object>> configMappers = new HashMap<>();
    static {
        configMappers.put(CalendarFolderConfig.COLOR, (config, value) -> {
            config.setColor((String) value);
        });
        configMappers.put(CalendarFolderConfig.ENABLED, (config, value) -> {
            config.setEnabled(Boolean.valueOf((String) value));
        });
        configMappers.put(CalendarFolderConfig.ITEM_ID, (config, value) -> {
            config.setItemId(value.toString());
        });
        configMappers.put(CalendarFolderConfig.REFRESH_INTERVAL, (config, value) -> {
            config.setRefreshInterval((Integer) value);
        });
        configMappers.put(CalendarFolderConfig.LOCALE, (config, value) -> {
            config.setLocale((String) value);
        });
    }

    /**
     * Creates the payload for a new folder
     * 
     * @param module The module
     * @param providerId The provider identifier
     * @param title the folder's title
     * @param config The folder's configuration
     * @param extProperties The extended properties
     * @return The payload
     */
    public static final NewFolderBody createFolderBody(String module, String providerId, String title, Boolean subscribed, JSONObject config, JSONObject extProperties) {
        NewFolderBody folderBody = new NewFolderBody();
        folderBody.setFolder(createFolder(module, providerId, title, subscribed, config, extProperties));
        return folderBody;
    }

    /**
     * Creates the payload
     * 
     * @param module the module
     * @param providerId The provider identifier
     * @param title The folder's title
     * @param config The configuration
     * @param extProperties The extended properties
     * @return The payload
     */
    private static final NewFolderBodyFolder createFolder(String module, String providerId, String title, Boolean subscribed, JSONObject config, JSONObject extProperties) {
        NewFolderBodyFolder folder = new NewFolderBodyFolder();
        folder.setComOpenexchangeCalendarProvider(providerId);
        folder.setModule(module);
        folder.setSubscribed(subscribed);
        folder.setTitle(title);
        folder.setComOpenexchangeCalendarConfig(createCalendarConfig(config));
        folder.setComOpenexchangeCalendarExtendedProperties(createCalendarExtendedProperties(extProperties));
        return folder;
    }

    /**
     * Creates a calendar configuration object from the specified {@link JSONObject} configuration
     * 
     * @param config The configuration
     * @return The {@link FolderCalendarConfig}
     */
    private static final FolderCalendarConfig createCalendarConfig(JSONObject config) {
        FolderCalendarConfig calendarConfig = new FolderCalendarConfig();
        for (CalendarFolderConfig folderConfig : CalendarFolderConfig.values()) {
            Object value = config.opt(folderConfig.getFieldName());
            if (value == null) {
                continue;
            }
            configMappers.get(folderConfig).accept(calendarConfig, value);
        }
        return calendarConfig;
    }

    /**
     * Creates a calendar extended properties object from the extended properties in the {@link JSONObject}
     * 
     * @param extProps The extended properties
     * @return The {@link FolderCalendarExtendedProperties} object
     */
    private static final FolderCalendarExtendedProperties createCalendarExtendedProperties(JSONObject extProps) {
        FolderCalendarExtendedProperties extendedProperties = new FolderCalendarExtendedProperties();
        for (CalendarFolderExtendedProperty property : CalendarFolderExtendedProperty.values()) {
            JSONObject c = extProps.optJSONObject(property.getFieldName());
            if (null == c || c.isEmpty()) {
                continue;
            }
            String value = c.optString("value");
            extPropertyMappers.get(property).accept(extendedProperties, value);
        }
        return extendedProperties;
    }
}
