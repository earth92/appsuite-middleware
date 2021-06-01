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

package com.openexchange.file.storage.json;

import static com.openexchange.file.storage.File.Field.CATEGORIES;
import static com.openexchange.file.storage.File.Field.COLOR_LABEL;
import static com.openexchange.file.storage.File.Field.CREATED;
import static com.openexchange.file.storage.File.Field.CREATED_BY;
import static com.openexchange.file.storage.File.Field.CURRENT_VERSION;
import static com.openexchange.file.storage.File.Field.DESCRIPTION;
import static com.openexchange.file.storage.File.Field.FILENAME;
import static com.openexchange.file.storage.File.Field.FILE_MD5SUM;
import static com.openexchange.file.storage.File.Field.FILE_MIMETYPE;
import static com.openexchange.file.storage.File.Field.FILE_SIZE;
import static com.openexchange.file.storage.File.Field.FOLDER_ID;
import static com.openexchange.file.storage.File.Field.ID;
import static com.openexchange.file.storage.File.Field.LAST_MODIFIED;
import static com.openexchange.file.storage.File.Field.LAST_MODIFIED_UTC;
import static com.openexchange.file.storage.File.Field.LOCKED_UNTIL;
import static com.openexchange.file.storage.File.Field.MODIFIED_BY;
import static com.openexchange.file.storage.File.Field.NUMBER_OF_VERSIONS;
import static com.openexchange.file.storage.File.Field.TITLE;
import static com.openexchange.file.storage.File.Field.URL;
import static com.openexchange.file.storage.File.Field.VERSION;
import static com.openexchange.file.storage.File.Field.VERSION_COMMENT;
import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.json.JSONAssertion.assertValidates;
import static com.openexchange.time.TimeTools.D;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import com.openexchange.file.storage.DefaultFile;
import com.openexchange.file.storage.json.actions.files.TestFriendlyInfostoreRequest;
import com.openexchange.java.Autoboxing;
import com.openexchange.json.JSONAssertion;

/**
 * {@link FileWriterTest}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class FileWriterTest extends FileTest {

    FileMetadataWriter writer = new FileMetadataWriter(null);

    @Test
    public void testWriteFileAsArray() throws JSONException {
        DefaultFile f = createFile();
        //@formatter:off
        JSONArray array = writer.writeArray(new JsonFieldHandler(new TestFriendlyInfostoreRequest()), f, Arrays.asList(
            CATEGORIES,
            COLOR_LABEL,
            CREATED,
            CREATED_BY,
            DESCRIPTION,
            FILE_MD5SUM,
            FILE_MIMETYPE,
            FILENAME,
            FILE_SIZE,
            FOLDER_ID,
            ID,
            CURRENT_VERSION,
            LAST_MODIFIED,
            LAST_MODIFIED_UTC,
            LOCKED_UNTIL,
            MODIFIED_BY,
            NUMBER_OF_VERSIONS,
            TITLE,
            URL,
            VERSION,
            VERSION_COMMENT));
        //@formatter:on
        assertNotNull(array);

        assertValidates(new JSONAssertion().isArray().withValues("cat1", "cat2", "cat3").inStrictOrder(), array.getJSONArray(0));
        assertEquals(12, array.getInt(1));
        assertEquals(D("Today at 08:00").getTime(), array.getLong(2));
        assertEquals(3, array.getInt(3));
        assertEquals("description", array.getString(4));
        assertEquals("md5sum", array.getString(5));
        assertEquals("mime/type", array.getString(6));
        assertEquals("name.txt", array.getString(7));
        assertEquals(1337, array.getLong(8));
        assertEquals("folder 3", array.getString(9));
        assertEquals("Id 23", array.getString(10));
        assertEquals(Boolean.TRUE, Autoboxing.valueOf(array.getBoolean(11)));
        assertEquals(D("Today at 10:00").getTime(), array.getLong(12));
        assertEquals(D("Today at 10:00").getTime(), array.getLong(13));
        assertEquals(D("Today at 18:00").getTime(), array.getLong(14));
        assertEquals(22, array.getInt(15));
        assertEquals(2, array.getInt(16));
        assertEquals("Nice Title", array.getString(17));
        assertEquals("url", array.getString(18));
        assertEquals(2, array.getInt(19));
        assertEquals("version comment", array.getString(20));
    }

    private DefaultFile createFile() {
        DefaultFile f = new DefaultFile();
        f.setCategories("cat1, cat2, cat3");
        f.setColorLabel(12);
        f.setCreated(D("Today at 08:00"));
        f.setCreatedBy(3);
        f.setDescription("description");
        f.setFileMD5Sum("md5sum");
        f.setFileMIMEType("mime/type");
        f.setFileName("name.txt");
        f.setFileSize(1337);
        f.setFolderId("folder 3");
        f.setId("Id 23");
        f.setIsCurrentVersion(true);
        f.setLastModified(D("Today at 10:00"));
        f.setLockedUntil(D("Today at 18:00"));
        f.setModifiedBy(22);
        f.setNumberOfVersions(2);
        f.setTitle("Nice Title");
        f.setURL("url");
        f.setVersion("2");
        f.setVersionComment("version comment");
        return f;
    }

    @Test
    public void testTimezone() throws JSONException {
        DefaultFile f = new DefaultFile();
        f.setCreated(D("Today at 10:00"));
        f.setLastModified(D("Today at 12:00"));
        f.setLockedUntil(D("Today at 20:00"));

        JSONArray array = writer.writeArray(new JsonFieldHandler(new TestFriendlyInfostoreRequest("GMT-2")), f, Arrays.asList(CREATED, LAST_MODIFIED, LAST_MODIFIED_UTC, LOCKED_UNTIL));

        assertNotNull(array);

        assertEquals(D("Today at 08:00").getTime(), array.getLong(0));
        assertEquals(D("Today at 10:00").getTime(), array.getLong(1));
        assertEquals(D("Today at 12:00").getTime(), array.getLong(2)); // Last modified UTC doesn't get the timezone offset
        //assertEquals(D("Today at 18:00").getTime(), array.getLong(3));
    }

    @Test
    public void testWriteAsObject() {
        DefaultFile file = createFile();
        JSONObject object = writer.write(new TestFriendlyInfostoreRequest(), file);

        //@formatter:off
        assertValidates(new JSONAssertion().isObject()
            .hasKey("categories").withValueArray().withValues("cat1", "cat2", "cat3").inStrictOrder()
            .hasKey("color_label").withValue(I(12))
            .hasKey("creation_date").withValue(L(D("Today at 08:00").getTime()))
            .hasKey("created_by").withValue(I(3))
            .hasKey("description").withValue("description")
            .hasKey("file_md5sum").withValue("md5sum")
            .hasKey("file_mimetype").withValue("mime/type")
            .hasKey("file_size").withValue(I(1337))
            .hasKey("folder_id").withValue("folder 3")
            .hasKey("id").withValue("Id 23")
            .hasKey("current_version").withValue(B(true))
            .hasKey("last_modified").withValue(L(D("Today at 10:00").getTime()))
            .hasKey("locked_until").withValue(L(D("Today at 18:00").getTime()))
            .hasKey("modified_by").withValue(I(22))
            .hasKey("number_of_versions").withValue(I(2))
            .hasKey("title").withValue("Nice Title")
            .hasKey("url").withValue("url")
            .hasKey("version").withValue("2")
            .hasKey("version_comment").withValue("version comment"), object);
        //@formatter:on
    }
}
