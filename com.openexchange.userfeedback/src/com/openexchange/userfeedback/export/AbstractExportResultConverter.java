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

package com.openexchange.userfeedback.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;
import com.openexchange.userfeedback.Feedback;
import com.openexchange.userfeedback.fields.UserFeedbackField;

/**
 * {@link AbstractExportResultConverter}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since v7.10.4
 */
public abstract class AbstractExportResultConverter implements ExportResultConverter {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractExportResultConverter.class);

    protected static final char TEXT_QUALIFIER = '"';
    protected static final char CELL_DELIMITER = ';';
    protected static final char CARRIAGE_RETURN = '\r';

    protected final Collection<Feedback> feedbacks;

    protected final char delimiter;

    public AbstractExportResultConverter(Collection<Feedback> lFeedbacks, Map<String, String> configuration) {
        this.feedbacks = lFeedbacks;

        String lDelimiter = configuration.get("delimiter");
        this.delimiter = lDelimiter != null ? lDelimiter.charAt(0) : CELL_DELIMITER;
    }

    /**
     * Defines the fields that should be imported for the underlying feedback mode
     * 
     * @return List of {@link UserFeedbackField}s that should be imported
     */
    protected abstract List<UserFeedbackField> getExportFields();

    /**
     * {@inheritDoc}
     */
    @Override
    public ExportResult get(ExportType type) {
        switch (type) {
            case CSV:
                return createCsvStream();
            case RAW:
            default:
                return createRaw();
        }
    }

    /**
     * Creates an {@link ExportResult} in raw format which means to be a {@link JSONArray}
     * 
     * @return {@link ExportResult} containing feedbacks in raw format
     */
    private ExportResult createRaw() {
        JSONArray result = new JSONArray(feedbacks.size());
        for (Feedback feedback : feedbacks) {
            JSONObject current = (JSONObject) feedback.getContent();
            result.put(current);
        }
        return new UserFeedbackExportResult(result);
    }

    /**
     * Creates an {@link ExportResult} in CSV format as a consumable {@link InputStream}
     * 
     * @return {@link ExportResult} containing feedbacks as an {@link InputStream}
     */
    private ExportResult createCsvStream() {
        ThresholdFileHolder sink = new ThresholdFileHolder();
        OutputStreamWriter writer = new OutputStreamWriter(sink.asOutputStream(), Charsets.UTF_8);
        boolean error = true;
        try {
            final List<UserFeedbackField> jsonFields = getExportFields();
            StringBuilder bob = new StringBuilder(1024);

            writer.write('\ufeff'); // BOM for UTF-*
            // Writer header line
            writer.write(convertToLine(jsonFields, null, bob));

            for (Feedback feedback : feedbacks) {
                // Write entry line
                String convertToLine = convertToLine(jsonFields, (JSONObject) feedback.getContent(), bob);
                writer.write(convertToLine);
            }
            writer.flush();
            error = false;
            return new UserFeedbackExportResult(sink.getClosingStream());
        } catch (IOException | OXException | JSONException | RuntimeException e) {
            LOG.error("Failed to create CSV stream", e);
        } finally {
            if (error) {
                sink.close();
            }
        }
        return new UserFeedbackExportResult(new JSONArray());
    }

    /**
     * Converts the given fields to a line
     * 
     * @param jsonFields The {@link UserFeedbackField}s for the line
     * @param object The feedback or null in case the header line should be written
     * @param sb {@link StringBuilder} the line might be attached.
     * @return String the feedback
     * @throws JSONException
     */
    private String convertToLine(List<UserFeedbackField> jsonFields, JSONObject object, StringBuilder sb) throws JSONException {
        StringBuilder bob;
        if (null == sb) {
            bob = new StringBuilder(1024);
        } else {
            bob = sb;
            bob.setLength(0);
        }

        if (null == object) {
            // Header line
            for (UserFeedbackField token : jsonFields) {
                bob.append(TEXT_QUALIFIER);
                bob.append(sanitize(token.getDisplayName()));
                bob.append(TEXT_QUALIFIER);
                bob.append(this.delimiter);
            }
        } else {
            for (UserFeedbackField token : jsonFields) {
                bob.append(TEXT_QUALIFIER);
                String sanitizedLineBreaks = correctLineBreaks(object, token);
                String replaced = useSingleQuotes(sanitizedLineBreaks);
                String sanitizedValue = sanitize(replaced);
                bob.append(sanitizedValue);
                bob.append(TEXT_QUALIFIER);
                bob.append(this.delimiter);
            }
        }
        bob.setCharAt(bob.length() - 1, CARRIAGE_RETURN);
        return bob.toString();
    }

    /**
     * Adapt for CSV output: replaces the provided String to only have single quotes
     * 
     * @param sanitizedLineBreaks The string to adapt
     * @return String not containing double quotes any more
     */
    private String useSingleQuotes(String sanitizedLineBreaks) {
        return sanitizedLineBreaks.replace("\"", "'");
    }

    private final Pattern p = Pattern.compile("\r?\n");

    /**
     * Adapt for CSV output: correct line breaks
     * 
     * @param object {@link JSONObject} to correct the line breaks for
     * @param token {@link UserFeedbackField} to correct the line breaks for
     * @return String with corrected line breaks for the given {@link UserFeedbackField}
     * @throws JSONException
     */
    private String correctLineBreaks(JSONObject object, UserFeedbackField token) throws JSONException {
        String content = object.getString(token.getName());

        Matcher m = p.matcher(content);
        StringBuffer buffer = new StringBuffer(content.length());
        while (m.find()) {
            m.appendReplacement(buffer, "\r\n");
        }
        m.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Sanitizes the given string
     * 
     * @param value String to sanitize
     * @return Sanitized string
     */
    private String sanitize(String value) {
        int length = value.length();
        if (length <= 0) {
            return value;
        }

        StringBuilder builder = null;

        char firstChar = value.charAt(0);
        if (needsSanitizing(firstChar)) {
            builder = new StringBuilder(length);
            builder.append('\'').append(firstChar);
        }

        for (int i = 1; i < length; i++) {
            char c = value.charAt(i);
            if (null == builder) {
                if (c == '|') {
                    builder = new StringBuilder(length);
                    if (i > 0) {
                        builder.append(value, 0, i);
                    }
                    builder.append("\\").append(c);
                }
            } else {
                if (c == '|') {
                    builder.append("\\").append(c);
                } else {
                    builder.append(c);
                }
            }
        }
        return null == builder ? value : builder.toString();
    }

    /**
     * Returns if the provided character has to be sanitized
     * 
     * @param c char to evaluate if sanitizing is required
     * @return <code>true</code> if sanitizing is required
     */
    private boolean needsSanitizing(char c) {
        switch (c) {
            case '=':
                return true;
            case '+':
                return true;
            case '-':
                return true;
            case '@':
                return true;
            case '|':
                return true;
            default:
                return false;
        }
    }
}
