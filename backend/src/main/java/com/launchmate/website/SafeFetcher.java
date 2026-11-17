package com.launchmate.website;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * One HTTP GET with hard limits: addresses validated at connect time, no automatic redirects (the
 * caller decides), a byte cap, and a total time cap. Closing the response without draining it
 * drops the connection, so an endless response cannot hold a worker.
 */
public final class SafeFetcher implements AutoCloseable {

    public record Response(int status, URI uri, String contentType, byte[] body, boolean truncated,
                           String location, long elapsedMs) {}

    private final CloseableHttpClient client;
    private final long totalTimeoutNanos;

    public SafeFetcher(UrlGuard guard, String userAgent, Duration connectTimeout, Duration totalTimeout) {
        Timeout connect = Timeout.ofMilliseconds(connectTimeout.toMillis());
        Timeout socket = Timeout.ofMilliseconds(totalTimeout.toMillis());
        PoolingHttpClientConnectionManager connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new GuardedDnsResolver(guard))
                .setMaxConnTotal(8)
                .setMaxConnPerRoute(2)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(connect).setSocketTimeout(socket).build())
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setRedirectsEnabled(false).setResponseTimeout(socket).build())
                .setUserAgent(userAgent)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
        this.totalTimeoutNanos = totalTimeout.toNanos();
    }

    public Response get(URI uri, long maxBytes) throws IOException {
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept", "text/html,application/xhtml+xml;q=0.9,text/plain;q=0.5,*/*;q=0.1");
        long start = System.nanoTime();
        try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
            byte[] body = new byte[0];
            boolean truncated = false;
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream in = entity.getContent()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        long remaining = maxBytes - out.size();
                        if (n > remaining) {
                            out.write(buffer, 0, (int) remaining);
                            truncated = true;
                            break;
                        }
                        out.write(buffer, 0, n);
                        if (System.nanoTime() - start > totalTimeoutNanos) {
                            truncated = true;
                            break;
                        }
                    }
                    body = out.toByteArray();
                }
            }
            return new Response(response.getCode(), uri, header(response, "Content-Type"), body, truncated,
                    header(response, "Location"), (System.nanoTime() - start) / 1_000_000);
        }
    }

    private static String header(ClassicHttpResponse response, String name) {
        Header h = response.getFirstHeader(name);
        return h == null ? null : h.getValue();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
