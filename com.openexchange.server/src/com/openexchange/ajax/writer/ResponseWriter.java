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

package com.openexchange.ajax.writer;

import static com.openexchange.ajax.Client.USM_EAS;
import static com.openexchange.ajax.Client.USM_JSON;
import static com.openexchange.ajax.fields.ResponseFields.*;
import static com.openexchange.java.util.Tools.getUnsignedInteger;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONValue;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.fields.ResponseFields;
import com.openexchange.ajax.fields.ResponseFields.ParsingFields;
import com.openexchange.ajax.fields.ResponseFields.TruncatedFields;
import com.openexchange.ajax.response.IncludeStackTraceService;
import com.openexchange.ajax.writer.filter.StackTraceBlacklist;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.Reloadables;
import com.openexchange.exception.Categories;
import com.openexchange.exception.Category;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXException.Parsing;
import com.openexchange.exception.OXException.ProblematicAttribute;
import com.openexchange.exception.OXException.Truncated;
import com.openexchange.exception.OXExceptionConstants;
import com.openexchange.i18n.LocaleTools;
import com.openexchange.i18n.Localizable;
import com.openexchange.java.Strings;
import com.openexchange.java.util.UUIDs;
import com.openexchange.json.OXJSONWriter;
import com.openexchange.json.io.Jsonable;
import com.openexchange.log.LogProperties;
import com.openexchange.server.services.ServerServiceRegistry;

