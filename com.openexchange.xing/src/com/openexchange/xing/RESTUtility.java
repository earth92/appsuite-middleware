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

package com.openexchange.xing;

import static com.openexchange.java.Autoboxing.I;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import javax.net.ssl.SSLException;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.json.JSONException;
import org.json.JSONInputStream;
import org.json.JSONObject;
import org.json.JSONValue;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.xing.exception.XingApiException;
import com.openexchange.xing.exception.XingException;
import com.openexchange.xing.exception.XingIOException;
import com.openexchange.xing.exception.XingParseException;
import com.openexchange.xing.exception.XingPermissionDeniedException;
import com.openexchange.xing.exception.XingSSLException;
import com.openexchange.xing.exception.XingServerException;
import com.openexchange.xing.exception.XingUnlinkedException;
import com.openexchange.xing.session.Session;
import com.openexchange.xing.util.Services;

/**
 * This class is mostly used internally by {@link XingAPI} for creating and executing REST requests to the XING API, and parsing responses.
 * You probably won't have a use for it other than {@link #parseDate(String)} for parsing modified times returned in metadata, or (in very
 * rare circumstances) writing your own API calls.
 */
public class RESTUtility {

    private static final List<Integer> EXPECTED_200_OK_OR_206_PARTIAL_CONTENT = Arrays.asList(I(XingServerException._200_OK), I(XingServerException._206_PARTIAL_CONTENT));
    private static final List<Integer> EXPECTED_200_OK = Arrays.asList(I(XingServerException._200_OK));

    /**
     * No initialization.
     */
    private RESTUtility() {
        super();
    }

    private static final DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss ZZZZZ", Locale.US);

    /** The HTTP method enumeration */
    public static enum Method {
        GET, POST, PUT, DELETE;
    }

