package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
import com.dndmaster.adventure.application.combat.CombatRangedAttackValidator;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import com.dndmaster.adventure.domain.combat.TurnResources;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionReviewPreparation;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CombatRangedAttackValidatorTest {
    private final UUID ownerId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID enemyId = UUID.randomUUID();
    private final UUID adventureId = UUID.randomUUID();
    private final Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(),
            new OwnerPlayerId(ownerId), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
            new CharacterSheetId(actorId), new AdventureContext("장면", null, null, null));
    private final CombatEncounterRepository encounters = mock(CombatEncounterRepository.class);
    private final ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
    private final CombatMapViewPort maps = mock(CombatMapViewPort.class);
    private final CombatRangedAttackValidator validator = new CombatRangedAttackValidator(encounters, packages, maps);
    private final UUID targetTokenId = UUID.randomUUID();
    private StructuredSpellDefinition definition;
    private CombatEncounter encounter;
    private CombatMapViewPort.View map;

    @BeforeEach void setUp() {
        definition = definition(StructuredSpellDefinition.ReviewStatus.APPROVED);
        var actor = new CombatParticipant(actorId, "영웅", CombatParticipant.Controller.PLAYER, 15, null,
                TurnResources.initial());
        var enemy = new CombatParticipant(enemyId, "적", CombatParticipant.Controller.AI, 10, "enemy",
                TurnResources.initial(), new CombatEnemyStatBlock(12, 10, 2, "1d6", new CombatStatBlockSource(UUID.randomUUID(), 1, "p1")));
        encounter = new CombatEncounter(UUID.randomUUID(), adventureId, CombatEncounter.Status.ACTIVE, 1,
                actorId, List.of(actor, enemy), 5, 0);
        map = view(5, 0, List.of());
        when(encounters.findActive(adventureId)).thenReturn(Optional.of(encounter));
        when(packages.findSpellDefinitionPreparationBySession(adventure.sessionId().value())).thenReturn(Optional.of(
                new SpellDefinitionReviewPreparation(UUID.randomUUID(), UUID.randomUUID(), ownerId, 2, true,
                        List.of(definition), adventure.sessionId().value())));
        when(maps.playerView(adventureId, ownerId)).thenReturn(Optional.of(map));
    }

    @Test void rejects_unapproved_definition() {
        definition = definition(StructuredSpellDefinition.ReviewStatus.PENDING);
        mockReview();
        assertRejected("ACTION_DEFINITION_UNAVAILABLE", 5, 0, 2);
    }

    @Test void rejects_stale_encounter_definition_and_map_versions() {
        assertRejected("ENCOUNTER_VERSION_CHANGED", 4, 0, 2);
        assertRejected("DEFINITION_VERSION_CHANGED", 5, 0, 1);
        map = view(5, 1, List.of());
        when(maps.playerView(adventureId, ownerId)).thenReturn(Optional.of(map));
        assertRejected("MAP_VERSION_CHANGED", 5, 0, 2);
    }

    @Test void rejects_out_of_range_target_before_attack_submission() {
        map = view(5, 0, List.of(), 8, 1);
        when(maps.playerView(adventureId, ownerId)).thenReturn(Optional.of(map));
        assertRejected("AIM_OUT_OF_RANGE", 5, 0, 2, 8, 1);
    }

    @Test void rejects_blocked_path_before_attack_submission() {
        map = view(5, 0, List.of(new CombatMapViewPort.Obstacle(2, 1)));
        when(maps.playerView(adventureId, ownerId)).thenReturn(Optional.of(map));
        assertRejected("AIM_PATH_BLOCKED", 5, 0, 2);
    }

    @Test void rejects_target_cell_that_cannot_be_associated_with_selected_participant() {
        var secondEnemy = new CombatParticipant(UUID.randomUUID(), "다른 적", CombatParticipant.Controller.AI, 9,
                "enemy", TurnResources.initial(), new CombatEnemyStatBlock(12, 10, 2, "1d6",
                        new CombatStatBlockSource(UUID.randomUUID(), 1, "p1")));
        encounter = new CombatEncounter(encounter.encounterId(), adventureId, CombatEncounter.Status.ACTIVE, 1,
                actorId, List.of(encounter.participants().getFirst(), encounter.participants().get(1), secondEnemy), 5, 0);
        when(encounters.findActive(adventureId)).thenReturn(Optional.of(encounter));
        assertRejected("TARGET_CELL_PARTICIPANT_MISMATCH", 5, 0, 2);
    }

    @Test void actual_validation_matches_preview_for_range_and_path_decisions() {
        assertActualValidationMatchesPreview(view(5, 0, List.of()), 4, 1);
        assertActualValidationMatchesPreview(view(5, 0, List.of(), 8, 1), 8, 1);
        assertActualValidationMatchesPreview(view(5, 0, List.of(new CombatMapViewPort.Obstacle(2, 1))), 4, 1);
    }

    @Test void accepts_current_approved_definition_and_forwards_validated_selection() {
        assertDoesNotThrow(() -> validator.validate(adventure, ownerId, actorId, 5, actorId, enemyId,
                4, 1, "bow", 2, 0));
    }

    private void assertRejected(String code, long encounterVersion, long mapVersion, long definitionVersion) {
        assertRejected(code, encounterVersion, mapVersion, definitionVersion, 4, 1);
    }

    private void assertRejected(String code, long encounterVersion, long mapVersion, long definitionVersion,
            int x, int y) {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> validator.validate(
                adventure, ownerId, actorId, encounterVersion, actorId, enemyId, x, y, "bow", definitionVersion, mapVersion));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getReason().startsWith(code), failure.getReason());
    }

    private void mockReview() {
        when(packages.findSpellDefinitionPreparationBySession(adventure.sessionId().value())).thenReturn(Optional.of(
                new SpellDefinitionReviewPreparation(UUID.randomUUID(), UUID.randomUUID(), ownerId, 2, true,
                        List.of(definition), adventure.sessionId().value())));
    }

    private void assertActualValidationMatchesPreview(CombatMapViewPort.View currentMap, int targetX, int targetY) {
        map = currentMap;
        when(maps.playerView(adventureId, ownerId)).thenReturn(Optional.of(currentMap));
        var actorToken = currentMap.tokens().stream().filter(token -> token.id().equals(actorId)).findFirst().orElseThrow();
        var targetToken = currentMap.tokens().stream().filter(token -> token.id().equals(targetTokenId)).findFirst().orElseThrow();
        var preview = com.dndmaster.adventure.domain.combat.CombatAimPreviewPolicy.preview(definition,
                new com.dndmaster.adventure.domain.combat.CombatAimPreviewPolicy.Cell(actorToken.x(), actorToken.y()),
                actorToken.elevationFeet(), new com.dndmaster.adventure.domain.combat.CombatAimPreviewPolicy.Cell(targetX, targetY),
                targetToken.elevationFeet(), currentMap.grid().distanceUnit(), currentMap.grid().width(), currentMap.grid().height(),
                currentMap.obstacles().stream().map(value -> new com.dndmaster.adventure.domain.combat.CombatAimPreviewPolicy.Cell(
                        value.x(), value.y())).toList());

        if (!preview.inRange()) {
            assertRejected("AIM_OUT_OF_RANGE", 5, currentMap.version(), 2, targetX, targetY);
        } else if (!preview.clearPath()) {
            assertRejected("AIM_PATH_BLOCKED", 5, currentMap.version(), 2, targetX, targetY);
        } else {
            assertDoesNotThrow(() -> validator.validate(adventure, ownerId, actorId, 5, actorId, enemyId,
                    targetX, targetY, "bow", 2, currentMap.version()));
        }
    }

    private CombatMapViewPort.View view(long distanceUnit, long version, List<CombatMapViewPort.Obstacle> obstacles) {
        return view(distanceUnit, version, obstacles, 4, 1);
    }

    private CombatMapViewPort.View view(long distanceUnit, long version, List<CombatMapViewPort.Obstacle> obstacles,
            int targetX, int targetY) {
        List<CombatMapViewPort.Position> visible = java.util.stream.IntStream.range(0, 10).boxed()
                .flatMap(y -> java.util.stream.IntStream.range(0, 10).mapToObj(x -> new CombatMapViewPort.Position(x, y))).toList();
        return new CombatMapViewPort.View(UUID.randomUUID(), new CombatMapViewPort.Grid(10, 10, 5, (int) distanceUnit),
                List.of(new CombatMapViewPort.Token(actorId, "PLAYER", 1, 1),
                        new CombatMapViewPort.Token(targetTokenId, "ENEMY", targetX, targetY)), obstacles, List.of(), List.of(),
                visible, visible, version);
    }

    private static StructuredSpellDefinition definition(StructuredSpellDefinition.ReviewStatus status) {
        return new StructuredSpellDefinition("bow", "활", UUID.randomUUID(), "p1", "v1", 1,
                null, null, "30/120 ft", null, null, null, null, null, List.of(368), "룰북 근거",
                true, status, false, StructuredSpellDefinition.ActionKind.RANGED_ATTACK,
                new StructuredSpellDefinition.RangeProfile(StructuredSpellDefinition.RangeForm.NORMAL_AND_LONG, 10, 20),
                new StructuredSpellDefinition.TargetingProfile(StructuredSpellDefinition.TargetMode.SINGLE_TARGET,
                        StructuredSpellDefinition.OriginMode.ACTOR_TOKEN, StructuredSpellDefinition.EffectShape.NONE,
                        null, null, null, null, StructuredSpellDefinition.PathBlockingRule.SOLID_BLOCKS,
                        StructuredSpellDefinition.PropagationRule.DIRECT), true);
    }
}
