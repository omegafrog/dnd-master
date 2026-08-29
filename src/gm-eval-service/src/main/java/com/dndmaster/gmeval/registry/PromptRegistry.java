package com.dndmaster.gmeval.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Role-isolated registry enforcing immutable versions and approved runtime reads. */
public final class PromptRegistry implements PromptRegistryReadPort {
    private final PromptRegistryStore store;
    private final Map<PromptVersion, PromptArtifact> artifacts = new LinkedHashMap<>();

    public PromptRegistry() {
        this(new InMemoryPromptRegistryStore());
    }

    public PromptRegistry(PromptRegistryStore store) {
        this.store = Objects.requireNonNull(store, "prompt registry store required");
        for (PromptArtifact artifact : store.load()) {
            if (artifacts.put(artifact.promptVersion(), artifact) != null) {
                throw new IllegalArgumentException("duplicate prompt version in registry");
            }
        }
        validateActiveUniqueness();
    }

    public synchronized PromptArtifact register(PromptArtifact artifact) {
        Objects.requireNonNull(artifact, "prompt artifact required");
        if (artifact.status() == PromptArtifactStatus.ACTIVE) {
            throw new IllegalArgumentException("active artifacts must be activated through the registry");
        }
        if (artifacts.containsKey(artifact.promptVersion())) {
            throw new IllegalArgumentException("prompt version is immutable: " + artifact.promptVersion());
        }
        artifacts.put(artifact.promptVersion(), artifact);
        persist();
        return artifact;
    }

    public synchronized PromptArtifact registerBaseline(PromptArtifact artifact) {
        if (artifact == null || !artifact.baseline()) throw new IllegalArgumentException("baseline artifact required");
        register(artifact);
        approve(artifact.promptVersion());
        return activate(artifact.promptVersion());
    }

    public synchronized PromptArtifact approve(PromptVersion version) {
        PromptArtifact artifact = require(version);
        if (artifact.status() == PromptArtifactStatus.ROLLED_BACK) {
            throw new IllegalStateException("rolled back prompt cannot be approved");
        }
        if (artifact.status() == PromptArtifactStatus.ACTIVE) return artifact;
        PromptArtifact approved = artifact.withStatus(PromptArtifactStatus.APPROVED);
        artifacts.put(version, approved);
        persist();
        return approved;
    }

    public synchronized PromptArtifact activate(PromptVersion version) {
        PromptArtifact candidate = require(version);
        if (!candidate.isApproved()) throw new IllegalStateException("prompt must be approved before activation");
        for (Map.Entry<PromptVersion, PromptArtifact> entry : new ArrayList<>(artifacts.entrySet())) {
            PromptArtifact current = entry.getValue();
            if (current.promptVersion().role() == version.role() && current.status() == PromptArtifactStatus.ACTIVE
                    && !current.promptVersion().equals(version)) {
                artifacts.put(entry.getKey(), current.withStatus(PromptArtifactStatus.ROLLED_BACK));
            }
        }
        PromptArtifact active = candidate.withStatus(PromptArtifactStatus.ACTIVE);
        artifacts.put(version, active);
        persist();
        return active;
    }

    @Override
    public synchronized PromptRuntimeConfiguration active(PromptRole role) {
        Objects.requireNonNull(role, "prompt role required");
        return PromptRuntimeConfiguration.from(artifacts.values().stream()
                .filter(artifact -> artifact.promptVersion().role() == role && artifact.status() == PromptArtifactStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no active approved prompt for role " + role)));
    }

    @Override
    public synchronized List<PromptArtifact> list() {
        return List.copyOf(artifacts.values());
    }

    /** Explicitly blocks legacy inline prompt fallback at the registry boundary. */
    public PromptRuntimeConfiguration resolveInline(PromptRole role, String inlinePrompt) {
        throw new IllegalStateException("inline prompts are not registered; resolve an approved active artifact for " + role);
    }

    private PromptArtifact require(PromptVersion version) {
        PromptVersion required = Objects.requireNonNull(version, "prompt version required");
        PromptArtifact artifact = artifacts.get(required);
        if (artifact == null) throw new IllegalStateException("prompt version is not registered: " + required);
        return artifact;
    }

    private void validateActiveUniqueness() {
        Map<PromptRole, Integer> activeByRole = new EnumMap<>(PromptRole.class);
        for (PromptArtifact artifact : artifacts.values()) {
            if (artifact.status() == PromptArtifactStatus.ACTIVE) {
                int count = activeByRole.merge(artifact.promptVersion().role(), 1, Integer::sum);
                if (count > 1) throw new IllegalArgumentException("multiple active prompts for role");
            }
        }
    }

    private void persist() {
        store.save(new ArrayList<>(artifacts.values()));
    }
}
