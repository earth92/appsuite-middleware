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

package com.openexchange.ajax.chronos;

import static com.openexchange.java.Autoboxing.I;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.openexchange.ajax.chronos.manager.EventManager;
import com.openexchange.ajax.chronos.util.DateTimeUtil;
import com.openexchange.junit.Assert;
import com.openexchange.test.TestClassConfig;
import com.openexchange.testing.httpclient.invoker.ApiClient;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.AlarmTrigger;
import com.openexchange.testing.httpclient.models.AlarmTriggerData;

/**
 * {@link AbstractAlarmTriggerTest}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.0
 */
public abstract class AbstractAlarmTriggerTest extends AbstractAlarmTest {

    protected String folderId2;
    protected UserApi user2;
    protected EventManager eventManager2;

    /**
     * Initializes a new {@link AbstractAlarmTriggerTest}.
     */
    public AbstractAlarmTriggerTest() {
        super();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        ApiClient client2 = testUser2.getApiClient();
        user2 = new UserApi(client2, getEnhancedApiClient(), testUser2);
        folderId2 = getDefaultFolder(client2);
        eventManager2 = new EventManager(user2, getDefaultFolder(client2));
    }

    @Override
    public TestClassConfig getTestConfig() {
        return TestClassConfig.builder().createApiClient().withUserPerContext(2).useEnhancedApiClients().build();
    }

    /**
     * Retrieves alarm triggers with a trigger time lower than the given limit and checks if the response contains the correct amount of alarm trigger objects.
     * Its also possible to filter for specific actions.
     *
     * @param until The upper limit
     * @param actions The actions to retrieve
     * @param expected The expected amount of alarm objects
     * @param filter The event id to filter for
     * @return The {@link AlarmTriggerData}
     * @throws ApiException
     */
    List<AlarmTrigger> getAndCheckAlarmTrigger(long until, String actions, int expected, String filter) throws ApiException {
        //AlarmTriggerData triggers = eventManager.getAlarmTrigger(until, actions);
        List<AlarmTrigger> triggers = eventManager.getAlarmTrigger(until, actions);
        //AlarmTriggerData result = triggers;
        List<AlarmTrigger> result = null;
        if (filter != null) {
            result = new ArrayList<>();
            for(AlarmTrigger trigger: triggers) {
                if (trigger.getEventId().equals(filter)) {
                    result.add(trigger);
                }
            }
        }
        assertNotNull(result);
        assertEquals(expected, result.size());
        return result;
    }

    /**
     * Retrieves alarm triggers from the given time until two days and checks if the response contains the correct amount of alarm trigger objects
     *
     * @param until The upper limit of the request
     * @param min The minimum amount of expected alarm trigger objects
     * @return The {@link AlarmTriggerData}
     * @throws ApiException
     */
    List<AlarmTrigger> getAndCheckAlarmTrigger(long until, int min) throws ApiException {
    	List<AlarmTrigger> triggers = eventManager.getAlarmTrigger(until);
        assertTrue(String.format("Missing some triggers. Expected at least %s triggers but only found %s", I(min), I(triggers.size())), min <= triggers.size());
        return triggers;
    }

    /**
     * Retrieves alarm triggers until two days and checks if the response contains the correct amount of alarm trigger objects
     *
     * @param from The lower limit of the request
     * @param min The minimum amount of expected alarm trigger objects
     * @return The {@link AlarmTriggerData}
     * @throws ApiException
     */
    List<AlarmTrigger> getAndCheckAlarmTrigger(int min) throws ApiException {
        return getAndCheckAlarmTrigger(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2), min);
    }

    /**
     * Retrieves alarm triggers until two days.
     *
     * @return The {@link AlarmTriggerData}
     * @throws ApiException
     */
    List<AlarmTrigger> getAlarmTriggers() throws ApiException {
        return eventManager.getAlarmTrigger(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2));
    }

    /**
     * Checks if the trigger is related to the given event and if the trigger time is correct
     *
     * @param trigger The trigger
     * @param eventId The event id
     * @param expectedTime The expected trigger time
     * @throws ParseException
     */
    protected void checkAlarmTime(AlarmTrigger trigger, String eventId, long expectedTime) throws ParseException {
        assertEquals("Wrong event id.", eventId, trigger.getEventId());
        Date parsedTime = DateTimeUtil.parseZuluDateTime(trigger.getTime());
        assertEquals("The alarm trigger time is different than expected.", expectedTime, parsedTime.getTime());
    }

    //protected boolean containsAlarm(AlarmTriggerData data, String folder, String alarmId, String eventId) {
    protected boolean containsAlarm(List<AlarmTrigger> data, String folder, String alarmId, String eventId) {
        for (AlarmTrigger trigger : data) {
            if ((folder == null || trigger.getFolder() == folder) &&
                (alarmId == null || trigger.getAlarmId() == alarmId) &&
                (eventId == null || trigger.getEventId() == eventId)) {
                return true;
            }
        }
        return false;
    }

    protected AlarmTrigger findTrigger(String eventId, List<AlarmTrigger> triggers) {
        Optional<AlarmTrigger> result = triggers.stream().filter(t -> t.getEventId().equals(eventId)).findFirst();
        if (result.isPresent() == false) {
            Assert.fail(String.format("Alarm trigger not found. Search for '%s', but only found the following ids: %s", eventId, triggers.stream().map(t -> t.getEventId()).collect(Collectors.joining(", "))));
            return null;
        }
        return result.get();
    }

    protected AlarmTrigger findTriggerByAlarm(Integer alarmId, List<AlarmTrigger> triggers) {
        for (AlarmTrigger trigger : triggers) {
            if (trigger.getAlarmId().equals(String.valueOf(alarmId))) {
                return trigger;
            }
        }
        Assert.fail(String.format("Alarm trigger not found. Search for alarm id '%s', but only found the following alarm ids: %s", alarmId, triggers.stream().map(t -> t.getAlarmId()).collect(Collectors.joining(", "))));
        return null;
    }

}
