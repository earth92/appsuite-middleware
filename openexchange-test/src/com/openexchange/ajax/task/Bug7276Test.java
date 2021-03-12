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

import static com.openexchange.java.Autoboxing.I;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.participant.ParticipantTools;
import com.openexchange.ajax.task.actions.DeleteRequest;
import com.openexchange.ajax.task.actions.GetRequest;
import com.openexchange.ajax.task.actions.GetResponse;
import com.openexchange.ajax.task.actions.InsertRequest;
import com.openexchange.ajax.task.actions.InsertResponse;
import com.openexchange.ajax.task.actions.UpdateRequest;
import com.openexchange.ajax.task.actions.UpdateResponse;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.tasks.Create;
import com.openexchange.groupware.tasks.Task;
import com.openexchange.groupware.tasks.TaskExceptionCode;
import com.openexchange.test.TestClassConfig;

/**
 * Tests problem described in bug #7276.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public class Bug7276Test extends AbstractTaskTest {

    private AJAXClient client2;

    /**
     * @param name
     */
    public Bug7276Test() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        client2 = testUser2.getAjaxClient();
    }

    @Override
    public TestClassConfig getTestConfig() {
        return TestClassConfig.builder().withUserPerContext(2).createAjaxClient().build();
    }

    /**
     * Tests if bug #7276 appears again.
     *
     * @throws Throwable if this test fails.
     */
    @Test
    public void testBug() throws Throwable {
        final int folder2 = client2.getValues().getPrivateTaskFolder();
        // User 1 inserts task.
        Task task = Create.createWithDefaults();
        task.setTitle("Test bug #7276");
        task.setParentFolderID(getClient().getValues().getPrivateTaskFolder());
        task.setParticipants(ParticipantTools.createParticipants(getClient().getValues().getUserId(), client2.getValues().getUserId()));
        {
            final InsertResponse response = getClient().execute(new InsertRequest(task, getClient().getValues().getTimeZone()));
            response.fillTask(task);
        }
        // User 2 checks if he can see it.
        client2.execute(new GetRequest(folder2, task.getObjectID()));
        // User 1 modifies the task and removes participant User 2
        {
            final GetResponse response = getClient().execute(new GetRequest(task.getParentFolderID(), task.getObjectID()));
            task = response.getTask(getClient().getValues().getTimeZone());
        }
        task.setParticipants(ParticipantTools.createParticipants(getClient().getValues().getUserId()));
        {
            final UpdateResponse response = getClient().execute(new UpdateRequest(task, getClient().getValues().getTimeZone()));
            task.setLastModified(response.getTimestamp());
        }
        // Now User 2 tries to load the task again.
        {
            final GetResponse response = client2.execute(new GetRequest(folder2, task.getObjectID(), false));
            assertTrue("Server does not give exception although it has to.", response.hasError());
            OXException expectedErr = TaskExceptionCode.NO_PERMISSION.create(I(0), I(0));
            OXException actual = response.getException();
            assertTrue("Wrong exception", actual.similarTo(expectedErr));
        }
        // Clean up
        getClient().execute(new DeleteRequest(task));
    }
}
