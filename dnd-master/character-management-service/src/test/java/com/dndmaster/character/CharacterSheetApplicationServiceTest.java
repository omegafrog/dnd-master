package com.dndmaster.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.character.application.*;
import com.dndmaster.character.domain.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterSheetApplicationServiceTest {
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
                                InputMode.DIALOGUE_ONLY)));
        assertEquals(5, sheet.data().level());
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
        @Override public Optional<CharacterSheet> findById(CharacterSheetId id) { return Optional.ofNullable(values.get(id)); }
        @Override public void save(CharacterSheet sheet) { values.put(sheet.id(), sheet); }
    }
}
