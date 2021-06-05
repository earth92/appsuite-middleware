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

package com.openexchange.file.storage.webdav;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.protocol.HttpContext;
import com.openexchange.annotation.NonNull;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.CapabilityAware;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageCapability;
import com.openexchange.file.storage.FileStorageCapabilityTools;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageService;
import com.openexchange.file.storage.webdav.utils.WebDAVEndpointConfig;
import com.openexchange.java.Strings;
import com.openexchange.session.Session;
import com.openexchange.webdav.client.BearerSchemeFactory;
import com.openexchange.webdav.client.WebDAVClient;

/**
 * {@link AbstractWebDAVAccountAccess}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since 7.10.4
 */
public abstract class AbstractWebDAVAccountAccess implements CapabilityAware {

    protected final FileStorageAccount account;
    protected final Session session;
    private final AbstractWebDAVFileStorageService service;

    private volatile WebDAVClient webdavClient;

    /**
     * Initializes a new {@link AbstractWebDAVAccountAccess}.
     *
     * @param service The {@link FileStorageService}
     * @param account The {@link FileStorageAccount}
     * @param session The {@link Session}
     */
    protected AbstractWebDAVAccountAccess(AbstractWebDAVFileStorageService service, FileStorageAccount account, Session session) {
        super();
        this.service = service;
        this.account = account;
        this.session = session;
    }

    @Override
    public Boolean supports(FileStorageCapability capability) {
        Boolean supportedByAbstractClass;
        if (capability.isFileAccessCapability()) {
            supportedByAbstractClass = FileStorageCapabilityTools.supportsByClass(AbstractWebDAVFileAccess.class, capability);
        } else {
            supportedByAbstractClass = FileStorageCapabilityTools.supportsFolderCapabilityByClass(AbstractWebDAVFolderAccess.class, capability);
        }
        return Boolean.TRUE.equals(supportedByAbstractClass) ? Boolean.TRUE : null;
    }

    /**
     * Gets the associated account
     *
     * @return The account
     */
    public FileStorageAccount getAccount() {
        return account;
    }

    public Session getSession() {
        return session;
    }

    @Override
    public void connect() throws OXException {
        Map<String, Object> configuration = account.getConfiguration();
        String configUrl = (String) configuration.get(WebDAVFileStorageConstants.WEBDAV_URL);
        if (Strings.isEmpty(configUrl)) {
            throw FileStorageExceptionCodes.INVALID_URL.create("not provided", "empty");
        }
        WebDAVEndpointConfig config = new WebDAVEndpointConfig.Builder(session, getWebDAVFileStorageService(), configUrl).build();
        HttpContext context = getContextByAuthScheme(configuration, config.getUrl());

        try {
            webdavClient = getWebDAVFileStorageService().getClientFactory().create(getSession(), getAccountId(), new URI(config.getUrl()), optHttpClientId(), context);
        } catch (URISyntaxException e) {
            throw FileStorageExceptionCodes.INVALID_URL.create(configUrl, e.getMessage());
        }
    }

    /**
     * Creates an {@link HttpClientContext} for bearer authentication
     *
     * @param configuration The configuration
     * @return The created {@link HttpClientContext}
     */
    protected @NonNull HttpContext setupBearerContext(Map<String, Object> configuration) {
        String oauth = (String) configuration.get("oauth");
        HttpClientContext context = HttpClientContext.create();
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(oauth, null));

