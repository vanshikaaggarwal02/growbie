package com.launchmate.website;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Facts about the business's own domain that can be measured directly. Anything that needs registration
 * records (age, expiry) or certificate details is listed as unavailable and never guessed.
 */
public final class DomainHealth {

    /** status is PASS, FAIL, INFO or UNAVAILABLE. */
    public record Fact(String key, String label, String status, String detail, String source) {}

    private static final String OBSERVED = "OBSERVED_PUBLIC_DATA";

    private DomainHealth() {}

    public static List<Fact> measure(URI entered, URI landed, SafeFetcher fetcher) {
        List<Fact> out = new ArrayList<>();
        String host = entered.getHost() == null ? "" : entered.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || !host.matches(".*[a-z].*") || !host.contains(".")) {
            out.add(new Fact("DOMAIN", "A domain name", "UNAVAILABLE",
                    "The address is not a domain name, so there is no domain to assess.", OBSERVED));
            return out;
        }
        String apex = host.startsWith("www.") ? host.substring(4) : host;
        out.add(new Fact("EXTENSION", "Domain extension", "INFO", "." + host.substring(host.lastIndexOf('.') + 1), OBSERVED));

        out.add(resolves("DNS_APEX", "The name resolves", apex));
        if (wwwApplies(apex)) {
            out.add(resolves("DNS_WWW", "The www address resolves", "www." + apex));
        }

        out.add("https".equalsIgnoreCase(landed.getScheme())
                ? new Fact("HTTPS", "Served over HTTPS", "PASS", "The homepage loads over HTTPS.", OBSERVED)
                : new Fact("HTTPS", "Served over HTTPS", "FAIL", "The homepage loads over plain HTTP.", OBSERVED));

        String landedHost = landed.getHost() == null ? "" : landed.getHost().toLowerCase(Locale.ROOT);
        out.add(landedHost.equals(host)
                ? new Fact("LANDING_HOST", "The address you gave is where visitors land", "PASS",
                        "Visitors who type " + host + " stay on " + host + ".", OBSERVED)
                : new Fact("LANDING_HOST", "The address you gave is where visitors land", "INFO",
                        "Visitors who type " + host + " are sent to " + landedHost + ".", OBSERVED));

        try {
            SafeFetcher.Response r = fetcher.get(URI.create("http://" + apex + "/"), 64_000);
            String loc = r.location() == null ? "" : r.location();
            if (r.status() >= 300 && r.status() < 400 && loc.regionMatches(true, 0, "https://", 0, 8)) {
                out.add(new Fact("HTTP_REDIRECT", "http:// redirects to https://", "PASS",
                        "Plain http:// visitors are sent to the secure address.", OBSERVED));
            } else if (r.status() == 200) {
                out.add(new Fact("HTTP_REDIRECT", "http:// redirects to https://", "FAIL",
                        "The plain http:// address answers without moving visitors to https://.", OBSERVED));
            } else {
                out.add(new Fact("HTTP_REDIRECT", "http:// redirects to https://", "INFO",
                        "The plain http:// address answered with HTTP " + r.status() + ".", OBSERVED));
            }
        } catch (IOException | RuntimeException e) {
            out.add(new Fact("HTTP_REDIRECT", "http:// redirects to https://", "UNAVAILABLE",
                    "The plain http:// address could not be tested.", OBSERVED));
        }

        out.add(new Fact("REGISTRATION_AGE", "How long the domain has been registered", "UNAVAILABLE",
                "Needs registration records (WHOIS or RDAP), which Growbie doesn't read, so no age is shown.", "UNAVAILABLE"));
        out.add(new Fact("EXPIRY", "When the registration expires", "UNAVAILABLE",
                "Needs registration records. Check it in your registrar account.", "UNAVAILABLE"));
        out.add(new Fact("CERT_EXPIRY", "When the HTTPS certificate expires", "UNAVAILABLE",
                "Not read by this audit. Your hosting provider can show it.", "UNAVAILABLE"));
        return out;
    }

    /** A www. address only makes sense for a registered domain such as example.com or example.co.in, not a subdomain. */
    static boolean wwwApplies(String apex) {
        String[] labels = apex.split("\\.");
        return labels.length == 2
                || (labels.length == 3 && java.util.Set.of("co", "com", "org", "net", "gov", "ac", "edu").contains(labels[1]));
    }

    private static Fact resolves(String key, String label, String name) {
        try {
            int n = InetAddress.getAllByName(name).length;
            return new Fact(key, label, "PASS", name + " resolves to " + n + " address" + (n == 1 ? "" : "es") + ".", OBSERVED);
        } catch (UnknownHostException e) {
            return new Fact(key, label, "FAIL", name + " does not resolve to an address.", OBSERVED);
        } catch (RuntimeException e) {
            return new Fact(key, label, "UNAVAILABLE", name + " could not be looked up.", OBSERVED);
        }
    }
}
