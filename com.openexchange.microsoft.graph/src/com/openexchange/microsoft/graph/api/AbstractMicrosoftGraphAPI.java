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

package com.openexchange.microsoft.graph.api;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.microsoft.graph.api.client.MicrosoftGraphRESTClient;
import com.openexchange.microsoft.graph.api.client.MicrosoftGraphRequest;
import com.openexchange.microsoft.graph.api.exception.MicrosoftGraphClientExceptionCodes;
import com.openexchange.rest.client.v2.RESTMethod;
import com.openexchange.rest.client.v2.RESTResponse;
import com.openexchange.rest.client.v2.entity.InputStreamEntity;
import com.openexchange.rest.client.v2.entity.JSONObjectEntity;

/**
 * {@link AbstractMicrosoftGraphAPI}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.2
 */
abstract class AbstractMicrosoftGraphAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMicrosoftGraphAPI.class);
    final MicrosoftGraphRESTClient client;
    static final String APPLICATION_JSON = "application/json";

    /**
     * Initialises a new {@link AbstractMicrosoftGraphAPI}.
     */
    public AbstractMicrosoftGraphAPI(MicrosoftGraphRESTClient client) {
        super();
        this.client = client;
    }

    //////////////////////////////// GET ///////////////////////////////////////

    /**
     * Gets the REST resource from the specified path
     *
     * @param accessToken The oauth access token
     * @param path The resource's path
     * @return A {@link JSONObject} with the resource
     * @throws OXException if an error is occurred
     */
    JSONObject getResource(String accessToken, String path) throws OXException {
        return getResource(accessToken, path, Collections.emptyMap());
    }

    /**
     * Gets the REST resource from the specified path and the specified query
     * parameters
     *
     * @param accessToken The oauth access token
     * @param path The resource's path
     * @param queryParams the request's query parameters
     * @return A {@link JSONObject} with the resource
     * @throws OXException if an error is occurred
     */
    JSONObject getResource(String accessToken, String path, Map<String, String> queryParams) throws OXException {
        return executeRequest(createRequest(RESTMethod.GET, accessToken, path, queryParams));
    }

    /**
     * Gets the stream from the specified path. Use to stream data from the
     * remote end-point to the client, i.e. download.
     *
     * @param path The path
     * @return The {@link InputStream}
     * @throws OXException if an error is occurred
     */
    InputStream getStream(String path) throws OXException {
        return client.download(() -> new HttpGet(path));
    }

    //////////////////////////////// POST ///////////////////////////////////////

    /**
     * Posts the specified {@link JSONObject} body to the specified path
     *
     * @param accessToken The oauth access token
     * @param path The path
     * @param body The body to post
     * @return A {@link JSONObject} with the resource metadata
     * @throws OXException if an error is occurred
     */
    JSONObject postResource(String accessToken, String path, JSONObject body) throws OXException {
        MicrosoftGraphRequest request = createRequest(RESTMethod.POST, accessToken, path);
        request.setBodyEntity(new JSONObjectEntity(body));
        return executeRequest(request);
    }

    //////////////////////////////// PUT ///////////////////////////////////////

    /**
     * Puts the specified {@link JSONObject} body to the specified path
     *
     * @param accessToken The oauth access token
     * @param path The path
     * @param body The body to post
     * @return A {@link JSONObject} with the resource metadata
     * @throws OXException if an error is occurred
     */
    JSONObject putResource(String accessToken, String path, String contentType, InputStream body) throws OXException {
        MicrosoftGraphRequest request = createRequest(RESTMethod.PUT, accessToken, path, contentType);
        request.setBodyEntity(new InputStreamEntity(body, contentType));
        return executeRequest(request);
    }

    //////////////////////////////// DELETE ///////////////////////////////////////

    /**
     * Deletes a resource
     *
     * @param accessToken The oauth access token
     * @param path The path
     * @throws OXException if an error is occurred
     */
    void deleteResource(String accessToken, String path) throws OXException {
        client.execute(createRequest(RESTMethod.DELETE, accessToken, path));
    }

    //////////////////////////////// PATCH ///////////////////////////////////////

    /**
     * Patches the specified {@link JSONObject} body to the specified path
     *
     * @param accessToken The oauth access token
     * @param path The path
     * @param body The body to post
     * @return A {@link JSONObject} with the resource metadata
     * @throws OXException if an error is occurred
     */
    JSONObject patchResource(String accessToken, String path, JSONObject body) throws OXException {
        MicrosoftGraphRequest request = createRequest(RESTMethod.PATCH, accessToken, path);
        request.setBodyEntity(new JSONObjectEntity(body));
        return executeRequest(request);
    }

    //////////////////////////////// HELPERS ///////////////////////////////////

    /**
     * Creates a {@link MicrosoftGraphRequest} with the specified {@link RESTMethod} and access token
     * for the specified end-point
     *
     * @param method the {@link RESTMethod}
     * @param accessToken The oauth access token
     * @param path The path
     * @return The {@link MicrosoftGraphRequest}
     */
    protected MicrosoftGraphRequest createRequest(RESTMethod method, String accessToken, String path) {
        return createRequest(method, accessToken, path, APPLICATION_JSON, Collections.emptyMap());
    }

    /**
     * Creates a {@link MicrosoftGraphRequest} with the specified {@link RESTMethod}, access token
     * and content type for the specified end-point
     *
     * @param method the {@link RESTMethod}
     * @param accessToken The oauth access token
     * @param path The path
     * @param contentType The Contenty-Type of the request body
     * @return The {@link MicrosoftGraphRequest}
     */
    private MicrosoftGraphRequest createRequest(RESTMethod method, String accessToken, String path, String contentType) {
        return createRequest(method, accessToken, path, contentType, Collections.emptyMap());
    }

    /**
     * Creates a {@link MicrosoftGraphRequest} with the specified {@link RESTMethod}, access token
     * and query parameters for the specified end-point
     *
     * @param method the {@link RESTMethod}
     * @param accessToken The oauth access token
     * @param path The path
     * @param queryParameters The query parameters
     * @return The {@link MicrosoftGraphRequest}
     */
    private MicrosoftGraphRequest createRequest(RESTMethod method, String accessToken, String path, Map<String, String> queryParameters) {
        return createRequest(method, accessToken, path, APPLICATION_JSON, queryParameters);
    }

    /**
     * Creates a {@link MicrosoftGraphRequest} with the specified {@link RESTMethod}, access token, content type
     * and query parameters for the specified end-point
     *
     * @param method the {@link RESTMethod}
     * @param accessToken The oauth access token
     * @param path The path
     * @param contentType The Content-Type of the request body
     * @param queryParameters The query parameters
     * @return The {@link MicrosoftGraphRequest}
     */
    private MicrosoftGraphRequest createRequest(RESTMethod method, String accessToken, String path, String contentType, Map<String, String> queryParameters) {
        MicrosoftGraphRequest request = new MicrosoftGraphRequest(method, path);
        request.setAccessToken(accessToken);
        request.setHeader(HttpHeaders.CONTENT_TYPE, Strings.isEmpty(contentType) ? APPLICATION_JSON : contentType);
        for (Entry<String, String> queryParam : queryParameters.entrySet()) {
            request.setQueryParameter(queryParam.getKey(), queryParam.getValue());
        }
        return request;
    }

    /**
     * Executes the specified {@link MicrosoftGraphRequest}
     *
     * @param request the request to execute
     * @return The response body as a {@link JSONObject}
     * @throws OXException if an error is occurred
     */
    private JSONObject executeRequest(MicrosoftGraphRequest request) throws OXException {
        RESTResponse restResponse = client.execute(request);
        if (restResponse.getResponseBody() == null) {
            throw MicrosoftGraphClientExceptionCodes.NO_JSON_OBJECT_IN_RESPONSE.create();
        }
        if (false == (restResponse.getResponseBody() instanceof JSONObject)) {
            LOGGER.debug("The response body does not contain a JSON object: {}", restResponse.getResponseBody());
            throw MicrosoftGraphClientExceptionCodes.RESPONSE_BODY_IS_NOT_JSON.create(restResponse.getResponseBody().getClass().getName());
        }
        return JSONObject.class.cast(restResponse.getResponseBody());
    }
}
