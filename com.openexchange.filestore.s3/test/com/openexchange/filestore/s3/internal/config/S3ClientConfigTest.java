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

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static junitx.framework.Assert.assertNotEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.mockito.Mockito;
import com.google.common.collect.ImmutableMap;
import com.openexchange.config.PropertyFilter;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.config.lean.Property;
import com.openexchange.exception.OXException;
import com.openexchange.server.SimpleServiceLookup;

/**
 * Tests different file store configs to ensure functioning with shared client configs
 * and full backwards-compatibility.
 *
 * Topology:
 *
 * registerfilestore -t s3://ox-filestore-ceph-1-1-1
 * registerfilestore -t s3://ox-filestore-ceph-1-1-2
 * registerfilestore -t s3://ox-filestore-ceph-1-2-1
 * registerfilestore -t s3://ox-filestore-ceph-1-2-10
 * registerfilestore -t s3://ox-filestore-ceph-2-1-1
 *
 * With filestore IDs of format {@code ox-filestore-ceph-[STORAGE_SYSTEM]-[STORAGE_USER]-[FILESTORE_NUMBER]}
 *
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.10.4
 */
public class S3ClientConfigTest {

    /**
     * The S3ClientConfigTest.java.
     */
    private static final String CLIENT_CEPH1_USER1 = "ceph-1-1";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String FILESTORE_CEPH2_USER1_WILDCARD = "ox-filestore-ceph-2-1-*";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String FILESTORE_CEPH1_USER2_WILDCARD = "ox-filestore-ceph-1-2-*";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String FILESTORE_CEPH1_USER1_WILDCARD = "ox-filestore-ceph-1-1-*";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String CHUNK_SIZE_4MB = "4MB";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String ENCRYPTION_SSE3 = "sse3";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String SECRET_CEPH2_USER1 = "secret-ceph2-user1";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String ACCESS_CEPH2_USER1 = "ceph2-user1";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String ENDPOINT_CEPH2 = "http://ceph2.storage.int/";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String CHUNK_SIZE_5MB = "5MB";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String FALSE = "false";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String ENCRYPTION_RSA = "rsa";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String SECRET_CEPH1_USER2 = "secret-ceph1-user2";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String ACCESS_CEPH1_USER2 = "ceph1-user2";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String BUCKET_CEPH1_USER1_NO1 = "ox-filestore-ceph-1-1-1";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String CHUNK_SIZE_8MB = "8MB";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String TRUE = "true";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String ENCRYPTION_NONE = "none";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String SECRET_CEPH1_USER1 = "secret-ceph1-user1";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String ACCESS_CEPH1_USER1 = "ceph1-user1";
    /**
     * The S3ClientConfigTest.java.
     */
    private static final String ENDPOINT_CEPH1 = "http://ceph1.storage.int/";
    private static final String BUCKET_CEPH1_USER2_NO10 = "ox-filestore-ceph-1-2-10";
    private static final Object CLIENT_CEPH1_USER2 = "ceph-1-2";
    private static final String BUCKET_CEPH2_USER1_NO1 = "ox-filestore-ceph-2-1-1";
    private static final Object CLIENT_CEPH2_USER1 = "ceph-2-1";
    private static final String BUCKET_CEPH1_USER1_NO2 = "ox-filestore-ceph-1-1-2";
    private static final String BUCKET_CEPH1_USER2_NO1 = "ox-filestore-ceph-1-2-1";

    @Test
    public void clientsOnlyWithBucketWildcardsOnlyByFilestoreIDsOnly() throws Exception {
        Map<String, String> config = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", FILESTORE_CEPH1_USER1_WILDCARD)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", FILESTORE_CEPH1_USER2_WILDCARD)
            .put("com.openexchange.filestore.s3client.ceph-2-1.buckets", FILESTORE_CEPH2_USER1_WILDCARD)
            .build();

