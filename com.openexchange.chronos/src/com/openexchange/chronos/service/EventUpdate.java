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
 *    trademarks of the OX Software GmbH group of companies.
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

package com.openexchange.chronos.service;

import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.AlarmField;
import com.openexchange.chronos.Attachment;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.Conference;
import com.openexchange.chronos.ConferenceField;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.groupware.tools.mappings.common.CollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.ItemUpdate;
import com.openexchange.groupware.tools.mappings.common.SimpleCollectionUpdate;

/**
 * {@link EventUpdate}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public interface EventUpdate extends ItemUpdate<Event, EventField> {

    /**
     * Gets the attendee-related changes between original and updated event.
     *
     * @return The attendee updates, or an empty collection update if there were no attendee-related changes
     */
    CollectionUpdate<Attendee, AttendeeField> getAttendeeUpdates();

    /**
     * Gets the conference-related changes between original and updated event.
     *
     * @return The conference updates, or an empty collection update if there were no conference-related changes
     */
    CollectionUpdate<Conference, ConferenceField> getConferenceUpdates();

    /**
     * Gets the alarm-related changes between original and updated event. Only alarms of the actual calendar user are considered.
     *
     * @return The alarm updates, or an empty collection update if there were no alarm-related changes
     */
    CollectionUpdate<Alarm, AlarmField> getAlarmUpdates();

    /**
     * Gets the attachment-related changes between original and updated event.
     *
     * @return The attachment updates, or an empty collection update if there were no attachment-related changes
     */
    SimpleCollectionUpdate<Attachment> getAttachmentUpdates();

}
