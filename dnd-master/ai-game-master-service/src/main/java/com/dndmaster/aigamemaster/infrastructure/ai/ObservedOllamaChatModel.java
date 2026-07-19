package com.dndmaster.aigamemaster.infrastructure.ai;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public final class ObservedOllamaChatModel implements ChatModel {
    private final ChatModel delegate;
    private final Duration deadline;
    private final ExecutorService executor;
    private final OllamaCallObservability observability;

    ObservedOllamaChatModel(
            ChatModel delegate, Duration deadline, ExecutorService executor, OllamaCallObservability observability) {
        this.delegate = Objects.requireNonNull(delegate);
        this.deadline = Objects.requireNonNull(deadline);
        this.executor = Objects.requireNonNull(executor);
        this.observability = Objects.requireNonNull(observability);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return observability.invoke(() -> callBeforeDeadline(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            observability.beforeCall();
            return delegate.stream(prompt)
                    .timeout(deadline)
                    .onErrorMap(TimeoutException.class, ProviderTimeoutException::new)
                    .doOnComplete(observability::recordSuccess)
                    .doOnError(RuntimeException.class, observability::recordFailure);
        });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    public ChatModel delegate() {
        return delegate;
    }

    private ChatResponse callBeforeDeadline(Prompt prompt) {
        Future<ChatResponse> future = executor.submit(() -> delegate.call(prompt));
        try {
            return future.get(deadline.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ProviderTimeoutException(exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Ollama chat call failed", exception.getCause());
        }
    }
}
