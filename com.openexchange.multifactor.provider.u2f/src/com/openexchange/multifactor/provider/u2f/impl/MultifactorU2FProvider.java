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

package com.openexchange.multifactor.provider.u2f.impl;

import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.java.Strings;
import com.openexchange.java.util.UUIDs;
import com.openexchange.multifactor.Challenge;
import com.openexchange.multifactor.ChallengeAnswer;
import com.openexchange.multifactor.MultifactorDevice;
import com.openexchange.multifactor.MultifactorProvider;
import com.openexchange.multifactor.MultifactorRequest;
import com.openexchange.multifactor.ParameterlessMultifactorDevice;
import com.openexchange.multifactor.RegistrationChallenge;
import com.openexchange.multifactor.exceptions.MultifactorExceptionCodes;
import com.openexchange.multifactor.provider.u2f.storage.U2FMultifactorDeviceStorage;
import com.openexchange.multifactor.storage.MultifactorTokenStorage;
import com.openexchange.multifactor.storage.impl.MemoryMultifactorDeviceStorage;
import com.openexchange.multifactor.util.DeviceNaming;
import com.yubico.u2f.U2F;
import com.yubico.u2f.data.DeviceRegistration;
import com.yubico.u2f.data.messages.RegisterRequestData;
import com.yubico.u2f.data.messages.RegisterResponse;
import com.yubico.u2f.data.messages.SignRequestData;
import com.yubico.u2f.data.messages.SignResponse;
import com.yubico.u2f.exceptions.NoEligibleDevicesException;
import com.yubico.u2f.exceptions.U2fAuthenticationException;
import com.yubico.u2f.exceptions.U2fBadConfigurationException;
import com.yubico.u2f.exceptions.U2fBadInputException;
import com.yubico.u2f.exceptions.U2fRegistrationException;

/**
 * {@link MultifactorU2FProvider}
 *
 * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
 * @since v7.10.2
 */
public class MultifactorU2FProvider  implements MultifactorProvider {

    static final String NAME = "U2F";


    private final MemoryMultifactorDeviceStorage<U2FMultifactorDevice> pendingStorage;
    private final U2FMultifactorDeviceStorage storage;
    private MultifactorTokenStorage<SignToken> tokenStorage;
    private final LeanConfigurationService configurationService;

    private static final Logger LOG = LoggerFactory.getLogger(MultifactorU2FProvider.class);

    /**
     * Initializes a new {@link MultifactorU2FProvider}.
     *
     * @param configurationService The configurationService to use
     * @param storage The storage for saving U2F devices
     * @param pendingStorage The storage for temporary saving pending registration devices
     * @param tokenStorage The storage for temporary saving u2f signing tokens/challenges
     */
    public MultifactorU2FProvider(LeanConfigurationService configurationService,
                                  U2FMultifactorDeviceStorage storage,
                                  MemoryMultifactorDeviceStorage<U2FMultifactorDevice> pendingStorage,
                                  MultifactorTokenStorage<SignToken> tokenStorage) {
        this.configurationService = configurationService;
        this.storage = storage;
        this.pendingStorage = pendingStorage;
        this.tokenStorage = tokenStorage;
    }

    /**
     * Internal method to check whether or not a given device is registered
     *
     * @param multifactorRequest The {@link MultifactorRequest}
     * @param deviceId The ID of the device to check
     * @return <code>true</code>, if the device with the given ID is already registered, <code>false</code> otherwise
     * @throws OXException
     */
    private boolean isDeviceRegistered(MultifactorRequest multifactorRequest, String deviceId) throws OXException {
        return getDevice(multifactorRequest, deviceId).isPresent();
    }

    /**
     * Internal method create the UTF APP ID
     *
     * @param multifactorRequest The {@link MultifactorRequest}
     * @return The UTF APP ID from configuration or from the session's host as fallback
     */
    private String getAppId(MultifactorRequest multifactorRequest) {
        final String appId = configurationService.getProperty(multifactorRequest.getUserId(), multifactorRequest.getContextId(), MultifactorU2FProperty.appId);
        if (Strings.isEmpty(appId)) {
            return "https://" + multifactorRequest.getHost();
        }
        return appId;
    }

    /**
     * Internal factory method to create a new {@link U2F} instance
     *
     * @return The new {@link U2F} instance.
     */
    private U2F createU2FInstance() {
        return new U2F();
    }

