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

package com.openexchange.ajax.multifactor;

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.config.util.ChangePropertiesRequest;
import com.openexchange.ajax.framework.config.util.ChangePropertiesResponse;
import com.openexchange.ajax.writer.ResponseWriter;
import com.openexchange.exception.OXException;
import com.openexchange.multifactor.MultifactorProperties;
import com.openexchange.test.common.test.TestClassConfig;
import com.openexchange.testing.httpclient.invoker.ApiClient;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.MultifactorDevice;
import com.openexchange.testing.httpclient.models.MultifactorStartRegistrationResponseData;
import com.openexchange.testing.httpclient.modules.MultifactorApi;
import com.openexchange.testing.restclient.models.MultifactorDeviceData;
import com.openexchange.testing.restclient.modules.AdminApi;

/**
 * {@link MultifactorRESTTests} contains test against the multifactor REST endpoint
 *
 * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
 * @since v7.10.2
 */
public class MultifactorRESTTests extends AbstractMultifactorTest {

    private final String testProviderName = "SMS";
    private final String testSMSToken = "0815";
    private int contextId;
    private int userId;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Override
    public TestClassConfig getTestConfig() {
        return TestClassConfig.builder().createApiClient().createAjaxClient().withUserPerContext(2).build();
    }

    private MultifactorDevice registerTestDevice(MultifactorApi api) throws ApiException {
        final String randomDeviceName = "My test device " + UUID.randomUUID().toString();
        final String randomPhoneNumber = "+49" + new Random().nextInt(9999999);
        final Boolean isBackupDevice = Boolean.FALSE;
        final MultifactorStartRegistrationResponseData response = startRegistration(api, testProviderName, randomDeviceName, randomPhoneNumber, isBackupDevice);
        MultifactorDevice newDevice = finishRegistration(api, testProviderName, response.getDeviceId(), testSMSToken, null, null);
        assertThat("The new device must have the correct name", newDevice.getName(), is(randomDeviceName));
        assertThat("The new device must have the correct 'backup' state", newDevice.getBackup(), is(isBackupDevice));
        return newDevice;
    }

    @Override
    protected Map<String, String> getNeededConfigurations() {
        HashMap<String, String> result = new HashMap<>();
        result.put("com.openexchange.multifactor.demo", Boolean.TRUE.toString());
        result.put(MultifactorProperties.PREFIX + "sms.enabled", Boolean.TRUE.toString());
        return result;
    }

    @Override
    protected String getReloadables() {
        return "DemoAwareTokenCreationStrategy,MultifactorSMSProvider";
    }

