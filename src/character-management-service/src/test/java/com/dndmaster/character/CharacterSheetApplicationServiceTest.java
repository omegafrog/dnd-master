package com.dndmaster.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.character.application.*;
import com.dndmaster.character.domain.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CharacterSheetApplicationServiceTest {
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
                    sheet.id(), sheet.adventureId(), sheet.edition(), sheet.data(), sheet.version(),
                    sheet.operationKey(), sheet.operationFingerprint());
        }
    }
}
