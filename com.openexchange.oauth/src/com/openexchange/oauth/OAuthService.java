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

package com.openexchange.oauth;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.exception.OXException;
import com.openexchange.oauth.scope.OAuthScope;
import com.openexchange.osgi.annotation.SingletonService;
import com.openexchange.session.Session;

/**
 * {@link OAuthService} - The OAuth service.
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 * @author <a href="mailto:karsten.will@open-xchange.com">Karsten Will</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
@SingletonService
public interface OAuthService {

    /**
     * Gets the meta data registry.
     *
     * @return The meta data registry
     */
    OAuthServiceMetaDataRegistry getMetaDataRegistry();

    /**
     * Gets all accounts belonging to specified user.
     *
     * @param session The session
     * @throws OXException If accounts cannot be returned
     * @return The accounts
     */
    List<OAuthAccount> getAccounts(Session session) throws OXException;

    /**
     * Gets all accounts belonging to specified user with given service identifier.
     * 
     * @param session The session
     * @param serviceMetaData The identifier of service meta data
     *
     * @throws OXException If accounts cannot be returned
     * @return The accounts
     */
    List<OAuthAccount> getAccounts(Session session, String serviceMetaData) throws OXException;

    /**
     * Initialises a new OAuth account.
     * 
     * @param session The session
     * @param serviceMetaData The identifier of service meta data
     * @param callbackUrl The optional call-back URL
     * @param currentHost The name of this host
     * @param a {@link Set} with {@link OAuthScope}s to enable for the {@link OAuthAccount}
     *
     * @throws OXException If initialisation fails
     * @return The OAuth interaction providing needed steps
     */
    OAuthInteraction initOAuth(Session session, String serviceMetaData, String callbackUrl, HostInfo currentHost, Set<OAuthScope> scopes) throws OXException;

    /**
     * Upserts an OAuth account. Obtains the {@link OAuthToken} from the provider via the requested {@link OAuthInteractionType}
     * and the user identity for the logged in user and either creates a new {@link OAuthAccount} or updates an already
     * existing one. The decision is made based on whether an account with the same user identity already exists for the
     * specified user.
     * 
     * @param session The session
     * @param serviceId the service provider id
     * @param accountId The optional account identifier
     * @param type the {@link OAuthInteractionType}
     * @param arguments The arguments appropriate for interaction type
     * @param scopes The requested scopes
     * 
     * @return The newly created or updated {@link OAuthAccount}
     * @throws OXException if any error is occurred
     */
    OAuthAccount upsertAccount(Session session, String serviceId, int accountId, OAuthInteractionType type, Map<String, Object> arguments, Set<OAuthScope> scopes) throws OXException;

    /**
     * Creates a new OAuth account completely from specified arguments.
     * 
     * @param session The session
     * @param serviceMetaData The identifier of service meta data
     * @param arguments The arguments providing {@link OAuthConstants#ARGUMENT_TOKEN}, {@link OAuthConstants#ARGUMENT_SECRET}, {@link OAuthConstants#ARGUMENT_PASSWORD}, and optional {@link OAuthConstants#ARGUMENT_DISPLAY_NAME}
     * @param a {@link Set} with {@link OAuthScope}s to enable for the {@link OAuthAccount}
     *
     * @return The newly created account
     * @throws OXException If creation fails
     */
    OAuthAccount createAccount(Session session, String serviceMetaData, Set<OAuthScope> scopes, Map<String, Object> arguments) throws OXException;

    /**
     * Create a new OAuth account from specified arguments.
     * <p>
     * The arguments should provide:
     * <ul>
     * <li>display name; {@link OAuthConstants#ARGUMENT_DISPLAY_NAME}</li>
     * <li>pin; {@link OAuthConstants#ARGUMENT_PIN}</li>
     * <li>request token; {@link OAuthConstants#ARGUMENT_REQUEST_TOKEN}</li>
     * <li>user password; {@link OAuthConstants#ARGUMENT_PASSWORD}</li>
     * </ul>
     * 
     * @param session The session
     * @param serviceMetaData The identifier of service meta data
     * @param type The interaction type
     * @param arguments The arguments appropriate for interaction type
     * @param a {@link Set} with {@link OAuthScope}s to enable for the {@link OAuthAccount}
     *
     * @throws OXException If creation fails
     * @return The newly created account
     */
    OAuthAccount createAccount(Session session, String serviceMetaData, Set<OAuthScope> scopes, OAuthInteractionType type, Map<String, Object> arguments) throws OXException;

    /**
     * Deletes the specified account.
     * 
     * @param session The session
     * @param accountId The account identifier
     *
     * @throws OXException If deletion fails
     */
    void deleteAccount(Session session, int accountId) throws OXException;

    /**
     * Update the specified account.
     * <p>
     * The arguments may provide:
     * <ul>
     * <li>display name; {@link OAuthConstants#ARGUMENT_DISPLAY_NAME}</li>
     * <li>request token; {@link OAuthConstants#ARGUMENT_REQUEST_TOKEN}</li>
     * <li>enabled scopes; {@link OAuthConstants#ARGUMENT_SCOPES}</li>
     * <li>user password is <b>mandatory</b> if request token shall be updated; {@link OAuthConstants#ARGUMENT_PASSWORD}</li>
     * </ul>
     * 
     * @param session The session
     * @param accountId The account identifier
     * @param arguments The arguments to update
     * @param a {@link Set} with {@link OAuthScope}s to enable for the {@link OAuthAccount}
     *
     * @throws OXException If update fails
     */
    void updateAccount(Session session, int accountId, Map<String, Object> arguments) throws OXException;

    /**
     * Updates the specified account
     * 
     * @param session The session
     * @param accountId
     * @param serviceMetaData
     * @param type
     * @param arguments
     * @param a {@link Set} with {@link OAuthScope}s to enable for the {@link OAuthAccount}
     *
     * @return the updated {@link OAuthAccount}
     * @throws OXException
     */
    OAuthAccount updateAccount(Session session, int accountId, String serviceMetaData, OAuthInteractionType type, Map<String, Object> arguments, Set<OAuthScope> scopes) throws OXException;

    /**
     * Gets the specified account.
     * 
     * @param session The session
     * @param accountId The account identifier
     *
     * @throws OXException If account cannot be returned
     * @return The account
     */
    OAuthAccount getAccount(Session session, int accountId) throws OXException;

    /**
     * Gets a default account for the given API type. Throws an exception if no account for the API can be found.
     *
     * @param api The API type
     * @param session The session
     * @return The default account for this API type
     * @throws OXException if no account for the API can be found.
     */
    OAuthAccount getDefaultAccount(API api, Session session) throws OXException;
}