    /**
     * Creates and sends a basic request to the XING API, without building the url, parses the response as JSON, and returns the result.
     *
     * @param method GET or POST
     * @param url the URL to use.
     * @param requestInformation The request's JSON object
     * @param session the {@link Session} to use for this request
     * @param expectedStatusCode the expected status code which should be returned on success
     * @return a parsed JSON object, typically a Map or a JSONArray
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code
     * @throws XingIOException if any network-related error occurs
     * @throws XingUnlinkedException if the user has revoked access
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static JSONValue basicRequest(final Method method, final String url, final JSONObject requestInformation, final Session session, final List<Integer> expectedStatusCode) throws XingException {
        final HttpRequestBase req;
        switch (method) {
        case PUT: {
            final HttpPut put = new HttpPut(url);
            if (null != requestInformation) {
                put.setEntity(new InputStreamEntity(new JSONInputStream(requestInformation, com.openexchange.java.Charsets.UTF_8_NAME), -1L, ContentType.APPLICATION_JSON));
            }
            req = put;
        }
        break;
        case POST: {
            final HttpPost post = new HttpPost(url);
            if (null != requestInformation) {
                post.setEntity(new InputStreamEntity(new JSONInputStream(requestInformation, com.openexchange.java.Charsets.UTF_8_NAME),requestInformation.toString().getBytes(com.openexchange.java.Charsets.UTF_8).length, ContentType.APPLICATION_JSON));
            }
            req = post;
        }
        break;
        case GET:
            req = new HttpGet(url);
            break;
        case DELETE:
            req = new HttpDelete(url);
            break;
        default:
            throw new XingException("Unsupported HTTP method: " + method);
        }
        final HttpResponse resp = execute(session, req, expectedStatusCode);

        return parseAsJSON(resp, expectedStatusCode);
    }

    /**
     * Creates and sends a request to the XING API, parses the response as JSON, and returns the result.
     *
     * @param method GET or POST.
     * @param host the hostname to use. Should be either api server, content server, or web server.
     * @param path the URL path, starting with a '/'.
     * @param apiVersion the API version to use. This should almost always be set to {@code XingAPI.VERSION}.
     * @param session the {@link Session} to use for this request.
     * @return a parsed JSON object, typically a Map or a JSONArray.
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException if any network-related error occurs.
     * @throws XingUnlinkedException if the user has revoked access.
     * @throws XingParseException if a malformed or unknown response was received from the server.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static JSONValue request(final Method method, final String host, final String path, final int apiVersion, final Session session) throws XingException {
        final HttpResponse resp = streamRequest(method, host, path, apiVersion, null, session).response;
        return parseAsJSON(resp, EXPECTED_200_OK);
    }

    /**
     * Creates and sends a request to the XING API, parses the response as JSON, and returns the result.
     *
     * @param method GET or POST.
     * @param host the hostname to use. Should be either api server, content server, or web server.
     * @param path the URL path, starting with a '/'.
     * @param apiVersion the API version to use. This should almost always be set to {@code XingAPI.VERSION}.
     * @param params the URL params in an array, with the even numbered elements the parameter names and odd numbered elements the values,
     *            e.g. <code>new String[] {"path", "/Public", "locale",
     *         "en"}</code>.
     * @param session the {@link Session} to use for this request.
     * @return a parsed JSON object, typically a Map or a JSONArray.
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException if any network-related error occurs.
     * @throws XingUnlinkedException if the user has revoked access.
     * @throws XingParseException if a malformed or unknown response was received from the server.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static JSONValue request(final Method method, final String host, final String path, final int apiVersion, final String[] params, final Session session) throws XingException {
        final HttpResponse resp = streamRequest(method, host, path, apiVersion, params, session).response;
        return parseAsJSON(resp, EXPECTED_200_OK);
    }

    /**
     * Creates and sends a request to the XING API, parses the response as JSON, and returns the result.
     *
     * @param method GET or POST.
     * @param host the hostname to use. Should be either api server, content server, or web server.
     * @param path the URL path, starting with a '/'.
     * @param apiVersion the API version to use. This should almost always be set to {@code XingAPI.VERSION}.
     * @param params the URL params in an array, with the even numbered elements the parameter names and odd numbered elements the values,
     *            e.g. <code>new String[] {"path", "/Public", "locale",
     *         "en"}</code>.
     * @param session the {@link Session} to use for this request.
     * @return a parsed JSON object, typically a Map or a JSONArray.
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException if any network-related error occurs.
     * @throws XingUnlinkedException if the user has revoked access.
     * @throws XingParseException if a malformed or unknown response was received from the server.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static JSONValue request(final Method method, final String host, final String path, final int apiVersion, final String[] params, final Session session, final List<Integer> expectedStatusCode) throws XingException {
        final HttpResponse resp = streamRequest(method, host, path, apiVersion, params, session, expectedStatusCode).response;
        return parseAsJSON(resp, expectedStatusCode);
    }

    /**
     * Creates and sends a request to the XING API, and returns a {@link RequestAndResponse} containing the {@link HttpUriRequest} and
     * {@link HttpResponse}.
     *
     * @param method GET or POST.
     * @param host the hostname to use. Should be either api server, content server, or web server.
     * @param path the URL path, starting with a '/'.
     * @param apiVersion the API version to use. This should almost always be set to {@code XingAPI.VERSION}.
     * @param params the URL params in an array, with the even numbered elements the parameter names and odd numbered elements the values,
     *            e.g. <code>new String[] {"path", "/Public", "locale",
     *         "en"}</code>.
     * @param session the {@link Session} to use for this request.
     * @return a parsed JSON object, typically a Map or a JSONArray.
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException if any network-related error occurs.
     * @throws XingUnlinkedException if the user has revoked access.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static RequestAndResponse streamRequest(final Method method, final String host, final String path, final int apiVersion, final String params[], final Session session) throws XingException {
        return streamRequest(
            method,
            host,
            path,
            apiVersion,
            params,
            null,
            session,
            EXPECTED_200_OK_OR_206_PARTIAL_CONTENT);
    }

    /**
     * Creates and sends a request to the XING API, and returns a {@link RequestAndResponse} containing the {@link HttpUriRequest} and
     * {@link HttpResponse}.
     *
     * @param method GET or POST.
     * @param host the hostname to use. Should be either api server, content server, or web server.
     * @param path the URL path, starting with a '/'.
     * @param apiVersion the API version to use. This should almost always be set to {@code XingAPI.VERSION}.
     * @param params the URL params in an array, with the even numbered elements the parameter names and odd numbered elements the values,
     *            e.g. <code>new String[] {"path", "/Public", "locale",
     *         "en"}</code>.
     * @param session the {@link Session} to use for this request.
     * @param expectedStatusCode the expected status code which should be returned on success.
     * @return a parsed JSON object, typically a Map or a JSONArray.
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException if any network-related error occurs.
     * @throws XingUnlinkedException if the user has revoked access.
     * @throws XingPermissionDeniedException if XING denies the requested action due to insufficient permissions granted to the associated XING app.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static RequestAndResponse streamRequest(final Method method, final String host, final String path, final int apiVersion, final String params[], final Session session, final List<Integer> expectedStatusCode) throws XingException {
        return streamRequest(method, host, path, apiVersion, params, null, session, expectedStatusCode);
    }

    /**
     * Creates and sends a request to the XING API, and returns a {@link RequestAndResponse} containing the {@link HttpUriRequest} and
     * {@link HttpResponse}.
     *
     * @param method GET or POST.
     * @param host the hostname to use. Should be either api server, content server, or web server.
     * @param path the URL path, starting with a '/'.
     * @param apiVersion the API version to use. This should almost always be set to {@code XingAPI.VERSION}.
     * @param params the URL params in an array, with the even numbered elements the parameter names and odd numbered elements the values,
     *            e.g. <code>new String[] {"path", "/Public", "locale",
     *         "en"}</code>.
     * @param requestInformation The request's JSON object
     * @param session the {@link Session} to use for this request.
     * @return a parsed JSON object, typically a Map or a JSONArray.
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException if any network-related error occurs.
     * @throws XingUnlinkedException if the user has revoked access.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static RequestAndResponse streamRequest(final Method method, final String host, final String path, final int apiVersion, final String params[], final JSONObject requestInformation, final Session session) throws XingException {
        return streamRequest(
            method,
            host,
            path,
            apiVersion,
            params,
            requestInformation,
            session,
            EXPECTED_200_OK_OR_206_PARTIAL_CONTENT);
    }

    /**
     * Creates and sends a request to the XING API, and returns a {@link RequestAndResponse} containing the {@link HttpUriRequest} and
     * {@link HttpResponse}.
     *
     * @param method GET or POST.
     * @param host the hostname to use. Should be either api server, content server, or web server.
     * @param path the URL path, starting with a '/'.
     * @param apiVersion the API version to use. This should almost always be set to {@code XingAPI.VERSION}.
     * @param params the URL params in an array, with the even numbered elements the parameter names and odd numbered elements the values,
     *            e.g. <code>new String[] {"path", "/Public", "locale",
     *         "en"}</code>.
     * @param requestInformation The request's JSON object
     * @param session the {@link Session} to use for this request.
     * @param expectedStatusCode the expected status code which should be returned on success.
     * @return a parsed JSON object, typically a Map or a JSONArray.
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException if any network-related error occurs.
     * @throws XingUnlinkedException if the user has revoked access.
     * @throws XingPermissionDeniedException if XING denies the requested action due to insufficient permissions granted to the associated XING app.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static RequestAndResponse streamRequest(final Method method, final String host, final String path, final int apiVersion, final String params[], final JSONObject requestInformation, final Session session, final List<Integer> expectedStatusCode) throws XingException {
        final HttpRequestBase req;
        switch (method) {
        case PUT:
        {
            final HttpPut put = new HttpPut(buildURL(host, apiVersion, path, params));
            if (null != requestInformation) {
                put.setEntity(new InputStreamEntity(new JSONInputStream(requestInformation, com.openexchange.java.Charsets.UTF_8_NAME), -1L, ContentType.APPLICATION_JSON));
            }
            req = put;
        }
        break;
        case POST:
        {
            final HttpPost post = new HttpPost(buildURL(host, apiVersion, path, params));
            if (null != requestInformation) {
                post.setEntity(new InputStreamEntity(new JSONInputStream(requestInformation, com.openexchange.java.Charsets.UTF_8_NAME), requestInformation.toString().length(), ContentType.APPLICATION_JSON));
            }
            req = post;
        }
        break;
        case GET:
            req = new HttpGet(buildURL(host, apiVersion, path, params));
            break;
        case DELETE:
            req = new HttpDelete(buildURL(host, apiVersion, path, params));
            break;
        default:
            throw new XingException("Unsupported HTTP method: " + method);
        }
        // Sign request
        session.sign(req);
        final HttpResponse resp = execute(session, req, expectedStatusCode);
        return new RequestAndResponse(req, resp);
    }

    /**
     * Reads in content from an {@link HttpResponse} and parses it as JSON.
     *
     * @param response the {@link HttpResponse}.
     * @param expectedStatusCode - Contains the expected status code on successful response
     * @return a parsed JSON object, typically a Map or a JSONArray.
     * @throws XingServerException if the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException if any network-related error occurs while reading in content from the {@link HttpResponse}.
     * @throws XingUnlinkedException if the user has revoked access.
     * @throws XingPermissionDeniedException if XING denies the requested action due to insufficient permissions granted to the associated XING app.
     * @throws XingParseException if a malformed or unknown response was received from the server.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    private static JSONValue parseAsJSON(final HttpResponse response, final List<Integer> expectedStatusCode) throws XingException {
        JSONValue result = null;

        BufferedReader bin = null;
        try {
            final HttpEntity ent = response.getEntity();
            if (ent != null) {
                final InputStreamReader in = new InputStreamReader(ent.getContent(), com.openexchange.java.Charsets.UTF_8);
                // Wrap this with a Buffer, so we can re-parse it if it's
                // not JSON
                // Has to be at least 16384, because this is defined as the buffer size in
                // org.json.simple.parser.Yylex.java
                // and otherwise the reset() call won't work
                bin = new BufferedReader(in, 16384);
                bin.mark(16384);
                result = JSONObject.parse(bin);
                if (result.isObject()) {
                    checkForError(result.toObject());
                }
            }
        } catch (IOException e) {
            throw new XingIOException(e);
        } catch (JSONException e) {
            if (XingServerException.isValidWithNullBody(response)) {
                // We have something from Xing, but it's an error with no reason
                throw new XingServerException(response);
            }
            // This is from Xing, and we shouldn't be getting it
            String body = XingParseException.stringifyBody(bin);
            if (Strings.isEmpty(body)) {
                throw new XingServerException(response, result);
            }
            throw new XingParseException("failed to parse: " + body);
        } finally {
            Streams.close(bin);
        }

        final int statusCode = response.getStatusLine().getStatusCode();
        if (false == expectedStatusCode.contains(I(statusCode))) {
            if (statusCode == XingServerException._401_UNAUTHORIZED) {
                throw new XingUnlinkedException();
            }
            throw new XingServerException(response, result);
        }

        return result;
    }

    private static void checkForError(final JSONObject responseObject) throws XingApiException, XingUnlinkedException, XingPermissionDeniedException {
        if (responseObject.has("error_name")) {
            String message = responseObject.optString("message");
            if (message.indexOf("Invalid OAuth token") >= 0) {
                throw new XingUnlinkedException("Invalid OAuth token");
            }
            if ("INSUFFICIENT_PRIVILEGES".equals(responseObject.optString("error_name"))) {
                throw new XingPermissionDeniedException("Insufficient Privileges");
            }
            throw new XingApiException(responseObject);
        }
    }

    /**
     * Reads in content from an {@link HttpResponse} and parses it as a query string.
     *
     * @param response The {@link HttpResponse}.
     * @return A map of parameter names to values from the query string.
     * @throws XingIOException if any network-related error occurs while reading in content from the {@link HttpResponse}.
     * @throws XingParseException if a malformed or unknown response was received from the server.
     * @throws XingException for any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    public static Map<String, String> parseAsQueryString(final HttpResponse response) throws XingException {
        final HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new XingParseException("Bad response from Xing.");
        }

        Scanner scanner = null;
        InputStream contentStream = null;
        try {
            contentStream = entity.getContent();
            scanner = new Scanner(contentStream, com.openexchange.java.Charsets.UTF_8_NAME);
            scanner.useDelimiter("&");

            Map<String, String> result = new HashMap<String, String>();
            while (scanner.hasNext()) {
                final String nameValue = scanner.next();
                final String[] parts = nameValue.split("=");
                if (parts.length != 2) {
                    throw new XingParseException("Bad query string from Xing.");
                }
                result.put(parts[0], parts[1]);
            }
            return result;
        } catch (IOException e) {
            throw new XingIOException(e);
        } finally {
            Streams.close(contentStream, scanner);
        }
    }

    /**
     * Get the {@link HttpClientService}
     *
     * @return The service
     * @throws XingException If service is missing
     */
    private static HttpClient getHttpClient() throws XingException {
        try {
            HttpClientService httpClientService = Services.getService(HttpClientService.class);
            if (null == httpClientService) {
                throw new XingException("Internal server error. Missing service " + HttpClientService.class.getSimpleName());
            }
            return httpClientService.getHttpClient("xing");
        } catch (Exception e) {
            throw new XingException("Internal server error. Unable to get HTTP client", e);
        }
    }

