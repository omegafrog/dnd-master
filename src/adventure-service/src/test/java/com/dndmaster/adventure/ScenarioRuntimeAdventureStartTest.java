package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.AdventurePlayerProjection;
import com.dndmaster.adventure.application.runtime.AdventureStartApplicationService;
import com.dndmaster.adventure.application.runtime.StartAdventureCommand;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.runtime.GameState;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import com.dndmaster.adventure.domain.scenario.CompilationOutcome;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioRuntimeAdventureStartTest {
    @Test
    void starts_only_a_ready_package_and_locks_its_version() {
        OwnerPlayerId owner = owner();
        ScenarioPackage packageVersion = readyPackage();
        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository();
        AdventureStartApplicationService service = new AdventureStartApplicationService(
                packageRepository(packageVersion), adventures);

        Adventure started = service.start(command(owner, packageVersion));

        assertEquals(packageVersion.packageId(), started.lockedScenarioPackageId());
        assertEquals(packageVersion.bundleRevision(), started.lockedScenarioPackageRevision());
        assertEquals(com.dndmaster.adventure.domain.adventure.AdventureStatus.ACTIVE, started.status());
        assertNotNull(started.currentSituation());
        assertEquals(packageVersion.scenarioModel().startingSituation(), started.currentSituation().problem());
        assertThrows(IllegalStateException.class, () -> started.lockScenarioPackage(UUID.randomUUID(), 99));
    }

    @Test
    void repeated_start_resumes_starting_adventure_without_duplicate_creation() {
        OwnerPlayerId owner = owner();
        ScenarioPackage packageVersion = readyPackage();
        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository();
        Adventure existing = Adventure.beginScenarioRuntime(
                new AdventureId(UUID.randomUUID()), SessionId.generate(), owner,
                new ScenarioId(packageVersion.packageId()), new RuleSetId(UUID.randomUUID()),
                packageVersion.packageId(), packageVersion.bundleRevision(), party(),
                new AdventureContext("opening", null, null, null));
        adventures.save(existing);
        AdventureStartApplicationService service = new AdventureStartApplicationService(
                packageRepository(packageVersion), adventures);

        Adventure resumed = service.start(new StartAdventureCommand(existing.id(), existing.sessionId(), owner,
                packageVersion.packageId(), packageVersion.bundleRevision(), new ScenarioId(packageVersion.packageId()),
                new RuleSetId(UUID.randomUUID()), party(), new AdventureContext("opening", null, null, null),
                UUID.randomUUID()));

        assertEquals(existing.id(), resumed.id());
        assertEquals(2, adventures.saveCount);
        assertEquals(com.dndmaster.adventure.domain.adventure.AdventureStatus.ACTIVE, resumed.status());
    }

    @Test
    void state_and_hidden_situation_round_trip_through_player_projection() {
        OwnerPlayerId owner = owner();
        ScenarioPackage packageVersion = readyPackage();
        Adventure adventure = Adventure.beginScenarioRuntime(
                AdventureId.generate(), SessionId.generate(), owner, new ScenarioId(packageVersion.packageId()),
                new RuleSetId(UUID.randomUUID()), packageVersion.packageId(), packageVersion.bundleRevision(), party(),
                new AdventureContext("opening", null, null, null));
        adventure.initializeScenarioRuntime(owner, new GameState(Map.of("door", "broken"), 1),
                new DisclosureState(List.of("door")),
                new CurrentSituation(UUID.randomUUID(), 1, "crypt", "Find the key", "guarded", "escape"),
                List.of(new RuntimeAddedFact(UUID.randomUUID(), "The keeper has a hidden sister.", UUID.randomUUID())),
                new AdventureContext("The broken door is open.", null, null, null));

        AdventurePlayerProjection projection = AdventurePlayerProjection.from(adventure);

        assertEquals("The broken door is open.", projection.currentScene());
        assertEquals(List.of("door"), projection.disclosedFactIds());
        assertEquals(List.of("adventureId", "status", "version", "currentScene", "disclosedFactIds"),
                java.util.Arrays.stream(AdventurePlayerProjection.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void rejects_non_ready_package_before_creating_adventure() {
        ScenarioPackage notReady = ScenarioPackage.publish(ScenarioBundleId.generate(), 1, "not-ready",
                List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.INVALID, List.of(), CompilationOutcome.FAILED),
                CharacterLimit.defaultLimit());
        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository();
        AdventureStartApplicationService service = new AdventureStartApplicationService(
                packageRepository(notReady), adventures);

        assertThrows(IllegalStateException.class, () -> service.start(command(owner(), notReady)));
        assertEquals(0, adventures.saveCount);
    }

    private static StartAdventureCommand command(OwnerPlayerId owner, ScenarioPackage packageVersion) {
        return new StartAdventureCommand(AdventureId.generate(), SessionId.generate(), owner,
                packageVersion.packageId(), packageVersion.bundleRevision(), new ScenarioId(packageVersion.packageId()),
                new RuleSetId(UUID.randomUUID()), party(), new AdventureContext("opening", null, null, null), UUID.randomUUID());
    }

    private static ScenarioPackageRepository packageRepository(ScenarioPackage packageVersion) {
        return new ScenarioPackageRepository() {
            @Override public Optional<ScenarioPackage> findById(UUID id) { return id.equals(packageVersion.packageId()) ? Optional.of(packageVersion) : Optional.empty(); }
            @Override public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) { return Optional.empty(); }
            @Override public List<ScenarioPackage> findByBundleId(UUID bundleId) { return List.of(packageVersion); }
            @Override public void save(ScenarioPackage scenarioPackage) {}
        };
    }

    private static ScenarioPackage readyPackage() {
        ScenarioModel model = new ScenarioModel(1,
                List.of(), List.of(), List.of(new ScenarioModelElement("objective", "objective", Map.of("value", "Find the key"), List.of())),
                List.of(), List.of(), List.of(), List.of(new ScenarioModelElement("resolution", "resolution", Map.of("value", "escape"), List.of())),
                "The crypt door groans open.");
        return ScenarioPackage.publishWithScenarioModel(ScenarioBundleId.generate(), 7, "ready",
                List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                CharacterLimit.defaultLimit(), null, List.of(), List.of(), model);
    }

    private static List<AdventurePartyMember> party() {
        return List.of(new AdventurePartyMember(new com.dndmaster.adventure.domain.adventure.CharacterSheetId(UUID.randomUUID()),
                ControlMode.DIRECT, true, true, true, true, true, true));
    }

    private static OwnerPlayerId owner() { return new OwnerPlayerId(UUID.randomUUID()); }

    private static final class InMemoryAdventureRepository implements AdventureRepository {
        private Adventure current;
        private int saveCount;
        @Override public Optional<Adventure> findById(AdventureId id) { return current != null && current.id().equals(id) ? Optional.of(current) : Optional.empty(); }
        @Override public List<Adventure> findSavedByOwner(OwnerPlayerId owner) { return current == null ? List.of() : List.of(current); }
        @Override public void save(Adventure adventure) { current = adventure; saveCount++; }
    }
}
