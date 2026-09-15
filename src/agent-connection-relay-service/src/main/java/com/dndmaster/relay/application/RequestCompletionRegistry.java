package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public final class RequestCompletionRegistry {
    private final ConcurrentHashMap<String, Sinks.One<String>> pending = new ConcurrentHashMap<>();

    public Mono<String> await(String requestId, Duration timeout) {
        return open(requestId, timeout).result().doFinally(ignored -> close(requestId));
    }

    public Pending open(String requestId, Duration timeout) {
        Sinks.One<String> sink = Sinks.one();
        if (pending.putIfAbsent(requestId, sink) != null) {
            return new Pending(false, Mono.error(new IllegalStateException("requestId is already pending")));
        }
        return new Pending(true, sink.asMono().timeout(timeout));
    }

    public boolean complete(String requestId, String finalContent) {
        var sink = pending.get(requestId);
        return sink != null && sink.tryEmitValue(finalContent).isSuccess();
    }

    public boolean fail(String requestId, Throwable failure) {
        var sink = pending.get(requestId);
        return sink != null && sink.tryEmitError(failure).isSuccess();
    }
    public boolean cancel(String requestId) { return fail(requestId, new java.util.concurrent.CancellationException("request cancelled")); }
    public void close(String requestId) { pending.remove(requestId); }
    public record Pending(boolean accepted, Mono<String> result) { }
}
