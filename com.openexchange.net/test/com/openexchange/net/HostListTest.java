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

package com.openexchange.net;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * {@link HostListTest}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.2
 */
public class HostListTest {

    /**
     * Initializes a new {@link HostListTest}.
     */
    public HostListTest() {
        super();
    }

    @Test
    public void testValueOf_hostlistEmpty_returnEmpty() {
        HostList hl = HostList.valueOf("");

        assertTrue(hl.equals(HostList.EMPTY));
    }

    @Test
    public void testValueOf_hostlistNull_returnEmpty() {
        HostList hl = HostList.valueOf(null);

        assertTrue(hl.equals(HostList.EMPTY));
    }

    @Test
    public void testContains_containsEmpty_returnFalse() {
        HostList hl = HostList.valueOf("");

        boolean contains = hl.contains("");

        assertFalse(contains);
    }

    @Test
    public void testContains_containsNull_returnEmpty() {
        HostList hl = HostList.valueOf(null);

        boolean contains = hl.contains((String) null);

        assertFalse(contains);
    }

    @Test
    public void testHostListv4() {
        try {
            HostList hl = HostList.valueOf("192.168.0.1, localhost, *.open-xchange.com");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("192.168.0.1"));
            assertFalse(hl.contains("127.168.32.4"));
            assertTrue(hl.contains("barfoo.open-xchange.com"));
            assertTrue(hl.contains("12.open-xchange.com"));
            assertTrue(hl.contains("localhost"));
            assertFalse(hl.contains("ox.io.com"));
            assertFalse(hl.contains("::1"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListv4_throwsException() {
        try {
            HostList.valueOf("**.open-xchange.com");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testHostListv4_throwsException2() {
        try {
            HostList.valueOf("*");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testHostListv4_throwsException3() {
        try {
            HostList.valueOf("test.*.com");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testHostListv4Ranges() {
        try {
            HostList hl = HostList.valueOf("127.0.0.1-127.255.255.255, 10.20.30.1-10.20.30.255");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertFalse(hl.contains("192.168.0.1"));
            assertFalse(hl.contains("192.168.255.255"));
            assertTrue(hl.contains("127.168.32.4"));
            assertTrue(hl.contains("10.20.30.222"));
            assertTrue(hl.contains("::1"));
            assertFalse(hl.contains("barfoo.open-xchange.com"));
            assertFalse(hl.contains("12.open-xchange.com"));
            assertFalse(hl.contains("ox.io.com"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListv4CIDRRanges() {
        try {
            HostList hl = HostList.valueOf("192.168.0.1/16");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("192.168.0.1"));
            assertTrue(hl.contains("192.168.255.255"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListv6() {
        try {
            HostList hl = HostList.valueOf("::1, FE80:0000:0000:0000:0202:B3FF:FE1E:8329, ");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("::1"));
            assertTrue(hl.contains("FE80:0000:0000:0000:0202:B3FF:FE1E:8329"));
            assertTrue(hl.contains("FE80:0:0:0:0202:B3FF:FE1E:8329"));
            assertTrue(hl.contains("FE80::0202:B3FF:FE1E:8329"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListv6SimilarityCheck() {
        //        all the same
        //        2001:cdba:0000:0000:0000:0000:3257:9652
        //        2001:cdba:0:0:0:0:3257:9652
        //        2001:cdba::3257:9652

        try {
            HostList hl = HostList.valueOf("2001:cdba:0000:0000:0000:0000:3257:9652, 2001:cdba:0:0:0:0:3257:9652");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("2001:cdba::3257:9652"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListv6SimilarityCheck2() {
        //      all the same
        //      2001:cdba:0000:0000:0000:0000:3257:9652
        //      2001:cdba:0:0:0:0:3257:9652
        //      2001:cdba::3257:9652

        try {
            HostList hl = HostList.valueOf("2001:cdba::3257:9652");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("2001:cdba:0000:0000:0000:0000:3257:9652"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListv6Ranges() {
        try {
            HostList hl = HostList.valueOf("2001:DB8::64-2001:DB8::C8");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("2001:db8:0:0:0:0:0:70"));
            assertTrue(hl.contains("2001:db8:0:0:0:0:0:7f"));

            assertFalse(hl.contains("::1"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListv6CIDRRanges() {
        try {
            HostList hl = HostList.valueOf("2001:DB1::0/120");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("2001:db1:0:0:0:0:0:0"));
            assertTrue(hl.contains("2001:db1:0:0:0:0:0:ff"));

            assertFalse(hl.contains("::1"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListFailToParse() {
        try {
            String hostList = "www.google.*";
            HostList.valueOf(hostList);
            fail("Host list did not fail to parse: " + hostList);
        } catch (@SuppressWarnings("unused") IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testHostListFailToParse2() {
        try {
            String hostList = "*.open-xchange.*";
            HostList.valueOf(hostList);
            fail("Host list did not fail to parse: " + hostList);
        } catch (@SuppressWarnings("unused") IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testForBug56558() {
        try {
            HostList hl = HostList.valueOf("192.168.0.10");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("192.168.10")); // 16 bit
            assertTrue(hl.contains("192.11010058")); // 24 bit
            assertTrue(hl.contains("3232235530")); // 32 bit

            // Octal
            assertTrue(hl.contains("0300.0250.00.012")); // 8 bit
            assertTrue(hl.contains("0300.0250.012")); // 16 bit
            assertTrue(hl.contains("0300.052000012")); // 24 bit
            assertTrue(hl.contains("030052000012")); // 32 bit

            // Hexadecimal
            assertTrue(hl.contains("0xc0.0xa8.0x0.0xa")); // 8 bit
            assertTrue(hl.contains("0xc0.0xa8.0xa")); // 16 bit
            assertTrue(hl.contains("0xc0.0xa8000a")); // 24 bit
            assertTrue(hl.contains("0xc0a8000a")); // 32 bit
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testForBug56706() {
        try {
            HostList hl = HostList.valueOf("127.0.0.1-127.255.255.255,localhost,192.168.0.10");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            assertTrue(hl.contains("0")); // All IPv4
            assertTrue(hl.contains("::")); // All IPv6
            assertTrue(hl.contains("0.0.0.0")); // All IPv4
            assertTrue(hl.contains("::1")); // Localhost IPv6

            assertTrue(hl.contains("0000")); // All IPv4
            assertTrue(hl.contains("00000000")); // All IPv4 (Leading zeros)
            assertTrue(hl.contains("0:0:0:0:0:FFFF:7F00:0001")); // IPv4 mapped IPv6 address
            assertTrue(hl.contains("0177.00.00.01")); // 8-Bit Octal conversion
            assertTrue(hl.contains("017700000001")); // 32-Bit Octal conversion
            assertTrue(hl.contains("0x7f000001")); // 32-Bit Hex conversion

            // Trial bypasses for IPv4 addresses (192.168.0.10 in this case)
            assertTrue(hl.contains("0:0:0:0:0:FFFF:C0A8:000A")); // IPv4 mapped IPv6 address
            assertTrue(hl.contains("030052000012")); // 32-Bit Hex conversion
            assertTrue(hl.contains("0xc0a8000a")); // 32-Bit Octal conversion
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testForBug67980() {
        try {
            HostList hl = HostList.valueOf("127.0.0.1-127.255.255.255,localhost");

            String shl = hl.toString();
            assertNotNull("Host-list's string representation is null", shl);

            /*-
             * xip.io and nip.io is a magic domain name that provides wildcard DNS
             * for any IP address. Say your LAN IP address is 10.0.0.1.
             * Using xip.io,
             *
             *          10.0.0.1.xip.io   resolves to   10.0.0.1
             *      www.10.0.0.1.xip.io   resolves to   10.0.0.1
             *   mysite.10.0.0.1.xip.io   resolves to   10.0.0.1
             *  foo.bar.10.0.0.1.xip.io   resolves to   10.0.0.1
             */

            assertTrue(hl.contains("127.0.0.1.nip.io")); // All IPv4
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

}
