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

package com.openexchange.api.client.impl;

import static com.openexchange.api.client.ApiCall.SESSION;
import static com.openexchange.api.client.common.OXExceptionParser.matches;
import static com.openexchange.api.client.common.OXExceptionParser.parseException;
import static com.openexchange.api.client.impl.osgi.ApiClientConfigConfigProvider.HTTP_CLIENT_IDENTIFIER;
import static com.openexchange.java.Autoboxing.I;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.TEXT_HTML;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.api.client.ApiCall;
import com.openexchange.api.client.ApiClient;
import com.openexchange.api.client.ApiClientExceptions;
import com.openexchange.api.client.HttpResponseParser;
import com.openexchange.api.client.InputStreamAwareResponse;
import com.openexchange.api.client.LoginInformation;
import com.openexchange.api.client.common.ApiClientUtils;
import com.openexchange.api.client.common.Checks;
import com.openexchange.api.client.common.JSONUtils;
import com.openexchange.api.client.common.ResourceReleasingInputStream;
import com.openexchange.api.client.common.calls.login.LogoutCall;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.rest.client.httpclient.HttpClients;
import com.openexchange.rest.client.httpclient.ManagedHttpClient;
import com.openexchange.rest.client.httpclient.util.HttpContextUtils;
import com.openexchange.server.ServiceLookup;
import com.openexchange.sessiond.SessionExceptionCodes;

/**
 * {@link AbstractApiClient} - Abstract API client that handles the remote lifecycle of
 * a session on another OX server, besides the login.
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.5
 */
