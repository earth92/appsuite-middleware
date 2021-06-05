
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

package com.openexchange.google.api.client;

import static com.openexchange.java.Autoboxing.I;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;
import org.json.JSONException;
import org.json.JSONObject;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.Google2Api;
import org.scribe.exceptions.OAuthException;
import org.scribe.model.Token;
import org.slf4j.Logger;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.openexchange.cluster.lock.ClusterLockService;
import com.openexchange.cluster.lock.ClusterTask;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.exception.ExceptionUtils;
import com.openexchange.exception.OXException;
import com.openexchange.google.api.client.services.Services;
import com.openexchange.java.Strings;
import com.openexchange.net.ssl.exception.SSLExceptionCode;
import com.openexchange.oauth.AbstractReauthorizeClusterTask;
import com.openexchange.oauth.KnownApi;
import com.openexchange.oauth.OAuthAccount;
import com.openexchange.oauth.OAuthExceptionCodes;
import com.openexchange.oauth.OAuthService;
import com.openexchange.oauth.OAuthUtil;
import com.openexchange.policy.retry.ExponentialBackOffRetryPolicy;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.session.Session;

/**
 * {@link GoogleApiClients} - Utility class for Google API client.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since 7.6.1
 */
public class GoogleApiClients {

    /** The refresh threshold in seconds: <code>60</code> */
    public static final int REFRESH_THRESHOLD = 60;

    /**
     * Initializes a new {@link GoogleApiClients}.
     */
    private GoogleApiClients() {
        super();
    }

    /** Global instance of the JSON factory. */
    public static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Gets the default Google OAuth account.
     * <p>
     * Validates expiry of current access token and requests a new one if less than 5 minutes to live
     *
     * @param session The session
     * @return The default Google OAuth account
     * @throws OXException If default Google OAuth account cannot be returned
     */
    public static OAuthAccount getDefaultGoogleAccount(final Session session) throws OXException {
        return getDefaultGoogleAccount(session, true);
    }

    /**
     * Gets the default Google OAuth account.
     * <p>
     * Optionally validates expiry of current access token and requests a new one if less than 5 minutes to live
     *
     * @param session The session
     * @param reacquireIfExpired <code>true</code> to re-acquire a new access token, if existing one is about to expire; otherwise <code>false</code>
     * @return The default Google OAuth account
     * @throws OXException If default Google OAuth account cannot be returned
     */
    public static OAuthAccount getDefaultGoogleAccount(final Session session, final boolean reacquireIfExpired) throws OXException {
        final OAuthService oAuthService = Services.optService(OAuthService.class);
        if (null == oAuthService) {
            throw ServiceExceptionCode.absentService(OAuthService.class);
        }

        // Get default Google account
        OAuthAccount defaultAccount = oAuthService.getDefaultAccount(KnownApi.GOOGLE, session);
        defaultAccount = reacquireIfExpired(session, reacquireIfExpired, defaultAccount);
        return defaultAccount;
    }

    /**
     * Check if the OAuth token of the specified {@link OAuthAccount} was expired and re-acquire if necessary
     *
     * @param session The session
     * @param reacquireIfExpired Whether to perform the requisition check
     * @param oauthAccount The OAuth account
     * @return The {@link OAuthAccount} with the (renewed) token
     * @throws OXException If an error is occurred
     */
    public static OAuthAccount reacquireIfExpired(final Session session, final boolean reacquireIfExpired, OAuthAccount oauthAccount) throws OXException {
        if (!reacquireIfExpired) {
            return oauthAccount;
        }
        OAuthAccount account = oauthAccount;
        try {
            // Create Scribe Google OAuth service
            final ServiceBuilder serviceBuilder = new ServiceBuilder().provider(Google2Api.class);
            serviceBuilder.apiKey(account.getMetaData().getAPIKey(session)).apiSecret(account.getMetaData().getAPISecret(session));
            Google2Api.GoogleOAuth2Service scribeOAuthService = (Google2Api.GoogleOAuth2Service) serviceBuilder.build();

            // Check expiry
            int expiry = scribeOAuthService.getExpiry(account.getToken());
            if (expiry < REFRESH_THRESHOLD) {
                // Less than 1 minute to live -> refresh token!
                ClusterLockService clusterLockService = Services.getService(ClusterLockService.class);
                account = clusterLockService.runClusterTask(new GoogleReauthorizeClusterTask(session, account), new ExponentialBackOffRetryPolicy());
            }
        } catch (org.scribe.exceptions.OAuthException e) {
            throw OAuthUtil.handleScribeOAuthException(e, account, session);
        }
        return account;
    }

