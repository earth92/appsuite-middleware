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

package com.openexchange.imap.command;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.List;
import javax.mail.Message;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;

/**
 * {@link IMAPNumArgSplitter}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class IMAPNumArgSplitter {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IMAPNumArgSplitter.class);

    private static interface Tokenizer {

        public String getNext(int index);
    }

    /**
     * Prevent instantiation
     */
    private IMAPNumArgSplitter() {
        super();
    }

    /*-
     * Formerly 16384
     * Now 8000
     */

    /**
     * From <a href="http://www.faqs.org/rfcs/rfc2683.html">RFC 2683</a> section 3.2.1.5. (Long Command Lines):<br>
     *
     * <pre>
     * &quot;
     * ...
     * A client should limit the length of the command lines it generates to
     * approximately 1000 octets (including all quoted strings but not
     * including literals).  If the client is unable to group things into
     * ranges so that the command line is within that length, it should
     * split the request into multiple commands.  The client should use
     * literals instead of long quoted strings, in order to keep the command
     * length down.
     *
     * For its part, a server should allow for a command line of at least
     * 8000 octets.  This provides plenty of leeway for accepting reasonable
     * length commands from clients.  The server should send a BAD response
     * to a command that does not end within the server's maximum accepted
     * command length.&quot;
     * </pre>
     */
    private static final int MAX_IMAP_COMMAND_LENGTH = 8000;

    /**
     * {@link #MAX_IMAP_COMMAND_LENGTH} - 512 (Default space for other command arguments)
     */
    private static final int MAX_IMAP_COMMAND_LENGTH_WITH_DEFAULT_CONSUMED = MAX_IMAP_COMMAND_LENGTH - 512;

    /**
     * Since an IMAP command MUST NOT exceed the maximum command length of the IMAP server, which is 8000 bytes, this method creates an
     * appropriate array of command arguments which can then be used with an instance of <code>{@link AbstractIMAPCommand}</code>
     *
     * @param arr - <code>int</code> array of message sequence numbers
     * @return an appropriate array of command arguments
     */
    public static String[] split(int[] arr) {
        return split(new Tokenizer() {

            @Override
            public String getNext(int index) {
                return String.valueOf(arr[index]);
            }
        }, arr.length);
    }

    /**
     * Since an IMAP command MUST NOT exceed the maximum command length of the IMAP server, which is 8000 bytes, this method creates an
     * appropriate array of command arguments which can then be used with an instance of <code>{@link AbstractIMAPCommand}</code>
     *
     * @param arr - <code>long</code> array of message UIDs
     * @return an appropriate array of command arguments
     */
    public static String[] split(long[] arr) {
        return split(new Tokenizer() {

            @Override
            public String getNext(int index) {
                return Long.toString(arr[index]);
            }
        }, arr.length);
    }

    /**
     * Since an IMAP command MUST NOT exceed the maximum command length of the IMAP server, which is 8000 bytes, this method creates an
     * appropriate array of command arguments which can then be used with an instance of <code>{@link AbstractIMAPCommand}</code>
     *
     * @param arr - <code>Message</code> array
     * @return an appropriate array of command arguments
     */
    public static String[] split(Message[] arr) {
        return split(new Tokenizer() {

            @Override
            public String getNext(int index) {
                return String.valueOf(arr[index].getMessageNumber());
            }
        }, arr.length);
    }

    /**
     * Given array of sequence numbers is first transformed into a valid IMAP command's number argument and then split into max. IMAP
     * command length pieces
     *
     * @param arr - the array of sequence numbers
     * @param keepOrder - whether the values' ordering in array parameter <code>arr</code> shall be kept or not; if ordering does not care a
     *            more compact number argument for IMAP command is going to be created by grouping sequential numbers e.g.
     *            <code>1,2,3,4,5 -> 1:5</code>
     * @param consumed The number of bytes already consumed or <code>-1</code> for default (512)
     * @return an appropriate array of command arguments
     */
    public static String[] splitSeqNumArg(int[] arr, boolean keepOrder, int consumed) {
        return getSeqNumArg(arr, keepOrder, true, consumed);
    }

    /**
     * Given array of sequence numbers is first transformed into a valid IMAP command's number argument and then split into max. IMAP
     * command length pieces if desired.
     *
     * @param arr - the array of sequence numbers
     * @param keepOrder - whether the values' ordering in array parameter <code>arr</code> shall be kept or not; if ordering does not care a
     *            more compact number argument for IMAP command is going to be created by grouping sequential numbers e.g.
     *            <code>1,2,3,4,5 -> 1:5</code>
     * @param split Whether to split number argument according to max. allowed IMAP command length
     * @param consumed The number of bytes already consumed or <code>-1</code> for default (512)
     * @return an appropriate array of command arguments
     */
    public static String[] getSeqNumArg(int[] arr, boolean keepOrder, boolean split, int consumed) {
        final TIntList l = new TIntArrayList(arr.length);
        for (int i = 0; i < arr.length; i++) {
            final int seqNum = arr[i];
            if (seqNum > 0) {
                l.add(seqNum);
            }
        }
        if (l.isEmpty()) {
            return new String[0];
        }
        if (!keepOrder) {
            l.sort();
        }
        if (!split) {
            return new String[] { getNumArg(l) };
        }
        return split(getNumArg(l), (-1 == consumed ? MAX_IMAP_COMMAND_LENGTH_WITH_DEFAULT_CONSUMED : MAX_IMAP_COMMAND_LENGTH - consumed));
    }

    /**
     * Given array of sequence numbers is first transformed into a valid IMAP command's number argument and then split into max. IMAP
     * command length pieces
     *
     * @param arr - the array of sequence numbers
     * @param keepOrder - whether the values' ordering in array parameter <code>arr</code> shall be kept or not; if ordering does not care a
     *            more compact number argument for IMAP command is going to be created by grouping sequential numbers e.g.
     *            <code>1,2,3,4,5 -> 1:5</code>
     * @param consumed The number of bytes already consumed or <code>-1</code> for default (512)
     * @return an appropriate array of command arguments
     */
    public static String[] splitMessageArg(Message[] arr, boolean keepOrder, int consumed) {
        final TIntList l = new TIntArrayList(arr.length);
        for (int i = 0; i < arr.length; i++) {
            final int messageNumber = arr[i].getMessageNumber();
            if (messageNumber > 0) {
                l.add(messageNumber);
            }
        }
        if (l.isEmpty()) {
            return new String[0];
        }
        if (!keepOrder) {
            l.sort();
        }
        return split(getNumArg(l), (-1 == consumed ? MAX_IMAP_COMMAND_LENGTH_WITH_DEFAULT_CONSUMED : MAX_IMAP_COMMAND_LENGTH - consumed));
    }

    /**
     * Given array of sequence numbers is first transformed into a valid IMAP command's number argument and then split into max. IMAP
     * command length pieces
     *
     * @param arr - the array of sequence numbers
     * @param keepOrder - whether the values' ordering in array parameter <code>arr</code> shall be kept or not; if ordering does not care a
     *            more compact number argument for IMAP command is going to be created by grouping sequential numbers e.g.
     *            <code>1,2,3,4,5 -> 1:5</code>
     * @param consumed The number of bytes already consumed or <code>-1</code> for default (512)
     * @return an appropriate array of command arguments
     */
    public static String[] splitUIDArg(long[] arr, boolean keepOrder, int consumed) {
        final TLongArrayList l = new TLongArrayList(arr.length);
        for (int i = 0; i < arr.length; i++) {
            final long uid = arr[i];
            if (uid >= 0) {
                l.add(uid);
            }
        }
        if (l.isEmpty()) {
            return new String[0];
        }
        if (!keepOrder) {
            l.sort();
        }
        return split(getNumArg(l), (-1 == consumed ? MAX_IMAP_COMMAND_LENGTH_WITH_DEFAULT_CONSUMED : MAX_IMAP_COMMAND_LENGTH - consumed));
    }

    /**
     * Generates a number argument valid for IMAP commands expecting message's sequence numbers or UIDs. That is contiguous numbers may be
     * abbreviated as a sequence representation e.g. <code>5:24</code> meaning all numbers beginning from 5 ending with 24. Non-contiguous
     * numbers must be delimited using a comma.
     * <p>
     * <b>NOTE:</b> This routine does not take care if the resulting argument in addition to rest of IMAP command exceeds the max. length of
     * 8000 bytes
     * <p>
     * A resulting string can look like this: <code>10031:10523,10525:11020,11022:11027,11030:11047,11050:11051,11053,11055:11558</code>
     *
     * @param numbers The list of numbers; either sequence numbers or UIDs
     * @return The number argument or an empty string if specified numbers are empty
     */
    public static String getNumArg(TIntList numbers) {
        int size = numbers.size();
        if (0 == size) {
            return "";
        }

        int prev = numbers.get(0);
        boolean contiguous = false;
        StringBuilder sb = new StringBuilder(size << 2);
        sb.append(prev);
        for (int i = 1; i < size; i++) {
            int current = numbers.get(i);
            if (prev + 1 == current) {
                contiguous = true;
            } else if (contiguous) {
                sb.append(':').append(prev);
                sb.append(',');
                sb.append(current);
                contiguous = false;
            } else {
                sb.append(',');
                sb.append(current);
            }
            prev = current;
        }
        if (contiguous) {
            sb.append(':').append(prev);
        }
        return sb.toString();
    }

    /**
     * Generates a number argument valid for IMAP commands expecting message's sequence numbers or UIDs. That is contiguous numbers may be
     * abbreviated as a sequence representation e.g. <code>5:24</code> meaning all numbers beginning from 5 ending with 24. Non-contiguous
     * numbers must be delimited using a comma.
     * <p>
     * <b>NOTE:</b> This routine does not take care if the resulting argument in addition to rest of IMAP command exceeds the max. length of
     * 8000 bytes
     * <p>
     * A resulting string can look like this: <code>10031:10523,10525:11020,11022:11027,11030:11047,11050:11051,11053,11055:11558</code>
     *
     * @param numbers The list of numbers; either sequence numbers or UIDs
     * @return The number argument or an empty string if specified numbers are empty
     */
    public static String getNumArg(TLongArrayList numbers) {
        final int size = numbers.size();
        if (0 == size) {
            return "";
        }
        long prev = numbers.getQuick(0);
        boolean contiguous = false;
        final StringBuilder sb = new StringBuilder(size << 2);
        sb.append(prev);
        for (int i = 1; i < size; i++) {
            final long current = numbers.getQuick(i);
            if (prev + 1 == current) {
                prev++;
                contiguous = true;
            } else if (contiguous) {
                sb.append(':').append(prev);
                sb.append(',');
                sb.append(current);
                prev = current;
                contiguous = false;
            } else {
                sb.append(',');
                sb.append(current);
                prev = current;
            }
        }
        if (contiguous) {
            sb.append(':').append(prev);
        }
        return sb.toString();
    }

    private static String[] split(Tokenizer tokenizer, int length) {
        final int initCap = (length / MAX_IMAP_COMMAND_LENGTH_WITH_DEFAULT_CONSUMED);
        final List<String> tmp = new ArrayList<String>(initCap == 0 ? 10 : initCap);
        final StringBuilder sb = new StringBuilder(MAX_IMAP_COMMAND_LENGTH_WITH_DEFAULT_CONSUMED);
        sb.append(tokenizer.getNext(0));
        for (int i = 1; i < length; i++) {
            final String sUid = tokenizer.getNext(i);
            if (sb.length() + sUid.length() + 1 > MAX_IMAP_COMMAND_LENGTH_WITH_DEFAULT_CONSUMED) {
                tmp.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(',');
            }
            sb.append(sUid);
        }
        tmp.add(sb.toString());
        return tmp.toArray(new String[tmp.size()]);
    }

    private static String[] split(String numArg, int maxLen) {
        final int len = numArg.length();
        if (len <= maxLen) {
            if (len == 0) {
                return new String[0];
            }
            return new String[] { numArg };
        }
        /*
         * Split into maxLen chunks
         */
        final List<String> tmp;
        {
            final int initCap = (len / maxLen);
            tmp = new ArrayList<String>(initCap == 0 ? 2 : initCap);
        }
        int offset = 0;
        while (offset < len) {
            int endPos = offset + maxLen;
            if (endPos < len) {
                for (char c = numArg.charAt(endPos); (c != ',') && (endPos >= 0);) {
                    c = numArg.charAt(--endPos);
                }
            } else {
                endPos = len;
            }
            if (endPos <= offset) {
                final int p = numArg.indexOf(',', offset);
                LOG.warn("Token does not fit into given max size of {} bytes: {}", I(maxLen), numArg.substring(offset, p));
                offset = p + 1;
            } else {
                tmp.add(numArg.substring(offset, endPos));
                offset = endPos + 1;
            }
        }
        return tmp.toArray(new String[tmp.size()]);
    }

}
