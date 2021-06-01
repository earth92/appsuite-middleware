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

package com.openexchange.database;

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;

/**
 * {@link IncorrectStringSQLException} - The special SQL exception signaling an attempt to pass an incorrect string to database.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class IncorrectStringSQLException extends StringLiteralSQLException {

    private static final long serialVersionUID = 2713082500383087281L;

    private static final Pattern PATTERN_ERROR_MESSAGE = Pattern.compile("(?:Data truncation: )?" + Pattern.quote("Incorrect string value:") + " *" + "'([^']+)'" + " for column " + "'([^']+)'" + " at row " + "([0-9]+)");

    /** The (vendor) error code <code>1366</code> that signals an attempt to pass an incorrect string to database */
    public static final int ERROR_CODE = com.mysql.jdbc.MysqlErrorNumbers.ER_TRUNCATED_WRONG_VALUE_FOR_FIELD;

    public static final char UNKNOWN = '\ufffd';

    /**
     * Attempts to yield an appropriate {@code IncorrectStringSQLException} instance for specified SQL exception.
     *
     * @param e The SQL exception
     * @return The appropriate {@code IncorrectStringSQLException} instance or <code>null</code>
     */
    public static IncorrectStringSQLException instanceFor(SQLException e) {
        if (null == e) {
            return null;
        }
        if (ERROR_CODE != e.getErrorCode()) {
            return null;
        }

        // E.g. "Incorrect string value: '\xF0\x9F\x92\xA9' for column 'field01' at row 1"
        Matcher m = PATTERN_ERROR_MESSAGE.matcher(e.getMessage());
        if (!m.matches()) {
            return null;
        }

        // Parse incorrect string value
        String incorrect;
        {
            ByteArrayOutputStream buf = Streams.newByteArrayOutputStream(4);
            String ic = m.group(1);
            int end = 0;
            for (int st = 0; ic.indexOf("\\x", st) == end;) {
                end = st + 4;
                buf.write(Integer.parseInt(ic.substring(st + 2, end), 16));
                st = end;

            }
            incorrect = new String(buf.toByteArray(), Charsets.UTF_8);
            int posUnknown = incorrect.indexOf(UNKNOWN);
            if (posUnknown > 0) {
                incorrect = incorrect.substring(0, posUnknown);
            }
        }

        return new IncorrectStringSQLException(incorrect, m.group(2), Integer.parseInt(m.group(3)), e);
    }

    // ---------------------------------------------------------------------------------------------------------------------

    private final String incorrectString;
    private final String column;
    private final int row;

    /**
     * Initializes a new {@link IncorrectStringSQLException}.
     *
     * @param incorrectString The incorrect string
     * @param column The column name
     * @param row The row number
     * @param sqlState The SQL state
     * @param vendorCode The vendor code (always <code>1366</code>)
     * @param cause The associated SQL exception
     */
    public IncorrectStringSQLException(String incorrectString, String column, int row, SQLException cause) {
        super(cause);
        this.incorrectString = incorrectString;
        this.column = column;
        this.row = row;
    }

    /**
     * Gets the incorrect string
     *
     * @return The incorrect string
     */
    public String getIncorrectString() {
        return incorrectString;
    }

    /**
     * Gets the column
     *
     * @return The column
     */
    public String getColumn() {
        return column;
    }

    /**
     * Gets the row
     *
     * @return The row
     */
    public int getRow() {
        return row;
    }

}
