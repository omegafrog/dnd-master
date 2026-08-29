package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.InMemoryNarrativeStateRepository;
import com.dndmaster.adventure.application.runtime.RuntimeNarrativeStateApplicationService;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import com.dndmaster.adventure.domain.runtime.narrative.WorldFact;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeNarrativeStateApplicationServiceTest {
    @Test
    void commitsOnlyThroughValidatedRuntimeRepositoryBoundary() {
        UUID sessionId = UUID.randomUUID();
        var repository = new InMemoryNarrativeStateRepository();
        var service = new RuntimeNarrativeStateApplicationService(repository);
        repository.save(sessionId, NarrativeState.empty().addWorldFact(new WorldFact("secret", "hidden", false)));

        service.commit(sessionId, new StateDelta(0, Set.of("secret"), Set.of("secret"), List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThat(service.project(sessionId, "player", "scene").worldFacts()).extracting(WorldFact::id).containsExactly("secret");
        assertThatThrownBy(() -> service.commit(sessionId, new StateDelta(0, Set.of(), Set.of(), List.of(), List.of(), List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("version");
    }
}