    /**
     * Internal method to perform U2F authentication
     *
     * @param multifactorRequest The multifactor request
     * @param u2fdevices a collection of devices for the given session
     * @param answer The answer to the {@link Challenge}
     * @throws OXException
     */
    private void doAuthenticationInternal(MultifactorRequest multifactorRequest, Collection<U2FMultifactorDevice> u2fdevices, ChallengeAnswer answer) throws OXException {
        final U2F u2f = createU2FInstance();
        try {

            final SignResponse signResponse = new SignResponse( answer.requireField(U2FAnswerField.CLIENT_DATA).toString(),
                                                                answer.requireField(U2FAnswerField.SIGNATURE_DATA).toString(),
                                                                answer.requireField(U2FAnswerField.KEYHANDLE).toString());

            final Optional<SignToken> signToken = tokenStorage.getAndRemove(multifactorRequest, signResponse.getRequestId());
            if (!signToken.isPresent()) {
                throw MultifactorExceptionCodes.AUTHENTICATION_FAILED.create();
            }

            final U2FMultifactorDevice u2fdevice = u2fdevices.stream().filter(device -> device.getKeyHandle().equals(signResponse.getKeyHandle())).findAny().orElse(null);

            if (u2fdevice == null) {
                throw MultifactorExceptionCodes.NO_DEVICES.create();
            }

            final DeviceRegistration deviceRegistration = new DeviceRegistration(
                u2fdevice.getKeyHandle(),
                u2fdevice.getPublicKey(),
                u2fdevice.getAttestationCertificate(),
                u2fdevice.getCounter(),
                u2fdevice.isCompromised());
            final DeviceRegistration updatedDevice = u2f.finishSignature(signToken.get().getValue(), signResponse, Arrays.asList(deviceRegistration));
            if (!storage.incrementCounter(multifactorRequest.getContextId(), multifactorRequest.getUserId(), u2fdevice.getId(), updatedDevice.getCounter())) {
                LOG.error("Unable to increment U2F counter");
            }
            return;
        } catch (U2fAuthenticationException e) {
            LOG.info(e.getMessage());
            throw MultifactorExceptionCodes.AUTHENTICATION_FAILED.create();
        } catch (U2fBadInputException e) {
            throw MultifactorExceptionCodes.UNKNOWN_ERROR.create(e.getMessage());
        }
    }

    /**
     * Internal method to get the token life-time as {@link Duration}
     *
     * @param multifactorRequest The request
     * @return The tokenLifetime as {@link Duration}
     */
    private Duration getTokenLifeTime(MultifactorRequest multifactorRequest) {
        final int tokenLifetime = configurationService.getIntProperty(multifactorRequest.getUserId(), multifactorRequest.getContextId(), MultifactorU2FProperty.tokenLifetime);
        return Duration.ofSeconds(tokenLifetime);
    }

    /**
     * Sets the token storage
     *
     * @param tokenStorage The storage for saving tokens
     * @return this
     */
    public MultifactorU2FProvider setTokenStorage(MultifactorTokenStorage<SignToken> tokenStorage) {
        this.tokenStorage  = tokenStorage;
        return this;
    }

    private static String newUid() {
        return UUIDs.getUnformattedString(UUID.randomUUID());
    }

    /**
     * Internal method to get the translated, default name for the device
     *
     * @param multifactorRequest The {@link MultifactorRequest}
     * @return A translated default name for devices
     * @throws OXException
     */
    private String getDefaultName(MultifactorRequest multifactorRequest) throws OXException {
        final int count = getCount(multifactorRequest) + 1;
        final String name = StringHelper.valueOf(multifactorRequest.getLocale()).getString(U2FStrings.AUTHENTICATION_NAME);
        return String.format(name, Integer.valueOf(count));
    }

    /**
     * Internal method to create the U2F {@link RegisterRequestData}
     *
     * @param multifactorRequest The request
     * @return The {@link RegisterRequestData}
     * @throws OXException
     * @throws U2fBadConfigurationException
     * @throws U2fBadInputException
     */
    private RegisterRequestData createU2FRegisterData(MultifactorRequest multifactorRequest) throws OXException, U2fBadConfigurationException {
        final U2F u2f = createU2FInstance();
        final ArrayList<DeviceRegistration> existing = getExistingDevices(multifactorRequest, false);
        return u2f.startRegistration(getAppId(multifactorRequest), existing);
    }

