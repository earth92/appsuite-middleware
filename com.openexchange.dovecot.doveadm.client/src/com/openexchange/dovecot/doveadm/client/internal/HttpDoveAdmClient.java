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

package com.openexchange.dovecot.doveadm.client.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONInputStream;
import org.json.JSONObject;
import org.json.JSONValue;
import org.slf4j.Logger;
import com.google.common.io.BaseEncoding;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.dovecot.doveadm.client.DoveAdmClient;
import com.openexchange.dovecot.doveadm.client.DoveAdmClientExceptionCodes;
import com.openexchange.dovecot.doveadm.client.DoveAdmCommand;
import com.openexchange.dovecot.doveadm.client.DoveAdmResponse;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.rest.client.endpointpool.Endpoint;
import com.openexchange.rest.client.endpointpool.EndpointManager;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.rest.client.httpclient.HttpClients;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;

/**
 * {@link HttpDoveAdmClient} - The REST client for DoveAdm interface.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v1.0.0
 */
public class HttpDoveAdmClient implements DoveAdmClient {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(HttpDoveAdmClient.class);

    // -------------------------------------------------------------------------------------------------------------- //

    /** The status code policy to obey */
    public static interface StatusCodePolicy {

        /**
         * Examines given status line
         *
         * @param httpResponse The HTTP response
         * @throws OXException If an Open-Xchange error is yielded from status
         * @throws HttpResponseException If status is interpreted as an error
         */
        void handleStatusCode(HttpResponse httpResponse, StringBuilder traceBuilder) throws OXException, HttpResponseException;
    }