    private Collection<MultifactorDevice> registerTestDevices(MultifactorApi api, int count) throws ApiException {
        ArrayList<MultifactorDevice> devicesCreated = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            devicesCreated.add(registerTestDevice(api));
        }
        return devicesCreated;
    }

    private AdminApi createAdminAPIWithoutCredentials() {
        return createAdminAPIWithCredentials(null, null);
    }

    private AdminApi createAdminAPIWithCredentials(String username, String password) {
        com.openexchange.testing.restclient.invoker.ApiClient adminRestClient = new com.openexchange.testing.restclient.invoker.ApiClient();
        adminRestClient.setBasePath(getRestBasePath());
        if (username != null && password != null) {
            String authorizationHeaderValue = "Basic " + Base64.encodeBase64String((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            adminRestClient.addDefaultHeader(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
        }
        return new AdminApi(adminRestClient);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Get context and user ID
        contextId = super.getClient().getValues().getContextId();
        userId = super.getClient().getValues().getUserId();
    }

    /**
     * Tests to list a user's multifactor devices using the administrative REST API
     *
     * @throws Exception
     */
    @Test
    public void testListDevices() throws Exception {
        //registering a test device
        MultifactorDevice registeredDevice = registerTestDevice(MultifactorApi());

        //Get the devices via the REST API
        List<MultifactorDeviceData> devices = getAdminApi().multifactorGetDevices(I(contextId), I(userId));
        assertThat("There must be exactly one multifactor device registered", I(devices.size()), is(I(1)));
        MultifactorDeviceData device = devices.get(0);
        assertThat(device.getProviderName(), is(testProviderName));
        assertThat(device.getName(), is(registeredDevice.getName()));
        assertThat(device.getId(), is(registeredDevice.getId()));
        assertThat(device.getEnabled(), is(B(true)));
        assertThat(device.getBackup(), is(B(false)));
    }

    /**
     * Sets up a configuration for given client
     *
     * @param client The client
     * @return The old/existing configuration before changed
     * @throws OXException
     * @throws IOException
     * @throws JSONException
     */
    private JSONObject setUpConfigForClient(AJAXClient client) throws OXException, IOException, JSONException {
        Map<String, String> map = getNeededConfigurations();
        ChangePropertiesRequest req = new ChangePropertiesRequest(map, getScope(), getReloadables());
        ChangePropertiesResponse response = client.execute(req);
        return ResponseWriter.getJSON(response.getResponse()).getJSONObject("data");
    }

    /**
     * Restores a configuration for a given client
     *
     * @param client The client
     * @param configToRestore The configuration to restore
     * @throws OXException
     * @throws IOException
     * @throws JSONException
     */
    private void restoreConfigForClient(AJAXClient client, JSONObject configToRestore) throws OXException, IOException, JSONException {
        Map<String, Object> map = configToRestore.asMap();
        Map<String, String> newMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == JSONObject.NULL) {
                value = null;
            }
            newMap.put(entry.getKey(), (String) value);
        }
        if (map.isEmpty()) {
            return;
        }
        ChangePropertiesRequest req = new ChangePropertiesRequest(newMap, getScope(), getReloadables());
        client.execute(req);
    }

    /**
     * Tests to delete all user's multifactor devices using the administrative REST API
     *
     * @throws Exception
     */
    @Test
    public void testDeleteAllDevices() throws Exception {
        //Create a bunch of test devices for a user
        final int numberOfDevices = 4;
        Collection<MultifactorDevice> devices = registerTestDevices(MultifactorApi(), numberOfDevices);
        assertThat(I(devices.size()), is(I(numberOfDevices)));

        ApiClient apiClient2 = testUser2.getApiClient();
        MultifactorApi api2 = new MultifactorApi(apiClient2);
        boolean createdForUser2 = false;

        JSONObject oldClient2Config = null;
        try {
            //The 2nd user needs the same configuration applied (SMS provider and DEMO mode)
            oldClient2Config = setUpConfigForClient(testUser2.getAjaxClient());

            //Create a test device for another user
            registerTestDevice(api2);
            createdForUser2 = true;

            //Delete all devices for the 1st user
            getAdminApi().multifactorDeleteDevices(I(contextId), I(userId));
            //..all should be gone
            List<MultifactorDeviceData> devicesForUser = getAdminApi().multifactorGetDevices(I(contextId), I(userId));
            assertThat(devicesForUser, is(empty()));

            //The device for the second user must still be present
            List<MultifactorDeviceData> devicesForUser2 = getAdminApi().multifactorGetDevices(I(testUser2.getAjaxClient().getValues().getContextId()), I(testUser2.getAjaxClient().getValues().getUserId()));
            assertThat(I(devicesForUser2.size()), is(I(1)));
        } finally {
            if (createdForUser2) {
                //Cleanup the devices of the 2nd user
                getAdminApi().multifactorDeleteDevices(I(testUser2.getAjaxClient().getValues().getContextId()), I(testUser2.getAjaxClient().getValues().getUserId()));
                List<MultifactorDeviceData> devicesForUser2 = getAdminApi().multifactorGetDevices(I(testUser2.getAjaxClient().getValues().getContextId()), I(testUser2.getAjaxClient().getValues().getUserId()));
                assertThat(devicesForUser2, is(empty()));
            }
            //Restoring the configuration for the 2nd user
            if (oldClient2Config != null) {
                restoreConfigForClient(testUser2.getAjaxClient(), oldClient2Config);
            }
        }
    }

    /**
     * Tests to delete a single multifactor device using the administrative REST API
     */
    @Test
    public void testDelteSingleDevice() throws Exception {
        //Create a bunch of test devices for a user
        final int numberOfDevices = 4;
        Collection<MultifactorDevice> devices = registerTestDevices(MultifactorApi(), numberOfDevices);
        assertThat(I(devices.size()), is(I(numberOfDevices)));
        MultifactorDevice firstDevice = devices.iterator().next();

        //Remove the first device
        getAdminApi().multifactorDeleteDevice(I(contextId), I(userId), testProviderName, firstDevice.getId());

        //Ensure that the first device is gone
        List<MultifactorDeviceData> devicesLeft = getAdminApi().multifactorGetDevices(I(contextId), I(userId));
        assertThat(I(devicesLeft.size()), is(I(numberOfDevices - 1)));
        assertThat(L(devicesLeft.stream().filter(d -> d.getId().equals(firstDevice.getId())).count()), is(L(0)));
    }

    /**
     * Tests that it's not possible to get devices without authentication
     */
    @Test
    public void testNotAbleToGetDevicesWithouthAuth() throws Exception {
        thrown.expect(com.openexchange.testing.restclient.invoker.ApiException.class);
        createAdminAPIWithoutCredentials().multifactorGetDevices(I(contextId), I(userId));
    }

    /**
     * Tests that it's not possible to get devices with wrong authentication
     */
    @Test
    public void testNotAbleToGetDevicesWithWrongAuth() throws Exception {
        thrown.expect(com.openexchange.testing.restclient.invoker.ApiException.class);
        createAdminAPIWithCredentials("wrong user", "wrong password").multifactorGetDevices(I(contextId), I(userId));
    }

    /**
     * Tests that it's not possible to delete devices without authentication
     */
    @Test
    public void testNotAbleToDeleteDevicesWithouthAuth() throws Exception {
        thrown.expect(com.openexchange.testing.restclient.invoker.ApiException.class);
        createAdminAPIWithoutCredentials().multifactorDeleteDevices(I(contextId), I(userId));
    }

    /**
     * Tests that it's not possible to delete devices with wrong authentication
     */
    @Test
    public void testNotAbleToDeleteDevicesWithWrongAuth() throws Exception {
        thrown.expect(com.openexchange.testing.restclient.invoker.ApiException.class);
        createAdminAPIWithCredentials("wrong user", "wrong password").multifactorDeleteDevices(I(contextId), I(userId));
    }
}
