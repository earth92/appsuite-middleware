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

package com.openexchange.chronos.provider.ical.conn;

import static com.openexchange.java.Autoboxing.L;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.DateUtils;
import com.openexchange.auth.info.AuthInfo;
import com.openexchange.auth.info.AuthType;
import com.openexchange.chronos.ical.ICalParameters;
import com.openexchange.chronos.ical.ICalService;
import com.openexchange.chronos.ical.ImportedCalendar;
import com.openexchange.chronos.provider.ical.ICalCalendarFeedConfig;
import com.openexchange.chronos.provider.ical.auth.AdvancedAuthInfo;
import com.openexchange.chronos.provider.ical.exception.ICalProviderExceptionCodes;
import com.openexchange.chronos.provider.ical.osgi.Services;
import com.openexchange.chronos.provider.ical.properties.ICalCalendarProviderProperties;
import com.openexchange.chronos.provider.ical.result.GetResponse;
import com.openexchange.chronos.provider.ical.result.GetResponseState;
import com.openexchange.chronos.provider.ical.utils.ICalProviderUtils;
import com.openexchange.exception.OXException;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.rest.client.httpclient.HttpClients;
import com.openexchange.session.Session;

/**
 *
 * {@link ICalFeedClient}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since v7.10.0
 */
public class ICalFeedClient {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ICalFeedClient.class);

    protected final ICalCalendarFeedConfig iCalFeedConfig;
    protected final Session session;

    public ICalFeedClient(Session session, ICalCalendarFeedConfig iCalFeedConfig) {
        this.session = session;
        this.iCalFeedConfig = iCalFeedConfig;
    }

    private HttpGet prepareGet() {
        HttpGet request = new HttpGet(iCalFeedConfig.getFeedUrl());
        request.addHeader(HttpHeaders.ACCEPT, "text/calendar");
        request.addHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");
        if (Strings.isNotEmpty(iCalFeedConfig.getEtag())) {
            request.addHeader(HttpHeaders.IF_NONE_MATCH, iCalFeedConfig.getEtag());
        }
        String ifModifiedSince = DateUtils.formatDate(new Date(iCalFeedConfig.getLastUpdated()));
        if (Strings.isNotEmpty(ifModifiedSince)) {
            request.setHeader(HttpHeaders.IF_MODIFIED_SINCE, ifModifiedSince);
        }

        handleAuth(request);
        return request;
    }

    protected void handleAuth(HttpRequestBase method) {
        AdvancedAuthInfo authInfo = this.iCalFeedConfig.getAuthInfo();
        AuthType authType = authInfo.getAuthType();
        switch (authType) {
            case BASIC: {
                StringBuilder auth = new StringBuilder();
                String login = authInfo.getLogin();
                if (Strings.isNotEmpty(login)) {
                    auth.append(login);
                }
                auth.append(':');
                String password = authInfo.getPassword();
                if (Strings.isNotEmpty(password)) {
                    auth.append(password);
                }

                byte[] encodedAuth = Base64.encodeBase64(auth.toString().getBytes(Charset.forName("ISO-8859-1")));
                String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.US_ASCII);

                method.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
            }
                break;
            case TOKEN: {
                String token = authInfo.getToken();
                String authHeader = "Token token=" + token;
                method.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
            }
                break;
            case NONE:
            default:
                break;
        }
    }

    private ImportedCalendar importCalendar(HttpEntity httpEntity) throws OXException {
        if (null == httpEntity) {
            return null;
        }
        ICalService iCalService = Services.getService(ICalService.class);
        ICalParameters parameters = iCalService.initParameters();
        parameters.set(ICalParameters.IGNORE_UNSET_PROPERTIES, Boolean.TRUE);
        try (InputStream inputStream = Streams.bufferedInputStreamFor(httpEntity.getContent())) {
            return iCalService.importICal(inputStream, parameters);
        } catch (UnsupportedOperationException | IOException e) {
            LOG.error("Error while processing the retrieved information:{}.", e.getMessage(), e);
            throw ICalProviderExceptionCodes.UNEXPECTED_FEED_ERROR.create(iCalFeedConfig.getFeedUrl(), e.getMessage());
        }
    }

    /**
     * Returns the calendar behind the given feed URL if available. If there is no update (based on etag and last modification header) the contained calendar will be <code>null</code>.
     *
     * @return GetResponse containing the feed content
     * @throws OXException
     */
    public GetResponse executeRequest() throws OXException {
        ICalProviderUtils.verifyURI(this.iCalFeedConfig.getFeedUrl());
        HttpGet request = prepareGet();

        HttpResponse response = null;
        try {
            response = getHttpClient().execute(request);
            int statusCode = assertStatusCode(response);
            if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                // OK, nothing was modified, no response body, return as is
                return new GetResponse(request.getURI(), GetResponseState.NOT_MODIFIED, response.getAllHeaders());
            } else if (statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE) {
                return new GetResponse(request.getURI(), GetResponseState.REMOVED, response.getAllHeaders());
            }
            // Prepare the response
            return prepareResponse(request.getURI(), response);
        } catch (ClientProtocolException e) {
            LOG.error("Error while processing the retrieved information:{}.", e.getMessage(), e);
            throw ICalProviderExceptionCodes.CLIENT_PROTOCOL_ERROR.create(e, e.getMessage());
        } catch (UnknownHostException | NoHttpResponseException e) {
            LOG.debug("Error while processing the retrieved information:{}.", e.getMessage(), e);
            throw ICalProviderExceptionCodes.NO_FEED.create(e, iCalFeedConfig.getFeedUrl());
        } catch (IOException e) {
            LOG.error("Error while processing the retrieved information:{}.", e.getMessage(), e);
            throw ICalProviderExceptionCodes.IO_ERROR.create(e, e.getMessage());
        } finally {
            HttpClients.close(request, response);
        }
    }

    private HttpClient getHttpClient() throws OXException {
        HttpClientService httpClientService = Services.getServiceLookup().getServiceSafe(HttpClientService.class);
        return httpClientService.getHttpClient("icalfeed");
    }

    private GetResponse prepareResponse(URI uri, HttpResponse httpResponse) throws OXException {
        GetResponse response = new GetResponse(uri, GetResponseState.MODIFIED, httpResponse.getAllHeaders());

        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) {
            return response;
        }
        long contentLength = entity.getContentLength();
        String contentLength2 = response.getContentLength();

        long allowedFeedSize = ICalCalendarProviderProperties.allowedFeedSize();
        if (contentLength > allowedFeedSize || (Strings.isNotEmpty(contentLength2) && Long.parseLong(contentLength2) > allowedFeedSize)) {
            throw ICalProviderExceptionCodes.FEED_SIZE_EXCEEDED.create(iCalFeedConfig.getFeedUrl(), L(allowedFeedSize), contentLength2 != null ? contentLength2 : L(contentLength));
        }
        response.setCalendar(importCalendar(entity));
        return response;
    }

    /**
     * Asserts the status code for any errors
     *
     * @param httpResponse The {@link HttpResponse}'s status code to assert
     * @return The status code
     * @throws OXException if an HTTP error is occurred (4xx or 5xx)
     */
    private int assertStatusCode(HttpResponse httpResponse) throws OXException {
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        // Assert the 4xx codes
        switch (statusCode) {
            case HttpStatus.SC_UNAUTHORIZED:
                throw unauthorizedException(httpResponse);
            case HttpStatus.SC_NOT_FOUND:
                throw ICalProviderExceptionCodes.NO_FEED.create(iCalFeedConfig.getFeedUrl());
        }
        if (statusCode >= 400 && statusCode <= 499) {
            throw ICalProviderExceptionCodes.UNEXPECTED_FEED_ERROR.create(iCalFeedConfig.getFeedUrl(), "Unknown server response. Status code " + statusCode);
        }

        // Assert the 5xx codes
        if (statusCode >= 500 && statusCode <= 599) {
            throw ICalProviderExceptionCodes.REMOTE_SERVER_ERROR.create(String.valueOf(httpResponse.getStatusLine()));
        }
        return statusCode;
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
     * Prepares an appropriate exception for a response with status <code>401 Unauthorized</code>.
     *
     * @param response The HTTP response to generate the exception for
     * @return An appropriate {@link OXException}
     */
    private OXException unauthorizedException(HttpResponse response) {
        String feedUrl = iCalFeedConfig.getFeedUrl();
        AuthInfo authInfo = iCalFeedConfig.getAuthInfo();

        boolean hadCredentials = null != authInfo && (Strings.isNotEmpty(authInfo.getPassword()) || Strings.isNotEmpty(authInfo.getToken()));
        String realm = getFirstHeaderElement(response, HttpHeaders.WWW_AUTHENTICATE, "Basic realm");
        if (null != realm && realm.contains("Share/Anonymous/")) {
            /*
             * anonymous, password-protected share
             */
            if (hadCredentials) {
                return ICalProviderExceptionCodes.PASSWORD_WRONG.create(feedUrl, String.valueOf(response.getStatusLine()), realm);
            }
            return ICalProviderExceptionCodes.PASSWORD_REQUIRED.create(feedUrl, String.valueOf(response.getStatusLine()), realm);
        }
        /*
         * generic credentials required, otherwise
         */
        if (hadCredentials) {
            if (iCalFeedConfig.getLastUpdated() > 0 || Strings.isNotEmpty(iCalFeedConfig.getEtag())) {
                return ICalProviderExceptionCodes.CREDENTIALS_CHANGED.create(feedUrl, String.valueOf(response.getStatusLine()), realm);
            }
            return ICalProviderExceptionCodes.CREDENTIALS_WRONG.create(feedUrl, String.valueOf(response.getStatusLine()), realm);
        }
        return ICalProviderExceptionCodes.CREDENTIALS_REQUIRED.create(feedUrl, String.valueOf(response.getStatusLine()), realm);
    }

    private static String getFirstHeaderElement(HttpResponse response, String headerName, String elementName) {
        Header header = response.getFirstHeader(headerName);
        if (null != header) {
            HeaderElement[] elements = header.getElements();
            if (null != elements && 0 < elements.length) {
                for (HeaderElement element : elements) {
                    if (elementName.equalsIgnoreCase(element.getName())) {
                        return element.getValue();
                    }
                }
            }
        }
        return null;
    }

}
