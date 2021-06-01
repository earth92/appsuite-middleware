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

package com.openexchange.saml;

import static com.openexchange.saml.utils.SAMLTestUtils.buildAddSessionParameter;
import static org.hamcrest.Matchers.containsString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.sim.SimHttpServletRequest;
import javax.servlet.http.sim.SimHttpServletResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SessionIndex;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.StatusResponseType;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.encryption.Encrypter;
import org.opensaml.saml.saml2.encryption.Encrypter.KeyPlacement;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.crypto.SigningUtil;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.algorithm.AlgorithmSupport;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.EncryptionConstants;
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.KeyInfoSupport;
import org.opensaml.xmlsec.keyinfo.NamedKeyInfoGeneratorManager;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.Signer;
import com.openexchange.ajax.LoginServlet;
import com.openexchange.ajax.fields.LoginFields;
import com.openexchange.ajax.login.HashCalculator;
import com.openexchange.ajax.login.LoginTools;
import com.openexchange.authentication.SessionEnhancement;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.SimConfigurationService;
import com.openexchange.dispatcher.DispatcherPrefixService;
import com.openexchange.groupware.ldap.SimUser;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.java.util.UUIDs;
import com.openexchange.saml.SAMLConfig.Binding;
import com.openexchange.saml.http.InitService;
import com.openexchange.saml.impl.AlgorithmUtils;
import com.openexchange.saml.impl.WebSSOProviderImpl;
import com.openexchange.saml.oauth.service.OAuthAccessTokenService;
import com.openexchange.saml.oauth.service.SimOAuthAccessTokenService;
import com.openexchange.saml.spi.CredentialProvider;
import com.openexchange.saml.spi.DefaultExceptionHandler;
import com.openexchange.saml.spi.SAMLBackend;
import com.openexchange.saml.state.SimStateManagement;
import com.openexchange.saml.tools.SAMLLoginTools;
import com.openexchange.saml.tools.SignatureHelper;
import com.openexchange.saml.utils.SecurityHelperUtils;
import com.openexchange.server.SimpleServiceLookup;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.session.reservation.SessionReservationService;
import com.openexchange.session.reservation.SimSessionReservationService;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.sessiond.SimSessiondService;
import com.openexchange.user.SimUserService;
import com.openexchange.user.UserService;
import net.shibboleth.utilities.java.support.codec.Base64Support;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;

/**
 * {@link SAMLWebSSOProviderTest}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.6.1
 */
public class SAMLWebSSOProviderTest {

    private static SAMLWebSSOProvider provider;
    private static TestConfig config;
    private static OpenSAML openSAML;
    private static TestCredentials testCredentials;
    private static CredentialProvider credentialProvider;
    private static SimStateManagement stateManagement;
    private static SimSessionReservationService sessionReservationService;
    private static SimSessiondService sessiondService;
    private static SimpleServiceLookup services;
    private static SimUserService userService;
    private static ParserPool parserPool;

    @BeforeClass
    public static void beforeClass() throws Exception {
        InitializationService.initialize();
        parserPool = XMLObjectProviderRegistrySupport.getParserPool();
        testCredentials = new TestCredentials();
        credentialProvider = testCredentials.getSPCredentialProvider();

        /*
         * Init service provider
         */
        config = new TestConfig();
        openSAML = new OpenSAML();

        services = new SimpleServiceLookup();
        sessionReservationService = new SimSessionReservationService();
        services.add(SessionReservationService.class, sessionReservationService);
        services.add(DispatcherPrefixService.class, new DispatcherPrefixService() {

            @Override
            public String getPrefix() {
                return "/appsuite/api/";
            }
        });
        TestSAMLBackend samlBackend = new TestSAMLBackend(credentialProvider, config);
        services.add(SAMLBackend.class, samlBackend);
        sessiondService = new SimSessiondService();
        services.add(SessiondService.class, sessiondService);
        userService = new SimUserService();
        services.add(UserService.class, userService);
        userService.addUser(new SimUser(1), 1);
        services.add(OAuthAccessTokenService.class, new SimOAuthAccessTokenService());
        stateManagement = new SimStateManagement();
        provider = new WebSSOProviderImpl(config, openSAML, stateManagement, services, samlBackend);

        // static dependency of c.o.ajax.SessionUtility
        SimConfigurationService simConfigurationService = new SimConfigurationService();
        ServerServiceRegistry.getInstance().addService(ConfigurationService.class, simConfigurationService);
    }

    @Test
    public void testMetadata() throws Exception {
        String metadataXML = provider.getMetadataXML();
        Assert.assertNotNull(metadataXML);
    }

