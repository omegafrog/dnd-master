package com.dndmaster.combatmap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.combatmap.application.view.CombatMapViewService;
import com.dndmaster.combatmap.application.view.CombatMapViewStore;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.domain.AdventureId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatMapViewServiceTest {
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void combatTokenTransition_retriesSameOperationWithoutApplyingMapChangeTwice(
            org.springframework.boot.test.system.CapturedOutput output) {
        var store = mock(CombatMapViewStore.class);
        var ai = mock(com.dndmaster.combatmap.application.view.AiMapGenerationPort.class);
        var owner = new MapOwnerId(UUID.randomUUID());
        var adventureId = new AdventureId(UUID.randomUUID());
        var mapId = new com.dndmaster.combatmap.domain.MapId(UUID.randomUUID());
        UUID operationId = UUID.randomUUID(), groupId = UUID.randomUUID(), tokenId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID(), enemyId = UUID.randomUUID();
        var map = new com.dndmaster.combatmap.domain.CombatMap(mapId, adventureId,
                new com.dndmaster.combatmap.domain.RuleSetId(UUID.randomUUID()),
                new com.dndmaster.combatmap.domain.GridSpec(8, 8, 1, 5),
                new com.dndmaster.combatmap.domain.PlayerId(owner.value()),
                java.util.List.of(new com.dndmaster.combatmap.domain.CombatToken(new com.dndmaster.combatmap.domain.TokenId(tokenId),
                                com.dndmaster.combatmap.domain.TokenType.PLAYER, new com.dndmaster.combatmap.domain.GridPosition(2, 2),
                                com.dndmaster.combatmap.domain.TokenController.PLAYER,
                                new com.dndmaster.combatmap.domain.PlayerId(owner.value())),
                        new com.dndmaster.combatmap.domain.CombatToken(new com.dndmaster.combatmap.domain.TokenId(enemyId),
                                com.dndmaster.combatmap.domain.TokenType.ENEMY, new com.dndmaster.combatmap.domain.GridPosition(5, 2),
                                com.dndmaster.combatmap.domain.TokenController.AI_GAME_MASTER, null)),
                java.util.List.of(), java.util.List.of(), 4, null);
        var initial = new com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap(map, owner, 4);
        var persisted = new java.util.concurrent.atomic.AtomicReference<com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap>();
        when(store.find(mapId)).thenReturn(java.util.Optional.of(initial));
        when(store.findByCommandId(operationId)).thenAnswer(invocation -> java.util.Optional.ofNullable(persisted.get()));
        when(store.update(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(operationId), org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            var changed = (com.dndmaster.combatmap.domain.CombatMap) invocation.getArgument(1);
            String fingerprint = invocation.getArgument(5);
            var recorded = new com.dndmaster.combatmap.domain.CombatMap(changed.id(), changed.adventureId(),
                    changed.ruleSetId(), changed.grid(), changed.ownerPlayerId(), changed.tokens(), changed.obstacles(),
                    changed.layers(), 5, operationId, fingerprint, changed.spatialFeatures(), changed.spatialPreparationBlocked());
            persisted.set(new com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap(recorded, owner, 5));
            return 5L;
        });
        var service = new CombatMapViewService(store, source -> null, ai);
        var groups = java.util.List.of(new com.dndmaster.combatmap.domain.CombatMap.CombatGroup(groupId, tokenId,
                java.util.List.of(new com.dndmaster.combatmap.domain.CombatMap.CombatMember(characterId,
                        com.dndmaster.combatmap.domain.CombatMap.Formation.FRONT))));

        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        CombatMapViewService.TokenTransitionResult applied;
        CombatMapViewService.TokenTransitionResult retried;
        try {
            applied = service.transitionCombatTokens(mapId, owner, 4, operationId,
                    CombatMapViewService.TokenTransitionDirection.ENTER, groups);
            retried = service.transitionCombatTokens(mapId, owner, 4, operationId,
                    CombatMapViewService.TokenTransitionDirection.ENTER, groups);
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }

        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(retried.status()).isEqualTo("APPLIED");
        assertThat(retried.version()).isEqualTo(5);
        assertThat(retried.tokens()).hasSize(2);
        assertThat(retried.tokens()).filteredOn(token -> token.type() == com.dndmaster.combatmap.domain.TokenType.PLAYER)
                .hasSize(1);
        verify(store, org.mockito.Mockito.times(1)).update(org.mockito.ArgumentMatchers.eq(owner),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(operationId), org.mockito.ArgumentMatchers.anyString());
        assertThat(output).contains("dev_combat_token_transition outcome=started", "operationId=" + operationId,
                "groupCount=1", "memberCount=1", "placementCount=0", "outcome=completed", "outcome=replayed",
                "actualVersion=5");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void 같은_지도와_다시_방문한_지도는_저장된_상태를_유지한다(boolean active,
            org.springframework.boot.test.system.CapturedOutput output) {
        var store = mock(CombatMapViewStore.class);
        var ai = mock(com.dndmaster.combatmap.application.view.AiMapGenerationPort.class);
        var owner = new MapOwnerId(UUID.randomUUID());
        var adventure = new AdventureId(UUID.randomUUID());
        UUID definition = UUID.randomUUID();
        var map = new com.dndmaster.combatmap.domain.CombatMap(
                new com.dndmaster.combatmap.domain.MapId(UUID.randomUUID()), adventure,
                new com.dndmaster.combatmap.domain.RuleSetId(UUID.randomUUID()), new com.dndmaster.combatmap.domain.GridSpec(22, 23, 42, 5),
                new com.dndmaster.combatmap.domain.PlayerId(owner.value()), java.util.List.of(), java.util.Set.of(),
                java.util.List.of(new com.dndmaster.combatmap.domain.MapLayer("MAP_DEFINITION_ID", definition.toString(),
                        com.dndmaster.combatmap.domain.LayerVisibility.AI_ONLY), new com.dndmaster.combatmap.domain.MapLayer("MAP_ENTERED", "true",
                        com.dndmaster.combatmap.domain.LayerVisibility.AI_ONLY)), 8, null);
        var state = new com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap(map, owner, 8);
        when(store.findByDefinition(adventure, owner, definition)).thenReturn(java.util.Optional.of(state));
        when(store.findByAdventureId(adventure, owner)).thenReturn(active ? java.util.Optional.of(state) : java.util.Optional.empty());
        var service = new CombatMapViewService(store, source -> null, ai);
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        try {
            System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
            var reused = service.reuseDefinition(adventure, owner, definition, 1,
                    com.dndmaster.combatmap.application.view.MapActivationContext.atStage(1),
                    new com.dndmaster.combatmap.application.spatial.SpatialPreparationCommand(UUID.randomUUID(), "switch", 8)).orElseThrow();
            assertThat(reused.mapId()).isEqualTo(map.id());
            assertThat(reused.version()).isEqualTo(8);
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
        verifyNoInteractions(ai);
        if (active) org.mockito.Mockito.verify(store, org.mockito.Mockito.never()).activate(adventure, owner, map.id(), 1);
        else verify(store).activate(adventure, owner, map.id(), 1);
        org.mockito.Mockito.verify(store, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(output).contains("outcome=reused", "definitionId=" + definition, "recoveryAction=preserve_map_state");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 3, 6, 7})
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void 시작_위치가_비어있으면_다섯번까지_재시도하고_후보가_생기면_멈춘다(int successAttempt,
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var store = mock(CombatMapViewStore.class);
        var ai = mock(com.dndmaster.combatmap.application.view.AiMapGenerationPort.class);
        var owner = new MapOwnerId(UUID.randomUUID());
        var grid = new com.dndmaster.combatmap.domain.GridSpec(22, 23, 42, 5);
        var map = new com.dndmaster.combatmap.domain.CombatMap(
                new com.dndmaster.combatmap.domain.MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new com.dndmaster.combatmap.domain.RuleSetId(UUID.randomUUID()), grid,
                new com.dndmaster.combatmap.domain.PlayerId(owner.value()), java.util.List.of(), java.util.Set.of(),
                java.util.List.of(), 0, null);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(ai.proposeEntryPlacement(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            var layers = calls.incrementAndGet() >= successAttempt ? java.util.List.of(new com.dndmaster.combatmap.domain.MapLayer(
                    "GM_PLAYER_START_PROPOSAL", "{\"position\":\"10,19\",\"confidence\":0.9,\"source\":\"MAP_IMAGE\",\"evidence\":[\"계단 끝 바닥\"]}",
                    com.dndmaster.combatmap.domain.LayerVisibility.AI_ONLY)) : java.util.List.<com.dndmaster.combatmap.domain.MapLayer>of();
            return new com.dndmaster.combatmap.application.view.PreparedMapData(grid, java.util.List.of(), java.util.Set.of(), layers);
        });
        var service = new CombatMapViewService(store, source -> null, ai);
        var method = CombatMapViewService.class.getDeclaredMethod("refreshEntryEvidence",
                com.dndmaster.combatmap.domain.CombatMap.class, com.dndmaster.combatmap.application.view.MapActivationContext.class);
        method.setAccessible(true);
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        try {
            System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
            method.invoke(service, map, com.dndmaster.combatmap.application.view.MapActivationContext.from(
                    1, java.util.Optional.empty(), java.util.Optional.empty(), UUID.randomUUID(), 1, 0,
                    "저장고", "저장고", "PLAYER_ACTION=남쪽 계단으로 내려간다"));
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
        assertThat(calls.get()).isEqualTo(Math.min(successAttempt, 6));
        assertThat(output).contains("dev_map_entry_retry outcome=started", "maxRetries=5", "expectedCandidatesMin=1");
        assertThat(output).contains(successAttempt <= 6 ? "actualCandidates=1" : "outcome=exhausted");
        verifyNoInteractions(store);
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void 대체_격자는_시작_위치_판정_전에_지도_보정을_요청한다(org.springframework.boot.test.system.CapturedOutput output) {
        var store = mock(CombatMapViewStore.class);
        var ai = mock(com.dndmaster.combatmap.application.view.AiMapGenerationPort.class);
        var owner = new MapOwnerId(UUID.randomUUID());
        var map = new com.dndmaster.combatmap.domain.CombatMap(
                new com.dndmaster.combatmap.domain.MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new com.dndmaster.combatmap.domain.RuleSetId(UUID.randomUUID()), new com.dndmaster.combatmap.domain.GridSpec(28, 19, 60, 5),
                new com.dndmaster.combatmap.domain.PlayerId(owner.value()), java.util.List.of(), java.util.Set.of(),
                java.util.List.of(new com.dndmaster.combatmap.domain.MapLayer("GRID_SOURCE", "FALLBACK",
                        com.dndmaster.combatmap.domain.LayerVisibility.PLAYER_VISIBLE)), 0, null);
        when(store.find(map.id())).thenReturn(java.util.Optional.of(
                new com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap(map, owner, 0)));
        var service = new CombatMapViewService(store, source -> null, ai);
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        try {
            System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
            assertThrows(com.dndmaster.combatmap.application.view.MapPlacementRequiredException.class,
                    () -> service.activateForAdventure(map.id(), owner,
                            com.dndmaster.combatmap.application.view.MapActivationContext.atStage(1)));
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
        verifyNoInteractions(ai);
        assertThat(output).contains("dev_map_grid_review outcome=required", "actualGridSource=FALLBACK",
                "width=28 height=19", "recoveryAction=open_crop_and_grid_preparation");
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void 저장된_정렬과_이미지가_일치하면_FALLBACK_표시가_있어도_시작_위치를_판정한다(
            org.springframework.boot.test.system.CapturedOutput output) {
        var store = mock(CombatMapViewStore.class);
        var alignments = mock(com.dndmaster.combatmap.application.view.MapGridAlignmentStore.class);
        var ai = mock(com.dndmaster.combatmap.application.view.AiMapGenerationPort.class);
        var owner = new MapOwnerId(UUID.randomUUID());
        var id = new com.dndmaster.combatmap.domain.MapId(UUID.randomUUID());
        var map = new com.dndmaster.combatmap.domain.CombatMap(id, new AdventureId(UUID.randomUUID()),
                new com.dndmaster.combatmap.domain.RuleSetId(UUID.randomUUID()), new com.dndmaster.combatmap.domain.GridSpec(28, 19, 60, 5),
                new com.dndmaster.combatmap.domain.PlayerId(owner.value()), java.util.List.of(), java.util.Set.of(),
                java.util.List.of(new com.dndmaster.combatmap.domain.MapLayer("GRID_SOURCE", "FALLBACK",
                        com.dndmaster.combatmap.domain.LayerVisibility.PLAYER_VISIBLE),
                        new com.dndmaster.combatmap.domain.MapLayer("MAP_LAYOUT_CONFIRMED", "USER|ALIGNMENT_VERSION=1",
                                com.dndmaster.combatmap.domain.LayerVisibility.PLAYER_VISIBLE),
                        new com.dndmaster.combatmap.domain.MapLayer("PLAYER_START_CONFIRMED", "1,1",
                                com.dndmaster.combatmap.domain.LayerVisibility.PLAYER_VISIBLE),
                        new com.dndmaster.combatmap.domain.MapLayer("MAP_IMAGE",
                                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/j4sAAAAASUVORK5CYII=",
                                com.dndmaster.combatmap.domain.LayerVisibility.PLAYER_VISIBLE)), 0, null);
        String imageRevision = com.dndmaster.combatmap.application.view.MapGridAlignmentService.imageRevision(map);
        when(store.find(id)).thenReturn(java.util.Optional.of(
                new com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap(map, owner, 0)));
        when(alignments.find(id)).thenReturn(java.util.Optional.of(
                new com.dndmaster.combatmap.application.view.MapGridAlignment(id, imageRevision, 0, 0, 60, 1)));
        when(store.activate(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1L);
        var service = new CombatMapViewService(store, source -> null, ai, null, alignments);
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        try {
            System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
            service.activateForAdventure(id, owner, com.dndmaster.combatmap.application.view.MapActivationContext.atStage(1));
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
        verify(store).activate(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        assertThat(output).contains("dev_map_grid_review outcome=confirmed", "actualGridSource=FALLBACK",
                "confirmedAlignmentVersion=1", "recoveryAction=use_saved_user_grid");
        verifyNoInteractions(ai);
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void 저장된_수동_격자_정렬은_시작_위치_검토에서_대체_격자로_취급하지_않는다(
            org.springframework.boot.test.system.CapturedOutput output) {
        var store = mock(CombatMapViewStore.class);
        var alignments = mock(com.dndmaster.combatmap.application.view.MapGridAlignmentStore.class);
        var ai = mock(com.dndmaster.combatmap.application.view.AiMapGenerationPort.class);
        var owner = new MapOwnerId(UUID.randomUUID());
        var id = new com.dndmaster.combatmap.domain.MapId(UUID.randomUUID());
        var map = new com.dndmaster.combatmap.domain.CombatMap(id, new AdventureId(UUID.randomUUID()),
                new com.dndmaster.combatmap.domain.RuleSetId(UUID.randomUUID()), new com.dndmaster.combatmap.domain.GridSpec(28, 19, 60, 5),
                new com.dndmaster.combatmap.domain.PlayerId(owner.value()), java.util.List.of(), java.util.Set.of(),
                java.util.List.of(new com.dndmaster.combatmap.domain.MapLayer("GRID_SOURCE", "FALLBACK",
                        com.dndmaster.combatmap.domain.LayerVisibility.PLAYER_VISIBLE),
                        new com.dndmaster.combatmap.domain.MapLayer("MAP_IMAGE",
                                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/j4sAAAAASUVORK5CYII=",
                                com.dndmaster.combatmap.domain.LayerVisibility.PLAYER_VISIBLE)), 0, null);
        String imageRevision = "confirmed-image-revision";
        var alignment = new com.dndmaster.combatmap.application.view.MapGridAlignment(id, imageRevision, 2, 3, 60, 1);
        when(store.find(id)).thenReturn(java.util.Optional.of(
                new com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap(map, owner, 0)));
        when(alignments.find(id)).thenReturn(java.util.Optional.of(alignment));
        var service = new CombatMapViewService(store, source -> null, ai, null, alignments);
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        try {
            System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
            service.updateLayout(id, owner, 0, UUID.randomUUID(), java.util.Set.of(), java.util.List.of(),
                    java.util.List.of(), null, 1L, imageRevision);
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
        var saved = org.mockito.ArgumentCaptor.forClass(com.dndmaster.combatmap.domain.CombatMap.class);
        verify(store).update(org.mockito.ArgumentMatchers.eq(owner), saved.capture(), org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        assertThat(saved.getValue().layers()).anySatisfy(layer -> {
            assertThat(layer.type()).isEqualTo("GRID_SOURCE");
            assertThat(layer.value()).isEqualTo("MANUAL");
        });
        assertThat(output).contains("dev_map_grid_confirmation outcome=completed", "previousGridSource=FALLBACK",
                "actualGridSource=MANUAL", "alignmentVersion=1");
    }

    @Test
    void mapless_situation_removes_previous_map_from_adventure_player_view() {
        CombatMapViewStore store = mock(CombatMapViewStore.class);
        var service = new CombatMapViewService(store, request -> null, request -> null);
        AdventureId adventureId = new AdventureId(UUID.randomUUID());
        MapOwnerId ownerId = new MapOwnerId(UUID.randomUUID());

        service.deactivateForMaplessSituation(adventureId, ownerId);

        verify(store).deactivateForAdventure(adventureId, ownerId);
    }
}
