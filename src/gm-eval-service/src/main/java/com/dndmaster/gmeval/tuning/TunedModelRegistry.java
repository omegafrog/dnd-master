package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory role-scoped CAS registry; history is retained for explicit rollback and audit. */
public final class TunedModelRegistry {
    private final RoleModelConfigurationStore store;
    private final Map<PromptRole, RoleModelConfiguration> active = new EnumMap<>(PromptRole.class);
    private final Map<PromptRole, List<RoleModelConfiguration>> history = new EnumMap<>(PromptRole.class);

    public TunedModelRegistry() { this(new InMemoryRoleModelConfigurationStore()); }

    public TunedModelRegistry(RoleModelConfigurationStore store) {
        this.store = Objects.requireNonNull(store, "role model configuration store required");
        for (RoleModelConfiguration configuration : store.load()) {
            active.put(configuration.role(), configuration);
            history.computeIfAbsent(configuration.role(), ignored -> new ArrayList<>()).add(configuration);
        }
    }

    public synchronized void registerBaseline(RoleModelConfiguration baseline) {
        Objects.requireNonNull(baseline, "baseline model configuration required");
        if (active.containsKey(baseline.role())) throw new IllegalArgumentException("active model already exists for role");
        active.put(baseline.role(), baseline);
        history.computeIfAbsent(baseline.role(), ignored -> new ArrayList<>()).add(baseline);
        persist();
    }

    public synchronized RoleModelConfiguration activate(RoleModelConfiguration candidate,
                                                         String expectedActiveModelVersion, String actor) {
        Objects.requireNonNull(candidate, "model configuration required");
        requireActor(actor);
        RoleModelConfiguration current = active.get(candidate.role());
        if (!Objects.equals(expectedActiveModelVersion, current == null ? null : current.modelVersion())) {
            throw new TuningActivationException("stale active model for role " + candidate.role());
        }
        active.put(candidate.role(), candidate);
        history.computeIfAbsent(candidate.role(), ignored -> new ArrayList<>()).add(candidate);
        persist();
        return candidate;
    }

    public synchronized RoleModelConfiguration rollback(PromptRole role, String targetModelVersion,
                                                         String expectedActiveModelVersion, String actor) {
        Objects.requireNonNull(role, "model role required");
        requireActor(actor);
        RoleModelConfiguration current = active.get(role);
        if (current == null || !Objects.equals(expectedActiveModelVersion, current.modelVersion())) {
            throw new TuningActivationException("stale active model for role " + role);
        }
        RoleModelConfiguration target = history.getOrDefault(role, List.of()).stream()
                .filter(value -> value.modelVersion().equals(targetModelVersion)).findFirst()
                .orElseThrow(() -> new TuningActivationException("rollback target not found for role " + role));
        active.put(role, target);
        history.get(role).add(target);
        persist();
        return target;
    }

    public synchronized Optional<RoleModelConfiguration> active(PromptRole role) { return Optional.ofNullable(active.get(role)); }
    public synchronized List<RoleModelConfiguration> history(PromptRole role) {
        return List.copyOf(history.getOrDefault(role, List.of()));
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("activation actor required");
    }

    private void persist() {
        List<RoleModelConfiguration> all = history.values().stream().flatMap(List::stream).toList();
        store.save(all);
    }
}
