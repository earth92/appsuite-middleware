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

package com.openexchange.admin.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * @author choeger
 * 
 */
public class NetUtilTest {

    /**
     * Test method for
     * {@link com.openexchange.admin.tools.NetUtil#isValidNetmask(java.lang.String)}.
     */
    @Test
    public final void testIsValidNetmask() {
        final String[] validMasks = new String[] { "255.255.255.0", "255.255.255.252", "255.0.0.0" };
        for (final String mask : validMasks) {
            assertTrue("Mask " + mask + " must be valid", NetUtil.isValidNetmask(mask));
        }
        final String[] invalidMasks = new String[] { "255.255.25.0", "255.255.255.242", "255.255.0.1", "42" };
        for (final String mask : invalidMasks) {
            assertFalse("Mask " + mask + " must be invalid", NetUtil.isValidNetmask(mask));
        }
    }

    /**
     * Test method for
     * {@link com.openexchange.admin.tools.NetUtil#isValidIPAddress(java.lang.String)}.
     */
    @Test
    public final void testIsValidIPAddress() {
        final String[] validIPs = new String[] { "192.168.1.2", "192.168.245.244" };
        for (final String ip : validIPs) {
            assertTrue("IP " + ip + " must be valid", NetUtil.isValidIPAddress(ip));
        }
        final String[] invalidIPs = new String[] { "292.168.1.2", "192.168.244" };
        for (final String ip : invalidIPs) {
            assertFalse("IP " + ip + " must be invalid", NetUtil.isValidIPAddress(ip));
        }
    }

    /**
     * Test method for
     * {@link com.openexchange.admin.tools.NetUtil#isValidBroadcast(java.lang.String, java.lang.String, java.lang.String)}.
     */
    @Test
    public final void testIsValidBroadcast() {
        String[] bcasts = new String[] { "192.168.0.111", "10.20.127.255" };
        String[] nets = new String[] { "192.168.0.96", "10.20.0.0" };
        String[] masks = new String[] { "255.255.255.240", "255.255.128.0" };
        for (int n = 0; n < bcasts.length; n++) {
            assertTrue("Broadcast " + bcasts[n] + " must be valid", NetUtil.isValidBroadcast(bcasts[n], nets[n], masks[n]));
        }
        bcasts = new String[] { "192.168.0.113", "10.20.127.254" };
        nets = new String[] { "192.168.0.96", "10.20.0.0" };
        masks = new String[] { "255.255.255.240", "255.255.128.0" };
        for (int n = 0; n < bcasts.length; n++) {
            assertFalse("Broadcast " + bcasts[n] + " must be invalid", NetUtil.isValidBroadcast(bcasts[n], nets[n], masks[n]));
        }
    }

    /**
     * Test method for
     * {@link com.openexchange.admin.tools.NetUtil#isValidIPNetmask(java.lang.String)}.
     */
    @Test
    public final void testIsValidIPNetmask() {
        final String[] validIPMasks = new String[] { "10.11.12.13/255.255.0.0", "172.16.13.14/8" };
        for (final String ipmask : validIPMasks) {
            assertTrue("IPMask " + ipmask + " must be valid", NetUtil.isValidIPNetmask(ipmask));
        }
        final String[] invalidIPMasks = new String[] { "10.11.12.13/2.255.0.0", "172.16.13.14/255.", "" };
        for (final String ipmask : invalidIPMasks) {
            assertFalse("IPMask " + ipmask + " must be invalid", NetUtil.isValidIPNetmask(ipmask));
        }
    }

    /**
     * Test method for
     * {@link com.openexchange.admin.tools.NetUtil#CIDR2Mask(int)}.
     */
    @Test
    public final void testCIDR2Mask() {
        final int[] cidrs = new int[] { 16, 32, 24, 30 };
        final String[] masks = new String[] { "255.255.0.0", "255.255.255.255", "255.255.255.0", "255.255.255.252", };
        for (int n = 0; n < cidrs.length; n++) {
            assertEquals("test failed for mask " + masks[n], masks[n], NetUtil.CIDR2Mask(cidrs[n]));
        }
    }

}