public abstract class AbstractApiClient implements ApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractApiClient.class);

    /** The default maximum number of retry attempts */
    private static final int DEFAULT_RETRIES = 3;

    /** The base number of milliseconds to wait until retrying */
    private static final int RETRY_BASE_DELAY = 500;

    /** The content type for "text/javascript" */
    public static final ContentType TEXT_JAVA_SCRIPT = ContentType.create("text/javascript");

    /** A list of content types which can be buffered to memory */
    public static final List<ContentType> BUFFERED_CONTENT_TYPES = Arrays.asList(TEXT_JAVA_SCRIPT, APPLICATION_JSON, TEXT_HTML);

    protected final ServiceLookup services;

    protected final int contextId;
    protected final int userId;
    protected final String sessionId;
    protected final URL loginLink;

    protected final CookieStore cookieStore;

    private final AtomicBoolean isClosed;

    /**
     * Initializes a new {@link AbstractApiClient}.
     *
     * @param services The service lookup
     * @param contextId The context identifier of this local OX node for logging
     * @param userId The user identifier of this local OX node for logging
     * @param sessionId The session identifier of this local OX node
     * @param loginLink The link to the target to log in into
     */
    public AbstractApiClient(ServiceLookup services, int contextId, int userId, String sessionId, URL loginLink) {
        super();

        this.services = services;
        this.loginLink = loginLink;
        this.contextId = contextId;
        this.userId = userId;
        this.sessionId = sessionId;

        this.cookieStore = HttpContextUtils.createCookieStore();

        this.isClosed = new AtomicBoolean(false);
    }

    @Override
    public int getContextId() {
        return contextId;
    }

    @Override
    public int getUserId() {
        return userId;
    }

    @Override
    public String getSession() {
        return sessionId;
    }

    @Override
    public URL getLoginLink() {
        return loginLink;
    }

    @Override
    public <T> T execute(ApiCall<T> call) throws OXException {
        try {
            return execute(buildRequest(call), call.getParser());
        } catch (OXException e) {
            if (matches(SessionExceptionCodes.SESSION_EXPIRED, e)) {
                /*
                 * Try relogin if session expired. Wrap error if no session can be established again
                 */
                reLogin(e);
                return execute(buildRequest(call), call.getParser());
            }
            throw e;
        }
    }

    @SuppressWarnings({ "resource", "unchecked" })
    private <T> T execute(HttpRequestBase request, HttpResponseParser<T> parser) throws OXException {
        if (isClosed.get()) {
            throw ApiClientExceptions.SESSION_EXPIRED.create();
        }
        Checks.checkSameOrigin(request, loginLink);

        HttpResponse response = null;
        try {
            /*
             * Prepare context and execute the request
             */
            LOGGER.trace("Sending request \"{}\" with cookies \"{}\"", request, cookieStore.toString());
            HttpContext httpContext = prepareContext();
            response = getHttpClient().execute(request, httpContext);

            /*
             * Log response if possible and necessary
             */
            if (canBuffer(response)) {
                response = ensureRepeatability(response);
            }
            log(response, cookieStore);

            final boolean enquedRequest = response.getStatusLine() != null && response.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED;

            /*
             * Check response for exceptions
             */
            OXException oxException = response.getEntity() != null && response.getEntity().isRepeatable() ? getNestedOXException(response) : null;
            if (null != oxException && !enquedRequest) {
                throw oxException;
            }

            /**
             * Check if the request was enqueued on the remote side, because it takes longer.
             */
            if (enquedRequest) {
                //We poll for the request until it's finished
                return reDo(parser, response);
            }

            /*
             * Finally parse object. Wrap input streams so HTTP data is closed after the input stream is consumed
             */
            T ret = parser != null ? parser.parse(response, httpContext) : null;
            if (ret instanceof InputStream) {
                //Do not release resources yet if we return an input stream
                ret = (T) new ResourceReleasingInputStream(request, response);
                request = null;
                response = null;
            } else if (ret instanceof InputStreamAwareResponse) {
                //Do not release resources yet if we return an InputStreamResponse
                final InputStreamAwareResponse inputStreamResponse = (InputStreamAwareResponse) ret;
                if (inputStreamResponse.getInputStream() != null) {
                    inputStreamResponse.setInputStream(new ResourceReleasingInputStream(request, response, inputStreamResponse.getInputStream()));
                    request = null;
                    response = null;
                }
            }
            return ret;
        } catch (IOException e) {
            throw ApiClientExceptions.IO_ERROR.create(e, e.getMessage());
        } finally {
            HttpClients.close(request, response);
        }
    }

    @Override
    public void logout() {
        if (isClosed.getAndSet(true)) {
            // Is already being closed
            return;
        }

        /*
         * Check if there is a session to remove on the remote system
         */
        LoginInformation infos = getLoginInformation();
        if (null == infos || Strings.isEmpty(infos.getRemoteSessionId())) {
            LOGGER.debug("Unable to logout client due missing session ID.");
            return;
        }

        LOGGER.debug("Client is closed. Logging out user {} in context {} on host {}", I(userId), I(contextId), loginLink.getHost());
        doLogout(true);
    }

    /**
     * Performs a logout call.
     * <p>
     * Clears the cookie store
     *
     * @param logOnError If an error should be logged
     */
    protected void doLogout(boolean logOnError) {
        HttpRequestBase request = null;
        HttpResponse response = null;
        try {
            /*
             * Execute the logout request manually to avoid constrains from execute method (cookies, shutdown flag, etc.)
             */
            HttpContext httpContext = prepareContext();
            request = buildRequest(new LogoutCall());
            response = getHttpClient().execute(request, httpContext);
            int status = response.getStatusLine().getStatusCode();
            if (HttpStatus.SC_OK != status && logOnError) {
                OXException oxException = getNestedOXException(response);
                LOGGER.info("Unable to logout user {} in context {} from host {} with error code {}", I(userId), I(contextId), loginLink.getHost(), I(status), oxException);
            }
        } catch (Exception e) {
            if (logOnError) {
                LOGGER.error("Unable to logout client", e);
            }
        } finally {
            HttpClients.close(request, response);
            cookieStore.clear();
        }
    }

    @Override
    public void login() throws OXException {
        if (isClosed.get()) {
            LOGGER.debug("Client is closed, login will not be perfomed.");
            return;
        }

        /*
         * Is already initialized?
         */
        LoginInformation infos = getLoginInformation();
        if (null != infos && Strings.isNotEmpty(infos.getRemoteSessionId())) {
            LOGGER.debug("Client has a session. Won't login again.");
            return;
        }

        /*
         * Finally perform login
         */
        doLogin();
    }

    /**
     * Performs the login
     *
     * @throws OXException In case login is not possible
     */
    protected abstract void doLogin() throws OXException;

    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    /*
     * ---------------------- HELPERS ----------------------
     */

    private ManagedHttpClient getHttpClient() throws OXException {
        return services.getServiceSafe(HttpClientService.class).getHttpClient(HTTP_CLIENT_IDENTIFIER);
    }

    private HttpContext prepareContext() {
        HttpContext httpContext = new BasicHttpContext();
        HttpContextUtils.addCookieStore(httpContext, cookieStore);
        return httpContext;
    }

    /**
     * Builds the request based on the given call
     *
     * @param <T> The class of the response object
     * @param call The call to translate into an HTTP request
     * @return The request
     * @throws OXException In case request can't be build
     */
    private <T> HttpRequestBase buildRequest(ApiCall<T> call) throws OXException {
        /*
         * Build the URI based on the host of the login link, the prefix parsed from the login
         * and the path announced by the call.
         * Add parameters to that URI in one process
         */
        URI uri;
        try {
            URIBuilder builder = new URIBuilder(loginLink.toString());
            builder.setPath(buildPath(call));
            builder.clearParameters();
            for (Entry<String, String> entry : call.getPathParameters().entrySet()) {
                builder.addParameter(entry.getKey(), entry.getValue());
            }
            LoginInformation infos = getLoginInformation();
            if (call.appendSessionToPath() && null != infos) {
                String remoteSessionId = infos.getRemoteSessionId();
                if (Strings.isNotEmpty(remoteSessionId)) {
                    builder.addParameter(SESSION, remoteSessionId);
                }
            }
            builder.addParameter("allow_enqueue", "true");
            uri = builder.build();
        } catch (URISyntaxException e) {
            throw ApiClientExceptions.INVALID_TARGET.create(e, loginLink);
        }

        HttpRequestBase request;
        switch (call.getHttpMehtod()) {
            case DELETE:
                request = new HttpDelete(uri);
                break;
            case GET:
                request = new HttpGet(uri);
                break;
            case PATCH:
                request = new HttpPatch(uri);
                break;
            case PUT:
                request = new HttpPut(uri);
                break;
            case POST:
                request = new HttpPost(uri);
                break;
            case OPTIONS:
                request = new HttpOptions(uri);
                break;
            default:
                throw ApiClientExceptions.UNEXPECTED_ERROR.create("Unkown HTTP method used.");
        }

        /*
         * Set body if necessary
         */
        if (request instanceof HttpEntityEnclosingRequestBase) {
            try {
                HttpEntity body = call.getBody();
                if (null != body) {
                    ((HttpEntityEnclosingRequestBase) request).setEntity(body);
                }
            } catch (JSONException e) {
                throw ApiClientExceptions.JSON_ERROR.create(e.getMessage(), e);
            }
        }

        /*
         * Set HTTP Header
         */
        for (Entry<String, String> entry : call.getHeaders().entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }

        return request;
    }

    /**
     * Builds the path for the request based on the prefix and
     * the actual API path for the original targeted host.
     * <p>
     * If the {@link ApiCall#appendDispatcherPrefix()} is set to
     * <code>false</code> the path is returned as-is.
     *
     * @param call The call to get the information from
     * @return the full qualified path
     */
    private <T> String buildPath(ApiCall<T> call) {
        if (false == call.appendDispatcherPrefix()) {
            String module = call.getModule();
            return '/' == module.charAt(0) ? module : '/' + module;
        }
        String prefix = ApiClientUtils.parseDispatcherPrefix(loginLink.getPath());
        if (call.getModule().startsWith("/")) {
            if (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
        } else {
            if (false == prefix.endsWith("/")) {
                prefix = prefix + "/";
            }
        }
        return prefix + call.getModule();
    }

    /**
     * Checks whether or not the given {@link HttpResponse} can be buffered to memory
     *
     * @param response The {@link HttpResponse}
     * @return True if it can be buffered to memory, false otherwise
     */
    private boolean canBuffer(HttpResponse response) {
        ContentType contentType = null;
        if (response.getEntity() != null && response.getEntity().getContentType() != null) {
            contentType = ContentType.parse(response.getEntity().getContentType().getValue());
        }
        if (contentType != null) {
            for (ContentType t : BUFFERED_CONTENT_TYPES) {
                if (t.getMimeType().contentEquals(contentType.getMimeType())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Ensures that the HTTP entity within the response is repeatable to enable
     * multiple consumption of the response
     *
     * @param response The response
     * @return A response that has a repeatable entity as per {@link HttpEntity#isRepeatable()}
     * @throws OXException In case of an I/O error
     */
    private static HttpResponse ensureRepeatability(HttpResponse response) throws OXException {
        if (response.getEntity().isRepeatable()) {
            return response;
        }
        try {
            BufferedHttpEntity httpEntity = new BufferedHttpEntity(response.getEntity());
            response.setEntity(httpEntity);
            return response;
        } catch (IOException e) {
            throw ApiClientExceptions.IO_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Checks if there is a nested {@link OXException} within the response and returns it
     * <p>
     * Content will be checked if the content type is
     * <li>application/json</li>
     * <li>text/javascript</li>
     * <li>text/html</li>
     * <p>
     * Content will not be checked for other known and used content types of the OX, namely:
     * <li>application/octet-stream</li>
     * <li>application/zip</li>
     *
     * @param response The response to check
     * @return A {@link OXException} In case a exception is found in the response or <code>null</code>
     */
    private static OXException getNestedOXException(HttpResponse response) {
        JSONValue json = JSONUtils.getJSON(response);
        if (null != json && json instanceof JSONObject) {
            try {
                return parseException((JSONObject) json);
            } catch (JSONException e) {
                LOGGER.debug("Unable to parse content", e);
            }
        }
        return null;
    }

    /**
     * Receives the request and response and logs them if the level is set to <code>TRACE</code>
     *
     * @param request The actual request
     * @param response The actual response
     */
    private static void log(HttpResponse response, CookieStore cookieStore) {
        if (false == LOGGER.isTraceEnabled()) {
            return;
        }
        String content = "data stream";
        if (response.getEntity().isRepeatable()) {
            content = ApiClientUtils.getBody(response);
        }

        LOGGER.trace("Received response. Cookies \"{}\".\nResponse content:\n\n{}\n", cookieStore, content, new OXException(999, "TRACE stack trace"));
    }

    /**
     * Tries to login the client again. Will wait a certain time after
     * a login request failed.
     * <p>
     * If the login constantly fails, the client will be closed as per
     * {@link #isClosed}
     *
     * @throws OXException In case a valid session can't be obtained, or the login fails because of another reason then {@link SessionExceptionCodes#SESSION_EXPIRED}
     */
    private synchronized void reLogin(OXException oxException) throws OXException {
        /*
         * Check if client has been closed meanwhile
         */
        if (isClosed.get()) {
            throw ApiClientExceptions.SESSION_EXPIRED.create();
        }
        LoginInformation loginInformation = getLoginInformation();
        if (null == loginInformation || Strings.isEmpty(loginInformation.getRemoteSessionId())) {
            throw ApiClientExceptions.SESSION_EXPIRED.create();
        }
        /*
         * Check if new session has been set meanwhile
         */
        if (null != oxException.getDisplayArgs() && oxException.getDisplayArgs().length > 0) {
            String expiredSessionId = (String) oxException.getDisplayArgs()[0];
            if (Strings.isNotEmpty(expiredSessionId) && false == expiredSessionId.equals(loginInformation.getRemoteSessionId())) {
                return;
            }
        } else {
            LOGGER.debug("Unable to extract expired session ID. Performing re-login to ensure reliable session");
        }

        LOGGER.debug("The session on the remote system {} expired for user {} in context {}. Trying to re-login", loginLink.getHost(), I(userId), I(contextId));
        for (int counter = 0; counter <= DEFAULT_RETRIES; counter++) {
            try {
                doLogout(false);
                doLogin();
                return;
            } catch (OXException e) {
                if (false == matches(ApiClientExceptions.NO_ACCESS, e)) {
                    /*
                     * Login failed due another reason besides session creation
                     */
                    isClosed.set(true);
                    cookieStore.clear();
                    throw e;
                }

                if (counter == DEFAULT_RETRIES) {
                    /*
                     * Login attempts failed. Throw error and close client. Logout doen't need to be performed, as no valid session is available
                     */
                    LOGGER.info("Unable to re-login at host {} for user {} in context {}", loginLink.getHost(), I(userId), I(contextId), e);
                    isClosed.set(true);
                    cookieStore.clear();
                    throw ApiClientExceptions.SESSION_EXPIRED.create(e);
                }
                /*
                 * Try login after a certain amount of time again
                 */
                int delay = RETRY_BASE_DELAY * counter;
                LOGGER.debug("Error during automatic login (\"{}\"), trying again in {}ms ({}/{})...", e.getMessage(), I(delay), I(counter), I(DEFAULT_RETRIES));
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(delay));
            }
        }
    }

    /**
     * "Re-Do" a request which was added to the job-queue on the remote server.
     * <p>
     * This parses the job-id from the given response and polls the job-module until the result is present.
     *
     * @param <T> The type of of the response.
     * @param parser The response parser to use for the actual result
     * @param response The response containing the job-id
     * @return The result of the job; i.e. the result of the original request
     * @throws OXException
     */
    private <T> T reDo(HttpResponseParser<T> parser, HttpResponse response) throws OXException {
        //Parse the job ID from the response body
        final JSONValue json = JSONUtils.getJSON(response);
        if (json instanceof JSONObject) {
            final JSONObject jsonObject = (JSONObject) json;
            if (jsonObject.hasAndNotNull("data")) {
                try {
                    final JSONObject data = jsonObject.getJSONObject("data");
                    if (data.hasAndNotNull("job")) {
                        String jobId = data.getString("job");

                        //Try to get the result again by using the parsed job ID
                        return execute(new com.openexchange.api.client.common.calls.jobs.GetCall<T>(jobId, parser));
                    }
                } catch (JSONException e) {
                    throw ApiClientExceptions.JSON_ERROR.create(e, e.getMessage());
                }
            }
        }
        throw ApiClientExceptions.UNEXPECTED_ERROR.create("Unexpected JSON response with status code 202.");
    }
}
