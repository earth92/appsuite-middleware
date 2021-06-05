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

package com.openexchange.preview.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import com.google.common.collect.ImmutableSet;
import com.openexchange.filemanagement.ManagedFile;
import com.openexchange.filemanagement.ManagedFileManagement;

/**
 * {@link TikaImageExtractingParser}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class TikaImageExtractingParser implements Parser {

    private static final long serialVersionUID = -8054020195071839180L;

    private static final Set<MediaType> TYPES_IMAGE = ImmutableSet.of(MediaType.image("bmp"), MediaType.image("gif"), MediaType.image("jpg"), MediaType.image("jpeg"), MediaType.image("png"), MediaType.image("tiff"), MediaType.image("heic"), MediaType.image("heif"));
    private static final Set<MediaType> TYPES_EXCEL = ImmutableSet.of(MediaType.image("vnd.ms-excel"));
    private static final Set<MediaType> TYPES = ImmutableSet.<MediaType> builder().addAll(TYPES_IMAGE).addAll(TYPES_EXCEL).build();

    private final TikaDocumentHandler documentHandler;
    @SuppressWarnings("unused")
    private final TikaConfig config;
    @SuppressWarnings("unused")
    private final ManagedFileManagement fileManagement;

    public TikaImageExtractingParser(final TikaDocumentHandler documentHandler) {
        super();
        this.documentHandler = documentHandler;
        config = TikaConfig.getDefaultConfig();
        fileManagement = documentHandler.serviceLookup.getService(ManagedFileManagement.class);
    }

    @Override
    public Set<MediaType> getSupportedTypes(final ParseContext context) {
        return TYPES;
    }

    @Override
    public void parse(final InputStream stream, final ContentHandler handler, final Metadata metadata, final ParseContext context) throws IOException, SAXException, TikaException {
        if (handledImage(stream, metadata)) {
            return;
        }
        if (handledExcel(metadata)) {
            return;
        }
    }

    private boolean handledExcel(final Metadata metadata) {
        final String type = metadata.get(HttpHeaders.CONTENT_TYPE);
        if (type == null) {
            return false;
        }
        for (final MediaType mt : TYPES_EXCEL) {
            if (mt.toString().equals(type)) {
                //handleImage(stream, fileName, type);
                return true;
            }
        }
        return false;
    }

    private boolean handledImage(final InputStream stream, final Metadata metadata) throws IOException {
        /*
         * Is it a supported image?
         */
        final String fileName = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
        final String type = metadata.get(HttpHeaders.CONTENT_TYPE);
        if (type != null) {
            for (final MediaType mt : TYPES_IMAGE) {
                if (mt.toString().equals(type)) {
                    handleImage(stream, fileName, type);
                    return true;
                }
            }
        }
        if (fileName != null) {
            for (final MediaType mt : TYPES_IMAGE) {
                final String ext = "." + mt.getSubtype();
                if (fileName.endsWith(ext)) {
                    handleImage(stream, fileName, type);
                    return true;
                }
            }
        }
        return false;
    }

    public void parse(final InputStream stream, final ContentHandler handler, final Metadata metadata) throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

    private void handleImage(final InputStream stream, final String fileName, final String type) throws IOException {
        final InputStream in = stream;
        try {
            final ManagedFile managedFile = documentHandler.extractedFiles.get(fileName);
            managedFile.setContentType(type);
            managedFile.setFileName(fileName);
            final File outputFile = managedFile.getFile();
            final FileOutputStream os = new FileOutputStream(outputFile);
            try {
                IOUtils.copy(in, os);
            } finally {
                IOUtils.closeQuietly(os);
            }
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

}
