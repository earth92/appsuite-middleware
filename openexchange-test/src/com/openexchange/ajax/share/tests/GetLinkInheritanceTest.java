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
 *    trademarks of the OX Software GmbH group of companies.
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

package com.openexchange.ajax.share.tests;

import static com.openexchange.ajax.folder.manager.FolderManager.INFOSTORE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.Objects;
import java.util.UUID;
import org.junit.Test;
import com.openexchange.ajax.folder.manager.FolderApi;
import com.openexchange.ajax.folder.manager.FolderManager;
import com.openexchange.ajax.framework.AbstractAPIClientSession;
import com.openexchange.testing.httpclient.invoker.ApiClient;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.FolderData;
import com.openexchange.testing.httpclient.models.FolderExtendedPermission;
import com.openexchange.testing.httpclient.models.ShareLinkResponse;
import com.openexchange.testing.httpclient.models.ShareTargetData;
import com.openexchange.testing.httpclient.modules.ShareManagementApi;

/**
 * {@link GetLinkInheritanceTest}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.0
 */
public class GetLinkInheritanceTest extends AbstractAPIClientSession {

    private FolderManager folderManager;
    private ShareManagementApi shareManagementApi;
    private String infostoreRoot;

    private String A, B, C, D, E;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        ApiClient client = getApiClient();
        folderManager = new FolderManager(new FolderApi(client, testUser), "1");
        shareManagementApi = new ShareManagementApi(client);
        infostoreRoot = folderManager.findInfostoreRoot();

        // @formatter:off
        /*
         * Create the following folder structure:
         * root
         *  - A
         *    - B
         *      - C
         *  - D
         *    - E
         */
        // @formatter:on
        A = folderManager.createFolder(infostoreRoot, "A_" + UUID.randomUUID(), INFOSTORE);
        D = folderManager.createFolder(infostoreRoot, "D_" + UUID.randomUUID(), INFOSTORE);

        B = folderManager.createFolder(A, "B_" + UUID.randomUUID(), INFOSTORE);
        C = folderManager.createFolder(B, "C_" + UUID.randomUUID(), INFOSTORE);
        E = folderManager.createFolder(D, "E_" + UUID.randomUUID(), INFOSTORE);
    }

    @Test
    public void testCreate() throws ApiException {
        Integer guestId = createShareLink(A);

        // Check sub-folder
        checkFolderPermissions(B, 1, 2, guestId);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 2, guestId);
        // Check no-link folder
        checkFolderPermissions(D, 1, 1);
    }

    @Test
    public void testMultipleLinks() throws ApiException {
        Integer guestId = createShareLink(A);
        Integer guestId2 = createShareLink(B);

        // Check sub-folder
        checkFolderPermissions(B, 2, 3, guestId);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 3, guestId, guestId2);
        // Check no-link folder
        checkFolderPermissions(D, 1, 1);
    }

    @Test
    public void testMultipleLinksAndMove() throws ApiException {
        Integer guestId = createShareLink(A);
        Integer guestId2 = createShareLink(B);

        // Check sub-folder
        checkFolderPermissions(B, 2, 3, guestId);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 3, guestId, guestId2);
        // Check no-link folder
        checkFolderPermissions(D, 1, 1);

        // Move sub-folder to no-link folder; Inherit permissions from D
        folderManager.moveFolder(B, D, Boolean.TRUE);

        // Check sub-folder
        checkFolderPermissions(B, 1, 1);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 1);
        // Check no-link folder
        checkFolderPermissions(D, 1, 1);

        // Move sub-folder back to parent folder; Inherit permissions from A
        folderManager.moveFolder(B, A, Boolean.TRUE);

        // Check sub-folder
        checkFolderPermissions(B, 1, 2, guestId);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 2, guestId);
        // Check no-link folder
        checkFolderPermissions(D, 1, 1);

    }

    @Test
    public void testMoveAway() throws ApiException {
        Integer guestId = createShareLink(A);

        // Check sub-folder
        checkFolderPermissions(B, 1, 2, guestId);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 2, guestId);
        // Check no-link folder
        checkFolderPermissions(D, 1, 1);

        // Move sub-folder to no-link folder
        folderManager.moveFolder(B, D, Boolean.TRUE);

        // Check sub-folder
        checkFolderPermissions(B, 1, 1);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 1);
        // Check no-link folder
        checkFolderPermissions(D, 1, 1);
    }

    @Test
    public void testMoveInto() throws ApiException {
        Integer guestId = createShareLink(A);

        // Check sub-folder
        checkFolderPermissions(B, 1, 2, guestId);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 2, guestId);
        // Check no-link folder
        checkFolderPermissions(D, 1, 1);
        // Check no-link sub-folder
        checkFolderPermissions(E, 1, 1);

        // Move no-link folder to parent folder
        folderManager.moveFolder(D, A);

        // Check sub-folder
        checkFolderPermissions(B, 1, 2, guestId);
        // Check subsub-folder
        checkFolderPermissions(C, 1, 2, guestId);
        // Check no-link folder
        checkFolderPermissions(D, 1, 2, guestId);
        // Check no-link sub-folder
        checkFolderPermissions(E, 1, 2, guestId);
    }

    /*
     * ---------------------------------- helper methods ------------------------------------------------------------------------
     */

    /**
     * Checks if folder contains the given amount of normal and extended permissions and optionally checks if the extended permissions contains an inherited permissions for the given guest ids.
     *
     * @param folderId The folder to check
     * @param permSize The amount of normal permissions
     * @param extendedPermSize The amount of extended permissions
     * @param optGuestIds The optional guest ids to check
     * @throws ApiException
     */
    private void checkFolderPermissions(String folderId, int permSize, int extendedPermSize, Integer... optGuestIds) throws ApiException {
        FolderData folder = folderManager.getFolder(folderId);
        assertEquals("Unexpected permission size", permSize, folder.getPermissions().size());
        assertEquals("Unexpected extended permission size", extendedPermSize, folder.getComOpenexchangeShareExtendedPermissions().size());
        if (optGuestIds != null) {
            for (Integer guestId : optGuestIds) {
                checkExtendedPermission(folder, guestId, true);
            }
        }
    }

    /**
     * Checks if the folder has an extended permission for the given entity and checks if this property inherited value is equal to the given value.
     *
     * @param folder The folder
     * @param entity The entity to search
     * @param isInherited Whether the entity should or should not be an inherited permission
     */
    private void checkExtendedPermission(FolderData folder, Integer entity, boolean isInherited) {
        for (FolderExtendedPermission perm : folder.getComOpenexchangeShareExtendedPermissions()) {
            if (Objects.equals(perm.getEntity(), entity)) {
                assertTrue("The extended permission does not have the expected inherited types", perm.getIsInherited().booleanValue() == isInherited);
                return;
            }
        }
        fail("Folder doesn't contain an extended permission for entity " + entity);
    }

    /**
     * Creates a new share link for the given folder
     *
     * @param folder The folder to create a share link for
     * @return The guest id of for the new link
     * @throws ApiException
     */
    private Integer createShareLink(String folder) throws ApiException {
        ShareTargetData data = new ShareTargetData();
        data.setFolder(folder);
        data.setModule(INFOSTORE);
        ShareLinkResponse shareLink = shareManagementApi.getShareLink(data);
        checkResponse(shareLink.getError(), shareLink.getErrorDesc(), shareLink.getData());
        folderManager.setLastTimestamp(shareLink.getTimestamp());
        return shareLink.getData().getEntity();
    }

}
