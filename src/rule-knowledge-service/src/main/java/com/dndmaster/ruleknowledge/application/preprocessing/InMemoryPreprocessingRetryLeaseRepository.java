package com.dndmaster.ruleknowledge.application.preprocessing;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryPreprocessingRetryLeaseRepository implements PreprocessingRetryLeaseRepository {
    private final ConcurrentMap<Key, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public RetryClaim claim(RulebookId documentId, String requestId, String candidateVersion, List<Integer> pages, Duration lease) {
        Key key = new Key(documentId, requestId);
        String token = UUID.randomUUID().toString();
        Entry next = entries.compute(key, (ignored, current) -> {
            if (current != null && "COMPLETED".equals(current.status())) return current;
            if (current != null && current.leaseUntil().isAfter(Instant.now())) return current;
            return new Entry(candidateVersion, List.copyOf(pages), token, Instant.now().plus(lease), "LEASED", null);
        });
        if ("COMPLETED".equals(next.status())) return new RetryClaim(false, true, next.leaseToken(), next.resultVersion());
        return new RetryClaim(token.equals(next.leaseToken()), false, next.leaseToken(), null);
    }

    @Override
    public boolean complete(RulebookId documentId, String requestId, String leaseToken, String candidateVersion, String resultVersion) {
        Key key = new Key(documentId, requestId);
        return entries.computeIfPresent(key, (ignored, current) -> {
            if (!leaseToken.equals(current.leaseToken()) || !candidateVersion.equals(current.candidateVersion())
                    || current.leaseUntil().isBefore(Instant.now())) return current;
            return new Entry(current.candidateVersion(), current.pages(), current.leaseToken(), current.leaseUntil(), "COMPLETED", resultVersion);
        }) != null && "COMPLETED".equals(entries.get(key).status()) && leaseToken.equals(entries.get(key).leaseToken());
    }

    @Override
    public void release(RulebookId documentId, String requestId, String leaseToken) {
        entries.computeIfPresent(new Key(documentId, requestId), (ignored, current) ->
                leaseToken.equals(current.leaseToken()) ? null : current);
    }

    @Override
    public Optional<String> completedResult(RulebookId documentId, String requestId) {
        Entry entry = entries.get(new Key(documentId, requestId));
        return entry != null && "COMPLETED".equals(entry.status()) ? Optional.ofNullable(entry.resultVersion()) : Optional.empty();
    }

    private record Key(RulebookId documentId, String requestId) {}
    private record Entry(String candidateVersion, List<Integer> pages, String leaseToken, Instant leaseUntil,
                         String status, String resultVersion) {}
}
