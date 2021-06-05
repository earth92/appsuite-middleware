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

package com.openexchange.multifactor.provider.totp.impl;

import static com.openexchange.java.Autoboxing.I;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
import com.openexchange.multifactor.provider.totp.TotpChallenge;
import com.openexchange.multifactor.provider.totp.TotpCore;
import com.openexchange.multifactor.provider.totp.TotpMultifactorDevice;
import com.openexchange.multifactor.provider.totp.TotpRegister;
import com.openexchange.multifactor.provider.totp.storage.TotpMultifactorDeviceStorage;
import com.openexchange.multifactor.storage.impl.MemoryMultifactorDeviceStorage;
import com.openexchange.multifactor.util.DeviceNaming;
import com.openexchange.multifactor.util.MultifactorFormatter;

/**
 * Provider for TOTP authentication like Google Authenticator
 *
 * @author <a href="mailto:greg.hill@open-xchange.com">Greg Hill</a>
 * @since v7.10.2
 */
public class MultifactorTotpProvider implements MultifactorProvider {

    public static final String NAME = "TOTP";

    private final TotpMultifactorDeviceStorage deviceStorage;
    private final MemoryMultifactorDeviceStorage<TotpMultifactorDevice> pendingDeviceStorage;
    private final LeanConfigurationService configurationService;
    private final TotpRegister challengeFactory = new TotpRegister();

    /**
     * Initializes a new {@link MultifactorTotpProvider}.
     *
     * @param configurationService The {@link LeanConfigurationService} to use
     * @param deviceStorage The {@link TotpMultifactorDeviceStorage} to use
     * @param pendingDeviceStorage The storage used for storing pending devices
     */
    public MultifactorTotpProvider(LeanConfigurationService configurationService,
                                   final TotpMultifactorDeviceStorage deviceStorage,
                                   final MemoryMultifactorDeviceStorage<TotpMultifactorDevice> pendingDeviceStorage) {
        this.configurationService = Objects.requireNonNull(configurationService, "configurationService must not be null");
        this.deviceStorage = Objects.requireNonNull(deviceStorage, "deviceStorage must not be null");
        this.pendingDeviceStorage = Objects.requireNonNull(pendingDeviceStorage, "pendingDeviceStorage must not be null");
    }

    /**
     * Internal method to check if a device is registered
     *
     * @param multifactorRequest The {@link MultifactorRequest}
     * @param deviceId The device id to check
     * @return <code>true</code>, if a device with the given device ID is registered, <code>false</code> otherwise
     * @throws OXException
     */
    private boolean isDeviceRegistered(MultifactorRequest multifactorRequest, String deviceId) throws OXException {
        return getDevice(multifactorRequest, deviceId).isPresent();
    }

    /**
     * Returns the count of devices registered to the user
     *
     * @param multifactorRequest The {@link MultifactorRequest}
     * @return The count of registered devices
     * @throws OXException
     */
    private int getCount(MultifactorRequest multifactorRequest) throws OXException {
        return deviceStorage.getCount(multifactorRequest.getContextId(), multifactorRequest.getUserId());
    }

    /**
     * Gets a translated, default name for the device
     *
     * @param multifactorRequest The {@link MultifactorRequest}
     * @return A translated default device name
     * @throws OXException
     */
    private String getDefaultName(MultifactorRequest multifactorRequest) throws OXException {
        final int count = getCount(multifactorRequest) + 1;
        final String name = StringHelper.valueOf(multifactorRequest.getLocale()).getString(TotpStrings.AUTHENTICATOR_DEFAULT_NAME);
        return String.format(name, I(count));
    }

    private void doAuthenticationInternal(ChallengeAnswer answer, TotpMultifactorDevice device) throws OXException {
        final String sharedSecret = device.getSharedSecret();
        String inputCode = answer.requireField(TotpField.SECRET).toString();
        if (!Strings.isEmpty(inputCode)) {
            inputCode = MultifactorFormatter.removeWhiteSpaces(inputCode);
            if (TotpCore.verify(sharedSecret, inputCode)) {
                return;
            }
            throw MultifactorExceptionCodes.AUTHENTICATION_FAILED.create();
        }
        throw MultifactorExceptionCodes.MISSING_PARAMETER.create(TotpMultifactorDevice.SECRET_CODE_PARAMETER);
    }