    @Test
    public void testLoginRoundtrip() throws Exception {
        /*
         * Trigger AuthnRequest
         */
        String requestHost = "webmail.example.com";
        String requestedLoginPath = "/fancyclient/login.html";
        SimHttpServletRequest loginHTTPRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost(requestHost)
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "login")
            .setParameter("loginPath", requestedLoginPath)
            .setParameter("client", "test-client")
            .build());
        URI authnRequestURI = new URI(provider.buildAuthnRequest(loginHTTPRequest, new SimHttpServletResponse()));

        /*
         * Validate redirect location
         */
        String relayState = parseURIQuery(authnRequestURI).get("RelayState");
        Assert.assertNotNull(relayState);
        SimHttpServletRequest authnHTTPRequest = prepareHTTPRequest("GET", authnRequestURI);
        Assert.assertNull(SignatureHelper.validateURISignature(authnHTTPRequest, Collections.singletonList(testCredentials.getSPSigningCredential())));
        AuthnRequest authnRequest = parseAuthnRequest(authnHTTPRequest);

        /*
         * Build response and process it
         */
        Response response = buildResponse(authnRequest);
        String marshall = marshall(response);

        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("POST", new URIBuilder(authnRequest.getAssertionConsumerServiceURL())
            .setParameter("SAMLResponse", Base64Support.encode(marshall.getBytes(), false))
            .setParameter("RelayState", relayState)
            .build());
        SimHttpServletResponse httpResponse = new SimHttpServletResponse();
        provider.handleAuthnResponse(samlResponseRequest, httpResponse, Binding.HTTP_POST);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final login redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals(requestHost, locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals(requestedLoginPath, redirectParams.get(SAMLLoginTools.PARAM_LOGIN_PATH));
        Assert.assertEquals("test-client", redirectParams.get(LoginFields.CLIENT_PARAM));
        Assert.assertEquals(SAMLLoginTools.ACTION_SAML_LOGIN, redirectParams.get("action"));
        String reservationToken = redirectParams.get(SAMLLoginTools.PARAM_TOKEN);
        Assert.assertNotNull(reservationToken);
        Assert.assertNotNull(sessionReservationService.removeReservation(reservationToken));
    }

    @Test
    public void testLoginRoundtrip_auth_bypass_vul() throws Exception {
        /*
         * Trigger AuthnRequest
         */
        String requestHost = "webmail.example.com";
        String requestedLoginPath = "/fancyclient/login.html";
        SimHttpServletRequest loginHTTPRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost(requestHost)
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "login")
            .setParameter("loginPath", requestedLoginPath)
            .setParameter("client", "test-client")
            .build());
        URI authnRequestURI = new URI(provider.buildAuthnRequest(loginHTTPRequest, new SimHttpServletResponse()));

        /*
         * Validate redirect location
         */
        String relayState = parseURIQuery(authnRequestURI).get("RelayState");
        Assert.assertNotNull(relayState);
        SimHttpServletRequest authnHTTPRequest = prepareHTTPRequest("GET", authnRequestURI);
        Assert.assertNull(SignatureHelper.validateURISignature(authnHTTPRequest, Collections.singletonList(testCredentials.getSPSigningCredential())));
        AuthnRequest authnRequest = parseAuthnRequest(authnHTTPRequest);

        /*
         * Build response and process it
         */
        Response response = buildResponse(authnRequest, false);
        String marshall = marshall(response).replace("oxuser1", "ox<!-- this is a comment -->user1");
        Assert.assertThat(marshall, containsString("<!-- this is a comment -->"));
        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("POST", new URIBuilder(authnRequest.getAssertionConsumerServiceURL())
            .setParameter("SAMLResponse", Base64Support.encode(marshall.getBytes(), false))
            .setParameter("RelayState", relayState)
            .build());
        SimHttpServletResponse httpResponse = new SimHttpServletResponse();
        provider.handleAuthnResponse(samlResponseRequest, httpResponse, Binding.HTTP_POST);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final login redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals(requestHost, locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals(requestedLoginPath, redirectParams.get(SAMLLoginTools.PARAM_LOGIN_PATH));
        Assert.assertEquals("test-client", redirectParams.get(LoginFields.CLIENT_PARAM));
        Assert.assertEquals(SAMLLoginTools.ACTION_SAML_LOGIN, redirectParams.get("action"));
        String reservationToken = redirectParams.get(SAMLLoginTools.PARAM_TOKEN);
        Assert.assertNotNull(reservationToken);
        Assert.assertNotNull(sessionReservationService.removeReservation(reservationToken));
    }

    @Test
    public void testAutoLoginWithDeepLink() throws Exception {
        /*
         * Fake SAML cookie and try auto login
         */
        TestLoginConfigurationLookup loginConfigurationLookup = new TestLoginConfigurationLookup();
        InitService initService = new InitService(config, provider, new DefaultExceptionHandler(), loginConfigurationLookup, services);
        final String samlCookieValue = UUIDs.getUnformattedString(UUID.randomUUID());
        Session session = sessiondService.addSession(buildAddSessionParameter(new SessionEnhancement() {

            @Override
            public void enhanceSession(Session session) {
                session.setParameter(SAMLSessionParameters.SESSION_INDEX, UUIDs.getUnformattedString(UUID.randomUUID()));
                session.setParameter(SAMLSessionParameters.SUBJECT_ID, UUIDs.getUnformattedString(UUID.randomUUID()));
                session.setParameter(SAMLSessionParameters.SESSION_COOKIE, samlCookieValue);
            }
        }));

        String deepLinkParams = "app=io.ox/mail&folder=virtual/all-unseen";
        SimHttpServletRequest autoLoginHTTPRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost("webmail.example.com")
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "login")
            .setParameter("client", "test-client")
            .setParameter("redirect", "true")
            .setParameter("uriFragment", "!!&" + deepLinkParams)
            .build());
        String cookieHash = HashCalculator.getInstance().getHash(
            autoLoginHTTPRequest,
            LoginTools.parseUserAgent(autoLoginHTTPRequest),
            LoginTools.parseClient(autoLoginHTTPRequest, false, loginConfigurationLookup.getLoginConfiguration().getDefaultClient()));
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(new Cookie(LoginServlet.SESSION_PREFIX + cookieHash, session.getSessionID()));
        cookies.add(new Cookie(LoginServlet.SECRET_PREFIX + cookieHash, session.getSecret()));
        autoLoginHTTPRequest.setCookies(cookies);
        SimHttpServletResponse initLoginResponse = new SimHttpServletResponse();
        initService.service(autoLoginHTTPRequest, initLoginResponse);
        Assert.assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, initLoginResponse.getStatus());
        String redirectLocation = initLoginResponse.getHeader("location");
        Assert.assertNotNull(redirectLocation);
        Matcher sessionMatcher = Pattern.compile("session=([a-z0-9]+)").matcher(redirectLocation);
        Assert.assertTrue(sessionMatcher.find());
        Assert.assertEquals(session.getSessionID(), sessionMatcher.group(1));

        Matcher deepLinkMatcher = Pattern.compile(Pattern.quote("&" + deepLinkParams)).matcher(redirectLocation);
        Assert.assertTrue(deepLinkMatcher.find());
    }

    @Test
    public void testSPInitiatedLogoutRoundtripPOST() throws Exception {
        /*
         * Create sim session
         */
        final String sessionIndex = UUIDs.getUnformattedString(UUID.randomUUID());
        NameID nameID = openSAML.buildSAMLObject(NameID.class);
        nameID.setFormat(NameID.EMAIL);
        nameID.setValue("test.user@example.com");
        final String marshalledNameID = openSAML.marshall(nameID);
        Session session = sessiondService.addSession(buildAddSessionParameter(new SessionEnhancement() {

            @Override
            public void enhanceSession(Session session) {
                session.setParameter(SAMLSessionParameters.SESSION_INDEX, sessionIndex);
                session.setParameter(SAMLSessionParameters.SUBJECT_ID, marshalledNameID);
            }
        }));

        /*
         * Trigger LogoutRequest
         */
        String requestHost = "webmail.example.com";
        SimHttpServletRequest logoutHTTPRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost(requestHost)
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "logout")
            .setParameter("session", session.getSessionID())
            .build());
        SimHttpServletResponse logoutHTTPResponse = new SimHttpServletResponse();
        URI logoutRequestURI = new URI(provider.buildLogoutRequest(logoutHTTPRequest, logoutHTTPResponse, session));

        /*
         * Validate redirect location
         */
        String relayState = parseURIQuery(logoutRequestURI).get("RelayState");
        Assert.assertNotNull(relayState);
        SimHttpServletRequest idpHTTPLogoutRequest = prepareHTTPRequest("GET", logoutRequestURI);
        Assert.assertNull(SignatureHelper.validateURISignature(idpHTTPLogoutRequest, Collections.singletonList(testCredentials.getSPSigningCredential())));
        LogoutRequest logoutRequest = parseLogoutRequest(idpHTTPLogoutRequest);
        Assert.assertEquals(marshalledNameID, openSAML.marshall(logoutRequest.getNameID()));
        Assert.assertEquals(sessionIndex, logoutRequest.getSessionIndexes().get(0).getSessionIndex());

        /*
         * Build response and process it
         */
        LogoutResponse logoutResponse = buildLogoutResponse(logoutRequest);
        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("POST", new URIBuilder(config.getSingleLogoutServiceURL())
            .setParameter("SAMLResponse", Base64Support.encode(marshall(logoutResponse).getBytes(), false))
            .setParameter("RelayState", relayState)
            .build());

        SimHttpServletResponse httpResponse = new SimHttpServletResponse();
        provider.handleLogoutResponse(samlResponseRequest, httpResponse, Binding.HTTP_POST);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final logout redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals(requestHost, locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals("samlLogout", redirectParams.get("action"));
        Assert.assertEquals(session.getSessionID(), redirectParams.get("session"));
    }

    @Test
    public void testSPInitiatedLogoutRoundtripREDIRECT() throws Exception {
        /*
         * Create sim session
         */
        final String sessionIndex = UUIDs.getUnformattedString(UUID.randomUUID());
        NameID nameID = openSAML.buildSAMLObject(NameID.class);
        nameID.setFormat(NameID.EMAIL);
        nameID.setValue("test.user@example.com");
        final String marshalledNameID = openSAML.marshall(nameID);
        Session session = sessiondService.addSession(buildAddSessionParameter(new SessionEnhancement() {

            @Override
            public void enhanceSession(Session session) {
                session.setParameter(SAMLSessionParameters.SESSION_INDEX, sessionIndex);
                session.setParameter(SAMLSessionParameters.SUBJECT_ID, marshalledNameID);
            }
        }));

        /*
         * Trigger LogoutRequest
         */
        String requestHost = "webmail.example.com";
        SimHttpServletRequest logoutHTTPRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost(requestHost)
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "logout")
            .setParameter("session", session.getSessionID())
            .build());
        SimHttpServletResponse logoutHTTPResponse = new SimHttpServletResponse();
        URI logoutRequestURI = new URI(provider.buildLogoutRequest(logoutHTTPRequest, logoutHTTPResponse, session));

        /*
         * Validate redirect location
         */
        String relayState = parseURIQuery(logoutRequestURI).get("RelayState");
        Assert.assertNotNull(relayState);
        SimHttpServletRequest idpHTTPLogoutRequest = prepareHTTPRequest("GET", logoutRequestURI);
        Assert.assertNull(SignatureHelper.validateURISignature(idpHTTPLogoutRequest, Collections.singletonList(testCredentials.getSPSigningCredential())));
        LogoutRequest logoutRequest = parseLogoutRequest(idpHTTPLogoutRequest);
        Assert.assertEquals(marshalledNameID, openSAML.marshall(logoutRequest.getNameID()));
        Assert.assertEquals(sessionIndex, logoutRequest.getSessionIndexes().get(0).getSessionIndex());

        /*
         * Build response as URL
         */
        LogoutResponse logoutResponse = buildUnsignedLogoutResponse(logoutRequest);
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFLATED, true);
        DeflaterOutputStream deflaterStream = new DeflaterOutputStream(bytesOut, deflater);
        deflaterStream.write(marshall(logoutResponse).getBytes(Charsets.UTF_8));
        deflaterStream.finish();
        String encoded = Base64Support.encode(bytesOut.toByteArray(), false);
        URIBuilder redirectLocationBuilder = new URIBuilder()
            .setScheme("https")
            .setHost(requestHost)
            .setPath("/appsuite/api/saml/sls")
            .setParameter("SAMLResponse", encoded)
            .setParameter("RelayState", relayState);
        Credential signingCredential = testCredentials.getIDPSigningCredential();
        redirectLocationBuilder.setParameter("SigAlg", AlgorithmUtils.getAlgorithmURI(signingCredential));
        String rawQuery = redirectLocationBuilder.build().getRawQuery();
        String sigAlg = signingCredential.getPrivateKey().getAlgorithm();
        byte[] rawSignature = SigningUtil.sign(signingCredential, sigAlg, AlgorithmSupport.isHMAC(sigAlg), rawQuery.getBytes(Charsets.UTF_8));
        String signature = Base64Support.encode(rawSignature, false);
        redirectLocationBuilder.setParameter("Signature", signature);

        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("GET", redirectLocationBuilder.build());
        SimHttpServletResponse httpResponse = new SimHttpServletResponse();
        provider.handleLogoutResponse(samlResponseRequest, httpResponse, Binding.HTTP_REDIRECT);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final logout redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals(requestHost, locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals("samlLogout", redirectParams.get("action"));
        Assert.assertEquals(session.getSessionID(), redirectParams.get("session"));
    }

    @Test
    public void testIdPInitiatedLogout() throws Exception {
        /*
         * Create sim session
         */
        final String sessionIndex = UUIDs.getUnformattedString(UUID.randomUUID());
        NameID nameID = openSAML.buildSAMLObject(NameID.class);
        nameID.setFormat(NameID.EMAIL);
        nameID.setValue("test.user@example.com");
        final String marshalledNameID = openSAML.marshall(nameID);
        Session session = sessiondService.addSession(buildAddSessionParameter(new SessionEnhancement() {

            @Override
            public void enhanceSession(Session session) {
                session.setParameter(SAMLSessionParameters.SESSION_INDEX, sessionIndex);
                session.setParameter(SAMLSessionParameters.SUBJECT_ID, marshalledNameID);
            }
        }));
        Assert.assertNotNull(sessiondService.getSession(session.getSessionID()));

        /*
         * Build logout request and compile redirect URI
         */
        String relayState = UUIDs.getUnformattedString(UUID.randomUUID());
        LogoutRequest logoutRequest = buildIdPLogoutRequest(sessionIndex, nameID);

        MessageContext<SAMLObject> context = new MessageContext<>();
        SAMLBindingSupport.setRelayState(context, relayState);
        SimHttpServletResponse logoutHTTPRedirect = new SimHttpServletResponse();
        SingleLogoutService peerEndpoint = openSAML.buildSAMLObject(SingleLogoutService.class);
        peerEndpoint.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
        peerEndpoint.setLocation(config.getSingleLogoutServiceURL());
        context.setMessage(logoutRequest);
        context.getSubcontext(SAMLPeerEntityContext.class, true).getSubcontext(SAMLEndpointContext.class, true).setEndpoint(peerEndpoint);

        final SignatureSigningParameters signingParameters = new SignatureSigningParameters();
        signingParameters.setSigningCredential(testCredentials.getIDPSigningCredential());
        signingParameters.setSignatureAlgorithm(AlgorithmUtils.getAlgorithmURI(testCredentials.getIDPSigningCredential()));
        context.getSubcontext(SecurityParametersContext.class, true).setSignatureSigningParameters(signingParameters);

        HTTPRedirectDeflateEncoder encoder = new HTTPRedirectDeflateEncoder();
        encoder.setHttpServletResponse(logoutHTTPRedirect);
        encoder.setMessageContext(context);
        encoder.initialize();
        encoder.encode();
        /*
         * Handle logout request
         */
        String redirectLocation = logoutHTTPRedirect.getHeader("Location");
        SimHttpServletRequest logoutHTTPRequest = prepareHTTPRequest("GET", new URI(redirectLocation));
        SimHttpServletResponse logoutHTTPResponse = new SimHttpServletResponse();
        provider.handleLogoutRequest(logoutHTTPRequest, logoutHTTPResponse, Binding.HTTP_REDIRECT);
        assertCachingDisabledHeaders(logoutHTTPResponse);
        URI responseRedirectURI = new URI(logoutHTTPResponse.getHeader("Location"));

        /*
         * Validate logout response
         */
        Map<String, String> params = parseURIQuery(responseRedirectURI);
        Assert.assertEquals(relayState, params.get("RelayState"));
        SimHttpServletRequest logoutResponseHTTPRequest = prepareHTTPRequest("GET", responseRedirectURI);
        Assert.assertNull(SignatureHelper.validateURISignature(logoutResponseHTTPRequest, Collections.singletonList(testCredentials.getSPSigningCredential())));
        LogoutResponse logoutResponse = parseLogoutResponse(logoutResponseHTTPRequest);
        Assert.assertEquals(logoutRequest.getID(), logoutResponse.getInResponseTo());
        Status status = logoutResponse.getStatus();
        Assert.assertNotNull(status);
        Assert.assertEquals("urn:oasis:names:tc:SAML:2.0:status:Success", status.getStatusCode().getValue());

        /*
         * Assert session was terminated
         */
        Assert.assertNull(sessiondService.getSession(session.getSessionID()));
    }

    @Test
    public void testIdPInitiatedLogin() throws Exception {
        AuthnRequest authnRequest = prepareAuthnRequest();

        /*
         * Build response and process it
         */
        Response response = buildResponseWithoutInResponseTo();
        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("POST", new URIBuilder(authnRequest.getAssertionConsumerServiceURL())
            .setParameter("SAMLResponse", Base64Support.encode(marshall(response).getBytes(), false))
            .build());

        SimHttpServletResponse httpResponse = new SimHttpServletResponse();
        provider.handleAuthnResponse(samlResponseRequest, httpResponse, Binding.HTTP_POST);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final login redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals("webmail.example.com", locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals(SAMLLoginTools.ACTION_SAML_LOGIN, redirectParams.get("action"));
        String reservationToken = redirectParams.get(SAMLLoginTools.PARAM_TOKEN);
        Assert.assertNotNull(reservationToken);
        Assert.assertNotNull(sessionReservationService.removeReservation(reservationToken));
    }

    @Test
    public void testIdPInitiatedLoginWithRelayState() throws Exception {
        AuthnRequest authnRequest = prepareAuthnRequest();

        String requestHost = "webmail2.example.com";
        String requestedLoginPath = "/fancyclient/login.html";
        String requestClient = "test-client";
        String split = ":";

        StringBuilder relayStateBuilder = new StringBuilder();
        relayStateBuilder.append("domain=").append(requestHost).append(split);
        relayStateBuilder.append("loginpath=").append(requestedLoginPath).append(split);
        relayStateBuilder.append("client=").append(requestClient);
        String encodedRelayState = Base64Support.encode(relayStateBuilder.toString().getBytes(), false);

        /*
         * Build response and process it
         */
        Response response = buildResponseWithoutInResponseTo();
        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("POST", new URIBuilder(authnRequest.getAssertionConsumerServiceURL())
            .setParameter("SAMLResponse", Base64Support.encode(marshall(response).getBytes(), false))
            .setParameter("RelayState", encodedRelayState)
            .build());

        SimHttpServletResponse httpResponse = new SimHttpServletResponse();

        provider.handleAuthnResponse(samlResponseRequest, httpResponse, Binding.HTTP_POST);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final login redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals(requestHost, locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals(requestedLoginPath, redirectParams.get(SAMLLoginTools.PARAM_LOGIN_PATH));
        Assert.assertEquals(requestClient, redirectParams.get(LoginFields.CLIENT_PARAM));
        Assert.assertEquals(SAMLLoginTools.ACTION_SAML_LOGIN, redirectParams.get("action"));
        String reservationToken = redirectParams.get(SAMLLoginTools.PARAM_TOKEN);
        Assert.assertNotNull(reservationToken);
        Assert.assertNotNull(sessionReservationService.removeReservation(reservationToken));
    }

    @Test
    public void testIdPInitiatedLoginWithPartlyRelayState() throws Exception {
        AuthnRequest authnRequest = prepareAuthnRequest();

        String requestHost = "webmail2.example.com";
        String split = ":";

        StringBuilder relayStateBuilder = new StringBuilder();
        relayStateBuilder.append("domain=").append(requestHost).append(split);
        String encodedRelayState = Base64Support.encode(relayStateBuilder.toString().getBytes(), false);

        /*
         * Build response and process it
         */
        Response response = buildResponseWithoutInResponseTo();
        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("POST", new URIBuilder(authnRequest.getAssertionConsumerServiceURL())
            .setParameter("SAMLResponse", Base64Support.encode(marshall(response).getBytes(), false))
            .setParameter("RelayState", encodedRelayState)
            .build());

        SimHttpServletResponse httpResponse = new SimHttpServletResponse();
        provider.handleAuthnResponse(samlResponseRequest, httpResponse, Binding.HTTP_POST);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final login redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals(requestHost, locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals(SAMLLoginTools.ACTION_SAML_LOGIN, redirectParams.get("action"));
        String reservationToken = redirectParams.get(SAMLLoginTools.PARAM_TOKEN);
        Assert.assertNotNull(reservationToken);
        Assert.assertNotNull(sessionReservationService.removeReservation(reservationToken));
    }

    @Test
    public void testShardNameRedirectParam() throws Exception {
        AuthnRequest authnRequest = prepareAuthnRequest();

        String requestHost = "webmail2.example.com";
        String split = ":";

        StringBuilder relayStateBuilder = new StringBuilder();
        relayStateBuilder.append("domain=").append(requestHost).append(split);
        String encodedRelayState = Base64Support.encode(relayStateBuilder.toString().getBytes(), false);

        /*
         * Build response and process it
         */
        Response response = buildResponseWithoutInResponseTo();
        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("POST", new URIBuilder(authnRequest.getAssertionConsumerServiceURL())
            .setParameter("SAMLResponse", Base64Support.encode(marshall(response).getBytes(), false))
            .setParameter("RelayState", encodedRelayState)
            .build());

        SimHttpServletResponse httpResponse = new SimHttpServletResponse();
        provider.handleAuthnResponse(samlResponseRequest, httpResponse, Binding.HTTP_POST);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final login redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals(requestHost, locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals("default", redirectParams.get(SAMLLoginTools.PARAM_SHARD));
    }

    @SuppressWarnings("synthetic-access")
    @Test
    public void testCachingHeadersOnInit() throws Exception {
        InitService initService = new InitService(config, provider, new DefaultExceptionHandler(), new TestLoginConfigurationLookup(), services);
        /*
         * login
         */
        SimHttpServletRequest initLoginRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost("example.com")
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "login")
            .build());
        SimServletOutputStream responseStream = new SimServletOutputStream();
        SimHttpServletResponse initLoginResponse = new SimHttpServletResponse();
        initLoginResponse.setOutputStream(responseStream);
        initService.service(initLoginRequest, initLoginResponse);
        Assert.assertEquals(HttpServletResponse.SC_OK, initLoginResponse.getStatus());
        assertCachingDisabledHeaders(initLoginResponse);
        /*
         * re-login
         */
        SimHttpServletRequest initReloginRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost("example.com")
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "relogin")
            .build());
        responseStream.reset();
        SimHttpServletResponse initReloginResponse = new SimHttpServletResponse();
        initReloginResponse.setOutputStream(responseStream);
        initService.service(initReloginRequest, initReloginResponse);
        Assert.assertEquals(HttpServletResponse.SC_OK, initReloginResponse.getStatus());
        assertCachingDisabledHeaders(initReloginResponse);
        /*
         * logout
         */
        Session session = sessiondService.addSession(buildAddSessionParameter(new SessionEnhancement() {

            @Override
            public void enhanceSession(Session session) {}
        }));
        SimHttpServletRequest initLogoutRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost("example.com")
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "logout")
            .setParameter("session", session.getSessionID())
            .build());
        responseStream.reset();
        SimHttpServletResponse initLogoutResponse = new SimHttpServletResponse();
        initLogoutResponse.setOutputStream(responseStream);
        initService.service(initLogoutRequest, initLogoutResponse);
        Assert.assertEquals(HttpServletResponse.SC_OK, initLogoutResponse.getStatus());
        assertCachingDisabledHeaders(initLogoutResponse);
    }

    @Test
    public void testLoginWithDeepLink() throws Exception {
        /*
         * Trigger AuthnRequest
         */
        String requestHost = "webmail.example.com";
        String uriFragment = "app=io.ox/mail&folder=default0/INBOX";
        SimHttpServletRequest loginHTTPRequest = prepareHTTPRequest("GET", new URIBuilder()
            .setScheme("https")
            .setHost(requestHost)
            .setPath("/appsuite/api/saml/init")
            .setParameter("flow", "login")
            .setParameter("uriFragment", uriFragment)
            .build());
        URI authnRequestURI = new URI(provider.buildAuthnRequest(loginHTTPRequest, new SimHttpServletResponse()));

        /*
         * Validate redirect location
         */
        String relayState = parseURIQuery(authnRequestURI).get("RelayState");
        Assert.assertNotNull(relayState);
        SimHttpServletRequest authnHTTPRequest = prepareHTTPRequest("GET", authnRequestURI);
        Assert.assertNull(SignatureHelper.validateURISignature(authnHTTPRequest, Collections.singletonList(testCredentials.getSPSigningCredential())));
        AuthnRequest authnRequest = parseAuthnRequest(authnHTTPRequest);

        /*
         * Build response and process it
         */
        Response response = buildResponse(authnRequest);
        String marshall = marshall(response);
        SimHttpServletRequest samlResponseRequest = prepareHTTPRequest("POST", new URIBuilder(authnRequest.getAssertionConsumerServiceURL())
            .setParameter("SAMLResponse", Base64Support.encode(marshall.getBytes(), false))
            .setParameter("RelayState", relayState)
            .build());

        SimHttpServletResponse httpResponse = new SimHttpServletResponse();
        provider.handleAuthnResponse(samlResponseRequest, httpResponse, Binding.HTTP_POST);
        assertCachingDisabledHeaders(httpResponse);

        /*
         * Assert final login redirect
         */
        Assert.assertEquals(HttpServletResponse.SC_FOUND, httpResponse.getStatus());
        String location = httpResponse.getHeader("Location");
        Assert.assertNotNull(location);
        URI locationURI = new URIBuilder(location).build();
        Assert.assertEquals(requestHost, locationURI.getHost());
        Map<String, String> redirectParams = parseURIQuery(locationURI);
        Assert.assertEquals(uriFragment, redirectParams.get(SAMLLoginTools.PARAM_URI_FRAGMENT));
        Assert.assertEquals(SAMLLoginTools.ACTION_SAML_LOGIN, redirectParams.get("action"));
        String reservationToken = redirectParams.get(SAMLLoginTools.PARAM_TOKEN);
        Assert.assertNotNull(reservationToken);
        Assert.assertNotNull(sessionReservationService.removeReservation(reservationToken));
    }

    private void assertCachingDisabledHeaders(SimHttpServletResponse response) {
        // Pragma: no-cache
        Assert.assertEquals("no-cache", response.getHeader("Pragma"));

        // Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0
        String cacheControl = response.getHeader("Cache-Control");
        Assert.assertNotNull(cacheControl);
        List<String> cacheControls = Strings.splitAndTrim(cacheControl, ",");
        Assert.assertTrue(cacheControls.contains("no-store"));
        Assert.assertTrue(cacheControls.contains("no-cache"));
        Assert.assertTrue(cacheControls.contains("must-revalidate"));
        Assert.assertTrue(cacheControls.contains("post-check=0"));
        Assert.assertTrue(cacheControls.contains("pre-check=0"));

        // Expires: Tue, 03 May 1988 12:00:00 GMT
        String expires = response.getHeader("Expires");
        Assert.assertNotNull(expires);
        try {
            Date expiryDate = new SimpleDateFormat("EEE',' dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).parse(expires);
            Assert.assertTrue(expiryDate.before(new Date()));
        } catch (ParseException e) {
            Assert.fail("Invalid date format for expires header: " + expires);
        }
    }

    private LogoutResponse parseLogoutResponse(SimHttpServletRequest logoutResponseHTTPRequest) throws Exception {
        HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();
        decoder.setParserPool(parserPool);
        decoder.setHttpServletRequest(logoutResponseHTTPRequest);
        decoder.initialize();
        decoder.decode();

        return (LogoutResponse) decoder.getMessageContext().getMessage();
    }

    private LogoutRequest buildIdPLogoutRequest(String sessionIndex, NameID nameID) {
        Issuer issuer = openSAML.buildSAMLObject(Issuer.class);
        issuer.setValue(config.getIdentityProviderEntityID());

        LogoutRequest logoutRequest = openSAML.buildSAMLObject(LogoutRequest.class);
        logoutRequest.setIssuer(issuer);
        logoutRequest.setDestination(config.getSingleLogoutServiceURL());
        logoutRequest.setID(UUIDs.getUnformattedString(UUID.randomUUID()));
        logoutRequest.setIssueInstant(new DateTime());
        logoutRequest.setVersion(SAMLVersion.VERSION_20);
        logoutRequest.setReason(LogoutRequest.GLOBAL_TIMEOUT_REASON);
        logoutRequest.setNotOnOrAfter(new DateTime(System.currentTimeMillis() + 5 * 60 * 1000l));
        SessionIndex indexElement = openSAML.buildSAMLObject(SessionIndex.class);
        indexElement.setSessionIndex(sessionIndex);
        logoutRequest.getSessionIndexes().add(indexElement);
        logoutRequest.setNameID(nameID);

        return logoutRequest;
    }

    private LogoutResponse buildLogoutResponse(LogoutRequest request) throws Exception {
        LogoutResponse response = openSAML.buildSAMLObject(LogoutResponse.class);
        response.setDestination(config.getSingleLogoutServiceURL());
        response.setID(UUIDs.getUnformattedString(UUID.randomUUID()));
        response.setInResponseTo(request.getID());
        response.setIssueInstant(new DateTime());
        response.setVersion(SAMLVersion.VERSION_20);

        Issuer responseIssuer = openSAML.buildSAMLObject(Issuer.class);
        responseIssuer.setValue(config.getIdentityProviderEntityID());
        response.setIssuer(responseIssuer);

        Status status = openSAML.buildSAMLObject(Status.class);
        StatusCode statusCode = openSAML.buildSAMLObject(StatusCode.class);
        statusCode.setValue(StatusCode.SUCCESS);
        status.setStatusCode(statusCode);
        response.setStatus(status);

        Credential signingCredential = testCredentials.getIDPSigningCredential();
        Signature responseSignature = openSAML.buildSAMLObject(Signature.class);
        responseSignature.setSigningCredential(signingCredential);
        SecurityHelperUtils.prepareSignatureParams(responseSignature, signingCredential);
        response.setSignature(responseSignature);
        openSAML.marshall(response); // marshalling is necessary for subsequent signing
        Signer.signObject(responseSignature);

        return response;
    }

    private LogoutResponse buildUnsignedLogoutResponse(LogoutRequest request) {
        LogoutResponse response = openSAML.buildSAMLObject(LogoutResponse.class);
        response.setDestination(config.getSingleLogoutServiceURL());
        response.setID(UUIDs.getUnformattedString(UUID.randomUUID()));
        response.setInResponseTo(request.getID());
        response.setIssueInstant(new DateTime());
        response.setVersion(SAMLVersion.VERSION_20);

        Issuer responseIssuer = openSAML.buildSAMLObject(Issuer.class);
        responseIssuer.setValue(config.getIdentityProviderEntityID());
        response.setIssuer(responseIssuer);

        Status status = openSAML.buildSAMLObject(Status.class);
        StatusCode statusCode = openSAML.buildSAMLObject(StatusCode.class);
        statusCode.setValue(StatusCode.SUCCESS);
        status.setStatusCode(statusCode);
        response.setStatus(status);

        return response;
    }

    private AuthnRequest parseAuthnRequest(HttpServletRequest httpRequest) throws Exception {
        HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();
        decoder.setParserPool(parserPool);
        decoder.setHttpServletRequest(httpRequest);
        decoder.initialize();
        decoder.decode();

        return (AuthnRequest) decoder.getMessageContext().getMessage();
    }

    private AuthnRequest prepareAuthnRequest() {
        AuthnRequest authnRequest = openSAML.buildSAMLObject(AuthnRequest.class);

        /*
         * The <Issuer> element MUST be present and MUST contain the unique identifier of the requesting service provider; the Format
         * attribute MUST be omitted or have a value of urn:oasis:names:tc:SAML:2.0:nameid-format:entity. [profiles 06 - 4.1.4.1p19]
         */
        Issuer issuer = openSAML.buildSAMLObject(Issuer.class);
        issuer.setValue(config.getEntityID());
        authnRequest.setIssuer(issuer);

        authnRequest.setProviderName(config.getProviderName());
        authnRequest.setVersion(SAMLVersion.VERSION_20);
        authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        authnRequest.setAssertionConsumerServiceURL(config.getAssertionConsumerServiceURL());
        authnRequest.setDestination(config.getIdentityProviderAuthnURL());
        authnRequest.setIsPassive(Boolean.FALSE);
        authnRequest.setForceAuthn(Boolean.FALSE);
        authnRequest.setID(UUIDs.getUnformattedString(UUID.randomUUID()));
        authnRequest.setIssueInstant(new DateTime());
        return authnRequest;
    }

    private LogoutRequest parseLogoutRequest(HttpServletRequest httpRequest) throws Exception {
        HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();
        decoder.setParserPool(parserPool);
        decoder.setHttpServletRequest(httpRequest);
        decoder.initialize();
        decoder.decode();

        return (LogoutRequest) decoder.getMessageContext().getMessage();
    }

    private static SimHttpServletRequest prepareHTTPRequest(String method, URI location) {
        SimHttpServletRequest request = new SimHttpServletRequest();
        request.setRequestURI(location.getRawPath());
        request.setRequestURL(location.getScheme() + "://" + location.getHost() + location.getPath());
        request.setMethod(method);
        request.setScheme(location.getScheme());
        request.setSecure("https".equals(location.getScheme()));
        request.setServerName(location.getHost());
        request.setQueryString(location.getRawQuery());
        request.setCookies(Collections.<Cookie> emptyList());
        request.setRemoteAddr("127.0.0.1");
        Map<String, String> params = parseURIQuery(location);
        for (String name : params.keySet()) {
            request.setParameter(name, params.get(name));
        }
        return request;
    }

    private static Map<String, String> parseURIQuery(URI uri) {
        Map<String, String> map = new HashMap<>();
        List<NameValuePair> pairs = URLEncodedUtils.parse(uri, Charset.forName("UTF-8"));
        for (NameValuePair pair : pairs) {
            map.put(pair.getName(), pair.getValue());
        }
        return map;
    }

    private Response buildResponse(AuthnRequest request) throws Exception {
        return buildResponse(request, true);
    }

    private Response buildResponse(AuthnRequest request, boolean encrypted) throws Exception {
        String requestID = request.getID();
        Response response = openSAML.buildSAMLObject(Response.class);
        response.setDestination(config.getAssertionConsumerServiceURL());
        response.setID(UUIDs.getUnformattedString(UUID.randomUUID()));
        response.setInResponseTo(requestID);
        response.setIssueInstant(new DateTime());
        response.setVersion(SAMLVersion.VERSION_20);

        Issuer responseIssuer = openSAML.buildSAMLObject(Issuer.class);
        responseIssuer.setValue(config.getIdentityProviderEntityID());
        response.setIssuer(responseIssuer);

        Status status = openSAML.buildSAMLObject(Status.class);
        StatusCode statusCode = openSAML.buildSAMLObject(StatusCode.class);
        statusCode.setValue(StatusCode.SUCCESS);
        status.setStatusCode(statusCode);
        response.setStatus(status);

        Assertion assertion = openSAML.buildSAMLObject(Assertion.class);
        assertion.setID(UUIDs.getUnformattedString(UUID.randomUUID()));
        assertion.setIssueInstant(new DateTime());

        Issuer assertionIssuer = openSAML.buildSAMLObject(Issuer.class);
        assertionIssuer.setValue(config.getIdentityProviderEntityID());
        assertion.setIssuer(assertionIssuer);

        Subject subject = openSAML.buildSAMLObject(Subject.class);
        NameID nameID = openSAML.buildSAMLObject(NameID.class);
        nameID.setFormat(NameIDType.TRANSIENT);
        nameID.setValue(UUIDs.getUnformattedString(UUID.randomUUID()));
        subject.setNameID(nameID);

        SubjectConfirmation subjectConfirmation = openSAML.buildSAMLObject(SubjectConfirmation.class);
        subjectConfirmation.setMethod("urn:oasis:names:tc:SAML:2.0:cm:bearer");
        SubjectConfirmationData subjectConfirmationData = openSAML.buildSAMLObject(SubjectConfirmationData.class);
        subjectConfirmationData.setAddress("10.20.30.1");
        subjectConfirmationData.setInResponseTo(requestID);
        subjectConfirmationData.setNotOnOrAfter(new DateTime(System.currentTimeMillis() + 60 * 60 * 1000));
        subjectConfirmationData.setRecipient(config.getAssertionConsumerServiceURL());
        subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);
        subject.getSubjectConfirmations().add(subjectConfirmation);
        assertion.setSubject(subject);

        Conditions conditions = openSAML.buildSAMLObject(Conditions.class);
        conditions.setNotBefore(new DateTime(System.currentTimeMillis() - 60 * 1000));
        conditions.setNotOnOrAfter(new DateTime(System.currentTimeMillis() + 60 * 60 * 1000));
        AudienceRestriction audienceRestriction = openSAML.buildSAMLObject(AudienceRestriction.class);
        Audience audience = openSAML.buildSAMLObject(Audience.class);
        audience.setAudienceURI(config.getEntityID());
        audienceRestriction.getAudiences().add(audience);
        conditions.getAudienceRestrictions().add(audienceRestriction);
        assertion.setConditions(conditions);

        AuthnStatement authnStatement = openSAML.buildSAMLObject(AuthnStatement.class);
        authnStatement.setAuthnInstant(new DateTime(System.currentTimeMillis() - 60 * 1000));
        authnStatement.setSessionIndex(UUIDs.getUnformattedString(UUID.randomUUID()));
        AuthnContext authnContext = openSAML.buildSAMLObject(AuthnContext.class);
        AuthnContextClassRef authnContextClassRef = openSAML.buildSAMLObject(AuthnContextClassRef.class);
        authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
        authnContext.setAuthnContextClassRef(authnContextClassRef);
        authnStatement.setAuthnContext(authnContext);
        assertion.getAuthnStatements().add(authnStatement);

        AttributeStatement attributeStatement = openSAML.buildSAMLObject(AttributeStatement.class);
        Attribute attribute = openSAML.buildSAMLObject(Attribute.class);
        attribute.setFriendlyName("userID");
        attribute.setName("urn:open-xchange:saml:userID");
        XSString attributeValue = (XSString) openSAML.getBuilderFactory().getBuilder(XSString.TYPE_NAME).buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
        attributeValue.setValue("oxuser1");
        attribute.getAttributeValues().add(attributeValue);
        attributeStatement.getAttributes().add(attribute);
        assertion.getAttributeStatements().add(attributeStatement);

        Credential signingCredential = testCredentials.getIDPSigningCredential();
        Signature assertionSignature = openSAML.buildSAMLObject(Signature.class);
        assertionSignature.setSigningCredential(signingCredential);
        SecurityHelperUtils.prepareSignatureParams(assertionSignature, signingCredential);
        assertion.setSignature(assertionSignature);
        openSAML.marshall(assertion); // marshalling is necessary for subsequent signing
        Signer.signObject(assertionSignature);

        if (encrypted) {
            EncryptedAssertion encryptedAssertion = getEncrypter().encrypt(assertion);
            response.getEncryptedAssertions().add(encryptedAssertion);
        } else {
            response.getAssertions().add(assertion);
        }

        Signature responseSignature = openSAML.buildSAMLObject(Signature.class);
        responseSignature.setSigningCredential(signingCredential);
        SecurityHelperUtils.prepareSignatureParams(responseSignature, signingCredential);
        response.setSignature(responseSignature);
        openSAML.marshall(response); // marshalling is necessary for subsequent signing
        Signer.signObject(responseSignature);

        return response;
    }

    private Response buildResponseWithoutInResponseTo() throws Exception {
        Response response = openSAML.buildSAMLObject(Response.class);
        response.setDestination(config.getAssertionConsumerServiceURL());
        response.setID(UUIDs.getUnformattedString(UUID.randomUUID()));
        response.setIssueInstant(new DateTime());
        response.setVersion(SAMLVersion.VERSION_20);

        Issuer responseIssuer = openSAML.buildSAMLObject(Issuer.class);
        responseIssuer.setValue(config.getIdentityProviderEntityID());
        response.setIssuer(responseIssuer);

        Status status = openSAML.buildSAMLObject(Status.class);
        StatusCode statusCode = openSAML.buildSAMLObject(StatusCode.class);
        statusCode.setValue(StatusCode.SUCCESS);
        status.setStatusCode(statusCode);
        response.setStatus(status);

        Assertion assertion = openSAML.buildSAMLObject(Assertion.class);
        assertion.setID(UUIDs.getUnformattedString(UUID.randomUUID()));
        assertion.setIssueInstant(new DateTime());

        Issuer assertionIssuer = openSAML.buildSAMLObject(Issuer.class);
        assertionIssuer.setValue(config.getIdentityProviderEntityID());
        assertion.setIssuer(assertionIssuer);

        Subject subject = openSAML.buildSAMLObject(Subject.class);
        NameID nameID = openSAML.buildSAMLObject(NameID.class);
        nameID.setFormat(NameIDType.TRANSIENT);
        nameID.setValue(UUIDs.getUnformattedString(UUID.randomUUID()));
        subject.setNameID(nameID);

        SubjectConfirmation subjectConfirmation = openSAML.buildSAMLObject(SubjectConfirmation.class);
        subjectConfirmation.setMethod("urn:oasis:names:tc:SAML:2.0:cm:bearer");
        SubjectConfirmationData subjectConfirmationData = openSAML.buildSAMLObject(SubjectConfirmationData.class);
        subjectConfirmationData.setAddress("10.20.30.1");
        subjectConfirmationData.setNotOnOrAfter(new DateTime(System.currentTimeMillis() + 60 * 60 * 1000));
        subjectConfirmationData.setRecipient(config.getAssertionConsumerServiceURL());
        subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);
        subject.getSubjectConfirmations().add(subjectConfirmation);
        assertion.setSubject(subject);

        Conditions conditions = openSAML.buildSAMLObject(Conditions.class);
        conditions.setNotBefore(new DateTime(System.currentTimeMillis() - 60 * 1000));
        conditions.setNotOnOrAfter(new DateTime(System.currentTimeMillis() + 60 * 60 * 1000));
        AudienceRestriction audienceRestriction = openSAML.buildSAMLObject(AudienceRestriction.class);
        Audience audience = openSAML.buildSAMLObject(Audience.class);
        audience.setAudienceURI(config.getEntityID());
        audienceRestriction.getAudiences().add(audience);
        conditions.getAudienceRestrictions().add(audienceRestriction);
        assertion.setConditions(conditions);

        AuthnStatement authnStatement = openSAML.buildSAMLObject(AuthnStatement.class);
        authnStatement.setAuthnInstant(new DateTime(System.currentTimeMillis() - 60 * 1000));
        authnStatement.setSessionIndex(UUIDs.getUnformattedString(UUID.randomUUID()));
        AuthnContext authnContext = openSAML.buildSAMLObject(AuthnContext.class);
        AuthnContextClassRef authnContextClassRef = openSAML.buildSAMLObject(AuthnContextClassRef.class);
        authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
        authnContext.setAuthnContextClassRef(authnContextClassRef);
        authnStatement.setAuthnContext(authnContext);
        assertion.getAuthnStatements().add(authnStatement);

        AttributeStatement attributeStatement = openSAML.buildSAMLObject(AttributeStatement.class);
        Attribute attribute = openSAML.buildSAMLObject(Attribute.class);
        attribute.setFriendlyName("userID");
        attribute.setName("urn:open-xchange:saml:userID");
        XSString attributeValue = (XSString) openSAML.getBuilderFactory().getBuilder(XSString.TYPE_NAME).buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
        attributeValue.setValue("oxuser1");
        attribute.getAttributeValues().add(attributeValue);
        attributeStatement.getAttributes().add(attribute);
        assertion.getAttributeStatements().add(attributeStatement);

        Credential signingCredential = testCredentials.getIDPSigningCredential();
        Signature assertionSignature = openSAML.buildSAMLObject(Signature.class);
        assertionSignature.setSigningCredential(signingCredential);
        SecurityHelperUtils.prepareSignatureParams(assertionSignature, signingCredential);
        assertion.setSignature(assertionSignature);
        openSAML.marshall(assertion); // marshalling is necessary for subsequent signing
        Signer.signObject(assertionSignature);

        EncryptedAssertion encryptedAssertion = getEncrypter().encrypt(assertion);
        response.getEncryptedAssertions().add(encryptedAssertion);

        Signature responseSignature = openSAML.buildSAMLObject(Signature.class);
        responseSignature.setSigningCredential(signingCredential);
        SecurityHelperUtils.prepareSignatureParams(responseSignature, signingCredential);
        response.setSignature(responseSignature);
        openSAML.marshall(response); // marshalling is necessary for subsequent signing
        Signer.signObject(responseSignature);

        return response;
    }

    private String marshall(StatusResponseType response) throws MarshallingException {
        // Never ever use the prettyPrint method! The resulting XML will differ slightly and signature validation will fail!
        return SerializeSupport.nodeToString(openSAML.getMarshallerFactory().getMarshaller(response).marshall(response));
    }

    private Encrypter getEncrypter() throws Exception {
        // https://wiki.shibboleth.net/confluence/display/OpenSAML/OSTwoUserManJavaXMLEncryption
        Credential keyEncryptionCredential = testCredentials.getEncryptionCredential();

        DataEncryptionParameters encParams = new DataEncryptionParameters();
        encParams.setAlgorithm(EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES128);

        KeyEncryptionParameters kekParams = new KeyEncryptionParameters();
        kekParams.setEncryptionCredential(keyEncryptionCredential);
        kekParams.setAlgorithm(EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP);
        KeyInfoGenerator keyInfoGenerator = KeyInfoSupport.getKeyInfoGenerator(keyEncryptionCredential, new NamedKeyInfoGeneratorManager(), null);
        kekParams.setKeyInfoGenerator(keyInfoGenerator);

        Encrypter samlEncrypter = new Encrypter(encParams, kekParams);
        samlEncrypter.setKeyPlacement(KeyPlacement.PEER);
        return samlEncrypter;

    }

    private static final class SimServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream responseStream = new ByteArrayOutputStream();

        private SimServletOutputStream() {
            super();
        }

        @Override
        public void write(int b) throws IOException {
            responseStream.write(b);
        }

        public void reset() {
            responseStream.reset();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // Nope...
        }

    }
}
