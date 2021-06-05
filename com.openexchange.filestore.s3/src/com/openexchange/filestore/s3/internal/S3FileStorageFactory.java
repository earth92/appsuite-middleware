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

package com.openexchange.filestore.s3.internal;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.openexchange.config.Interests;
import com.openexchange.exception.OXException;
import com.openexchange.filestore.FileStorageProvider;
import com.openexchange.filestore.InterestsAware;
import com.openexchange.filestore.s3.internal.client.S3ClientRegistry;
import com.openexchange.filestore.s3.internal.client.S3FileStorageClient;
import com.openexchange.filestore.s3.internal.config.S3ClientConfig;
import com.openexchange.java.Strings;
import com.openexchange.server.ServiceLookup;

/**
 * {@link S3FileStorageFactory}
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 */
public class S3FileStorageFactory implements FileStorageProvider, InterestsAware {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(S3FileStorageFactory.class);

    /**
     * The URI scheme identifying S3 file storages.
     */
    private static final String S3_SCHEME = "s3";

    /**
     * The file storage's ranking compared to other sharing the same URL scheme.
     */
    private static final int RANKING = 5634;

    private final S3ClientRegistry clientRegistry;
    private final ServiceLookup services;

    /**
     * Initializes a new {@link S3FileStorageFactory}.
     *
     * @param clientRegistry The {@link S3ClientRegistry}
     * @param services The service lookup
     */
    public S3FileStorageFactory(S3ClientRegistry clientRegistry, ServiceLookup services) {
        super();
        this.clientRegistry = clientRegistry;
        this.services = services;
    }

    @Override
    public Interests getInterests() {
        return clientRegistry.getInterests();
    }

