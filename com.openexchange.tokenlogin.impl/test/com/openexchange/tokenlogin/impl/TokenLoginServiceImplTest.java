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

package com.openexchange.tokenlogin.impl;

import static com.openexchange.java.Autoboxing.I;
import static org.mockito.ArgumentMatchers.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.config.ConfigurationService;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;
import com.openexchange.session.ObfuscatorService;
import com.openexchange.session.Session;
import com.openexchange.sessiond.AddSessionParameter;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.test.mock.MockUtils;
import com.openexchange.tokenlogin.DefaultTokenLoginSecret;
import com.openexchange.tokenlogin.TokenLoginSecret;

/**
 * Unit tests for {@link TokenLoginServiceImpl}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.4
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Services.class })
public class TokenLoginServiceImplTest {

    /**
     * Instance to test
     */
    private TokenLoginServiceImpl tokenLoginServiceImpl;

    /**
     * Idle time required for the constructor
     */
    private final int maxIdleTime = 1000;

    /**
     * Fake token for the tests
     */
    private final String token = "8a07c5a2e4974a75ae70bd9a36198f03_obf-067e61623b6f4ae2a1712470b63dff00";

    /**
     * Mock of the {@link ConfigurationService}
     */
    @Mock
    private ConfigurationService configService;

    /**
     * Mock of the {@link Session}
     */
    @Mock
    private Session session;

    /**
     * Mock of the {@link ContextService}
     */
    @Mock
    private ContextService contextService;

    /**
     * Mock of the {@link SessiondService}
     */
    @Mock
    private SessiondService sessiondService;

    @Mock
    private ObfuscatorService obfuscatorService;

    /**
     * A temporary folder that could be used by each mock.
     */
    @Rule
    protected TemporaryFolder folder = new TemporaryFolder();

    /**
     * Secret token to check
     */
    private static String SECRET_TOKEN = "1234-56789-98765-4321";

    /**
     * Content of the secret file
     */
    private final String secretFileContent = "#\n# Listing of known Web Application secrets followed by an optional semicolon-separated parameter list\n# e.g. 1254654698621354; accessPasword=true\n# Dummy entry\n" + SECRET_TOKEN + "; accessPassword=true\n";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(Services.class);

        PowerMockito.when(Services.getService(ObfuscatorService.class)).thenReturn(obfuscatorService);

        PowerMockito.when(obfuscatorService.obfuscate(any(String.class))).thenAnswer(i -> i.getArgument(0) + "_obf");
        PowerMockito.when(obfuscatorService.unobfuscate(any(String.class))).thenAnswer(i -> ((String) i.getArgument(0)).substring(0, ((String) i.getArgument(0)).length() - "_obf".length()));

        // BEHAVIOUR
        PowerMockito.when(I(session.getContextId())).thenReturn(I(424242669));
        PowerMockito.when(session.getSessionID()).thenReturn("8a07c5a2e4974a75ae70bd9a36198f03");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_configServiceNull_ThrowException() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, null);
    }

    @Test
    public void testConstructor_parameterProperlyFilled_ReturnServiceImpl() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        Assert.assertNotNull(this.tokenLoginServiceImpl);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAcquireToken_SessionParameterNull_ThrowException() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        this.tokenLoginServiceImpl.acquireToken(null);
    }

    @Test
    public void testAcquireToken_tokenNull_ReturnNewToken() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        String localToken = this.tokenLoginServiceImpl.acquireToken(this.session);

        Mockito.verify(this.session, Mockito.times(1)).getSessionID();
        Assert.assertNotNull(localToken);
    }

    @Test
    public void testAcquireToken_FindToken_ReturnToken() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        MockUtils.injectValueIntoPrivateField(this.tokenLoginServiceImpl, "sessionId2token", createSessionId2Token());

        String localToken = this.tokenLoginServiceImpl.acquireToken(this.session);

        Mockito.verify(this.session, Mockito.times(1)).getSessionID();
        Assert.assertNotNull(localToken);
    }

    @Test(expected = OXException.class)
    public void testRedeemToken_secretIsEmptyOrNotInMap_ThrowsOXException() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService) {

            @Override
            public TokenLoginSecret getTokenLoginSecret(String secret) {
                return null;
            }
        };

        this.tokenLoginServiceImpl.redeemToken(this.token, "appSecret", "optClientId", "optAuthId", "optHash", "optClientIp", "optUserAgent");
    }

    @Test(expected = OXException.class)
    public void testRedeemToken_SessiondServiceNull_ThrowsOXException() throws OXException {
        PowerMockito.when(Services.getService(SessiondService.class)).thenReturn(null);

        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(maxIdleTime, configService) {

            @Override
            public TokenLoginSecret getTokenLoginSecret(String secret) {
                return new DefaultTokenLoginSecret();
            }
        };

        this.tokenLoginServiceImpl.redeemToken(this.token, "appSecret", "optClientId", "optAuthId", "optHash", "optClientIp", "optUserAgent");
    }

    @Test(expected = OXException.class)
    public void testRedeemToken_ContextServiceNull_ThrowsOXException() throws OXException {
        PowerMockito.when(Services.getService(SessiondService.class)).thenReturn(this.sessiondService);
        PowerMockito.when(Services.getService(ContextService.class)).thenReturn(null);

        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService) {

            @Override
            public TokenLoginSecret getTokenLoginSecret(String secret) {
                return new DefaultTokenLoginSecret();
            }
        };

        this.tokenLoginServiceImpl.redeemToken(this.token, "appSecret", "optClientId", "optAuthId", "optHash", "optClientIp", "optUserAgent");
    }

    @Test(expected = OXException.class)
    public void testRedeemToken_NoSessionIdAvailableAndNoSuchToken_ThrowsOXException() throws OXException {
        PowerMockito.when(Services.getService(SessiondService.class)).thenReturn(this.sessiondService);
        PowerMockito.when(Services.getService(ContextService.class)).thenReturn(this.contextService);

        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService) {

            @Override
            public TokenLoginSecret getTokenLoginSecret(String secret) {
                return new DefaultTokenLoginSecret();
            }
        };

        this.tokenLoginServiceImpl.redeemToken(this.token, "appSecret", "optClientId", "optAuthId", "optHash", "optClientIp", "optUserAgent");
    }

    @Test(expected = OXException.class)
    public void testRedeemToken_NotAbleToCreateNewSession_ThrowsOXException() throws OXException {
        PowerMockito.when(Services.getService(SessiondService.class)).thenReturn(this.sessiondService);
        PowerMockito.when(Services.getService(ContextService.class)).thenReturn(this.contextService);

        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService) {

            @Override
            public TokenLoginSecret getTokenLoginSecret(String secret) {
                return new DefaultTokenLoginSecret();
            }
        };

        MockUtils.injectValueIntoPrivateField(this.tokenLoginServiceImpl, "sessionId2token", createSessionId2Token());

        this.tokenLoginServiceImpl.redeemToken(this.token, "appSecret", "optClientId", "optAuthId", "optHash", "optClientIp", "optUserAgent");
    }

    @Test
    public void testRedeemToken_EverythingFine_ReturnSession() throws OXException {
        PowerMockito.when(this.sessiondService.getSession(ArgumentMatchers.anyString())).thenReturn(this.session);
        PowerMockito.when(this.sessiondService.peekSession(ArgumentMatchers.anyString())).thenReturn(this.session);
        PowerMockito.when(this.sessiondService.addSession(ArgumentMatchers.any(AddSessionParameter.class))).thenReturn(this.session);
        PowerMockito.when(Services.getService(SessiondService.class)).thenReturn(this.sessiondService);
        PowerMockito.when(Services.getService(ContextService.class)).thenReturn(this.contextService);

        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService) {

            @Override
            public TokenLoginSecret getTokenLoginSecret(String secret) {
                return new DefaultTokenLoginSecret();
            }
        };

        MockUtils.injectValueIntoPrivateField(this.tokenLoginServiceImpl, "sessionId2token", createSessionId2Token());

        Session returnedSession = this.tokenLoginServiceImpl.redeemToken(this.token, "appSecret", "optClientId", "optAuthId", "optHash", "optClientIp", "optUserAgent");

        Assert.assertNotNull(returnedSession);
        Mockito.verify(sessiondService, Mockito.times(1)).addSession(ArgumentMatchers.any(AddSessionParameter.class));
    }

    @Test
    public void testInitSecrets_SecretFileNull_ReturnEmptyMap() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        Map<String, TokenLoginSecret> secrets = this.tokenLoginServiceImpl.initSecrets(null);

        Assert.assertNotNull(secrets);
        Assert.assertEquals(0, secrets.size());
    }

    @Test
    public void testInitSecrets_CorrectInput_ReturnCorrectSize() throws OXException, IOException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        File file = folder.newFile("tokenlogin-secrets");
        writeStringToFile(file, secretFileContent, Charset.defaultCharset(), true);

        Map<String, TokenLoginSecret> initSecrets = this.tokenLoginServiceImpl.initSecrets(file);

        Assert.assertEquals(1, initSecrets.size());
    }

    @Test
    public void testInitSecrets_CorrectInput_ReturnCorrectContent() throws OXException, IOException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        File file = folder.newFile("tokenlogin-secrets");
        writeStringToFile(file, secretFileContent, Charset.defaultCharset(), true);

        Map<String, TokenLoginSecret> initSecrets = this.tokenLoginServiceImpl.initSecrets(file);

        Assert.assertNotNull(initSecrets.get(SECRET_TOKEN));
        Assert.assertEquals(SECRET_TOKEN, initSecrets.get(SECRET_TOKEN).getSecret());
    }

    @Test
    public void testInitSecrets_CorrectInput_ReturnAccessPasswordTrue() throws OXException, IOException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        File file = folder.newFile("tokenlogin-secrets");
        writeStringToFile(file, secretFileContent, Charset.defaultCharset(), true);

        Map<String, TokenLoginSecret> initSecrets = this.tokenLoginServiceImpl.initSecrets(file);

        Assert.assertEquals(Boolean.TRUE, initSecrets.get(SECRET_TOKEN).getParameters().get("accessPassword"));
    }

    @Test
    public void testInitSecrets_TextInput_ReturnTextInputPasswordFalse() throws OXException, IOException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        String secret = "this should not be returned as correct";
        File file = folder.newFile("tokenlogin-secrets");
        writeStringToFile(file, secret, Charset.defaultCharset(), true);

        Map<String, TokenLoginSecret> initSecrets = this.tokenLoginServiceImpl.initSecrets(file);

        Assert.assertEquals(1, initSecrets.size());
        Assert.assertEquals(secret, initSecrets.get(secret).getSecret());
    }

    @Test
    public void testInitSecrets_TextInput_ReturnTextInputPasswordTrue() throws OXException, IOException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        String secret = "this should not be returned as correct";
        String password = "; accessPassword=true";
        File file = folder.newFile("tokenlogin-secrets");
        writeStringToFile(file, secret + password, Charset.defaultCharset(), true);

        Map<String, TokenLoginSecret> initSecrets = this.tokenLoginServiceImpl.initSecrets(file);

        Assert.assertEquals(Boolean.TRUE, initSecrets.get(secret).getParameters().get("accessPassword"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveTokenFor_SessionNull_ThrowException() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);

        this.tokenLoginServiceImpl.removeTokenFor(null);
    }

    @Test
    public void testRemoveTokenFor_TokenNotAvailadfdble_Return() throws OXException {
        this.tokenLoginServiceImpl = new TokenLoginServiceImpl(this.maxIdleTime, this.configService);
        MockUtils.injectValueIntoPrivateField(this.tokenLoginServiceImpl, "sessionId2token", createSessionId2Token());

        this.tokenLoginServiceImpl.removeTokenFor(this.session);

        Cache<String, String> token2sessionMap = (Cache<String, String>) MockUtils.getValueFromField(this.tokenLoginServiceImpl, "sessionId2token");
        Assert.assertNotNull(token2sessionMap);
        Assert.assertEquals(0, token2sessionMap.size());
    }

    private Cache<String, String> buildCache() {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().concurrencyLevel(4).maximumSize(Integer.MAX_VALUE).initialCapacity(1024).expireAfterAccess(maxIdleTime, TimeUnit.MILLISECONDS);
        Cache<String, String> cache = builder.build();
        return cache;
    }

    /**
     * Creates the session id-token map
     *
     * @return {@link ConcurrentMap<String, String>} with the desired mapping
     */
    private Cache<String, String> createSessionId2Token() {
        Cache<String, String> sessionId2Token = buildCache();
        sessionId2Token.put("8a07c5a2e4974a75ae70bd9a36198f03", this.token);

        return sessionId2Token;
    }

    /**
     * Writes a String to a file creating the file if it does not exist.
     */
    private static void writeStringToFile(File file, String data, Charset encoding, boolean append) throws IOException {
        try (OutputStream out = openOutputStream(file, append)) {
            write(data, out, encoding);
            out.close(); // don't swallow close Exception if copy completes normally
        }
    }

    /**
     * Writes chars from a <code>String</code> to bytes on an
     * <code>OutputStream</code> using the specified character encoding.
     */
    private static void write(String data, OutputStream output, Charset encoding) throws IOException {
        if (data == null) {
            return;
        }
        if (encoding == null) {
            IOUtils.write(data, output, Charsets.UTF_8);
        } else {
            output.write(data.getBytes(encoding));
        }
    }

    /**
     * Opens a {@link FileOutputStream} for the specified file, checking and
     * creating the parent directory if it does not exist.
     * <p>
     * At the end of the method either the stream will be successfully opened,
     * or an exception will have been thrown.
     * <p>
     * The parent directory will be created if it does not exist.
     * The file will be created if it does not exist.
     * An exception is thrown if the file object exists but is a directory.
     * An exception is thrown if the file exists but cannot be written to.
     * An exception is thrown if the parent directory cannot be created.
     *
     * @param append
     */
    private static FileOutputStream openOutputStream(File file, boolean append) throws IOException {
        if (file.exists()) {
            if (file.isDirectory()) {
                throw new IOException("File '" + file + "' exists but is a directory");
            }
            if (file.canWrite() == false) {
                throw new IOException("File '" + file + "' cannot be written to");
            }
        } else {
            File parent = file.getParentFile();
            if (parent != null && parent.exists() == false) {
                if (parent.mkdirs() == false) {
                    throw new IOException("File '" + file + "' could not be created");
                }
            }
        }
        return new FileOutputStream(file, append);
    }

}
