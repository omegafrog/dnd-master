package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioRepository;
import com.dndmaster.adventure.application.scenario.ScenarioNotReadyException;
import com.dndmaster.adventure.application.scenario.ScenarioPreparationFailedException;
import com.dndmaster.adventure.application.scenario.ScenarioPreparationPort;
import com.dndmaster.adventure.application.scenario.ScenarioStoragePort;
import com.dndmaster.adventure.application.scenario.ScenarioUpload;
import com.dndmaster.adventure.domain.scenario.AdventureScenario;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.RequestingPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioAccessDeniedException;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import com.dndmaster.adventure.domain.scenario.ScenarioPreparationStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioSource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureScenarioApplicationServiceTest {
    @Test
    void uploadStoresSourceAndCreatesOwnedUploadedScenario() {
        InMemoryScenarioRepository repository = new InMemoryScenarioRepository();
        FakeStorage storage = new FakeStorage();
        AdventureScenarioApplicationService service = service(repository, storage, source -> {});
        OwnerPlayerId owner = owner();

        AdventureScenario scenario = service.uploadScenario(upload(owner));

        assertEquals(ScenarioPreparationStatus.UPLOADED, scenario.status());
        assertEquals(owner, scenario.ownerPlayerId());
        assertEquals("scenario.md", scenario.source().originalFilename());
        assertEquals(1, storage.calls);
        assertEquals(scenario, repository.findById(scenario.id()).orElseThrow());
    }

    @Test
    void successfulPreparationMakesScenarioReadyOnlyForOwner() {
        InMemoryScenarioRepository repository = new InMemoryScenarioRepository();
        AdventureScenarioApplicationService service = service(repository, new FakeStorage(), source -> {});
        OwnerPlayerId owner = owner();
        AdventureScenario uploaded = service.uploadScenario(upload(owner));

        AdventureScenario ready = service.prepareScenario(uploaded.id(), requester(owner));

        assertEquals(ScenarioPreparationStatus.READY, ready.status());
        assertTrue(ready.isUsableByAiGameMaster());
        assertEquals(ready, service.accessScenario(ready.id(), requester(owner)));
        assertThrows(
                ScenarioAccessDeniedException.class,
                () -> service.accessScenario(ready.id(), new RequestingPlayerId(UUID.randomUUID())));
    }

    @Test
    void preparationFailureIsPersistedAndCanNeverBeAccessedAsReady() {
        InMemoryScenarioRepository repository = new InMemoryScenarioRepository();
        AdventureScenarioApplicationService service = service(
                repository,
                new FakeStorage(),
                source -> {
                    throw new IllegalStateException("fake preparation failure");
                });
        OwnerPlayerId owner = owner();
        AdventureScenario uploaded = service.uploadScenario(upload(owner));

        assertThrows(
                ScenarioPreparationFailedException.class,
                () -> service.prepareScenario(uploaded.id(), requester(owner)));

        AdventureScenario failed = repository.findById(uploaded.id()).orElseThrow();
        assertEquals(ScenarioPreparationStatus.FAILED, failed.status());
        assertFalse(failed.isUsableByAiGameMaster());
        assertTrue(failed.failureReason().isPresent());
        assertThrows(
                ScenarioNotReadyException.class,
                () -> service.accessScenario(failed.id(), requester(owner)));
        assertThrows(IllegalStateException.class, failed::recordPreparationSuccess);
    }

    private static AdventureScenarioApplicationService service(
            AdventureScenarioRepository repository,
            ScenarioStoragePort storage,
            ScenarioPreparationPort preparation) {
        return new AdventureScenarioApplicationService(repository, storage, preparation);
    }

    private static ScenarioUpload upload(OwnerPlayerId owner) {
        return new ScenarioUpload(owner, "scenario.md", "A dark cave".getBytes(StandardCharsets.UTF_8));
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.randomUUID());
    }

    private static RequestingPlayerId requester(OwnerPlayerId owner) {
        return new RequestingPlayerId(owner.value());
    }

    private static final class FakeStorage implements ScenarioStoragePort {
        private int calls;

        @Override
        public ScenarioSource store(ScenarioUpload upload) {
            calls++;
            return new ScenarioSource("scenarios/one", upload.originalFilename(), "sha256:one");
        }
    }

    private static final class InMemoryScenarioRepository implements AdventureScenarioRepository {
        private final Map<ScenarioId, AdventureScenario> scenarios = new HashMap<>();

        @Override
        public Optional<AdventureScenario> findById(ScenarioId scenarioId) {
            return Optional.ofNullable(scenarios.get(scenarioId));
        }

        @Override
        public void save(AdventureScenario scenario) {
            scenarios.put(scenario.id(), scenario);
        }
    }
}
