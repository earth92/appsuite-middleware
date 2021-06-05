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

package com.openexchange.ajax.container;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;
import javax.mail.util.SharedFileInputStream;
import com.openexchange.ajax.fileholder.ByteArrayRandomAccess;
import com.openexchange.ajax.fileholder.FileRandomAccess;
import com.openexchange.ajax.fileholder.IFileHolder;
import com.openexchange.ajax.fileholder.InputStreamReadable;
import com.openexchange.ajax.fileholder.Readable;
import com.openexchange.configuration.ServerConfig;
import com.openexchange.exception.OXException;
import com.openexchange.java.SizeKnowingInputStream;
import com.openexchange.java.Streams;
import com.openexchange.java.UnsynchronizedByteArrayOutputStream;
import com.openexchange.log.LogProperties;
import com.openexchange.tools.servlet.AjaxExceptionCodes;

/**
 * {@link ThresholdFileHolder} - A {@link IFileHolder} that backs data in a <code>byte</code> array as long as specified threshold is not
 * exceeded, but streams data to a temporary file otherwise.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ThresholdFileHolder implements IFileHolder {

    /** The default in-memory threshold of 500 KB. */
    public static final int DEFAULT_IN_MEMORY_THRESHOLD = 500 * 1024; // 500KB

    /** The in-memory buffer where data is stored */
    private ByteArrayOutputStream buf;

    /** The number of valid bytes that were already written */
    private long count;

    /** The temporary file to stream to if threshold exceeded */
    private File tempFile;

    /** The file name */
    private String name;

    /** The <code>Content-Type</code> value */
    private String contentType;

    /** The disposition */
    private String disposition;

    /** The delivery model */
    private String delivery;

    /** The in-memory threshold */
    private final int threshold;

    /** The initial capacity */
    private final int initalCapacity;

    /** The list for post-processing tasks */
    private final List<Runnable> tasks;

    /** <code>true</code> to signal automatic management for the created file (deleted after processing threads terminates); otherwise <code>false</code> to let the caller control file's life-cycle */
    private final boolean autoManaged;

    /** The directory in which the file is to be created when exceeding in-memory threshold */
    private final File tempFileDirectory;

    /**
     * Initializes a new {@link ThresholdFileHolder} with default threshold (500 KB) and default initial capacity (64 KB).
     */
    public ThresholdFileHolder() {
        this(-1, -1);
    }

    /**
     * Initializes a new {@link ThresholdFileHolder} with default threshold (500 KB) and default initial capacity (64 KB).
     *
     * @param autoManaged <code>true</code> to signal automatic management for the created file (deleted after processing threads terminates); otherwise <code>false</code> to let the caller control file's life-cycle
     */
    public ThresholdFileHolder(boolean autoManaged) {
        this(-1, -1, autoManaged);
    }

    /**
     * Initializes a new {@link ThresholdFileHolder} with default initial capacity (64 KB).
     *
     * @param threshold The threshold
     */
    public ThresholdFileHolder(int threshold) {
        this(threshold, -1);
    }

    /**
     * Initializes a new {@link ThresholdFileHolder}.
     *
     * @param threshold The threshold
     * @param initalCapacity The initial capacity
     */
    public ThresholdFileHolder(int threshold, int initalCapacity) {
        this(threshold, initalCapacity, true);
    }

    /**
     * Initializes a new {@link ThresholdFileHolder}.
     *
     * @param threshold The threshold
     * @param initalCapacity The initial capacity
     * @param autoManaged <code>true</code> to signal automatic management for the created file (deleted after processing threads terminates); otherwise <code>false</code> to let the caller control file's life-cycle
     */
    public ThresholdFileHolder(int threshold, int initalCapacity, boolean autoManaged) {
        this(threshold, initalCapacity, autoManaged, null);
    }

    /**
     * Initializes a new {@link ThresholdFileHolder}.
     *
     * @param threshold The threshold
     * @param initalCapacity The initial capacity
     * @param autoManaged <code>true</code> to signal automatic management for the created file (deleted after processing threads terminates); otherwise <code>false</code> to let the caller control file's life-cycle
     * @param tempFileDirectory The directory in which the file is to be created when exceeding in-memory threshold
     */
    public ThresholdFileHolder(int threshold, int initalCapacity, boolean autoManaged, File tempFileDirectory) {
        super();
        this.autoManaged = autoManaged;
        count = 0;
        this.threshold = threshold > 0 ? threshold : DEFAULT_IN_MEMORY_THRESHOLD;
        contentType = "application/octet-stream";
        this.initalCapacity = initalCapacity > 0 ? initalCapacity : 65536;
        tasks = new LinkedList<Runnable>();
        this.tempFileDirectory = tempFileDirectory == null ? ServerConfig.getTmpDir() : tempFileDirectory;
    }

    /**
     * Creates a copy from given {@link IFileHolder}.
     *
     * @param source The source file holder
     * @throws OXException If an error occurs
     */
    public ThresholdFileHolder(IFileHolder source) throws OXException {
        this();
        write(source.getStream());
        name = source.getName();
        contentType = source.getContentType();
        delivery = source.getDelivery();
        disposition = source.getDisposition();
    }

    /**
     * Resets this file holder.
     * <p>
     * Deletes associated file (if set) and resets internal buffer.
     */
    public void reset() {
        count = 0;
        final File tempFile = this.tempFile;
        if (null != tempFile) {
            tempFile.delete();
            this.tempFile = null;
        }
        final ByteArrayOutputStream  baos = this.buf;
        if (null != baos) {
            baos.reset();
        }
    }

    /**
     * Creates a new temporary file.
     *
     * @return The newly created file
     * @throws OXException If temporary file cannot be created
     */
    private File newTempFile() throws OXException {
        return TmpFileFileHolder.newTempFile(null, autoManaged, tempFileDirectory);
    }

    /**
     * Handles specified I/O error.
     *
     * @param e The I/O error to handle
     * @return The appropriate exception result
     */
    private static OXException handleIOException(IOException e) {
        String message = e.getMessage();
        if (message != null && message.indexOf("not enough space on the disk") >= 0) {
            return AjaxExceptionCodes.DISK_FULL.create(e, new Object[0]);
        }
        return AjaxExceptionCodes.IO_ERROR.create(e, message);
    }

    /**
     * Lets this file holder be auto-managed by calling thread.
     *
     * @return <code>true</code> if this file holder could be made auto-managed by calling thread; otherwise <code>false</code>
     */
    public boolean automanaged() {
        if (autoManaged) {
            return false;
        }

        File tempFile = this.tempFile;
        if (null == tempFile) {
            return false;
        }

        LogProperties.addTempFile(tempFile);
        return true;
    }

    /**
     * Gets the internal buffer.
     *
     * @return The buffer
     * @see #isInMemory()
     */
    public ByteArrayOutputStream getBuffer() {
        return buf;
    }

    /**
     * Checks if file holder's content is completely held in memory.
     *
     * @return <code>true</code> if in memory; otherwise <code>false</code>
     */
    public boolean isInMemory() {
        return (null == tempFile) && (buf != null);
    }

    /**
     * Gets the optional temporary file.
     * <p>
     * If {@link #isInMemory()} signals <code>true</code>, then this method will return <code>null</code>, and the content should rather be obtained by {@link #getBuffer()}.
     *
     * @return The temporary file or <code>null</code>
     * @see #isInMemory()
     */
    public File getTempFile() {
        return tempFile;
    }

    /**
     * Gets the {@link OutputStream} view on this file holder.
     *
     * @return An {@link OutputStream} that writes data into this file holder
     */
    public OutputStream asOutputStream() {
        return new TransferringOutStream(this);
    }

    /**
     * Writes the specified content to this file holder.
     *
     * @param bytes The content to be written.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @return This file holder with content written
     * @throws OXException If write attempt fails
     * @throws IndexOutOfBoundsException If illegal arguments are specified
     */
    public ThresholdFileHolder write(final byte[] bytes, final int off, final int len) throws OXException {
        if (bytes == null) {
            return this;
        }
        if ((off < 0) || (off > bytes.length) || (len < 0) || ((off + len) > bytes.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return this;
        }
        return write(new com.openexchange.java.UnsynchronizedByteArrayInputStream(bytes, off, len));
    }

    /**
     * Writes the specified content to this file holder.
     *
     * @param bytes The content to be written.
     * @return This file holder with content written
     * @throws OXException If write attempt fails
     */
    public ThresholdFileHolder write(final byte[] bytes) throws OXException {
        if (bytes == null) {
            return this;
        }
        if (null == tempFile && null == buf && bytes.length > threshold) {
            // Nothing written & content does exceed threshold
            final File tempFile = newTempFile();
            this.tempFile = tempFile;
            OutputStream out = null;
            try {
                out = new FileOutputStream(tempFile);
                out.write(bytes, 0, bytes.length);
                out.flush();
            } catch (IOException e) {
                throw handleIOException(e);
            } finally {
                Streams.close(out);
            }
            return this;
        }
        // Deal with possible available content
        return write(Streams.newByteArrayInputStream(bytes));
    }

    /**
     * Writes the specified content to this file holder.
     * <p>
     * Orderly closes specified {@link InputStream} instance.
     *
     * @param in The content to be written.
     * @return This file holder with content written
     * @throws OXException If write attempt fails
     */
    public ThresholdFileHolder write(final InputStream in) throws OXException {
        if (null == in) {
            return this;
        }
        if (in instanceof ThresholdFileHolderInputStream) {
            ThresholdFileHolder fileHolder = ((ThresholdFileHolderInputStream) in).getFileHolder();
            copy(fileHolder, this, true);
            return this;
        }
        return write(new InputStreamReadable(in));
    }

    /**
     * Writes the specified content to this file holder.
     * <p>
     * Orderly closes specified {@link InputStream} instance.
     *
     * @param in The content to be written.
     * @return This file holder with content written
     * @throws OXException If write attempt fails
     */
    public ThresholdFileHolder write(final Readable in) throws OXException {
        if (null == in) {
            return this;
        }
        OutputStream out = null;
        try {
            File tempFile = this.tempFile;
            long count = this.count;
            if (null == tempFile) {
                // Threshold not yet exceeded
                ByteArrayOutputStream baos = buf;
                if (null == baos) {
                    baos = Streams.newByteArrayOutputStream(initalCapacity);
                    this.buf = baos;
                }
                out = baos;
                final int inMemoryThreshold = threshold;
                final int buflen = 0xFFFF; // 64KB
                final byte[] buffer = new byte[buflen];
                for (int len; (len = in.read(buffer, 0, buflen)) > 0;) {
                    // Count bytes
                    count += len;
                    if ((null == tempFile) && (count > inMemoryThreshold) && baos != null) {
                        // Stream to file because threshold is exceeded
                        tempFile = newTempFile();
                        this.tempFile = tempFile;
                        out = new FileOutputStream(tempFile);
                        baos.writeTo(out);
                        baos = null;
                        buf = null;
                    }
                    out.write(buffer, 0, len);
                }
                out.flush();
            } else {
                // Threshold already exceeded. Stream to file.
                out = new FileOutputStream(tempFile, true);
                final int buflen = 0xFFFF; // 64KB
                final byte[] buffer = new byte[buflen];
                for (int len; (len = in.read(buffer, 0, buflen)) > 0;) {
                    // Count bytes
                    count += len;
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            this.count = count;
        } catch (IOException e) {
            throw handleIOException(e);
        } catch (RuntimeException e) {
            throw AjaxExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            Streams.close(in);
            Streams.close(out);
        }
        return this;
    }

    /**
     * Writes zero bytes to this file holder; just for initialization purpose.
     *
     * @return This file holder
     */
    public ThresholdFileHolder writeZeroBytes() {
        if (null == tempFile && null == buf) {
            buf = Streams.newByteArrayOutputStream(initalCapacity);
        }
        return this;
    }

    /**
     * Gets the MD5 sum for this file holder's content
     *
     * @return The MD5 sum
     * @throws OXException If MD5 sum cannot be returned
     */
    public String getMD5() throws OXException {
        File tempFile = this.tempFile;
        if (null != tempFile) {
            DigestInputStream digestStream = null;
            try {
                digestStream = new DigestInputStream(new FileInputStream(tempFile), MessageDigest.getInstance("MD5"));
                byte[] buf = new byte[8192];
                while (digestStream.read(buf, 0, 8192) > 0) {
                    // Nothing
                }
                byte[] digest = digestStream.getMessageDigest().digest();
                return jonelo.jacksum.util.Service.format(digest);
            } catch (NoSuchAlgorithmException e) {
                throw AjaxExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
            } catch (IOException e) {
                throw handleIOException(e);
            } finally {
                Streams.close(digestStream);
            }
        }

        // In memory...
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(Streams.stream2bytes(getStream()));
            return jonelo.jacksum.util.Service.format(digest);
        } catch (NoSuchAlgorithmException e) {
            throw AjaxExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        } catch (IOException e) {
            throw handleIOException(e);
        }
    }

    /**
     * Gets the number of valid bytes written to this file holder.
     *
     * @return The number of bytes
     */
    public long getCount() {
        return count;
    }

    @Override
    public boolean repetitive() {
        return true;
    }

    @Override
    public void close() {
        final File tempFile = this.tempFile;
        if (null != tempFile) {
            tempFile.delete();
            this.tempFile = null;
        }
        this.buf = null;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        try {
            close();
        } catch (@SuppressWarnings("unused") Exception ignore) {
            // Ignore
        }
    }

    /**
     * Writes the complete contents of this file holder to the specified output stream argument, as if by calling the output stream's write
     * method using <code>out.write(buf, 0, count)</code>.
     *
     * @param out the output stream to which to write the data.
     * @throws OXException If an I/O error occurs.
     */
    public void writeTo(OutputStream out) throws OXException {
        if (count <= 0) {
            return;
        }
        final ByteArrayOutputStream buf = this.buf;
        if (null == buf) {
            final File tempFile = this.tempFile;
            if (null == tempFile) {
                final IOException e = new IOException("Already closed.");
                throw handleIOException(e);
            }
            InputStream in = null;
            try {
                in = new FileInputStream(tempFile);
                final int buflen = 0xFFFF; // 64KB
                final byte[] buffer = new byte[buflen];
                for (int len; (len = in.read(buffer, 0, buflen)) > 0;) {
                    out.write(buffer, 0, len);
                }
            } catch (IOException e) {
                throw handleIOException(e);
            } finally {
                Streams.close(in);
            }
        } else {
            try {
                buf.writeTo(out);
            } catch (IOException e) {
                throw handleIOException(e);
            }
        }
    }

    /**
     * Gets this file holder content as a byte array.
     *
     * @return The byte array
     * @throws OXException If byte array cannot be returned for any reason
     */
    public byte[] toByteArray() throws OXException {
        if (count <= 0) {
            return new byte[0];
        }
        final ByteArrayOutputStream buf = this.buf;
        if (null != buf) {
            return buf.toByteArray();
        }
        final File tempFile = this.tempFile;
        if (null == tempFile) {
            final IOException e = new IOException("Already closed.");
            throw handleIOException(e);
        }
        InputStream in = null;
        try {
            in = new FileInputStream(tempFile);
            final ByteArrayOutputStream baos = Streams.newByteArrayOutputStream(in.available());
            final int buflen = 0xFFFF; // 64KB
            final byte[] buffer = new byte[buflen];
            for (int len; (len = in.read(buffer, 0, buflen)) > 0;) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw handleIOException(e);
        } finally {
            Streams.close(in);
        }
    }

    /**
     * Creates a copy of this file holder.
     *
     * @return A copy
     * @throws OXException If returning a copy fails
     */
    public ThresholdFileHolder copy() throws OXException {
        return copy(this, new ThresholdFileHolder(autoManaged), false);
    }

    /**
     * Creates a copy from source file holder.
     *
     * @param source The source file holder to copy from
     * @param copy The file holder to copy to
     * @param onlyData <code>true</code> if only source's data is supposed to be copied; otherwise <code>false</code> to include meta-data as well
     * @return The copy
     * @throws OXException If returning a copy fails
     */
    private static ThresholdFileHolder copy(ThresholdFileHolder source, ThresholdFileHolder copy, boolean onlyData) throws OXException {
        copy.count = source.count;
        if (false == onlyData) {
            copy.contentType = source.contentType;
            copy.delivery = source.delivery;
            copy.disposition = source.disposition;
            copy.name = source.name;
        }

        // Check if content is available
        if (source.count <= 0) {
            // No content to make a copy of
            return copy;
        }

        // Check internal buffer vs temp. file
        final ByteArrayOutputStream buf = source.buf;
        if (null != buf) {
            copy.buf = new UnsynchronizedByteArrayOutputStream(buf);
        } else if (null != source.tempFile) {
            try {
                final File newTempFile = TmpFileFileHolder.newTempFile(source.autoManaged);
                copyFile(source.tempFile, newTempFile);
                copy.tempFile = newTempFile;
            } catch (IOException e) {
                throw handleIOException(e);
            }
        }

        return copy;
    }

    /**
     * Gets the input stream for this file holder's content.
     * <p>
     * Closing the stream will also {@link #close() close} this file holder.
     *
     * @return The input stream
     * @throws OXException If input stream cannot be returned
     */
    public InputStream getClosingStream() throws OXException {
        return new ClosingInputStream(this);
    }

    @Override
    public InputStream getStream() throws OXException {
        return new ThresholdFileHolderInputStream(this);
    }

    /**
     * Gets the effective input stream from this file holder; either array- or file-backed.
     *
     * @param sizeKnowing <code>true</code> to return a size-knowing input stream; otherwise <code>false</code>
     * @return The effective input stream
     * @throws OXException If input stream cannot be returned due to an I/O error
     */
    InputStream getInnerStream(boolean sizeKnowing) throws OXException {
        if (count <= 0) {
            return Streams.EMPTY_INPUT_STREAM;
        }
        ByteArrayOutputStream buf = this.buf;
        if (null != buf) {
            return sizeKnowing ? new SizeKnowingInputStream(Streams.asInputStream(buf), buf.size()) : Streams.asInputStream(buf);
        }
        File tempFile = this.tempFile;
        if (null == tempFile) {
            IOException e = new IOException("Already closed.");
            throw handleIOException(e);
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(tempFile);
            InputStream retval = sizeKnowing ? new SizeKnowingInputStream(fis, tempFile.length()) : fis;
            fis = null; // Avoid premature closing
            return retval;
        } catch (IOException e) {
            throw handleIOException(e);
        } finally {
            Streams.close(fis);
        }
    }

    /**
     * Gets the random access for this file holder's content.
     * <p>
     * Closing the random access will also {@link #close() close} this file holder.
     *
     * @return The random access
     * @throws OXException If random access cannot be returned
     */
    public RandomAccess getClosingRandomAccess() throws OXException {
        return new ClosingRandomAccess(this);
    }

    @Override
    public RandomAccess getRandomAccess() throws OXException {
        if (count <= 0) {
            return new ByteArrayRandomAccess(new byte[0]);
        }

        ByteArrayOutputStream buf = this.buf;
        if (null != buf) {
            return new ByteArrayRandomAccess(buf.toByteArray());
        }

        File tempFile = this.tempFile;
        if (null != tempFile) {
            try {
                return new FileRandomAccess(tempFile);
            } catch (FileNotFoundException e) {
                throw AjaxExceptionCodes.IO_ERROR.create(e, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Gets this instance's content as a {@link SharedInputStream} appropriate to create MIME resources from it
     *
     * @return The shared input stream
     * @throws IOException If an I/O error occurs
     */
    public SharedInputStream getSharedStream() throws IOException {
        if (count <= 0) {
            return new SharedByteArrayInputStream(new byte[0]);
        }
        ByteArrayOutputStream buf = this.buf;
        if (null != buf) {
            return new SharedByteArrayInputStream(buf.toByteArray());
        }
        File tempFile = this.tempFile;
        if (null == tempFile) {
            throw new IOException("Already closed.");
        }
        return new SharedFileInputStream(tempFile);
    }

    @Override
    public long getLength() {
        if (count <= 0) {
            return 0;
        }
        final ByteArrayOutputStream buf = this.buf;
        if (null != buf) {
            return buf.size();
        }
        final File tempFile = this.tempFile;
        if (null == tempFile) {
            throw new IllegalStateException("Already closed.");
        }
        return tempFile.length();
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisposition() {
        return disposition;
    }

    @Override
    public String getDelivery() {
        return delivery;
    }

    /**
     * Sets the content information retrieved from passed file holder.
     * <ul>
     * <li>MIME type</li>
     * <li>Disposition</li>
     * <li>Name</li>
     * <li>Delivery</li>
     * </ul>
     *
     * @param fileHolder The file holder to get the content information from
     * @return This file holder instance with content information applied
     */
    public ThresholdFileHolder setContentInfo(IFileHolder fileHolder) {
        if (null != fileHolder) {
            setContentType(fileHolder.getContentType());
            setDelivery(fileHolder.getDelivery());
            setDisposition(fileHolder.getDisposition());
            setName(fileHolder.getName());
        }
        return this;
    }

    @Override
    public List<Runnable> getPostProcessingTasks() {
        return tasks;
    }

    @Override
    public void addPostProcessingTask(Runnable task) {
        if (null != task) {
            tasks.add(task);
        }
    }

    /**
     * Sets the disposition.
     *
     * @param disposition The disposition
     */
    public void setDisposition(final String disposition) {
        this.disposition = disposition;
    }

    /**
     * Sets the content type; e.g. "application/octet-stream"
     *
     * @param contentType The content type
     */
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    /**
     * Sets the (file) name.
     *
     * @param name The name
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Sets the delivery
     *
     * @param delivery The delivery to set
     */
    public void setDelivery(final String delivery) {
        this.delivery = delivery;
    }

    private static final class TransferringOutStream extends OutputStream {

        private final ThresholdFileHolder fileHolder;

        TransferringOutStream(final ThresholdFileHolder fileHolder) {
            super();
            this.fileHolder = fileHolder;
        }

        @Override
        public void write(final int b) throws IOException {
            try {
                fileHolder.write(new byte[] { (byte) b });
            } catch (OXException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw new IOException(e);
            }
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            try {
                fileHolder.write(b, off, len);
            } catch (OXException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw new IOException(e);
            }
        }
    } // End of class TransferringOutStream

    private static final class ClosingInputStream extends ThresholdFileHolderInputStream {

        /**
         * Initializes a new {@link ClosingInputStream}.
         */
        protected ClosingInputStream(final ThresholdFileHolder fileHolder) throws OXException {
            super(fileHolder);
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                fileHolder.close();
            }
        }
    }

    /**
     * The input stream backed by <code>ThresholdFileHolder</code> instance.
     */
    public static class ThresholdFileHolderInputStream extends SizeKnowingInputStream {

        /** The <code>ThresholdFileHolder</code> instance */
        protected final ThresholdFileHolder fileHolder;

        /**
         * Initializes a new {@link ClosingInputStream}.
         */
        ThresholdFileHolderInputStream(final ThresholdFileHolder fileHolder) throws OXException {
            super(fileHolder.getInnerStream(false), fileHolder.getLength());
            this.fileHolder = fileHolder;
        }

        /**
         * Gets the file holder
         *
         * @return The file holder
         */
        public ThresholdFileHolder getFileHolder() {
            return fileHolder;
        }
    }

    private static final class ClosingRandomAccess implements RandomAccess {

        private final ThresholdFileHolder fileHolder;
        private final RandomAccess randomAccess;

        /**
         * Initializes a new {@link ClosingInputStream}.
         */
        protected ClosingRandomAccess(ThresholdFileHolder fileHolder) throws OXException {
            super();
            this.fileHolder = fileHolder;
            this.randomAccess = fileHolder.getRandomAccess();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return randomAccess.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return randomAccess.read(b, off, len);
        }

        @Override
        public void seek(long pos) throws IOException {
            randomAccess.seek(pos);
        }

        @Override
        public long length() throws IOException {
            return randomAccess.length();
        }

        @Override
        public void close() throws IOException {
            try {
                randomAccess.close();
            } finally {
                fileHolder.close();
            }
        }
    }

    private static void copyFile(final File in, final File out) throws IOException {
        FileInputStream inStream = null;
        FileOutputStream outStream  = null;
        try {
            inStream = new FileInputStream(in);
            outStream = new FileOutputStream(out);

            int buflen = 0xFFFF;
            byte[] buf = new byte[buflen];
            for (int read; (read = inStream.read(buf, 0, buflen)) > 0;) {
                outStream.write(buf, 0, read);
            }
            outStream.flush();
        } finally {
            Streams.close(inStream, outStream);
        }
    }

    private static final char[] hexadecimal = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    /**
     * Encodes the 128 bit (16 bytes) MD5 into a 32 character String.
     *
     * @param binaryData The digest
     * @return The encoded MD5, or <code>null</code> if encoding failed
     */
    public static String md5Encode(byte[] binaryData) {
        if (null == binaryData || binaryData.length != 16) {
            return null;
        }

        char[] buffer = new char[32];

        for (int i = 0; i < 16; i++) {
            int low = binaryData[i] & 0x0f;
            int high = (binaryData[i] & 0xf0) >> 4;
            buffer[i << 1] = hexadecimal[high];
            buffer[(i << 1) + 1] = hexadecimal[low];
        }

        return new String(buffer);

    }

}
