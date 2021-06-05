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

package com.openexchange.chronos.impl.availability.performer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.openexchange.chronos.Availability;
import com.openexchange.chronos.Available;
import com.openexchange.chronos.service.AvailableField;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.storage.CalendarAvailabilityStorage;
import com.openexchange.exception.OXException;

/**
 * {@link AbstractUpdatePerformer}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
abstract class AbstractUpdatePerformer extends AbstractPerformer {

    /**
     * Initialises a new {@link AbstractUpdatePerformer}.
     */
    AbstractUpdatePerformer(CalendarAvailabilityStorage storage, CalendarSession session) {
        super(storage, session);
    }

    ////////////////////////////////////////////////////////// HELPERS ////////////////////////////////////////////////////////

    /**
     * Prepares the specified {@link List} of {@link Availability} blocks for the storage.
     * <ul>
     * <li>Assigns identifiers for the {@link Available} blocks</li>
     * </ul>
     * 
     * @param storage The {@link CalendarAvailabilityStorage} instance
     * @param availabilities A {@link List} with {@link Availability} blocks to prepare
     * @return The {@link List} with the {@link Availability} identifiers
     * @throws OXException if an error is occurred
     */
    List<String> prepareForStorage(CalendarAvailabilityStorage storage, List<Availability> availabilities) throws OXException {
        List<String> caIds = new ArrayList<>(availabilities.size());
        for (Availability availability : availabilities) {
            prepareForStorage(storage, availability);
        }
        return caIds;
    }

    /**
     * Prepares the specified {@link Availability} block for the storage.
     * <ul>
     * <li>Assigns identifiers for the {@link Available} blocks</li>
     * </ul>
     * 
     * @param storage The {@link CalendarAvailabilityStorage} instance
     * @param availability An {@link Availability} block to prepare
     * @throws OXException if an error is occurred
     */
    void prepareForStorage(CalendarAvailabilityStorage storage, Availability availability) throws OXException {
        Date timeNow = new Date(System.currentTimeMillis());
        // Prepare the free slots
        for (Available available : availability.getAvailable()) {
            available.setId(available.contains(AvailableField.id) ? available.getId() : storage.nextAvailableId());
            available.setCalendarUser(getSession().getUserId());
            // Set the creation timestamp (a.k.a. dtstamp) from the last modified if not present
            available.setLastModified(timeNow);
            if (available.getCreated() == null) {
                available.setCreated(timeNow);
            }
            if (available.getCreationTimestamp() == null) {
                available.setCreationTimestamp(timeNow);
            }
            if (available.getStartTime() == null) {
                available.setStartTime(CheckUtil.MIN_DATE_TIME);
            }
            if (available.getEndTime() == null) {
                available.setEndTime(CheckUtil.MAX_DATE_TIME);
            }
        }
    }

}