    /**
     * Get a list of available devices
     *
     * @param multifactorRequest The {@link MultifactorRequest}
     * @param throwIfEmpty Whether an exception should be thrown if the device is empty
     * @return A list of available devices
     * @throws OXException
     */
    private ArrayList<DeviceRegistration> getExistingDevices(MultifactorRequest multifactorRequest, boolean throwIfEmpty) throws OXException {
        final ArrayList<DeviceRegistration> devicesAvailable = new ArrayList<DeviceRegistration>();
        final Collection<U2FMultifactorDevice> u2fdevices = storage.getDevices(multifactorRequest.getContextId(), multifactorRequest.getUserId());
        if (throwIfEmpty && u2fdevices.isEmpty()) {
            throw MultifactorExceptionCodes.NO_DEVICES.create();
        }
        for (final U2FMultifactorDevice dev : u2fdevices) {
            final DeviceRegistration deviceRegistration = new DeviceRegistration(
                dev.getKeyHandle(),
                dev.getPublicKey(),
                dev.getAttestationCertificate(),
                dev.getCounter(),
                dev.isCompromised());
            devicesAvailable.add(deviceRegistration);
        }
        return devicesAvailable;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEnabled(MultifactorRequest multifactorRequest) {
        return configurationService.getBooleanProperty(multifactorRequest.getUserId(), multifactorRequest.getContextId(), MultifactorU2FProperty.enabled);
    }

    @Override
    public Collection<? extends MultifactorDevice> getDevices(MultifactorRequest multifactorRequest) throws OXException {
        //Wrap in order to remove all, possibly, security related secrets
        return storage.getDevices(multifactorRequest.getContextId(), multifactorRequest.getUserId())
            .stream().map(d -> new ParameterlessMultifactorDevice(d)).collect(Collectors.toList());
    }

    @Override
    public Collection<? extends MultifactorDevice> getEnabledDevices(MultifactorRequest multifactorRequest) throws OXException {
        return getDevices(multifactorRequest).stream().filter(d -> d.isEnabled() != null && d.isEnabled().booleanValue()).collect(Collectors.toList());
    }

    @Override
    public Optional<? extends MultifactorDevice> getDevice(MultifactorRequest multifactorRequest, String deviceId) throws OXException {
        //Wrap in order to remove all, possibly, security related secrets
        return storage.getDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId)
            .map(d -> new ParameterlessMultifactorDevice(d));
    }

    /**
     * Return count of devices registerd to the user
     *
     * @param multifactorRequest The request
     * @return The count of registered devices for the given request
     * @throws OXException
     */
    private int getCount(MultifactorRequest multifactorRequest) throws OXException {
        return storage.getCount(multifactorRequest.getContextId(), multifactorRequest.getUserId());
    }

