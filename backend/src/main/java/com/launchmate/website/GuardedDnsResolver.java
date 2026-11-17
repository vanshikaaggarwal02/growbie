package com.launchmate.website;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;

/** Plugs {@link UrlGuard} into the HTTP client, so every connection (and redirect) is checked. */
final class GuardedDnsResolver implements DnsResolver {

    private final UrlGuard guard;

    GuardedDnsResolver(UrlGuard guard) {
        this.guard = guard;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return guard.resolvePublic(host);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return host;
    }
}