    /**
     * Gets the denoted Google OAuth account.
     * <p>
     * Validates expiry of current access token and requests a new one if less than 5 minutes to live
     *
     * @param accountId The account identifier
     * @param session The session
     * @return The Google OAuth account
     * @throws OXException If default Google OAuth account cannot be returned
     */
    public static OAuthAccount getGoogleAccount(int accountId, Session session) throws OXException {
        return getGoogleAccount(accountId, session, true);
    }

    /**
     * Gets the denoted Google OAuth account.
     * <p>
     * Optionally validates expiry of current access token and requests a new one if less than 5 minutes to live
     *
     * @param accountId The account identifier
     * @param session The session
     * @param reacquireIfExpired <code>true</code> to re-acquire a new access token, if existing one is about to expire; otherwise <code>false</code>
     * @return The Google OAuth account
     * @throws OXException If default Google OAuth account cannot be returned
     */
    public static OAuthAccount getGoogleAccount(int accountId, Session session, final boolean reacquireIfExpired) throws OXException {
        final OAuthService oAuthService = Services.optService(OAuthService.class);
        if (null == oAuthService) {
            throw ServiceExceptionCode.absentService(OAuthService.class);
        }

        // Get default Google account
        OAuthAccount googleAccount = oAuthService.getAccount(session, accountId);

        googleAccount = reacquireIfExpired(session, reacquireIfExpired, googleAccount);

        return googleAccount;
    }

    /**
     * Gets the expiry (in seconds) for given Google OAuth account
     *
     * @param googleAccount The Google OAuth account
     * @param session The associated session
     * @return The expiry in seconds
     * @throws OXException If expiry cannot be returned
     */
    public static long getGoogleAccountExpiry(OAuthAccount googleAccount, Session session) throws OXException {
        if (null == googleAccount) {
            return -1L;
        }

        try {
            // Create Scribe Google OAuth service
            final ServiceBuilder serviceBuilder = new ServiceBuilder().provider(Google2Api.class);
            serviceBuilder.apiKey(googleAccount.getMetaData().getAPIKey(session)).apiSecret(googleAccount.getMetaData().getAPISecret(session));
            Google2Api.GoogleOAuth2Service scribeOAuthService = (Google2Api.GoogleOAuth2Service) serviceBuilder.build();

            // Check expiry
            return scribeOAuthService.getExpiry(googleAccount.getToken());
        } catch (org.scribe.exceptions.OAuthException e) {
            throw OAuthUtil.handleScribeOAuthException(e, googleAccount, session);
        }
    }

    /**
     * Gets a non-expired candidate for given Google OAuth account
     *
     * @param googleAccount The Google OAuth account to check
     * @param session The associated session
     * @return The non-expired candidate or <code>null</code> if given account appears to have enough time left
     * @throws OXException If a non-expired candidate cannot be returned
     */
    public static OAuthAccount ensureNonExpiredGoogleAccount(final OAuthAccount googleAccount, final Session session) throws OXException {
        if (null == googleAccount) {
            return googleAccount;
        }

        // Get OAuth service
        final OAuthService oAuthService = Services.optService(OAuthService.class);
        if (null == oAuthService) {
            throw ServiceExceptionCode.absentService(OAuthService.class);
        }

        try {
            // Create Scribe Google OAuth service
            final ServiceBuilder serviceBuilder = new ServiceBuilder().provider(Google2Api.class);
            serviceBuilder.apiKey(googleAccount.getMetaData().getAPIKey(session)).apiSecret(googleAccount.getMetaData().getAPISecret(session));
            final Google2Api.GoogleOAuth2Service scribeOAuthService = (Google2Api.GoogleOAuth2Service) serviceBuilder.build();

            // Check expiry
            int expiry = scribeOAuthService.getExpiry(googleAccount.getToken());
            if (expiry >= REFRESH_THRESHOLD) {
                // More than 1 minute to live
                return null;
            }

            ClusterLockService clusterLockService = Services.getService(ClusterLockService.class);
            return clusterLockService.runClusterTask(new GoogleReauthorizeClusterTask(session, googleAccount), new ExponentialBackOffRetryPolicy());
        } catch (org.scribe.exceptions.OAuthException e) {
            throw OAuthUtil.handleScribeOAuthException(e, googleAccount, session);
        }
    }

