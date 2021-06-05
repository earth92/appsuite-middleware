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

package com.openexchange.chronos.itip.sender.datasources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import javax.activation.DataSource;
import com.openexchange.exception.OXException;
import com.openexchange.java.Streams;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.tools.stream.UnsynchronizedByteArrayOutputStream;

/**
 * {@link MessageDataSource} - Allows creation of a data source by either an input stream, a string or a byte array.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class MessageDataSource implements DataSource {

    private static final int DEFAULT_BUF_SIZE = 0x1000;

    private final byte[] data;

    private final String contentType;

    private String name;

    /**
     * Create a data source from an input stream
     * 
     * @param inputStream The {@link InputStream} that holds the data. Gets copied and closed.
     * @param contentType The {@link ContentType} as string
     * @throws IOException See {@link InputStream#read(byte[], int, int)}
     */
    public MessageDataSource(final InputStream inputStream, final String contentType) throws IOException {
        this(inputStream, contentType, null);
    }

    /**
     * Create a data source from an input stream
     * 
     * @param inputStream The {@link InputStream} that holds the data. Gets copied and closed.
     * @param contentType The {@link ContentType}
     * @throws IOException See {@link InputStream#read(byte[], int, int)}
     */
    public MessageDataSource(final InputStream inputStream, final ContentType contentType) throws IOException {
        this(inputStream, contentType, null);
    }

    /**
     * Create a data source from an input stream
     * 
     * @param inputStream The {@link InputStream} that holds the data. Gets copied and closed.
     * @param contentType The {@link ContentType} as string
     * @param name The name to return for {@link #getName()}
     * @throws IOException See {@link InputStream#read(byte[], int, int)}
     */
    public MessageDataSource(final InputStream inputStream, final String contentType, final String name) throws IOException {
        this.contentType = contentType;
        this.data = copyStream(inputStream);
        this.name = name;
    }

    /**
     * Create a data source from an input stream
     * 
     * @param inputStream The {@link InputStream} that holds the data. Gets copied and closed.
     * @param contentType The {@link ContentType}
     * @param name The name to return for {@link #getName()}
     * @throws IOException See {@link InputStream#read(byte[], int, int)}
     */
    public MessageDataSource(final InputStream inputStream, final ContentType contentType, final String name) throws IOException {
        this(inputStream, contentType.toString(), name);
    }

    /**
     * Create a data source from a byte array
     * 
     * @param data The data to hold
     * @param contentType The {@link ContentType}
     */
    public MessageDataSource(final byte[] data, final String contentType) {
        this.contentType = contentType;
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    /**
     * Create a data source from a String
     * 
     * @param data The data
     * @param contentType The {@link ContentType} as string
     * @throws UnsupportedEncodingException see {@link String#getBytes(java.nio.charset.Charset)}
     * @throws OXException See {@link ContentType#ContentType(String)}
     */
    public MessageDataSource(final String data, final String contentType) throws UnsupportedEncodingException, OXException {
        final ContentType ct = new ContentType(contentType);
        if (!ct.containsCharsetParameter()) {
            ct.setCharsetParameter(MailProperties.getInstance().getDefaultMimeCharset());
        }
        this.data = data.getBytes(ct.getCharsetParameter());
        this.contentType = ct.toString();
    }

    /**
     * Create a data source from a String
     * 
     * @param data The data
     * @param contentType The {@link ContentType}
     * @throws UnsupportedEncodingException see {@link String#getBytes(java.nio.charset.Charset)}
     */
    public MessageDataSource(final String data, final ContentType contentType) throws UnsupportedEncodingException {
        final ContentType ct;
        if (contentType.containsCharsetParameter()) {
            ct = contentType;
        } else {
            ct = new ContentType();
            ct.setContentType(contentType);
            ct.setCharsetParameter(MailProperties.getInstance().getDefaultMimeCharset());
        }
        this.data = data.getBytes(ct.getCharsetParameter());
        this.contentType = ct.toString();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (data == null) {
            throw new IOException("no data");
        }
        return new ByteArrayInputStream(data);
    }

    /**
     * Not implemented
     */
    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new IOException(this.getClass().getName() + ".getOutputStream() isn't implemented");
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getName() {
        return name;
    }

    protected static byte[] copyStream(final InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = null;
        try {
            baos = new UnsynchronizedByteArrayOutputStream(DEFAULT_BUF_SIZE << 1);
            final byte[] bbuf = new byte[DEFAULT_BUF_SIZE];
            int len;
            while ((len = inputStream.read(bbuf, 0, bbuf.length)) > 0) {
                baos.write(bbuf, 0, len);
            }
            byte[] byteArray = baos.toByteArray();
            return byteArray;
        } finally {
            Streams.close(inputStream);
            Streams.close(baos);
        }
    }
}