    private static String newUid() {
        return UUIDs.getUnformattedString(UUID.randomUUID());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEnabled(MultifactorRequest multifactorRequest) {
        return configurationService.getBooleanProperty(multifactorRequest.getUserId(), multifactorRequest.getContextId(), MultifactorTotpProperty.enabled);
    }

    @Override
    public Collection<? extends MultifactorDevice> getDevices(MultifactorRequest multifactorRequest) throws OXException {
        //Wrap in order to remove all, possibly, security related secrets
        return deviceStorage.getDevices(multifactorRequest.getContextId(), multifactorRequest.getUserId())
            .stream().map(d -> new ParameterlessMultifactorDevice(d)).collect(Collectors.toList());
    }

    @Override
    public Collection<MultifactorDevice> getEnabledDevices(MultifactorRequest multifactorRequest) throws OXException {
        return getDevices(multifactorRequest).stream().filter(d -> d.isEnabled().booleanValue()).collect(Collectors.toList());
    }

    @Override
    public Optional<? extends MultifactorDevice> getDevice(MultifactorRequest multifactorRequest, String deviceId) throws OXException {
        Optional<TotpMultifactorDevice> device = deviceStorage.getDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId);
        //Wrap in order to remove all, possibly, security related secrets
        return device.map(d -> new ParameterlessMultifactorDevice(d));
    }

    @Override
    public RegistrationChallenge startRegistration(MultifactorRequest multifactorRequest, MultifactorDevice inputDevice) throws OXException {

        //Get the name from the request if present, or use a default name otherwise
        DeviceNaming.applyName(inputDevice, () -> getDefaultName(multifactorRequest));
        inputDevice.setId(newUid());
        TotpChallenge challenge = challengeFactory.createChallenge(multifactorRequest, inputDevice, configurationService.getIntProperty(MultifactorTotpProperty.maximumQRCodeLength));
        final TotpMultifactorDevice newDevice = new TotpMultifactorDevice(challenge.getDeviceId(), inputDevice.getName(), challenge.getSecret());

        // We just create a new TOTP device with the input device taken as basis
        newDevice.setIsTrustedApplicationDevice(false);

        // Add pending registration
        pendingDeviceStorage.registerDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), newDevice);

        return challenge;
    }

    @Override
    public MultifactorDevice finishRegistration(MultifactorRequest multifactorRequest, String deviceId, ChallengeAnswer answer) throws OXException {
        if (!isDeviceRegistered(multifactorRequest, deviceId)) {
            final Optional<TotpMultifactorDevice> pendingDevice = pendingDeviceStorage.getDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId);
            if (pendingDevice.isPresent()) {
                doAuthenticationInternal(answer, pendingDevice.get());

                //Enable the device
                pendingDevice.get().enable(Boolean.TRUE);

                try {
                    //Add the device
                    deviceStorage.registerDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), pendingDevice.get());
                } catch(Exception e) {
                    pendingDevice.get().enable(Boolean.FALSE);
                    throw e;
                }

                //Remove the device from the pending registrations
                pendingDeviceStorage.unregisterDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId);

                return pendingDevice.get();
            }
            throw MultifactorExceptionCodes.REGISTRATION_FAILED.create();
        }
        throw MultifactorExceptionCodes.DEVICE_ALREADY_REGISTERED.create();
    }

    @Override
    public void deleteRegistration(MultifactorRequest multifactorRequest, String deviceId) throws OXException {
        if (deviceStorage.unregisterDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId)) {
           return;
        }
        throw MultifactorExceptionCodes.DEVICE_REMOVAL_FAILED.create();
    }

    @Override
    public boolean deleteRegistrations(int contextId, int userId) throws OXException {
        return deviceStorage.deleteAllForUser(userId, contextId);
    }

    @Override
    public boolean deleteRegistrations(int contextId) throws OXException {
        return deviceStorage.deleteAllForContext(contextId);
    }

    @Override
    public Challenge beginAuthentication(MultifactorRequest multifactorRequest, String deviceId) {
        // return empty challenge
        return new Challenge() { /** empty **/};
    }

    @Override
    public void doAuthentication(MultifactorRequest multifactorRequest, String deviceId, ChallengeAnswer answer) throws OXException {
        final Optional<TotpMultifactorDevice> device = deviceStorage.getDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), deviceId);
        if (!device.isPresent()) {
            throw MultifactorExceptionCodes.UNKNOWN_DEVICE_ID.create();  // No devices
        }
        doAuthenticationInternal(answer, device.get());
    }

    @Override
    public boolean isTrustedApplicationProvider() {
        return true;
    }

    @Override
    public MultifactorDevice renameDevice(MultifactorRequest multifactorRequest, MultifactorDevice inputDevice) throws OXException {
        if (inputDevice.getName() == null) {
            throw MultifactorExceptionCodes.MISSING_PARAMETER.create("Name missing or invalid");
        }
        if (deviceStorage.renameDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), inputDevice.getId(), inputDevice.getName())) {
            return deviceStorage.getDevice(multifactorRequest.getContextId(), multifactorRequest.getUserId(), inputDevice.getId()).get();
        }
        throw MultifactorExceptionCodes.UNKNOWN_DEVICE_ID.create();
    }
}
