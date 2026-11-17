package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UrlGuardTest {

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Names resolve to fixed addresses, and anything else does not exist. */
    private static UrlGuard guard() {
        return new UrlGuard(host -> switch (host) {
            case "example.com", "www.example.com" -> new InetAddress[] {ip("93.184.216.34")};
            case "internal.example.org" -> new InetAddress[] {ip("10.0.0.5")};
            case "mixed.example.org" -> new InetAddress[] {ip("93.184.216.34"), ip("192.168.1.10")};
            case "metadata.example.org" -> new InetAddress[] {ip("169.254.169.254")};
            default -> throw new UnknownHostException(host);
        }, AddressPolicy::isPublic);
    }

    private static void assertRejected(String url) {
        assertThrows(UrlRejectedException.class, () -> guard().validateSyntax(url), "should reject: " + url);
    }

    @Test
    void rejectsBadSchemesCredentialsPortsAndLocalNames() {
        for (String bad : new String[] {
                "", "   ", "ftp://example.com", "file:///etc/passwd", "javascript:alert(1)", "gopher://example.com",
                "http://user:secret@example.com", "http://example.com:8080", "http://example.com:22",
                "http://localhost", "http://app.localhost", "http://printer.local", "http://db.internal",
                "http://intranet", "http://example.com/" + "a".repeat(2100)}) {
            assertRejected(bad);
        }
    }

    @Test
    void rejectsEveryIpAddressSpelling() {
        for (String bad : new String[] {
                "http://127.0.0.1", "http://10.0.0.1/admin", "http://169.254.169.254/latest/meta-data/",
                "http://2130706433/", "http://0x7f.1/", "http://0177.0.0.1/", "http://[::1]/", "http://[fe80::1]/",
                "http://[::ffff:127.0.0.1]/", "http://8.8.8.8/"}) {
            assertRejected(bad);
        }
    }

    @Test
    void acceptsOrdinaryWebAddresses() {
        for (String ok : new String[] {
                "https://example.com", "https://www.Example.com/path?x=1#top", "http://example.com:80/",
                "https://example.com:443/"}) {
            assertDoesNotThrow(() -> guard().validateSyntax(ok), ok);
        }
        assertEquals("example.com", guard().validateSyntax("https://Example.com/a").getHost().toLowerCase());
    }

    @Test
    void refusesNamesThatPointAtPrivateOrMetadataAddresses() {
        for (String url : new String[] {
                "https://internal.example.org", "https://mixed.example.org", "https://metadata.example.org"}) {
            UrlRejectedException e = assertThrows(UrlRejectedException.class, () -> guard().validate(url), url);
            assertTrue(e.getMessage().contains("private or internal"), e.getMessage());
        }
    }

    @Test
    void reportsUnknownNamesAndAcceptsPublicOnes() {
        UrlRejectedException e = assertThrows(UrlRejectedException.class,
                () -> guard().validate("https://nonexistent.example.net"));
        assertTrue(e.getMessage().contains("could not be found"), e.getMessage());

        URI ok = guard().validate("https://example.com/");
        assertEquals("example.com", ok.getHost());
    }

    @Test
    void connectTimeResolutionIsCheckedIndependentlyOfTheUpFrontCheck() {
        // A DNS-rebinding attacker answers with a public address first and a private one afterwards.
        AtomicInteger lookups = new AtomicInteger();
        UrlGuard rebinding = new UrlGuard(host ->
                new InetAddress[] {lookups.getAndIncrement() == 0 ? ip("93.184.216.34") : ip("127.0.0.1")},
                AddressPolicy::isPublic);

        assertDoesNotThrow(() -> rebinding.validate("https://rebind.example.net/"),
                "the up-front check sees only the public answer");
        UnknownHostException blocked = assertThrows(UnknownHostException.class,
                () -> rebinding.resolvePublic("rebind.example.net"),
                "the connection-time lookup must refuse the private answer");
        assertTrue(blocked.getMessage().startsWith("blocked"));
    }
}