    /** The default status code policy; accepting greater than/equal to <code>200</code> and lower than <code>300</code> */
    public static final StatusCodePolicy STATUS_CODE_POLICY_DEFAULT = new StatusCodePolicy() {

        @Override
        public void handleStatusCode(HttpResponse httpResponse, StringBuilder traceBuilder) throws OXException, HttpResponseException {
            final StatusLine statusLine = httpResponse.getStatusLine();
            final int statusCode = statusLine.getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String body = null;
                if (null != traceBuilder) {
                    try {
                        body = Streams.reader2string(new InputStreamReader(httpResponse.getEntity().getContent(), Charsets.UTF_8));
                        traceBuilder.append(body);
                    } catch (Exception x) {
                        // ignore
                    }
                }

                if (404 == statusCode) {
                    throw DoveAdmClientExceptionCodes.NOT_FOUND_SIMPLE.create();
                }
                String reason;
                try {
                    JSONObject jsonObject;
                    if (null != body) {
                        jsonObject = new JSONObject(body);
                    } else {
                        jsonObject = new JSONObject(new InputStreamReader(httpResponse.getEntity().getContent(), Charsets.UTF_8));
                    }
                    reason = jsonObject.getString("reason");
                } catch (Exception e) {
                    reason = statusLine.getReasonPhrase();
                }
                throw new HttpResponseException(statusCode, reason);
            }
        }
    };

    /** The status code policy; accepting greater than/equal to <code>200</code> and lower than <code>300</code> while ignoring <code>404</code> */
    public static final StatusCodePolicy STATUS_CODE_POLICY_IGNORE_NOT_FOUND = new StatusCodePolicy() {

        @Override
        public void handleStatusCode(HttpResponse httpResponse, StringBuilder traceBuilder) throws HttpResponseException {
            final StatusLine statusLine = httpResponse.getStatusLine();
            final int statusCode = statusLine.getStatusCode();
            if ((statusCode < 200 || statusCode >= 300) && statusCode != 404) {
                String body = null;
                if (null != traceBuilder) {
                    try {
                        body = Streams.reader2string(new InputStreamReader(httpResponse.getEntity().getContent(), Charsets.UTF_8));
                        traceBuilder.append(body);
                    } catch (Exception x) {
                        // ignore
                    }
                }

                String reason;
                try {
                    JSONObject jsonObject;
                    if (null != body) {
                        jsonObject = new JSONObject(body);
                    } else {
                        jsonObject = new JSONObject(new InputStreamReader(httpResponse.getEntity().getContent(), Charsets.UTF_8));
                    }
                    reason = jsonObject.getJSONObject("error").getString("message");
                } catch (Exception e) {
                    reason = statusLine.getReasonPhrase();
                }
                throw new HttpResponseException(statusCode, reason);
            }
        }
    };

    // -------------------------------------------------------------------------------------------------------------- //

    private final HttpDoveAdmEndpointManager endpointManager;
    private final BasicHttpContext localcontext;
    private final String authorizationHeaderValue;
    private final ServiceLookup services;

    /**
     * Initializes a new {@link HttpDoveAdmClient}.
     *
     * @param apiKey The API key
     * @param endpointManager The {@link EndpointManager}
     * @param services The {@link ServiceLookup}
     */
    public HttpDoveAdmClient(String apiKey, HttpDoveAdmEndpointManager endpointManager, ServiceLookup services) {
        super();
        this.endpointManager = endpointManager;
        this.services = services;

        // Generate BASIC scheme object and stick it to the local execution context
        final BasicHttpContext context = new BasicHttpContext();
        final BasicScheme basicAuth = new BasicScheme();
        context.setAttribute("preemptive-auth", basicAuth);
        this.localcontext = context;
        String encodedApiKey = BaseEncoding.base64().encode(apiKey.getBytes(Charsets.UTF_8));
        authorizationHeaderValue = "X-Dovecot-API " + encodedApiKey;
    }

    @Override
    public String checkUser(String user, int userId, int contextId) throws OXException {
        ConfigViewFactory viewFactory = services.getService(ConfigViewFactory.class);
        if (null == viewFactory) {
            throw ServiceExceptionCode.absentService(ConfigViewFactory.class);
        }

        ConfigView view = viewFactory.getView(userId, contextId);

        String usr = user;
        String proxyDelim = ConfigViews.getDefinedStringPropertyFrom("com.openexchange.dovecot.doveadm.proxyDelimiter", null, view);
        if (Strings.isNotEmpty(proxyDelim)) {
            int pos = usr.indexOf(proxyDelim);
            if (pos > 0) {
                usr = usr.substring(0, pos);
            }
        }
        return usr;
    }

    private CallProperties getCallProperties(HttpDoveAdmCall call) throws OXException {
        EndpointAndClientId ec = endpointManager.getEndpoint(call);
        Endpoint endpoint = ec.getEndpoint();
        String sUrl = endpoint.getBaseUri();
        try {
            URI uri = new URI(sUrl);
            HttpHost targetHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
            return new CallProperties(uri, targetHost, endpoint, ec.getHttpClientId());
        } catch (URISyntaxException e) {
            throw DoveAdmClientExceptionCodes.INVALID_DOVECOT_URL.create(null == sUrl ? "<empty>" : sUrl);
        }
    }

    /**
     * Shuts-down this instance.
     */
    public void shutDown() {
        endpointManager.shutDown();
    }

    /**
     * Builds the JSON request body from specified command.
     * <pre>
     * [
     *   ["command", {"parameter":"value"}, "optional identifier"]
     * ]
     * </pre>
     *
     * @param command The command to build from
     * @return The resulting JSON request body
     */
    private JSONArray buildRequestBody(DoveAdmCommand command) {
        return buildRequestBody(Collections.singletonList(command));
    }

    /**
     * Builds the JSON request body from specified commands collection.
     * <pre>
     * [
     *   ["command1", {"parameter1":"value1"}, "optional identifier"],
     *   ...
     *   ["commandN", {"parameterN":"valueN"}, "optional identifier"]
     * ]
     * </pre>
     *
     * @param commands The commands to build from
     * @return The resulting JSON request body
     */
    private JSONArray buildRequestBody(Collection<DoveAdmCommand> commands) {
        JSONArray jCommands = new JSONArray(commands.size());
        for (DoveAdmCommand command : commands) {
            String optionalIdentifier = command.getOptionalIdentifier();
            if (Strings.isEmpty(optionalIdentifier)) {
                jCommands.put(new JSONArray(2).put(command.getCommand()).put(new JSONObject(command.getParameters())));
            } else {
                jCommands.put(new JSONArray(3).put(command.getCommand()).put(new JSONObject(command.getParameters())).put(optionalIdentifier));
            }
        }
        return jCommands;
    }

    @Override
    public DoveAdmResponse executeCommand(DoveAdmCommand command) throws OXException {
        if (null == command) {
            return null;
        }

        // Build JSON request body & execute command
        JSONValue jRetval = executePost(HttpDoveAdmCall.DEFAULT, null, null, buildRequestBody(command), ResultType.JSON);

        // Check result (should be a JSON array)
        if (!jRetval.isArray()) {
            throw DoveAdmClientExceptionCodes.JSON_ERROR.create("Expected a JSON array as return value, but is a JSON object: " + jRetval);
        }

        JSONArray jResponses = jRetval.toArray();
        if (jResponses.length() != 1) {
            throw DoveAdmClientExceptionCodes.JSON_ERROR.create("Unexpected number of responses: " + jRetval);
        }

        ParsedResponses responses = ParsedResponses.valueFor(jResponses);
        if (responses.isEmpty()) {
            throw DoveAdmClientExceptionCodes.JSON_ERROR.create("Empty or invalid responses: " + jRetval);
        }

        if (Strings.isEmpty(command.getOptionalIdentifier())) {
            // Grab first response
            return responses.getResponses().get(0);
        }

        DoveAdmResponse response = responses.getTaggedResponse(command.getOptionalIdentifier());
        if (null == response) {
            throw DoveAdmClientExceptionCodes.JSON_ERROR.create("No such response: " + command.getOptionalIdentifier());
        }
        return response;
    }

    @Override
    public List<DoveAdmResponse> executeCommands(List<DoveAdmCommand> commands) throws OXException {
        if (null == commands || commands.isEmpty()) {
            return Collections.emptyList();
        }

        checkOptionalIdentifiers(commands);

        // Build JSON request body & execute command
        JSONValue jRetval = executePost(HttpDoveAdmCall.DEFAULT, null, null, buildRequestBody(commands), ResultType.JSON);

        // Check result (should be a JSON array)
        if (!jRetval.isArray()) {
            throw DoveAdmClientExceptionCodes.JSON_ERROR.create("Expected a JSON array as return value, but is a JSON object: " + jRetval);
        }

        JSONArray jResponses = jRetval.toArray();
        if (jResponses.length() != commands.size()) {
            throw DoveAdmClientExceptionCodes.JSON_ERROR.create("Unexpected number of responses: " + jRetval);
        }

        ParsedResponses responses = ParsedResponses.valueFor(jResponses);
        if (responses.isEmpty()) {
            throw DoveAdmClientExceptionCodes.JSON_ERROR.create("Empty or invalid responses: " + jRetval);
        }

        List<DoveAdmResponse> doveAdmDataResponses = new ArrayList<>(commands.size());
        int i = 0;
        for (DoveAdmCommand command : commands) {
            if (Strings.isEmpty(command.getOptionalIdentifier())) {
                // Grab matching response
                doveAdmDataResponses.add(responses.getResponses().get(i));
            } else {
                DoveAdmResponse response = responses.getTaggedResponse(command.getOptionalIdentifier());
                if (null == response) {
                    throw DoveAdmClientExceptionCodes.JSON_ERROR.create("No such response: " + command.getOptionalIdentifier());
                }
                doveAdmDataResponses.add(response);
            }
            i++;
        }

        return doveAdmDataResponses;
    }

    private void checkOptionalIdentifiers(List<DoveAdmCommand> commands) throws OXException {
        Set<String> oids = new HashSet<>(commands.size());
        String oid;
        for (DoveAdmCommand command : commands) {
            oid = command.getOptionalIdentifier();
            if (Strings.isNotEmpty(oid) && false == oids.add(oid)) {
                throw DoveAdmClientExceptionCodes.DUPLICATE_OPTIONAL_IDENTIFIER.create(oid);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    private void setCommonHeaders(HttpRequestBase request, StringBuilder traceBuilder) {
        request.setHeader(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
        request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());

        if (null != traceBuilder) {
            String lineSeparator = Strings.getLineSeparator();
            traceBuilder.append(lineSeparator).append(HttpHeaders.AUTHORIZATION).append(": ").append(authorizationHeaderValue);
            traceBuilder.append(lineSeparator).append(HttpHeaders.CONTENT_TYPE).append(": ").append(ContentType.APPLICATION_JSON.getMimeType());
            traceBuilder.append(lineSeparator).append(HttpHeaders.ACCEPT).append(": ").append(ContentType.APPLICATION_JSON.getMimeType());
        }
    }

    private <R> R executePost(HttpDoveAdmCall call, String path, Map<String, String> parameters, JSONValue jBody, ResultType<R> resultType) throws OXException {
        CallProperties callProperties = getCallProperties(call);

        int maxTries = 3;
        int count = 1;
        while (count <= maxTries) {
            HttpPost post = null;
            try {
                URI uri = buildUri(callProperties.uri, toQueryString(parameters), path);
                post = new HttpPost(uri);

                StringBuilder traceBuilder = null;
                if (LOG.isTraceEnabled()) {
                    traceBuilder = new StringBuilder(2084);
                    traceBuilder.append("Request:").append(Strings.getLineSeparator());
                    traceBuilder.append("POST ").append(uri);
                }

                setCommonHeaders(post, traceBuilder);
                post.setEntity(new InputStreamEntity(new JSONInputStream(jBody, "UTF-8"), -1L, ContentType.APPLICATION_JSON));
                if (null != traceBuilder) {
                    traceBuilder.append(Strings.getLineSeparator()).append(jBody);
                }

                try {
                    R response = handleHttpResponse(execute(post, callProperties.targetHost, callProperties.httpClientId), resultType, traceBuilder);
                    if (null != traceBuilder) {
                        LOG.trace(traceBuilder.toString());
                    }
                    return response;
                } catch (HttpResponseException e) {
                    if (400 == e.getStatusCode() || 401 == e.getStatusCode()) {
                        // Authentication failed
                        throw DoveAdmClientExceptionCodes.AUTH_ERROR.create(e, e.getMessage());
                    }
                    throw handleHttpResponseError(null, e);
                } catch (IOException e) {
                    if (null != traceBuilder) {
                        String separator = Strings.getLineSeparator();
                        traceBuilder.append(separator).append(separator).append("Response:").append(separator);
                        traceBuilder.append("Encountered an I/O error: ").append(e.getMessage());
                        LOG.trace(traceBuilder.toString());
                    }
                    throw handleIOError(e, callProperties.endpoint, call);
                }
            } catch (NullPointerException e) {
                if (++count <= maxTries) {
                    long nanosToWait = TimeUnit.NANOSECONDS.convert((count * 1000) + ((long) (Math.random() * 1000)), TimeUnit.MILLISECONDS);
                    LockSupport.parkNanos(nanosToWait);
                } else {
                    throw DoveAdmClientExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
                }
            } catch (RuntimeException e) {
                throw DoveAdmClientExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
            } finally {
                reset(post);
            }
        }

        // Never reached...
        return null;
    }

    // --------------------------------------------------------------------------------------------------------------------------------

    /**
     * Builds the URI from given arguments
     *
     * @param baseUri The base URI
     * @param queryString The query string parameters
     * @return The built URI string
     * @throws IllegalArgumentException If the given string violates RFC 2396
     */
    protected static URI buildUri(URI baseUri, List<NameValuePair> queryString, String optPath) {
        try {
            URIBuilder builder = new URIBuilder();
            builder.setScheme(baseUri.getScheme()).setHost(baseUri.getHost()).setPort(baseUri.getPort()).setPath(null == optPath ? baseUri.getPath() : optPath).setQuery(null == queryString ? null : URLEncodedUtils.format(queryString, "UTF-8"));
            return builder.build();
        } catch (URISyntaxException x) {
            throw new IllegalArgumentException("Failed to build URI", x);
        }
    }

    /**
     * Gets a (parameters) map for specified arguments.
     *
     * @param args The arguments
     * @return The resulting map
     */
    protected static Map<String, String> mapFor(String... args) {
        if (null == args) {
            return null;
        }

        int length = args.length;
        if (0 == length || (length % 2) != 0) {
            return null;
        }

        Map<String, String> map = new LinkedHashMap<String, String>(length >> 1);
        for (int i = 0; i < length; i+=2) {
            map.put(args[i], args[i+1]);
        }
        return map;
    }

    /**
     * Turns specified JSON value into an appropriate HTTP entity.
     *
     * @param jValue The JSON value
     * @return The HTTP entity
     * @throws JSONException If a JSON error occurs
     * @throws IOException If an I/O error occurs
     */
    protected InputStreamEntity asHttpEntity(JSONValue jValue) throws JSONException, IOException {
        if (null == jValue) {
            return null;
        }

        ThresholdFileHolder sink = new ThresholdFileHolder();
        boolean error = true;
        try {
            final OutputStreamWriter osw = new OutputStreamWriter(sink.asOutputStream(), Charsets.UTF_8);
            jValue.write(osw);
            osw.flush();
            final InputStreamEntity entity = new InputStreamEntity(sink.getStream(), sink.getLength(), ContentType.APPLICATION_JSON);
            error = false;
            return entity;
        } catch (OXException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException(null == cause ? e : cause);
        } finally {
            if (error) {
                Streams.close(sink);
            }
        }
    }

    /**
     * Gets the appropriate query string for given parameters
     *
     * @param parameters The parameters
     * @return The query string
     */
    protected static List<NameValuePair> toQueryString(Map<String, String> parameters) {
        if (null == parameters || parameters.isEmpty()) {
            return null;
        }
        final List<NameValuePair> l = new LinkedList<NameValuePair>();
        for (final Map.Entry<String, String> e : parameters.entrySet()) {
            l.add(new BasicNameValuePair(e.getKey(), e.getValue()));
        }
        return l;
    }

    /**
     * Executes specified HTTP method/request.
     *
     * @param call The DoveAdm Call
     * @param method The method/request to execute
     * @param targetHost The target host
     * @return The HTTP response
     * @throws ClientProtocolException If client protocol error occurs
     * @throws IOException If an I/O error occurs
     */
    protected HttpResponse execute(HttpRequestBase method, HttpHost targetHost, String httpClientId) throws ClientProtocolException, IOException {
        return execute(method, targetHost, localcontext, httpClientId);
    }

    /**
     * Executes specified HTTP method/request.
     *
     * @param call The DoveAdm Call
     * @param method The method/request to execute
     * @param targetHost The target host
     * @param context The context
     * @return The HTTP response
     * @throws ClientProtocolException If client protocol error occurs
     * @throws IOException If an I/O error occurs
     */
    protected HttpResponse execute(HttpRequestBase method, HttpHost targetHost, BasicHttpContext context, String httpClientId) throws ClientProtocolException, IOException {
        try {
            HttpClientService httpClientService = services.getServiceSafe(HttpClientService.class);
            return httpClientService.getHttpClient(httpClientId).execute(targetHost, method, context);
        } catch (OXException e) {
            throw new IOException("Unable to obtain connection", e);
        }
    }

    /**
     * Resets given HTTP request
     *
     * @param request The HTTP request
     */
    protected static void reset(HttpRequestBase request) {
        if (null != request) {
            try {
                request.reset();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Ensures that the entity content is fully consumed and the content stream, if exists, is closed silently.
     *
     * @param response The HTTP response to consume and close
     */
    protected static void consume(HttpResponse response) {
        if (null != response) {
            HttpEntity entity = response.getEntity();
            if (null != entity) {
                try {
                    EntityUtils.consume(entity);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Closes the supplied HTTP request & response resources silently.
     *
     * @param request The HTTP request to reset
     * @param response The HTTP response to consume and close
     */
    protected static void close(HttpRequestBase request, HttpResponse response) {
        consume(response);
        reset(request);
    }

    /**
     * Handles given HTTP response while expecting <code>200 (Ok)</code> status code.
     *
     * @param httpResponse The HTTP response
     * @param type The type of the result object
     * @return The result object
     * @throws OXException If an Open-Xchange error occurs
     * @throws ClientProtocolException If a client protocol error occurs
     * @throws IOException If an I/O error occurs
     */
    protected <R> R handleHttpResponse(HttpResponse httpResponse, ResultType<R> type, StringBuilder traceBuilder) throws OXException, ClientProtocolException, IOException {
        try {
            return handleHttpResponse(httpResponse, STATUS_CODE_POLICY_DEFAULT, type, traceBuilder);
        } finally {
            HttpClients.close(httpResponse, false);
        }
    }

    /**
     * Handles given HTTP response while expecting given status code.
     *
     * @param httpResponse The HTTP response
     * @param policy The status code policy to obey
     * @param type The type of the result object
     * @return The result object
     * @throws OXException If an Open-Xchange error occurs
     * @throws ClientProtocolException If a client protocol error occurs
     * @throws IOException If an I/O error occurs
     * @throws IllegalStateException If content stream cannot be created
     */
    protected <R> R handleHttpResponse(HttpResponse httpResponse, StatusCodePolicy policy, ResultType<R> type, StringBuilder traceBuilder) throws OXException, ClientProtocolException, IOException {
        if (null != traceBuilder) {
            String separator = Strings.getLineSeparator();
            traceBuilder.append(separator).append(separator).append("Response:").append(separator);
            traceBuilder.append(httpResponse.getStatusLine()).append(separator);

            for (Header hdr : httpResponse.getAllHeaders()) {
                traceBuilder.append(hdr.getName()).append(": ").append(hdr.getValue()).append(separator);
            }
        }

        policy.handleStatusCode(httpResponse, traceBuilder);

        // OK, continue
        if (ResultType.JSON == type) {
            try {
                JSONValue jResponse = JSONObject.parse(new InputStreamReader(httpResponse.getEntity().getContent(), Charsets.UTF_8));
                if (null != traceBuilder) {
                    traceBuilder.append(jResponse);
                }
                return (R) jResponse;
            } catch (JSONException e) {
                throw DoveAdmClientExceptionCodes.JSON_ERROR.create(e, e.getMessage());
            } finally {
                consume(httpResponse);
            }
        }

        if (ResultType.VOID == type) {
            consume(httpResponse);
            return null;
        }

        if (ResultType.INPUT_STREAM == type) {
            return (R) httpResponse.getEntity().getContent();
        }

        R retval = parseIntoObject(httpResponse.getEntity().getContent(), type);
        consume(httpResponse);
        return retval;
    }

    /**
     * Parses the JSON data provided by given input stream to its Java object representation.
     *
     * @param inputStream The input stream to read JSON data from
     * @param clazz The type of the Java representation
     * @return The Java object representation
     * @throws OXException If Java object representation cannot be returned
     */
    @SuppressWarnings("unchecked")
    protected static <T> T parseIntoObject(InputStream inputStream, ResultType<T> resultType) throws OXException {
        if (ResultType.JSON.equals(resultType)) {
            // Return JSONValue
            try (InputStreamReader reader = new InputStreamReader(inputStream, Charsets.UTF_8)) {
                return (T) JSONObject.parse(reader);
            } catch (JSONException e) {
                throw DoveAdmClientExceptionCodes.JSON_ERROR.create(e, e.getMessage());
            } catch (IOException e) {
                throw DoveAdmClientExceptionCodes.IO_ERROR.create(e, e.getMessage());
            } catch (RuntimeException e) {
                throw DoveAdmClientExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
            }
        } else if (ResultType.INPUT_STREAM.equals(resultType)) {
            // Return the input stream
            return (T) inputStream;
        }
        // else; Void
        return null;
    }

    /**
     * Handles given I/O error.
     *
     * @param e The I/O error
     * @param endpoint The end-point for which an I/O error occurred
     * @param call The associated call
     * @return The resulting exception
     */
    protected OXException handleIOError(IOException e, Endpoint endpoint, HttpDoveAdmCall call) {
        final Throwable cause = e.getCause();
        if (cause instanceof AuthenticationException) {
            return DoveAdmClientExceptionCodes.AUTH_ERROR.create(cause, cause.getMessage());
        }

        boolean blacklisted = endpointManager.blacklist(call, endpoint);
        if (blacklisted) {
            LOG.warn("Encountered I/O error \"{}\" ({}) while trying to access DoveAdm end-point {}. End-point is therefore added to black-list until re-available", e.getMessage(), e.getClass().getName(), endpoint.getBaseUri());
        } else {
            LOG.warn("Encountered I/O error \"{}\" ({}) while trying to access DoveAdm end-point {}.", e.getMessage(), e.getClass().getName(), endpoint.getBaseUri());
        }
        return DoveAdmClientExceptionCodes.IO_ERROR.create(e, e.getMessage());
    }

    /** Status code (401) indicating that the request requires HTTP authentication. */
    private static final int SC_UNAUTHORIZED = 401;

    /** Status code (404) indicating that the requested resource is not available. */
    private static final int SC_NOT_FOUND = 404;

    /**
     * Handles given HTTP response error.
     *
     * @param identifier The optional identifier for associated Microsoft OneDrive resource
     * @param e The HTTP error
     * @return The resulting exception
     */
    protected OXException handleHttpResponseError(String identifier, HttpResponseException e) {
        if (null != identifier && SC_NOT_FOUND == e.getStatusCode()) {
            return DoveAdmClientExceptionCodes.NOT_FOUND.create(e, identifier);
        }
        if (SC_UNAUTHORIZED == e.getStatusCode()) {
            return DoveAdmClientExceptionCodes.AUTH_ERROR.create();
        }
        return DoveAdmClientExceptionCodes.DOVEADM_SERVER_ERROR.create(e, Integer.valueOf(e.getStatusCode()), e.getMessage());
    }

    // --------------------------------------------------------------------------------------------------------------------------------

    private static final class CallProperties {

        final URI uri;
        final HttpHost targetHost;
        final Endpoint endpoint;
        final String httpClientId;

        CallProperties(URI uri, HttpHost targetHost, Endpoint endpoint, String httpClientId) {
            super();
            this.uri = uri;
            this.targetHost = targetHost;
            this.endpoint = endpoint;
            this.httpClientId = httpClientId;
        }
    }

}
