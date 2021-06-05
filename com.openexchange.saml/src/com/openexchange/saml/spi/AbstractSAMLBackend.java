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

package com.openexchange.saml.spi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.login.LoginConfiguration;
import com.openexchange.ajax.login.LoginTools;
import com.openexchange.authentication.Authenticated;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.login.LoginRequest;
import com.openexchange.saml.SAMLConfig;
import com.openexchange.saml.SAMLExceptionCode;
import com.openexchange.saml.state.AuthnRequestInfo;
import com.openexchange.saml.state.DefaultAuthnRequestInfo;
import com.openexchange.saml.state.StateManagement;
import com.openexchange.saml.validation.StrictValidationStrategy;
import com.openexchange.saml.validation.ValidationStrategy;
import com.openexchange.user.User;


/**
 * It's considered best practice to inherit from this class when implementing a {@link SAMLBackend}.
 * That allows you to start with the minimal set of necessary methods to implement. Additionally it
 * will try to retain compile-time compatibility while the {@link SAMLBackend} interface evolves. Minor
 * changes/extensions will then simply be handled by default implementations within this class.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.6.1
 */
public abstract class AbstractSAMLBackend implements SAMLBackend {

    private static final Logger LOG_ABSTRACT = LoggerFactory.getLogger(AbstractSAMLBackend.class);

    /**
     * Initializes a new {@link AbstractSAMLBackend}.
     */
    protected AbstractSAMLBackend() {
        super();
    }

    /**
     * Initializes the credential provider and returns it.
     *
     * @return The credential provider
     */
    protected abstract CredentialProvider doGetCredentialProvider();

    /**
     * @see SAMLBackend#resolveAuthnResponse(Response, Assertion)
     */
    protected abstract AuthenticationInfo doResolveAuthnResponse(Response response, Assertion assertion) throws OXException;

    /**
     * Gets the possible available access token from given assertion.
     *
     * @param assertion The assertion to get the access token from
     * @return The access token or <code>null</code>
     * @throws OXException If determining the OAuth access token fails
     */
    @SuppressWarnings("unused")
    protected String doGetAccessToken(Assertion assertion) throws OXException {
        return null;
    }

    /**
     * @see SAMLBackend#resolveLogoutRequest(LogoutRequest)
     */
    protected abstract LogoutInfo doResolveLogoutRequest(LogoutRequest request) throws OXException;

    /**
     * @see SAMLBackend#finishLogout(HttpServletRequest, HttpServletResponse)
     */
    protected abstract void doFinishLogout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException;

    /**
     * @see SAMLBackend#getWebSSOCustomizer()
     */
    protected WebSSOCustomizer doGetWebSSOCustomizer() {
        return null;
    }

    /**
     * @see SAMLBackend#getExceptionHandler()
     */
    protected ExceptionHandler doGetExceptionHandler() {
        return new DefaultExceptionHandler();
    }

    /**
     * @see SAMLBackend#getValidationStrategy(SAMLConfig, StateManagement)
     */
    protected ValidationStrategy doGetValidationStrategy(SAMLConfig config, StateManagement stateManagement) {
        return new StrictValidationStrategy(config, getCredentialProvider(), stateManagement);
    }

    @Override
    public LoginRequest prepareLoginRequest(HttpServletRequest httpRequest, LoginConfiguration loginConfiguration, User user, Context context) throws OXException {
        String login = user.getLoginInfo() + '@' + context.getLoginInfo()[0];
        String defaultClient = loginConfiguration.getDefaultClient();
        return LoginTools.parseLogin(httpRequest, login, null, false, defaultClient, loginConfiguration.isCookieForceHTTPS(), false);
    }

    /**
     * @see SAMLBackend#enhanceAuthenticated(Authenticated, Map)
     */
    @SuppressWarnings("unused")
    protected Authenticated doEnhanceAuthenticated(Authenticated authenticated, Map<String, String> properties) {
        return null;
    }

    @Override
    public CredentialProvider getCredentialProvider() {
        return doGetCredentialProvider();
    }

    @Override
    public WebSSOCustomizer getWebSSOCustomizer() {
        return doGetWebSSOCustomizer();
    }

    @Override
    public ExceptionHandler getExceptionHandler() {
        return doGetExceptionHandler();
    }

    @Override
    public ValidationStrategy getValidationStrategy(SAMLConfig config, StateManagement stateManagement) {
        return doGetValidationStrategy(config, stateManagement);
    }

    @Override
    public Authenticated enhanceAuthenticated(Authenticated authenticated, Map<String, String> properties) {
        return doEnhanceAuthenticated(authenticated, properties);
    }

    @Override
    public AuthenticationInfo resolveAuthnResponse(Response response, Assertion assertion) throws OXException {
        return doResolveAuthnResponse(response, assertion);
    }

    @Override
    public AuthenticationInfo resolveAuthnResponse(Response response, Assertion assertion, AuthnRequestInfo requestInfo) throws OXException {
        // if there is a need to use the AuthnRequestInfo, just override this method
        return doResolveAuthnResponse(response, assertion);
    }

    @Override
    public String getAccessToken(Assertion assertion) throws OXException {
        return doGetAccessToken(assertion);
    }

    @Override
    public LogoutInfo resolveLogoutRequest(LogoutRequest request) throws OXException {
        return doResolveLogoutRequest(request);
    }

    @Override
    public void finishLogout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        doFinishLogout(httpRequest, httpResponse);
    }

    @Override
    public AuthnRequestInfo parseRelayState(Response response, String relayState) throws OXException {
        try {
            LOG_ABSTRACT.debug("RelayState: {}", relayState);
            DefaultAuthnRequestInfo defaultAuthnRequestInfo = new DefaultAuthnRequestInfo();
            String string = new String(Base64.decode(relayState.getBytes(StandardCharsets.ISO_8859_1)));
            LOG_ABSTRACT.debug("decoded RelayState: {}", string);
            String[] splitRelayState = string.split(":");
            for (String s : splitRelayState) {
                String[] split = s.split("=");
                if (null != split && split.length == 2) {
                    switch (split[0]) {
                        case "domain":
                            defaultAuthnRequestInfo.setDomainName(split[1]);
                            break;
                        case "loginpath":
                            defaultAuthnRequestInfo.setLoginPath(split[1]);
                            break;
                        case "client":
                            defaultAuthnRequestInfo.setClientID(split[1]);
                            break;
                        default:
                            break;
                    }
                }
            }
            return defaultAuthnRequestInfo;
        } catch (DecoderException e) {
            // Base64-decoding failed
            throw SAMLExceptionCode.INVALID_REQUEST.create(e, "The 'RelayState' parameter is invalid");
        } catch (RuntimeException e) {
            // Whatever unexpected uncaught exception
            throw SAMLExceptionCode.INVALID_REQUEST.create(e, "Parsing the 'RelayState' parameter failed");
        }
    }

    @Override
    public String getPath() {
        return doGethPath();
    }

    /**
     * The implementation of the getPath Method
     * @return the path part
     */
    protected String doGethPath() {
        return "";
    }

    @Override
    public String getStaticLoginRedirectLocation(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return doGetStaticLoginRedirectLocation(httpRequest, httpResponse);
    }

    /**
     * The implementation of the getStaticLoginRedirectLocation Method
     * @return the static redirect at login and relogin time
     */
    @SuppressWarnings("unused")
    protected String doGetStaticLoginRedirectLocation(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return null;
    }

}
