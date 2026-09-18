package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CombatMapPreviewPosition;
import com.dndmaster.adventure.application.combat.CombatMapPreviewResult;
import com.dndmaster.adventure.application.combat.CombatMapPreparationPort;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AdventureMovementPreviewBoundaryTest {
    @Test
    void rejects_a_preview_for_a_map_not_owned_by_the_requested_adventure() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adventureMapId = UUID.randomUUID();
        UUID requestedMapId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class);
        CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class);
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        Adventure adventure = mock(Adventure.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                adventureMapId, new CombatMapViewPort.Grid(2, 2, 50, 5), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(combatMap.preview(any())).thenReturn(new CombatMapPreviewResult(adventureMapId,
                List.of(new CombatMapPreviewPosition(0, 0)), 0, 0, "preview"));

        AdventureController controller = controller(adventures, combatMap, mapViews, playerResolver);

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(adventureId,
                new AdventureController.CombatMapMovementPreviewRequest(requestedMapId, 0L, UUID.randomUUID(),
                        new AdventureController.PositionPayload(1, 1), List.of())));
        verifyNoInteractions(combatMap);
    }

    @Test
    void rejects_malformed_preview_before_calling_combat_map() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class);
        CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class);
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        Adventure adventure = mock(Adventure.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(2, 2, 50, 5), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));

        AdventureController controller = controller(adventures, combatMap, mapViews, playerResolver);
        List<AdventureController.PositionPayload> waypoints = java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> new AdventureController.PositionPayload(1, 1)).toList();

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(adventureId,
                new AdventureController.CombatMapMovementPreviewRequest(mapId, 0L, UUID.randomUUID(),
                        new AdventureController.PositionPayload(1, 1), waypoints)));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(adventureId,
                new AdventureController.CombatMapMovementPreviewRequest(mapId, 0L, UUID.randomUUID(),
                        new AdventureController.PositionPayload(null, 1), List.of())));
        verifyNoInteractions(combatMap);
    }

    @Test
    void rejects_a_preview_with_a_null_waypoint_before_calling_combat_map() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class);
        CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class);
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        Adventure adventure = mock(Adventure.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(2, 2, 50, 5), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));

        AdventureController controller = controller(adventures, combatMap, mapViews, playerResolver);

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(adventureId,
                new AdventureController.CombatMapMovementPreviewRequest(mapId, 0L, UUID.randomUUID(),
                        new AdventureController.PositionPayload(1, 1), java.util.Arrays.asList((AdventureController.PositionPayload) null))));
        verifyNoInteractions(combatMap);
    }

    @Test
    void rejects_confirmed_move_without_server_preview_fingerprint() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("validateConfirmedMapPreview", Adventure.class,
                UUID.class, AdventureController.MapActionPayload.class);
        method.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), new AdventureController.MapActionPayload(
                        UUID.randomUUID(), 0L, UUID.randomUUID(), "MOVE", List.of(
                                new AdventureController.PositionPayload(0, 0), new AdventureController.PositionPayload(1, 0)),
                        null, new AdventureController.PositionPayload(1, 0), List.of(), null)));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_confirmed_move_without_an_explicit_map_version() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("validateConfirmedMapPreview", Adventure.class,
                UUID.class, AdventureController.MapActionPayload.class);
        method.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), new AdventureController.MapActionPayload(
                        UUID.randomUUID(), null, UUID.randomUUID(), "MOVE", List.of(
                                new AdventureController.PositionPayload(0, 0), new AdventureController.PositionPayload(1, 0)),
                        null, new AdventureController.PositionPayload(1, 0), List.of(), "preview")));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_confirmed_move_with_a_malformed_path_before_preview() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("validateConfirmedMapPreview", Adventure.class,
                UUID.class, AdventureController.MapActionPayload.class);
        method.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), new AdventureController.MapActionPayload(
                        UUID.randomUUID(), 0L, UUID.randomUUID(), "MOVE", java.util.Arrays.asList(
                                new AdventureController.PositionPayload(0, 0), null),
                        null, null, List.of(), "preview")));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_confirmed_move_without_a_token_before_resolving_party_ownership() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("applyMapAction", Adventure.class, UUID.class,
                UUID.class, com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput.class);
        method.setAccessible(true);

        UUID mapId = UUID.randomUUID();
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(
                mapId, 0, "{\"mapId\":\"" + mapId
                        + "\",\"mapVersion\":0,\"action\":\"MOVE\",\"path\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":0}],\"fingerprint\":\"preview\"}");

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), UUID.randomUUID(), input));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_a_null_durable_confirmation_document_as_a_typed_error() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("prepareMapCommand", Adventure.class, UUID.class,
                UUID.class, UUID.class, com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput.class);
        method.setAccessible(true);

        UUID mapId = UUID.randomUUID();
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, 0, "null");
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), input));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_a_map_action_without_outer_map_binding_as_a_typed_error() {
        assertThrows(ApiRequestGuard.ApiContractException.class, () ->
                new AdventureController.GmInputRequest("MAP_ACTION", null, null, null, "MOVE", null).toDomain());
    }

    private static AdventureController controller(AdventureRepository adventures, CombatMapPort combatMap,
            CombatMapViewPort mapViews, AuthenticatedPlayerResolver playerResolver) {
        return new AdventureController(
                mock(SavedAdventureApplicationService.class), mock(RuntimeTurnApplicationService.class), adventures,
                mock(GmTurnFailureRecorder.class), mock(GmTurnRepository.class), mock(RuntimeTurnRepository.class),
                mock(SessionEventRepository.class), mock(RuleGuidanceApplicationService.class),
                mock(AdventureCombatApplicationService.class), mock(CombatActionApplicationService.class),
                mock(AdventureScenarioApplicationService.class), playerResolver, provider(combatMap),
                provider(mock(CharacterCombatPort.class)), new ObjectMapper(), provider(mapViews),
                provider(mock(CombatMapPreparationPort.class)), mock(ScenarioPackageRepository.class),
                mock(CombatLifecycleApplicationService.class), mock(AppliedRuleSetApplicationService.class));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return value; }
            @Override public T getIfAvailable(java.util.function.Supplier<T> fallback) { return value; }
        };
    }
}
