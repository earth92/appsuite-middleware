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

package com.openexchange.file.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.conversion.ConversionResult;
import com.openexchange.conversion.DataArguments;
import com.openexchange.conversion.DataHandler;
import com.openexchange.conversion.SimpleData;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionCode;
import com.openexchange.session.Session;

import static com.openexchange.java.Autoboxing.b;
import static com.openexchange.java.Autoboxing.B;

/**
 * {@link FileStorageAccountErrorHandler} - provides functionality to handle and persist errors occurred while accessing a {@link FileStorageAccount}
 *
 * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
 * @since v7.10.5
 */
public class FileStorageAccountErrorHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(FileStorageAccountErrorHandler.class);

    private final FileStorageAccountAccess accountAccess;
    private final int retryAfterError;
    private final Session session;
    private final DataHandler json2error;
    private final DataHandler error2json;
    private final Function<OXException, Boolean> shouldSaveExceptionFunc;

    /**
     * {@link Result} - represents a result for handling an {@link OXException}
     *
     * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
     * @since v7.10.5
     */
    public static class Result {

        private final boolean handled;
        private final OXException exception;

        /**
         * Initializes a new {@link Result}.
         *
         * @param handled <code>true</code> if the error was handled, <code>false</code> if it was ignored.
         * @param exception The {@link OXException}
         */
        public Result(boolean handled, OXException exception) {
            this.handled = handled;
            this.exception = exception;
        }

        /**
         * Gets the handled
         *
         * @return <code>true</code> if the error was handled, <code>false</code> if it was ignored.
         */
        public boolean isHandled() {
            return handled;
        }

        /**
         * Gets the exception
         *
         * @return The {@link OXException}
         */
        public OXException getException() {
            return exception;
        }
    }

    /**
     * {@link CompositingFilter} - A compositing filter function which allows to set more than one filter functions.
     *
     * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
     * @since v7.10.5
     */
    public static class CompositingFilter implements Function<OXException, Boolean> {

        private final List<Function<OXException, Boolean>> functions;

        /**
         * Initializes a new {@link CompositingFilter}.
         */
        public CompositingFilter() {
            this.functions = new ArrayList<>();
        }

        /**
         * Initializes a new {@link CompositingFilter}.
         *
         * @param functions The filter functions to use
         */
        public CompositingFilter(List<Function<OXException, Boolean>> functions) {
            this.functions = functions;
        }

        /**
         * Adds a new filter function
         *
         * @param function The function to add
         * @return this
         */
        public CompositingFilter add(Function<OXException, Boolean> function) {
            functions.add(function);
            return this;
        }

        @Override
        public Boolean apply(OXException t) {
            if (functions != null) {
                for (Function<OXException, Boolean> function : functions) {
                    if (Boolean.FALSE.equals(function.apply(t))) {
                        return Boolean.FALSE;
                    }
                }
            }
            return Boolean.TRUE;
        }
    }

    /**
     * {@link IgnoreExceptionPrefixes} - Defines a {@link Function} which will ignore certain {@link OXException} based on their prefix ({@link OXException#getPrefix()}).
     *
     * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
     * @since v7.10.5
     */
    public static class IgnoreExceptionPrefixes implements Function<OXException, Boolean> {

        private final List<String> ignoredPrefixes;

        /**
         * Initializes a new {@link IgnoreExceptionPrefixes}.
         *
         * @param ignoredPrefixes A list of exception prefixes to ignore
         */
        public IgnoreExceptionPrefixes(List<String> ignoredPrefixes) {
            this.ignoredPrefixes = ignoredPrefixes;
        }

        /**
         * Initializes a new {@link IgnoreExceptionPrefixes}.
         *
         * @param ignoredPrefixes A list of exception prefixes to ignore
         */
        public IgnoreExceptionPrefixes(String... ignoredPrefixes) {
            this.ignoredPrefixes = Arrays.asList(ignoredPrefixes);
        }

        @Override
        public Boolean apply(OXException exception) {
            return B(exception != null && !ignoredPrefixes.contains(exception.getPrefix()));
        }
    }

    /**
     * {@link IgnoreExceptionCodes} - A filter function which will ignore certain {@link OXExceptionCode}s.
     *
     * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
     * @since v7.10.5
     */
    public static class IgnoreExceptionCodes implements Function<OXException, Boolean> {

        private final List<OXExceptionCode> ignoredCodes;

        /**
         * Initializes a new {@link IgnoreExceptionCodes}.
         *
         * @param ignoredCodes The list of {@link OXExceptionCode}s to ignore.
         */
        public IgnoreExceptionCodes(List<OXExceptionCode> ignoredCodes) {
            this.ignoredCodes = ignoredCodes;
        }

        /**
         * Initializes a new {@link IgnoreExceptionCodes}.
         *
         * @param ignoredCodes The list of {@link OXExceptionCode}s to ignore.
         */
        public IgnoreExceptionCodes(OXExceptionCode... ignoredCodes) {
            this.ignoredCodes = Arrays.asList(ignoredCodes);
        }

        @Override
        public Boolean apply(OXException exception) {
            if (exception != null && ignoredCodes != null) {
                if (ignoredCodes.stream().anyMatch(code -> exception.similarTo(code))) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
    }

    /**
     * Initializes a new {@link FileStorageAccountErrorHandler} which handles all errors.
     *
     * @param error2jsonDataHandler A {@link DataHandler} which will used to serialize an error
     * @param json2errorDataHandler A {@link DataHandler} which will used to de-serialize an error
     * @param accountAccess The related {@link XOXAccountAccess}
     * @param session The {@link Session}
     * @param retryAfterError The amount of time in seconds after which an persistent account error should be ignored.
     */
    //@formatter:off
    public FileStorageAccountErrorHandler(
            DataHandler error2jsonDataHandler,
            DataHandler json2errorDataHandler,
            FileStorageAccountAccess accountAccess,
            Session session,
            int retryAfterError) {
        this(error2jsonDataHandler, json2errorDataHandler, accountAccess, session, retryAfterError, (e) -> Boolean.TRUE);
    }
    //@formatter:on

    /**
     * Initializes a new {@link FileStorageAccountErrorHandler}.
     *
     * @param error2jsonDataHandler A {@link DataHandler} which will used to serialize an error
     * @param json2errorDataHandler A {@link DataHandler} which will used to de-serialize an error
     * @param accountAccess The related {@link XOXAccountAccess}
     * @param session The {@link Session}
     * @param retryAfterError The amount of time in seconds after which an persistent account error should be ignored.
     * @param A {@link Function} indicating which errors should be handled and saved.
     */
    //@formatter:off
    public FileStorageAccountErrorHandler(
            DataHandler error2jsonDataHandler,
            DataHandler json2errorDataHandler,
            FileStorageAccountAccess accountAccess,
            Session session,
            int retryAfterError,
            Function<OXException, Boolean> shouldSaveExceptionFunc) {
        this.json2error = Objects.requireNonNull(json2errorDataHandler, "json2errorDataHandler must not be null");
        this.error2json = Objects.requireNonNull(error2jsonDataHandler, "error2JsonDataHandler must not be null");
        this.accountAccess = Objects.requireNonNull(accountAccess, "accountAccess must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.retryAfterError = retryAfterError;
        this.shouldSaveExceptionFunc = shouldSaveExceptionFunc;
    }
    //@formatter:on

    /**
     * Checks whether or not the given exception should be saved to the DB
     *
     * @param exception The exception to check
     * @return <code>True</code> if the exception should be stored to the DB, <code>false</code> otherwise
     */
    private boolean shouldSaveException(OXException exception) {
        if (exception != null && shouldSaveExceptionFunc != null) {
            return b(shouldSaveExceptionFunc.apply(exception));
        }
        return false;
    }

    /**
     * Internal method to get the related {@link FileStorageAccount}
     *
     * @return The related {@link FileStorageAccount}
     * @throws OXException
     */
    private FileStorageAccount getAccount() throws OXException {
        return accountAccess.getService().getAccountManager().getAccount(accountAccess.getAccountId(), session);
    }

    /**
     * Gets the last known error for this account, occurred during the last n seconds.
     *
     * @param t The time in seconds
     * @return The error occurred in the last t seconds, or null if there is no current error or it occurred longer than the given t seconds.
     * @throws OXException In case of an JSON error
     */
    private FileStorageAccountError getRecentError(int t) throws OXException {
        try {
            final FileStorageAccount account = getAccount();
            JSONObject lastError = FileStorageAccountMetaDataUtil.getAccountError(account);
            if (lastError != null) {
                JSONObject error = lastError.getJSONObject(FileStorageAccountMetaDataUtil.JSON_FIELD_EXCEPTION);
                Date lastErrorTimeStamp = new Date(lastError.getLong(FileStorageAccountMetaDataUtil.JSON_FIELD_TIMESTAMP));
                //Check if the error occurred in the last n seconds
                final Instant now = new Date().toInstant();
                final Instant errorOccuredOn = lastErrorTimeStamp.toInstant();
                if (t <= 0 || errorOccuredOn.isAfter(now.minusSeconds(t))) {
                    ConversionResult result = json2error.processData(new SimpleData<JSONObject>(error), new DataArguments(), null);
                    if (result.getData() != null && OXException.class.isInstance(result.getData())) {
                        //The error occurred in the last n seconds
                        return new FileStorageAccountError((OXException) result.getData(), lastErrorTimeStamp);
                    } else if (result.getData() == null) {
                        LOGGER.warn("Cannot parse exception from empty result data.");
                    } else {
                        LOGGER.warn("Cannot parse exception from unknown result data.");
                    }
                }
            }
            return null;
        } catch (JSONException e) {
            throw FileStorageExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Gets the last known exception for this account, occurred during the last t seconds.
     *
     * @param t The time in seconds
     * @return The exception occurred in the last t seconds, or null if there is no current error or it occurred longer than the given t seconds.
     * @throws OXException In case of an JSON error
     */
    private OXException getRecentException(int t) throws OXException {
        FileStorageAccountError lastError = getRecentError(t);
        return lastError != null ? lastError.getException() : null;
    }

    /**
     * Gets the last known exception for this account, occurred during the last t (configured) seconds.
     * getRecentException
     *
     * @return The exception occurred in the last t (configured) seconds, or null if there is no current error or it occurred longer than the given t seconds.
     * @throws OXException In case of an JSON error
     */
    public OXException getRecentException() throws OXException {
        return getRecentException(retryAfterError);
    }

    /**
     * Asserts that there was no recent exception, in the last t seconds, accessing this account
     *
     * @param t time in seconds
     * @param ignore If <code>ignore</code> is set to true, this method does actually nothing; useful for a more fluent like style for the caller
     * @throws OXException if there was a known exception in the last t seconds
     */
    private void assertNoRecentException(int t) throws OXException {
        OXException recentException = getRecentException(t);
        if (recentException != null) {
            throw recentException;
        }
    }

    /**
     * Asserts that there was no recent exception, in the last t (configured) seconds, accessing this account.
     *
     * @throws OXException if there was a known exception in the last t seconds
     */
    public void assertNoRecentException() throws OXException {
        assertNoRecentException(retryAfterError);
    }

    /**
     * Checks if there is a recent exception, in the last t (configured) seconds, accessing this account.
     *
     * @return <code>true</code> if there was a recent exception in the last t (configured) seconds, <code>false</code> otherwise.
     * @throws OXException In case of an JSON error
     */
    public boolean hasRecentException() throws OXException {
        return getRecentException(retryAfterError) != null;
    }

    /**
     * Handles an exception; i.e. if the exception is found to be long-lasting, it is saved to the DB
     *
     * @param exception exception to save
     * @return The exception
     * @throws OXException If the exception could not be saved
     */
    public Result handleException(OXException exception) throws OXException {
        try {
            boolean handled = false;
            if (shouldSaveException(exception)) {
                FileStorageAccount account = getAccount();
                JSONObject metadata = FileStorageAccountMetaDataUtil.getAccountMetaData(account);
                JSONObject lastError = FileStorageAccountMetaDataUtil.getAccountError(account);
                if (lastError == null) {
                    lastError = new JSONObject();
                    metadata.put(FileStorageAccountMetaDataUtil.JSON_FIELD_LAST_ERROR, lastError);
                }
                lastError.put(FileStorageAccountMetaDataUtil.JSON_FIELD_TIMESTAMP, new Date().getTime());

                ConversionResult result = error2json.processData(new SimpleData<OXException>(exception), new DataArguments(), null);
                Object data = result.getData();
                if (data != null && JSONObject.class.isInstance(data)) {
                    lastError.put(FileStorageAccountMetaDataUtil.JSON_FIELD_EXCEPTION, data);
                }

                accountAccess.getService().getAccountManager().updateAccount(account, session);
                handled = true;
            }
            return new Result(handled, exception);
        } catch (JSONException e) {
            throw FileStorageExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Deletes the recent exception, if any
     *
     * @throws OXException In case of account errors
     */
    public void removeRecentException() throws OXException {
        FileStorageAccount account = getAccount();
        JSONObject metadata = account.getMetadata();
        Object objectRemoved = metadata.remove(FileStorageAccountMetaDataUtil.JSON_FIELD_LAST_ERROR);
        if (objectRemoved != null) {
            accountAccess.getService().getAccountManager().updateAccount(account, session);
        }
    }
}
