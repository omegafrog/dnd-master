package com.dndmaster.aigamemaster.infrastructure.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import reactor.core.publisher.Flux;

public final class SpringAiChatAdapter implements GmCompletionAdapter {
    private final ChatModel model;
    private final int maxAttempts;
    private final SafeAiAuditLogger logger;
    private final boolean thinkingOnly;
    private final Map<String, CachedResult> completed = new ConcurrentHashMap<>();
    private final Map<String, String> fingerprints = new ConcurrentHashMap<>();

    public SpringAiChatAdapter(ChatModel model, int maxAttempts, SafeAiAuditLogger logger) {
        this(model, maxAttempts, logger, false);
    }

    public SpringAiChatAdapter(ChatModel model, int maxAttempts, SafeAiAuditLogger logger, boolean thinkingOnly) {
        this.model = Objects.requireNonNull(model);
        if (maxAttempts < 1) throw new IllegalArgumentException("max attempts positive");
        this.maxAttempts = maxAttempts;
        this.logger = Objects.requireNonNull(logger);
        this.thinkingOnly = thinkingOnly;
    }

    @Override
    public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
        return completeWithModel(operationId, prompt, parser, null);
    }

    public <T> T completeWithModel(String operationId, String prompt, StructuredResponseParser<T> parser, String requestedModel) {
        String fingerprint = register(operationId, prompt);
        CachedResult cached = completed.get(operationId);
        if (cached != null) return cast(cached.value);
        for (int attempt = 1; ; attempt++) {
            try {
                OllamaChatOptions.Builder options = OllamaChatOptions.builder().format("json").numPredict(1536);
                if (requestedModel != null && !requestedModel.isBlank()) options.model(requestedModel);
                if (thinkingOnly) options.enableThinking(); else options.disableThinking();
                ChatResponse response = model.call(new Prompt(prompt, options.build()));
                T parsed = parser.parse(text(response));
                completed.put(operationId, new CachedResult(fingerprint, parsed));
                logger.success(operationId);
                return parsed;
            } catch (ProviderMalformedResponseException exception) {
                logger.failure(operationId, exception);
                throw exception;
            } catch (RuntimeException exception) {
                RuntimeException mapped = SpringAiProviderExceptionMapper.map(exception);
                if (!retryable(mapped) || attempt >= maxAttempts) {
                    logger.failure(operationId, mapped);
                    throw mapped;
                }
            }
        }
    }

    public Flux<String> stream(String operationId, String prompt) {
        String fingerprint = register(operationId, prompt);
        CachedResult cached = completed.get(operationId);
        if (cached != null) {
            @SuppressWarnings("unchecked") List<String> chunks = (List<String>) cached.value;
            return Flux.fromIterable(chunks);
        }
        return Flux.defer(() -> {
            List<String> chunks = new ArrayList<>();
            return model.stream(new Prompt(prompt)).map(SpringAiChatAdapter::text)
                    .doOnNext(chunks::add)
                    .doOnComplete(() -> {
                        completed.put(operationId, new CachedResult(fingerprint, List.copyOf(chunks)));
                        logger.success(operationId);
                    })
                    .doOnError(exception -> logger.failure(operationId, exception));
        });
    }

    private String register(String id, String prompt) {
        if (id == null || id.isBlank() || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("operation id and prompt required");
        }
        String hash = hash(prompt);
        String previous = fingerprints.putIfAbsent(id, hash);
        if (previous != null && !previous.equals(hash)) throw new AiOperationConflictException();
        return hash;
    }

    private static boolean retryable(Throwable error) {
        return error instanceof ProviderTimeoutException || error instanceof ProviderRateLimitException;
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new ProviderMalformedResponseException("AI response missing text");
        }
        String value = response.getResult().getOutput().getText();
        if (value != null && !value.isBlank()) return value;
        Object thinking = response.getResult().getOutput().getMetadata().get("thinking");
        if (thinking instanceof String fallback && !fallback.isBlank()) return fallback;
        throw new ProviderMalformedResponseException("AI response missing text");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) { return (T) value; }

    private record CachedResult(String fingerprint, Object value) {}
}