    /**
     * Executes an {@link HttpUriRequest} with the given {@link Session} and returns an {@link HttpResponse}.
     *
     * @param session The session to use.
     * @param req The request to execute.
     * @param expectedStatusCode the expected status code which should be returned on success.
     * @return An {@link HttpResponse}.
     * @throws XingServerException If the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException If any network-related error occurs.
     * @throws XingUnlinkedException If the user has revoked access.
     * @throws XingPermissionDeniedException if XING denies the requested action due to insufficient permissions granted to the associated XING app.
     * @throws XingException For any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    private static HttpResponse execute(final Session session, final HttpRequestBase req, final List<Integer> expectedStatusCode) throws XingException {
        return execute(session, req, -1, expectedStatusCode);
    }

    /**
     * Executes an {@link HttpUriRequest} with the given {@link Session} and returns an {@link HttpResponse}.
     *
     * @param session The session to use.
     * @param req The request to execute.
     * @param socketTimeoutOverrideMs If >= 0, the socket timeout to set on this request. Does nothing if set to a negative number.
     * @param expectedStatusCode - Contains the expected status code on successful response
     * @return An {@link HttpResponse}.
     * @throws XingServerException If the server responds with an error code. See the constants in {@link XingServerException} for the
     *             meaning of each error code.
     * @throws XingIOException If any network-related error occurs.
     * @throws XingUnlinkedException If the user has revoked access.
     * @throws XingPermissionDeniedException if XING denies the requested action due to insufficient permissions granted to the associated XING app.
     * @throws XingException For any other unknown errors. This is also a superclass of all other XING exceptions, so you may want to only
     *             catch this exception which signals that some kind of error occurred.
     */
    private static HttpResponse execute(final Session session, final HttpRequestBase req, final int socketTimeoutOverrideMs, final List<Integer> expectedStatusCode) throws XingException {
        final HttpClient client = getHttpClient();

        // Set request timeouts.
        session.setRequestTimeout(req);
        if (socketTimeoutOverrideMs >= 0) {
            req.setConfig(RequestConfig.custom().setConnectTimeout(req.getConfig().getConnectTimeout()).setSocketTimeout(socketTimeoutOverrideMs).build());
        }

        final boolean repeatable = isRequestRepeatable(req);

        HttpResponse response = null;
        try {
            for (int retries = 0; response == null && retries < 5; retries++) {
                /*
                 * The try/catch is a workaround for a bug in the HttpClient libraries. It should be returning null instead when an error
                 * occurs. Fixed in HttpClient 4.1, but we're stuck with this for now. See:
                 * http://code.google.com/p/android/issues/detail?id=5255
                 */
                try {
                    response = client.execute(req);
                } catch (NullPointerException e) {
                    // Leave 'response' as null. This is handled below.
                }

                if (!repeatable) {
                    break;
                }
            }

            if (response == null) {
                // This is from that bug, and retrying hasn't fixed it.
                throw new XingIOException("Apache HTTPClient encountered an error. No response, try again.");
            }

            final int statusCode = response.getStatusLine().getStatusCode();

            if (false == expectedStatusCode.contains(I(statusCode))) {
                // This will throw the right thing: either a XingServerException or a XingProxyException
                parseAsJSON(response, expectedStatusCode);
            }
            return response;
        } catch (SSLException e) {
            throw new XingSSLException(e);
        } catch (IOException e) {
            // Quite common for network going up & down or the request being
            // cancelled, so don't worry about logging this
            throw new XingIOException(e);
        }
    }

