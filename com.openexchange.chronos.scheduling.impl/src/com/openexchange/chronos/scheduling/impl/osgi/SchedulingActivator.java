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

package com.openexchange.chronos.scheduling.impl.osgi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.ical.ICalService;
import com.openexchange.chronos.itip.IncomingSchedulingMailFactory;
import com.openexchange.chronos.scheduling.SchedulingBroker;
import com.openexchange.chronos.scheduling.TransportProvider;
import com.openexchange.chronos.scheduling.impl.SchedulingBrokerImpl;
import com.openexchange.chronos.scheduling.impl.incoming.IncomingSchedulingMailFactoryImpl;
import com.openexchange.config.ConfigurationService;
import com.openexchange.context.ContextService;
import com.openexchange.mail.api.crypto.CryptographicAwareMailAccessFactory;
import com.openexchange.osgi.HousekeepingActivator;

/**
 * {@link SchedulingActivator}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.3
 */
public class SchedulingActivator extends HousekeepingActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulingActivator.class);

    private SchedulingBrokerImpl broker;

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class[] { ConfigurationService.class, ContextService.class, ICalService.class };
    }

    @Override
    protected Class<?>[] getOptionalServices() {
        return new Class[] { CryptographicAwareMailAccessFactory.class };
    }

    @Override
    protected void startBundle() throws Exception {
        LOGGER.info("Starting calendar scheduling related services");

        broker = new SchedulingBrokerImpl(context, this);
        /*
         * Register service tracker
         */
        track(TransportProvider.class, broker);
        openTrackers();

        /*
         * Register broker as service
         */
        registerService(SchedulingBroker.class, broker);

        /*
         * Register factory
         */
        registerService(IncomingSchedulingMailFactory.class, new IncomingSchedulingMailFactoryImpl(this));
    }

    @Override
    protected void stopBundle() throws Exception {
        broker.close();
        unregisterService(SchedulingBroker.class);
        super.stopBundle();
    }

}