        LeanConfigurationService configService = configServiceFor(config);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        String filestoreID = BUCKET_CEPH1_USER1_NO1;
        assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH1_USER2_NO10;
        assertClientSettings_CEPH1_USER2(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH2_USER1_NO1;
        assertClientSettings_CEPH2_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
    }

    @Test
    public void clientsOnlyWithBucketWildcardsOnlyForManyFilestores() throws Exception {
        Map<String, String> config = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", FILESTORE_CEPH1_USER1_WILDCARD)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", FILESTORE_CEPH1_USER2_WILDCARD)
            .put("com.openexchange.filestore.s3client.ceph-2-1.buckets", FILESTORE_CEPH2_USER1_WILDCARD)
            .build();

        LeanConfigurationService configService = configServiceFor(config);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        for (int storage = 1; storage <= 2; storage++) {
            for (int user = 1; user <= 2; user++) {
                for (int no = 1; no <= 1001; no++) {
                    String filestoreID = "ox-filestore-ceph-" + storage + "-" + user + "-" + no;
                    if (storage == 1) {
                        if (user == 1) {
                            assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
                        } else {
                            assertClientSettings_CEPH1_USER2(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
                        }
                    } else {
                        if (user == 1) {
                            assertClientSettings_CEPH2_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
                        }
                    }
                }
            }
        }

        String filestoreID = BUCKET_CEPH1_USER1_NO1;
        assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH1_USER2_NO10;
        assertClientSettings_CEPH1_USER2(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH2_USER1_NO1;
        assertClientSettings_CEPH2_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
    }

    @Test
    public void clientsOnlyWithBucketListsOnlyByFilestoreIDsOnly() throws Exception {
        Map<String, String> config = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", BUCKET_CEPH1_USER1_NO1 + ", " + BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", BUCKET_CEPH1_USER2_NO1  + ", " +  BUCKET_CEPH1_USER2_NO10)
            .put("com.openexchange.filestore.s3client.ceph-2-1.buckets", BUCKET_CEPH2_USER1_NO1)
            .build();

        LeanConfigurationService configService = configServiceFor(config);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        String filestoreID = BUCKET_CEPH1_USER1_NO1;
        assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
        filestoreID = BUCKET_CEPH1_USER1_NO2;
        assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH1_USER2_NO1;
        assertClientSettings_CEPH1_USER2(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
        filestoreID = BUCKET_CEPH1_USER2_NO10;
        assertClientSettings_CEPH1_USER2(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH2_USER1_NO1;
        assertClientSettings_CEPH2_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
    }

    @Test
    public void clientsOnlyWithBucketListsOnlyByBucketNames() throws Exception {
        Map<String, String> config = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", BUCKET_CEPH1_USER1_NO1 + ", " + BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", BUCKET_CEPH1_USER2_NO1  + ", " +  BUCKET_CEPH1_USER2_NO10)
            .put("com.openexchange.filestore.s3client.ceph-2-1.buckets", BUCKET_CEPH2_USER1_NO1)
            .put("com.openexchange.filestore.s3.ox-filestore-01.bucketName", BUCKET_CEPH1_USER1_NO1)
            .put("com.openexchange.filestore.s3.ox-filestore-02.bucketName", BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3.ox-filestore-03.bucketName", BUCKET_CEPH1_USER2_NO1)
            .put("com.openexchange.filestore.s3.ox-filestore-04.bucketName", BUCKET_CEPH1_USER2_NO10)
            .put("com.openexchange.filestore.s3.ox-filestore-05.bucketName", BUCKET_CEPH2_USER1_NO1)
            .build();

        LeanConfigurationService configService = configServiceFor(config);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        String filestoreID = "ox-filestore-01";
        assertClientSettings_CEPH1_USER1(BUCKET_CEPH1_USER1_NO1, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
        filestoreID = "ox-filestore-02";
        assertClientSettings_CEPH1_USER1(BUCKET_CEPH1_USER1_NO2, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = "ox-filestore-03";
        assertClientSettings_CEPH1_USER2(BUCKET_CEPH1_USER2_NO1, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
        filestoreID = "ox-filestore-04";
        assertClientSettings_CEPH1_USER2(BUCKET_CEPH1_USER2_NO10, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = "ox-filestore-05";
        assertClientSettings_CEPH2_USER1(BUCKET_CEPH2_USER1_NO1, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
    }

    @Test
    public void filestoresOnly() throws Exception {
        Map<String, String> config = ImmutableMap.<String, String>builder()
             // Storage #1; User #1; NO #1
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.bucketName", BUCKET_CEPH1_USER1_NO1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.endpoint", ENDPOINT_CEPH1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.accessKey", ACCESS_CEPH1_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.secretKey", SECRET_CEPH1_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.encryption", ENCRYPTION_NONE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.pathStyleAccess", TRUE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.chunkSize", CHUNK_SIZE_8MB)
             // Storage #1; User #1; NO #2
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.bucketName", BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.endpoint", ENDPOINT_CEPH1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.accessKey", ACCESS_CEPH1_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.secretKey", SECRET_CEPH1_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.encryption", ENCRYPTION_NONE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.pathStyleAccess", TRUE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.chunkSize", CHUNK_SIZE_8MB)
             // Storage #1; User #2; NO #1
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-1.bucketName", BUCKET_CEPH1_USER2_NO1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-1.endpoint", ENDPOINT_CEPH1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-1.accessKey", ACCESS_CEPH1_USER2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-1.secretKey", SECRET_CEPH1_USER2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-1.encryption", ENCRYPTION_RSA)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-1.pathStyleAccess", FALSE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-1.chunkSize", CHUNK_SIZE_5MB)
             // Storage #1; User #2; NO #10
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.bucketName", BUCKET_CEPH1_USER2_NO10)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.endpoint", ENDPOINT_CEPH1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.accessKey", ACCESS_CEPH1_USER2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.secretKey", SECRET_CEPH1_USER2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.encryption", ENCRYPTION_RSA)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.pathStyleAccess", FALSE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.chunkSize", CHUNK_SIZE_5MB)
             // Storage #2; User #1; NO #1
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.bucketName", BUCKET_CEPH2_USER1_NO1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.endpoint", ENDPOINT_CEPH2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.accessKey", ACCESS_CEPH2_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.secretKey", SECRET_CEPH2_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.encryption", ENCRYPTION_SSE3)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.pathStyleAccess", TRUE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.chunkSize", CHUNK_SIZE_4MB)
            .build();

        LeanConfigurationService configService = configServiceFor(config);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        String filestoreID = BUCKET_CEPH1_USER1_NO1;
        assertClientSettings_CEPH1_USER1(BUCKET_CEPH1_USER1_NO1, S3ClientScope.DEDICATED, S3ClientConfig.init(filestoreID, services));
        filestoreID = BUCKET_CEPH1_USER1_NO2;
        assertClientSettings_CEPH1_USER1(BUCKET_CEPH1_USER1_NO2, S3ClientScope.DEDICATED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH1_USER2_NO1;
        assertClientSettings_CEPH1_USER2(BUCKET_CEPH1_USER2_NO1, S3ClientScope.DEDICATED, S3ClientConfig.init(filestoreID, services));
        filestoreID = BUCKET_CEPH1_USER2_NO10;
        assertClientSettings_CEPH1_USER2(BUCKET_CEPH1_USER2_NO10, S3ClientScope.DEDICATED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH2_USER1_NO1;
        assertClientSettings_CEPH2_USER1(BUCKET_CEPH2_USER1_NO1, S3ClientScope.DEDICATED, S3ClientConfig.init(filestoreID, services));
    }

    @Test
    public void filestoresAndClientsMixed() throws OXException {
        Map<String, String> config = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", BUCKET_CEPH1_USER1_NO1 + ", " + BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", BUCKET_CEPH1_USER2_NO1)

             // Filestores
             // Storage #1; User #2; NO #10
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.bucketName", BUCKET_CEPH1_USER2_NO10)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.endpoint", ENDPOINT_CEPH1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.accessKey", ACCESS_CEPH1_USER2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.secretKey", SECRET_CEPH1_USER2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.encryption", ENCRYPTION_RSA)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.pathStyleAccess", FALSE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-2-10.chunkSize", CHUNK_SIZE_5MB)
             // Storage #2; User #1; NO #1
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.bucketName", BUCKET_CEPH2_USER1_NO1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.endpoint", ENDPOINT_CEPH2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.accessKey", ACCESS_CEPH2_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.secretKey", SECRET_CEPH2_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.encryption", ENCRYPTION_SSE3)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.pathStyleAccess", TRUE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-2-1-1.chunkSize", CHUNK_SIZE_4MB)


            .build();

        LeanConfigurationService configService = configServiceFor(config);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        // Clients
        String filestoreID = BUCKET_CEPH1_USER1_NO1;
        assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
        filestoreID = BUCKET_CEPH1_USER1_NO2;
        assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH1_USER2_NO1;
        assertClientSettings_CEPH1_USER2(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        // Filestores
        filestoreID = BUCKET_CEPH1_USER2_NO10;
        assertClientSettings_CEPH1_USER2(filestoreID, S3ClientScope.DEDICATED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH2_USER1_NO1;
        assertClientSettings_CEPH2_USER1(filestoreID, S3ClientScope.DEDICATED, S3ClientConfig.init(filestoreID, services));
    }

    @Test
    public void filestoresAndClientsMixedConflicting() throws OXException {
        Map<String, String> config = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", BUCKET_CEPH1_USER1_NO1 + ", " + BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", BUCKET_CEPH1_USER2_NO1)

             // Filestores
             // Storage #1; User #1; NO #1
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.bucketName", BUCKET_CEPH1_USER1_NO1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.endpoint", ENDPOINT_CEPH2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.accessKey", ACCESS_CEPH2_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.secretKey", SECRET_CEPH2_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.encryption", ENCRYPTION_NONE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.pathStyleAccess", TRUE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.chunkSize", CHUNK_SIZE_8MB)
             // Storage #1; User #1; NO #2
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.bucketName", BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.endpoint", ENDPOINT_CEPH2)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.accessKey", ACCESS_CEPH2_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.secretKey", SECRET_CEPH2_USER1)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.encryption", ENCRYPTION_NONE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.pathStyleAccess", TRUE)
            .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.chunkSize", CHUNK_SIZE_8MB)

            .build();

        LeanConfigurationService configService = configServiceFor(config);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        // Clients
        String filestoreID = BUCKET_CEPH1_USER1_NO1;
        assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
        filestoreID = BUCKET_CEPH1_USER1_NO2;
        assertClientSettings_CEPH1_USER1(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));

        filestoreID = BUCKET_CEPH1_USER2_NO1;
        assertClientSettings_CEPH1_USER2(filestoreID, S3ClientScope.SHARED, S3ClientConfig.init(filestoreID, services));
    }

    @Test
    public void fingerprintChangesForClient() throws Exception {
        ImmutableMap<String, String> oldConfig = ImmutableMap.<String, String>builderWithExpectedSize(16)
            // Storage #1; User #1
           .put("com.openexchange.filestore.s3client.ceph-1-1.endpoint", ENDPOINT_CEPH1)
           .put("com.openexchange.filestore.s3client.ceph-1-1.accessKey", ACCESS_CEPH1_USER1)
           .put("com.openexchange.filestore.s3client.ceph-1-1.secretKey", SECRET_CEPH1_USER1)
           .put("com.openexchange.filestore.s3client.ceph-1-1.encryption", ENCRYPTION_NONE)
           .put("com.openexchange.filestore.s3client.ceph-1-1.pathStyleAccess", TRUE)
           .put("com.openexchange.filestore.s3client.ceph-1-1.chunkSize", CHUNK_SIZE_8MB)
           .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", BUCKET_CEPH1_USER1_NO1 + ", " + BUCKET_CEPH1_USER1_NO2)
            // Storage #1; User #2
           .put("com.openexchange.filestore.s3client.ceph-1-2.endpoint", ENDPOINT_CEPH1)
           .put("com.openexchange.filestore.s3client.ceph-1-2.accessKey", ACCESS_CEPH1_USER2)
           .put("com.openexchange.filestore.s3client.ceph-1-2.secretKey", SECRET_CEPH1_USER2)
           .put("com.openexchange.filestore.s3client.ceph-1-2.encryption", ENCRYPTION_RSA)
           .put("com.openexchange.filestore.s3client.ceph-1-2.pathStyleAccess", FALSE)
           .put("com.openexchange.filestore.s3client.ceph-1-2.chunkSize", CHUNK_SIZE_5MB)
           .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", BUCKET_CEPH1_USER2_NO1  + ", " +  BUCKET_CEPH1_USER2_NO10)
           .build();

        LeanConfigurationService configService = configServiceFor(oldConfig);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        S3ClientConfig clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        int oldFingerprint = clientConfig.getFingerprint();

        // override credentials and verify that fingerprint changed
        Map<String, String> newConfig = new HashMap<>(oldConfig);
        newConfig.put("com.openexchange.filestore.s3client.ceph-1-1.accessKey", "new-access-key-to-verify-fingerprint-change");
        newConfig.put("com.openexchange.filestore.s3client.ceph-1-1.secretKey", "new-secret-key-to-verify-fingerprint-change");
        configService = configServiceFor(newConfig);
        services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        assertNotEquals(oldFingerprint, clientConfig.getFingerprint());
    }

    @Test
    public void fingerprintChangesForFilestore() throws Exception {
        Map<String, String> oldConfig = ImmutableMap.<String, String>builder()
            // Storage #1; User #1; NO #1
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.bucketName", BUCKET_CEPH1_USER1_NO1)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.endpoint", ENDPOINT_CEPH1)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.accessKey", ACCESS_CEPH1_USER1)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.secretKey", SECRET_CEPH1_USER1)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.encryption", ENCRYPTION_NONE)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.pathStyleAccess", TRUE)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.chunkSize", CHUNK_SIZE_8MB)
            // Storage #1; User #1; NO #2
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.bucketName", BUCKET_CEPH1_USER1_NO2)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.endpoint", ENDPOINT_CEPH1)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.accessKey", ACCESS_CEPH1_USER1)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.secretKey", SECRET_CEPH1_USER1)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.encryption", ENCRYPTION_NONE)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.pathStyleAccess", TRUE)
           .put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-2.chunkSize", CHUNK_SIZE_8MB)
           .build();

