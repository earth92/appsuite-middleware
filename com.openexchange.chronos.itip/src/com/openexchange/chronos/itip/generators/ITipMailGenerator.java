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

package com.openexchange.chronos.itip.generators;

import java.util.List;
import com.openexchange.chronos.itip.ITipMethod;
import com.openexchange.chronos.itip.ITipRole;
import com.openexchange.exception.OXException;

/**
 * {@link ITipMailGenerator}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public interface ITipMailGenerator {

    /**
     * Generates an invitation mail to a new event for given participant.
     * 
     * @param participant The {@link NotificationParticipant} to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#REQUEST
     */
    NotificationMail generateCreateMailFor(NotificationParticipant participant) throws OXException;

    /**
     * Generates a mail to updated an existing event for given participant.
     * 
     * @param participant The {@link NotificationParticipant} to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#REQUEST
     */
    NotificationMail generateUpdateMailFor(NotificationParticipant participant) throws OXException;

    /**
     * Generates a deletion mail of an deleted event for given participant.
     * 
     * @param participant The {@link NotificationParticipant} to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#CANCEL
     */
    NotificationMail generateDeleteMailFor(NotificationParticipant participant) throws OXException;

    /**
     * Generates a invitation mail to a new created event exception for given participant.
     * 
     * @param participant The {@link NotificationParticipant} to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#REQUEST
     */
    NotificationMail generateCreateExceptionMailFor(NotificationParticipant participant) throws OXException;

    /**
     * Generates a refresh mail for given participant.
     * 
     * @param participant The {@link NotificationParticipant} to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#REFRESH
     */
    NotificationMail generateRefreshMailFor(NotificationParticipant participant) throws OXException;

    /**
     * Generates a decline counter mail to given participant.
     * 
     * @param participant The {@link NotificationParticipant} to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#DECLINECOUNTER
     */
    NotificationMail generateDeclineCounterMailFor(NotificationParticipant participant) throws OXException;

    /**
     * Generates an invitation mail to a new event for given participant.
     * 
     * @param email The mail address of the participant to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#REQUEST
     */
    NotificationMail generateCreateMailFor(String email) throws OXException;

    /**
     * Generates a mail to updated an existing event for given participant.
     * 
     * @param email The mail address of the participant to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#REQUEST
     */
    NotificationMail generateUpdateMailFor(String email) throws OXException;

    /**
     * Generates a deletion mail of an deleted event for given participant.
     * 
     * @param email The mail address of the participant to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#CANCEL
     */
    NotificationMail generateDeleteMailFor(String email) throws OXException;

    /**
     * Generates a invitation mail to a new created event exception for given participant.
     * 
     * @param email The mail address of the participant to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#REQUEST
     */
    NotificationMail generateCreateExceptionMailFor(String email) throws OXException;

    /**
     * Generates a refresh mail for given participant.
     * 
     * @param email The mail address of the participant to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#REFRESH
     */
    NotificationMail generateRefreshMailFor(String email) throws OXException;

    /**
     * Generates a decline counter mail to given participant.
     * 
     * @param email The mail address of the participant to send a mail to
     * @return A {@link NotificationMail}
     * @throws OXException If mail can't be created or rendered
     * @see ITipMethod#DECLINECOUNTER
     */
    NotificationMail generateDeclineCounterMailFor(String email) throws OXException;

    /**
     * Get all recipients to send a mail to.
     * 
     * @return A {@link List} of all {@link NotificationParticipant}s
     * @see NotificationParticipantResolver#resolveAllRecipients(com.openexchange.chronos.Event, com.openexchange.chronos.Event, com.openexchange.groupware.ldap.User, com.openexchange.groupware.ldap.User, com.openexchange.groupware.contexts.Context,
     *      com.openexchange.session.Session, com.openexchange.chronos.CalendarUser)
     */
    List<NotificationParticipant> getRecipients();

    /**
     * If the user has the role of the {@link ITipRole#ORGANIZER}
     * 
     * @return <code>true</code> if the user is the organizer, <code>false</code> otherwise
     */
    boolean userIsTheOrganizer();

    /**
     * Clones the current actor and set {@link NotificationParticipant#setVirtual(boolean)} to <code>true</code>.
     * 
     * Efficiently enables mail generation for the current actor/user.
     */
    void noActor();

}
