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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.Writer;
import org.json.helpers.ExceptionAwarePipedInputStream;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;

/**
 * {@link AbstractJSONValue} - The abstract {@link JSONValue} providing some general-purpose methods.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
abstract class AbstractJSONValue implements JSONValue {

    private static final long serialVersionUID = -1594307735237035381L;

    /**
     * 2K buffer
     */
    private static final int BUF_SIZE = 0x800;

    /**
     * Initialize with 8K
     */
    private static final int SB_SIZE = 0x2000;

    /**
     * Feature that specifies that all characters beyond 7-bit ASCII range (i.e. code points of 128 and above) need to be output using
     * format-specific escapes (for JSON, backslash escapes), if format uses escaping mechanisms (which is generally true for textual
     * formats but not for binary formats).
     * <p>
     * Feature is disabled by default.
     */
    private static final Feature ESCAPE_NON_ASCII = JsonGenerator.Feature.ESCAPE_NON_ASCII;

    /**
     * Reads the content from given reader.
     *
     * @param reader The reader
     * @param maxRead The max. number of characters to read
     * @return The reader's content
     * @throws IOException If an I/O error occurs
     */
    protected static String readFrom(final Reader reader, final long maxRead) throws IOException {
        if (null == reader) {
            return null;
        }
        final int buflen = BUF_SIZE;
        final char[] cbuf = new char[buflen];
        final StringBuilder sa = new StringBuilder(SB_SIZE);
        long count = 0;
        for (int read = reader.read(cbuf, 0, buflen); read > 0; read = reader.read(cbuf, 0, buflen)) {
            if (maxRead > 0) {
                count += read;
                if (count >= maxRead) {
                    break;
                }
            }
            sa.append(cbuf, 0, read);
        }
        if (0 == sa.length()) {
            return null;
        }
        return sa.toString();
    }

    /**
     * Acquires next token from given {@link JsonParser} ignoring possible <code>"Unexpected character"</code> exception.
     *
     * @param jParser The JSON parser
     * @return The next token with possible <code>"Unexpected character"</code> exception(s) ignored
     * @throws IOException If an I/O error occurs
     */
    protected static JsonToken nextTokenSafe(final JsonParser jParser) throws IOException {
        JsonToken token = null;
        while (null == token) {
            try {
                token = jParser.nextToken();
            } catch (JsonParseException e) {
                if (!e.getMessage().startsWith("Unexpected character")) {
                    throw e;
                }
                token = null;
            }
        }
        return token;
    }

    /**
     * Creates a new JSON parser.
     *
     * @param reader The reader to read from
     * @return The new parser reading from given stream
     * @throws IOException If a JSON error occurs
     * @throws JsonParseException If a parsing error occurs
     */
    protected static JsonParser createParser(final Reader reader) throws IOException, JsonParseException {
        final JsonParser jParser = JSON_FACTORY.createParser(reader);
        jParser.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
        jParser.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        jParser.enable(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER);
        jParser.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
        jParser.enable(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS);
        return jParser;
    }

    /**
     * Creates a new JSON generator.
     *
     * @param writer The writer to write to
     * @return The created generator
     * @throws IOException If an I/O error occurs
     */
    protected static JsonGenerator createGenerator(final Writer writer, final boolean asciiOnly) throws IOException {
        final JsonGenerator jGenerator = JSON_FACTORY.createGenerator(writer);
        jGenerator.disable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);
        jGenerator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        if (asciiOnly) {
            jGenerator.enable(ESCAPE_NON_ASCII);
        }
        return jGenerator;
    }

    /**
     * Writes end character and flushes generator.
     *
     * @param jGenerator The generator to write to and to flush
     * @param isJsonObject Whether generating a JSON object or a JSON array
     */
    protected static void writeEndAndFlush(final JsonGenerator jGenerator, final boolean isJsonObject) {
        if (null != jGenerator) {
            try {
                if (isJsonObject) {
                    jGenerator.writeEndObject(); // }
                } else {
                    jGenerator.writeEndArray(); // ]
                }
            } catch (Exception e) {
                // Ignore
            }
            try {
                jGenerator.flush();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Generates String directly from given character array.
     *
     * @param off The offset
     * @param len The length
     * @param chars The character array
     * @return The resulting String
     */
    protected static String directString(final int off, final int len, final char[] chars) {
        try {
            return new String(chars, off, len);
        } catch (Exception e) {
            return new String(chars, off, len);
        }
    }

    /**
     * The JSON factory.
     */
    protected static final JsonFactory JSON_FACTORY = new JsonFactory();

    /**
     * The minimal pretty-printer.
     */
    protected static final MinimalPrettyPrinter STANDARD_MINIMAL_PRETTY_PRINTER = new MinimalPrettyPrinter();

    /**
     * The default pretty-printer.
     */
    protected static final DefaultPrettyPrinter STANDARD_DEFAULT_PRETTY_PRINTER = new DefaultPrettyPrinter();

    /**
     * Initializes a new {@link AbstractJSONValue}.
     */
    protected AbstractJSONValue() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo(File file) throws JSONException {
        if (null == file) {
            return;
        }
        Writer writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            write(writer);
            writer.flush();
        } catch (IOException e) {
            throw new JSONException(e);
        } finally {
            close(writer);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prettyPrintTo(File file) throws JSONException {
        if (null == file) {
            return;
        }

        Writer writer = null;
        JsonGenerator jGenerator = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            jGenerator = createGenerator(writer, false);
            jGenerator.setPrettyPrinter(STANDARD_DEFAULT_PRETTY_PRINTER);
            write(this, jGenerator);
            writer.flush();
        } catch (IOException e) {
            throw new JSONException(e);
        } finally {
            close(jGenerator);
            close(writer);
        }
    }

    @Override
    public InputStream getStream(final boolean asciiOnly) throws JSONException {
        try {
            final PipedOutputStream pos = new PipedOutputStream();
            final ExceptionAwarePipedInputStream pin = new ExceptionAwarePipedInputStream(pos, 65536);

            final Runnable r = new Runnable() {

                @Override
                public void run() {
                    OutputStreamWriter writer = null;
                    try {
                        writer = new OutputStreamWriter(pos, "UTF-8");
                        write(writer, asciiOnly);
                        writer.flush();
                    } catch (Exception e) {
                        pin.setException(e);
                    } finally {
                        close(writer, pos);
                    }
                }
            };

            new Thread(r, "getStreamFromJSON").start();

            return pin;
        } catch (IOException e) {
            throw new JSONException(e);
        }
    }

    /**
     * Closes given <code>java.io.Closeable</code> instance (if non-<code>null</code>).
     *
     * @param closeable The <code>java.io.Closeable</code> instance
     */
    protected static void close(java.io.Closeable... closeables) {
        if (null != closeables) {
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

    /**
     * Writes to given generator.
     *
     * @param asciiOnly Whether to write only ASCII characters
     * @param jGenerator The generator
     * @throws IOException If an I/O error occurs
     * @throws JSONException IOf a JSON error occurs
     */
    protected abstract void writeTo(JsonGenerator jGenerator) throws IOException, JSONException;

    /**
     * Writes specified object to given generator.
     *
     * @param v The object
     * @param asciiOnly Whether to allow only ASCII characters
     * @param jGenerator The generator
     * @throws IOException If an I/O error occurs
     * @throws JSONException IOf a JSON error occurs
     */
    protected static void write(final Object v, final JsonGenerator jGenerator) throws IOException, JSONException {
        if (null == v || JSONObject.NULL.equals(v)) {
            jGenerator.writeNull();
        } else if (v instanceof AbstractJSONValue) {
            ((AbstractJSONValue) v).writeTo(jGenerator);
        } else if (v instanceof JSONBinary) {
            InputStream binary = null;
            try {
                final JSONBinary jsonBinary = (JSONBinary) v;
                binary = jsonBinary.getBinary();
                jGenerator.writeBinary(binary, (int) jsonBinary.length());
            } catch (Exception e) {
                throw new JSONException(e);
            } finally {
                close(binary);
            }
        } else if (v instanceof JSONString) {
            try {
                final String s = ((JSONString) v).toJSONString();
                jGenerator.writeString(s);
            } catch (Exception e) {
                throw new JSONException(e);
            }
        } else if (v instanceof Reader) {
            Reader reader = new JSONStringEncoderReader((Reader) v);
            try {
                jGenerator.writeRawValue("\""); // String start

                int buflen = 8192;
                char[] cbuf = new char[buflen];
                for (int read; (read = reader.read(cbuf, 0, buflen)) > 0;) {
                    jGenerator.writeRaw(cbuf, 0, read);
                }

                jGenerator.writeRaw('"'); // String end
            } catch (Exception e) {
                throw new JSONException(e);
            } finally {
                close(reader);
            }
        } else if (v instanceof Number) {
            jGenerator.writeNumber(JSONObject.numberToString((Number) v));
        } else if (v instanceof Boolean) {
            jGenerator.writeBoolean(((Boolean) v).booleanValue());
        } else {
            // Write as String value
            if (jGenerator.isEnabled(ESCAPE_NON_ASCII)) {
                jGenerator.writeString(v.toString());
            } else {
                final String str = v.toString();
                if (escapeNonAscii(str)) {
                    // Escape non-ascii characters
                    final int prev = jGenerator.getHighestEscapedChar();
                    // Set to ASCII only
                    jGenerator.setHighestNonEscapedChar(127);
                    jGenerator.writeString(str);
                    // Restore
                    jGenerator.setHighestNonEscapedChar(prev);
                } else {
                    jGenerator.writeString(str);
                }
            }
        }
    }

    /**
     * Indicates whether to escape non-ASCII characters.
     *
     * @param str The string to check
     * @return <code>true</code> to escape them; otherwise <code>false</code>
     */
    protected static boolean escapeNonAscii(final String str) {
        if (null == str) {
            return false;
        }
        return str.indexOf('\u2028') >= 0 || str.indexOf('\u2029') >= 0;
    }

    /**
     * Checks if passed value is either <code>null</code> or <code>JSONObject.NULL</code>.
     *
     * @param value The value
     * @return <code>true</code> if value is either <code>null</code> or <code>JSONObject.NULL</code>; otherwise <code>false</code>
     */
    protected static boolean isNull(final Object value) {
        return (value == null || value == JSONObject.NULL);
    }

}
