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

package com.openexchange.mail.authenticity.impl.core.metrics;

import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.mail.authenticity.MailAuthenticityProperty;
import com.openexchange.mail.authenticity.MailAuthenticityResultKey;
import com.openexchange.mail.authenticity.mechanism.MailAuthenticityMechanismResult;
import com.openexchange.mail.dataobjects.MailAuthenticityResult;

/**
 * {@link MailAuthenticityMetricFileLogger}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.0
 */
public class MailAuthenticityMetricFileLogger implements MailAuthenticityMetricLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailAuthenticityMetricFileLogger.class);
    private final LeanConfigurationService leanConfigService;

    /**
     * Initialises a new {@link MailAuthenticityMetricFileLogger}.
     */
    public MailAuthenticityMetricFileLogger(LeanConfigurationService leanConfigService) {
        super();
        this.leanConfigService = leanConfigService;
    }

    @Override
    public void log(String mailId, List<String> rawHeaders, MailAuthenticityResult overallResult) {
        Object arg = new Object() {

            @Override
            public String toString() {
                return compileLog(mailId, rawHeaders, overallResult).toString();
            }
        };
        LOGGER.debug("{}", arg);
    }

    /**
     * Compiles the entire log entry
     * 
     * @param mailId The mail identifier
     * @param rawHeaders a {@link List} with the raw headers of the message
     * @param overallResult The overall {@link MailAuthenticityResult}
     * @return A {@link JSONObject} with the log entry
     */
    JSONObject compileLog(String mailId, List<String> rawHeaders, MailAuthenticityResult overallResult) {
        JSONObject jLog = new JSONObject();
        try {
            logRawHeaders(jLog, rawHeaders);
            logMechanisms(jLog, overallResult);
            jLog.put(MailAuthenticityMetricLogField.mail_id.name(), DigestUtils.sha256Hex(mailId).substring(0, 12));
            jLog.put(MailAuthenticityMetricLogField.domain_mismatch.name(), overallResult.getAttribute(MailAuthenticityResultKey.DOMAIN_MISMATCH, Boolean.class));
            jLog.put(MailAuthenticityMetricLogField.overall_result.name(), overallResult.getStatus().getTechnicalName());
            jLog.put(MailAuthenticityMetricLogField.from_header.name(), overallResult.getAttribute(MailAuthenticityResultKey.FROM_HEADER_DOMAIN));
            return jLog;
        } catch (JSONException e) {
            LOGGER.error("Unable to compile debug log entry for mail with id '{}'", mailId, e);
        }
        return jLog;
    }

    /**
     * Log the raw headers if enabled.
     *
     * @param jLog The JSON log object
     * @param rawHeaders The {@link List} with the raw headers of the message
     * @throws JSONException if a JSON error is occurred
     */
    private void logRawHeaders(JSONObject jLog, List<String> rawHeaders) throws JSONException {
        if (!leanConfigService.getBooleanProperty(MailAuthenticityProperty.LOG_RAW_HEADERS)) {
            return;
        }
        JSONArray jRawHeadersArray = new JSONArray(rawHeaders.size());
        for (String rawHeader : rawHeaders) {
            jRawHeadersArray.put(rawHeader);
        }
        jLog.put(MailAuthenticityMetricLogField.raw_headers.name(), jRawHeadersArray);
    }

    /**
     * Log the mechanisms
     *
     * @param jLog The JSON log object
     * @param overallResult The overall result containing the mechanisms
     * @throws JSONException if a JSON error is occurred
     */
    @SuppressWarnings("unchecked")
    private void logMechanisms(JSONObject jLog, MailAuthenticityResult overallResult) throws JSONException {
        List<MailAuthenticityMechanismResult> results = overallResult.getAttribute(MailAuthenticityResultKey.MAIL_AUTH_MECH_RESULTS, List.class);
        if (null == results) {
            jLog.put(MailAuthenticityMetricLogField.mechanism_results.name(), JSONArray.EMPTY_ARRAY);
            return;
        }

        JSONObject jResultsObject = new JSONObject();
        for (MailAuthenticityMechanismResult result : results) {
            logMechanism(jResultsObject, result);
        }
        jLog.put(MailAuthenticityMetricLogField.mechanism_results.name(), jResultsObject);
    }

    /**
     * Log a single mechanism
     *
     * @param jResultsArray The JSON array holding all the logged mechanism results
     * @param result The {@link MailAuthenticityMechanismResult}
     * @throws JSONException if a JSON error is occurred
     */
    private void logMechanism(JSONObject resultsObject, MailAuthenticityMechanismResult result) throws JSONException {
        JSONObject jResultLog = new JSONObject();
        jResultLog.put(MailAuthenticityMetricLogField.result.name(), result.getResult().getTechnicalName());
        for (Map.Entry<String, String> entry : result.getProperties().entrySet()) {
            jResultLog.put(entry.getKey(), entry.getValue());
        }
        jResultLog.put(MailAuthenticityMetricLogField.domain_mismatch.name(), !result.isDomainMatch());
        resultsObject.put(result.getMechanism().getTechnicalName(), jResultLog);
    }
}
