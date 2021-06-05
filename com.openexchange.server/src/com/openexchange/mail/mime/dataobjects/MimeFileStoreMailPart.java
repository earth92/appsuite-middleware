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

package com.openexchange.mail.mime.dataobjects;

import static com.openexchange.java.CharsetDetector.detectCharset;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.MessageRemovedException;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.DefaultFile;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.composition.IDBasedFileAccess;
import com.openexchange.file.storage.composition.IDBasedFileAccessFactory;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.mail.mime.MimeTypes;
import com.openexchange.mail.mime.datasource.MessageDataSource;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.stream.UnsynchronizedByteArrayInputStream;
import com.openexchange.tx.TransactionAwares;

/**
 * {@link MimeFileStoreMailPart} - A {@link MailPart} implementation that keeps a reference to a temporary uploaded file that shall be added
 * as an attachment later
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public abstract class MimeFileStoreMailPart extends MailPart {

    private static final long serialVersionUID = 257902073011243269L;

    private static final transient org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(MimeFileStoreMailPart.class);

    private final IDBasedFileAccessFactory fileAccessFactory;

    private final int userId;

    private final int contextId;

    private final String id;

    private transient MessageDataSource dataSource;

    private transient Object cachedContent;

    /**
     * Initializes a new {@link MimeFileStoreMailPart}
     *
     * @param fileDataSource The file data source
     * @throws OXException If upload file's content type cannot be parsed
     */
    protected MimeFileStoreMailPart(DataSource dataSource, long size, Session session) throws OXException {
        super();
        try {
            final File file = new DefaultFile();
            file.setId(FileStorageFileAccess.NEW);
            file.setFolderId(String.valueOf(new OXFolderAccess(getContextFrom(session)).getDefaultFolderID(
                session.getUserId(),
                FolderObject.INFOSTORE)));
            final String name = dataSource.getName();
            setContentType(prepareContentType(dataSource.getContentType(), name));

            if (getContentType().getCharsetParameter() == null && getContentType().startsWith(TEXT)) {
                /*
                 * Guess charset for textual attachment
                 */
                final String cs = detectCharset(dataSource.getInputStream());
                getContentType().setCharsetParameter(cs);
            }

            file.setFileName(name);
            file.setFileMIMEType(getContentType().toString());
            file.setTitle(name);
            file.setFileSize(size);
            /*
             * Put attachment's document to dedicated infostore folder
             */
            fileAccessFactory = ServerServiceRegistry.getInstance().getService(IDBasedFileAccessFactory.class, true);
            IDBasedFileAccess fileAccess = fileAccessFactory.createAccess(session);
            boolean retry = true;
            int count = 1;
            final StringBuilder hlp = new StringBuilder(16);
            while (retry) {
                /*
                 * Get attachment's input stream
                 */
                final InputStream in = dataSource.getInputStream();
                try {
                    /*
                     * Start InfoStore transaction
                     */
                    fileAccess.startTransaction();
                    try {
                        fileAccess.saveDocument(file, in, FileStorageFileAccess.DISTANT_FUTURE);
                        fileAccess.commit();
                        retry = false;
                    } catch (OXException x) {
                        fileAccess.rollback();
                        if (!x.isPrefix("IFO")) {
                            throw x;
                        }
                        if (441 != x.getCode()) {
                            throw x;
                        }
                        /*
                         * Duplicate document name, thus retry with a new name
                         */
                        hlp.setLength(0);
                        final int pos = name == null ? -1 : name.lastIndexOf('.');
                        final String newName;
                        if (pos >= 0) {
                            newName =
                                hlp.append(name.substring(0, pos)).append("_(").append(++count).append(')').append(name.substring(pos)).toString();
                        } else {
                            newName = hlp.append(name).append("_(").append(++count).append(')').toString();
                        }
                        file.setFileName(newName);
                        file.setTitle(newName);
                    } catch (RuntimeException e) {
                        fileAccess.rollback();
                        throw MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
                    } finally {
                        fileAccess.finish();
                    }
                } finally {
                    Streams.close(in);
                }
            }
            id = String.valueOf(file.getId());
            userId = session.getUserId();
            contextId = session.getContextId();
        } catch (IOException e) {
            if ("com.sun.mail.util.MessageRemovedIOException".equals(e.getClass().getName()) || (e.getCause() instanceof MessageRemovedException)) {
                throw MailExceptionCode.MAIL_NOT_FOUND_SIMPLE.create(e);
            }
            throw MailExceptionCode.IO_ERROR.create(e, e.getMessage());
        }
    }

    private static Context getContextFrom(Session session) throws OXException {
        if (session instanceof ServerSession) {
            return ((ServerSession) session).getContext();
        }
        return ContextStorage.getStorageContext(session.getContextId());
    }

    private Session getSession() throws OXException {
        final Collection<Session> sessions =
            ServerServiceRegistry.getInstance().getService(SessiondService.class, true).getSessions(userId, contextId);
        if (null == sessions || sessions.isEmpty()) {
            throw MailExceptionCode.MISSING_FIELD.create("session");
        }
        return sessions.iterator().next();
    }

    private static String prepareContentType(String contentType, String preparedFileName) {
        if (null == contentType || contentType.length() == 0) {
            return MimeTypes.MIME_APPL_OCTET;
        }
        final String retval;
        {
            final int mlen = contentType.length() - 1;
            if (0 == contentType.indexOf('"') && mlen == contentType.lastIndexOf('"')) {
                retval = contentType.substring(1, mlen);
            } else {
                retval = contentType;
            }
        }
        if ("multipart/form-data".equalsIgnoreCase(retval)) {
            return MimeType2ExtMap.getContentType(preparedFileName);

        }
        return contentType;
    }

    private static final String TEXT = "text/";

    private MessageDataSource getDataSource() throws OXException {
        /*
         * Lazy creation
         */
        if (null == dataSource) {
            IDBasedFileAccess fileAccess = null;
            try {
                ContentType contentType = getContentType();
                fileAccess = fileAccessFactory.createAccess(getSession());
                MessageDataSource mds = new MessageDataSource(fileAccess.getDocument(id, FileStorageFileAccess.CURRENT_VERSION), contentType);
                if (contentType.getCharsetParameter() == null && contentType.startsWith(TEXT)) {
                    /*
                     * Guess charset for textual attachment
                     */
                    final String cs = detectCharset(new UnsynchronizedByteArrayInputStream(mds.getData()));
                    contentType.setCharsetParameter(cs);
                    mds.setContentType(contentType.toString());
                }
                dataSource = mds;
            } catch (IOException e) {
                LOG.error("", e);
                dataSource = new MessageDataSource(new byte[0], MimeTypes.MIME_APPL_OCTET);
            } finally {
                TransactionAwares.finishSafe(fileAccess);
            }
        }
        return dataSource;
    }

    /**
     * Gets the identifier of the file associated with this mail part
     *
     * @return The identifier of the file associated with this mail part
     */
    public String getFileId() {
        return id;
    }

    @Override
    public Object getContent() throws OXException {
        if (cachedContent != null) {
            return cachedContent;
        }
        if (getContentType().isMimeType(MimeTypes.MIME_TEXT_ALL)) {
            final MessageDataSource mds = getDataSource();
            String charset = getContentType().getCharsetParameter();
            if (charset == null) {
                charset = "UTF-8";
            }
            try {
                cachedContent = new String(mds.getData(), Charsets.forName(charset));
            } catch (UnsupportedCharsetException e) {
                throw MailExceptionCode.ENCODING_ERROR.create(e, e.getMessage());
            }
            return cachedContent;
        }
        return null;
    }

    @Override
    public DataHandler getDataHandler() throws OXException {
        return new DataHandler(getDataSource());
    }

    @Override
    public int getEnclosedCount() throws OXException {
        return NO_ENCLOSED_PARTS;
    }

    @Override
    public MailPart getEnclosedMailPart(int index) throws OXException {
        return null;
    }

    @Override
    public InputStream getInputStream() throws OXException {
        try {
            return getDataSource().getInputStream();
        } catch (IOException e) {
            if ("com.sun.mail.util.MessageRemovedIOException".equals(e.getClass().getName()) || (e.getCause() instanceof MessageRemovedException)) {
                throw MailExceptionCode.MAIL_NOT_FOUND_SIMPLE.create(e);
            }
            throw MailExceptionCode.IO_ERROR.create(e, e.getMessage());
        }
    }

    @Override
    public void loadContent() {
        // Nothing to do
    }

    @Override
    public void prepareForCaching() {
        // Nothing to do
    }

}
