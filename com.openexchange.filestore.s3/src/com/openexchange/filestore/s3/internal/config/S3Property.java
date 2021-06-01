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

package com.openexchange.filestore.s3.internal.config;

import static com.openexchange.java.Autoboxing.I;
import java.util.Arrays;
import java.util.Optional;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.lean.Property;

/**
 * {@link S3Property}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.1
 */
public enum S3Property implements Property {

    /**
     * "com.openexchange.filestore.s3.[filestoreID].bucketName"
     */
    BUCKET_NAME("bucketName"),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].endpoint"
     */
    ENDPOINT(S3ClientProperty.ENDPOINT),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].region"
     */
    REGION(S3ClientProperty.REGION),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].pathStyleAccess"
     */
    PATH_STYLE_ACCESS(S3ClientProperty.PATH_STYLE_ACCESS),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].accessKey"
     */
    ACCESS_KEY(S3ClientProperty.ACCESS_KEY),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].secretKey"
     */
    SECRET_KEY(S3ClientProperty.SECRET_KEY),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].encryption"
     */
    ENCRYPTION(S3ClientProperty.ENCRYPTION),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].signerOverride"
     */
    SIGNER_OVERRIDE(S3ClientProperty.SIGNER_OVERRIDE),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].chunkSize"
     */
    CHUNK_SIZE(S3ClientProperty.CHUNK_SIZE),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].encryption.rsa.keyStore"
     */
    RSA_KEYSTORE(S3ClientProperty.RSA_KEYSTORE),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].encryption.rsa.password"
     */
    RSA_PASSWORD(S3ClientProperty.RSA_PASSWORD),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].connectTimeout"
     */
    CONNECT_TIMEOUT(S3ClientProperty.CONNECT_TIMEOUT),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].readTimeout"
     */
    READ_TIMEOUT(S3ClientProperty.READ_TIMEOUT),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].maxConnectionPoolSize"
     */
    MAX_CONNECTION_POOL_SIZE(S3ClientProperty.MAX_CONNECTION_POOL_SIZE),
    /**
     * "com.openexchange.filestore.s3.metricCollection"
     */
    METRIC_COLLECTION("com.openexchange.filestore.s3.", "metricCollection", Boolean.FALSE),
    /**
     * "com.openexchange.filestore.s3.[filestoreID].credentialsSource"
     */
    CREDENTIALS_SOURCE(S3ClientProperty.CREDENTIALS_SOURCE),
    /**
     * The max. number of clients that might be configured to have per-client monitoring enabled. This has
     * an all-or-nothing semantics, i.e. if more clients are configured than this limit, no client is
     * monitored at all. Otherwise, all clients are monitored. Per-filestore clients
     * based on "com.openexchange.filestore.s3.[filestoreID]." properties alone are never monitored.
     * <p>
     * com.openexchange.filestore.s3.maxNumberOfMonitoredClients
     */
    MAX_NUMBER_OF_MONITORED_CLIENTS("com.openexchange.filestore.s3.", "maxNumberOfMonitoredClients", I(20)),

    ;

    public static final String OPTIONAL_NAME = "filestoreID";

    /** "com.openexchange.filestore.s3." **/
    private static final String PREFIX = "com.openexchange.filestore.s3.";

    /** "com.openexchange.filestore.s3.[filestoreID]." **/
    private static final String FQN_PREFIX = PREFIX + "[" + OPTIONAL_NAME + "].";

    /** The according client property, if any */
    private final S3ClientProperty clientProperty;
    private final String shortName;
    private final String fqn;
    private final Object defaultValue;


    /**
     * Initializes a new {@link S3Property}.
     */
    private S3Property(String propName) {
        this(propName, null);
    }

    /**
     * Initializes a new {@link S3Property}.
     */
    private S3Property(String propName, Object defaultValue) {
        this(FQN_PREFIX, propName, defaultValue);
    }

    /**
     * Initializes a new {@link S3Property}.
     */
    private S3Property(String prefix, String propName, Object defaultValue) {
        this.clientProperty = null;
        this.shortName = propName;
        this.fqn = prefix + propName;
        this.defaultValue = defaultValue;
    }

    /**
     * Initializes a new {@link S3Property} which at runtime overrides a certain {@link S3ClientProperty},
     * if set.
     *
     * @param property
     */
    private S3Property(S3ClientProperty property) {
        this.clientProperty = property;
        this.shortName = property.getShortName();
        this.fqn = FQN_PREFIX + property.getShortName();
        this.defaultValue = property.getDefaultValue();
    }

    @Override
    public String getFQPropertyName() {
        return fqn;
    }

    @Override
    public Object getDefaultValue() {
        return defaultValue;
    }

    public String getShortName() {
        return shortName;
    }

    /**
     * Gets the per-filestore property for a given client property, if one exists.
     *
     * @param property The {@link S3ClientProperty}
     * @return The property or {@code null}
     */
    public static Optional<S3Property> of(S3ClientProperty property) {
        return Arrays.asList(values()).parallelStream().filter((p) -> p.clientProperty == property).findFirst();
    }

    /**
     * Gets a wildcard string that can be used to signal {@link Interests} by {@link Reloadable}s
     * to get callbacks whenever a configuration property was changed and reloaded.
     */
    public static String getInterestsWildcard() {
        return PREFIX + "*";
    }

}
