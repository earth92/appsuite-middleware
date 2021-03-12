/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.ajax.chronos.itip;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.exparity.hamcrest.date.DateMatchers;
import org.jdom2.IllegalDataException;
import com.openexchange.ajax.chronos.AbstractChronosTest;
import com.openexchange.ajax.chronos.manager.EventManager;
import com.openexchange.test.TestClassConfig;
import com.openexchange.test.pool.TestContext;
import com.openexchange.test.pool.TestUser;
import com.openexchange.testing.httpclient.invoker.ApiClient;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.ActionResponse;
import com.openexchange.testing.httpclient.models.AnalyzeResponse;
import com.openexchange.testing.httpclient.models.ConversionDataSource;
import com.openexchange.testing.httpclient.models.MailData;
import com.openexchange.testing.httpclient.models.UserResponse;
import com.openexchange.testing.httpclient.modules.ChronosApi;
import com.openexchange.testing.httpclient.modules.UserApi;
import com.openexchange.tools.client.EnhancedApiClient;

/**
 * {@link AbstractITipTest}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.0
 */
public abstract class AbstractITipTest extends AbstractChronosTest {

    /** Participant status */
    public static enum PartStat {

        ACCEPTED("ACCEPTED"),
        TENTATIVE("TENTATIVE"),
        DECLINED("DECLINED"),
        NEEDS_ACTION("NEEDS-ACTION");

        protected final String status;

        private PartStat(String status) {
            this.status = status;
        }

        /**
         * Gets the status
         *
         * @return The status
         */
        public String getStatus() {
            return status;
        }

    }

    /** All available data sources for iTIP calls */
    public static enum DataSources {

        /** Currently the only valid input for the field 'dataSoure' on iTIP actions */
        MAIL("com.openexchange.mail.ical");

        private final String source;

        DataSources(String source) {
            this.source = source;
        }

        String getDataSource() {
            return source;
        }
    }

    /** All available output formats for iTIP calls */
    public static enum DescriptionFormat {

        /** Currently the only valid input for the field 'descriptionFormat' on iTIP actions */
        HTML("html");

        private final String format;

        DescriptionFormat(String format) {
            this.format = format;
        }

        String getFormat() {
            return format;
        }
    }

    protected ApiClient apiClient;

    protected UserResponse userResponseC1;

    protected UserResponse userResponseC2;

    protected ApiClient apiClientC2;

    protected TestUser testUserC2;

    protected TestContext context2;

    protected String folderIdC2;

    protected EventManager eventManagerC2;

    protected EnhancedApiClient enhancedApiClientC2;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        apiClient = testUser.getApiClient();
        UserApi api = new UserApi(getApiClient());
        userResponseC1 = api.getUser(String.valueOf(testUser.getUserId()));

        context2 = testContextList.get(1);
        testUserC2 = context2.acquireUser();
        apiClientC2 = testUserC2.getApiClient();
        UserApi anotherUserApi = new UserApi(apiClientC2);
        userResponseC2 = anotherUserApi.getUser(String.valueOf(apiClientC2.getUserId()));
        // Validate
        if (null == userResponseC1 || null == userResponseC2) {
            throw new IllegalDataException("Need both users for iTIP tests!");
        }

        folderIdC2 = getDefaultFolder(apiClientC2);

        enhancedApiClientC2 = getEnhancedApiClient2();
        eventManagerC2 = new EventManager(new com.openexchange.ajax.chronos.UserApi(apiClientC2, enhancedApiClientC2, testUserC2), folderIdC2);

