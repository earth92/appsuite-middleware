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

package com.openexchange.chronos.storage.rdb;

import static com.openexchange.chronos.common.CalendarUtils.ID_COMPARATOR;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.exception.ProblemSeverity;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.tools.mappings.MappedProblematic;
import com.openexchange.groupware.tools.mappings.Mapping;
import com.openexchange.groupware.tools.mappings.database.DbMapping;
import com.openexchange.i18n.tools.StringHelper;

/**
 * {@link CalendarStorage}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public abstract class CalendarStorageWarnings {

    /** A named logger instance */
    protected static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CalendarStorageWarnings.class);

    private SortedMap<String, List<OXException>> warnings;
    private ProblemSeverity unsupportedDataThreshold;
    private Locale locale;

    /**
     * Initializes a new {@link CalendarStorageWarnings}.
     */
    protected CalendarStorageWarnings() {
        super();
        this.unsupportedDataThreshold = ProblemSeverity.MINOR;
    }

    /**
     * Sets the locale to use when generating translated error messages.
     *
     * @param locale The locale
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * Configures the severity threshold defining which unsupported data errors can be ignored.
     *
     * @param severityThreshold The threshold defining up to which severity unsupported data errors can be ignored, or
     *            <code>null</code> to not ignore any unsupported data error at all
     */
    public void setUnsupportedDataThreshold(ProblemSeverity severityThreshold) {
        this.unsupportedDataThreshold = severityThreshold;
    }

    /**
     * Gets the severity threshold defining which unsupported data errors can be ignored.
     * 
     * @return The threshold defining up to which severity unsupported data errors can be ignored, or <code>null</code> if unsupported
     *         data is not ignored at all
     */
    public ProblemSeverity getUnsupportedDataThreshold() {
        return unsupportedDataThreshold;
    }

    /**
     * Keeps track of a warning that occurred when processing the data of a specific event.
     *
     * @param eventId The identifier of the event the warning is associated with
     * @param warning The warning
     */
    public void addWarning(String eventId, OXException warning) {
        if (null == warnings) {
            warnings = new TreeMap<String, List<OXException>>(ID_COMPARATOR);
        }
        com.openexchange.tools.arrays.Collections.put(warnings, eventId, warning);
    }

    /**
     * Initializes and keeps track of a {@link CalendarExceptionCodes#IGNORED_INVALID_DATA} warning that occurred when processing the data
     * of a specific event.
     *
     * @param eventId The identifier of the event the warning is associated with
     * @param field The corresponding event field of the invalid data
     * @param severity The problem severity
     * @param message The message providing details of the warning
     * @param cause The optional initial cause
     * @return The added warning
     */
    public OXException addInvalidDataWarning(String eventId, EventField field, ProblemSeverity severity, String message, Throwable cause) {
        OXException warning = CalendarExceptionCodes.IGNORED_INVALID_DATA.create(cause, eventId, getReadableName(field), String.valueOf(severity), message);
        Mapping<? extends Object, Event> mapping = com.openexchange.chronos.common.mapping.EventMapper.getInstance().opt(field);
        if (null != mapping) {
            warning.addProblematic(new MappedProblematic<Event>(mapping));
        }
        if (0 > ProblemSeverity.NORMAL.compareTo(severity)) {
            LOG.info(warning.getLogMessage());
        } else {
            LOG.debug(warning.getLogMessage());
        }
        addWarning(eventId, warning);
        return warning;
    }

    /**
     * Initializes a new {@link CalendarExceptionCodes#UNSUPPORTED_DATA} error that occurred when processing the data of a specific event.
     * <p/>
     * In case errors up to a certain problem severity can be ignored, an appropriate warning is tracked, otherwise, the error is raised.
     *
     * @param eventId The identifier of the event the error is associated with
     * @param field The corresponding event field of the unsupported data
     * @param severity The problem severity
     * @param message The message providing details of the error
     * @throws OXException {@link CalendarExceptionCodes#UNSUPPORTED_DATA}
     */
    public void addUnsupportedDataError(String eventId, EventField field, ProblemSeverity severity, String message) throws OXException {
        addUnsupportedDataError(eventId, field, severity, message, null);
    }

    /**
     * Initializes and adds a new {@link CalendarExceptionCodes#UNSUPPORTED_DATA} error that occurred when processing the data of a specific event.
     * <p/>
     * In case errors up to a certain problem severity can be ignored, an appropriate warning is tracked, otherwise, the error is raised.
     *
     * @param eventId The identifier of the event the error is associated with
     * @param field The corresponding event field of the unsupported data
     * @param severity The problem severity
     * @param message The message providing details of the error
     * @param cause The optional initial cause
     * @throws OXException {@link CalendarExceptionCodes#UNSUPPORTED_DATA}
     */
    public void addUnsupportedDataError(String eventId, EventField field, ProblemSeverity severity, String message, Throwable cause) throws OXException {
        OXException error = getUnsupportedDataError(eventId, field, severity, message, cause);
        if (null == unsupportedDataThreshold || 0 > unsupportedDataThreshold.compareTo(severity)) {
            throw error;
        }
        addInvalidDataWarning(eventId, field, severity, message, error);
    }

    /**
     * Initializes a new {@link CalendarExceptionCodes#UNSUPPORTED_DATA} error that occurred when processing the data of a specific event.
     * <p/>
     * Any error arguments are also put into the exceptions argument collection, also, an appropriate {@link MappedProblematic} is added
     * based on the given event field.
     *
     * @param eventId The identifier of the event the error is associated with
     * @param field The corresponding event field of the unsupported data
     * @param severity The problem severity
     * @param message The message providing details of the error
     * @param cause The optional initial cause
     * @return The initialized {@link CalendarExceptionCodes#UNSUPPORTED_DATA}
     */
    public OXException getUnsupportedDataError(String eventId, EventField field, ProblemSeverity severity, String message, Throwable cause) {
        OXException error = CalendarExceptionCodes.UNSUPPORTED_DATA.create(cause, eventId, getReadableName(field), String.valueOf(severity), message);
        error.setArgument("severity", severity);
        error.setArgument("eventId", eventId);
        error.setArgument("field", field);
        error.setArgument("message", message);
        Mapping<? extends Object, Event> mapping = com.openexchange.chronos.common.mapping.EventMapper.getInstance().opt(field);
        if (null != mapping) {
            error.addProblematic(new MappedProblematic<Event>(mapping));
        }
        return error;
    }

    /**
     * Gets any tracked warnings that occurred when processing the stored data.
     *
     * @return The warnings, mapped to the associated event identifier, or an empty map if there are none
     */
    public Map<String, List<OXException>> getWarnings() {
        return null == warnings ? Collections.<String, List<OXException>> emptyMap() : warnings;
    }

    /**
     * Gets any tracked warnings that occurred when processing the stored data and flushes them, so that subsequent invocations would
     * return an empty map.
     *
     * @return The warnings, mapped to the associated event identifier, or an empty map if there are none
     */
    public Map<String, List<OXException>> getAndFlushWarnings() {
        if (null == warnings) {
            return Collections.emptyMap();
        }
        Map<String, List<OXException>> result = new TreeMap<String, List<OXException>>(warnings);
        warnings = null;
        return result;
    }

    /**
     * Gets the human-readable name for a specific event field, optionally translated if a locale is defined.
     *
     * @param field The field to get the readable name for
     * @return The readable name, falling back to the plain field name if no readable name is available
     */
    protected String getReadableName(EventField field) {
        return getReadableName(field, EventMapper.getInstance().opt(field));
    }

    /**
     * Gets the human-readable name for a specific event field, optionally translated if a locale is defined.
     *
     * @param field The field to get the readable name for
     * @param mapping The associated database mapping for the field, or <code>null</code> if there is none
     * @return The readable name, falling back to the plain field name if no readable name is available
     */
    protected <O, E extends Enum<E>> String getReadableName(E field, DbMapping<? extends Object, O> mapping) {
        if (null != mapping) {
            String readableName = mapping.getReadableName(null);
            if (null != readableName) {
                return StringHelper.valueOf(locale).getString(readableName);
            }
        }
        return String.valueOf(field);
    }

}
