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

package com.openexchange.groupware.tools.mappings.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.openexchange.database.DatabaseExceptionCodes;
import com.openexchange.database.IncorrectStringSQLException;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.tools.mappings.DefaultMapping;
import com.openexchange.groupware.tools.mappings.MappedIncorrectString;

/**
 * {@link DefaultDbMapping} - Abstract {@link DbMapping} implementation.
 *
 * @param <T> the type of the property
 * @param <O> the type of the object
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public abstract class DefaultDbMapping<T, O> extends DefaultMapping<T, O> implements DbMapping<T, O> {

    /** Pattern to extract problematic characters from verifier error messages */
    private static final Pattern HEX_CHARACTER_PATTERN = Pattern.compile("\\b0x([a-fA-F0-9]+)\\b");

	private final String columnLabel;
	private final String readableName;
	private final int sqlType;

	/**
	 * Initializes a new {@link DefaultDbMapping}.
	 *
	 * @param columnLabel the column label
	 * @param sqlType the SQL type
	 */
	public DefaultDbMapping(final String columnLabel, final int sqlType) {
		this(columnLabel, null, sqlType);
	}

	/**
	 * Initializes a new {@link DefaultDbMapping}.
	 *
	 * @param columnLabel the column label
	 * @param readableName the readable label
	 * @param sqlType the SQL type
	 */
	public DefaultDbMapping(final String columnLabel, final String readableName, final int sqlType) {
		super();
		this.sqlType = sqlType;
		this.columnLabel = columnLabel;
		this.readableName = readableName;
	}

	@Override
    public int set(final PreparedStatement statement, final int parameterIndex, final O object) throws SQLException {
		if (this.isSet(object)) {
			final T value = this.get(object);
			if (null != value) {
				statement.setObject(parameterIndex, value, this.getSqlType());
			} else {
				statement.setNull(parameterIndex, this.getSqlType());
			}
		} else {
			statement.setNull(parameterIndex, this.getSqlType());
		}
        return 1;
	}

    /**
     * {@inheritDoc}
     * <p/>
     * Neutral default implementation, override if applicable.
     */
    @Override
    public void validate(O object) throws OXException {
        // empty
    }

    @Override
    public void set(final ResultSet resultSet, final O object) throws SQLException, OXException {
        set(resultSet, object, getColumnLabel());
    }

    @Override
    public void set(final ResultSet resultSet, final O object, String columnLabel) throws SQLException, OXException {
        final T value = this.get(resultSet, columnLabel);
        if (false == resultSet.wasNull()) {
            this.set(object, value);
        }
    }

    @Override
    public String getColumnLabel() {
        return this.columnLabel;
    }

    @Override
    public String getColumnLabel(String prefix) {
        return prefix + this.columnLabel;
    }

	@Override
	public String getReadableName(O Object) {
		return this.readableName;
	}

	@Override
	public int getSqlType() {
		return this.sqlType;
	}

    /**
     * Validates the supplied string value against allowed character data, throwing an exception if validation fails.
     * <p/>
     * If validation fails, a {@link DatabaseExceptionCodes#STRING_LITERAL_ERROR} exception is thrown, decorated with a suitable
     * {@link MappedIncorrectString} as problematic attribute, if possible.
     *
     * @param value The string to validate, or <code>null</code> to skip
     * @throws OXException {@link DatabaseExceptionCodes#STRING_LITERAL_ERROR} if validation fails
     */
    protected void validateString(String value) throws OXException {
        if (null == value) {
            return;
        }
        String result = org.jdom2.Verifier.checkCharacterData(value);
        if (null == result) {
            return;
        }
        Matcher matcher = HEX_CHARACTER_PATTERN.matcher(result);
        if (matcher.find()) {
            String incorrectString = matcher.group(1);
            try {
                char character = (char) Integer.parseInt(incorrectString, 16);
                incorrectString = String.valueOf(character);
            } catch (Exception e) {
                // ignore & fall back to matched string
                org.slf4j.LoggerFactory.getLogger(DefaultDbMapping.class).debug(
                    "Error parsing problematic character from \"{}\", falling back to \"{}\".", result, incorrectString, e);
            }
            MappedIncorrectString<O> mappedIncorrectString = new MappedIncorrectString<O>(this, incorrectString, getReadableName(null));
            IncorrectStringSQLException cause = new IncorrectStringSQLException(incorrectString, getColumnLabel(), 1, new SQLException());
            OXException e = DatabaseExceptionCodes.STRING_LITERAL_ERROR.create(cause, incorrectString);
            e.addProblematic(mappedIncorrectString);
            throw e;
        }
    }

	@Override
	public String toString() {
		return String.format("[%s] %s", columnLabel, readableName);
	}

}
