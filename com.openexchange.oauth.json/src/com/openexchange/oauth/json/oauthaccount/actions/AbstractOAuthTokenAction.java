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

package com.openexchange.oauth.json.oauthaccount.actions;

import static com.openexchange.java.Strings.isEmpty;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_ACCESS_DENIED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_ADDITIONAL_AUTHORIZATION_REQUIRED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_CONSUMER_KEY_REFUSED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_CONSUMER_KEY_REJECTED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_CONSUMER_KEY_UNKNOWN;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_NONCE_USED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_PARAMETER_ABSENT;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_PARAMETER_REJECTED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_PERMISSION_DENIED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_PERMISSION_UNKNOWN;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_SIGNATURE_INVALID;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_SIGNATURE_METHOD_REJECTED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_TIMESTAMP_REFUSED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_TOKEN_EXPIRED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_TOKEN_REJECTED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_TOKEN_REVOKED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_TOKEN_USED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_USER_REFUSED;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_VERIFIER_INVALID;
import static com.openexchange.oauth.OAuthConstants.OAUTH_PROBLEM_VERSION_REJECTED;
import static com.openexchange.oauth.OAuthConstants.URLPARAM_OAUTH_ACCEPTABLE_TIMESTAMPS;
import static com.openexchange.oauth.OAuthConstants.URLPARAM_OAUTH_PARAMETERS_ABSENT;
import static com.openexchange.oauth.OAuthConstants.URLPARAM_OAUTH_PARAMETERS_REJECTED;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.cluster.lock.ClusterTask;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.oauth.API;
import com.openexchange.oauth.DefaultOAuthToken;
import com.openexchange.oauth.OAuthAPIRegistry;
import com.openexchange.oauth.OAuthAccount;
import com.openexchange.oauth.OAuthConstants;
import com.openexchange.oauth.OAuthExceptionCodes;
import com.openexchange.oauth.OAuthInteractionType;
import com.openexchange.oauth.OAuthService;
import com.openexchange.oauth.OAuthServiceMetaData;
import com.openexchange.oauth.access.OAuthAccess;
import com.openexchange.oauth.access.OAuthAccessRegistry;
import com.openexchange.oauth.access.OAuthAccessRegistryService;
import com.openexchange.oauth.json.AbstractOAuthAJAXActionService;
import com.openexchange.oauth.json.Services;
import com.openexchange.oauth.json.oauthaccount.AccountField;
import com.openexchange.oauth.scope.OAuthScope;
import com.openexchange.oauth.scope.OAuthScopeRegistry;
import com.openexchange.oauth.scope.OXScope;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link AbstractOAuthTokenAction}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public abstract class AbstractOAuthTokenAction extends AbstractOAuthAJAXActionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractOAuthTokenAction.class);

    /**
     * Initializes a new {@link AbstractOAuthTokenAction}.
     */
    public AbstractOAuthTokenAction() {
        super();
    }

    protected Map<String, Object> processOAuthArguments(final AJAXRequestData request, final ServerSession session, final OAuthServiceMetaData service) throws OXException {
        /*
         * Parse OAuth parameters
         */
        // http://wiki.oauth.net/w/page/12238555/Signed-Callback-URLs
        // http://developer.linkedin.com/message/4568

        /*
         * Check for reported oauth problems
         */
        {
            String oauth_problem = request.getParameter(OAuthConstants.URLPARAM_OAUTH_PROBLEM);
            if (Strings.isNotEmpty(oauth_problem)) {
                throw fromOauthProblem(oauth_problem, request, service);
            }
            oauth_problem = request.getParameter(OAuthConstants.URLPARAM_ERROR);
            if (Strings.isNotEmpty(oauth_problem)) {
                throw fromOauthProblem(oauth_problem, request, service);
            }
        }

        String oauthToken = request.getParameter(OAuthConstants.URLPARAM_OAUTH_TOKEN);
        if (oauthToken == null) {
            oauthToken = request.getParameter("access_token");
        }
        if (oauthToken != null) {
            oauthToken = stripExpireParam(oauthToken);
        }
        final String uuid = request.getParameter(OAuthConstants.SESSION_PARAM_UUID);
        if (uuid == null) {
            throw AjaxExceptionCodes.MISSING_PARAMETER.create(OAuthConstants.SESSION_PARAM_UUID);
        }

        /*
         * Get request token secret from session parameters
         */
        @SuppressWarnings("unchecked") final Map<String, Object> state = (Map<String, Object>) session.getParameter(uuid); //request.getParameter("oauth_token_secret");
        if (null == state) {
            throw OAuthExceptionCodes.CANCELED_BY_USER.create();
        }
        String oauthTokenSecret = (String) state.get(OAuthConstants.ARGUMENT_SECRET);
        if (oauthTokenSecret != null) {
            oauthTokenSecret = stripExpireParam(oauthTokenSecret);
        }
        session.setParameter(uuid, null);
        /*
         * The OAuth verifier (PIN)
         */
        final String oauthVerfifier = request.getParameter(OAuthConstants.URLPARAM_OAUTH_VERIFIER);
        /*
         * Invoke
         */
        final Map<String, Object> arguments = new HashMap<>(3);
        {
            final String displayName = request.getParameter(AccountField.DISPLAY_NAME.getName());
            if (Strings.isEmpty(displayName)) {
                throw AjaxExceptionCodes.MISSING_PARAMETER.create(AccountField.DISPLAY_NAME.getName());
            }
            arguments.put(OAuthConstants.ARGUMENT_DISPLAY_NAME, displayName);
        }
        arguments.put(OAuthConstants.ARGUMENT_PIN, oauthVerfifier);
        arguments.put(OAuthConstants.ARGUMENT_SESSION, session);
        final DefaultOAuthToken token = new DefaultOAuthToken();
        token.setSecret(oauthTokenSecret);
        token.setToken(oauthToken);
        arguments.put(OAuthConstants.ARGUMENT_REQUEST_TOKEN, token);
        final String actionHint = request.getParameter(OAuthConstants.URLPARAM_ACTION_HINT);
        if (Strings.isNotEmpty(actionHint)) {
            arguments.put(OAuthConstants.ARGUMENT_ACTION_HINT, actionHint);
        }
        /*
         * Process arguments
         */
        service.processArguments(arguments, request.getParameters(), state);
        return arguments;
    }

    private static final Pattern P_EXPIRES = Pattern.compile("&expires(=[0-9]+)?$");

    /**
     * @param requestData
     * @return
     */
    int getAccountId(AJAXRequestData requestData) {
        String id = requestData.getParameter(AccountField.ID.getName());
        if (Strings.isEmpty(id)) {
            return -1;
        }
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            LOGGER.debug("Cannot parse account id from {}", id, e);
            return -1;
        }
    }

    /*
     * Fixes bug 24332
     */
    private String stripExpireParam(final String token) {
        if (token.indexOf("&expires") < 0) {
            return token;
        }
        final Matcher m = P_EXPIRES.matcher(token);
        final StringBuffer sb = new StringBuffer(token.length());
        if (m.find()) {
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Create the correct {@link OAuthExceptionCode} by mapping the incoming problem against the known problems in {@link OAuthConstants}
     *
     * @param oauth_problem the incoming problem
     * @param request the associated {@link AJAXRequestData}
     * @param service
     * @return the correct {@link OAuthExceptionCode} based on the known problems in {@link OAuthConstants} or
     */
    public static OXException fromOauthProblem(String oauth_problem, AJAXRequestData request, OAuthServiceMetaData service) {
        final String displayName = service.getDisplayName();
        if (OAUTH_PROBLEM_ADDITIONAL_AUTHORIZATION_REQUIRED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_ADDITIONAL_AUTHORIZATION_REQUIRED.create(displayName);
        }
        if (OAUTH_PROBLEM_CONSUMER_KEY_REFUSED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_CONSUMER_KEY_REFUSED.create(displayName);
        }
        if (OAUTH_PROBLEM_CONSUMER_KEY_REJECTED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_CONSUMER_KEY_REJECTED.create(displayName);
        }
        if (OAUTH_PROBLEM_CONSUMER_KEY_UNKNOWN.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_CONSUMER_KEY_UNKNOWN.create(displayName);
        }
        if (OAUTH_PROBLEM_NONCE_USED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_NONCE_USED.create();
        }
        if (OAUTH_PROBLEM_PARAMETER_ABSENT.equals(oauth_problem)) {
            String absent_parameters = request.getParameter(URLPARAM_OAUTH_PARAMETERS_ABSENT);
            absent_parameters = Strings.isEmpty(absent_parameters) ? "unknown" : absent_parameters;
            return OAuthExceptionCodes.OAUTH_PROBLEM_PARAMETER_ABSENT.create(absent_parameters);
        }
        if (OAUTH_PROBLEM_PARAMETER_REJECTED.equals(oauth_problem)) {
            String rejected_parameters = request.getParameter(URLPARAM_OAUTH_PARAMETERS_REJECTED);
            rejected_parameters = Strings.isEmpty(rejected_parameters) ? "unknown" : rejected_parameters;
            return OAuthExceptionCodes.OAUTH_PROBLEM_PARAMETER_REJECTED.create(rejected_parameters);
        }
        if (OAUTH_PROBLEM_PERMISSION_DENIED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_PERMISSION_DENIED.create();
        }
        if (OAUTH_PROBLEM_ACCESS_DENIED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_ACCESS_DENIED.create();
        }
        if (OAUTH_PROBLEM_PERMISSION_UNKNOWN.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_PERMISSION_UNKNOWN.create();
        }
        if (OAUTH_PROBLEM_SIGNATURE_INVALID.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_SIGNATURE_INVALID.create();
        }
        if (OAUTH_PROBLEM_SIGNATURE_METHOD_REJECTED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_SIGNATURE_METHOD_REJECTED.create();
        }
        if (OAUTH_PROBLEM_TIMESTAMP_REFUSED.equals(oauth_problem)) {
            String acceptable_timestamps = request.getParameter(URLPARAM_OAUTH_ACCEPTABLE_TIMESTAMPS);
            acceptable_timestamps = Strings.isEmpty(acceptable_timestamps) ? "unknown" : acceptable_timestamps;
            return OAuthExceptionCodes.OAUTH_PROBLEM_TIMESTAMP_REFUSED.create(acceptable_timestamps);
        }
        if (OAUTH_PROBLEM_TOKEN_EXPIRED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_TOKEN_EXPIRED.create(displayName);
        }
        if (OAUTH_PROBLEM_TOKEN_REJECTED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_TOKEN_REJECTED.create(displayName);
        }
        if (OAUTH_PROBLEM_TOKEN_REVOKED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_TOKEN_REVOKED.create(displayName);
        }
        if (OAUTH_PROBLEM_TOKEN_USED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_TOKEN_USED.create(displayName);
        }
        if (OAUTH_PROBLEM_USER_REFUSED.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_USER_REFUSED.create(displayName);
        }
        if (OAUTH_PROBLEM_VERIFIER_INVALID.equals(oauth_problem)) {
            return OAuthExceptionCodes.OAUTH_PROBLEM_VERIFIER_INVALID.create();
        }
        if (OAUTH_PROBLEM_VERSION_REJECTED.equals(oauth_problem)) {
            String acceptable_versions = request.getParameter(URLPARAM_OAUTH_PARAMETERS_ABSENT);
            acceptable_versions = Strings.isEmpty(acceptable_versions) ? "unknown" : acceptable_versions;
        }
        return OAuthExceptionCodes.OAUTH_PROBLEM_UNEXPECTED.create(oauth_problem);
    }

    /**
     * Gets the scopes from the request and converts them to {@link OAuthScope}s using the {@link OAuthScopeRegistry}
     *
     * @param request The {@link AJAXRequestData}
     * @param serviceId The OAuth service provider's identifier
     * @return A {@link Set} with all {@link OAuthScope}s to enable
     * @throws OXException if the {@link OAuthScope}s can not be retrieved or if the <code>scopes</code> URL parameter is missing form the request
     */
    protected Set<OAuthScope> getScopes(AJAXRequestData request, String serviceId) throws OXException {
        OAuthScopeRegistry scopeRegistry = Services.getService(OAuthScopeRegistry.class);
        // Get the scope parameter
        String scope = request.getParameter("scopes");

        OAuthAPIRegistry service = Services.getService(OAuthAPIRegistry.class);
        if (service == null) {
            throw ServiceExceptionCode.absentService(OAuthAPIRegistry.class);
        }
        if (isEmpty(scope)) {
            API api = service.resolveFromServiceId(serviceId);
            if (null == api) {
                throw OXException.general("No such API: " + serviceId);
            }
            return scopeRegistry.getLegacyScopes(api);
        }
        // Get the scopes
        return scopeRegistry.getAvailableScopes(service.resolveFromServiceId(serviceId), OXScope.valuesOf(scope));
    }

    class ReauthorizeClusterTask implements ClusterTask<Void> {

        private final String taskName;
        private final ServerSession session;
        private final String accountId;
        private final String serviceId;
        private final AJAXRequestData request;

        /**
         * Initialises a new {@link ReauthorizeAction.ReauthorizeClusterTask}.
         */
        public ReauthorizeClusterTask(AJAXRequestData request, ServerSession session, String accountId, String serviceId) {
            super();
            this.request = request;
            this.session = session;
            this.accountId = accountId;
            this.serviceId = serviceId;

            StringBuilder builder = new StringBuilder();
            builder.append(session.getUserId()).append("@");
            builder.append(session.getContextId());
            builder.append(":").append(accountId);
            builder.append(":").append(serviceId);

            taskName = builder.toString();
        }

        @Override
        public String getTaskName() {
            return taskName;
        }

        @Override
        public Void perform() throws OXException {
            OAuthService oauthService = getOAuthService();
            OAuthAccount dbOAuthAccount = oauthService.getAccount(session, Integer.parseInt(accountId));

            OAuthAccessRegistryService registryService = Services.getService(OAuthAccessRegistryService.class);
            OAuthAccessRegistry oAuthAccessRegistry = registryService.get(serviceId);
            OAuthAccess access = oAuthAccessRegistry.get(session.getContextId(), session.getUserId(), dbOAuthAccount.getId());

            if (access == null) {
                performReauthorize(oauthService);
            } else {
                // If the OAuth access is not initialised yet reload from DB, as it may have been changed from another node
                OAuthAccount cachedOAuthAccount = (access.getOAuthAccount() == null) ? oauthService.getAccount(session, Integer.parseInt(accountId)) : access.getOAuthAccount();
                if (dbOAuthAccount.getToken().equals(cachedOAuthAccount.getToken()) && dbOAuthAccount.getSecret().equals(cachedOAuthAccount.getSecret())) {
                    performReauthorize(oauthService);
                    access.initialize();
                } else {
                    access.initialize();
                }
            }

            return null;
        }

        private void performReauthorize(OAuthService oauthService) throws OXException {
            OAuthServiceMetaData service = oauthService.getMetaDataRegistry().getService(serviceId, session.getUserId(), session.getContextId());
            Map<String, Object> arguments = processOAuthArguments(request, session, service);
            // Get the scopes
            Set<OAuthScope> scopes = getScopes(request, serviceId);
            // By now it doesn't matter which interaction type is passed
            oauthService.updateAccount(session, Integer.parseInt(accountId), serviceId, OAuthInteractionType.CALLBACK, arguments, scopes);
        }

        @Override
        public int getContextId() {
            return session.getContextId();
        }

        @Override
        public int getUserId() {
            return session.getUserId();
        }
    }
}
