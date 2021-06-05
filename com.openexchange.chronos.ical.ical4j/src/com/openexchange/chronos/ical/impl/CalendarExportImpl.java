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

package com.openexchange.chronos.ical.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.Availability;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.ExtendedProperty;
import com.openexchange.chronos.FreeBusyData;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.ical.CalendarExport;
import com.openexchange.chronos.ical.ICalExceptionCodes;
import com.openexchange.chronos.ical.ICalParameters;
import com.openexchange.chronos.ical.ical4j.VCalendar;
import com.openexchange.chronos.ical.ical4j.mapping.ICalMapper;
import com.openexchange.chronos.ical.ical4j.osgi.Services;
import com.openexchange.exception.OXException;
import com.openexchange.java.Autoboxing;
import com.openexchange.version.VersionService;
import net.fortuna.ical4j.extensions.property.WrCalName;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyFactoryImpl;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VAvailability;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VFreeBusy;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Version;

/**
 * {@link CalendarExportImpl}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class CalendarExportImpl implements CalendarExport {

    private final List<OXException> warnings;
    private final ICalMapper mapper;
    private final ICalParameters parameters;
    private final VCalendar vCalendar;
    private final Set<String> timezoneIDs;
    
    /**
     * Initializes a new {@link CalendarExportImpl}.
     * 
     * @param mapper The iCal mapper to use
     * @param parameters The iCal parameters
     * @param warnings The warnings
     */
    public CalendarExportImpl(ICalMapper mapper, ICalParameters parameters, List<OXException> warnings) {
        super();
        this.mapper = mapper;
        this.parameters = parameters;
        this.warnings = warnings;
        this.timezoneIDs = new HashSet<String>();
        this.vCalendar = initCalendar();
    }

    @Override
    public CalendarExport add(ExtendedProperty property) {
        vCalendar.getProperties().add(ICalUtils.exportProperty(property));
        return this;
    }

    public void setProductId(String prodId) {
        ProdId property = (ProdId) vCalendar.getProperty(Property.PRODID);
        if (null == property) {
            property = new ProdId();
            vCalendar.getProperties().add(property);
        }
        property.setValue(prodId);
    }

    public void setVersion(String version) {
        Version property = (Version) vCalendar.getProperty(Property.VERSION);
        if (null == property) {
            property = new Version();
            vCalendar.getProperties().add(property);
        }
        property.setValue(version);
    }

    @Override
    public void setName(String name) {
        WrCalName property = (WrCalName) vCalendar.getProperty(WrCalName.PROPERTY_NAME);
        if (null == property) {
            property = new WrCalName(PropertyFactoryImpl.getInstance());
            vCalendar.getProperties().add(property);
        }
        property.setValue(name);
    }

    @Override
    public void setMethod(String method) {
        Method property = (Method) vCalendar.getProperty(Property.METHOD);
        if (null == property) {
            property = new Method();
            vCalendar.getProperties().add(property);
        }
        property.setValue(method);
    }

    @Override
    public List<OXException> getWarnings() {
        return warnings;
    }

    @Override
    public CalendarExport add(Event event) throws OXException {
        vCalendar.add(exportEvent(event));
        return this;
    }

    @Override
    public CalendarExport add(FreeBusyData freeBusyData) throws OXException {
        vCalendar.add(exportFreeBusy(freeBusyData));
        return this;
    }

    @Override
    public CalendarExport add(Availability calendarAvailability) throws OXException {
        vCalendar.add(exportAvailability(calendarAvailability));
        return this;
    }

    @Override
    public CalendarExport add(String timeZoneID) {
        trackTimezones(timeZoneID);
        return this;
    }

    @Override
    public ThresholdFileHolder getVCalendar() throws OXException {
        ThresholdFileHolder fileHolder = new ThresholdFileHolder();
        writeVCalendar(fileHolder.asOutputStream());
        return fileHolder;
    }

    @Override
    public void writeVCalendar(OutputStream outputStream) throws OXException {
        /*
         * add components for all contained timezones
         */
        for (String timezoneID : timezoneIDs) {
            TimeZoneRegistry timeZoneRegistry = parameters.get(ICalParametersImpl.TIMEZONE_REGISTRY, TimeZoneRegistry.class);
            net.fortuna.ical4j.model.TimeZone timeZone = timeZoneRegistry.getTimeZone(timezoneID);
            if (null != timeZone) {
                vCalendar.add(0, timeZone.getVTimeZone());
            } else {
                warnings.add(ICalExceptionCodes.CONVERSION_FAILED.create(Component.VTIMEZONE, "No timezone '" + timezoneID + "' registered."));
            }
        }
        /*
         * export calendar
         */
        ICalUtils.exportCalendar(vCalendar, outputStream);
    }
    
    @Override
    public InputStream getClosingStream() throws OXException {
        return getVCalendar().getClosingStream();
    }

    @Override
    public byte[] toByteArray() throws OXException {
        try (ThresholdFileHolder fileHolder = getVCalendar()) {
            return fileHolder.toByteArray();
        }
    }

    private VEvent exportEvent(Event event) {
        /*
         * export event data, track timezones
         */
        VEvent vEvent = mapper.exportEvent(event, parameters, warnings);
        ICalUtils.removeProperties(vEvent, parameters.get(ICalParameters.IGNORED_PROPERTIES, String[].class));
        ICalUtils.removeParameters(vEvent, parameters.get(ICalParameters.IGNORED_PROPERTY_PARAMETERS, String[].class));
        if (false == CalendarUtils.isFloating(event)) {
            trackTimezones(event.getStartDate(), event.getEndDate());
        }
        /*
         * export alarms as sub-components
         */
        if (!Autoboxing.b(parameters.get(ICalParameters.IGNORE_ALARM, Boolean.class, Boolean.FALSE))) {
            List<Alarm> alarms = event.getAlarms();
            if (null != alarms && 0 < alarms.size()) {
                for (Alarm alarm : alarms) {
                    vEvent.getAlarms().add(exportAlarm(alarm));
                }
            }
        }
        return vEvent;
    }

    private VAlarm exportAlarm(Alarm alarm) {
        /*
         * export alarm data
         */
        VAlarm vAlarm = mapper.exportAlarm(alarm, parameters, warnings);
        ICalUtils.removeProperties(vAlarm, parameters.get(ICalParameters.IGNORED_PROPERTIES, String[].class));
        ICalUtils.removeParameters(vAlarm, parameters.get(ICalParameters.IGNORED_PROPERTY_PARAMETERS, String[].class));
        return vAlarm;
    }

    private VFreeBusy exportFreeBusy(FreeBusyData freeBusyData) {
        /*
         * export free/busy data, track timezones
         */
        VFreeBusy vFreeBusy = mapper.exportFreeBusy(freeBusyData, parameters, warnings);
        ICalUtils.removeProperties(vFreeBusy, parameters.get(ICalParameters.IGNORED_PROPERTIES, String[].class));
        ICalUtils.removeParameters(vFreeBusy, parameters.get(ICalParameters.IGNORED_PROPERTY_PARAMETERS, String[].class));
        trackTimezones(freeBusyData.getStartDate(), freeBusyData.getEndDate());
        return vFreeBusy;
    }

    /**
     * Exports the specified {@link Availability} to a {@link VAvailability} component
     *
     * @param availability The {@link Availability} to export
     * @return The exported {@link VAvailability} component
     */
    private VAvailability exportAvailability(Availability availability) {
        VAvailability vAvailability = mapper.exportAvailability(availability, parameters, warnings);
        ICalUtils.removeProperties(vAvailability, parameters.get(ICalParameters.IGNORED_PROPERTIES, String[].class));
        ICalUtils.removeParameters(vAvailability, parameters.get(ICalParameters.IGNORED_PROPERTY_PARAMETERS, String[].class));
        // TODO: Track timezones of availability/available components
        return vAvailability;
    }

    private boolean trackTimezones(String... timeZoneIDs) {
        boolean added = false;
        if (null != timeZoneIDs && 0 < timeZoneIDs.length) {
            for (String timeZoneID : timeZoneIDs) {
                if (null != timeZoneID && false == "UTC".equals(timeZoneID)) {
                    added |= timezoneIDs.add(timeZoneID);
                }
            }
        }
        return added;
    }

    private boolean trackTimezones(org.dmfs.rfc5545.DateTime... dateTimes) {
        boolean added = false;
        if (null != dateTimes && 0 < dateTimes.length) {
            for (int i = 0; i < dateTimes.length; i++) {
                org.dmfs.rfc5545.DateTime dateTime = dateTimes[i];
                if (null != dateTime && false == dateTime.isFloating() && null != dateTime.getTimeZone()) {
                    added |= trackTimezones(dateTime.getTimeZone().getID());
                }
            }
        }
        return added;
    }
    
    public boolean trackTimeZones(Event event) {
        boolean added = false;
        if (false == CalendarUtils.isFloating(event)) {
            added |= trackTimezones(event.getStartDate(), event.getEndDate());
        }
        return added;
    }

    private static VCalendar initCalendar() {
        VCalendar vCalendar = new VCalendar();
        vCalendar.getProperties().add(Version.VERSION_2_0);
        VersionService versionService = Services.optService(VersionService.class);
        String versionString = null != versionService ? versionService.getVersionString() : "<unknown version>";
        vCalendar.getProperties().add(new ProdId("-//" + VersionService.NAME + "//" + versionString + "//EN"));
        return vCalendar;
    }

}