        LeanConfigurationService configService = configServiceFor(oldConfig);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        S3ClientConfig clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        int oldFingerprint = clientConfig.getFingerprint();

        // override credentials and verify that fingerprint changed
        Map<String, String> newConfig = new HashMap<>(oldConfig);
        newConfig.put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.accessKey", "new-access-key-to-verify-fingerprint-change");
        newConfig.put("com.openexchange.filestore.s3.ox-filestore-ceph-1-1-1.secretKey", "new-secret-key-to-verify-fingerprint-change");
        configService = configServiceFor(newConfig);
        services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        assertNotEquals(oldFingerprint, clientConfig.getFingerprint());
    }

    @Test
    public void metricCollectionAndClientCount() throws Exception {
        Map<String, String> config = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", BUCKET_CEPH1_USER1_NO1 + ", " + BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", BUCKET_CEPH1_USER2_NO1  + ", " +  BUCKET_CEPH1_USER2_NO10)
            .put("com.openexchange.filestore.s3client.ceph-2-1.buckets", BUCKET_CEPH2_USER1_NO1)
            .put("com.openexchange.filestore.s3.metricCollection", Boolean.TRUE.toString())
            .put("com.openexchange.filestore.s3.maxNumberOfMonitoredClients", "21")
            .build();

        LeanConfigurationService configService = configServiceFor(config);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        S3ClientConfig clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        assertTrue(clientConfig.enableMetricCollection());
        assertEquals(3, clientConfig.getNumberOfConfiguredClients());
        assertEquals(21, clientConfig.getMaxNumberOfMonitoredClients());
    }

    @Test
    public void enableMetricCollectionChangesFingerprint() throws Exception {
        Map<String, String> oldConfig = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", BUCKET_CEPH1_USER1_NO1 + ", " + BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", BUCKET_CEPH1_USER2_NO1  + ", " +  BUCKET_CEPH1_USER2_NO10)
            .put("com.openexchange.filestore.s3client.ceph-2-1.buckets", BUCKET_CEPH2_USER1_NO1)
            .put("com.openexchange.filestore.s3.metricCollection", Boolean.FALSE.toString())
            .build();

        LeanConfigurationService configService = configServiceFor(oldConfig);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        S3ClientConfig clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        int oldFingerprint = clientConfig.getFingerprint();

        // override credentials and verify that fingerprint changed
        Map<String, String> newConfig = new HashMap<>(oldConfig);
        newConfig.put("com.openexchange.filestore.s3.metricCollection", Boolean.TRUE.toString());
        configService = configServiceFor(newConfig);
        services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        assertNotEquals(oldFingerprint, clientConfig.getFingerprint());
    }

    @Test
    public void changingMaxMonitoredClientsChangesFingerprint() throws Exception {
        Map<String, String> oldConfig = ImmutableMap.<String, String>builder()
            .putAll(getClientOnlyConfig())
            .put("com.openexchange.filestore.s3client.ceph-1-1.buckets", BUCKET_CEPH1_USER1_NO1 + ", " + BUCKET_CEPH1_USER1_NO2)
            .put("com.openexchange.filestore.s3client.ceph-1-2.buckets", BUCKET_CEPH1_USER2_NO1  + ", " +  BUCKET_CEPH1_USER2_NO10)
            .put("com.openexchange.filestore.s3client.ceph-2-1.buckets", BUCKET_CEPH2_USER1_NO1)
            .put("com.openexchange.filestore.s3.metricCollection", Boolean.TRUE.toString())
            .build();

        LeanConfigurationService configService = configServiceFor(oldConfig);
        SimpleServiceLookup services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        S3ClientConfig clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        int oldFingerprint = clientConfig.getFingerprint();

        // override credentials and verify that fingerprint changed
        Map<String, String> newConfig = new HashMap<>(oldConfig);
        newConfig.put("com.openexchange.filestore.s3.maxNumberOfMonitoredClients", "21");
        configService = configServiceFor(newConfig);
        services = new SimpleServiceLookup();
        services.add(LeanConfigurationService.class, configService);

        clientConfig = S3ClientConfig.init(BUCKET_CEPH1_USER1_NO1, services);
        assertNotEquals(oldFingerprint, clientConfig.getFingerprint());
    }

    private void assertClientSettings_CEPH1_USER1(String bucketName, S3ClientScope scope, S3ClientConfig clientConfig) {
        assertEquals(scope, clientConfig.getClientScope());
        if (scope.isShared()) {
            assertTrue(clientConfig.getClientID().isPresent());
            assertEquals(CLIENT_CEPH1_USER1, clientConfig.getClientID().get());
        } else {
            assertFalse(clientConfig.getClientID().isPresent());
        }
        assertEquals(bucketName, clientConfig.getBucketName());
        assertEquals(ENDPOINT_CEPH1, clientConfig.getProperty(S3ClientProperty.ENDPOINT).getValue());
        assertEquals(ACCESS_CEPH1_USER1, clientConfig.getProperty(S3ClientProperty.ACCESS_KEY).getValue());
        assertEquals(SECRET_CEPH1_USER1, clientConfig.getProperty(S3ClientProperty.SECRET_KEY).getValue());
        assertEquals(ENCRYPTION_NONE, clientConfig.getProperty(S3ClientProperty.ENCRYPTION).getValue());
        assertEquals(TRUE, clientConfig.getProperty(S3ClientProperty.PATH_STYLE_ACCESS).getValue());
        assertEquals(CHUNK_SIZE_8MB, clientConfig.getProperty(S3ClientProperty.CHUNK_SIZE).getValue());
    }

    private void assertClientSettings_CEPH1_USER2(String bucketName, S3ClientScope scope, S3ClientConfig clientConfig) {
        assertEquals(scope, clientConfig.getClientScope());
        if (scope.isShared()) {
            assertTrue(clientConfig.getClientID().isPresent());
            assertEquals(CLIENT_CEPH1_USER2, clientConfig.getClientID().get());
        } else {
            assertFalse(clientConfig.getClientID().isPresent());
        }
        assertEquals(bucketName, clientConfig.getBucketName());
        assertEquals(ENDPOINT_CEPH1, clientConfig.getProperty(S3ClientProperty.ENDPOINT).getValue());
        assertEquals(ACCESS_CEPH1_USER2, clientConfig.getProperty(S3ClientProperty.ACCESS_KEY).getValue());
        assertEquals(SECRET_CEPH1_USER2, clientConfig.getProperty(S3ClientProperty.SECRET_KEY).getValue());
        assertEquals(ENCRYPTION_RSA, clientConfig.getProperty(S3ClientProperty.ENCRYPTION).getValue());
        assertEquals(FALSE, clientConfig.getProperty(S3ClientProperty.PATH_STYLE_ACCESS).getValue());
        assertEquals(CHUNK_SIZE_5MB, clientConfig.getProperty(S3ClientProperty.CHUNK_SIZE).getValue());
    }

    private void assertClientSettings_CEPH2_USER1(String bucketName, S3ClientScope scope, S3ClientConfig clientConfig) {
        assertEquals(scope, clientConfig.getClientScope());
        if (scope.isShared()) {
            assertTrue(clientConfig.getClientID().isPresent());
            assertEquals(CLIENT_CEPH2_USER1, clientConfig.getClientID().get());
        } else {
            assertFalse(clientConfig.getClientID().isPresent());
        }
        assertEquals(bucketName, clientConfig.getBucketName());
        assertEquals(ENDPOINT_CEPH2, clientConfig.getProperty(S3ClientProperty.ENDPOINT).getValue());
        assertEquals(ACCESS_CEPH2_USER1, clientConfig.getProperty(S3ClientProperty.ACCESS_KEY).getValue());
        assertEquals(SECRET_CEPH2_USER1, clientConfig.getProperty(S3ClientProperty.SECRET_KEY).getValue());
        assertEquals(ENCRYPTION_SSE3, clientConfig.getProperty(S3ClientProperty.ENCRYPTION).getValue());
        assertEquals(TRUE, clientConfig.getProperty(S3ClientProperty.PATH_STYLE_ACCESS).getValue());
        assertEquals(CHUNK_SIZE_4MB, clientConfig.getProperty(S3ClientProperty.CHUNK_SIZE).getValue());
    }

    private Map<String, String> getClientOnlyConfig() {
        return ImmutableMap.<String, String>builderWithExpectedSize(18)
            // Storage #1; User #1
           .put("com.openexchange.filestore.s3client.ceph-1-1.endpoint", ENDPOINT_CEPH1)
           .put("com.openexchange.filestore.s3client.ceph-1-1.accessKey", ACCESS_CEPH1_USER1)
           .put("com.openexchange.filestore.s3client.ceph-1-1.secretKey", SECRET_CEPH1_USER1)
           .put("com.openexchange.filestore.s3client.ceph-1-1.encryption", ENCRYPTION_NONE)
           .put("com.openexchange.filestore.s3client.ceph-1-1.pathStyleAccess", TRUE)
           .put("com.openexchange.filestore.s3client.ceph-1-1.chunkSize", CHUNK_SIZE_8MB)
            // Storage #1; User #2
           .put("com.openexchange.filestore.s3client.ceph-1-2.endpoint", ENDPOINT_CEPH1)
           .put("com.openexchange.filestore.s3client.ceph-1-2.accessKey", ACCESS_CEPH1_USER2)
           .put("com.openexchange.filestore.s3client.ceph-1-2.secretKey", SECRET_CEPH1_USER2)
           .put("com.openexchange.filestore.s3client.ceph-1-2.encryption", ENCRYPTION_RSA)
           .put("com.openexchange.filestore.s3client.ceph-1-2.pathStyleAccess", FALSE)
           .put("com.openexchange.filestore.s3client.ceph-1-2.chunkSize", CHUNK_SIZE_5MB)
            // Storage #2; User #1
           .put("com.openexchange.filestore.s3client.ceph-2-1.endpoint", ENDPOINT_CEPH2)
           .put("com.openexchange.filestore.s3client.ceph-2-1.accessKey", ACCESS_CEPH2_USER1)
           .put("com.openexchange.filestore.s3client.ceph-2-1.secretKey", SECRET_CEPH2_USER1)
           .put("com.openexchange.filestore.s3client.ceph-2-1.encryption", ENCRYPTION_SSE3)
           .put("com.openexchange.filestore.s3client.ceph-2-1.pathStyleAccess", TRUE)
           .put("com.openexchange.filestore.s3client.ceph-2-1.chunkSize", CHUNK_SIZE_4MB)
           .build();
    }

    private LeanConfigurationService configServiceFor(Map<String, String> config) {
        LeanConfigurationService configService = Mockito.mock(LeanConfigurationService.class);
        Mockito.when(configService.getProperty(Mockito.any(Property.class))).thenAnswer((i) -> {
            Property p = i.getArgument(0);
            String value = config.get(p.getFQPropertyName());
            if (value == null) {
                return p.getDefaultValue(String.class);
            }
            return value;
        });
        Mockito.when(I(configService.getIntProperty(Mockito.any(Property.class)))).thenAnswer((i) -> {
            Property p = i.getArgument(0);
            String value = config.get(p.getFQPropertyName());
            if (value == null) {
                return p.getDefaultValue(Integer.class);
            }
            return I(Integer.parseInt(value));
        });
        Mockito.when(B(configService.getBooleanProperty(Mockito.any(Property.class)))).thenAnswer((i) -> {
            Property p = i.getArgument(0);
            String value = config.get(p.getFQPropertyName());
            if (value == null) {
                return p.getDefaultValue(Boolean.class);
            }
            return B(Boolean.parseBoolean(value));
        });
        Mockito.when(configService.getProperty(Mockito.any(Property.class), Mockito.anyMap())).thenAnswer((i) -> {
            Property p = i.getArgument(0);
            Map<String, String> optionals = i.getArgument(1);
            String value = config.get(p.getFQPropertyName(optionals));
            if (value == null) {
                return p.getDefaultValue(String.class);
            }
            return value;
        });
        Mockito.when(configService.getProperties(Mockito.any(PropertyFilter.class))).thenAnswer((i) -> {
            PropertyFilter f = i.getArgument(0);
            return config.entrySet().stream()
                .filter(e -> {
                    try {
                        return f.accept(e.getKey(), e.getValue());
                    } catch (OXException e1) {
                        throw new RuntimeException(e1);
                    }
                })
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        });
        return configService;
    }

}
