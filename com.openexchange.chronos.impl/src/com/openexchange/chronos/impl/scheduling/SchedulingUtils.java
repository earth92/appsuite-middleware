/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH. group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.chronos.impl.scheduling;

import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.service.EventUpdate;

/**
 * {@link SchedulingUtils}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v8.0.0
 */
public class SchedulingUtils {

    private SchedulingUtils() {
        super();
    }

    /**
     * 
     * Check if originator is allowed to perform any action, either by perfect match comparing to the organizer or by comparing to the sent-by field of the organizer
     *
     * @param originalEvent The original event to get the organizer from
     * @param originator The originator of a scheduling action
     * @return <code>true</code> if the originator matches the organizer, <code>false</code> otherwise
     */
    public static boolean originatorMatches(Event originalEvent, CalendarUser originator) {
        Organizer organizer = originalEvent.getOrganizer();
        return CalendarUtils.matches(originator, organizer) //perfect match 
            || (null != organizer.getSentBy() && CalendarUtils.matches(originator, organizer.getSentBy()));
    }

    /**
     * Gets a value indicating whether an update of an event is only about adjusted
     * timestamps for consistency, e.g. master is "touched" when exceptions is updated
     *
     * @param eventUpdate The update to check
     * @return <code>true</code> if the event was only touched for consistency, <code>false</code> otherwise
     */
    public static boolean isTouchedEvent(EventUpdate eventUpdate) {
        return 3 == eventUpdate.getUpdatedFields().size() // @formatter:off
            && eventUpdate.getUpdatedFields().contains(EventField.LAST_MODIFIED) 
            && eventUpdate.getUpdatedFields().contains(EventField.MODIFIED_BY) 
            && eventUpdate.getUpdatedFields().contains(EventField.TIMESTAMP);// @formatter:on
    }

}