    /**
     * Gets the expiry (in seconds) for given Google OAuth account
     *
     * @param googleAccount The Google OAuth account to check
     * @param session The associated session
     * @return The expiry in seconds or <code>-1</code> if access token is already expired
     * @throws OXException If expiry cannot be returned
     * @throws IllegalArgumentException If provided account is <code>null</code>
     */
    public static int getExpiryForGoogleAccount(final OAuthAccount googleAccount, final Session session) throws OXException {
        if (null == googleAccount) {
            throw new IllegalArgumentException("Account must not be null");
        }

        // Get OAuth service
        final OAuthService oAuthService = Services.optService(OAuthService.class);
        if (null == oAuthService) {
            throw ServiceExceptionCode.absentService(OAuthService.class);
        }

        try {
            // Create Scribe Google OAuth service
            final ServiceBuilder serviceBuilder = new ServiceBuilder().provider(Google2Api.class);
            serviceBuilder.apiKey(googleAccount.getMetaData().getAPIKey(session)).apiSecret(googleAccount.getMetaData().getAPISecret(session));
            final Google2Api.GoogleOAuth2Service scribeOAuthService = (Google2Api.GoogleOAuth2Service) serviceBuilder.build();

            // Check expiry
            return scribeOAuthService.getExpiry(googleAccount.getToken());
        } catch (org.scribe.exceptions.OAuthException e) {
            throw OAuthUtil.handleScribeOAuthException(e, googleAccount, session);
        }
    }

    /**
     * Handles the specified {@link OAuthException} for the specified {@link OAuthAccount} and the
     * specified {@link Session} and returns an appropriate {@link OXException}.
     *
     * @param e The exception to handle
     * @param googleAccount the {@link OAuthAccount}
     * @param session The groupware session
     * @return The appropriate OXException
     */
    static OXException handleScribeOAuthException(OAuthException e, OAuthAccount googleAccount, Session session) {
        if (ExceptionUtils.isEitherOf(e, SSLHandshakeException.class)) {
            List<Object> displayArgs = new ArrayList<>(2);
            displayArgs.add(SSLExceptionCode.extractArgument(e, "fingerprint"));
            displayArgs.add("www.googleapis.com");
            return SSLExceptionCode.UNTRUSTED_CERTIFICATE.create(e, displayArgs.toArray(new Object[] {}));
        }

        String exMessage = e.getMessage();
        String errorMsg = parseKeyFrom(exMessage, "error");
        if (Strings.isEmpty(errorMsg)) {
            return OAuthExceptionCodes.OAUTH_ERROR.create(e, exMessage);
        }
        if (exMessage.contains("invalid_grant") || exMessage.contains("deleted_client")) {
            if (null != googleAccount) {
                return OAuthExceptionCodes.OAUTH_ACCESS_TOKEN_INVALID.create(e, googleAccount.getDisplayName(), I(googleAccount.getId()), I(session.getUserId()), I(session.getContextId()));
            }
            return OAuthExceptionCodes.INVALID_ACCOUNT.create(e, new Object[0]);
        }

        String errorDescription = parseKeyFrom(exMessage, "error_description");
        if (Strings.isEmpty(errorDescription)) {
            return OAuthExceptionCodes.OAUTH_ERROR.create(e, exMessage);
        }
        if (errorDescription.contains("Missing required parameter: refresh_token")) {
             return OAuthExceptionCodes.INVALID_ACCOUNT_EXTENDED.create(googleAccount.getDisplayName(), I(googleAccount.getId()));
        }
        return OAuthExceptionCodes.OAUTH_ERROR.create(e, exMessage);
    }

    /**
     * Parses the specified key from from the specified message
     *
     * @param message The message from which to parse the error code
     * @return The error code, or <code>null</code> if none can be parsed
     */
    private static String parseKeyFrom(String message, String key) {
        if (Strings.isEmpty(message)) {
            return null;
        }

        String marker = "Can't extract a token from this: '";
        int pos = message.indexOf(marker);
        if (pos < 0) {
            return null;
        }

        try {
            JSONObject jo = new JSONObject(message.substring(pos + marker.length(), message.length() - 1));
            return jo.optString(key, null);
        } catch (JSONException e) {
            // Apparent no JSON response
            return null;
        }
    }

