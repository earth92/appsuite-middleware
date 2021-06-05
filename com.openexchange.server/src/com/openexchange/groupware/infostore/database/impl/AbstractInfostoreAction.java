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

package com.openexchange.groupware.infostore.database.impl;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONValue;
import com.openexchange.ajax.tools.JSONCoercion;
import com.openexchange.database.IncorrectStringSQLException;
import com.openexchange.database.tx.AbstractDBAction;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.MediaStatus;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.infostore.InfostoreFolderPath;
import com.openexchange.groupware.infostore.utils.GetSwitch;
import com.openexchange.groupware.infostore.utils.Metadata;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.java.GeoLocation;
import com.openexchange.session.Session;
import com.openexchange.tools.exceptions.SimpleIncorrectStringAttribute;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;

public abstract class AbstractInfostoreAction extends AbstractDBAction {

    /** The associated session (optional) */
    protected final Session optSession;

    /** The catalog queries */
	private InfostoreQueryCatalog queries;

	/**
	 * Initializes a new {@link AbstractInfostoreAction}.
	 *
	 * @param optSession The optional session
	 */
	protected AbstractInfostoreAction(Session optSession) {
	    super();
	    this.optSession = optSession;
	}

	private static User getUser(Session session) throws OXException {
        if (session instanceof ServerSession) {
            return ((ServerSession) session).getUser();
        }
        return UserStorage.getInstance().getUser(session.getUserId(), session.getContextId());
    }

	/**
     * Launders specified <code>OXException</code> instance.
     *
     * @param e The <code>OXException</code> instance
     * @param optSession The optional session
     * @return The appropriate <code>OXException</code> instance
     * @throws OXException If operation fails
     */
    public static OXException launderOXException(OXException e, Session optSession) throws OXException {
        Throwable cause = e.getCause();
        if (!(cause instanceof IncorrectStringSQLException)) {
            return e;
        }

        return handleIncorrectStringError((IncorrectStringSQLException) cause, optSession);
    }

    /**
     * Handles specified <code>IncorrectStringSQLException</code> instance.
     *
     * @param incorrectStringError The incorrect string error to handle
     * @param optSession The optional session
     * @return The appropriate <code>OXException</code> instance
     * @throws OXException If operation fails
     */
    public static OXException handleIncorrectStringError(IncorrectStringSQLException incorrectStringError, Session optSession) throws OXException {
        Metadata metadata = Metadata.get(incorrectStringError.getColumn());
        if (null == metadata) {
            return InfostoreExceptionCodes.INVALID_CHARACTER_SIMPLE.create(incorrectStringError);
        }
        return handleIncorrectStringError(incorrectStringError, metadata, optSession);
    }

    /**
     * Handles specified <code>IncorrectStringSQLException</code> instance.
     *
     * @param incorrectStringError The incorrect string error to handle
     * @param metadata The associated meta data
     * @param optSession The optional session
     * @return The appropriate <code>OXException</code> instance
     * @throws OXException If operation fails
     */
    public static OXException handleIncorrectStringError(IncorrectStringSQLException incorrectStringError, Metadata metadata, Session optSession) throws OXException {
        if (null == optSession) {
            return InfostoreExceptionCodes.INVALID_CHARACTER.create(incorrectStringError, incorrectStringError.getIncorrectString(), incorrectStringError.getColumn());
        }
        String displayName = metadata.getDisplayName();
        if (null == displayName) {
            return InfostoreExceptionCodes.INVALID_CHARACTER.create(incorrectStringError, incorrectStringError.getIncorrectString(), incorrectStringError.getColumn());
        }

        String translatedName = StringHelper.valueOf(getUser(optSession).getLocale()).getString(displayName);
        OXException oxe = InfostoreExceptionCodes.INVALID_CHARACTER.create(incorrectStringError, incorrectStringError.getIncorrectString(), translatedName);
        oxe.addProblematic(new SimpleIncorrectStringAttribute(metadata.getId(), incorrectStringError.getIncorrectString()));
        return oxe;
    }

	@Override
	protected int doUpdates(List<UpdateBlock> updates) throws OXException {
	    try {
            return super.doUpdates(updates);
        } catch (OXException e) {
            throw launderOXException(e, optSession);
        }
	}

	@Override
	protected int doUpdates(UpdateBlock... updates) throws OXException {
	    try {
            return super.doUpdates(updates);
        } catch (OXException e) {
            throw launderOXException(e, optSession);
        }
	}

    protected final void fillStmt(final PreparedStatement stmt, final Metadata[] fields, final DocumentMetadata doc, final Object...additional) throws SQLException {
        fillStmt(1, stmt, fields, doc, additional);
    }

