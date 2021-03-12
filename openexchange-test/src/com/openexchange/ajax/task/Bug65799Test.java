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

package com.openexchange.ajax.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.ByteArrayInputStream;
import org.junit.Test;
import com.openexchange.ajax.attach.actions.AttachRequest;
import com.openexchange.ajax.attach.actions.AttachResponse;
import com.openexchange.ajax.framework.Abstrac2UserAJAXSession;
import com.openexchange.ajax.task.actions.InsertRequest;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.tasks.Create;
import com.openexchange.groupware.tasks.Task;
import com.openexchange.java.util.UUIDs;

/**
 * {@link Bug65799Test}
 *
 * Attachment API allows access to private tasks
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.3
 */
public class Bug65799Test extends Abstrac2UserAJAXSession {

    @Test
    public void testAddAttachment() throws Exception {
        /*
         * as user a, create a task folder, shared to user b
         */
        FolderObject folder = ftm.insertFolderOnServer(ftm.generateSharedFolder(UUIDs.getUnformattedStringFromRandom(), FolderObject.TASK,
            getClient().getValues().getPrivateTaskFolder(), getClient().getValues().getUserId(), client2.getValues().getUserId()));
        /*
         * create a 'private' task in private folder as user a
         */
        Task task = Create.createWithDefaults(folder.getObjectID(), "test");
        task.setPrivateFlag(true);
        getClient().execute(new InsertRequest(task, getClient().getValues().getTimeZone(), true)).fillTask(task);
        /*
         * try and attach a file to the tasks as user 2
         */
        AttachResponse attachResponse = client2.execute(new AttachRequest(task, "test.txt", new ByteArrayInputStream("wurst".getBytes()), "text/plain"));
        assertTrue(attachResponse.hasError());
        assertEquals("TSK-0046", attachResponse.getException().getErrorCode());
    }

}
