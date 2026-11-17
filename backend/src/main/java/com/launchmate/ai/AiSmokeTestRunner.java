package com.launchmate.ai;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Developer check, off by default. Run with --launchmate.ai.smoke-test=true to make the same
 * Claude call twice and confirm that the second call reads the system prompt from the cache.
 * It then shuts the application down.
 */
@Component
@ConditionalOnProperty(name = "launchmate.ai.smoke-test", havingValue = "true")
public class AiSmokeTestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiSmokeTestRunner.class);

    private final ClaudeService claude;
    private final ConfigurableApplicationContext context;

    public AiSmokeTestRunner(ClaudeService claude, ConfigurableApplicationContext context) {
        this.claude = claude;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            var request = new ClaudeService.ClaudeRequest(
                    "smoke_test", null, java.util.List.of("common_rules"),
                    "Reply with exactly the word OK and nothing else.",
                    Model.CLAUDE_SONNET_5_5, 1024, OutputConfig.Effort.LOW, null);

            var first = claude.complete(request);
            var second = claude.complete(request);

            log.info("SMOKE reply1='{}' reply2='{}'", first.text().trim(), second.text().trim());
            log.info("SMOKE call1 cacheWrite={} cacheRead={}", first.cacheCreationTokens(), first.cacheReadTokens());
            log.info("SMOKE call2 cacheWrite={} cacheRead={}", second.cacheCreationTokens(), second.cacheReadTokens());
            if (second.cacheReadTokens() > 0) {
                log.info("SMOKE RESULT: prompt caching is WORKING");
            } else {
                log.warn("SMOKE RESULT: no cache read on the second call - prefix too short or changing");
                exitCode = 2;
            }
        } catch (Exception e) {
            log.error("SMOKE RESULT: call failed: {}", e.getClass().getSimpleName() + ": " + e.getMessage());
            exitCode = 1;
        }
        final int code = exitCode;
        SpringApplication.exit(context, () -> code);
    }
}
