package com.launchmate.website;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Decides whether an IP address is a normal public internet address. Everything else (loopback,
 * private, link-local including cloud metadata at 169.254.169.254, carrier-grade NAT, documentation
 * and reserved ranges, multicast, and IPv6 forms that embed an IPv4 address) is refused.
 */
public final class AddressPolicy {

    private AddressPolicy() {}

    public static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        return b.length == 4 ? !blockedV4(b) : !blockedV6(b);
    }

    private static boolean blockedV4(byte[] b) {
        int o0 = b[0] & 0xff;
        int o1 = b[1] & 0xff;
        int o2 = b[2] & 0xff;
        return o0 == 0                                        // 0.0.0.0/8
                || (o0 == 100 && o1 >= 64 && o1 <= 127)       // 100.64.0.0/10 carrier-grade NAT
                || (o0 == 192 && o1 == 0 && o2 == 0)          // 192.0.0.0/24
                || (o0 == 192 && o1 == 0 && o2 == 2)          // 192.0.2.0/24 documentation
                || (o0 == 192 && o1 == 88 && o2 == 99)        // 6to4 relay anycast
                || (o0 == 198 && (o1 == 18 || o1 == 19))      // 198.18.0.0/15 benchmarking
                || (o0 == 198 && o1 == 51 && o2 == 100)       // documentation
                || (o0 == 203 && o1 == 0 && o2 == 113)        // documentation
                || o0 >= 240;                                 // 240.0.0.0/4 reserved and broadcast
    }

    private static boolean blockedV6(byte[] b) {
        // An IPv6 address that wraps an IPv4 address is judged by the IPv4 rules.
        if (zeros(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
            return !isPublicV4(b[12], b[13], b[14], b[15]);
        }
        if (zeros(b, 0, 12)) {
            return true;                                      // ::/96 IPv4-compatible (deprecated)
        }
        int b0 = b[0] & 0xff;
        int b1 = b[1] & 0xff;
        int b2 = b[2] & 0xff;
        int b3 = b[3] & 0xff;
        return (b0 & 0xfe) == 0xfc                            // fc00::/7 unique local
                || (b0 == 0x00 && b1 == 0x64 && b2 == 0xff && b3 == 0x9b)   // 64:ff9b::/32 NAT64
                || (b0 == 0x20 && b1 == 0x02)                 // 2002::/16 6to4
                || (b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00)   // 2001::/32 Teredo
                || (b0 == 0x20 && b1 == 0x01 && b2 == 0x0d && b3 == 0xb8)   // 2001:db8::/32 documentation
                || (b0 == 0x01 && zeros(b, 1, 8));            // 100::/64 discard-only
    }

    private static boolean isPublicV4(byte a, byte b, byte c, byte d) {
        try {
            return isPublic(InetAddress.getByAddress(new byte[] {a, b, c, d}));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean zeros(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
