package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.clock.AdventureClock;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFact;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFactLedger;
import com.dndmaster.adventure.domain.runtime.plan.AdventureStoryPlanRevision;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test/local adapters. Production adapters enforce the same expected-version checks in SQL. */
public final class InMemoryStoryContinuityRepositories {
    public static final class Clocks implements AdventureClockRepository {
        private final ConcurrentHashMap<UUID, AdventureClock> values = new ConcurrentHashMap<>();
        public java.util.Optional<AdventureClock> findBySessionId(UUID id) { return java.util.Optional.ofNullable(values.get(id)); }
        public void save(AdventureClock clock, long expectedVersion) {
            values.compute(clock.sessionId(), (id, current) -> {
                long actual = current == null ? 0 : current.version();
                if (actual != expectedVersion) throw new IllegalStateException("adventure clock version conflict");
                return clock;
            });
        }
    }
    public static final class Facts implements CommittedWorldFactRepository {
        private final ConcurrentHashMap<UUID, CommittedWorldFactLedger> values = new ConcurrentHashMap<>();
        public CommittedWorldFactLedger findBySessionId(UUID id) { return values.getOrDefault(id, CommittedWorldFactLedger.empty()); }
        public synchronized void append(UUID id, CommittedWorldFact fact) { values.put(id, findBySessionId(id).append(fact)); }
    }
    public static final class Plans implements StoryPlanRevisionRepository {
        private final ConcurrentHashMap<UUID, List<AdventureStoryPlanRevision>> values = new ConcurrentHashMap<>();
        public java.util.Optional<AdventureStoryPlanRevision> current(UUID id) { return java.util.Optional.ofNullable(values.get(id)).filter(v -> !v.isEmpty()).map(v -> v.get(v.size() - 1)); }
        public List<AdventureStoryPlanRevision> history(UUID id) { return List.copyOf(values.getOrDefault(id, List.of())); }
        public synchronized void append(AdventureStoryPlanRevision revision) {
            List<AdventureStoryPlanRevision> history = new ArrayList<>(values.getOrDefault(revision.sessionId(), List.of()));
            if (history.stream().anyMatch(existing -> existing.revisionId().equals(revision.revisionId())
                    || existing.causeTurnId().equals(revision.causeTurnId()))) return;
            if (!history.isEmpty() && !history.get(history.size() - 1).revisionId().equals(revision.predecessorRevisionId())) throw new IllegalStateException("story plan predecessor conflict");
            if (!history.isEmpty() && revision.version() != history.get(history.size() - 1).version() + 1) throw new IllegalStateException("story plan version conflict");
            values.put(revision.sessionId(), java.util.stream.Stream.concat(history.stream(), java.util.stream.Stream.of(revision)).toList());
        }
    }
}
