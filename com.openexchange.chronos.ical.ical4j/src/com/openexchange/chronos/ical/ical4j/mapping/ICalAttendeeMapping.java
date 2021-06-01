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

package com.openexchange.chronos.ical.ical4j.mapping;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.mail.internet.AddressException;
import org.dmfs.rfc5545.DateTime;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.ExtendedPropertyParameter;
import com.openexchange.chronos.ParticipantRole;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.ical.ICalParameters;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.mail.mime.QuotedInternetAddress;
import net.fortuna.ical4j.extensions.caldav.parameter.CalendarServerDtStamp;
import net.fortuna.ical4j.model.AddressList;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.parameter.Member;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.parameter.Rsvp;
import net.fortuna.ical4j.model.parameter.SentBy;
import net.fortuna.ical4j.model.parameter.XParameter;
import net.fortuna.ical4j.util.Uris;

/**
 * {@link ICalAttendeeMapping}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public abstract class ICalAttendeeMapping<T extends CalendarComponent, U> extends AbstractICalMapping<T, U> {
    
    /**
     * Initializes a new {@link ICalAttendeeMapping}.
     */
    protected ICalAttendeeMapping() {
        super();
    }

    protected abstract List<Attendee> getValue(U object);

    protected abstract void setValue(U object, List<Attendee> value);

    @Override
    public void export(U object, T component, ICalParameters parameters, List<OXException> warnings) {
        List<Attendee> attendees = getValue(object);
        if (null == attendees || 0 == attendees.size()) {
            removeProperties(component, Property.ATTENDEE);
        } else {
            PropertyList properties = component.getProperties(Property.ATTENDEE);
            removeProperties(component, Property.ATTENDEE);
            for (Attendee attendee : attendees) {
                net.fortuna.ical4j.model.property.Attendee property = getMatchingAttendee(properties, attendee.getUri());
                if (null == property) {
                    property = new net.fortuna.ical4j.model.property.Attendee();
                }
                try {
                    component.getProperties().add(exportAttendee(attendee, property));
                } catch (URISyntaxException e) {
                    addConversionWarning(warnings, e, Property.ATTENDEE, e.getMessage());
                }
            }
        }
    }

    @Override
    public void importICal(T component, U object, ICalParameters parameters, List<OXException> warnings) {
        PropertyList properties = component.getProperties(Property.ATTENDEE);
        if (null == properties || 0 == properties.size()) {
            if (false == isIgnoreUnsetProperties(parameters)) {
                setValue(object, null);
            }
        } else {
            List<Attendee> attendees = new ArrayList<Attendee>(properties.size());
            for (Iterator<?> iterator = properties.iterator(); iterator.hasNext();) {
                net.fortuna.ical4j.model.property.Attendee property = (net.fortuna.ical4j.model.property.Attendee) iterator.next();
                attendees.add(importAttendee(sanitize(property)));
            }
            setValue(object, attendees);
        }
    }

    /**
     * Gets an attendee property from the supplied property list whose calendar address matches a specific URI.
     *
     * @param properties The property list to check
     * @param uri The URI to match the calendar user address against
     * @return The matching attendee, or <code>null</code> if not found
     */
    private static net.fortuna.ical4j.model.property.Attendee getMatchingAttendee(PropertyList properties, String uri) {
        for (Iterator<?> iterator = properties.iterator(); iterator.hasNext();) {
            net.fortuna.ical4j.model.property.Attendee property = (net.fortuna.ical4j.model.property.Attendee) iterator.next();
            URI calAddress = property.getCalAddress();
            if (null != calAddress && calAddress.toString().equals(uri)) {
                return property;
            }
        }
        return null;
    }

    private static net.fortuna.ical4j.model.property.Attendee exportAttendee(Attendee attendee, net.fortuna.ical4j.model.property.Attendee property) throws URISyntaxException {
        if (Strings.isNotEmpty(attendee.getUri())) {
            property.setValue(attendee.getUri());
        }
        if (Strings.isNotEmpty(attendee.getCn())) {
            property.getParameters().replace(new Cn(attendee.getCn()));
        } else {
            property.getParameters().removeAll(Parameter.CN);
        }
        if (null != attendee.getSentBy() && Strings.isNotEmpty(attendee.getSentBy().getUri())) {
            property.getParameters().replace(new SentBy(attendee.getSentBy().getUri()));
        } else {
            property.getParameters().removeAll(Parameter.SENT_BY);
        }
        if (null != attendee.getPartStat()) {
            property.getParameters().replace(getPartStat(attendee.getPartStat()));
        } else {
            property.getParameters().removeAll(Parameter.PARTSTAT);
        }
        if (null != attendee.getRole()) {
            property.getParameters().replace(new Role(attendee.getRole().toString()));
        } else {
            property.getParameters().removeAll(Parameter.ROLE);
        }
        if (null != attendee.getCuType()) {
            property.getParameters().replace(new CuType(attendee.getCuType().toString()));
        } else {
            property.getParameters().removeAll(Parameter.CUTYPE);
        }
        if (null != attendee.isRsvp()) {
            property.getParameters().replace(new Rsvp(attendee.isRsvp()));
        } else {
            property.getParameters().removeAll(Parameter.RSVP);
        }
        if (null != attendee.getMember() && 0 < attendee.getMember().size()) {
            property.getParameters().replace(new Member(getAddressList(attendee.getMember())));
        } else {
            property.getParameters().removeAll(Parameter.MEMBER);
        }
        if (Strings.isNotEmpty(attendee.getEMail())) {
            property.getParameters().replace(new XParameter("EMAIL", attendee.getEMail()));
        } else {
            property.getParameters().removeAll("EMAIL");
        }
        if (attendee.getTimestamp() > 0) {
            property.getParameters().replace(new CalendarServerDtStamp(new DateTime(attendee.getTimestamp()).toString()));
        } else {
            property.getParameters().removeAll(CalendarServerDtStamp.PARAMETER_NAME);
        }
        if (null != attendee.getExtendedParameters() && 0 < attendee.getExtendedParameters().size()) {
            for (ExtendedPropertyParameter parameter : attendee.getExtendedParameters()) {
                property.getParameters().replace(new XParameter(parameter.getName(), parameter.getValue()));
            }
        }
        return property;
    }

    private static PartStat getPartStat(ParticipationStatus participationStatus) {
        return null == participationStatus ? null : new PartStat(participationStatus.getValue());
    }

    private static Attendee importAttendee(net.fortuna.ical4j.model.property.Attendee property) {
        Attendee attendee = prepareAttendee();
        if (null != property.getCalAddress()) {
            attendee.setUri(property.getCalAddress().toString());
        } else if (Strings.isNotEmpty(property.getValue())) {
            if (property.getValue().startsWith("mailto:")) {
                attendee.setUri(property.getValue());
            } else {
                attendee.setUri("mailto:" + property.getValue());
            }
        }
        ParameterList parameterList = property.getParameters();
        if (null != parameterList && 0 < parameterList.size()) {
            List<ExtendedPropertyParameter> extendedParameters = new ArrayList<ExtendedPropertyParameter>();
            for (Iterator<?> iterator = parameterList.iterator(); iterator.hasNext();) {
                Parameter parameter = (Parameter) iterator.next();
                String value = parameter.getValue();
                switch (parameter.getName()) {
                    case Parameter.SENT_BY:
                        if (Strings.isNotEmpty(value)) {
                            CalendarUser sentByUser = new CalendarUser();
                            sentByUser.setUri(value);
                            attendee.setSentBy(sentByUser);
                        }
                        break;
                    case Parameter.CN:
                        attendee.setCn(value);
                        break;
                    case Parameter.PARTSTAT:
                        attendee.setPartStat(null != value ? new ParticipationStatus(value) : null);
                        break;
                    case Parameter.ROLE:
                        attendee.setRole(null != value ? new ParticipantRole(value) : null);
                        break;
                    case Parameter.CUTYPE:
                        attendee.setCuType(null != value ? new CalendarUserType(value) : null);
                        break;
                    case Parameter.RSVP:
                        attendee.setRsvp(Boolean.valueOf(value));
                        break;
                    case Parameter.MEMBER:
                        if (Member.class.isInstance(parameter)) {
                            attendee.setMember(getUris(((Member) parameter).getGroups()));
                        }
                        break;
                    case "EMAIL":
                        attendee.setEMail(value);
                        break;
                    case CalendarServerDtStamp.PARAMETER_NAME:
                        try {
                            DateTime dateTime = DateTime.parse(value);
                            if (null != dateTime) {
                                attendee.setTimestamp(dateTime.getTimestamp());
                            }
                        } catch (IllegalArgumentException e) {
                            LOG.debug("Recieved invalid data", e);
                        }
                        break;
                    default:
                        extendedParameters.add(new ExtendedPropertyParameter(parameter.getName(), parameter.getValue()));
                        break;
                }
            }
            if (0 < extendedParameters.size()) {
                attendee.setExtendedParameters(extendedParameters);
            }
        }
        return attendee;
    }

    private static net.fortuna.ical4j.model.property.Attendee sanitize(net.fortuna.ical4j.model.property.Attendee property) {
        URI uri = property.getCalAddress();
        if (null != uri) {
            if (Uris.INVALID_SCHEME.equals(uri.getScheme()) && Strings.isNotEmpty(uri.getSchemeSpecificPart())) {
                /*
                 * try and parse value as quoted internet address
                 */
                try {
                    QuotedInternetAddress address = new QuotedInternetAddress(uri.getSchemeSpecificPart());
                    if (Strings.isNotEmpty(address.getAddress())) {
                        property.setCalAddress(new URI("mailto", address.getAddress(), null));
                        if (Strings.isNotEmpty(address.getPersonal()) && Strings.isEmpty(optParameterValue(property, Parameter.CN))) {
                            property.getParameters().replace(new Cn(address.getPersonal()));
                        }
                    }
                } catch (AddressException | URISyntaxException e) {
                    // best effort
                }
            }
        }
        return property;
    }

    private static List<String> getUris(AddressList addressList) {
        if (null == addressList || 0 == addressList.size()) {
            return null;
        }
        List<String> uris = new ArrayList<String>(addressList.size());
        for (Iterator<?> iterator = addressList.iterator(); iterator.hasNext();) {
            URI uri = (URI) iterator.next();
            uris.add(String.valueOf(uri));
        }
        return uris;
    }

    private static AddressList getAddressList(List<String> uris) throws URISyntaxException {
        if (null == uris || 0 == uris.size()) {
            return null;
        }
        AddressList addressList = new AddressList();
        for (String uri : uris) {
            addressList.add(new URI(uri));
        }
        return addressList;
    }

    /**
     * Prepares a new, empty attendee, with all properties considered during iCal serialization being explicitly <i>set</i> to its neutral
     * value.
     * 
     * @return A new & prepared attendee instance
     */
    private static Attendee prepareAttendee() {
        Attendee attendee = new Attendee();
        attendee.setCn(null);
        attendee.setCuType(null);
        attendee.setEMail(null);
        attendee.setExtendedParameters(null);
        attendee.setMember(null);
        attendee.setPartStat(null);
        attendee.setRole(null);
        attendee.setRsvp(null);
        attendee.setSentBy(null);
        attendee.setUri(null);
        attendee.setTimestamp(0L);
        return attendee;
    }

}
