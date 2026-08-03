package com.dndmaster.character.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.dndmaster.character.domain.CharacterSheet;
import com.dndmaster.character.domain.CharacterSheetData2014;
import com.dndmaster.character.domain.CharacterSheetId;
import com.dndmaster.character.domain.SessionId;
import com.dndmaster.character.domain.SheetEdition;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterSheetPersistenceRoundTripTest {
    @Test
    void createsAndReopensA2014SheetWithoutLosingBuildDerivedOrStateData() {
        InMemoryRepository repository = new InMemoryRepository();
        CharacterSheetApplicationService service = new CharacterSheetApplicationService(
                repository,
                ignored -> SheetEdition.DND_5E_2014);
        SessionId sessionId = new SessionId(UUID.randomUUID());
        UUID owner = UUID.randomUUID();
        CharacterSheetData2014 data = new CharacterSheetData2014(
                "아리아", 1, false, "인간", "파이터", "군인",
                "strength=15,dexterity=14,constitution=13,intelligence=10,wisdom=12,charisma=8",
                "{\"armorClass\":18,\"hitPointMaximum\":12}",
                "{\"schemaVersion\":2,\"ownedEquipment\":[\"체인 메일\"],\"ownedWeaponIds\":[]}",
                "{\"currentHitPoints\":12,\"equippedItems\":{\"armor\":\"체인 메일\",\"shield\":true,\"mainHandWeaponId\":null,\"offHandWeaponId\":null,\"twoHandedWeaponId\":null}}");

        CharacterSheet created = service.createSheet(new CreateCharacterSheetCommand(
                sessionId, owner, SheetEdition.DND_5E_2014, data));
        CharacterSheet reopened = service.openSheet(created.id(), SheetEdition.DND_5E_2014);

        assertSame(created, reopened);
        assertEquals(sessionId, reopened.sessionId());
        assertEquals(owner, reopened.ownerPlayerId());
        assertEquals(data.startingAbilities(), reopened.data().startingAbilities());
        assertEquals(data.derivedStatistics(), reopened.data().derivedStatistics());
        assertEquals(data.characterBuild(), reopened.data().characterBuild());
        assertEquals(data.characterState(), reopened.data().characterState());
    }

    private static final class InMemoryRepository implements CharacterSheetRepository {
        private final Map<CharacterSheetId, CharacterSheet> values = new HashMap<>();
        private final Map<UUID, CharacterSheet> commands = new HashMap<>();

        @Override public Optional<CharacterSheet> findById(CharacterSheetId id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<CharacterSheet> findByCommandId(UUID commandId) { return Optional.ofNullable(commands.get(commandId)); }
        @Override public void save(CharacterSheet sheet) { values.put(sheet.id(), sheet); }
        @Override public void save(CharacterSheet sheet, long version, UUID operationKey, String fingerprint) {
            sheet.markPersisted(version, operationKey, fingerprint);
            values.put(sheet.id(), sheet);
            commands.put(operationKey, sheet);
        }
        @Override public void deleteById(CharacterSheetId id) { values.remove(id); }
    }
}
