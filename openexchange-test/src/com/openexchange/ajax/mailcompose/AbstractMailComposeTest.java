/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH. group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.ajax.mailcompose;

import static com.openexchange.java.Autoboxing.I;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.rmi.server.UID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.openexchange.ajax.framework.AbstractAPIClientSession;
import com.openexchange.java.Strings;
import com.openexchange.test.common.configuration.AJAXConfig;
import com.openexchange.test.common.test.TestClassConfig;
import com.openexchange.test.common.test.TestInit;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.ContactData;
import com.openexchange.testing.httpclient.models.FolderUpdateResponse;
import com.openexchange.testing.httpclient.models.MailComposeAttachmentPostResponse;
import com.openexchange.testing.httpclient.models.MailComposeAttachmentResponse;
import com.openexchange.testing.httpclient.models.MailComposeGetResponse;
import com.openexchange.testing.httpclient.models.MailComposeResponse;
import com.openexchange.testing.httpclient.models.MailComposeResponseMessageModel;
import com.openexchange.testing.httpclient.models.MailComposeSendResponse;
import com.openexchange.testing.httpclient.models.MailDestinationData;
import com.openexchange.testing.httpclient.models.MailImportResponse;
import com.openexchange.testing.httpclient.models.NewFolderBody;
import com.openexchange.testing.httpclient.models.NewFolderBodyFolder;
import com.openexchange.testing.httpclient.modules.ContactsApi;
import com.openexchange.testing.httpclient.modules.FoldersApi;
import com.openexchange.testing.httpclient.modules.MailApi;
import com.openexchange.testing.httpclient.modules.MailComposeApi;

/**
 * {@link AbstractMailComposeTest}
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 * @since v7.10.2
 */
public abstract class AbstractMailComposeTest extends AbstractAPIClientSession {

    protected MailComposeApi api;
    protected final String DEFAULT_COLUMNS = "from,sender,to,cc,bcc,subject";
    protected final String ALL_COLUMNS = "from,sender,to,cc,bcc,subject,content,contentType,attachments,sharedAttachmentsInfo,meta,requestReadReceipt,priority,security,contentEncrypted";
    protected final List<String> compositionSpaceIds = new ArrayList<>();

    protected MailApi mailApi;
    protected File attachment;
    protected File attachment2;
    protected String testMailDir;
    protected final List<MailDestinationData> IMPORTED_EMAILS = new ArrayList<>();
    protected FoldersApi foldersApi;

    private String testFolderId = null;

    private static final String FOLDER = "default0%2FINBOX";

    /**
     * Initializes a new {@link AbstractMailComposeTest}.
     */
    public AbstractMailComposeTest() {
        super();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.api = new MailComposeApi(getApiClient());

        mailApi = new MailApi(getApiClient());
        foldersApi = new FoldersApi(getApiClient());
        testMailDir = AJAXConfig.getProperty(AJAXConfig.Property.TEST_DIR);

        attachment = new File(TestInit.getTestProperty("ajaxPropertiesFile"));
        attachment2 = new File(TestInit.getTestProperty("provisioningFile"));

        MailComposeGetResponse allSpaces = api.getMailCompose(null);
        for (MailComposeResponseMessageModel model : allSpaces.getData()) {
            api.deleteMailComposeById(model.getId(), null);
        }
    }

    @Override
    public TestClassConfig getTestConfig() {
        return TestClassConfig.builder().createApiClient().withUserPerContext(2).build();
    }

    protected MailDestinationData importTestMailWithAttachment() throws ApiException {
        return importTestMail("mailcompose_mail-with-pdf-attachment.eml");
    }

    /**
     *
     * importTestMail
     *
     * @param fileName The file name of the mail to upload
     * @return
     * @throws ApiException
     */
    protected MailDestinationData importTestMail(String fileName) throws ApiException {
        if (testFolderId == null) {
            NewFolderBody body = new NewFolderBody();
            NewFolderBodyFolder folder = new NewFolderBodyFolder();
            folder.setTitle(this.getClass().getSimpleName() + "_" + new UID().toString());
            folder.setModule("mail");
            folder.setPermissions(null);
            body.setFolder(folder);
            FolderUpdateResponse createFolder = foldersApi.createFolder(FOLDER, body, "0", null, null, null);
            testFolderId = createFolder.getData();
        }

        File emlFile = new File(testMailDir, fileName);
        MailImportResponse response = mailApi.importMail(testFolderId, emlFile, null, Boolean.TRUE);
        List<MailDestinationData> data = response.getData();
        MailDestinationData mailWithAttachment = data.get(0);
        IMPORTED_EMAILS.add(mailWithAttachment);
        return mailWithAttachment;
    }

    protected MailComposeResponseMessageModel createNewCompositionSpace() throws Exception {
        MailComposeResponse response = api.postMailCompose(null, null, null, null);
        assertTrue(response.getErrorDesc(), Strings.isEmpty(response.getError()));
        MailComposeResponseMessageModel data = response.getData();
        compositionSpaceIds.add(data.getId());
        return data;
    }

    protected String getMailAddress() throws Exception {
        ContactsApi contactsApi = new ContactsApi(getApiClient());
        ContactData data = contactsApi.getContactByUser(I(testUser.getUserId())).getData();
        assertNotNull("No contact data for user.", data);
        String mailAddress = data.getEmail1();
        assertFalse("No mail address for user.", Strings.isEmpty(mailAddress));
        return mailAddress;
    }

    protected String getOtherMailAddress() throws Exception {
        ContactsApi contactsApi = new ContactsApi(getApiClient());
        ContactData data = contactsApi.getContactByUser(I(testUser2.getUserId())).getData();
        assertNotNull("No contact data for other user.", data);
        String mailAddress = data.getEmail1();
        assertFalse("No mail address for other user.", Strings.isEmpty(mailAddress));
        return mailAddress;
    }

    protected List<String> getSender() throws Exception {
        return Arrays.asList(new String[] { testUser.getUser(), getMailAddress() });
    }

    protected List<List<String>> getRecipient() throws Exception {
        return Collections.singletonList(Arrays.asList(new String[] { testUser2.getUser(), getOtherMailAddress() }));
    }

    protected void check(MailComposeSendResponse response) {
        check(response.getErrorDesc(), response.getError(), response);
    }

    protected void check(MailComposeResponse response) {
        check(response.getErrorDesc(), response.getError(), response);
    }

    protected void check(MailComposeGetResponse response) {
        check(response.getErrorDesc(), response.getError(), response);
    }

    protected void check(MailComposeAttachmentResponse response) {
        check(response.getErrorDesc(), response.getError(), response);
    }

    protected void check(MailComposeAttachmentPostResponse response) {
        check(response.getErrorDesc(), response.getError(), response);
    }

    //    protected void check(InlineResponse2002 response) {
    //        assertTrue(b(response.getSuccess()));
    //    }

    protected void check(String errorDesc, String error, Object notNull) {
        assertNotNull(notNull);
        assertNull(errorDesc, error);
    }

}
