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

package com.openexchange.saml.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.security.credential.Credential;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.saml.SAMLConfig;
import com.openexchange.saml.SAMLConfig.Binding;
import com.openexchange.saml.spi.AbstractSAMLBackend;
import com.openexchange.saml.spi.AuthenticationInfo;
import com.openexchange.saml.spi.CredentialProvider;
import com.openexchange.saml.spi.DefaultConfig;
import com.openexchange.saml.spi.LogoutInfo;
import com.openexchange.saml.state.AuthnRequestInfo;
import com.openexchange.saml.state.LogoutRequestInfo;
import com.openexchange.saml.state.StateManagement;
import com.openexchange.saml.validation.AuthnResponseValidationResult;
import com.openexchange.saml.validation.ValidationException;
import com.openexchange.saml.validation.ValidationStrategy;
import com.openexchange.user.UserService;


/**
 * A backend for development and debugging purposes that simply accepts every response and assertion.
 * Users are resolved by NameID, which is expected in email format and to match {@code <user>@<context>}.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.10.3
 */
public class VeryDangerousSAMLBackend extends AbstractSAMLBackend {

    private final UserService userService;

    private final ContextService contextService;

    private DefaultConfig defaultConfig;

    /**
     * Initializes a new {@link VeryDangerousSAMLBackend}.
     *
     * @param userService
     * @param contextService
     * @param config The {@link LeanConfigurationService} to use
     * @throws OXException
     */
    public VeryDangerousSAMLBackend(UserService userService, ContextService contextService, LeanConfigurationService config) throws OXException {
        super();
        this.userService = userService;
        this.contextService = contextService;
        this.defaultConfig = DefaultConfig.init(config);
    }

    @Override
    protected CredentialProvider doGetCredentialProvider() {
        return new CredentialProvider() {

            @Override
            public boolean hasValidationCredentials() {
                return false;
            }

            @Override
            public boolean hasValidationCredential() {
                return false;
            }

            @Override
            public boolean hasSigningCredential() {
                return false;
            }

            @Override
            public boolean hasDecryptionCredential() {
                return false;
            }

            @Override
            public List<Credential> getValidationCredentials() {
                return Collections.emptyList();
            }

            @Override
            public Credential getValidationCredential() {
                return null;
            }

            @Override
            public Credential getSigningCredential() {
                return null;
            }

            @Override
            public Credential getDecryptionCredential() {
                return null;
            }
        };
    }

    @Override
    protected ValidationStrategy doGetValidationStrategy(SAMLConfig config, StateManagement stateManagement) {
        return new ValidationStrategy() {

            @Override
            public void validateLogoutResponse(LogoutResponse response, HttpServletRequest httpRequest, LogoutRequestInfo requestInfo, Binding binding) throws ValidationException {

            }

            @Override
            public void validateLogoutRequest(LogoutRequest logoutRequest, HttpServletRequest httpRequest, Binding binding) throws ValidationException {

            }

            @Override
            public AuthnResponseValidationResult validateAuthnResponse(Response response, AuthnRequestInfo requestInfo, Binding binding) throws ValidationException {
                Optional<Assertion> assertion = response.getAssertions().stream().findFirst();
                return new AuthnResponseValidationResult(assertion.get());
            }
        };
    }

    @Override
    protected AuthenticationInfo doResolveAuthnResponse(Response response, Assertion assertion) throws OXException {
        String[] split = assertion.getSubject().getNameID().getValue().split("@");
        String userInfo = split[0];
        String contextInfo = split[1];

        if (userInfo == null || contextInfo == null) {
            throw new OXException();
        }

        int contextId = contextService.getContextId(contextInfo);
        if (contextId < 0) {
            throw new OXException();
        }

        int userId = userService.getUserId(userInfo, contextService.getContext(contextId));
        AuthenticationInfo authInfo = new AuthenticationInfo(contextId, userId);
        return authInfo;
    }

    @Override
    protected LogoutInfo doResolveLogoutRequest(LogoutRequest request) throws OXException {
        LogoutInfo logoutInfo = new LogoutInfo();
        return logoutInfo;
    }

    @Override
    protected void doFinishLogout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        httpResponse.sendRedirect("https://www.google.com");
    }

    @Override
    public SAMLConfig getConfig() {
        return defaultConfig;
    }

}
