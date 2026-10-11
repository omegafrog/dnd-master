package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.combat.CombatAimPreviewPolicy;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionReviewPreparation;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class CombatAimPreviewControllerTest {
    @Test
    void passes_only_public_closed_blockers_to_preview_policy() {
        var view = new CombatMapViewPort.View(UUID.randomUUID(), new CombatMapViewPort.Grid(5, 2, 30, 5),
                List.of(), List.of(new CombatMapViewPort.Obstacle(1, 0), new CombatMapViewPort.Obstacle(3, 0)),
                List.of(new CombatMapViewPort.Door(2, 0, false), new CombatMapViewPort.Door(4, 0, false),
                        new CombatMapViewPort.Door(1, 1, true)), List.of(),
                List.of(new CombatMapViewPort.Position(0, 0), new CombatMapViewPort.Position(1, 0), new CombatMapViewPort.Position(2, 0)),
                List.of(new CombatMapViewPort.Position(0, 0)), 1L);

        assertEquals(List.of(new CombatAimPreviewPolicy.Cell(1, 0), new CombatAimPreviewPolicy.Cell(2, 0)),
                CombatAimPreviewController.publicBlockers(view));
    }

    @Test
    void includes_only_boundaries_with_public_adjacent_cells() {
        var visible = new CombatMapViewPort.View(UUID.randomUUID(), new CombatMapViewPort.Grid(3, 2, 30, 5),
                List.of(), List.of(), List.of(), List.of(new CombatMapViewPort.Layer("MAP_BOUNDARIES",
                        "1,0,VERTICAL,WALL,false;2,0,VERTICAL,DOOR,false")),
                List.of(new CombatMapViewPort.Position(0, 0), new CombatMapViewPort.Position(1, 0)),
                List.of(new CombatMapViewPort.Position(0, 0), new CombatMapViewPort.Position(1, 0)), 1L);

        assertEquals(List.of(new CombatAimPreviewPolicy.Boundary(1, 0, "VERTICAL", "WALL", false)),
                CombatAimPreviewController.publicBoundaries(visible));
    }

    @Test
    void preview_uses_read_ports_without_writing_encounter_map_or_definitions() {
        UUID adventureId = UUID.randomUUID(), ownerId = UUID.randomUUID(), sessionId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID(), mapId = UUID.randomUUID();
        var adventure = mock(Adventure.class);
        when(adventure.id()).thenReturn(new AdventureId(adventureId));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(adventure.sessionId()).thenReturn(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId));
        when(adventure.party()).thenReturn(List.of(new AdventurePartyMember(new CharacterSheetId(tokenId),
                ControlMode.DIRECT, true, true, true, true, true, true)));

        var encounter = mock(CombatEncounter.class);
        when(encounter.version()).thenReturn(3L);
        when(encounter.currentParticipantId()).thenReturn(tokenId);
        var encounters = mock(CombatEncounterRepository.class);
        when(encounters.findActive(adventureId)).thenReturn(java.util.Optional.of(encounter));

        var definition = new StructuredSpellDefinition("aim-test", "테스트 행동", UUID.randomUUID(), "p.1", "v1", 1,
                "", "", "15ft", "", "", "", "", "", List.of(368), "source", true,
                StructuredSpellDefinition.ReviewStatus.APPROVED, false, StructuredSpellDefinition.ActionKind.RANGED_ATTACK,
                new StructuredSpellDefinition.RangeProfile(StructuredSpellDefinition.RangeForm.SINGLE, 20, null),
                new StructuredSpellDefinition.TargetingProfile(StructuredSpellDefinition.TargetMode.POINT,
                        StructuredSpellDefinition.OriginMode.ACTOR_TOKEN, StructuredSpellDefinition.EffectShape.NONE,
                        null, null, null, null, StructuredSpellDefinition.PathBlockingRule.NONE,
                        StructuredSpellDefinition.PropagationRule.NONE), true);
        var packages = mock(ScenarioPackageRepository.class);
        when(packages.findSpellDefinitionPreparationBySession(sessionId)).thenReturn(java.util.Optional.of(
                new SpellDefinitionReviewPreparation(UUID.randomUUID(), UUID.randomUUID(), ownerId, 2, true,
                        List.of(definition), sessionId)));

        var view = new CombatMapViewPort.View(mapId, new CombatMapViewPort.Grid(4, 2, 30, 5),
                List.of(new CombatMapViewPort.Token(tokenId, "PLAYER", 0, 0, 10)), List.of(), List.of(), List.of(),
                List.of(new CombatMapViewPort.Position(0, 0), new CombatMapViewPort.Position(2, 0)),
                List.of(new CombatMapViewPort.Position(0, 0), new CombatMapViewPort.Position(2, 0)), 4L);
        var maps = mock(CombatMapViewPort.class);
        when(maps.playerView(adventureId, ownerId)).thenReturn(java.util.Optional.of(view));
        var adventures = mock(AdventureRepository.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(java.util.Optional.of(adventure));
        var playerResolver = mock(AuthenticatedPlayerResolver.class);
        when(playerResolver.playerId()).thenReturn(ownerId);
        var controller = new CombatAimPreviewController(encounters, adventures, playerResolver, packages, maps);

        Logger logger = (Logger) LoggerFactory.getLogger(CombatAimPreviewController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String oldDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        CombatAimPreviewController.AimPreviewResponse result;
        try {
            result = controller.preview(adventureId, new CombatAimPreviewController.AimPreviewRequest(
                    UUID.randomUUID(), definition.id(), 2, tokenId, 2, 0, 3, 4));
        } finally {
            if (oldDiagnostics == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", oldDiagnostics);
            logger.detachAppender(appender);
        }

        assertEquals(10, result.distanceFeet());
        assertTrue(result.inRange());
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("outcome=started")
                && event.getFormattedMessage().contains("definitionVersion=2")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("outcome=completed")
                && event.getFormattedMessage().contains("distanceFeet=10")
                && event.getFormattedMessage().contains("maxRangeFeet=20")));
        verify(encounters, never()).save(any());
        verify(packages, never()).updateSpellDefinitionPreparation(any(), anyLong(), any(), any());
        verify(maps, never()).updateLayout(any(), any(), anyLong(), any(), any(), any(), any(), any());
    }
}
