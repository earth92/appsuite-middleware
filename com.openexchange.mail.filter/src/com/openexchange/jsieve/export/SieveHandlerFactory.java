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

package com.openexchange.jsieve.export;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import javax.mail.internet.idn.IDNA;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailfilter.Credentials;
import com.openexchange.mailfilter.exceptions.MailFilterExceptionCode;
import com.openexchange.mailfilter.internal.CircuitBreakerInfo;
import com.openexchange.mailfilter.properties.CredentialSource;
import com.openexchange.mailfilter.properties.LoginType;
import com.openexchange.mailfilter.properties.MailFilterProperty;
import com.openexchange.mailfilter.properties.PasswordSource;
import com.openexchange.mailfilter.services.Services;
import com.openexchange.tools.net.URIDefaults;
import com.openexchange.tools.net.URIParser;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link SieveHandlerFactory}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public final class SieveHandlerFactory {

    /**
     * Connect to the Sieve server and return a handler
     *
     * @param creds The credentials
     * @param optionalCircuitBreaker The optional breaker for mail filter access
     * @return A sieve handler
     * @throws OXException
     */
    public static SieveHandler getSieveHandler(Credentials creds, Optional<CircuitBreakerInfo> optionalCircuitBreaker) throws OXException {
        return getSieveHandler(creds, optionalCircuitBreaker, false);
    }

    /**
     * Connect to the Sieve server and return a handler
     *
     * @param creds The credentials
     * @param optionalCircuitBreaker The optional breaker for mail filter access
     * @param onlyWelcome <code>true</code> if only server's welcome message is of interest; otherwise <code>false</code> for full login round-trip
     * @return A sieve handler
     * @throws OXException
     */
    public static SieveHandler getSieveHandler(Credentials creds, Optional<CircuitBreakerInfo> optionalCircuitBreaker, boolean onlyWelcome) throws OXException {
        LeanConfigurationService mailFilterConfig = Services.getService(LeanConfigurationService.class);

        int userId = creds.getUserid();
        int contextId = creds.getContextid();

        // Determine & parse host and port
        int sievePort;
        String sieveServer;
        User user = null;
        MailAccount mailAccount = null;
        {
            String sLoginType = mailFilterConfig.getProperty(userId, contextId, MailFilterProperty.loginType);
            LoginType loginType = LoginType.loginTypeFor(sLoginType);

            switch (loginType) {
                case GLOBAL:
                    sieveServer = mailFilterConfig.getProperty(userId, contextId, MailFilterProperty.server);
                    if (null == sieveServer) {
                        throw MailFilterExceptionCode.PROPERTY_ERROR.create(MailFilterProperty.server.getFQPropertyName());
                    }
                    sievePort = getPort(mailFilterConfig, userId, contextId);
                    break;
                case USER:
                    user = getUser(creds);
                    if (user.isGuest()) {
                        throw MailFilterExceptionCode.INVALID_USER_SPECIFIED.create();
                    }
                    mailAccount = getMailAccount(userId, contextId);
                    String mailServerURL = mailAccount.getMailServer();
                    try {
                        URI uri = URIParser.parse(IDNA.toASCII(mailServerURL), URIDefaults.IMAP);
                        if (null == uri) {
                            throw MailFilterExceptionCode.UNABLE_TO_EXTRACT_SIEVE_SERVER_URI.create();
                        }
                        sieveServer = uri.getHost();
                    } catch (URISyntaxException e) {
                        throw MailFilterExceptionCode.NO_SERVERNAME_IN_SERVERURL.create(e, mailServerURL);
                    }
                    sievePort = getPort(mailFilterConfig, userId, contextId);

                    break;
                default:
                    throw MailFilterExceptionCode.NO_VALID_LOGIN_TYPE.create();
            }
        }

        if (onlyWelcome) {
            // Host name and port are sufficient...
            return new SieveHandler(sieveServer, sievePort);
        }

        // Get the 'authenticationEncoding' property
        String authEnc = mailFilterConfig.getProperty(userId, contextId, MailFilterProperty.authenticationEncoding);

        // Determine & parse login and password dependent on configured credentials source
        String sCredSrc = mailFilterConfig.getProperty(userId, contextId, MailFilterProperty.credentialSource);
        CredentialSource credentialSource = CredentialSource.credentialSourceFor(sCredSrc);
        switch (credentialSource) {
            case IMAP_LOGIN: {
                String authname = getMailAccount(userId, contextId, mailAccount).getLogin();
                return newSieveHandlerUsing(sieveServer, sievePort, creds.getUsername(), authname, getRightPassword(mailFilterConfig, creds), authEnc, creds.getOauthToken(), optionalCircuitBreaker, userId, contextId);
            }
            case MAIL: {
                String authname = getUser(creds, user).getMail();
                return newSieveHandlerUsing(sieveServer, sievePort, creds.getUsername(), authname, getRightPassword(mailFilterConfig, creds), authEnc, creds.getOauthToken(), optionalCircuitBreaker, userId, contextId);
            }
            case SESSION:
                // fall-through
            case SESSION_FULL_LOGIN:
                return newSieveHandlerUsing(sieveServer, sievePort, creds.getUsername(), creds.getAuthname(), getRightPassword(mailFilterConfig, creds), authEnc, creds.getOauthToken(), optionalCircuitBreaker, userId, contextId);
            default:
                throw MailFilterExceptionCode.NO_VALID_CREDSRC.create();
        }
    }

    /**
     * Creates an new {@link SieveHandler} with the specified properties
     *
     * @param host The host
     * @param port The port
     * @param userName The user name
     * @param authName The authentication name
     * @param password The password
     * @param authEncoding The authentication encoding
     * @param oauthToken The OAuth token
     * @param optionalCircuitBreaker The optional breaker for mail filter access
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The {@link SieveHandler}
     */
    private static SieveHandler newSieveHandlerUsing(String host, int port, String userName, String authName, String password, String authEncoding, String oauthToken, Optional<CircuitBreakerInfo> optionalCircuitBreaker, int userId, int contextId) {
        return new SieveHandler(null == userName ? authName : userName, authName, password, host, port, authEncoding, oauthToken, optionalCircuitBreaker, userId, contextId);
    }

    /**
     * Get the user
     *
     * @param credentials The {@link Credentials}
     * @return the user
     * @throws OXException
     */
    private static User getUser(Credentials credentials) throws OXException {
        return getUser(credentials, null);
    }

    /**
     * Get the user
     *
     * @param creds The {@link Credentials}
     * @param user The optional {@link User}
     * @return the user
     * @throws OXException
     */
    private static User getUser(Credentials creds, User user) throws OXException {
        if (null != user) {
            return user;
        }

        User storageUser = Services.requireService(UserService.class).getUser(creds.getUserid(), creds.getContextid());
        if (null == storageUser) {
            throw OXException.general("Could not get a valid user object for uid " + creds.getUserid() + " and contextid " + creds.getContextid());
        }
        return storageUser;
    }

    /**
     * Get the primary mail account for given user
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The mail account
     * @throws OXException
     */
    private static MailAccount getMailAccount(int userId, int contextId) throws OXException {
        return getMailAccount(userId, contextId, null);
    }

    /**
     * Get the primary mail account for given user
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param mailAccount The optional mail account
     * @return The mail account
     * @throws OXException
     */
    private static MailAccount getMailAccount(int userId, int contextId, MailAccount mailAccount) throws OXException {
        if (null != mailAccount) {
            return mailAccount;
        }

        return Services.requireService(MailAccountStorageService.class).getDefaultMailAccount(userId, contextId);
    }

    /**
     * Get the port from the configuration service
     *
     * @param mailFilterConfig The {@link MailFilterConfigurationService}
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The sieve port
     * @throws OXException if an error is occurred
     */
    private static int getPort(LeanConfigurationService mailFilterConfig, int userId, int contextId) throws OXException {
        try {
            return mailFilterConfig.getIntProperty(userId, contextId, MailFilterProperty.port);
        } catch (RuntimeException e) {
            throw MailFilterExceptionCode.PROPERTY_ERROR.create(e, MailFilterProperty.port.getFQPropertyName());
        }
    }

    /**
     * Get the correct password according to the credentials
     *
     * @param config
     * @param creds
     * @return
     * @throws OXException
     */
    public static String getRightPassword(final LeanConfigurationService config, final Credentials creds) throws OXException {
        int userId = creds.getUserid();
        int contextId = creds.getContextid();
        String sPasswordsrc = config.getProperty(userId, contextId, MailFilterProperty.passwordSource);
        PasswordSource passwordSource = PasswordSource.passwordSourceFor(sPasswordsrc);

        switch (passwordSource) {
            case GLOBAL: {
                String masterpassword = config.getProperty(userId, contextId, MailFilterProperty.masterPassword);
                if (null == masterpassword || masterpassword.length() == 0) {
                    throw MailFilterExceptionCode.NO_MASTERPASSWORD_SET.create();
                }
                return masterpassword;
            }
            case SESSION:
                return creds.getPassword();
            default:
                throw MailFilterExceptionCode.NO_VALID_PASSWORDSOURCE.create();
        }
    }
}
