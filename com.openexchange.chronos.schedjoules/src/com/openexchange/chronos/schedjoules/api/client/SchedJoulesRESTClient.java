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

package com.openexchange.chronos.schedjoules.api.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import com.openexchange.chronos.schedjoules.exception.SchedJoulesAPIExceptionCodes;
import com.openexchange.chronos.schedjoules.osgi.Services;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.rest.client.v2.AbstractRESTClient;
import com.openexchange.rest.client.v2.RESTMethod;
import com.openexchange.rest.client.v2.RESTResponse;

/**
 * {@link SchedJoulesRESTClient}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class SchedJoulesRESTClient extends AbstractRESTClient {

    private static final String LAST_MODIFIED_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z";
    private final static ThreadLocal<SimpleDateFormat> LAST_MODIFIED_DATE_PARSER = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(LAST_MODIFIED_DATE_PATTERN, java.util.Locale.US);
        }
    };

    private static final String CLIENT_NAME = "schedjoules";
    private static final int API_VERSION = 1;

    private static final String ACCEPT_HEADER = "application/vnd.schedjoules; version=%s";
    private static final String AUTHORIZATION_HEADER = "Token token=\"%s\"";

    private String authorizationHeader;
    private final String acceptHeader;
    private final String scheme;
    private final String host;

    /**
     * Initialises a new {@link SchedJoulesRESTClient}.
     * 
     * @param scheme The HTTP scheme 
     * @param host The host to send requests to
     * @param apiKey The API key
     * @param timeout The timeout for requests
     */
    public SchedJoulesRESTClient(String scheme, String host, String apiKey) {
        super(Services.getServiceLookup(), CLIENT_NAME, new SchedJoulesRESTResponseParser());
        this.scheme = scheme;
        this.host = host;
        authorizationHeader = String.format(AUTHORIZATION_HEADER, apiKey);
        acceptHeader = String.format(ACCEPT_HEADER, Integer.toString(API_VERSION));
    }

    /**
     * Prepares the specified HTTP request and adds the Authorization header
     *
     * @param request The {@link HttpRequestBase} to prepare
     * @param scheme The mandatory scheme/protocol
     * @param baseUrl The mandatory base URL
     * @param path The mandatory path
     * @param query The optional query string
     * @throws OXException if the path is not valid
     */
    private void prepareAuthorizedRequest(HttpRequestBase request, String scheme, String baseUrl, String path, String query) throws OXException {
        prepareRequest(request, scheme, baseUrl, path, query, null, -1);
        request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }

    /**
     * Prepares the specified HTTP request
     *
     * @param request The {@link HttpRequestBase} to prepare
     * @param scheme The mandatory scheme/protocol
     * @param baseUrl The mandatory base URL
     * @param path The mandatory path
     * @param query The optional query string
     * @throws OXException if the path is not valid
     */
    private void prepareRequest(HttpRequestBase request, String scheme, String baseUrl, String path, String query, String eTag, long lastModified) throws OXException {
        try {
            request.setURI(new URI(scheme, baseUrl, path, query, null));
            request.addHeader(HttpHeaders.ACCEPT, acceptHeader);
            if (Strings.isNotEmpty(eTag)) {
                request.addHeader(HttpHeaders.IF_NONE_MATCH, eTag);
            }
            if (lastModified > 0) {
                request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, LAST_MODIFIED_DATE_PARSER.get().format(new Date(lastModified)));
            }
        } catch (URISyntaxException e) {
            throw SchedJoulesAPIExceptionCodes.INVALID_URI_PATH.create(e, path);
        }
    }

    /**
     * Prepares the specified {@link SchedJoulesRequest}
     *
     * @param request The {@link SchedJoulesRequest} to prepare
     * @return an {@link HttpUriRequest}
     * @throws OXException if an unknown HTTP method is defined in the request
     */
    private HttpRequestBase prepareRequest(SchedJoulesRequest request) throws OXException {
        // Prepare the query
        String query = prepareQuery(request.getQueryParameters());

        // Prepare the request
        HttpRequestBase httpRequest = createRequest(request.getMethod());
        prepareAuthorizedRequest(httpRequest, this.scheme, this.host, request.getEndPoint(), query);
        return httpRequest;
    }

    /**
     * Creates an {@link HttpUriRequest} from the specified {@link URL} and the specified {@link HttpMethod}
     *
     * @param url The {@link URL} to use
     * @param httpMethod The {@link HttpMethod}
     * @param eTag the optional etag
     * @param lastModified The optional last modified timestamp to use
     * @return The new {@link HttpUriRequest}
     * @throws OXException if an unknown HTTP method is provided
     * @throws URISyntaxException If an invalid URL is provided
     */
    private HttpRequestBase prepareRequest(URL url, RESTMethod httpMethod, String eTag, long lastModified) throws OXException {
        HttpRequestBase httpRequest = createRequest(httpMethod);
        prepareRequest(httpRequest, url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(), eTag, lastModified);
        return httpRequest;
    }

    /**
     * Executes the specified {@link SchedJoulesRequest}
     *
     * @param request The {@link SchedJoulesRequest} to execute
     * @return The {@link RESTResponse}
     * @throws OXException if an error is occurred
     */
    public RESTResponse executeRequest(SchedJoulesRequest request) throws OXException {
        return executeRequest(prepareRequest(request));
    }

    /**
     * Executes a GET request to the specified {@link URL}
     *
     * @param url The {@link URL}
     * @return The {@link RESTResponse}
     * @throws OXException if an error is occurred
     */
    public RESTResponse executeRequest(URL url) throws OXException {
        return executeRequest(url, RESTMethod.GET, null, -1);
    }

    /**
     * Executes the specified {@link SchedJoulesRequest} with the specified method
     *
     * @param request The {@link SchedJoulesRequest}
     * @param httpMethod the {@link RESTMethod} to use
     * @param eTag The optional etag to use
     * @param lastModified The optional last modified timestamp to use
     * @return The {@link RESTResponse}
     * @throws OXException if an error is occurred
     */
    public RESTResponse executeRequest(SchedJoulesRequest request, RESTMethod httpMethod, String eTag, long lastModified) throws OXException {
        HttpRequestBase httpRequest = createRequest(httpMethod);
        prepareRequest(httpRequest, this.scheme, this.host, request.getEndPoint(), prepareQuery(request.getQueryParameters()), eTag, lastModified);
        httpRequest.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        return executeRequest(httpRequest);
    }

    /**
     * Executes a request with the specified method to the specified {@link URL}
     *
     * @param url The {@link URL}
     * @param httpMethod the {@link RESTMethod} to use
     * @param eTag The optional etag to use
     * @param lastModified The optional last modified timestamp to use
     * @return The {@link RESTResponse}
     * @throws OXException if an error is occurred
     */
    public RESTResponse executeRequest(URL url, RESTMethod httpMethod, String eTag, long lastModified) throws OXException {
        return executeRequest(prepareRequest(url, httpMethod, eTag, lastModified));
    }
}
