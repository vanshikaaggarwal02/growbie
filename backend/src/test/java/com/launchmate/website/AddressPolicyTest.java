package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class AddressPolicyTest {

    private static InetAddress ip(String literal) throws Exception {
        return InetAddress.getByName(literal); // literals only, so no DNS lookup happens
    }

    @Test
    void blocksLoopbackPrivateLinkLocalAndMetadataAddresses() throws Exception {
        for (String blocked : new String[] {
                "127.0.0.1", "127.1.2.3", "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255",
                "192.168.0.1", "192.168.255.254", "169.254.169.254", "169.254.0.1", "0.0.0.0", "0.1.2.3"}) {
            assertFalse(AddressPolicy.isPublic(ip(blocked)), blocked + " must be blocked");
        }
    }

    @Test
    void blocksSharedReservedDocumentationAndMulticastRanges() throws Exception {
        for (String blocked : new String[] {
                "100.64.0.1", "100.127.255.255", "192.0.0.1", "192.0.2.5", "198.18.0.1", "198.19.255.255",
                "198.51.100.7", "203.0.113.9", "224.0.0.1", "239.255.255.255", "240.0.0.1", "255.255.255.255"}) {
            assertFalse(AddressPolicy.isPublic(ip(blocked)), blocked + " must be blocked");
        }
    }

    @Test
    void blocksIpv6LoopbackLocalAndEmbeddedPrivateIpv4() throws Exception {
        for (String blocked : new String[] {
                "::1", "::", "fe80::1", "fc00::1", "fd12:3456:789a::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1",
                "::ffff:169.254.169.254", "64:ff9b::7f00:1", "2002:7f00:1::1", "2001:db8::1", "2001:0:4136:e378::1",
                "ff02::1"}) {
            assertFalse(AddressPolicy.isPublic(ip(blocked)), blocked + " must be blocked");
        }
    }

    @Test
    void allowsOrdinaryPublicAddressesIncludingTheEdgesOfBlockedRanges() throws Exception {
        for (String allowed : new String[] {
                "93.184.216.34", "8.8.8.8", "1.1.1.1", "172.15.255.255", "172.32.0.1", "100.63.255.255",
                "100.128.0.1", "192.0.3.1", "198.17.255.255", "198.20.0.1", "2606:4700:4700::1111",
                "2001:4860:4860::8888", "::ffff:8.8.8.8"}) {
            assertTrue(AddressPolicy.isPublic(ip(allowed)), allowed + " must be allowed");
        }
    }
}
