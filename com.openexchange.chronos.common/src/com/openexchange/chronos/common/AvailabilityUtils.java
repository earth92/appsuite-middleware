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

package com.openexchange.chronos.common;

import java.util.Date;
import org.dmfs.rfc5545.DateTime;
import com.openexchange.chronos.Availability;
import com.openexchange.chronos.Available;
import com.openexchange.chronos.BusyType;
import com.openexchange.chronos.FbType;

/**
 * {@link AvailabilityUtils}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public final class AvailabilityUtils {

    ////////////////////////////////////////// Calendar Availability ////////////////////////////////////////////////

    /**
     * Checks if the {@link DateTime} interval of the specified {@link Availability} A precedes and intersects with the
     * {@link DateTime} interval of the specified {@link Availability} interval B
     * 
     * @param a The {@link Availability} A
     * @param b The {@link Availability} B
     * @return <code>true</code> if the {@link DateTime} interval of the specified {@link Availability} A precedes and intersects
     *         with {@link DateTime} interval of the specified {@link Availability} B; <code>false</code> otherwise
     */
    public static boolean precedesAndIntersects(Availability a, Availability b) {
        return precedesAndIntersects(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /**
     * Checks if the {@link DateTime} interval of the specified {@link Availability} A succeeds and intersects with the
     * {@link DateTime} interval of the specified {@link Availability} interval B
     * 
     * @param a The {@link Availability} A
     * @param b The {@link Availability} B
     * @return <code>true</code> if the {@link DateTime} interval of the specified {@link Availability} A succeeds and intersects
     *         with {@link DateTime} interval of the specified {@link Availability} B; <code>false</code> otherwise
     */
    public static boolean succeedsAndIntersects(Availability a, Availability b) {
        return succeedsAndIntersects(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /**
     * Checks if the {@link DateTime} interval of the specified {@link Availability} A is completely contained
     * with in the {@link DateTime} interval of the specified {@link Availability} B
     * 
     * @param startA The start date of the interval A
     * @param endA The end date of the interval A
     * @param startB the start date of the interval B
     * @param endB The end date of the interval B
     * @return <code>true</code> if the {@link DateTime} interval of the specified {@link Availability} A is completely contained
     *         with in the {@link DateTime} interval of the specified {@link Availability} B; <code>false</code> otherwise
     */
    public static boolean contained(Availability a, Availability b) {
        return contained(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /**
     * Checks if the specified availability A intersects with the specified availability B.
     * Two checks are performed internally:
     * <ul>
     * <li>whether the starting point of A is before the ending point of B</li>
     * <li>whether the ending point of A is after the starting point of B</li>
     * </ul>
     * 
     * @param a The {@link Availability} A
     * @param b The {@link Availability} B
     * @return <code>true</code> if they intersect; <code>false</code> otherwise
     */
    public static boolean intersect(Availability a, Availability b) {
        return intersect(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /////////////////////////////////////////// Calendar Free Slot ////////////////////////////////////////////////////

    /**
     * Checks if the {@link DateTime} interval of the specified {@link Available} A precedes and intersects with the
     * {@link DateTime} interval of the specified {@link Available} interval B
     * 
     * @param a The {@link Available} A
     * @param b The {@link Available} B
     * @return <code>true</code> if the {@link DateTime} interval of the specified {@link Available} A precedes and intersects
     *         with {@link DateTime} interval of the specified {@link Available} B; <code>false</code> otherwise
     */
    public static boolean precedesAndIntersects(Available a, Available b) {
        return precedesAndIntersects(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /**
     * Checks if the {@link DateTime} interval of the specified {@link Available} A succeeds and intersects with the
     * {@link DateTime} interval of the specified {@link Available} interval B
     * 
     * @param a The {@link Available} A
     * @param b The {@link Available} B
     * @return <code>true</code> if the {@link DateTime} interval of the specified {@link Available} A succeeds and intersects
     *         with {@link DateTime} interval of the specified {@link Available} B; <code>false</code> otherwise
     */
    public static boolean succeedsAndIntersects(Available a, Available b) {
        return succeedsAndIntersects(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /**
     * Checks if the {@link Date} interval of the specified {@link Available} A is completely contained
     * with in the {@link Date} interval of the specified {@link Available} B
     * 
     * @param startA The start date of the interval A
     * @param endA The end date of the interval A
     * @param startB the start date of the interval B
     * @param endB The end date of the interval B
     * @return <code>true</code> if the {@link Date} interval of the specified {@link Available} A is completely contained
     *         with in the {@link Date} interval of the specified {@link Available} B; <code>false</code> otherwise
     */
    public static boolean contained(Available a, Available b) {
        return contained(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /**
     * Checks if the specified free slot A intersects with the specified free slot B.
     * Two checks are performed internally:
     * <ul>
     * <li>whether the starting point of A is before the ending point of B</li>
     * <li>whether the ending point of A is after the starting point of B</li>
     * </ul>
     * 
     * @param a The {@link Availability} A
     * @param b The {@link Availability} B
     * @return <code>true</code> if they intersect; <code>false</code> otherwise
     */
    public static boolean intersect(Available a, Available b) {
        return intersect(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /////////////////////////////////////////////// DateTime /////////////////////////////////////////////

    /**
     * Checks if the specified {@link DateTime} interval A is completely contained with in the specified {@link DateTime} interval B
     * 
     * @param startA The start date of the interval A
     * @param endA The end date of the interval A
     * @param startB the start date of the interval B
     * @param endB The end date of the interval B
     * @return <code>true</code> if the specified {@link DateTime} interval A is completely
     *         contained with in the specified {@link DateTime} interval B; <code>false</code> otherwise
     */
    public static boolean contained(DateTime startA, DateTime endA, DateTime startB, DateTime endB) {
        return (startA.after(startB) || startA.equals(startB)) && (endA.before(endB) || endA.equals(endB));
    }

    /**
     * Checks if the specified {@link DateTime} interval A precedes and intersects with the specified {@link DateTime} interval B
     * 
     * @param startA The start date of the interval A
     * @param endA The end date of the interval A
     * @param startB the start date of the interval B
     * @param endB The end date of the interval B
     * @return <code>true</code> if the specified {@link DateTime} interval A precedes and intersects
     *         with the specified {@link DateTime} interval B; <code>false</code> otherwise
     */
    public static boolean precedesAndIntersects(DateTime startA, DateTime endA, DateTime startB, DateTime endB) {
        if (!intersect(startA, endA, startB, endB)) {
            return false;
        }
        return startA.before(startB) && endA.after(startB);
    }

    /**
     * Checks if the specified {@link DateTime} interval A succeeds and intersects with the specified {@link DateTime} interval B
     * 
     * @param startA The start date of the interval A
     * @param endA The end date of the interval A
     * @param startB the start date of the interval B
     * @param endB The end date of the interval B
     * @return <code>true</code> if the specified {@link DateTime} interval A succeeds and intersects
     *         with the specified {@link DateTime} interval B; <code>false</code> otherwise
     */
    public static boolean succeedsAndIntersects(DateTime startA, DateTime endA, DateTime startB, DateTime endB) {
        if (!intersect(startA, endA, startB, endB)) {
            return false;
        }
        return startA.before(endB) && endA.after(endB);
    }

    /**
     * Two checks are performed:
     * <ul>
     * <li>whether the starting point of A is before the ending point of B</li>
     * <li>whether the ending point of A is after the starting point of B</li>
     * </ul>
     * 
     * @param startA The starting date A
     * @param endA The ending date A
     * @param startB The starting date B
     * @param endB The ending date B
     * @return <code>true</code> if they intersect; <code>false</code> otherwise
     */
    public static boolean intersect(DateTime startA, DateTime endA, DateTime startB, DateTime endB) {
        return startA.before(endB) && endA.after(startB);
    }

    /**
     * Check for intersection but not contains
     * 
     * @param startA The start date of interval A
     * @param endA The end date of interval A
     * @param startB The start date of interval B
     * @param endB The end date of interval B
     * @return <code>true</code> if the date intervals intersect but NOT contained within each other; <code>false</code> otherwise
     */
    public static boolean intersectsButNotContained(DateTime startA, DateTime endA, DateTime startB, DateTime endB) {
        if (!intersect(startA, endA, startB, endB)) {
            return false;
        }

        // Extra check for preceding OR succeeding respectively
        return (startA.before(startB) && endA.before(endB)) || (startA.after(startB) && endA.after(endB));
    }

    ////////////////////////////////////////////////////// Date //////////////////////////////////////////////////

    /**
     * Checks if the specified {@link Date} interval A is completely contained with in the specified {@link Date} interval B
     * 
     * @param startA The start date of the interval A
     * @param endA The end date of the interval A
     * @param startB the start date of the interval B
     * @param endB The end date of the interval B
     * @return <code>true</code> if the specified {@link Date} interval A is completely
     *         contained with in the specified {@link Date} interval B; <code>false</code> otherwise
     */
    public static boolean contained(Date startA, Date endA, Date startB, Date endB) {
        return (startA.after(startB) || startA.equals(startB)) && (endA.before(endB) || endA.equals(endB));
    }

    /**
     * Checks if the specified {@link Date} interval A precedes and intersects with the specified {@link Date} interval B
     * 
     * @param startA The start date of the interval A
     * @param endA The end date of the interval A
     * @param startB the start date of the interval B
     * @param endB The end date of the interval B
     * @return <code>true</code> if the specified {@link Date} interval A precedes and intersects
     *         with the specified {@link Date} interval B; <code>false</code> otherwise
     */
    public static boolean precedesAndIntersects(Date startA, Date endA, Date startB, Date endB) {
        if (!intersect(startA, endA, startB, endB)) {
            return false;
        }
        return (startA.before(startB) || startA.equals(startB)) && endA.after(startB);
    }

    /**
     * Checks if the specified {@link Date} interval A succeeds and intersects with the specified {@link Date} interval B
     * 
     * @param startA The start date of the interval A
     * @param endA The end date of the interval A
     * @param startB the start date of the interval B
     * @param endB The end date of the interval B
     * @return <code>true</code> if the specified {@link Date} interval A succeeds and intersects
     *         with the specified {@link Date} interval B; <code>false</code> otherwise
     */
    public static boolean succeedsAndIntersects(Date startA, Date endA, Date startB, Date endB) {
        if (!intersect(startA, endA, startB, endB)) {
            return false;
        }
        return startA.before(endB) && (endA.after(endB) || endA.equals(endB));
    }

    /**
     * Two checks are performed:
     * <ul>
     * <li>whether the starting point of A is before the ending point of B</li>
     * <li>whether the ending point of A is after the starting point of B</li>
     * </ul>
     * 
     * @param startA The starting date A
     * @param endA The ending date A
     * @param startB The starting date B
     * @param endB The ending date B
     * @return <code>true</code> if they intersect; <code>false</code> otherwise
     */
    public static boolean intersect(Date startA, Date endA, Date startB, Date endB) {
        return startA.before(endB) && endA.after(startB);
    }

    //////////////////////////////////// Merge /////////////////////////////////////////

    /**
     * Merges the specified {@link Available}s
     * 
     * @param a The {@link Available} A
     * @param b The {@link Available} B
     * @return a new merged {@link Available} instance
     */
    public static Available merge(Available a, Available b) {
        Available merged = new Available();
        merged.setStartTime(a.getStartTime().before(b.getStartTime()) ? a.getStartTime() : b.getStartTime());
        merged.setEndTime(a.getEndTime().after(b.getEndTime()) ? a.getEndTime() : b.getEndTime());
        //TOOD: copy all relevant attributes from a and b to the merged available block
        return merged;
    }

    ////////////////////////////////////// Convert ///////////////////////////////////////

    /**
     * Converts the specified {@link BusyType} to its equivalent {@link FbType}
     * 
     * @param busyType The {@link BusyType} to convert
     * @return The equivalent {@link FbType}
     */
    public static FbType convertFreeBusyType(BusyType busyType) {
        switch (busyType) {
            case BUSY:
                return FbType.BUSY;
            case BUSY_TENTATIVE:
                return FbType.BUSY_TENTATIVE;
            case BUSY_UNAVAILABLE:
                return FbType.BUSY_UNAVAILABLE;
            default:
                return FbType.BUSY;
        }
    }
}
