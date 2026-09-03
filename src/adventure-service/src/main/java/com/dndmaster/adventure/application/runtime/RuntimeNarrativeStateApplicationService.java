package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import com.dndmaster.adventure.domain.runtime.narrative.StateDeltaValidator;
import java.util.Objects;
import java.util.UUID;

/** Runtime-owned state boundary. Static evidence is intentionally not an input to commit. */
public final class RuntimeNarrativeStateApplicationService {
    private final NarrativeStateRepository repository;
    private final StateDeltaValidator validator;
    public RuntimeNarrativeStateApplicationService(NarrativeStateRepository repository) {
        this(repository, new StateDeltaValidator());
    }
    public RuntimeNarrativeStateApplicationService(NarrativeStateRepository repository, StateDeltaValidator validator) {
        this.repository = Objects.requireNonNull(repository, "narrative state repository must not be null");
        this.validator = Objects.requireNonNull(validator, "state delta validator must not be null");
    }
    public NarrativeState load(UUID sessionId) { return repository.findBySessionId(sessionId).orElseGet(NarrativeState::empty); }
    public NarrativeContext project(UUID sessionId, String actorId, String currentScene) {
        return load(sessionId).project(actorId, currentScene);
    }
    public NarrativeState commit(UUID sessionId, StateDelta delta) {
        NarrativeState committed = validator.validateAndCommit(load(sessionId), delta);
        repository.save(sessionId, committed);
        return committed;
    }
    public NarrativeState commitProposal(UUID sessionId, StateDelta proposal) {
        NarrativeState committed = validator.validateAndCommitProposal(load(sessionId), proposal);
        repository.save(sessionId, committed);
        return committed;
    }
}
