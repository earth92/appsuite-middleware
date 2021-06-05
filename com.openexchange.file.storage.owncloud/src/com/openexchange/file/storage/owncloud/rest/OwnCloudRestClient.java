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

package com.openexchange.file.storage.owncloud.rest;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.annotation.NonNull;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.owncloud.rest.OCShares.OCShare;
import com.openexchange.rest.client.httpclient.HttpClients;
import com.openexchange.rest.client.httpclient.ManagedHttpClient;
import com.openexchange.webdav.client.functions.ErrorAwareFunction;

/**
 *
 * {@link OwnCloudRestClient}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.4
 */
@SuppressWarnings("unused")
public class OwnCloudRestClient {

    private static final String OCS_APIREQUEST_HEADER = "OCS-APIRequest"; //Some OwnCloud variants, like nextcloud, require additional header

    private static final String CAPABILITIES_PATH = "/ocs/v1.php/cloud/capabilities?format=json";
    private static final String SHARES_FORMAT ="/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json&path=%s&reshares=true";
    private static final String CREATE_SHARE ="/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json";

    private static Cache<Object, OCCapabilities> cache;

    static {
        Cache<Object, OCCapabilities> cache;
        cache = CacheBuilder.<String, OCCapabilities>newBuilder()
          .expireAfterAccess(1, TimeUnit.DAYS)
          .build();
        OwnCloudRestClient.cache = cache;
    }

    private final ManagedHttpClient client;
    private final String host;
    private final HttpContext context;
    private final Callable<OCCapabilities> loader;

    /**
     * Initializes a new {@link OwnCloudRestClient}.
     *
     * @param client The {@link ManagedHttpClient} to use
     * @param host The base path
     * @param context The {@link HttpClientContext} to use for every request or null to use the default one
     */
    public OwnCloudRestClient(@NonNull ManagedHttpClient client, @NonNull String host, @NonNull HttpContext context) {
        super();
        this.client = client;
        this.host = host;
        this.context = context;

        loader = () -> performGet(CAPABILITIES_PATH, json -> {
            return OCCapabilities.parse(json);
        });
    }

    /**
     * Gets the capabilities of the owncloud server
     *
     * @return The {@link OCCapabilities}
     * @throws OXException
     */
    public OCCapabilities getCapabilities() throws OXException {
        try {
            return cache.get(host, loader);
        } catch (ExecutionException e) {
            throw (OXException) e.getCause();
        }
    }

    /**
     * Get the shares for the given file path
     *
     * @param filePath The file path
     * @return The {@link OCShares}
     * @throws OXException
     */
    public OCShares getShares(String filePath) throws OXException {
        String path = String.format(SHARES_FORMAT, filePath);
        return performGet(path, json -> {
            return OCShares.parse(json);
        });
    }

    /**
     * Creates the given share
     *
     * @param filePath The file path
     * @param share The {@link OCShare}
     * @return The {@link OCShares}
     * @throws OXException
     */
    public void createShare(String filePath, OCShare share) throws OXException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("shareType", String.valueOf(share.getShare_type())));
        params.add(new BasicNameValuePair("shareWith", share.getShare_with()));
        params.add(new BasicNameValuePair("permission", String.valueOf(share.getPermission())));
        params.add(new BasicNameValuePair("path", filePath));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Charset.forName("UTF-8"));
        performPost(CREATE_SHARE, entity, json -> {
            return OCShares.parse(json);
        });
    }

    /**
     * Performs a put operation on the given path and parses the response with the help of the given handler
     *
     * @param path The path
     * @param entity The  put {@link HttpEntity}
     * @param handler A ErrorAwareFunction which handles the json response
     * @return The parsed response
     * @throws OXException
     */
    <T> T performPut(String path, HttpEntity entity, ErrorAwareFunction<JSONObject, T> handler) throws OXException {
        HttpPut put = new HttpPut(getbasePath() + path);
        put.setEntity(entity);
        return perform(put, handler);
    }

    /**
     * Performs a post operation on the given path and parses the response with the help of the given handler
     *
     * @param path The path
     * @param entity The post {@link HttpEntity}
     * @param handler A ErrorAwareFunction which handles the json response
     * @return The parsed response
     * @throws OXException
     */
    <T> T performPost(String path, HttpEntity entity, ErrorAwareFunction<JSONObject, T> handler) throws OXException {
        HttpPost post = new HttpPost(getbasePath() + path);
        post.setEntity(entity);
        return perform(post, handler);
    }

    /**
     * Performs a get operation on the given path and parses the response with the help of the given handler
     *
     * @param path The path to get
     * @param handler A ErrorAwareFunction which handles the json response
     * @return The parsed object
     * @throws OXException
     */
    <T> T performGet(String path, ErrorAwareFunction<JSONObject, T> handler) throws OXException {
        HttpGet get = new HttpGet(getbasePath() + path);
        return perform(get, handler);
    }

    /**
     * Performs the {@link HttpUriRequest}
     *
     * @param <T> The response type
     * @param request The HTTP request to perform
     * @param handler The response handler
     * @return The parsed response
     * @throws OXException
     */
    private final <T> T perform(HttpRequestBase request, ErrorAwareFunction<JSONObject, T> handler) throws OXException {
        HttpResponse response = null;
        try {
            request.addHeader(OCS_APIREQUEST_HEADER, "true");
            response = client.execute(request, context);
            String json = EntityUtils.toString(response.getEntity());
            return handler.apply(new JSONObject(json));
        } catch (JSONException e) {
            throw FileStorageExceptionCodes.JSON_ERROR.create(e.getMessage(), e);
        } catch (ClientProtocolException e) {
            throw FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e.getMessage(), e);
        } catch (IOException e) {
            throw FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e.getMessage(), e);
        } finally {
            HttpClients.close(request, response);
        }
    }

    /**
     * Gets the base path for the rest interface
     *
     * @return The base path
     */
    private String getbasePath() {
        return host;
    }

}
