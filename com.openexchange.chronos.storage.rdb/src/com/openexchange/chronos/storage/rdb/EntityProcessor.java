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

package com.openexchange.chronos.storage.rdb;

import static com.openexchange.chronos.common.CalendarUtils.extractEMailAddress;
import static com.openexchange.chronos.common.CalendarUtils.isInternal;
import static com.openexchange.chronos.common.CalendarUtils.optExtendedParameter;
import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import com.github.mangstadt.vinnie.SyntaxStyle;
import com.github.mangstadt.vinnie.VObjectParameters;
import com.github.mangstadt.vinnie.VObjectProperty;
import com.github.mangstadt.vinnie.io.Context;
import com.github.mangstadt.vinnie.io.SyntaxRules;
import com.github.mangstadt.vinnie.io.VObjectDataAdapter;
import com.github.mangstadt.vinnie.io.VObjectReader;
import com.github.mangstadt.vinnie.io.VObjectWriter;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.DelegatingEvent;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.ExtendedPropertyParameter;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.ResourceId;
import com.openexchange.chronos.common.EntityUtils;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.exception.OXException;
import com.openexchange.java.Reference;
import com.openexchange.java.Strings;
import com.openexchange.tools.arrays.Arrays;

/**
 * {@link EntityProcessor}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class EntityProcessor {

    private final EntityResolver entityResolver;
    private final int contextId;

    /**
     * Initializes a new {@link EntityProcessor}.
     *
     * @param contextId The context identifier
     * @param entityResolver The entity resolver to use
     */
    public EntityProcessor(int contextId, EntityResolver entityResolver) {
        super();
        this.contextId = contextId;
        this.entityResolver = entityResolver;
    }

    /**
     * Gets the underlying entity resolver.
     *
     * @return The entity resolver
     */
    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    /**
     * Adjusts certain properties of an event prior inserting it into the database.
     *
     * @param event The event to adjust
     * @return The (possibly adjusted) event reference
     */
    public Event adjustPriorSave(Event event) throws OXException {
        if (event.containsOrganizer() && null != event.getOrganizer()) {
            /*
             * encode organizer
             */
            final Organizer storedOrganizer = new Organizer();
            storedOrganizer.setUri(encode(event.getOrganizer()));
            event = new DelegatingEvent(event) {

                @Override
                public Organizer getOrganizer() {
                    return storedOrganizer;
                }

                @Override
                public boolean containsOrganizer() {
                    return true;
                }
            };
        }
        return event;
    }


    /**
     * Adjusts certain properties of an attendee prior inserting it into the database.
     * <p/>
     * This includes the default adjustments for internal attendees, as well as assigning virtual (negative) entity identifiers for
     * external attendees and moving properties to the extended parameters collection.
     *
     * @param attendee The attendee to adjust
     * @param usedEntities The so far used entities to avoid hash collisions when generating virtual entity identifiers for external attendees
     * @param entitySalt Random to enrich the hash calculation for the entity identifiers of external attendees
     * @return The (possibly adjusted) attendee reference
     */
    public Attendee adjustPriorInsert(Attendee attendee, Set<Integer> usedEntities, int entitySalt) throws OXException {
        if (isInternal(attendee)) {
            /*
             * track entity & remove redundant properties for non-individual internal attendees
             */
            usedEntities.add(I(attendee.getEntity()));
            if (CalendarUserType.GROUP.equals(attendee.getCuType()) || CalendarUserType.RESOURCE.equals(attendee.getCuType()) ||
                CalendarUserType.ROOM.equals(attendee.getCuType())) {
                AttendeeField[] preservedFields = Arrays.remove(AttendeeField.values(), AttendeeField.CN, AttendeeField.COMMENT);
                attendee = com.openexchange.chronos.common.mapping.AttendeeMapper.getInstance().copy(attendee, null, preservedFields);
            }
            return attendee;
        }
        /*
         * assign and track entity & store email as extended parameter if needed
         */
        Attendee savedAttendee = com.openexchange.chronos.common.mapping.AttendeeMapper.getInstance().copy(attendee, null, (AttendeeField[]) null);
        savedAttendee.setEntity(EntityUtils.determineEntity(attendee, usedEntities, entitySalt));
        if (Strings.isNotEmpty(attendee.getEMail()) && false == attendee.getEMail().equals(extractEMailAddress(attendee.getUri()))) {
            List<ExtendedPropertyParameter> extendedParameters = savedAttendee.getExtendedParameters();
            if (null == extendedParameters) {
                extendedParameters = new ArrayList<ExtendedPropertyParameter>(1);
                savedAttendee.setExtendedParameters(extendedParameters);
            } else {
                ExtendedPropertyParameter parameter = optExtendedParameter(extendedParameters, "EMAIL");
                if (null != parameter) {
                    extendedParameters.remove(parameter);
                }
            }
            extendedParameters.add(new ExtendedPropertyParameter("EMAIL", attendee.getEMail()));
        }
        return savedAttendee;
    }

    /**
     * Adjusts certain properties of an event after loading it from the database.
     *
     * @param event The event to adjust
     * @return The (possibly adjusted) event reference
     */
    public Event adjustAfterLoad(Event event) throws OXException {
        if (null != event.getOrganizer()) {
            /*
             * decode organizer
             */
            event.setOrganizer(decode(event.getOrganizer().getUri()));
        }
        /*
         * apply further entity data
         */
        if (null != entityResolver) {
            if (null != event.getCalendarUser()) {
                entityResolver.applyEntityData(event.getCalendarUser(), CalendarUserType.INDIVIDUAL);
            }
            if (null != event.getCreatedBy()) {
                entityResolver.applyEntityData(event.getCreatedBy(), CalendarUserType.INDIVIDUAL);
            }
            if (null != event.getModifiedBy()) {
                entityResolver.applyEntityData(event.getModifiedBy(), CalendarUserType.INDIVIDUAL);
            }
        }
        return event;
    }

    /**
     * Adjusts certain properties of an attendee after loading it from the database.
     *
     * @param attendee The attendee to adjust
     * @return The (possibly adjusted) attendee reference
     */
    public Attendee adjustAfterLoad(Attendee attendee) throws OXException {
        /*
         * remove virtual (negative) entity identifier for external attendees
         */
        if (0 > attendee.getEntity()) {
            attendee.removeEntity();
        }
        List<ExtendedPropertyParameter> extendedParameters = attendee.getExtendedParameters();
        if (null != extendedParameters && 0 < extendedParameters.size()) {
            /*
             * move over extended parameters as needed
             */
            ExtendedPropertyParameter parameter = optExtendedParameter(extendedParameters, "EMAIL");
            if (null != parameter && extendedParameters.remove(parameter)) {
                attendee.setEMail(parameter.getValue());
            }
        }
        /*
         * apply entity data afterwards
         */
        if (null != entityResolver) {
            attendee = entityResolver.applyEntityData(attendee);
        }
        return attendee;
    }

    private String encode(Organizer organizer) throws OXException {
        if (null == organizer) {
            return null;
        }
        CalendarUser sentBy = organizer.getSentBy();
        String uri;
        String cn;
        if (0 < organizer.getEntity()) {
            uri = ResourceId.forUser(contextId, organizer.getEntity());
            if (null == sentBy) {
                /*
                 * no parameters needed, use uri as-is
                 */
                return uri;
            }
            cn = null;
        } else {
            uri = organizer.getUri();
            cn = organizer.getCn();
        }
        /*
         * encode as vobject
         */
        VObjectParameters parameters = new VObjectParameters();
        if (null != sentBy) {
            if (0 < sentBy.getEntity()) {
                parameters.put("SENT-BY", ResourceId.forUser(contextId, organizer.getSentBy().getEntity()));
            } else if (Strings.isNotEmpty(organizer.getSentBy().getUri())) {
                parameters.put("SENT-BY", organizer.getSentBy().getUri());
            }
        }
        if (null != cn) {
            parameters.put("CN", cn);
        }
        VObjectProperty property = new VObjectProperty(null, "X", parameters, uri);
        return writeVObjectProperty(property);
    }

    private <T extends CalendarUser> T parseResourceId(T calendarUser, String value, boolean fallbackToUri) throws OXException {
        ResourceId resourceId = ResourceId.parse(value);
        if (null != resourceId && CalendarUserType.INDIVIDUAL.equals(resourceId.getCalendarUserType())) {
            if (null != entityResolver) {
                entityResolver.applyEntityData(calendarUser, resourceId.getEntity());
            } else {
                calendarUser.setEntity(resourceId.getEntity());
                calendarUser.setUri(resourceId.getURI());
            }
            return calendarUser;
        }
        if (fallbackToUri) {
            calendarUser.setUri(value);
            if (null != entityResolver) {
                entityResolver.applyEntityData(calendarUser, CalendarUserType.INDIVIDUAL);
            }
            return calendarUser;
        }
        return null;
    }

    private Organizer decode(String value) throws OXException {
        /*
         * attempt to parse internal organizer directly
         */
        Organizer organizer = parseResourceId(new Organizer(), value, false);
        if (null != organizer) {
            return organizer;
        }
        /*
         * parse as vobject, otherwise
         */
        VObjectProperty property = parseVObjectProperty(value);
        if (null == property) {
            return null;
        }
        organizer = parseResourceId(new Organizer(), property.getValue(), true);
        VObjectParameters parameters = property.getParameters();
        if (null != parameters) {
            for (Entry<String, List<String>> parameter : parameters) {
                String firstValue = parameter.getValue().get(0);
                if ("SENT-BY".equals(parameter.getKey())) {
                    try {
                        organizer.setSentBy(parseResourceId(new CalendarUser(), firstValue, true));
                    } catch (OXException e) {
                        if (CalendarExceptionCodes.INVALID_CALENDAR_USER.equals(e)) {
                            org.slf4j.LoggerFactory.getLogger(EntityProcessor.class).debug(
                                "Ignoring invalid proxy {} for SENT-BY property of {}.", firstValue, organizer, e);
                        } else {
                            throw e;
                        }
                    }
                }
                if ("CN".equals(parameter.getKey())) {
                    organizer.setCn(firstValue);
                }
            }
        }
        return organizer;
    }

    private static String writeVObjectProperty(VObjectProperty property) throws OXException {
        try (StringWriter stringWriter = new StringWriter(256);
            VObjectWriter vObjectWriter = new VObjectWriter(stringWriter, SyntaxStyle.NEW)) {
            vObjectWriter.getFoldedLineWriter().setLineLength(null);
            vObjectWriter.setCaretEncodingEnabled(true);
            vObjectWriter.writeProperty(property);
            return stringWriter.toString();
        } catch (IOException e) {
            throw CalendarExceptionCodes.DB_ERROR.create(e, e.getMessage());
        }
    }

    private static VObjectProperty parseVObjectProperty(String value) throws OXException {
        try (StringReader stringReader = new StringReader("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + value.trim() + "\r\nEND:VCALENDAR\r\n");
            VObjectReader vObjectReader = new VObjectReader(stringReader, SyntaxRules.iCalendar())) {
            final Reference<VObjectProperty> vObjectReference = new Reference<VObjectProperty>();
            vObjectReader.parse(new VObjectDataAdapter() {

                @Override
                public void onProperty(VObjectProperty property, Context context) {
                    vObjectReference.setValue(property);
                }
            });
            return vObjectReference.getValue();
        } catch (IOException e) {
            throw CalendarExceptionCodes.DB_ERROR.create(e, e.getMessage());
        }
    }

}
