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

package com.openexchange.mail.mime.utils;

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.ParameterList;
import javax.mail.internet.ParseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.openexchange.exception.OXException;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.mime.ContentDisposition;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.utils.MessageUtility;
import com.sun.mail.imap.protocol.BODYSTRUCTURE;

/**
 * {@link MimeMessageUtilityTest}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:vitali.sjablow@open-xchange.com">Vitali Sjablow</a>
 * @since 7.6.1
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ MimeMessageUtility.class })
public class MimeMessageUtilityTest {

    private static final String ATTACHMENT_WRONG_FOUND = "Attachment found where none should be";
    private static final String TESTFILE_RTF = "testfile; filename=randomfile.rtf";
    private static final String PLAIN_TEXT = "plain/text";
    private static final String NOT_PROPERLY_UNFOLDED_DECODED = "Not properly unfolded/decoded.";
    private static final String ATTACHMENT_NOT_IDENTIFIED = "Attachment was not identified";
    @Mock
    private Part part;
    @Mock
    private MailPart mailpart;
    @Mock
    private BODYSTRUCTURE bodystructure;

    private static final String MIXED_SUBTYPE = "mixed";

    @Before
    public void setUp() {
        bodystructure.subtype = MIXED_SUBTYPE;
    }

    public MimeMessageUtilityTest() {
        super();
    }

    @Test
    public void testForBug53100() {
        String s = "=?CP-850?B?U3BhbV9JTVNWQTpOb24gcmVjYXBpdGFiaWxlOiBTcGE=?==?CP-850?B?bV9JTVNWQTo4NTI2?=";
        s = MimeMessageUtility.decodeEnvelopeSubject(s);

        assertEquals("Subject nor properly unfolded/decoded.", "Spam_IMSVA:Non recapitabile: Spam_IMSVA:8526", s);
    }

    @Test
    public void testForBug53023() {
        String s = "=?utf-8?B?TGEgdHVhIExpYmVybyBNYWlsIFBsdXMgc3RhIHBlciBlc3M=?==?utf-8?B?ZXJlIHJpbm5vdmF0YQ==?=";
        s = MimeMessageUtility.decodeEnvelopeSubject(s);

        assertEquals("Subject nor properly unfolded/decoded.", "La tua Libero Mail Plus sta per essere rinnovata", s);
    }

    @Test
    public void testForBug52314() {
        String s = "=?utf-8?B?44CQ6YeN6KaB44CR5oqA6KGT5bGANOaciDHml6XjgYvjgonj?= =?utf-8?B?ga7pgYvnlKjlpInmm7TvvIjmp4vpgKDmlLnpnanvvIk=?=";
        s = MimeMessageUtility.decodeEnvelopeSubject(s);

        assertTrue("Subject nor properly unfolded/decoded", s.indexOf(MessageUtility.UNKNOWN) < 0);
    }

    @Test
    public void testForBug33044_SubjectUnfolding() {
        String s = "=?UTF-8?B?UG90d2llcmR6ZW5pZSB6YW3Ds3dp?=\r\n =?UTF-8?B?ZW5pYQ==?=";
        s = MimeMessageUtility.decodeEnvelopeSubject(s);

        assertEquals("Subject nor properly unfolded/decoded.", "Potwierdzenie zam\u00f3wienia", s);
    }

    @Test
    public void testForBug55229() {
        String s = "=?iso-2022-jp?B?GyRCJE8kNyQ0JEAkKyVGJTklSCEnfGIbKEI=?=";
        s = MimeMessageUtility.decodeEnvelopeSubject(s);

        assertTrue("Subject nor properly unfolded/decoded", MessageUtility.containsNoUnknown(s));
    }

    @Test
    public void testForBug36072_AddressUnfolding() {
        String s = "=?UTF-8?Q?Wielkoszcz=C4=99ko=C5=9Bciskowiczkiewi?= =?UTF-8?Q?cz=C3=B3wnaOm=C3=B3jbo=C5=BCejestemno=C5=BCemwie?= " + "=?UTF-8?Q?leznacz=C4=85cychznak=C3=B3wsi=C4=99znaczyb?= =?UTF-8?Q?oprzecie=C5=BCniemo=C5=BCeby=C4=87zbyt=C5=82atwo!?= <foo@bar.tld>";
        s = MimeMessageUtility.decodeMultiEncodedHeader(s);
        assertEquals("Address nor properly unfolded/decoded.", "Wielkoszcz\u0119ko\u015bciskowiczkiewicz\u00f3wnaOm\u00f3jbo\u017cejestemno\u017cemwieleznacz\u0105cychznak\u00f3wsi\u0119znaczyboprzecie\u017cniemo\u017ceby\u0107zbyt\u0142atwo! " + "<foo@bar.tld>", s);

        s = "=?ISO-8859-1?Q?a?= b";
        s = MimeMessageUtility.decodeMultiEncodedHeader(s);
        assertEquals(NOT_PROPERLY_UNFOLDED_DECODED, "a b", s);

        s = "=?ISO-8859-1?Q?a?= =?ISO-8859-1?Q?b?=";
        s = MimeMessageUtility.decodeMultiEncodedHeader(s);
        assertEquals(NOT_PROPERLY_UNFOLDED_DECODED, "ab", s);

        s = "=?ISO-8859-1?Q?a?=  =?ISO-8859-1?Q?b?=";
        s = MimeMessageUtility.decodeMultiEncodedHeader(s);
        assertEquals(NOT_PROPERLY_UNFOLDED_DECODED, "ab", s);

        s = "=?ISO-8859-1?Q?a?=\r\n" + "\t=?ISO-8859-1?Q?b?=";
        s = MimeMessageUtility.decodeMultiEncodedHeader(s);
        assertEquals(NOT_PROPERLY_UNFOLDED_DECODED, "ab", s);

        s = "=?ISO-8859-1?Q?a_b?=";
        s = MimeMessageUtility.decodeMultiEncodedHeader(s);
        assertEquals(NOT_PROPERLY_UNFOLDED_DECODED, "a b", s);

        s = "=?ISO-8859-1?Q?a?= =?ISO-8859-2?Q?_b?=";
        s = MimeMessageUtility.decodeMultiEncodedHeader(s);
        assertEquals(NOT_PROPERLY_UNFOLDED_DECODED, "a b", s);

        s = "a b";
        s = MimeMessageUtility.decodeMultiEncodedHeader(s);
        assertEquals(NOT_PROPERLY_UNFOLDED_DECODED, "a b", s);
    }

    @Test
    public void testHasAttachment_MultipartDisposition() {
        boolean result = false;
        try {
            Mockito.when(part.getContentType()).thenReturn("inline");
            Mockito.when(part.getContentType()).thenReturn(PLAIN_TEXT);
            Mockito.when(part.getDisposition()).thenReturn("attachment; random=value");
            result = MimeMessageUtility.hasAttachments(part);
        } catch (MessagingException | OXException | IOException e) {
            e.printStackTrace();
        }
        assertTrue(ATTACHMENT_NOT_IDENTIFIED, result);
    }

    @Test
    public void testHasAttachment_MultipartFilename() {
        boolean result = false;
        try {
            Mockito.when(part.getContentType()).thenReturn("inline");
            Mockito.when(part.getContentType()).thenReturn(PLAIN_TEXT);
            Mockito.when(part.getDisposition()).thenReturn(TESTFILE_RTF);
            result = MimeMessageUtility.hasAttachments(part);
        } catch (MessagingException | OXException | IOException e) {
            e.printStackTrace();
        }
        assertTrue(ATTACHMENT_NOT_IDENTIFIED, result);
    }

    @Test
    public void testHasAttachment_MultipartSignature() {
        boolean result = true;
        try {
            Mockito.when(part.getContentType()).thenReturn("inline");
            Mockito.when(part.getContentType()).thenReturn("application/pkcs7-signature");
            result = MimeMessageUtility.hasAttachments(part);
        } catch (MessagingException | OXException | IOException e) {
            e.printStackTrace();
        }
        assertFalse(ATTACHMENT_WRONG_FOUND, result);
    }

    @Test
    public void testHasAttachment_MailpartDisposition() {
        boolean result = false;
        try {
            Mockito.when(I(mailpart.getEnclosedCount())).thenReturn(I(1));
            ContentDisposition cd = new ContentDisposition("attachment; random=value");
            Mockito.when(mailpart.getContentDisposition()).thenReturn(cd);
            ContentType ct = new ContentType(PLAIN_TEXT);
            Mockito.when(mailpart.getContentType()).thenReturn(ct);
            Mockito.when(mailpart.getEnclosedMailPart(0)).thenReturn(mailpart);
            result = MimeMessageUtility.hasAttachments(mailpart, "inline");
        } catch (MessagingException | OXException | IOException e) {
            e.printStackTrace();
        }
        assertTrue(ATTACHMENT_NOT_IDENTIFIED, result);
    }

    @Test
    public void testHasAttachment_MailpartFilename() {
        boolean result = false;
        try {
            Mockito.when(I(mailpart.getEnclosedCount())).thenReturn(I(1));
            ContentDisposition cd = new ContentDisposition(TESTFILE_RTF);
            Mockito.when(mailpart.getContentDisposition()).thenReturn(cd);
            ContentType ct = new ContentType(PLAIN_TEXT);
            Mockito.when(mailpart.getContentType()).thenReturn(ct);
            Mockito.when(mailpart.getEnclosedMailPart(0)).thenReturn(mailpart);
            result = MimeMessageUtility.hasAttachments(mailpart, "inline");
        } catch (MessagingException | OXException | IOException e) {
            e.printStackTrace();
        }
        assertTrue(ATTACHMENT_NOT_IDENTIFIED, result);
    }

    @Test
    public void testHasAttachment_MailpartSignature() {
        boolean result = true;
        try {
            ContentType ct = new ContentType("Application");
            Mockito.when(mailpart.getContentType()).thenReturn(ct);
            result = MimeMessageUtility.hasAttachments(mailpart, "Pkcs7-Signature");
        } catch (MessagingException | OXException | IOException e) {
            e.printStackTrace();
        }
        assertFalse(ATTACHMENT_WRONG_FOUND, result);
    }

    @Test
    public void testHasAttachment_BodystructureDisposition() {
        boolean result = false;
        setContentTypePlainText(bodystructure);
        bodystructure.disposition = "attachment";
        result = MimeMessageUtility.hasAttachments(bodystructure);
        assertTrue(ATTACHMENT_NOT_IDENTIFIED, result);
    }

    @Test
    public void testHasAttachment_BodystructureFilename() throws ParseException {
        boolean result = false;
        setContentTypePlainText(bodystructure);
        bodystructure.dParams = new ParameterList(";filename=randomfile.rtf");
        result = MimeMessageUtility.hasAttachments(bodystructure);
        assertTrue(ATTACHMENT_NOT_IDENTIFIED, result);
    }

    @Test
    public void testHasAttachment_BodystructureSignature() {
        boolean result = true;
        bodystructure.type = "APPLICATION";
        bodystructure.subtype = "Pkcs7-Signature";
        result = MimeMessageUtility.hasAttachments(bodystructure);
        assertFalse(ATTACHMENT_WRONG_FOUND, result);
    }

    public void testHasAttachment_BodystructureMessageRFC() {
        boolean result = true;
        bodystructure.type = "message";
        bodystructure.subtype = "rfc822";
        result = MimeMessageUtility.hasAttachments(bodystructure);
        assertTrue(ATTACHMENT_WRONG_FOUND, result);
    }

    private static void setContentTypePlainText(BODYSTRUCTURE bs) {
        bs.type = "plain";
        bs.subtype = "text";
    }

    @Test
    public void testReplaceWithComma_MissingParameter() {
        String result = MimeMessageUtility.replaceWithComma(null);
        assertTrue("result not null, like expected", result == null);
    }

    @Test
    public void testReplaceWithComma_failedCheckReplaceWithComma() {
        PowerMockito.stub(PowerMockito.method(MimeMessageUtility.class, "checkReplaceWithComma")).toReturn(B(false));
        String address = "address";
        String result = MimeMessageUtility.replaceWithComma(address);
        assertTrue("result not like expected", result == address);
    }

    @Test
    public void testReplaceWithComma_MatcherFailed() {
        PowerMockito.stub(PowerMockito.method(MimeMessageUtility.class, "checkReplaceWithComma")).toReturn(B(true));
        String address = "address";
        String result = MimeMessageUtility.replaceWithComma(address);
        assertTrue("result not like expected", result == address);
    }

    @Test
    public void testReplaceWithComma_OneAddress() {
        PowerMockito.stub(PowerMockito.method(MimeMessageUtility.class, "checkReplaceWithComma")).toReturn(B(true));
        String address = ".address;";
        String result = MimeMessageUtility.replaceWithComma(address);
        assertTrue("result not like expected", result.equals(".address,"));
    }

    @Test
    public void testReplaceWithComma_MultipleAddresses() {
        PowerMockito.stub(PowerMockito.method(MimeMessageUtility.class, "checkReplaceWithComma")).toReturn(B(true));
        String address = ".address;.addressTwo";
        String result = MimeMessageUtility.replaceWithComma(address);
        assertTrue("result not like expected", result.equals(".address,.addressTwo"));
    }
}