    /**
     * Gets the Google credentials from <b>default</b> OAuth account.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * Please use {@link #getCredentials(OAuthAccount, Session)} if a concrete Google OAuth account is supposed to be used
     * </div>
     * <p>
     *
     * @param session The associated session
     * @return The Google credentials from default OAuth account
     * @throws OXException If Google credentials cannot be returned
     */
    public static GoogleCredential getCredentials(Session session) throws OXException {
        OAuthAccount defaultAccount = getDefaultGoogleAccount(session);
        return getCredentials(defaultAccount, session);
    }

    /**
     * Gets the Google credentials from default OAuth account.
     *
     * @param googleOAuthAccount The Google OAuth account
     * @param session The associated session
     * @return The Google credentials from given OAuth account or <code>null</code> if either of arguments is <code>null</code>
     * @throws OXException If Google credentials cannot be returned
     */
    public static GoogleCredential getCredentials(OAuthAccount googleOAuthAccount, Session session) throws OXException {
        if (null == googleOAuthAccount) {
            return null;
        }
        if (null == session) {
            return null;
        }
        OAuthAccount googleAccount = googleOAuthAccount;
        try {
            // Initialize transport
            NetHttpTransport transport = new NetHttpTransport.Builder().doNotValidateCertificate().build();

            googleAccount = reacquireIfExpired(session, true, googleAccount);

            // Build credentials
            return new GoogleCredential.Builder().setClientSecrets(googleAccount.getMetaData().getAPIKey(session), googleAccount.getMetaData().getAPISecret(session)).setJsonFactory(JSON_FACTORY).setTransport(transport).build().setRefreshToken(googleAccount.getSecret()).setAccessToken(googleAccount.getToken());
        } catch (GeneralSecurityException e) {
            throw OAuthExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Gets the product name associated with registered Google application
     *
     * @return The product name
     */
    public static String getGoogleProductName(Session session) {
        if (null != session) {
            ConfigViewFactory factory = Services.getService(ConfigViewFactory.class);
            if (null != factory) {
                try {
                    ConfigView view = factory.getView(session.getUserId(), session.getContextId());
                    return view.opt("com.openexchange.oauth.google.productName", String.class, "");
                } catch (OXException e) {
                    Logger logger = org.slf4j.LoggerFactory.getLogger(GoogleApiClients.class);
                    logger.warn("Failed to look-up property \"com.openexchange.oauth.google.productName\" using config-cascade. Falling back to configuration service...", e);
                }
            }
        }

        ConfigurationService configService = Services.getService(ConfigurationService.class);
        return null == configService ? "" : configService.getProperty("com.openexchange.oauth.google.productName", "");
    }

    //////////////////////// HELPERS /////////////////////////////

    /**
     * {@link GoogleReauthorizeClusterTask}
     */
    private static class GoogleReauthorizeClusterTask extends AbstractReauthorizeClusterTask implements ClusterTask<OAuthAccount> {

        /**
         * Initialises a new {@link GoogleApiClients.GoogleReauthorizeClusterTask}.
         */
        public GoogleReauthorizeClusterTask(Session session, OAuthAccount cachedAccount) {
            super(Services.getServiceLookup(), session, cachedAccount);
        }

        @Override
        public Token reauthorize() throws OXException {
            final ServiceBuilder serviceBuilder = new ServiceBuilder().provider(Google2Api.class);
            serviceBuilder.apiKey(getCachedAccount().getMetaData().getAPIKey(getSession())).apiSecret(getCachedAccount().getMetaData().getAPISecret(getSession()));
            Google2Api.GoogleOAuth2Service scribeOAuthService = (Google2Api.GoogleOAuth2Service) serviceBuilder.build();

            // Refresh the token
            try {
                return scribeOAuthService.getAccessToken(new Token(getCachedAccount().getToken(), getCachedAccount().getSecret()), null);
            } catch (OAuthException e) {
                OAuthAccount dbAccount = getDBAccount();
                throw OAuthUtil.handleScribeOAuthException(e, dbAccount, getSession());
            }
        }
    }
}
