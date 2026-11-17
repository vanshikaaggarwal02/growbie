package com.launchmate.website;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class WebsiteConfig {

    /** Identifies the crawler to site owners; "growbiebot" is the name robots.txt rules can target. */
    static final String USER_AGENT = "GrowbieBot/1.0 (website audit requested by the site owner)";

    @Bean
    UrlGuard urlGuard() {
        return UrlGuard.strict();
    }

    @Bean(destroyMethod = "close")
    SafeFetcher safeFetcher(UrlGuard guard) {
        return new SafeFetcher(guard, USER_AGENT, Duration.ofSeconds(5), Duration.ofSeconds(12));
    }

    @Bean
    WebsiteCrawler websiteCrawler(SafeFetcher fetcher, UrlGuard guard) {
        return new WebsiteCrawler(fetcher, guard::validateSyntax);
    }

    @Bean
    PageSpeedClient pageSpeedClient(@Value("${launchmate.pagespeed.base-url}") String baseUrl,
                                    @Value("${launchmate.pagespeed.api-key:}") String apiKey,
                                    ObjectMapper json) {
        return new PageSpeedClient(baseUrl, apiKey, json);
    }

    /**
     * Shared by website audits and channel analyses: two at a time, ten waiting. A full queue is
     * reported to the user, never blocked on.
     */
    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor analysisExecutor() {
        AtomicInteger n = new AtomicInteger();
        return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10), r -> {
            Thread t = new Thread(r, "analysis-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
}