        eventManager.setIgnoreConflicts(true);
        eventManagerC2.setIgnoreConflicts(true);
    }

    @Override
    public TestClassConfig getTestConfig() {
        return TestClassConfig.builder().createApiClient().withContexts(2).withUserPerContext(2).useEnhancedApiClients().build();
    }

    /*
     * =========================
     * ========SHORTCUTS========
     * =========================
     */

    /**
     * @See {@link ChronosApi#accept(String,String, String, ConversionDataSource)}
     */
    protected ActionResponse accept(ConversionDataSource body, String comment) throws ApiException {
        return accept(testUser.getApiClient(), body, comment);
    }

    /**
     * @See {@link ChronosApi#accept(String,String, String, ConversionDataSource)}
     */
    protected ActionResponse accept(ApiClient apiClient, ConversionDataSource body, String comment) throws ApiException {
        ActionResponse response = new ChronosApi(apiClient).accept(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body, comment);
        validateActionResponse(response);
        return response;
    }

    /**
     * @See {@link ChronosApi#acceptAndIgnoreConflicts(String, String, String, ConversionDataSource)}
     */
    protected ActionResponse acceptAndIgnoreConflicts(ConversionDataSource body, String comment) throws ApiException {
        ActionResponse response = chronosApi.acceptAndIgnoreConflicts(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body, comment);
        validateActionResponse(response);
        return response;
    }

    /**
     * @See {@link ChronosApi#tentative(String, String, String, ConversionDataSource)}
     */
    protected ActionResponse tentative(ConversionDataSource body, String comment) throws ApiException {
        ActionResponse response = chronosApi.tentative(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body, comment);
        validateActionResponse(response);
        return response;
    }

    /**
     * @See {@link ChronosApi#decline(String, String, String, ConversionDataSource)}
     */
    protected ActionResponse decline(ConversionDataSource body, String comment) throws ApiException {
        ActionResponse response = chronosApi.decline(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body, comment);
        validateActionResponse(response);
        return response;
    }

    /**
     * @See {@link ChronosApi#decline(String, String, String, ConversionDataSource)}
     */
    protected ActionResponse decline(ApiClient apiClient, ConversionDataSource body, String comment) throws ApiException {
        ActionResponse response = new ChronosApi(apiClient).decline(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body, comment);
        validateActionResponse(response);
        return response;
    }

    /**
     * @See {@link ChronosApi#update(String, String, String, ConversionDataSource)}
     */
    protected ActionResponse update(ConversionDataSource body) throws ApiException {
        return update(testUser.getApiClient(), body);
    }

    /**
     * @See {@link ChronosApi#update(String, String, String, ConversionDataSource)}
     */
    protected ActionResponse update(ApiClient apiClient, ConversionDataSource body) throws ApiException {
        ActionResponse response = new ChronosApi(apiClient).update(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body);
        validateActionResponse(response);
        return response;
    }

    /**
     * @See {@link ChronosApi#cancel(String, String, String, ConversionDataSource)}
     * @param expectData A value indicating whether it is expected that event data is returned or not. If set to <code>false</code> only the timestamp will be checked
     */
    protected ActionResponse cancel(ConversionDataSource body, String comment, boolean expectData) throws ApiException {
        return cancel(testUser.getApiClient(), body, comment, expectData);
    }

    /**
     * @See {@link ChronosApi#cancel(String, String, String, ConversionDataSource)}
     * @param expectData A value indicating whether it is expected that event data is returned or not. If set to <code>false</code> only the timestamp will be checked
     */
    protected ActionResponse cancel(ApiClient apiClient, ConversionDataSource body, String comment, boolean expectData) throws ApiException {
        ActionResponse response = new ChronosApi(apiClient).cancel(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body, comment);
        assertThat(response.getTimestamp(), is(not(nullValue())));
        assertThat("Only timestamp should be returned", new Date(response.getTimestamp().longValue()), DateMatchers.within(3, ChronoUnit.SECONDS, new Date()));
        if (expectData) {
            validateActionResponse(response);
        } else {
            assertThat("Only timestamp should be returned", response.getData(), is(empty()));
        }
        return response;
    }

    private void validateActionResponse(ActionResponse response) {
        assertThat("Excpected analyze-data", response.getData(), is(not(empty())));
    }

    /**
     * @See {@link ChronosApi#analyze(String, String, String, ConversionDataSource, String)}
     */
    protected AnalyzeResponse analyze(ConversionDataSource body) throws ApiException {
        return chronosApi.analyze(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body, null);
    }

    protected static AnalyzeResponse analyze(ApiClient apiClient, MailData mailData) throws Exception {
        ConversionDataSource body = ITipUtil.constructBody(mailData);
        return new ChronosApi(apiClient).analyze(DataSources.MAIL.getDataSource(), DescriptionFormat.HTML.getFormat(), body, null);
    }

    protected Long now() {
        return Long.valueOf(System.currentTimeMillis());
    }

}
