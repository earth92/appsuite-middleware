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

package com.openexchange.java;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import org.apache.commons.io.FileUtils;

/**
 * {@link Streams} - A utility class for streams.
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class Streams {

    /**
     * No initialization.
     */
    private Streams() {
        super();
    }

    /** An input stream that just returns EOF. */
    public static final InputStream EMPTY_INPUT_STREAM = new InputStream() {

        @Override
        public int available() {
            return 0;
        }

        @Override
        public int read() {
            return -1;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            return -1;
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return -1;
        }
    };

    /** An output stream that just discards passed bytes. */
    public static final OutputStream EMPTY_OUTPUT_STREAM = new OutputStream() {

        @Override
        public void write(int b) throws IOException {
            // Nothing
        }

        @Override
        public void write(byte[] b) throws IOException {
            // Nothing
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            // Nothing
        }

        @Override
        public void flush() throws IOException {
            // Nothing
        }
    };

    /**
     * Consumes given input stream.
     *
     * @param in The input stream to consume
     * @return The number of bytes consumed
     * @throws IOException If reading from stream fails
     */
    public static long consume(InputStream in) throws IOException {
        if (null == in) {
            return 0L;
        }

        long consumed = 0L;
        int size = 65536;
        byte[] skipBuffer = new byte[size];
        for (int read; (read = in.read(skipBuffer, 0, size)) > 0;) {
            consumed += read;
        }

        return consumed;
    }

    /**
     * Checks if specified stream is empty.
     * <p>
     * If <code>null</code> is returned, the given stream is ensured to be closed.
     *
     * @param is The stream to check
     * @return The stream if not empty; otherwise <code>null</code> if empty
     * @throws IOException If an I/O error occurs
     */
    public static InputStream getNonEmpty(InputStream is) throws IOException {
        if (null == is) {
            return null;
        }

        // Try to read first byte
        PushbackInputStream pis = new PushbackInputStream(is);
        int check = pis.read();
        if (check < 0) {
            Streams.close(pis);
            return null;
        }

        // ... then push back to non-empty stream
        pis.unread(check);
        return pis;
    }

    /**
     * Returns a buffered {@link InputStream} for specified stream.
     *
     * @param in The stream
     * @return A new buffered input stream
     */
    public static InputStream bufferedInputStreamFor(final InputStream in) {
        if (null == in) {
            return in;
        }
        if ((in instanceof BufferedInputStream) || (in instanceof ByteArrayInputStream)) {
            return in;
        }
        return new BufferedInputStream(in, 65536);
    }

    /**
     * Returns a buffered {@link OutputStream} for specified stream.
     *
     * @param out The stream
     * @return A new buffered output stream
     */
    public static OutputStream bufferedOutputStreamFor(final OutputStream out) {
        if (null == out) {
            return out;
        }
        if ((out instanceof BufferedOutputStream) || (out instanceof ByteArrayOutputStream)) {
            return out;
        }
        return new BufferedOutputStream(out, 65536);
    }

    /**
     * Gets the specified stream's string representation using given character encoding.
     *
     * @param is The stream to read from
     * @param charset The character encoding
     * @return The string
     * @throws IOException If an I/O error occurs
     */
    public static String stream2string(InputStream is, String charset) throws IOException {
        if (null == is) {
            return null;
        }
        try {
            @SuppressWarnings("resource")
            UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream(4096);
            int buflen = 2048;
            byte[] buf = new byte[buflen];
            for (int read; (read = is.read(buf, 0, buflen)) > 0;) {
                bos.write(buf, 0, read);
            }
            return bos.toString(Charsets.forName(charset));
        } finally {
            close(is);
        }
    }

    /**
     * Reads the content from given reader.
     *
     * @param reader The reader
     * @return The reader's content
     * @throws IOException If an I/O error occurs
     */
    public static String reader2string(Reader reader) throws IOException {
        if (null == reader) {
            return null;
        }
        try {
            StringBuilder builder = new StringBuilder(8192);
            int buflen = 2048;
            char[] cbuf = new char[buflen];
            for (int read; (read = reader.read(cbuf, 0, buflen)) > 0;) {
                builder.append(cbuf, 0, read);
            }
            return 0 == builder.length() ? null : builder.toString();
        } finally {
            close(reader);
        }
    }

    /**
     * Creates an appropriate <tt>ByteArrayInputStream</tt> carrying given <tt>ByteArrayOutputStream</tt>'s valid bytes.
     * <p>
     * <b>Note</b>: The byte array from specified <tt>ByteArrayOutputStream</tt> is possibly shared to <tt>ByteArrayInputStream</tt>.
     *
     * @param baos The <tt>ByteArrayOutputStream</tt> instance
     * @return The associated <tt>ByteArrayInputStream</tt> instance
     */
    public static ByteArrayInputStream asInputStream(final ByteArrayOutputStream baos) {
        if (null == baos) {
            return null;
        }
        if (baos instanceof UnsynchronizedByteArrayOutputStream) {
            return ((UnsynchronizedByteArrayOutputStream) baos).toByteArrayInputStream();
        }
        return new UnsynchronizedByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Creates an appropriate <tt>ByteArrayInputStream</tt> carrying given <tt>InputStream</tt>'s bytes.
     *
     * @param in The <tt>InputStream</tt> instance
     * @return The associated <tt>ByteArrayInputStream</tt> instance
     * @throws IOException If an I/O error occurs
     */
    public static ByteArrayInputStream asInputStream(final InputStream in) throws IOException {
        if (null == in) {
            return null;
        }
        return newByteArrayInputStream(in);
    }

    /**
     * Writes specified input stream's content to a <code>ByteArrayOutputStream</code> array.
     *
     * @param is The input stream to read from
     * @return A newly created <code>byte</code> array carrying input stream's bytes.
     * @throws IOException If an I/O error occurs
     */
    public static ByteArrayOutputStream stream2ByteArrayOutputStream(final InputStream is) throws IOException {
        if (null == is) {
            return null;
        }
        try {
            final ByteArrayOutputStream bos = newByteArrayOutputStream(4096);
            final int buflen = 2048;
            final byte[] buf = new byte[buflen];
            for (int read; (read = is.read(buf, 0, buflen)) > 0;) {
                bos.write(buf, 0, read);
            }
            return bos;
        } finally {
            close(is);
        }
    }

    /**
     * Converts specified input stream to a <code>byte</code> array.
     *
     * @param is The input stream to read from
     * @return A newly created <code>byte</code> array carrying input stream's bytes.
     * @throws IOException If an I/O error occurs
     */
    public static byte[] stream2bytes(final InputStream is) throws IOException {
        if (null == is) {
            return new byte[0];
        }
        if (is instanceof UnsynchronizedByteArrayInputStream) {
            final UnsynchronizedByteArrayInputStream ubais = (UnsynchronizedByteArrayInputStream) is;
            final byte[] buf = ubais.getBuf();
            final int pos = ubais.getPosition();
            final int len = ubais.getCount() - pos;
            final byte[] newbuf = new byte[len];
            System.arraycopy(buf, pos, newbuf, 0, len);
            return newbuf;
        }
        // Check first byte
        int check = is.read();
        if (check < 0) {
            Streams.close(is);
            return new byte[0];
        }
        try {
            final ByteArrayOutputStream bos = newByteArrayOutputStream(4096);
            bos.write(check);
            final int buflen = 2048;
            final byte[] buf = new byte[buflen];
            for (int read; (read = is.read(buf, 0, buflen)) > 0;) {
                bos.write(buf, 0, read);
            }
            return bos.toByteArray();
        } finally {
            close(is);
        }
    }

    /**
     * Creates a new non-thread-safe {@link Reader} whose source is the specified string.
     *
     * @param s The string to read from
     * @return The reader
     */
    @SuppressWarnings("resource")
    public static Reader newStringReader(final String s) {
        return null == s ? null : new UnsynchronizedStringReader(s);
    }

    /**
     * Creates a new non-thread-safe {@link Writer} that collects its output in a string allocator, which can then be used to construct a
     * string.
     *
     * @return A new writer
     */
    public static Writer newStringWriter() {
        return new UnsynchronizedStringWriter(32);
    }

    /**
     * Creates a new non-thread-safe {@link Writer} that collects its output in a string allocator, which can then be used to construct a
     * string.
     *
     * @param initial The initial capacity
     * @return A new writer
     */
    public static Writer newStringWriter(final int initial) {
        return new UnsynchronizedStringWriter(initial);
    }

    /**
     * Creates a new non-thread-safe {@link ByteArrayOutputStream} instance with default initial capacity of <code>32</code>.
     *
     * @return A new non-thread-safe {@link ByteArrayOutputStream} instance
     */
    public static ByteArrayOutputStream newByteArrayOutputStream() {
        return new UnsynchronizedByteArrayOutputStream(32);
    }

    /**
     * Creates a new non-thread-safe {@link ByteArrayOutputStream} instance.
     *
     * @param capacity The initial capacity
     * @return A new non-thread-safe {@link ByteArrayOutputStream} instance
     */
    public static ByteArrayOutputStream newByteArrayOutputStream(final int capacity) {
        return new UnsynchronizedByteArrayOutputStream(capacity);
    }

    /**
     * Creates a new non-thread-safe {@link ByteArrayInputStream} instance carrying specified input stream's data.
     *
     * @param inputStream The input stream
     * @return A new non-thread-safe {@link ByteArrayInputStream} instance
     * @throws IOException If an I/O error occurs
     */
    public static ByteArrayInputStream newByteArrayInputStream(final InputStream inputStream) throws IOException {
        try {
            @SuppressWarnings("resource")
            final UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream(4096);
            final int buflen = 2048;
            final byte[] buf = new byte[buflen];
            for (int read; (read = inputStream.read(buf, 0, buflen)) > 0;) {
                bos.write(buf, 0, read);
            }
            return bos.toByteArrayInputStream();
        } finally {
            close(inputStream);
        }
    }

    /**
     * Creates a new non-thread-safe {@link ByteArrayInputStream} instance carrying specified bytes.
     *
     * @param bytes The bytes
     * @return A new non-thread-safe {@link ByteArrayInputStream} instance
     */
    public static ByteArrayInputStream newByteArrayInputStream(final byte[] bytes) {
        return new UnsynchronizedByteArrayInputStream(bytes);
    }

    /**
     * Creates a new non-thread-safe {@link ByteArrayInputStream} instance carrying specified bytes.
     *
     * @param bytes The bytes
     * @param offset The offset in the buffer of the first byte to read
     * @param length The maximum number of bytes to read from the buffer
     * @return A new non-thread-safe {@link ByteArrayInputStream} instance
     */
    public static ByteArrayInputStream newByteArrayInputStream(final byte[] bytes, final int offset, final int length) {
        return new UnsynchronizedByteArrayInputStream(bytes, offset, length);
    }

    /**
     * Counts the number of bytes readable from specified input stream.
     * <p>
     * The input stream will be closed orderly.
     *
     * @param in The input stream
     * @return The number of bytes
     * @throws IOException If an I/O error occurs
     */
    public static long countInputStream(final InputStream in) throws IOException {
        if (null == in) {
            return 0L;
        }
        try {
            final int blen = 65536;
            final byte[] buf = new byte[blen];
            long count = 0;
            for (int read; (read = in.read(buf, 0, blen)) > 0;) {
                count += read;
            }
            return count;
        } finally {
            close(in);
        }
    }

    /**
     * Safely closes specified {@link Closeable} instance.
     *
     * @param toClose The {@link Closeable} instance
     */
    public static void close(final Closeable toClose) {
        if (null != toClose) {
            try {
                toClose.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Safely closes specified {@link Closeable} instances.
     *
     * @param closeables The {@link Closeable} instances
     */
    public static void close(final Closeable... closeables) {
        if (null != closeables) {
            for (final Closeable toClose : closeables) {
                if (null != toClose) {
                    try {
                        toClose.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
    }

    /**
     * Safely closes specified {@link Closeable} instances.
     *
     * @param closeables The {@link Closeable} instances
     */
    public static void close(final Collection<? extends Closeable> closeables) {
        if (null != closeables) {
            for (final Closeable toClose : closeables) {
                if (null != toClose) {
                    try {
                        toClose.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
    }

    /**
     * Safely closes specified {@link Closeable} instance.
     *
     * @param toClose The {@link Closeable} instance
     */
    public static void close(final AutoCloseable toClose) {
        if (null != toClose) {
            try {
                toClose.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Safely closes specified {@link Closeable} instances.
     *
     * @param closeables The {@link Closeable} instances
     */
    public static void close(final AutoCloseable... closeables) {
        if (null != closeables) {
            for (final AutoCloseable toClose : closeables) {
                if (null != toClose) {
                    try {
                        toClose.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
    }

    /**
     * Safely closes specified {@link Closeable} instances.
     *
     * @param closeables The {@link Closeable} instances
     */
    public static void closeAutoCloseables(final Collection<? extends AutoCloseable> closeables) {
        if (null != closeables) {
            for (final AutoCloseable toClose : closeables) {
                if (null != toClose) {
                    try {
                        toClose.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
    }

    /**
     * Safely flushes specified {@link Flushable} instance.
     *
     * @param toFlush The {@link Flushable} instance
     */
    public static void flush(final Flushable toFlush) {
        if (null != toFlush) {
            try {
                toFlush.flush();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Creates a {@link java.io.PushbackInputStream} for given input stream.
     *
     * @param in The input stream to create a <code>PushbackInputStream</code> for
     * @return A <code>PushbackInputStream</code> for given input stream
     */
    public static PushbackInputStream pushbackInputStreamFor(final InputStream in) {
        return in instanceof PushbackInputStream ? (PushbackInputStream) in : new PushbackInputStream(in);
    }

    /**
     * Checks if specified stream only consists of ASCII-only bytes.
     *
     * @param in The stream to check
     * @return <code>true</code> if ASCII-only; otherwise <code>false</code>
     * @throws IOException If reading from stream yields an I/O error
     */
    public static boolean isAscii(InputStream in) throws IOException {
        if (null == in) {
            return true;
        }

        try {
            boolean isAscci = true;
            int buflen = 2048;
            byte[] buf = new byte[buflen];
            for (int read; isAscci && (read = in.read(buf, 0, buflen)) > 0;) {
                for (int i = read; isAscci && i-- > 0;) {
                    isAscci = (buf[i] >= 0);
                }
            }
            return isAscci;
        } finally {
            close(in);
        }
    }

    /**
     * Checks if specified bytes are ASCII-only.
     *
     * @param bytes The bytes to check
     * @return <code>true</code> if ASCII-only; otherwise <code>false</code>
     */
    public static boolean isAscii(byte[] bytes) {
        boolean isAscci = true;
        if (null != bytes) {
            for (int i = bytes.length; isAscci && (i-- > 0);) {
                isAscci = (bytes[i] >= 0);
            }
        }
        return isAscci;
    }

    private static final int BUFFER_SIZE = 65536;

    /**
     * Transfers content of given source input stream to specified (temporary) file and returns a {@code FileInputStream} reading from that
     * file.
     *
     * @param source The source input stream to transfer to file
     * @param tmpFile The file to transfer to and create the {@code FileInputStream} from
     * @return The {@code FileInputStream} reading from given file
     * @throws IOException If an I/O error occurs
     * @throws FileNotFoundException If given file does not exist
     */
    public static FileInputStream transferToFileAndCreateStream(InputStream source, File tmpFile) throws IOException, FileNotFoundException {
        try (InputStream in = source; OutputStream out = FileUtils.openOutputStream(tmpFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int n; (n = in.read(buffer)) > 0;) {
                out.write(buffer, 0, n);
            }
            out.flush();
        }
        return new FileInputStream(tmpFile);
    }

}
