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

package com.openexchange.ajax.folder.api2;

import static com.openexchange.java.Autoboxing.I;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.util.Date;
import java.util.Iterator;
import org.junit.Before;
import org.junit.Test;
import com.openexchange.ajax.folder.actions.EnumAPI;
import com.openexchange.ajax.folder.actions.GetRequest;
import com.openexchange.ajax.folder.actions.GetResponse;
import com.openexchange.ajax.folder.actions.ListRequest;
import com.openexchange.ajax.folder.actions.ListResponse;
import com.openexchange.ajax.framework.Abstrac2UserAJAXSession;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.test.common.test.PermissionTools;

/**
 * {@link Bug16303Test}
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public class Bug16303Test extends Abstrac2UserAJAXSession {

    private FolderObject createdFolder;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        createdFolder = new FolderObject();
        createdFolder.setModule(FolderObject.CALENDAR);
        createdFolder.setParentFolderID(getClient().getValues().getPrivateAppointmentFolder());
        createdFolder.setPermissions(PermissionTools.P(I(client1.getValues().getUserId()), PermissionTools.ADMIN, I(client2.getValues().getUserId()), "arawada"));
        createdFolder.setFolderName("testFolder4Bug16303");
        ftm.insertFolderOnServer(createdFolder);
        // Unfortunately no timestamp when creating a mail folder through Outlook folder tree.
        createdFolder.setLastModified(new Date());

        // Init some caching with other user
        ListRequest listRequest = new ListRequest(EnumAPI.OUTLOOK, FolderStorage.SHARED_ID);
        ListResponse listResponse = client2.execute(listRequest);
        String expectedId = FolderObject.SHARED_PREFIX + client1.getValues().getUserId();
        Iterator<FolderObject> iter = listResponse.getFolder();
        FolderObject foundUserShared = null;
        while (iter.hasNext()) {
            FolderObject folder = iter.next();
            if (expectedId.equals(folder.getFullName())) {
                foundUserShared = folder;
            }
        }
        assertNotNull("Expected user named shared folder below root shared folder.", foundUserShared);

        ListRequest listRequest2 = new ListRequest(EnumAPI.OUTLOOK, foundUserShared.getFullName());
        listResponse = client2.execute(listRequest2);
        iter = listResponse.getFolder();
        FolderObject foundShared = null;
        while (iter.hasNext()) {
            FolderObject folder = iter.next();
            if (folder.getObjectID() == folder.getObjectID()) {
                foundShared = folder;
            }
        }
        assertNotNull("Shared folder expected below shared parent folder.", foundShared);
    }

    @Test
    public void testForDisappearingFolder() throws Throwable {
        GetRequest request = new GetRequest(EnumAPI.OUTLOOK, client1.getValues().getPrivateAppointmentFolder());
        GetResponse response = client1.execute(request);
        FolderObject testFolder = response.getFolder();
        assertTrue("Private appointment folder must have subfolder flag true.", testFolder.hasSubfolders());
    }
}
