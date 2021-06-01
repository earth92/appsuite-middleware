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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Class for storing character sets.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class Charsets {

    /**
     * US-ASCII character set.
     */
    public static final Charset US_ASCII = Charset.forName("US-ASCII");

    /**
     * The name of "UTF-8" charset.
     */
    public static final String UTF_8_NAME = "UTF-8";

    /**
     * UTF-8 character set.
     */
    public static final Charset UTF_8 = Charset.forName(UTF_8_NAME);

    /**
     * ISO-8859-1 character set.
     */
    public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

    /**
     * The charset cache.
     */
    private static final ConcurrentMap<String, Charset> CACHE = new ConcurrentHashMap<String, Charset>();

    private static final Map<Integer, Charset> CODE_PAGES;

    static {
        Pattern patternCodePage = Pattern.compile("[^\\d]*(\\d+)");
        SortedMap<String, Charset> availableCharsets = Charset.availableCharsets();
        SortedMap<Integer, Charset> codePages = new TreeMap<>();
        for (Entry<String, Charset> entry : availableCharsets.entrySet()) {
            Charset charset = entry.getValue();

            // First, check display name
            Matcher matcher = patternCodePage.matcher(entry.getKey());
            if (matcher.matches()) {
                Integer numericCode = Integer.valueOf(matcher.group(1));
                if (false == codePages.containsKey(numericCode)) {
                    codePages.put(numericCode, charset);
                }
            } else {
                // Then, check aliases
                Set<String> aliases = charset.aliases();
                for (String alias : aliases) {
                    matcher = patternCodePage.matcher(alias);
                    if (matcher.matches()) {
                        Integer numericCode = Integer.valueOf(matcher.group(1));
                        if (false == codePages.containsKey(numericCode)) {
                            codePages.put(numericCode, charset);
                        }
                    }
                }
            }
        }
        CODE_PAGES = ImmutableMap.copyOf(codePages);
    }

    /**
     * Prevent instantiation
     */
    private Charsets() {
        super();
    }

    /**
     * Gets the ASCII string from specified <code>ByteArrayInputStream</code> instance.
     *
     * @param is The {@link ByteArrayInputStream}
     * @return The ASCII string
     */
    public static String toAsciiString(final ByteArrayInputStream is) {
        int size = is.available();
        if (size <= 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(size);
        for (int i; (i = is.read()) >= 0;) {
            sb.append((char) i);
        }
        return sb.toString();
    }

    /**
     * Gets the ASCII string from specified bytes.
     *
     * @param bytes The bytes
     * @return The ASCII string
     */
    public static String toAsciiString(final byte[] bytes) {
        int length = bytes.length;
        if (length <= 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) (bytes[i] & 0xff));
        }
        return sb.toString();
    }

    /**
     * Gets the ASCII string from specified bytes.
     *
     * @param bytes The bytes
     * @param off The start offset in the data.
     * @param len The number of bytes to write
     * @return The ASCII string
     */
    public static String toAsciiString(final byte[] bytes, final int off, final int len) {
        if ((off < 0) || (off > bytes.length) || (len < 0) || ((off + len) > bytes.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(bytes.length);
        for (int i = 0 ; i < len ; i++) {
            sb.append((char) (bytes[off + i] & 0xff));
        }
        return sb.toString();
    }

    /**
     * Gets specified string's ASCII bytes
     *
     * @param cs The string as {@link CharSequence}
     * @return The ASCII bytes
     */
    public static byte[] toAsciiBytes(final CharSequence cs) {
        return toAsciiBytes(cs.toString());
    }

    /**
     * Gets specified string's ASCII bytes
     *
     * @param str The string
     * @return The ASCII bytes
     */
    public static byte[] toAsciiBytes(final String str) {
        if (null == str) {
            return null;
        }
        return str.getBytes(Charsets.US_ASCII);
    }

    private static final int _64K = 65536;

    /**
     * Writes specified string's ASCII bytes to given stream.
     *
     * @param str The string
     * @param out The stream to write to
     * @throws IOException If an I/O error occurs
     */
    public static void writeAsciiBytes(final String str, final OutputStream out) throws IOException {
        if (null == str) {
            return;
        }
        final int length = str.length();
        if (0 == length) {
            return;
        }
        if (length <= _64K) {
            for (int i = 0; i < length; i++) {
                out.write((byte) str.charAt(i++));
            }
        } else {
            final byte[] ret = str.getBytes(Charsets.US_ASCII);
            out.write(ret, 0, length);
        }
    }

    private static final Set<String> SET_ASCII_NAMES = ImmutableSet.of("US-ASCII", "ASCII");

    /**
     * Checks if specified charset name denotes ASCII charset.
     *
     * @param charset The charset name to check
     * @return <code>true</code> if specified charset name denotes ASCII charset; otherwise <code>false</code>
     */
    public static boolean isAsciiCharset(final String charset) {
        if (null == charset) {
            return false;
        }
        return SET_ASCII_NAMES.contains(charset.toUpperCase());
    }

    /**
     * Gets the {@link Charset charset} object associated with specified code page.
     *
     * @param codePage The code page
     * @return The {@link Charset charset} object or <code>null</code>
     */
    public static Charset forCodePage(int codePage) {
        if (codePage <= 0) {
            return null;
        }

        return CODE_PAGES.get(Integer.valueOf(codePage));
    }

    /**
     * Gets a {@link Charset charset} object for the named charset.
     *
     * @param charsetName The name of the requested charset; may be either a canonical name or an alias
     * @return The {@link Charset charset} object for the named charset
     * @throws IllegalCharsetNameException If the given charset name is illegal
     * @throws UnsupportedCharsetException If no support for the named charset is available in this instance of the Java virtual machine
     */
    public static Charset forName(final String charsetName) {
        Charset cs = CACHE.get(charsetName);
        if (null == cs) {
            final Charset ncs = Charset.forName(charsetName);
            cs = CACHE.putIfAbsent(charsetName, ncs);
            if (null == cs) {
                cs = ncs;
            }
        }
        return cs;
    }

    /**
     * Constructs a new <tt>String</tt> by decoding the specified array of bytes using the specified charset. The length of the new
     * <tt>String</tt> is a function of the charset, and hence may not be equal to the length of the byte array.
     *
     * @param bytes The bytes to construct the <tt>String</tt> from
     * @param charset The charset
     * @return The new <tt>String</tt>
     */
    public static String toString(final byte[] bytes, final Charset charset) {
        return charset.decode(ByteBuffer.wrap(bytes)).toString();
    }

    /**
     * Encodes specified <tt>String</tt> into a sequence of bytes using the given charset, storing the result into a new byte array.
     *
     * @param source The string
     * @param charset The charset
     * @return The resulting bytes
     */
    public static byte[] getBytes(final String source, final Charset charset) {
        final ByteBuffer buf = charset.encode(CharBuffer.wrap(source));
        final byte[] retval = new byte[buf.limit()];
        buf.get(retval);
        return retval;
    }

}