    /**
     * Fills the supplied prepared statement using the values of the denoted fields from a document.
     *
     * @param parameterIndex The (1-based) parameter index to start with
     * @param stmt The statement to populate
     * @param fields The used fields
     * @param doc The document to get the field values from
     * @param additional Any additional arbitrary fields to set in the statement after the document values were set
     * @return The updated parameter index
     * @throws SQLException
     */
    protected final int fillStmt(final int parameterIndex, final PreparedStatement stmt, final Metadata[] fields, final DocumentMetadata doc, final Object...additional) throws SQLException {
        final GetSwitch get = new GetSwitch(doc);
        int index = parameterIndex;
        for(final Metadata m : fields) {
            int id = m.getId();
            if (Metadata.META_LITERAL.getId() == id) {
                setMeta(index++, stmt, doc.getMeta());
            } else if (Metadata.MEDIA_META_LITERAL.getId() == id) {
                setMeta(index++, stmt, doc.getMediaMeta());
            } else if (Metadata.ORIGIN_LITERAL.getId() == id) {
                setOriginPath(index++, stmt, doc);
            } else if (Metadata.GEOLOCATION_LITERAL.getId() == id) {
                setGeoLocation(index++, stmt, doc);
            } else if (Metadata.MEDIA_STATUS_LITERAL.getId() == id) {
                setMediaStatus(index++, stmt, doc);
            } else if (Metadata.WIDTH == id || Metadata.HEIGHT == id || Metadata.CAMERA_ISO_SPEED == id) {
                setIfNotNullAndPositive(index++, stmt, (Long) m.doSwitch(get));
            } else if (Metadata.CAMERA_APERTURE == id || Metadata.CAMERA_EXPOSURE_TIME == id || Metadata.CAMERA_FOCAL_LENGTH == id) {
                setMediaFieldIfNotNullAndPositive(index++, stmt, (Double) m.doSwitch(get));
            } else {
                stmt.setObject(index++, process(m, m.doSwitch(get)));
            }
        }
        for (Object o : additional) {
            stmt.setObject(index++, o);
        }
        return index;
    }

    private final Object process(final Metadata field, final Object value) {
        switch (field.getId()) {
        default:
            return value;
        case Metadata.CREATION_DATE:
        case Metadata.LOCKED_UNTIL:
        case Metadata.LAST_MODIFIED_UTC:
            return Long.valueOf(((Date) value).getTime());
        case Metadata.CAPTURE_DATE:
        case Metadata.MEDIA_DATE:
            return null == value ? null : Long.valueOf(((Date) value).getTime());
        case Metadata.LAST_MODIFIED:
            return (value != null) ? Long.valueOf(((Date) value).getTime()) : Long.valueOf(System.currentTimeMillis());
        }
    }

    private final void setIfNotNullAndPositive(int parameterIndex, PreparedStatement stmt, Long value) throws SQLException {
        if (null == value || value.longValue() < 0) {
            stmt.setNull(parameterIndex, java.sql.Types.INTEGER);
        } else {
            stmt.setObject(parameterIndex, value);
        }
    }

    private final void setMediaFieldIfNotNullAndPositive(int parameterIndex, PreparedStatement stmt, Double value) throws SQLException {
        if (null == value || value.longValue() < 0) {
            stmt.setNull(parameterIndex, java.sql.Types.DOUBLE);
        } else {
            double x = value.doubleValue();
            if (x == Double.POSITIVE_INFINITY) {
                // Store a negative value to signal infinity
                stmt.setObject(parameterIndex, Double.valueOf(-1));
            } else if (x == Double.NEGATIVE_INFINITY || Double.isNaN(x)) {
                // Such a value cannot be stored
                stmt.setNull(parameterIndex, java.sql.Types.DOUBLE);
            } else {
                stmt.setObject(parameterIndex, value);
            }
        }
    }

    private final void setMediaStatus(int parameterIndex, final PreparedStatement stmt, final DocumentMetadata doc) throws SQLException {
        MediaStatus status = doc.getMediaStatus();
        if (null == status) {
            stmt.setNull(parameterIndex, java.sql.Types.VARCHAR);
        } else {
            stmt.setString(parameterIndex, status.toString());
        }
    }

    private final void setGeoLocation(int parameterIndex, final PreparedStatement stmt, final DocumentMetadata doc) throws SQLException {
        GeoLocation geolocation = doc.getGeoLocation();
        if (null == geolocation) {
            stmt.setNull(parameterIndex, java.sql.Types.VARCHAR); // geolocation
        } else {
            stmt.setString(parameterIndex, geolocation.toSqlPoint()); // geolocation
        }
    }

    private final void setOriginPath(int parameterIndex, final PreparedStatement stmt, final DocumentMetadata doc) throws SQLException {
        InfostoreFolderPath folderPath = doc.getOriginFolderPath();
        if (null == folderPath) {
            stmt.setNull(parameterIndex, java.sql.Types.VARCHAR); // origin
        } else {
            stmt.setString(parameterIndex, folderPath.toString()); // origin
        }
    }

    private final void setMeta(int parameterIndex, PreparedStatement stmt, Map<String, Object> meta) throws SQLException {
        if (null == meta || meta.isEmpty()) {
            stmt.setNull(parameterIndex, java.sql.Types.BLOB); // meta
        } else {
            try {
                final Object coerced = JSONCoercion.coerceToJSON(meta);
                if (null == coerced || JSONObject.NULL.equals(coerced)) {
                    stmt.setNull(parameterIndex, java.sql.Types.BLOB); // meta
                } else {
                    JSONValue jValue = (JSONValue) coerced;
                    stmt.setBinaryStream(parameterIndex, jValue.getStream(true)); // meta
                }
            } catch (JSONException e) {
                throw new SQLException("Meta information could not be coerced to a JSON equivalent.", e);
            }
        }
    }

	public void setQueryCatalog(final InfostoreQueryCatalog queries){
		this.queries = queries;
	}

	public InfostoreQueryCatalog getQueryCatalog(){
		return this.queries;
	}

}
