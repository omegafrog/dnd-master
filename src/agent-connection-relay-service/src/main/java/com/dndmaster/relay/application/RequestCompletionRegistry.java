package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public final class RequestCompletionRegistry {
    private final ConcurrentHashMap<String, Sinks.One<String>> pending = new ConcurrentHashMap<>();

    public Mono<String> await(String requestId, Duration timeout) {
        Sinks.One<String> sink = Sinks.one();
        if (pending.putIfAbsent(requestId, sink) != null) return Mono.error(new IllegalStateException("requestId is already pending"));
        return sink.asMono().timeout(timeout).doFinally(ignored -> pending.remove(requestId, sink));
    }

    public boolean complete(String requestId, String finalContent) {
        var sink = pending.remove(requestId);
        return sink != null && sink.tryEmitValue(finalContent).isSuccess();
    }

    public boolean fail(String requestId, Throwable failure) {
        var sink = pending.remove(requestId);
        return sink != null && sink.tryEmitError(failure).isSuccess();
    }
}
