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

package com.openexchange.chronos.provider.account;

import java.util.List;
import org.json.JSONObject;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.CalendarProvider;
import com.openexchange.chronos.provider.basic.CalendarSettings;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.exception.OXException;
import com.openexchange.osgi.annotation.SingletonService;
import com.openexchange.session.Session;

/**
 * {@link CalendarAccountService}
 *
 * @author <a href="mailto:Jan-Oliver.Huhn@open-xchange.com">Jan-Oliver Huhn</a>
 * @since v7.10.0
 */
@SingletonService
public interface CalendarAccountService {

    /**
     * Gets a list of registered calendar providers.
     *
     * @return The calendar providers
     */
    List<CalendarProvider> getProviders() throws OXException;

    /**
     * Probes specific client-supplied, possibly erroneous and/or incomplete calendar settings by checking if they are valid or further
     * configuration settings are required. This step is typically performed prior creating a new account.
     * <p/>
     * In case the settings are valid and can be used to create a new calendar account, the result will contain the proposed calendar
     * settings, which may be enhanced by additional default values for certain properties of the calendar. The client is encouraged to
     * create the account with these settings, then.
     * <p/>
     * In case the settings are invalid or incomplete, an appropriate exception is thrown providing further details about the root cause.
     *
     * @param session The user's session
     * @param providerId The identifier of the corresponding calendar provider
     * @param settings Calendar settings to be probed for the new account as supplied by the client
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     * @return The proposed calendar settings, enhanced by additional default values
     */
    CalendarSettings probeAccountSettings(Session session, String providerId, CalendarSettings settings, CalendarParameters parameters) throws OXException;

    /**
     * Creates a new calendar account for the current session's user in a <i>basic</i> calendar provider.
     *
     * @param session The current session
     * @param providerId The identifier of the corresponding calendar provider
     * @param settings Calendar settings for the new account as supplied by the client
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     * @return The created calendar account
     */
    CalendarAccount createAccount(Session session, String providerId, CalendarSettings settings, CalendarParameters parameters) throws OXException;

    /**
     * Creates a new calendar account for the current session's user in a <i>folder</i> calendar provider.
     *
     * @param session The current session
     * @param providerId The identifier of the corresponding calendar provider
     * @param userConfig Provider-specific <i>user</i> configuration data for the calendar account
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     * @return The created calendar account
     */
    CalendarAccount createAccount(Session session, String providerId, JSONObject userConfig, CalendarParameters parameters) throws OXException;

    /**
     * Updates an existing calendar account in a <i>basic</i> calendar provider.
     *
     * @param session The current session
     * @param id The identifier of the account to update
     * @param settings Updated calendar settings for the account as supplied by the client
     * @param clientTimestamp The last timestamp known by the client to catch concurrent updates
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     * @return The updated calendar account
     */
    CalendarAccount updateAccount(Session session, int id, CalendarSettings settings, long clientTimestamp, CalendarParameters parameters) throws OXException;

    /**
     * Updates an existing calendar account.
     *
     * @param session The current session
     * @param id The identifier of the account to update
     * @param userConfig Updated provider-specific <i>user</i> configuration data for the calendar account
     * @param clientTimestamp The last timestamp known by the client to catch concurrent updates
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     * @return The updated calendar account
     */
    CalendarAccount updateAccount(Session session, int id, JSONObject userConfig, long clientTimestamp, CalendarParameters parameters) throws OXException;

    /**
     * Deletes an existing calendar account.
     *
     * @param session The current user session
     * @param id The identifier of the account to delete
     * @param clientTimestamp The last timestamp known by the client to catch concurrent updates
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     * @throws OXException if permission check fails
     */
    void deleteAccount(Session session, int id, long clientTimestamp, CalendarParameters parameters) throws OXException;

    /**
     * Gets an existing calendar account.
     *
     * @param session The current user session
     * @param id The identifier of the account to load
     * @return The loaded calendar account
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     */
    CalendarAccount getAccount(Session session, int id, CalendarParameters parameters) throws OXException;

    /**
     * Gets multiple existing calendar accounts.
     *
     * @param session The current user session
     * @param ids The identifiers of the account to load
     * @return The loaded calendar accounts
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     */
    List<CalendarAccount> getAccounts(Session session, int[] ids, CalendarParameters parameters) throws OXException;

    /**
     * Gets a list of all calendar accounts stored for a specific user.
     *
     * @param session The current user session
     * @return The accounts, or an empty list if none were found
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     * @throws OXException if permission check fails
     */
    List<CalendarAccount> getAccounts(Session session, CalendarParameters parameters) throws OXException;

    /**
     * Gets a list of all calendar accounts stored for s specific user and provider.
     *
     * @param session The current user session
     * @param providerId The providerId to search with
     * @return The accounts, or an empty list if none were found
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     * @throws OXException if permission check fails
     */
    List<CalendarAccount> getAccounts(Session session, String providerId, CalendarParameters parameters) throws OXException;

}
