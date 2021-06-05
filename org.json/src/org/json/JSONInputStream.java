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

package org.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64InputStream;
import org.json.helpers.UnsynchronizedByteArrayOutputStream;

/**
 * {@link JSONInputStream} - Directly converts a given {@link JSONValue} to a readable input stream.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class JSONInputStream extends InputStream {

    /** Pattern to detect unicode escape sequences for supplementary code points */
    static final Pattern PATTERN_SUPPLEMENTARY_CODE_POINTS = Pattern.compile("\\\\u([0-9a-fA-F]{5,})");

    private static final int BUFSIZE = 8192;

    private static interface Bufferer {

        /**
         * Gets the closing character
         *
         * @return The closing character
         */
        char getClosing();

        /**
         * Signals if there is another element to write
         *
         * @return <code>true</code> if further element available; otherwise <code>false</code>
         */
        boolean hasNext();

        /**
         * Gets the next element to write
         *
         * @return The next element
         * @throws IOException If next element cannot be returned
         */
        Object next() throws IOException;

        /**
         * Attempts to write more bytes.
         *
         * @return <code>true</code> if more bytes available to write; otherwise <code>false</code> if no more bytes are available
         * @throws IOException If an I/O error occurs
         */
        boolean writeMoreBytes() throws IOException;
    }

    private abstract class AbstractBufferer implements Bufferer {

        protected AbstractBufferer() {
            super();
        }

        @Override
        public boolean writeMoreBytes() throws IOException {
            out.reset();
            if (!hasNext()) {
                out.write(getClosing());
                count = 1;
                pos = 0;
                finished = true;
                nested = null;
                return false;
            }
            if (first) {
                first = false;
            } else {
                out.write(',');
            }
            writeValue(next());
            pos = 0;
            count = out.getCount();
            return true;
        }

        private void writeValue(final Object v) throws IOException {
            if (null == v || JSONObject.NULL.equals(v)) {
                out.write(toAsciiBytes("null"));
            } else if (v instanceof JSONValue) {
                nested = new JSONInputStream((JSONValue) v, charset);
            } else if (v instanceof JSONBinary) {
                InputStream binary = null;
                Base64InputStream encoder = null;
                try {
                    final JSONBinary jsonBinary = (JSONBinary) v;
                    binary = jsonBinary.getBinary();

                    out.write('"');
                    encoder = new Base64InputStream(binary, true, 0, null);
                    byte[] buf = new byte[8192];
                    for (int read; (read = encoder.read(buf, 0, 8192)) > 0;) {
                        out.write(buf, 0, read);
                    }
                    out.write('"');
                } finally {
                    closeInstances(encoder, binary);
                }
            } else if (v instanceof JSONString) {
                String s = ((JSONString) v).toJSONString();
                out.write('"');
                out.write(toAsciiBytes(toAscii(s)));
                out.write('"');
            } else if (v instanceof Reader) {
                Reader reader = new JSONStringEncoderReader((Reader) v);
                try {
                    out.write('"'); // String start

                    int buflen = 8192;
                    char[] cbuf = new char[buflen];
                    int off = 0;
                    for (int read; (read = reader.read(cbuf, off, buflen)) > 0;) {
                        char lastChar = cbuf[read - 1];
                        byte[] bytesToWrite;
                        if (Character.isHighSurrogate(lastChar)) {
                            bytesToWrite = toAsciiBytes(toAscii(new String(cbuf, 0, read - 1)));
                            off = 1;
                            cbuf[0] = lastChar;
                        } else {
                            bytesToWrite = toAsciiBytes(toAscii(new String(cbuf, 0, read)));
                            off = 0;
                        }
                        out.write(bytesToWrite);

                    }

                    out.write('"'); // String end
                } finally {
                    closeInstances(reader);
                }
            } else if (v instanceof Number) {
                try {
                    out.write(toAsciiBytes(JSONObject.numberToString((Number) v)));
                } catch (JSONException e) {
                    throw new IOException(e.getMessage(), e);
                }
            } else if (v instanceof Boolean) {
                out.write(toAsciiBytes(((Boolean) v).booleanValue() ? "true" : "false"));
            } else {
                // Write as String value
                out.write('"');
                out.write(toAsciiBytes(toAscii(v.toString())));
                out.write('"');
            }
        }

        /** Writes specified String's ASCII bytes */
        protected byte[] toAsciiBytes(final String str) {
            if (null == str) {
                return null;
            }
            final int length = str.length();
            if (0 == length) {
                return new byte[0];
            }
            final byte[] ret = new byte[length];
            str.getBytes(0, length, ret, 0);
            return ret;
        }

        /** Converts specified String to JSON's ASCII notation */
        protected String toAscii(final String str) {
            if (null == str) {
                return str;
            }

            if (0 == str.length()) {
                return str;
            }

            String s = replaceSupplementaryCodePoints(str);
            int length = s.length();
            StringBuilder sb = null;
            for (int i = 0; i < length; i++) {
                char c = s.charAt(i);
                if ((c > 127) || (c < 32)) {
                    if (sb == null) {
                        sb = new StringBuilder(length + (length >> 1));
                        if (i > 0) {
                            sb.append(s, 0, i);
                        }
                    }
                    if (Character.isSupplementaryCodePoint(c)) {
                        char[] chars = Character.toChars(c);
                        for (int j = 0; j < chars.length; j++) {
                            appendAsJsonUnicode(chars[j], sb);
                        }
                    } else {
                        appendAsJsonUnicode(c, sb);
                    }
                } else if ('\\' == c || '"' == c) {
                    if (sb == null) {
                        sb = new StringBuilder(length + (length >> 1));
                        if (i > 0) {
                            sb.append(s, 0, i);
                        }
                    }
                    sb.append('\\').append(c);
                } else {
                    if (sb != null) {
                        sb.append(c);
                    }
                }
            }
            return sb == null ? s : sb.toString();
        }

        private void appendAsJsonUnicode(final int ch, final StringBuilder sa) {
            sa.append("\\u");
            final String hex = Integer.toString(ch, 16);
            for (int i = hex.length(); i < 4; i++) {
                sa.append('0');
            }
            sa.append(hex);
        }

        private String replaceSupplementaryCodePoints(final String str) {
            if (str.indexOf("\\u") < 0) {
                return str;
            }

            Matcher m = PATTERN_SUPPLEMENTARY_CODE_POINTS.matcher(str);
            if (!m.find()) {
                return str;
            }

            StringBuffer sb = new StringBuffer(str.length());
            do {
                int codePoint = Integer.parseInt(m.group(1), 16);
                if (Character.isSupplementaryCodePoint(codePoint)) {
                    final char[] chars = Character.toChars(codePoint);
                    final StringBuilder tmp = new StringBuilder(32);
                    for (int j = 0; j < chars.length; j++) {
                        appendAsJsonUnicode(chars[j], tmp);
                    }
                    m.appendReplacement(sb, Matcher.quoteReplacement(tmp.toString()));
                }
            } while (m.find());
            m.appendTail(sb);
            return sb.toString();
        }

        /**
         * Closes given <code>java.io.Closeable</code> instance (if non-<code>null</code>).
         *
         * @param closeable The <code>java.io.Closeable</code> instance
         */
        protected void closeInstances(final java.io.Closeable... closeables) {
            if (null != closeables && closeables.length > 0) {
                for (java.io.Closeable closeable : closeables) {
                    if (null != closeable) {
                        try {
                            closeable.close();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                }
            }
        }

    }

    private final class ArrayBufferer extends AbstractBufferer {

        private final Iterator<Object> arrIterator;

        ArrayBufferer(Iterator<Object> arrIterator) {
            super();
            this.arrIterator = arrIterator;
        }

        @Override
        public char getClosing() {
            return ']';
        }

        @Override
        public boolean hasNext() {
            return arrIterator.hasNext();
        }

        @Override
        public Object next() {
            return arrIterator.next();
        }

    }

    private final class ObjectBufferer extends AbstractBufferer {

        private final Iterator<Entry<String, Object>> objIterator;

        ObjectBufferer(final Iterator<Entry<String, Object>> objIterator) {
            super();
            this.objIterator = objIterator;
        }

        @Override
        public char getClosing() {
            return '}';
        }

        @Override
        public boolean hasNext() {
            return objIterator.hasNext();
        }

        @Override
        public Object next() throws IOException {
            final Entry<String, Object> entry = objIterator.next();
            out.write('"');
            out.write(toAsciiBytes(toAscii(entry.getKey())));
            out.write('"');
            out.write(':');
            return entry.getValue();
        }

    }

    protected final String charset;
    private final Bufferer bufferer;
    protected int pos;
    protected int count;
    protected boolean finished;
    protected boolean first;
    protected final UnsynchronizedByteArrayOutputStream out;
    protected InputStream nested;

    /**
     * Initializes a new {@link JSONInputStream}.
     *
     * @param jsonValue The JSON value to read from
     * @param charset The charset
     */
    public JSONInputStream(final JSONValue jsonValue, final String charset) {
        super();
        first = true;
        finished = false;
        this.charset = charset;
        out = new UnsynchronizedByteArrayOutputStream(BUFSIZE);
        if (jsonValue.isArray()) {
            bufferer = new ArrayBufferer(jsonValue.toArray().iterator());
            out.write('[');
        } else {
            bufferer = new ObjectBufferer(jsonValue.toObject().entrySet().iterator());
            out.write('{');
        }
        count = 1;
        pos = 0;
    }

    private boolean hasBytes() {
        return (pos < count) || (nested != null);
    }

    private boolean writeMoreBytes() throws IOException {
        return bufferer.writeMoreBytes();
    }

    @Override
    public int read() throws IOException {
        if (finished) {
            return -1;
        }
        if (!hasBytes()) {
            // Write more bytes to buffer
            if (!writeMoreBytes()) {
                // Last byte written
                return out.getBuf()[pos++];
            }
        }
        if (pos < count) {
            return out.getBuf()[pos++];
        }
        final int read = nested.read();
        if (read >= 0) {
            return read;
        }
        // Reached end of nested stream
        nested = null;
        return read();
    }

}