        context.setCredentialsProvider(credsProvider);
        context.setAuthSchemeRegistry(RegistryBuilder.<AuthSchemeProvider> create()
            .register("Bearer", new BearerSchemeFactory())
            .build());
        context.setRequestConfig(RequestConfig.custom()
            .setTargetPreferredAuthSchemes(Collections.unmodifiableList(Arrays.asList(
                "Bearer")))
            .build());
        return context;
    }

    /**
     * Gets the {@link HttpContext} based on the configured auth scheme
     *
     * @param configuration The configuration
     * @param host The host uri
     * @return The {@link HttpContext}
     * @throws OXException in case the host uri is invalid
     */
    protected HttpContext getContextByAuthScheme(Map<String, Object> configuration, String host) throws OXException {
        // Check which authentication mechanism should be used
        WebDAVAuthScheme authScheme = WebDAVAuthScheme.getByName(configuration.getOrDefault("authScheme", "Basic").toString());
        switch (authScheme) {
            case BEARER:
                return setupBearerContext(configuration);
            case BASIC:
            default:
                return setupBasicAuthContext(configuration, host);
        }
    }

    /**
     * Creates an {@link HttpClientContext} for basic auth authentication
     *
     * @param configuration The configuration
     * @param host The host
     * @return The created {@link HttpClientContext}
     * @throws OXException in case the host uri is invalid
     */
    protected @NonNull HttpClientContext setupBasicAuthContext(Map<String, Object> configuration, String host) throws OXException {
        String login = (String) configuration.get("login");
        String password = (String) configuration.get("password");

        if (Strings.isEmpty(login) || Strings.isEmpty(password)) {
            throw FileStorageExceptionCodes.MISSING_CONFIG.create(getService().getId(), getAccountId());
        }

        try {
            URI uri = new URI(host);
            HttpHost targetHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(login, password));

            AuthCache authCache = new BasicAuthCache();
            authCache.put(targetHost, new BasicScheme());

            // Add AuthCache to the execution context
            HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credsProvider);
            context.setAuthCache(authCache);
            return context;
        } catch (URISyntaxException e) {
            throw FileStorageExceptionCodes.INVALID_URL.create(host, e.getMessage(), e);
        }
    }

    @Override
    public boolean isConnected() {
        return null != webdavClient;
    }

    @Override
    public void close() {
        webdavClient = null;
    }

    @Override
    public boolean ping() throws OXException {
        try {
            connect();
            WebDAVFolder rootFolder = getFolderAccess().getRootFolder();
            getFolderAccess().getFolder(rootFolder.getId());
            return true;
        } finally {
            close();
        }
    }

    @Override
    public boolean cacheable() {
        return false;
    }

    @Override
    public String getAccountId() {
        return account.getId();
    }

    @Override
    public AbstractWebDAVFileAccess getFileAccess() throws OXException {
        if (null == webdavClient) {
            throw FileStorageExceptionCodes.NOT_CONNECTED.create();
        }
        return initWebDAVFileAccess(webdavClient);
    }

    /**
     * Initializes the WebDAV file access.
     *
     * @param webdavClient The {@link WebDAVClient}
     * @return The WebDAV file access
     * @throws OXException If WebDAV file access cannot be initialized
     */
    protected abstract AbstractWebDAVFileAccess initWebDAVFileAccess(WebDAVClient webdavClient) throws OXException;

    @Override
    public AbstractWebDAVFolderAccess getFolderAccess() throws OXException {
        if (null == webdavClient) {
            throw FileStorageExceptionCodes.NOT_CONNECTED.create();
        }
        return initWebDAVFolderAccess(webdavClient);
    }

    /**
     * Initializes the WebDAV folder access.
     *
     * @param webdavClient The {@link WebDAVClient}
     * @return The WebDAV folder access
     * @throws OXException If WebDAV folder access cannot be initialized
     */
    protected abstract AbstractWebDAVFolderAccess initWebDAVFolderAccess(WebDAVClient webdavClient) throws OXException;

    @Override
    public FileStorageFolder getRootFolder() throws OXException {
        connect();
        return getFolderAccess().getRootFolder();
    }

    @Override
    public FileStorageService getService() {
        return getWebDAVFileStorageService();
    }

    /**
     * Gets the optional client identifier for the http client.
     * If none is provided, then the default "webdav" is used instead.
     *
     * @return The optional http client identifier
     */
    protected Optional<String> optHttpClientId() {
        return Optional.empty();
    }

    /**
     * Gets the service
     *
     * @return The service
     */
    public AbstractWebDAVFileStorageService getWebDAVFileStorageService() {
        return service;
    }
}