    @Override
    public S3FileStorage getFileStorage(URI uri) throws OXException {
        try {
            LOG.debug("Initializing S3 client for {}", uri);
            /*
             * extract file storage ID from authority part of URI
             */
            String filestoreID = extractFilestoreID(uri);
            LOG.debug("Using \"{}\" as filestore ID.", filestoreID);

            S3ClientConfig clientConfig = S3ClientConfig.init(filestoreID, services);
            S3FileStorageClient client = clientRegistry.getOrCreate(clientConfig);
            LOG.debug("Using \"{}\" as bucket name for filestore \"{}\". Client is \"{}\" with scope \"{}\".",
                clientConfig.getBucketName(), filestoreID, client.getKey(), clientConfig.getClientScope());
            S3FileStorage s3FileStorage = new S3FileStorage(uri, extractFilestorePrefix(uri), clientConfig.getBucketName(), client);
            s3FileStorage.ensureBucket();
            return s3FileStorage;
        } catch (OXException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof AmazonS3Exception) {
                AmazonS3Exception s3Exception = (AmazonS3Exception) cause;
                if (s3Exception.getStatusCode() == 400) {
                    // throw a simple OXException here to be able forwarding this exception to RMI clients (Bug #42102)
                    throw new OXException(S3ExceptionCode.BadRequest.getNumber(), S3ExceptionMessages.BadRequest_MSG, s3Exception);
                }
            }

            throw ex;
        }
    }

    @Override
    public boolean supports(URI uri) {
         return null != uri && S3_SCHEME.equalsIgnoreCase(uri.getScheme());
    }

    @Override
    public int getRanking() {
        return RANKING;
    }

    // ----------------------------------------------------------- HELPERS -----------------------------------------------------------------

    /**
     * The expected pattern for file store names associated with a context - defined by
     * com.openexchange.filestore.FileStorages.getNameForContext(int) ,
     * so expect nothing else; e.g. <code>"57462_ctx_store"</code>
     */
    private static final Pattern CTX_STORE_PATTERN = Pattern.compile("(\\d+)_ctx_store");

    /**
     * The expected pattern for file store names associated with a user - defined by
     * com.openexchange.filestore.FileStorages.getNameForUser(int, int) ,
     * so expect nothing else; e.g. <code>"57462_ctx_5_user_store"</code>
     */
    private static final Pattern USER_STORE_PATTERN = Pattern.compile("(\\d+)_ctx_(\\d+)_user_store");

    /**
     * Extracts the file storage's prefix from the configured file storage URI, i.e. the 'path' part of the URI.
     *
     * @param uri The file storage URI
     * @return The prefix to use
     * @throws IllegalArgumentException If given URI contains no path component
     */
    private static String extractFilestorePrefix(URI uri) throws IllegalArgumentException {
        // Extract & prepare path to be used as prefix
        String path = uri.getPath();
        if (path == null) {
            throw new IllegalArgumentException("Path component of given URI is undefined: " + uri);
        }
        while (0 < path.length() && '/' == path.charAt(0)) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (path.endsWith("_ctx_store")) {
            // Expect context store identifier
            Matcher matcher = CTX_STORE_PATTERN.matcher(path);
            if (false == matcher.matches()) {
                throw new IllegalArgumentException("Path does not match the expected pattern \"\\d+_ctx_store\" in URI: " + uri);
            }
            return new StringBuilder(16).append(matcher.group(1)).append("ctxstore").toString();
        }

        if (path.endsWith("_user_store")) {
            // Expect user store identifier
            Matcher matcher = USER_STORE_PATTERN.matcher(path);
            if (false == matcher.matches()) {
                throw new IllegalArgumentException("Path does not match the expected pattern \"(\\d+)_ctx_(\\d+)_user_store\" in URI: " + uri);
            }
            return new StringBuilder(24).append(matcher.group(1)).append("ctx").append(matcher.group(2)).append("userstore").toString();
        }

        // Any path that serves as prefix; e.g. "photos"
        if (Strings.isEmpty(path)) {
            throw new IllegalArgumentException("Path is empty in URI: " + uri);
        }
        return sanitizePathForPrefix(path);
    }

    /**
     * Strips all characters from specified prefix path, which are no allows according to
     * <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/UsingMetadata.html">this article</a>
     *
     * @param path The path to sanitize
     * @return The sanitized path ready to be used as prefix
     */
    private static String sanitizePathForPrefix(String path) {
        StringBuilder sb = null;
        for (int k = path.length(), i = 0; k-- > 0; i++) {
            char ch = path.charAt(i);
            if (Strings.isAsciiLetterOrDigit(ch) || isAllowedSpecial(ch)) {
                // Append
                if (null != sb) {
                    sb.append(ch);
                }
            } else {
                // Not allowed in prefix
                if (null == sb) {
                    sb = new StringBuilder(path.length());
                    if (i > 0) {
                        sb.append(path, 0, i);
                    }
                }
            }
        }
        return null == sb ? path : sb.toString();
    }

    /**
     * Checks if the character is an allowed special character
     *
     * @param ch The character to check
     * @return true if it is an allowed special character, false otherwise
     */
    private static boolean isAllowedSpecial(char ch) {
        switch (ch) {
            case '!':
            case '-':
            case '_':
            case '.':
            case '*':
            case '\'':
            case '(':
            case ')':
                return true;
            default:
                return false;
        }
    }

    /**
     * Extracts the file storage ID from the configured file storage URI, i.e. the 'authority' part from the URI.
     *
     * @param uri The file storage URI
     * @return The file storage ID
     * @throws IllegalArgumentException If no valid ID could be extracted
     */
    private static String extractFilestoreID(URI uri) throws IllegalArgumentException {
        String authority = uri.getAuthority();
        if (null == authority) {
            throw new IllegalArgumentException("No 'authority' part specified in filestore URI");
        }
        while (0 < authority.length() && '/' == authority.charAt(0)) {
            authority = authority.substring(1);
        }
        if (0 == authority.length()) {
            throw new IllegalArgumentException("No 'authority' part specified in filestore URI");
        }
        return authority;
    }

}