/**
 * JSON writer for the response container object.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class ResponseWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseWriter.class);

    /**
     * A set of reserved identifiers.
     */
    private static final Set<String> RESERVED_IDENTIFIERS = ResponseFields.RESERVED_IDENTIFIERS;

    private static volatile Locale defaultLocale;

    /**
     * The default locale.
     */
    private static Locale defaultLocale() {
        Locale tmp = defaultLocale;
        if (null == tmp) {
            synchronized (ResponseWriter.class) {
                tmp = defaultLocale;
                if (null == tmp) {
                    final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                    if (null == service) {
                        tmp = Locale.US;
                    } else {
                        final String property = service.getProperty("com.openexchange.i18n.defaultLocale");
                        tmp = null == property ? Locale.US : LocaleTools.getLocale(property);
                        if (null == tmp) {
                            tmp = Locale.US;
                        }
                    }
                    defaultLocale = tmp;
                }
            }
        }
        return tmp;
    }

    private ResponseWriter() {
        super();
    }

    private static final AtomicReference<IncludeStackTraceService> INCL_STACKTRACE_REF = new AtomicReference<IncludeStackTraceService>();

    /**
     * Sets the specified instance.
     *
     * @param includeStackTraceService The instance to set
     */
    public static void setIncludeStackTraceService(final IncludeStackTraceService includeStackTraceService) {
        INCL_STACKTRACE_REF.set(includeStackTraceService);
    }

    /** Server side switch to enable stacktrace invocation on responses */
    private static final String STACKTRACE_INCLUDE_ERROR = "com.openexchange.ajax.response.includeStackTraceOnError";
    static volatile Boolean     includeStackTraceOnError;

    private static boolean includeStackTraceOnError() {
        // First consult IncludeStackTraceService
        {
            final IncludeStackTraceService traceService = INCL_STACKTRACE_REF.get();
            if (null != traceService && traceService.isEnabled()) {
                try {
                    final int contextId = getUnsignedInteger(LogProperties.get(LogProperties.Name.SESSION_CONTEXT_ID));
                    if (contextId > 0) {
                        final int userId = getUnsignedInteger(LogProperties.get(LogProperties.Name.SESSION_USER_ID));
                        if (userId > 0) {
                            return traceService.includeStackTraceOnError(userId, contextId);
                        }
                    }
                } catch (Exception e) {
                    final Logger logger = org.slf4j.LoggerFactory.getLogger(ResponseWriter.class);
                    logger.error("Could not check includeStackTraceOnError()", e);
                }
            }
        }

        Boolean b = includeStackTraceOnError;
        if (null == b) {
            synchronized (ResponseWriter.class) {
                b = includeStackTraceOnError;
                if (null == b) {
                    final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                    if (null == service) {
                        return false;
                    }
                    b = Boolean.valueOf(service.getBoolProperty(STACKTRACE_INCLUDE_ERROR, false));
                    includeStackTraceOnError = b;
                }
            }
        }
        return b.booleanValue();
    }

    /** Whether the JSON response object in case of an error should include the exception arguments or not */
    private static final String INCLUDE_ARGUMENTS = "com.openexchange.ajax.response.includeArguments";
    static volatile Boolean     includeArguments;

    private static boolean includeArguments() {
        Boolean b = includeArguments;
        if (null == b) {
            synchronized (ResponseWriter.class) {
                b = includeArguments;
                if (null == b) {
                    final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                    if (null == service) {
                        return false;
                    }
                    b = Boolean.valueOf(service.getBoolProperty(INCLUDE_ARGUMENTS, false));
                    includeArguments = b;
                }
            }
        }
        return b.booleanValue();
    }

    /**
     * Gets the JSON object resulting from specified response using default locale.
     *
     * @param response The response
     * @return The JSON object
     * @throws JSONException If writing JSON fails
     */
    public static JSONObject getJSON(final Response response) throws JSONException {
        final JSONObject json = new JSONObject(8);
        write(response, json, defaultLocale());
        return json;
    }

    /**
     * Gets the JSON object resulting from specified response using given locale.
     *
     * @param response The response
     * @param locale The locale
     * @return The JSON object
     * @throws JSONException If writing JSON fails
     */
    public static JSONObject getJSON(final Response response, final Locale locale) throws JSONException {
        final JSONObject json = new JSONObject(8);
        write(response, json, locale);
        return json;
    }

    /**
     * Writes specified response to given JSON object using default locale.
     *
     * @param response The response to write
     * @param json The JOSN object to write to
     * @throws JSONException If writing JSON fails
     */
    public static void write(final Response response, final JSONObject json) throws JSONException {
        write(response, json, defaultLocale());
    }

    /**
     * Writes specified response to given JSON object using passed locale.
     *
     * @param response The response to write
     * @param json The JOSN object to write to
     * @param locale The locale
     * @throws JSONException If writing JSON fails
     */
    public static void write(final Response response, final JSONObject json, final Locale locale) throws JSONException {
        /*-
         * TODO: Also check for
         *       "JSONObject.NULL.equals(response.getData())"
         *       when performing null comparison?
         */
        {
            Object data = response.getData();
            if (null != data) {
                json.put(DATA, data);
            }
        }

        {
            Date timestamp = response.getTimestamp();
            if (null != timestamp) {
                json.put(TIMESTAMP, timestamp.getTime());
            }
        }

        {
            UUID continuationUUID = response.getContinuationUUID();
            if (null != continuationUUID) {
                json.put(CONTINUATION, UUIDs.getUnformattedString(continuationUUID));
            }
        }

        List<OXException> warnings = response.getWarnings();
        OXException exception = response.getException();
        if (null == exception) {
            /*
             * Any warning available? Set first warning as "exception" for compatibility reasons
             */
            if (null != warnings && !warnings.isEmpty()) {
                addException(json, warnings.get(0), locale, response.includeStackTraceOnError());
            }
        } else {
            addException(json, exception, locale, response.includeStackTraceOnError());
        }
        /*
         * Add warnings
         */
        addWarnings(json, warnings, locale, response.includeStackTraceOnError());
        /*
         * Add properties
         */
        addProperties(json, response.getProperties());
    }

    /**
     * Writes given properties to specified JSON object.
     *
     * @param json The JSON object
     * @param properties The properties
     * @throws JSONException If writing JSON fails
     */
    public static void addProperties(final JSONObject json, final Map<String, Object> properties) throws JSONException {
        if (null == properties || properties.isEmpty()) {
            return;
        }
        for (final Entry<String, Object> entry : properties.entrySet()) {
            final String name = entry.getKey();
            if (null != name && !RESERVED_IDENTIFIERS.contains(name)) {
                final Object value = entry.getValue();
                if (null != value) {
                    json.put(name, value);
                }
            } else {
                LoggerFactory.getLogger(ResponseWriter.class).warn("Response property discarded. Illegal property name: {}", (name == null ? "null" : name));
            }
        }
    }

    /**
     * Writes specified warning to given JSON object using passed locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param warning The warning
     * @param locale The locale
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addWarning(final JSONObject json, final OXException warning, final Locale locale) throws JSONException {
        if (null == warning) {
            return;
        }
        final JSONObject jsonWarning = new JSONObject(8);
        addException(jsonWarning, warning.setCategory(Category.CATEGORY_WARNING), locale);
        json.put(WARNINGS, jsonWarning);
    }

    /**
     * Writes specified warnings to given JSON object using default locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param warnings The warnings
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addWarnings(final JSONObject json, final List<OXException> warnings) throws JSONException {
        addWarnings(json, warnings, defaultLocale(), false);
    }

    /**
     * Writes specified warnings to given JSON object using passed locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param warnings The warnings
     * @param locale The locale
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addWarnings(final JSONObject json, final List<OXException> warnings, final Locale locale) throws JSONException {
        addWarnings(json, warnings, locale, false);
    }

    /**
     * Writes specified warnings to given JSON object using passed locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param warnings The warnings
     * @param locale The locale
     * @param includeStackTraceOnError <code>true</code> to append stack trace elements to JSON object; otherwise <code>false</code>
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addWarnings(final JSONObject json, final List<OXException> warnings, final Locale locale, final boolean includeStackTraceOnError) throws JSONException {
        if (null == warnings || warnings.isEmpty()) {
            return;
        }
        if (1 == warnings.size()) {
            final JSONObject jsonWarning = new JSONObject(8);
            final OXException warning = warnings.get(0).setCategory(Category.CATEGORY_WARNING);
            addException(jsonWarning, warning, locale);
            json.put(WARNINGS, jsonWarning);
            // Check if error has already been set
            if (!json.hasAndNotNull(ERROR)) {
                addException(json, warning, locale, includeStackTraceOnError);
            }
        } else {
            final JSONArray jsonArray = new JSONArray(warnings.size());
            for (final OXException warning : warnings) {
                final JSONObject jsonWarning = new JSONObject();
                addException(jsonWarning, warning.setCategory(Category.CATEGORY_WARNING), locale);
                jsonArray.put(jsonWarning);
            }
            json.put(WARNINGS, jsonArray);
            if (!warnings.isEmpty() && !json.hasAndNotNull(ERROR)) {
                addException(json, warnings.get(0).setCategory(Category.CATEGORY_WARNING), locale, includeStackTraceOnError);
            }
        }
    }

    /**
     * Writes specified exception to given JSON object using default locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param exception The exception to write
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addException(final JSONObject json, final OXException exception) throws JSONException {
        addException(json, exception, defaultLocale());
    }

    /**
     * Writes specified exception to given JSON object using default locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param exception The exception to write
     * @param includeStackTraceOnError <code>true</code> to append stack trace elements to JSON object; otherwise <code>false</code>
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addException(final JSONObject json, final OXException exception, final boolean includeStackTraceOnError) throws JSONException {
        addException(json, exception, defaultLocale(), includeStackTraceOnError);
    }

    /**
     * Writes specified exception to given JSON object using passed locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param exception The exception to write
     * @param locale The locale
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addException(final JSONObject json, final OXException exception, final Locale locale) throws JSONException {
        addException(json, exception, locale, false, ERROR);
    }

    /**
     * Writes specified exception to given JSON object using passed locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param exception The exception to write
     * @param locale The locale
     * @param includeStackTraceOnError <code>true</code> to append stack trace elements to JSON object; otherwise <code>false</code>
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addException(final JSONObject json, final OXException exception, final Locale locale, final boolean includeStackTraceOnError) throws JSONException {
        addException(json, exception, locale, includeStackTraceOnError, ERROR);
    }

    /**
     * Writes specified exception to given JSON object using passed locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param exception The exception to write
     * @param locale The locale
     * @param includeStackTraceOnError <code>true</code> to append stack trace elements to JSON object; otherwise <code>false</code>
     * @param errorKey The key value for the error value inside the JSON object; e.g. <code>"error"</code>
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addException(final JSONObject json, final OXException exception, final Locale locale, final boolean includeStackTraceOnError, String errorKey) throws JSONException {
        addException(json, exception, locale, new WriteExceptionProps().errorKey(errorKey).includeStackTraceOnError(includeStackTraceOnError));
    }

    /**
     * Writes specified exception to given JSON object using passed locale (if no other locale specified through {@link OXExceptionConstants#PROPERTY_LOCALE}.
     *
     * @param json The JSON object
     * @param exception The exception to write
     * @param locale The locale
     * @param properties The properties to obey when writing specified exception to given JSON object
     * @throws JSONException If writing JSON fails
     * @see OXExceptionConstants#PROPERTY_LOCALE
     */
    public static void addException(JSONObject json, OXException exception, Locale locale, WriteExceptionProps properties) throws JSONException {
        Locale l;
        {
            final String property = exception.getProperty(OXExceptionConstants.PROPERTY_LOCALE);
            if (null == property) {
                l = LocaleTools.getSaneLocale(locale);
            } else {
                final Locale parsedLocale = LocaleTools.getLocale(property);
                l = null == parsedLocale ? LocaleTools.getSaneLocale(locale) : parsedLocale;
            }
        }
        json.put(properties.errorKey, exception.getDisplayMessage(l));
        /*
         * Put argument JSON array for compatibility reasons
         */
        {
            Object[] args = exception.getDisplayArgs();
            /*-
             * Only check display arguments to keep the ones for log message intact
             *
            if ((null == args) || (0 == args.length)) {
                args = exception.getLogArgs();
            }
            */
            // For compatibility
            if ((null == args) || (0 == args.length)) {
                json.put(ERROR_PARAMS, new JSONArray(0));
            } else {
                JSONArray jArgs = new JSONArray(args.length);
                for (int i = 0; i < args.length; i++) {
                    Object obj = args[i];
                    if (obj != null) {
                        jArgs.put(obj instanceof Localizable ? obj.toString() : obj);
                    }
                }
                json.put(ERROR_PARAMS, jArgs);
            }
        }
        /*
         * Categories
         */
        {
            List<Category> categories = getCategoriesFrom(exception);
            int size = categories.size();
            if (1 == size) {
                Category category = categories.get(0);
                json.put(ERROR_CATEGORIES, category.toString());
                // For compatibility
                int number = Categories.getFormerCategoryNumber(category);
                if (number > 0) {
                    json.put(ERROR_CATEGORY, number);
                }
            } else {
                if (size <= 0) {
                    // Empty JSON array
                    json.put(ERROR_CATEGORIES, new JSONArray(0));
                } else {
                    JSONArray jArray = new JSONArray(size);
                    for (Category category : categories) {
                        jArray.put(category.toString());
                    }
                    json.put(ERROR_CATEGORIES, jArray);
                    // For compatibility
                    int number = Categories.getFormerCategoryNumber(categories.get(0));
                    if (number > 0) {
                        json.put(ERROR_CATEGORY, number);
                    }
                }
            }
        }
        /*
         * Error code, identifier, and description
         */
        json.put(ERROR_CODE, exception.getErrorCode());
        json.put(ERROR_ID, exception.getExceptionId());
        json.put(ERROR_DESC, exception.getSoleMessage());

        /*
         * Problematics
         */
        if (properties.checkProblematic) {
            toJSON(json, exception.getProblematics());
        }
        /*
         * Truncated
         */
        if (properties.checkTruncated && Category.CATEGORY_TRUNCATED.equals(exception.getCategory())) {
            addTruncated(json, exception.getProblematics());
        }
        /*
         * Stack trace
         */
        if (includeStacktrace(properties.includeStackTraceOnError, exception)) {
            // Write exception
            StackTraceElement[] traceElements = exception.getStackTrace();
            final JSONArray jsonStack = new JSONArray(traceElements.length << 1);
            jsonStack.put(exception.getSoleMessage());
            Throwable cause = exception;
            final StringBuilder tmp = new StringBuilder(64);
            while (null != traceElements && traceElements.length > 0) {
                for (final StackTraceElement stackTraceElement : traceElements) {
                    tmp.setLength(0);
                    writeElementTo(stackTraceElement, tmp);
                    jsonStack.put(tmp.toString());
                }
                cause = cause.getCause();
                if (null == cause) {
                    traceElements = null;
                } else {
                    tmp.setLength(0);
                    jsonStack.put(tmp.append("Caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).toString());
                    traceElements = cause.getStackTrace();
                }
            }
            json.put(ERROR_STACK, jsonStack);
        }
    }

    /**
     * Checks if a stack-trace should be included in the response.
     *
     * @param includeStackTraceOnError <code>true</code> if the client wants to add the stack-trace, <code>false</code> otherwise
     * @param exception The {@link OXException} to check
     * @return <code>true</code> If and only if it is supposed to be checked, either client or server configured stack-trace invocation
     *         and if the exception is not blacklisted for stack-trace writing
     *         <code>false</code> if one condition is not met
     */
    private static boolean includeStacktrace(boolean includeStackTraceOnError, OXException exception) {
        return (null != exception) && (includeStackTraceOnError || includeStackTraceOnError()) && isIncludeAllowed(exception);
    }

    private static List<Category> getCategoriesFrom(OXException exception) {
        List<Category> categories = exception.getCategories();
        if (null == categories) {
            return Collections.emptyList();
        }

        List<Category> nonnulls = null;
        for (ListIterator<Category> listIterator = categories.listIterator(); listIterator.hasNext();) {
            int index = listIterator.nextIndex();
            Category category = listIterator.next();
            if (null == category) {
                if (null == nonnulls) {
                    nonnulls = new ArrayList<>(categories.size());
                    if (index > 0) {
                        nonnulls.addAll(categories.subList(0, index));
                    }
                }
            } else {
                if (null != nonnulls) {
                    nonnulls.add(category);
                }
            }
        }
        return null == nonnulls ? categories : nonnulls;
    }

    private static void writeElementTo(final StackTraceElement element, final StringBuilder sb) {
        sb.append(element.getClassName()).append('.').append(element.getMethodName());
        if (element.isNativeMethod()) {
            sb.append("(Native Method)");
        } else {
            final String fileName = element.getFileName();
            if (null == fileName) {
                sb.append("(Unknown Source)");
            } else {
                sb.append('(').append(fileName);
                final int lineNumber = element.getLineNumber();
                if (lineNumber >= 0) {
                    sb.append(':').append(lineNumber);
                }
                sb.append(')');
            }
        }
    }

    private static void toJSON(final JSONObject json, final ProblematicAttribute[] problematics) throws JSONException {
        final JSONArray array = new JSONArray(problematics.length);
        for (final ProblematicAttribute problematic : problematics) {
            array.put(toJSON(problematic));
        }
        if (array.length() > 0) {
            json.put(PROBLEMATIC, array);
        }
    }

    public static JSONObject toJSON(final ProblematicAttribute problematic) throws JSONException {
        if (problematic instanceof Truncated) {
            return toJSON((Truncated) problematic);
        } else if (problematic instanceof Parsing) {
            return toJSON((Parsing) problematic);
        }
        return new JSONObject();
    }

    public static JSONObject toJSON(final Truncated truncated) throws JSONException {
        final JSONObject json = new JSONObject(3);
        json.put(TruncatedFields.ID, truncated.getId());
        json.put(TruncatedFields.LENGTH, truncated.getLength());
        json.put(TruncatedFields.MAX_SIZE, truncated.getMaxSize());
        return json;
    }

    public static JSONObject toJSON(final Parsing parsing) throws JSONException {
        final JSONObject json = new JSONObject(1);
        json.put(ParsingFields.NAME, parsing.getAttribute());
        return json;
    }

    /**
     * This method adds the old truncated ids.
     */
    private static void addTruncated(final JSONObject json, final ProblematicAttribute[] problematics) throws JSONException {
        final JSONArray array = new JSONArray(problematics.length);
        for (final ProblematicAttribute problematic : problematics) {
            if (Truncated.class.isAssignableFrom(problematic.getClass())) {
                array.put(((Truncated) problematic).getId());
            }
        }
        json.put(TRUNCATED, array);
    }

    /**
     * Serializes a Response object to given instance of <code>{@link JSONWriter}</code> .
     *
     * @param response - the <code>{@link Response}</code> object to serialize.
     * @param writer - the <code>{@link JSONWriter}</code> to write to
     * @param locale - the {@link Locale} for {@link OXException#getDisplayMessage(Locale)}
     * @throws JSONException - if writing fails
     */
    public static void write(final Response response, final JSONWriter writer, final Locale locale) throws JSONException {
        writer.object();
        final JSONObject json = new JSONObject(8);
        write(response, json, locale);
        final Set<Map.Entry<String, Object>> entrySet = json.entrySet();
        final int len = entrySet.size();
        final Iterator<Map.Entry<String, Object>> iter = entrySet.iterator();
        for (int i = 0; i < len; i++) {
            final Map.Entry<String, Object> e = iter.next();
            writer.key(e.getKey()).value(e.getValue());
        }
        writer.endObject();
    }

    /**
     * Serializes a Response object to the writer.
     *
     * @param response Response object to serialize.
     * @param writer the serialized object will be written to this writer.
     * @throws JSONException if writing fails.
     * @throws IOException If an I/O error occurs during writing
     */
    public static void write(final Response response, final Writer writer) throws JSONException, IOException {
        final Locale locale = response.getLocale();
        write(response, writer, locale == null ? defaultLocale() : locale);
    }

    /**
     * Serializes a Response object to the writer.
     *
     * @param response Response object to serialize.
     * @param writer the serialized object will be written to this writer.
     * @param locale The locale
     * @throws JSONException if writing fails.
     * @throws IOException If an I/O error occurs during writing
     */
    public static void write(final Response response, final Writer writer, final Locale locale) throws JSONException, IOException {
        write(response, writer, locale, false);
    }

    /**
     * Serializes a Response object to the writer.
     *
     * @param response Response object to serialize.
     * @param writer the serialized object will be written to this writer.
     * @param locale The locale
     * @param asciiOnly <code>true</code> to only write ASCII characters; otherwise <code>false</code>
     * @throws JSONException if writing fails.
     * @throws IOException If an I/O error occurs during writing
     */
    public static void write(final Response response, final Writer writer, final Locale locale, final boolean asciiOnly) throws JSONException, IOException {
        final JSONObject json = new JSONObject();
        ResponseWriter.write(response, json, locale);
        try {
            json.write(writer, asciiOnly);
        } catch (JSONException e) {
            if (e.getCause() instanceof IOException) {
                /*
                 * Throw proper I/O error since a serious socket error could been occurred which prevents further communication. Just
                 * throwing a JSON error possibly hides this fact by trying to write to/read from a broken socket connection.
                 */
                throw (IOException) e.getCause();
            }
            /*
             * Just re-throw JSON error probably caused by a JSON syntax error.
             */
            throw e;
        }
    }

    /**
     * Writes specified warnings to {@link JSONWriter} instance using default locale.
     *
     * @param warnings The warnings
     * @param writer The JSON writer
     * @throws JSONException If writing JSON fails
     */
    public static void writeWarnings(final List<OXException> warnings, final JSONWriter writer) throws JSONException {
        writeWarnings(warnings, writer, defaultLocale());
    }

    /**
     * Writes specified warnings to {@link JSONWriter} instance.
     *
     * @param warnings The warnings
     * @param writer The JSON writer
     * @param locale The locale
     * @throws JSONException If writing JSON fails
     */
    public static void writeWarnings(final List<OXException> warnings, final JSONWriter writer, final Locale locale) throws JSONException {
        if (null == warnings || warnings.isEmpty()) {
            return;
        }
        writer.key(WARNINGS);
        if (1 == warnings.size()) {
            final OXException warning = warnings.get(0);
            writer.object();
            try {
                writeException(warning/* .setCategory(Category.CATEGORY_WARNING) */, writer, locale);
            } finally {
                writer.endObject();
            }
            if (writer instanceof OXJSONWriter) {
                final JSONValue jv = ((OXJSONWriter) writer).getObject();
                if (jv.isObject()) {
                    final JSONObject json = jv.toObject();
                    if (!json.hasAndNotNull(ERROR)) {
                        addException(json, warning, locale);
                    }
                }
            }
        } else {
            writer.array();
            try {
                for (final OXException warning : warnings) {
                    writer.object();
                    try {
                        writeException(warning/* .setCategory(Category.CATEGORY_WARNING) */, writer, locale);
                    } finally {
                        writer.endObject();
                    }
                }
            } finally {
                writer.endArray();
            }
            if (!warnings.isEmpty() && (writer instanceof OXJSONWriter)) {
                final JSONValue jv = ((OXJSONWriter) writer).getObject();
                if (jv.isObject()) {
                    final JSONObject json = jv.toObject();
                    if (!json.hasAndNotNull(ERROR)) {
                        addException(json, warnings.get(0), locale);
                    }
                }
            }
        }
    }

    /**
     * Writes given instance of <code>OXException</code> into given instance of <code>JSONWriter</code> assuming that writer's mode is
     * already set to writing a JSON object
     *
     * @param exc - the exception to write
     * @param writer - the writer to write to
     * @throws JSONException - if writing fails
     */
    public static void writeException(final OXException exc, final JSONWriter writer) throws JSONException {
        writeException(exc, writer, defaultLocale());
    }

    /**
     * Writes given instance of <code>OXException</code> into given instance of <code>JSONWriter</code> assuming that writer's mode is
     * already set to writing a JSON object
     *
     * @param exc - the exception to write
     * @param writer - the writer to write to
     * @param locale The locale to use for internationalization of the error message
     * @throws JSONException - if writing fails
     */
    public static void writeException(final OXException exc, final JSONWriter writer, final Locale locale) throws JSONException {
        writeException(exc, writer, locale, false);
    }

    /**
     * Writes given instance of <code>OXException</code> into given instance of <code>JSONWriter</code> assuming that writer's mode is
     * already set to writing a JSON object
     *
     * @param exc - the exception to write
     * @param writer - the writer to write to
     * @param locale The locale to use for internationalization of the error message
     * @param includeStackTraceOnError <code>true</code> to include the exception's stack trace; otherwise <code>false</code>
     * @throws JSONException - if writing fails
     */
    public static void writeException(final OXException exc, final JSONWriter writer, final Locale locale, final boolean includeStackTraceOnError) throws JSONException {
        writer.key(ERROR).value(exc.getDisplayMessage(locale));
        /*
         * Put argument JSON array for compatibility reasons
         */
        {
            Object[] args = exc.getDisplayArgs();
            /*-
             * Only check display arguments to keep the ones for log message intact
             *
            if ((null == args) || (0 == args.length)) {
                args = exc.getLogArgs();
            }
            */
            // For compatibility
            if ((null == args) || (0 == args.length)) {
                writer.key(ResponseFields.ERROR_PARAMS).value(new JSONArray(0));
            } else {
                final JSONArray jArgs = new JSONArray(args.length);
                for (int i = 0; i < args.length; i++) {
                    Object obj = args[i];
                    jArgs.put(obj instanceof Localizable ? obj.toString() : obj);
                }
                writer.key(ResponseFields.ERROR_PARAMS).value(jArgs);
            }
        }
        {
            List<Category> categories = getCategoriesFrom(exc);
            int size = categories.size();
            if (1 == size) {
                Category category = categories.get(0);
                writer.key(ERROR_CATEGORIES).value(category.toString());
                // For compatibility
                int number = Categories.getFormerCategoryNumber(category);
                if (number > 0) {
                    writer.key(ERROR_CATEGORY).value(number);
                }
            } else {
                writer.key(ERROR_CATEGORIES);
                if (size <= 0) {
                    // Empty JSON array
                    writer.array().endArray();
                } else {
                    writer.array();
                    try {
                        for (Category category : categories) {
                            writer.value(category.toString());
                        }
                    } finally {
                        writer.endArray();
                    }
                    // For compatibility
                    int number = Categories.getFormerCategoryNumber(categories.get(0));
                    if (number > 0) {
                        writer.key(ERROR_CATEGORY).value(number);
                    }
                }
            }
        }
        writer.key(ERROR_CODE).value(exc.getErrorCode());
        writer.key(ERROR_ID).value(exc.getExceptionId());
        writer.key(ERROR_DESC).value(exc.getSoleMessage());
        writeProblematic(exc, writer);
        writeTruncated(exc, writer);
        if (includeArguments()) {
            writeArguments(exc, writer);
        }
        // Write stack trace
        if (includeStacktrace(includeStackTraceOnError, exc)) {
            writer.key(ERROR_STACK);
            writer.array();
            try {
                writer.value(exc.getSoleMessage());
                final StackTraceElement[] traceElements = exc.getStackTrace();
                if (null != traceElements && traceElements.length > 0) {
                    final StringBuilder tmp = new StringBuilder(64);
                    for (final StackTraceElement stackTraceElement : traceElements) {
                        tmp.setLength(0);
                        writeElementTo(stackTraceElement, tmp);
                        writer.value(tmp.toString());
                    }
                }
            } finally {
                writer.endArray();
            }
        }
    }

    private static void writeProblematic(final OXException exc, final JSONWriter writer) throws JSONException {
        final ProblematicAttribute[] problematics = exc.getProblematics();
        if (problematics.length > 0) {
            writer.key(PROBLEMATIC);
            writer.array();
            for (final ProblematicAttribute problematic : problematics) {
                writer.value(toJSON(problematic));
            }
            writer.endArray();
        }
    }

    private static void writeTruncated(final OXException exc, final JSONWriter writer) throws JSONException {
        final ProblematicAttribute[] problematics = exc.getProblematics();
        if (problematics.length > 0) {
            final JSONArray array = new JSONArray();
            for (final ProblematicAttribute problematic : problematics) {
                if (problematic instanceof Truncated) {
                    array.put(((Truncated) problematic).getId());
                }
            }
            writer.key(TRUNCATED).value(array);
        }
    }

    private static void writeArguments(final OXException exc, final JSONWriter writer) throws JSONException {
        Map<String, Object> arguments = exc.getArguments();
        if (!arguments.isEmpty()) {
            try {
                JSONObject jArguments = null;
                for (Entry<String, Object> argument : arguments.entrySet()) {
                    Object value = argument.getValue();
                    if (value instanceof Jsonable) {
                        Object jValue = ((Jsonable) value).toJson();
                        if (null != jValue) {
                            if (null == jArguments) {
                                jArguments = new JSONObject(4);
                            }
                            jArguments.put(argument.getKey(), jValue);
                        }
                    }
                }
                if (null != jArguments) {
                    writer.key(ARGUMENTS).value(jArguments);
                }
            } catch (IOException e) {
                Throwable cause = e.getCause();
                if (cause instanceof JSONException) {
                    throw (JSONException) cause;
                }
                throw new JSONException("Error while composing JSON", e);
            }
        }
    }

    /** The property defining the blacklisted exceptions */
    private final static String         STACKTRACE_BLACKLIST = "com.openexchange.ajax.response.excludeStackTraceFor";
    static volatile StackTraceBlacklist blacklist;

    /**
     * Check if the given {@link OXException} is allowed to be included into a response
     *
     * @param exception The {@link OXException} to check
     * @return <code>true</code> if the exception and its stacktrace can be included into the response
     *         <code>false</code> otherwise
     */
    private static boolean isIncludeAllowed(OXException exception) {
        StackTraceBlacklist b = blacklist;
        if (null == b) {
            synchronized (ResponseWriter.class) {
                b = blacklist;
                if (null == b) {
                    final ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                    if (null == service) {
                        // Trigger default
                        LOGGER.warn("Couldn't get ConfigurationService. Falling back to default for {}.", STACKTRACE_BLACKLIST);
                        b = new StackTraceBlacklist(null);
                    } else {
                        b = new StackTraceBlacklist(service.getProperty(STACKTRACE_BLACKLIST, ""));
                    }
                    blacklist = b;
                }
            }
        }
        return b.isIncludeAllowed(exception);
    }

    /**
     * Gets a value indicating whether the supplied client identifier indicates an USM session or not.
     *
     * @param clientId the client ID to check
     * @return <code>true</code> if the client denotes an USM client, <code>false</code>, otherwise
     */
    public static boolean isUsmEas(String clientId) {
        if (Strings.isEmpty(clientId)) {
            return false;
        }
        String uc = Strings.toUpperCase(clientId);
        return uc.startsWith(USM_EAS.getClientId()) || uc.startsWith(USM_JSON.getClientId());
    }

    // -----------------------------------------------------------------------------------------------------------------------------

    /**
     * Gets the <code>Reloadable</code> instance for the {@link ResponseWriter}
     *
     * @return The <code>Reloadable</code> to handle reloads
     */
    public static Reloadable getReloadable() {
        return new ResponseWriterReloadable();
    }

    /**
     * {@link ResponseWriterReloadable}
     *
     * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
     * @since v7.10.0
     */
    private static final class ResponseWriterReloadable implements Reloadable {

        /**
         * Initializes a new {@link ResponseWriter.ResponseWriterReloadable}.
         */
        ResponseWriterReloadable() {
            super();
        }

        @Override
        public void reloadConfiguration(ConfigurationService configService) {
            blacklist = new StackTraceBlacklist(configService.getProperty(STACKTRACE_BLACKLIST, ""));
            includeStackTraceOnError = Boolean.valueOf(configService.getBoolProperty(STACKTRACE_INCLUDE_ERROR, false));
            includeArguments = Boolean.valueOf(configService.getBoolProperty(INCLUDE_ARGUMENTS, false));
        }

        @Override
        public Interests getInterests() {
            return Reloadables.interestsForProperties(STACKTRACE_BLACKLIST, STACKTRACE_INCLUDE_ERROR, INCLUDE_ARGUMENTS);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance of {@link WriteExceptionProps}.
     *
     * @return A new instance of {@link WriteExceptionProps}
     */
    public static WriteExceptionProps newWriteExceptionProps() {
        return new WriteExceptionProps();
    }

    /**
     * {@link WriteExceptionProps} - Specifies properties to obey when serializing an {@link OXException} instance into a JSON object.
     *
     * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
     * @since v7.8.2
     */
    public static final class WriteExceptionProps {

        String  errorKey;
        boolean includeStackTraceOnError;
        boolean checkProblematic;
        boolean checkTruncated;

        /**
         * Initializes a new {@link WriteExceptionProps} instance.
         */
        public WriteExceptionProps() {
            super();
            errorKey = ERROR;
            includeStackTraceOnError = false;
            checkProblematic = true;
            checkTruncated = true;
        }

        /**
         * Sets the error key to use.
         *
         * @param errorKey The error key to use
         * @return This instance
         */
        public WriteExceptionProps errorKey(String errorKey) {
            this.errorKey = errorKey;
            return this;
        }

        /**
         * Sets whether to include the exception's stack trace.
         *
         * @param includeStackTraceOnError <code>true</code> to include the exception's stack trace; otherwise <code>false</code>
         * @return This instance
         */
        public WriteExceptionProps includeStackTraceOnError(boolean includeStackTraceOnError) {
            this.includeStackTraceOnError = includeStackTraceOnError;
            return this;
        }

        /**
         * Sets whether possible problematic arguments as indicated by an {@link OXException} instance should be considered.
         *
         * @param checkProblematic <code>true</code> to consider possible problematic arguments; otherwise <code>false</code>
         * @return This instance
         */
        public WriteExceptionProps checkProblematic(boolean checkProblematic) {
            this.checkProblematic = checkProblematic;
            return this;
        }

        /**
         * Sets whether possible truncated arguments as indicated by an {@link OXException} instance should be considered.
         *
         * @param checkTruncated <code>true</code> to consider possible truncated arguments; otherwise <code>false</code>
         * @return This instance
         */
        public WriteExceptionProps checkTruncated(boolean checkTruncated) {
            this.checkTruncated = checkTruncated;
            return this;
        }
    }

}
