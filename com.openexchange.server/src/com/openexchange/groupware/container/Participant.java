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

package com.openexchange.groupware.container;

import java.io.Serializable;

/**
 * {@link Participant} - Represents a participant of either a group appointment or group task.
 *
 * @author <a href="mailto:sebastian.kauss@open-xchange.com">Sebastian Kauss</a>
 */
public interface Participant extends Serializable, Cloneable {

    static final int USER = 1;

    static final int GROUP = 2;

    static final int RESOURCE = 3;

    static final int RESOURCEGROUP = 4;

    static final int EXTERNAL_USER = 5;

    static final int EXTERNAL_GROUP = 6;

    static final int NO_ID = -1;

    /**
     * @deprecated Use explicit constructor. {@link UserParticipant#UserParticipant(int)}, {@link GroupParticipant#GroupParticipant(int)},
     *             {@link ResourceParticipant#ResourceParticipant(int)}, {@link ResourceGroupParticipant#ResourceGroupParticipant(int)}
     */
    @Deprecated
    void setIdentifier(final int id);

    /**
     * Gets this participant's identifier.
     *
     * @return This participant's identifier
     */
    int getIdentifier();

    /**
     * Sets this participant's display name.
     *
     * @param displayName The display name to set
     */
    void setDisplayName(final String displayName);

    /**
     * Gets this participant's display name.
     *
     * @return This participant's display name
     */
    String getDisplayName();

    /**
     * Gets this participant's email address.
     *
     * @return This participant's email address.
     */
    String getEmailAddress();

    /**
     * Gets this participant's type.
     *
     * @return This participant's type; either {@link #USER}, {@link #GROUP}, {@link #RESOURCE}, {@link #RESOURCEGROUP},
     *         {@link #EXTERNAL_USER} , or {@link #EXTERNAL_GROUP}
     */
    int getType();

    /**
     * Checks if notification for this participant shall be ignored.<br>
     * Default is <code>false</code>.
     *
     * @return <code>true</code> if notification for this participant shall be ignored; otherwise <code>false</code>
     */
    boolean isIgnoreNotification();

    /**
     * Sets whether notification for this participant are discarded.
     *
     * @param ignoreNotification <code>true</code> to ignore any notification for this participant; otherwise <code>false</code>
     */
    void setIgnoreNotification(boolean ignoreNotification);

    /**
     * Should delegate to {@link java.lang.Object#clone()}
     *
     * @return The clone
     * @throws CloneNotSupportedException If {@link Cloneable} interface is not implemented
     */
    Participant getClone() throws CloneNotSupportedException;
}
