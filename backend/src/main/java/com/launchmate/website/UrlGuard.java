package com.launchmate.website;

import com.launchmate.domain.DomainNormaliser;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Server-side request forgery protection. Two layers:
 * <ol>
 *   <li>{@link #validate} rejects bad addresses up front, so a user gets a clear message.</li>
 *   <li>{@link #resolvePublic} is what the HTTP client uses to resolve names at connection time,
 *       so the address that is checked is the very address that is connected to. This closes the
 *       gap that DNS-rebinding attacks rely on, and it also covers every redirect hop.</li>
 * </ol>
 */
public final class UrlGuard {

    /** Name resolution, replaceable in tests. */
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static final int MAX_LENGTH = 2048;

    private final Resolver resolver;
    private final Predicate<InetAddress> policy;

    public UrlGuard(Resolver resolver, Predicate<InetAddress> policy) {
        this.resolver = resolver;
        this.policy = policy;
    }

    public static UrlGuard strict() {
        return new UrlGuard(InetAddress::getAllByName, AddressPolicy::isPublic);
    }

    /** Syntax rules only; no network access. */
    public URI validateSyntax(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UrlRejectedException("Enter a website address.");
        }
        String s = raw.trim();
        if (s.length() > MAX_LENGTH) {
            throw new UrlRejectedException("The website address is too long.");
        }
        URI uri;
        try {
            uri = new URI(s);
        } catch (URISyntaxException e) {
            throw new UrlRejectedException("The website address is not valid.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UrlRejectedException("The website address must start with http:// or https://.");
        }
        if (uri.getUserInfo() != null) {
            throw new UrlRejectedException("The website address must not contain a username or password.");
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new UrlRejectedException("Only the standard web ports (80 and 443) are supported.");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new UrlRejectedException("The website address must include a domain name.");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || host.endsWith(".home.arpa")) {
            throw new UrlRejectedException("Internal and local addresses cannot be audited.");
        }
        // A real hostname has an alphabetic ending (TLD). That also rules out every IP literal form,
        // including decimal, hex and octal spellings such as http://2130706433/.
        if (DomainNormaliser.normalise(host).isEmpty()) {
            throw new UrlRejectedException("Use the site's domain name (for example example.com), not an IP address.");
        }
        return uri;
    }

    /** Syntax rules plus a DNS lookup, so unsafe destinations fail fast with a clear message. */
    public URI validate(String raw) {
        URI uri = validateSyntax(raw);
        try {
            resolvePublic(uri.getHost());
        } catch (UnknownHostException e) {
            throw new UrlRejectedException(e.getMessage() != null && e.getMessage().startsWith("blocked")
                    ? "This address points to a private or internal network and cannot be audited."
                    : "This website address could not be found.");
        }
        return uri;
    }

    /** Resolves a name and refuses it if any returned address is not a public address. */
    public InetAddress[] resolvePublic(String host) throws UnknownHostException {
        InetAddress[] all = resolver.resolve(host);
        if (all == null || all.length == 0) {
            throw new UnknownHostException("not found");
        }
        for (InetAddress a : all) {
            if (!policy.test(a)) {
                throw new UnknownHostException("blocked: non-public address");
            }
        }
        return all;
    }
}
