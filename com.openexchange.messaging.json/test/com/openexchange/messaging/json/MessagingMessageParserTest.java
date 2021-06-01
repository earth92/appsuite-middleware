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

package com.openexchange.messaging.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import com.openexchange.exception.OXException;
import com.openexchange.messaging.BinaryContent;
import com.openexchange.messaging.ContentType;
import com.openexchange.messaging.MessagingBodyPart;
import com.openexchange.messaging.MessagingContent;
import com.openexchange.messaging.MessagingField;
import com.openexchange.messaging.MessagingHeader;
import com.openexchange.messaging.MessagingMessage;
import com.openexchange.messaging.MessagingMessageGetSwitch;
import com.openexchange.messaging.MultipartContent;
import com.openexchange.messaging.StringContent;
import com.openexchange.tools.encoding.Base64;

/**
 * {@link MessagingMessageParserTest}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class MessagingMessageParserTest {

    @Test
    public void testParseSimpleFields() throws JSONException, OXException, IOException {
        final JSONObject messageJSON = new JSONObject();

        messageJSON.put("colorLabel", 12);
        messageJSON.put("id", "13");
        messageJSON.put("flags", 313);
        messageJSON.put("receivedDate", 7331);
        messageJSON.put("size", 23);
        messageJSON.put("threadLevel", 3);

        final JSONArray userFlags = new JSONArray();

        userFlags.put("flag1");
        userFlags.put("flag2");
        userFlags.put("flag3");

        messageJSON.put("userFlags", userFlags);
        messageJSON.put("folder", "niceFolder17");
        messageJSON.put("picture", "http://somesite.invalid/somepic.png");

        final MessagingMessage message = new MessagingMessageParser().parse(messageJSON, null, null);

        assertNotNull(message);

        assertEquals("13", message.getSectionId());
        assertEquals(12, message.getColorLabel());
        assertEquals(313, message.getFlags());

        final Collection<String> flags = message.getUserFlags();
        assertNotNull(flags);
        assertEquals(3, flags.size());
        final Iterator<String> iterator = flags.iterator();
        final Set<String> expectedFlags = new HashSet<String>(Arrays.asList("flag1", "flag2", "flag3"));
        while (iterator.hasNext()) {
            assertTrue(expectedFlags.remove(iterator.next()));
        }
        assertTrue(expectedFlags.isEmpty());
        assertEquals(7331, message.getReceivedDate());

        assertEquals(23, message.getSize());
        assertEquals(3, message.getThreadLevel());

        assertEquals("niceFolder17", message.getFolder());
        assertEquals("http://somesite.invalid/somepic.png", message.getPicture());
    }

    @Test
    public void testHeaders() throws JSONException, OXException, IOException {
        final JSONObject messageJSON = new JSONObject();

        final JSONObject headers = new JSONObject();
        headers.put("singleValue", "Value1");

        final JSONArray multiValue = new JSONArray();
        multiValue.put("1").put("2").put("3");
        headers.put("multiValue", multiValue);

        messageJSON.put("headers", headers);

        final MessagingMessage message = new MessagingMessageParser().parse(messageJSON, null, null);

        assertNotNull(message);

        Collection<MessagingHeader> header = message.getHeader("singleValue");
        assertNotNull(header);
        assertEquals("Value1", header.iterator().next().getValue());

        header = message.getHeader("multiValue");
        assertNotNull(header);

        assertEquals(3, header.size());

        final Iterator<MessagingHeader> iterator = header.iterator();
        assertEquals("1", iterator.next().getValue());
        assertEquals("2", iterator.next().getValue());
        assertEquals("3", iterator.next().getValue());

    }

    @Test
    public void testSpecialHeader() throws JSONException, OXException, IOException {
        final JSONObject messageJSON = new JSONObject();

        final JSONObject headers = new JSONObject();
        headers.put("singleValue", "1eulaV");

        messageJSON.put("headers", headers);

        final MessagingMessageParser parser = new MessagingMessageParser();

        parser.addHeaderParser(new InvertedHeaderParser());
        final MessagingMessage message = parser.parse(messageJSON, null, null);

        assertNotNull(message);

        final Collection<MessagingHeader> header = message.getHeader("singleValue");
        assertNotNull(header);
        assertEquals("Value1", header.iterator().next().getValue());
    }

    @Test
    public void testParseHeaderAsAttributeIfItIsAMessagingField() throws JSONException, OXException, IOException {
        final String date = "Sun, 7 Feb 2010 19:20:40 +0100 (CET)";
        final JSONObject messageJSON = new JSONObject("{'to':[{'address':'to.clark.kent@dailyplanet.com'}],'flags':0,'subject':'Subject-Value','bcc':[{'address':'bcc.clark.kent@dailyplanet.com'}],'contentType':{'params':{},'type':'text/plain'},'from':[{'address':'from.clark.kent@dailyplanet.com'}],'size':0,'threadLevel':0,'dispositionNotificationTo':[{'address':'disp.notification.to.clark.kent@dailyplanet.com'}],'priority':'12','sentDate':'" + date + "','cc':[{'address':'cc.clark.kent@dailyplanet.com'}]}");

        final MessagingMessage message = new MessagingMessageParser().parse(messageJSON, null, null);
        assertNotNull(message);

        final MessagingMessageGetSwitch get = new MessagingMessageGetSwitch();

        for (final MessagingField field : MessagingField.values()) {
            if (field.getEquivalentHeader() != null) {
                final Object value = field.doSwitch(get, message);
                assertNotNull(value);
            }
        }
    }

    @Test
    public void testAttributeTrumpsHeader() {

    }

    @Test
    public void testPlainBody() throws JSONException, OXException, IOException {
        final JSONObject messageJSON = new JSONObject();

        messageJSON.put("body", "I am the content");

        final JSONObject headers = new JSONObject();
        headers.put("content-type", "text/plain");
        messageJSON.put("headers", headers);

        final MessagingMessage message = new MessagingMessageParser().parse(messageJSON, null, null);

        assertNotNull(message);

        final MessagingContent content = message.getContent();
        assertNotNull(content);

        assertEquals("I am the content", getStringData(content));
    }

    private String getStringData(final MessagingContent content) throws OXException, IOException {
        if (StringContent.class.isInstance(content)) {
            return ((StringContent) content).getData();
        } else if (BinaryContent.class.isInstance(content)) {
            return inputStream2String(((BinaryContent) content).getData());
        }
        return null;
    }

    @Test
    public void testBinaryBodyInBase64() throws OXException, JSONException, IOException {
        final JSONObject messageJSON = new JSONObject();

        messageJSON.put("body", Base64.encode("I am the content"));

        final JSONObject headers = new JSONObject();
        headers.put("content-type", "application/octet-stream");
        messageJSON.put("headers", headers);

        final MessagingMessage message = new MessagingMessageParser().parse(messageJSON, null, null);

        assertNotNull(message);

        final MessagingContent content = message.getContent();
        assertNotNull(content);

        assertTrue(BinaryContent.class.isInstance(content));

        assertEquals("I am the content", inputStream2String(((BinaryContent) content).getData()));

    }

    @SuppressWarnings("synthetic-access")
    @Test
    public void testBinaryBodyByReference() throws OXException, IOException, JSONException {
        final JSONObject messageJSON = new JSONObject();

        messageJSON.put("body", new JSONObject("{ref : '12'}"));

        final JSONObject headers = new JSONObject();
        headers.put("content-type", "application/octet-stream");
        messageJSON.put("headers", headers);

        final SimInputStreamRegistry registry = new SimInputStreamRegistry();

        final MessagingMessage message = new MessagingMessageParser().parse(messageJSON, registry, null);

        assertEquals("12", registry.getId());
        assertNotNull(message);

        final MessagingContent content = message.getContent();
        assertNotNull(content);

        assertTrue(BinaryContent.class.isInstance(content));

        assertEquals("Mock value", inputStream2String(((BinaryContent) content).getData()));
    }

    @Test
    public void testMultipart() throws JSONException, OXException, IOException {
        final JSONObject messageJSON = new JSONObject("{headers : {'content-type' : 'multipart/mixed'}}");

        final JSONArray multipartJSON = new JSONArray();

        final JSONObject body1 = new JSONObject("{body : 'simpleContent', headers: {content-type : 'text/plain'}, id: '1'}");
        final JSONObject body2 = new JSONObject("{body : '" + Base64.encode("binaryData") + "', headers: {content-type : 'application/octet-stream'}, id: '2'}");
        multipartJSON.put(body1);
        multipartJSON.put(body2);

        messageJSON.put("body", multipartJSON);

        final MessagingMessage message = new MessagingMessageParser().parse(messageJSON, null, null);

        assertNotNull(message);

        final MessagingContent content = message.getContent();
        assertNotNull(content);
        assertTrue(MultipartContent.class.isInstance(content));

        final MultipartContent multipart = (MultipartContent) content;

        assertEquals(2, multipart.getCount());

        final MessagingBodyPart textPart = multipart.get(0);
        //assertEquals("1", textPart.getId());
        assertEquals("text/plain", textPart.getContentType().getBaseType());
        assertEquals("simpleContent", ((StringContent) textPart.getContent()).getData());

        final MessagingBodyPart binPart = multipart.get(1);
        //assertEquals("2", binPart.getId());
        assertEquals("application/octet-stream", binPart.getContentType().getBaseType());
        assertEquals("binaryData", inputStream2String(((BinaryContent) binPart.getContent()).getData()));

    }

    private static final class ReversedContentParser implements MessagingContentParser {

        @Override
        public int getRanking() {
            return 2;
        }

        @Override
        public boolean handles(final MessagingBodyPart partlyParsedMessage, final Object content) throws OXException {
            final ContentType contentType = partlyParsedMessage.getContentType();
            return null != contentType && "text/plain".equals(contentType.getBaseType());
        }

        @Override
        public MessagingContent parse(final MessagingBodyPart partlyParsedMessage, final Object content, final MessagingInputStreamRegistry registry) throws JSONException, OXException, IOException {
            return new StringContent(new StringBuilder((String) content).reverse().toString());
        }

    }

    @SuppressWarnings("synthetic-access")
    @Test
    public void testSpecialBody() throws OXException, JSONException, IOException {
        final JSONObject messageJSON = new JSONObject();

        messageJSON.put("body", "tnetnoc eht ma I");

        final JSONObject headers = new JSONObject();
        headers.put("content-type", "text/plain");
        messageJSON.put("headers", headers);

        final MessagingMessageParser parser = new MessagingMessageParser();
        parser.addContentParser(new ReversedContentParser());
        final MessagingMessage message = parser.parse(messageJSON, null, null);

        assertNotNull(message);

        final MessagingContent content = message.getContent();
        assertNotNull(content);

        assertEquals("I am the content", getStringData(content));
    }

    private String inputStream2String(final InputStream data) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b = 1;
        while ((b = data.read()) != -1) {
            baos.write(b);
        }
        return new String(baos.toByteArray(), com.openexchange.java.Charsets.UTF_8);
    }

    private static final class SimInputStreamRegistry implements MessagingInputStreamRegistry {

        private Object id;

        @Override
        public InputStream get(final Object id) throws OXException, IOException {
            this.id = id;
            return new ByteArrayInputStream("Mock value".getBytes(com.openexchange.java.Charsets.UTF_8));
        }

        public Object getId() {
            return id;
        }

        @Override
        public Object getRegistryEntry(final Object id) throws OXException {
            return "Mock value";
        }

    }

}
