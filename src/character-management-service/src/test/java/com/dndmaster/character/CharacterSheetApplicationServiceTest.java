package com.dndmaster.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.character.application.*;
import com.dndmaster.character.domain.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CharacterSheetApplicationServiceTest {
    @Test
    void stores_death_save_progress_stabilizes_and_restores_hit_point_on_natural_twenty() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(repository,
                id -> SheetEdition.DND_5E_2014, id -> SessionCharacterPolicy.started("DND_5E_2014"));
        AdventureId adventureId = adventure();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), adventureId,
                new SessionId(adventureId.value()), owner, SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 1, false, "Elf", "파이터", "군인", "STR=15",
                        "{\"hitPointMaximum\":12}", "{\"ownedEquipment\":[]}",
                        "{\"currentHitPoints\":0,\"deathSavingThrowSuccesses\":2,\"equippedItems\":{}}"), 0, null, null);
        repository.save(sheet);

        CharacterSheet stable = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                new RuntimeCharacterMutation(0, 0, List.of(), List.of(), 0, 10), UUID.randomUUID(), 0);
        var stableState = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stable.data().characterState());
        assertEquals(true, stableState.path("stable").asBoolean());
        assertEquals(0, stableState.path("deathSavingThrowSuccesses").asInt());
        CharacterSheet recovered = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                new RuntimeCharacterMutation(1, 0, List.of(), List.of()), UUID.randomUUID(), 1);
        var recoveredState = new com.fasterxml.jackson.databind.ObjectMapper().readTree(recovered.data().characterState());
        assertEquals(1, recoveredState.path("currentHitPoints").asInt());
        assertEquals(false, recoveredState.path("stable").asBoolean());

        CharacterSheet revival = new CharacterSheet(CharacterSheetId.generate(), adventureId,
                new SessionId(adventureId.value()), owner, SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Borin", 1, false, "Elf", "파이터", "군인", "STR=15",
                        "{\"hitPointMaximum\":12}", "{\"ownedEquipment\":[]}", "{\"currentHitPoints\":0,\"equippedItems\":{}}"), 0, null, null);
        repository.save(revival);
        CharacterSheet healed = service.applyRuntimeMutation(revival.id(), new SessionId(adventureId.value()), owner,
                new RuntimeCharacterMutation(0, 0, List.of(), List.of(), 0, 20), UUID.randomUUID(), 0);
        var healedState = new com.fasterxml.jackson.databind.ObjectMapper().readTree(healed.data().characterState());
        assertEquals(1, healedState.path("currentHitPoints").asInt());
        assertEquals(false, healedState.path("stable").asBoolean());
    }

    @Test
    void marks_dead_after_third_death_save_failure() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(repository,
                id -> SheetEdition.DND_5E_2014, id -> SessionCharacterPolicy.started("DND_5E_2014"));
        AdventureId adventureId = adventure();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), adventureId,
                new SessionId(adventureId.value()), owner, SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 1, false, "Elf", "파이터", "군인", "STR=15",
                        "{\"hitPointMaximum\":12}", "{\"ownedEquipment\":[]}",
                        "{\"currentHitPoints\":0,\"deathSavingThrowFailures\":2,\"equippedItems\":{}}"), 0, null, null);
        repository.save(sheet);

        CharacterSheet dead = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                new RuntimeCharacterMutation(0, 0, List.of(), List.of(), 0, 9), UUID.randomUUID(), 0);
        var deadState = new com.fasterxml.jackson.databind.ObjectMapper().readTree(dead.data().characterState());
        assertEquals(true, deadState.path("dead").asBoolean());
        assertEquals(3, deadState.path("deathSavingThrowFailures").asInt());
    }

    @Test
    void applies_runtime_hp_and_inventory_mutation_after_session_started() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(repository, id -> SheetEdition.DND_5E_2014,
                id -> SessionCharacterPolicy.started("DND_5E_2014"));
        AdventureId adventureId = adventure();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), adventureId, new SessionId(adventureId.value()), owner, SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 1, false, "Elf", "파이터", "군인", "STR=15", "{}",
                        "{\"ownedEquipment\":[\"단검\"]}", "{\"currentHitPoints\":10,\"currency\":5,\"equippedItems\":{}}"), 0, null, null);
        repository.save(sheet);
        CharacterSheet result = service.applyRuntimeMutation(sheet.id(), new SessionId(sheet.adventureId().value()), owner,
                new RuntimeCharacterMutation(-3, 2, List.of("횃불"), List.of()), UUID.randomUUID(), 0);
        assertEquals(7, new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.data().characterState()).get("currentHitPoints").intValue());
        assertEquals(2, new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.data().characterBuild()).get("ownedEquipment").size());
    }

    @Test
    void caps_positive_runtime_hp_mutation_at_derived_hit_point_maximum() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(repository, id -> SheetEdition.DND_5E_2014,
                id -> SessionCharacterPolicy.started("DND_5E_2014"));
        AdventureId adventureId = adventure();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), adventureId, new SessionId(adventureId.value()), owner,
                SheetEdition.DND_5E_2014, new CharacterSheetData2014("Aria", 1, false, "Elf", "파이터", "군인", "STR=15",
                        "{\"hitPointMaximum\":12}", "{\"ownedEquipment\":[]}", "{\"currentHitPoints\":10,\"equippedItems\":{}}"), 0, null, null);
        repository.save(sheet);

        CharacterSheet result = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                new RuntimeCharacterMutation(5, 0, List.of(), List.of()), UUID.randomUUID(), 0);

        assertEquals(12, new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.data().characterState()).get("currentHitPoints").intValue());
    }

    @Test
    void clamps_runtime_damage_at_zero_hit_points() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(repository, id -> SheetEdition.DND_5E_2014,
                id -> SessionCharacterPolicy.started("DND_5E_2014"));
        AdventureId adventureId = adventure();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), new SessionId(adventureId.value()), owner, SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 1, false, "Elf", "파이터", "군인", "STR=15", "{}", "{\"ownedEquipment\":[]}", "{\"currentHitPoints\":1,\"equippedItems\":{}}"));
        repository.save(sheet);
        CharacterSheet result = service.applyRuntimeMutation(sheet.id(), new SessionId(sheet.adventureId().value()), owner,
                new RuntimeCharacterMutation(-4, 0, List.of(), List.of()), UUID.randomUUID(), 0);
        assertEquals(0, new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.data().characterState())
                .get("currentHitPoints").intValue());
    }

    @Test
    void initializes_missing_runtime_resources_from_derived_statistics_and_replays_idempotently() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(repository, id -> SheetEdition.DND_5E_2014,
                id -> SessionCharacterPolicy.started("DND_5E_2014"));
        AdventureId adventureId = adventure();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), adventureId, new SessionId(adventureId.value()), owner, SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 1, false, "Elf", "파이터", "군인", "STR=15",
                        "{\"hitPointMaximum\":12}", "{\"ownedEquipment\":[]}", "{\"equippedItems\":{}}"), 0, null, null);
        repository.save(sheet);
        UUID commandId = UUID.randomUUID();
        RuntimeCharacterMutation mutation = new RuntimeCharacterMutation(-1, 2, List.of("횃불"), List.of());

        CharacterSheet result = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                mutation, commandId, 0);
        CharacterSheet replay = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                mutation, commandId, 0);

        var state = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.data().characterState());
        assertEquals(11, state.get("currentHitPoints").intValue());
        assertEquals(2, state.get("currency").intValue());
        assertEquals(1, new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.data().characterBuild()).get("ownedEquipment").size());
        assertEquals(1, result.version());
        assertEquals(1, replay.version());
    }

    @Test
    void consumes_spell_slot_once_and_rejects_when_no_slot_remains() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(repository,
                id -> SheetEdition.DND_5E_2014, id -> SessionCharacterPolicy.started("DND_5E_2014"));
        AdventureId adventureId = adventure();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), adventureId,
                new SessionId(adventureId.value()), owner, SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("마루", 1, false, "인간", "위저드", "학자", "INT=16", "{}",
                        "{\"learnedSpells\":[\"마법 화살\"],\"ownedEquipment\":[]}",
                        "{\"currentHitPoints\":8,\"equippedItems\":{}}"), 0, null, null);
        repository.save(sheet);
        UUID commandId = UUID.randomUUID();
        RuntimeCharacterMutation mutation = new RuntimeCharacterMutation(0, 0, List.of(), List.of(), 1);

        CharacterSheet consumed = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                mutation, commandId, 0);
        CharacterSheet replay = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                mutation, commandId, 0);

        var state = new com.fasterxml.jackson.databind.ObjectMapper().readTree(consumed.data().characterState());
        assertEquals(1, state.path("spellSlots").path("1").intValue());
        assertEquals(1, consumed.version());
        assertEquals(1, replay.version());
        CharacterSheet secondCast = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                mutation, UUID.randomUUID(), 1);
        assertEquals(0, new com.fasterxml.jackson.databind.ObjectMapper().readTree(secondCast.data().characterState())
                .path("spellSlots").path("1").intValue());
        assertThrows(IllegalArgumentException.class, () -> service.applyRuntimeMutation(sheet.id(),
                new SessionId(adventureId.value()), owner, mutation, UUID.randomUUID(), 2));
        assertEquals(2, repository.findById(sheet.id()).orElseThrow().version());
    }

    @Test
    void clamps_damage_from_derived_hp_baseline_at_zero_when_runtime_hp_is_missing() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(repository, id -> SheetEdition.DND_5E_2014,
                id -> SessionCharacterPolicy.started("DND_5E_2014"));
        AdventureId adventureId = adventure();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), new SessionId(adventureId.value()), owner, SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 1, false, "Elf", "파이터", "군인", "STR=15",
                        "{\"hitPointMaximum\":12}", "{\"ownedEquipment\":[]}", "{\"equippedItems\":{}}"));
        repository.save(sheet);

        CharacterSheet result = service.applyRuntimeMutation(sheet.id(), new SessionId(adventureId.value()), owner,
                new RuntimeCharacterMutation(-13, 0, List.of(), List.of()), UUID.randomUUID(), 0);
        assertEquals(0, new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.data().characterState())
                .get("currentHitPoints").intValue());
    }
    @Test
    void rejects_initial_attribute_change_when_session_policy_freezes_it() {
        InMemoryRepository repository = new InMemoryRepository();
        AdventureId adventureId = new AdventureId(UUID.randomUUID());
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(
                repository, id -> SheetEdition.DND_5E_2024,
                id -> new SessionCharacterPolicy(true, false, false));
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(adventureId, SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false)));

        assertThrows(IllegalStateException.class, () -> service.manageCharacter(sheet.id(), new CharacterSheetUpdate(
                SheetEdition.DND_5E_2024, new CharacterSheetData2024("Borin", 1, false),
                InputMode.STRUCTURED_SHEET, UUID.randomUUID(), 0)));
    }

    @Test
    void rejects_open_and_update_after_session_termination() {
        InMemoryRepository repository = new InMemoryRepository();
        AdventureId adventureId = adventure();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(
                repository, id -> SheetEdition.DND_5E_2024,
                id -> new SessionCharacterPolicy(false, false, false));
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), adventureId, SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false));
        repository.save(sheet);

        assertThrows(IllegalStateException.class, () -> service.openSheet(sheet.id(), SheetEdition.DND_5E_2024));
        assertThrows(IllegalStateException.class, () -> service.manageCharacter(sheet.id(), new CharacterSheetUpdate(
                SheetEdition.DND_5E_2024, sheet.data(), InputMode.STRUCTURED_SHEET, UUID.randomUUID(), 0)));
    }

    @Test
    void allows_reading_a_party_sheet_after_its_session_has_started() {
        InMemoryRepository repository = new InMemoryRepository();
        AdventureId adventureId = adventure();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(
                repository, id -> SheetEdition.DND_5E_2014,
                id -> SessionCharacterPolicy.started("DND_5E_2014"));
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), adventureId,
                SheetEdition.DND_5E_2014, new CharacterSheetData2014("Aria", 1, false));
        repository.save(sheet);

        assertEquals(sheet.id(), service.openSheet(sheet.id(), SheetEdition.DND_5E_2014).id());
    }

    @Test
    void enforces_all_six_initial_attribute_policies() {
        InMemoryRepository repository = new InMemoryRepository();
        AdventureId adventureId = adventure();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(
                repository, id -> SheetEdition.DND_5E_2024,
                id -> new SessionCharacterPolicy(true, false, false, false, false, false, false));
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(adventureId, SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false, "Elf", "Wizard", "Sage", "STR:8")));

        assertThrows(IllegalStateException.class, () -> service.manageCharacter(sheet.id(), new CharacterSheetUpdate(
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false, "Dwarf", "Fighter", "Soldier", "STR:15"),
                InputMode.STRUCTURED_SHEET, UUID.randomUUID(), 0)));
    }
    @Test
    void supportsDedicatedDataFor2014And2024Editions() {
        assertCreates(
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 5, true));
        assertCreates(
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Borin", 7, true));
    }

    @Test
    void rejectsSheetWhenAdventureEditionDoesNotMatch() {
        AdventureId adventureId = adventure();
        CharacterSheetApplicationService service = service(
                new InMemoryRepository(), id -> SheetEdition.DND_5E_2014);

        assertThrows(
                CharacterSheetEditionMismatchException.class,
                () -> service.createSheet(new CreateCharacterSheetCommand(
                        adventureId,
                        SheetEdition.DND_5E_2024,
                        new CharacterSheetData2024("Aria", 5, false))));
    }

    @Test
    void rejectsSheetWhenItsSessionHasAPinnedDifferentEdition() {
        AdventureId sessionId = adventure();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(
                new InMemoryRepository(), ignored -> SheetEdition.DND_5E_2024,
                ignored -> new SessionCharacterPolicy(true, true, true, true, true, true, true, "DND_5E_2014"));

        assertThrows(CharacterSheetEditionMismatchException.class, () -> service.createSheet(
                new CreateCharacterSheetCommand(sessionId, SheetEdition.DND_5E_2024,
                        new CharacterSheetData2024("Aria", 1, false))));
    }

    @Test
    void rejectsOperationWhenAdventureEditionHttpLookupFails() {
        CharacterSheetApplicationService service = service(
                new InMemoryRepository(),
                id -> { throw new AdventureEditionUnavailableException(); });

        assertThrows(
                AdventureEditionUnavailableException.class,
                () -> service.createSheet(new CreateCharacterSheetCommand(
                        adventure(),
                        SheetEdition.DND_5E_2014,
                        new CharacterSheetData2014("Aria", 5, false))));
    }

    @Test
    void rejectsDialogueOnlyInputForStructuredSheetUpdate() {
        InMemoryRepository repository = new InMemoryRepository();
        CharacterSheetApplicationService service = service(
                repository, id -> SheetEdition.DND_5E_2014);
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(
                adventure(),
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 5, false)));

        assertThrows(
                StructuredSheetRequiredException.class,
                () -> service.manageCharacter(
                        sheet.id(),
                        new CharacterSheetUpdate(
                                SheetEdition.DND_5E_2014,
                                new CharacterSheetData2014("Aria", 6, true),
                                InputMode.DIALOGUE_ONLY,
                                UUID.randomUUID(),
                                0)));
        assertEquals(5, sheet.data().level());
    }

    @Test
    void replays_the_same_character_command_without_double_applying() {
        InMemoryRepository repository = new InMemoryRepository();
        CharacterSheetApplicationService service = service(repository, id -> SheetEdition.DND_5E_2014);
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(
                adventure(),
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 5, false)));

        UUID commandId = UUID.randomUUID();
        CharacterSheetUpdate update = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 6, true),
                InputMode.STRUCTURED_SHEET,
                commandId,
                0);

        CharacterSheet first = service.manageCharacter(sheet.id(), update);
        CharacterSheet second = service.manageCharacter(sheet.id(), update);

        assertEquals(6, first.data().level());
        assertEquals(6, second.data().level());
        assertEquals(commandId, second.operationKey());
    }

    @Test
    void rejects_replay_of_a_character_command_after_session_termination() {
        InMemoryRepository repository = new InMemoryRepository();
        AtomicBoolean active = new AtomicBoolean(true);
        AdventureId adventureId = adventure();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(
                repository, id -> SheetEdition.DND_5E_2014,
                id -> new SessionCharacterPolicy(active.get(), true, true));
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(
                adventureId, SheetEdition.DND_5E_2014, new CharacterSheetData2014("Aria", 5, false)));
        UUID commandId = UUID.randomUUID();
        CharacterSheetUpdate update = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2014, new CharacterSheetData2014("Aria", 6, true),
                InputMode.STRUCTURED_SHEET, commandId, 0);

        service.manageCharacter(sheet.id(), update);
        active.set(false);

        assertThrows(IllegalStateException.class, () -> service.manageCharacter(sheet.id(), update));
    }

    @Test
    void rejects_stale_character_update_when_version_has_moved_on() {
        InMemoryRepository repository = new InMemoryRepository();
        CharacterSheetApplicationService service = service(repository, id -> SheetEdition.DND_5E_2014);
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(
                adventure(),
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 5, false)));

        service.manageCharacter(
                sheet.id(),
                new CharacterSheetUpdate(
                        SheetEdition.DND_5E_2014,
                        new CharacterSheetData2014("Aria", 6, true),
                        InputMode.STRUCTURED_SHEET,
                        UUID.randomUUID(),
                        0));

        assertThrows(
                IllegalStateException.class,
                () -> service.manageCharacter(
                        sheet.id(),
                        new CharacterSheetUpdate(
                                SheetEdition.DND_5E_2014,
                                new CharacterSheetData2014("Aria", 7, false),
                                InputMode.STRUCTURED_SHEET,
                                UUID.randomUUID(),
                                0)));
    }

    @Test
    void rejects_reusing_a_character_command_id_for_different_payload() {
        InMemoryRepository repository = new InMemoryRepository();
        CharacterSheetApplicationService service = service(repository, id -> SheetEdition.DND_5E_2014);
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(
                adventure(),
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 5, false)));

        UUID commandId = UUID.randomUUID();
        service.manageCharacter(
                sheet.id(),
                new CharacterSheetUpdate(
                        SheetEdition.DND_5E_2014,
                        new CharacterSheetData2014("Aria", 6, true),
                        InputMode.STRUCTURED_SHEET,
                        commandId,
                        0));

        assertThrows(
                IllegalStateException.class,
                () -> service.manageCharacter(
                        sheet.id(),
                        new CharacterSheetUpdate(
                                SheetEdition.DND_5E_2014,
                                new CharacterSheetData2014("Aria", 7, false),
                                InputMode.STRUCTURED_SHEET,
                                commandId,
                                1)));
    }

    @Test
    void replays_an_older_character_command_from_history_even_after_a_later_update() {
        InMemoryRepository repository = new InMemoryRepository();
        CharacterSheetApplicationService service = service(repository, id -> SheetEdition.DND_5E_2014);
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(
                adventure(),
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 5, false)));

        UUID firstCommandId = UUID.randomUUID();
        CharacterSheetUpdate firstUpdate = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 6, true),
                InputMode.STRUCTURED_SHEET,
                firstCommandId,
                0);
        CharacterSheet first = service.manageCharacter(sheet.id(), firstUpdate);

        CharacterSheetUpdate secondUpdate = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2014,
                new CharacterSheetData2014("Aria", 7, false),
                InputMode.STRUCTURED_SHEET,
                UUID.randomUUID(),
                1);
        service.manageCharacter(sheet.id(), secondUpdate);

        CharacterSheet replay = service.manageCharacter(sheet.id(), firstUpdate);

        assertEquals(6, first.data().level());
        assertEquals(6, replay.data().level());
        assertEquals(firstCommandId, replay.operationKey());
        assertEquals(1L, replay.version());
    }

    private static void assertCreates(SheetEdition edition, CharacterSheetData data) {
        CharacterSheetApplicationService service = service(new InMemoryRepository(), id -> edition);
        CharacterSheet sheet = service.createSheet(new CreateCharacterSheetCommand(adventure(), edition, data));
        assertEquals(edition, sheet.edition());
        assertEquals(data, sheet.data());
    }

    private static CharacterSheetApplicationService service(
            CharacterSheetRepository repository, AdventureEditionHttpPort editionPort) {
        return new CharacterSheetApplicationService(repository, editionPort);
    }

    private static AdventureId adventure() { return new AdventureId(UUID.randomUUID()); }

    private static final class InMemoryRepository implements CharacterSheetRepository {
        private final Map<CharacterSheetId, CharacterSheet> values = new HashMap<>();
        private final Map<UUID, CharacterSheet> commandHistory = new HashMap<>();
        @Override public Optional<CharacterSheet> findById(CharacterSheetId id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<CharacterSheet> findByCommandId(UUID commandId) { return Optional.ofNullable(commandHistory.get(commandId)); }
        @Override public void save(CharacterSheet sheet) {
            values.put(sheet.id(), copy(sheet));
        }
        @Override public void save(CharacterSheet sheet, long persistedVersion, UUID operationKey, String operationFingerprint) {
            sheet.markPersisted(persistedVersion, operationKey, operationFingerprint);
            CharacterSheet snapshot = copy(sheet);
            values.put(sheet.id(), snapshot);
            if (operationKey != null) {
                commandHistory.put(operationKey, copy(sheet));
            }
        }
        @Override public void deleteById(CharacterSheetId id) { values.remove(id); }

        private static CharacterSheet copy(CharacterSheet sheet) {
            return new CharacterSheet(
                    sheet.id(), sheet.adventureId(), sheet.sessionId(), sheet.ownerPlayerId(), sheet.edition(), sheet.data(), sheet.version(),
                    sheet.operationKey(), sheet.operationFingerprint());
        }
    }
}