    @Override
    public RegistrationChallenge startRegistration(MultifactorRequest multifactorRequest, MultifactorDevice inputDevice) throws OXException {
        try {
            //Gets the device name from the request if present, default otherwise
            DeviceNaming.applyName(inputDevice, () -> getDefaultName(multifactorRequest));

            final RegisterRequestData registerData = createU2FRegisterData(multifactorRequest);
            final U2FMultifactorDevice device = new U2FMultifactorDevice(newUid(), inputDevice.getName());
            device.setRegisterRequestData(registerData);
            pendingStorage.registerDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), device);
            return new U2FRegistrationChallenge(device.getId(), registerData);
        } catch (U2fBadConfigurationException e) {
            throw MultifactorExceptionCodes.UNKNOWN_ERROR.create(e, e.getMessage());
        }
    }

    @Override
    public MultifactorDevice finishRegistration(MultifactorRequest multifactorRequest, String deviceId, ChallengeAnswer answer) throws OXException {
        if (!isDeviceRegistered(multifactorRequest, deviceId)) {
            final Optional<U2FMultifactorDevice> pendingDevice = pendingStorage.getDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId);
            if (pendingDevice.isPresent()) {
                U2FMultifactorDevice device = pendingDevice.get();
                try {
                    final U2F u2f = createU2FInstance();
                    final RegisterRequestData registerRequestData = device.getRequestData().orElseThrow(() -> MultifactorExceptionCodes.ACTION_REQUIRES_AUTHENTICATION.create());

                    // do auth/registration
                    final DeviceRegistration deviceRegistration = u2f.finishRegistration(registerRequestData, new RegisterResponse(answer.requireField(U2FAnswerField.REGISTRATION_DATA).toString(), answer.requireField(U2FAnswerField.CLIENT_DATA).toString()));
                    device.setKeyHandle(deviceRegistration.getKeyHandle());
                    device.setPublicKey(deviceRegistration.getPublicKey());
                    device.setAttestationCertificate(deviceRegistration.getAttestationCertificate());
                    device.setCounter(deviceRegistration.getCounter());
                    device.setCompromised(deviceRegistration.isCompromised());
                    device.enable(Boolean.TRUE);

                    try {
                        // store authed/registered device
                        storage.registerDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), device);
                    } catch (Exception e) {
                        device.enable(Boolean.FALSE);
                        throw e;
                    }

                    //Remove the device from the pending registrations
                    pendingStorage.unregisterDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId);

                    return pendingDevice.get();
                } catch (U2fRegistrationException e) {
                    // Registration denied; Could not validate signature
                    throw MultifactorExceptionCodes.REGISTRATION_FAILED.create(e);
                } catch (U2fBadInputException | CertificateException e) {
                    throw MultifactorExceptionCodes.UNKNOWN_ERROR.create(e, e.getMessage());
                }
            }
            throw MultifactorExceptionCodes.REGISTRATION_FAILED.create();
        }
        throw MultifactorExceptionCodes.DEVICE_ALREADY_REGISTERED.create();
    }

    @Override
    public void deleteRegistration(MultifactorRequest multifactorRequest, String deviceId) throws OXException {
        if (storage.unregisterDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId)) {
            return;
        }
        throw MultifactorExceptionCodes.DEVICE_REMOVAL_FAILED.create();
    }

    @Override
    public boolean deleteRegistrations(int contextId, int userId) throws OXException {
        return storage.deleteAllForUser(userId, contextId);
    }

    @Override
    public boolean deleteRegistrations(int contextId) throws OXException {
        return storage.deleteAllForContext(contextId);
    }

    @Override
    public Challenge beginAuthentication(MultifactorRequest multifactoRequest, String deviceId) throws OXException {

        final U2F u2f = createU2FInstance();
        try {
            final SignRequestData startSignatureData = u2f.startSignature(getAppId(multifactoRequest), getExistingDevices(multifactoRequest, true));
            // Adding a new temp. token to the session where the device-id is key
            this.tokenStorage.add(multifactoRequest, startSignatureData.getRequestId(), new SignToken(startSignatureData, getTokenLifeTime(multifactoRequest)));

            return new Challenge() {

                @Override
                public Map<String, Object> getChallenge() throws OXException {
                    HashMap<String, Object> result = new HashMap<>(2);
                    result.put(U2FMultifactorDevice.REQUEST_ID_PARAMETER, startSignatureData.getRequestId());
                    try {
                        result.put(U2FMultifactorDevice.SIGN_REQUESTS_PARAMETER, new JSONObject(startSignatureData.toJson()).getJSONArray(U2FMultifactorDevice.SIGN_REQUESTS_PARAMETER));
                    } catch (JSONException e) {
                        throw MultifactorExceptionCodes.JSON_ERROR.create(e.getMessage());
                    }
                    return result;
                }

            };
        } catch (NoEligibleDevicesException | U2fBadConfigurationException e) {
            throw MultifactorExceptionCodes.UNKNOWN_ERROR.create(e.getCause());
        }
    }

    @Override
    public void doAuthentication(MultifactorRequest multifactorRequest, String deviceId, ChallengeAnswer answer) throws OXException {
        final Collection<U2FMultifactorDevice> u2fdevices = storage.getDevices(multifactorRequest.getContextId(), multifactorRequest.getUserId());
        if (u2fdevices == null || u2fdevices.isEmpty()) {
            throw MultifactorExceptionCodes.NO_DEVICES.create();
        }
        doAuthenticationInternal(multifactorRequest, u2fdevices, answer);
    }

    @Override
    public MultifactorDevice renameDevice(MultifactorRequest multifactorRequest, MultifactorDevice inputDevice) throws OXException {
        if (Strings.isEmpty(inputDevice.getName())) {
            throw MultifactorExceptionCodes.MISSING_PARAMETER.create("Name missing or invalid");
        }
        if (storage.renameDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), inputDevice.getId(), inputDevice.getName())) {
            return storage.getDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), inputDevice.getId()).orElseThrow(() -> MultifactorExceptionCodes.UNKNOWN_DEVICE_ID.create());
        }
        throw MultifactorExceptionCodes.UNKNOWN_DEVICE_ID.create();
    }
}
