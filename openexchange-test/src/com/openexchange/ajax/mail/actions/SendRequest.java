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

package com.openexchange.ajax.mail.actions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.framework.AJAXRequest;
import com.openexchange.ajax.framework.AbstractUploadParser;
import com.openexchange.ajax.framework.Header;

/**
 * {@link SendRequest}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class SendRequest implements AJAXRequest<SendResponse> {

    private static final String DEFAULT_MIME_TYPE = "text/plain; charset=us-ascii";

    /**
     * URL of the tasks AJAX interface.
     */
    public static final String MAIL_URL = "/ajax/mail";

    private final String mailStr;

    private final List<InputStream> uploads;

    private final boolean failOnError;

    private final String mimeType;

    /**
     * Initializes a new {@link SendRequest}
     *
     * @param mailStr The mail string (JSON)
     */
    public SendRequest(final String mailStr) {
        this(mailStr, null);
    }

    public SendRequest(String mailStr, boolean failOnError) {
        this(mailStr, null, failOnError);
    }

    public SendRequest(String mailStr, InputStream upload, boolean failOnError) {
        this(mailStr, upload, DEFAULT_MIME_TYPE, failOnError);
    }
    
    public SendRequest(String mailStr, InputStream upload, String mimeType, boolean failOnError) {
        super();
        this.mailStr = mailStr;
        this.mimeType = mimeType;
        this.uploads = new LinkedList<InputStream>();
        if (null != upload) {
            this.uploads.add(upload);
        }
        this.failOnError = failOnError;
    }

    /**
     * Initializes a new {@link SendRequest}
     *
     * @param mailStr The mail string (JSON)
     * @param upload The upload input stream
     */
    public SendRequest(final String mailStr, final InputStream upload) {
        this(mailStr, upload, true);
    }

    /**
     * Adds an upload
     *
     * @param upload The upload
     */
    public void addUpload(final InputStream upload) {
        this.uploads.add(upload);
    }

    @Override
    public Object getBody() {
        return null;
    }

    @Override
    public Method getMethod() {
        return Method.UPLOAD;
    }

    @Override
    public Header[] getHeaders() {
        return NO_HEADER;
    }

    @Override
    public Parameter[] getParameters() {
        final List<Parameter> params = new ArrayList<AJAXRequest.Parameter>(4);
        params.add(new Parameter(AJAXServlet.PARAMETER_ACTION, AJAXServlet.ACTION_NEW));
        params.add(new FieldParameter("json_0", mailStr));
        if (null != uploads) {
            final int size = uploads.size();
            for (int i = 0; i < size; i++) {
                final String sNum = Integer.toString(i + 1);
                params.add(new FileParameter("file_" + sNum, "text" + sNum + ".txt", uploads.get(i), mimeType));
            }
        }
        return params.toArray(new Parameter[params.size()]);
    }

    @Override
    public String getServletPath() {
        return MAIL_URL;
    }

    @Override
    public SendParser getParser() {
        return new SendParser(failOnError);
    }

    private static final class SendParser extends AbstractUploadParser<SendResponse> {

        public SendParser(final boolean failOnError) {
            super(failOnError);
        }

        @Override
        protected SendResponse createResponse(final Response response) {
            return new SendResponse(response);
        }

    }
}