    private static boolean isRequestRepeatable(final HttpRequest req) {
        // If the request contains an HttpEntity that can't be "reset" (like an InputStream),
        // then it isn't repeatable.
        if (req instanceof HttpEntityEnclosingRequest) {
            final HttpEntityEnclosingRequest ereq = (HttpEntityEnclosingRequest) req;
            final HttpEntity entity = ereq.getEntity();
            if (entity != null && !entity.isRepeatable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a URL for a request to the XING API.
     *
     * @param host The XING host (i.e., api server, content server, or web server).
     * @param apiVersion The API version to use. You should almost always use {@code XingAPI.VERSION} for this.
     * @param target The target path, staring with a '/'.
     * @param params Any URL params in an array, with the even numbered elements the parameter names and odd numbered elements the values,
     *            e.g. <code>new String[] {"path", "/Public", "locale",
     *         "en"}</code>.
     * @return A full URL for making a request.
     */
    public static String buildURL(final String host, final int apiVersion, final String target, final String[] params) {
        String trgt = target;
        if (!trgt.startsWith("/")) {
            trgt = "/" + trgt;
        }

        try {
            // We have to encode the whole line, then remove + and / encoding
            // to get a good OAuth URL.
            if (apiVersion > 0) {
                trgt = URLEncoder.encode(new StringBuilder(16).append("/v").append(apiVersion).append(trgt).toString(), com.openexchange.java.Charsets.UTF_8_NAME);
            } else {
                trgt = URLEncoder.encode(new StringBuilder(16).append(trgt).toString(), com.openexchange.java.Charsets.UTF_8_NAME);
            }
            trgt = trgt.replace("%2F", "/");

            if (params != null && params.length > 0) {
                trgt += "?" + urlencode(params);
            }

            // These substitutions must be made to keep OAuth happy.
            trgt = trgt.replace("+", "%20").replace("*", "%2A");
        } catch (UnsupportedEncodingException uce) {
            return null;
        }

        return new StringBuilder(32).append("https://").append(host).append(":443").append(trgt).toString();
    }

    /**
     * Parses a date/time returned by the XING API. Returns <code>null</code> if it cannot be parsed.
     *
     * @param date A date returned by the API.
     * @return A {@link Date}.
     */
    public static Date parseDate(final String date) {
        try {
            synchronized (dateFormat) {
                return dateFormat.parse(date);
            }
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    /**
     * URL encodes an array of parameters into a query string.
     */
    private static String urlencode(final String[] params) {
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("Params must have an even number of elements.");
        }
        try {
            final StringBuilder result = new StringBuilder(params.length << 4);
            boolean firstTime = true;
            for (int i = 0; i < params.length; i += 2) {
                final String value = params[i + 1];
                if (null != value) {
                    if (firstTime) {
                        firstTime = false;
                    } else {
                        result.append('&');
                    }
                    result.append(encodeUrl(params[i])).append('=').append(encodeUrl(value));
                }
            }
            return result.toString().replace("*", "%2A");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final URLCodec URL_CODEC = new URLCodec(CharEncoding.UTF_8);

    /**
     * URL encodes given string.
     */
    public static final String encodeUrl(String s) {
        try {
            return com.openexchange.java.Strings.isEmpty(s) ? s : URL_CODEC.encode(s);
        } catch (EncoderException e) {
            return s;
        }
    }
}
