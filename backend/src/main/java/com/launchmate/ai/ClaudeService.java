package com.launchmate.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.launchmate.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Single entry point for Claude calls. System prompts go first, each marked cacheable, with the
 * per-request evidence in the user message after them. Every call is logged to ai_usage.
 */
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private final AnthropicClient client;
    private final PromptTemplateService prompts;
    private final UsageRecorder usageRecorder;

    public ClaudeService(AnthropicClient client, PromptTemplateService prompts, UsageRecorder usageRecorder) {
        this.client = client;
        this.prompts = prompts;
        this.usageRecorder = usageRecorder;
    }

    /** {@code format} is optional; when present the reply is constrained to that JSON schema. */
    public record ClaudeRequest(
            String feature,
            UUID profileId,
            List<String> systemPromptNames,
            String userContent,
            Model model,
            long maxTokens,
            OutputConfig.Effort effort,
            JsonOutputFormat format) {

        ClaudeRequest withUserContent(String content) {
            return new ClaudeRequest(feature, profileId, systemPromptNames, content, model, maxTokens, effort, format);
        }
    }

    public record ClaudeResult(
            String text,
            long inputTokens,
            long outputTokens,
            long cacheCreationTokens,
            long cacheReadTokens,
            String stopReason,
            long latencyMs,
            String requestId) {}

    public ClaudeResult complete(ClaudeRequest req) {
        // Shared rules come first so that prefix is reused by every feature; the task prompt follows.
        List<TextBlockParam> system = new ArrayList<>();
        for (String name : req.systemPromptNames()) {
            system.add(TextBlockParam.builder()
                    .text(prompts.load(name))
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build());
        }

        OutputConfig.Builder output = OutputConfig.builder().effort(req.effort());
        if (req.format() != null) {
            output.format(req.format());
        }
        MessageCreateParams params = MessageCreateParams.builder()
                .model(req.model())
                .maxTokens(req.maxTokens())
                .systemOfTextBlockParams(system)
                .outputConfig(output.build())
                .addUserMessage(req.userContent())
                .build();

        long start = System.nanoTime();
        Message message;
        try {
            message = client.messages().create(params);
        } catch (RateLimitException e) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CLAUDE_RATE_LIMITED",
                    "The AI service is busy. Please try again shortly.");
        } catch (AnthropicException e) {
            log.warn("claude call failed feature={} error={}", req.feature(), e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CLAUDE_PROVIDER_ERROR",
                    "The AI analysis is unavailable right now. Please try again.");
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        Usage usage = message.usage();
        long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
        long cacheRead = usage.cacheReadInputTokens().orElse(0L);
        String stopReason = message.stopReason().map(Object::toString).orElse(null);

        usageRecorder.record(req.profileId(), req.feature(), message.model().asString(),
                PromptTemplateService.CURRENT_VERSION, usage.inputTokens(), usage.outputTokens(),
                cacheCreation, cacheRead, stopReason, latencyMs, message.id());
        log.info("claude call feature={} model={} input={} output={} cacheWrite={} cacheRead={} stop={} latencyMs={}",
                req.feature(), message.model().asString(), usage.inputTokens(), usage.outputTokens(),
                cacheCreation, cacheRead, stopReason, latencyMs);

        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CLAUDE_PROVIDER_ERROR",
                    "The AI could not complete this request.");
        }

        String text = message.content().stream()
                .filter(ContentBlock::isText)
                .map(b -> b.text().get().text())
                .collect(Collectors.joining());
        return new ClaudeResult(text, usage.inputTokens(), usage.outputTokens(), cacheCreation,
                cacheRead, stopReason, latencyMs, message.id());
    }

    /**
     * Calls the model and validates the reply. If validation fails, the model gets exactly one retry
     * with the reason; a second failure becomes CLAUDE_INVALID_OUTPUT. Nothing invalid is returned.
     */
    public <T> T completeValidated(ClaudeRequest req, Function<String, T> parseAndValidate) {
        ClaudeResult first = complete(req);
        try {
            return parseAndValidate.apply(first.text());
        } catch (InvalidAiOutputException firstFailure) {
            log.warn("invalid AI output feature={} reason={}", req.feature(), firstFailure.getMessage());
            String retryContent = req.userContent()
                    + "\n\nYour previous reply was rejected: " + firstFailure.getMessage()
                    + " Reply again with only a JSON object that follows the schema and every length and size limit.";
            ClaudeResult second = complete(req.withUserContent(retryContent));
            try {
                return parseAndValidate.apply(second.text());
            } catch (InvalidAiOutputException secondFailure) {
                log.warn("invalid AI output after retry feature={} reason={}", req.feature(), secondFailure.getMessage());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "CLAUDE_INVALID_OUTPUT",
                        "The analysis could not be completed. Please try again.");
            }
        }
    }
}
