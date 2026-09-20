package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

class RelayLoadProfileTest {
    @Test void recordsRepresentativePayloadConcurrencyMemoryThroughputAndP95Latency() {
        int concurrentConnections = 200;
        String prompt = "가".repeat(21_846); // 65,538 UTF-8 bytes, representative p95 payload.
        var request = new RelayExecutionRequest(UUID.randomUUID(), "load", "work", prompt, "model", "medium", "text", null, List.of());
        var lease = new ConnectionLocationLease(request.soloPlayerId(), "a", "http://a", "s", "c", Instant.now().plusSeconds(30));
        var latencies = new CopyOnWriteArrayList<Long>();
        var dispatcher = new RelayExecutionDispatcher("a", id -> reactor.core.publisher.Mono.just(Optional.of(lease)),
                execution -> reactor.core.publisher.Mono.just(RelayExecutionResult.success(execution.requestId(), "완료")),
                (address, execution) -> reactor.core.publisher.Mono.never(), RelayMetrics.noop(), Duration.ofSeconds(2));
        long heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long directBefore = directMemory();
        long started = System.nanoTime();
        Flux.range(0, concurrentConnections).parallel().runOn(Schedulers.parallel()).flatMap(index -> {
            long oneStarted = System.nanoTime();
            return dispatcher.execute(request).doFinally(ignored -> latencies.add(System.nanoTime() - oneStarted));
        }).sequential().blockLast();
        long durationNanos = System.nanoTime() - started;
        long heapDelta = Math.max(0, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() - heapBefore);
        long directDelta = Math.max(0, directMemory() - directBefore);
        var sorted = latencies.stream().sorted().toList();
        long p95Nanos = sorted.get((int) Math.ceil(sorted.size() * .95) - 1);
        double bytesPerSecond = (prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length * (double) concurrentConnections)
                / (durationNanos / 1_000_000_000d);
        System.out.printf(Locale.ROOT, "RELAY_LOAD concurrent=%d promptUtf8Bytes=%d heapDeltaBytes=%d directDeltaBytes=%d throughputBytesPerSecond=%.0f p95Millis=%.3f%n",
                concurrentConnections, prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, heapDelta, directDelta, bytesPerSecond, p95Nanos / 1_000_000d);
        assertEquals(concurrentConnections, latencies.size());
    }

    private static long directMemory() {
        return ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class).stream()
                .filter(pool -> pool.getName().equals("direct")).mapToLong(java.lang.management.BufferPoolMXBean::getMemoryUsed).sum();
    }
}
