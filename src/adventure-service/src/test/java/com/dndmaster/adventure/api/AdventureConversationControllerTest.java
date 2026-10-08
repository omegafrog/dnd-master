package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeCheckProposal;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureConversationControllerTest {
    @Test
    void hides_internal_judgment_messages_from_the_player_conversation() {
        UUID ownerId = UUID.randomUUID();
        AdventureId adventureId = AdventureId.generate();
        Adventure adventure = mock(Adventure.class);
        when(adventure.id()).thenReturn(adventureId);
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(adventure.version()).thenReturn(2L);
        when(adventure.currentContext()).thenReturn(new AdventureContext("cellar", null, null, null));
        when(adventure.conversation()).thenReturn(List.of(
                new ConversationEntry(0, "PLAYER", "맥주를 달라고 한다"),
                new ConversationEntry(1, "AI_GAME_MASTER", "NPC가 협상 조건을 설명하는 직접 대사."),
                new ConversationEntry(2, "AI_GAME_MASTER", "Glowkindle은 보상 조건을 설명했다.")));
        AdventureRepository adventures = new AdventureRepository() {
            @Override public Optional<Adventure> findById(AdventureId id) { return Optional.of(adventure); }
            @Override public List<Adventure> findSavedByOwner(OwnerPlayerId owner) { return List.of(); }
            @Override public void save(Adventure value) { }
        };
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(ownerId);
        RuntimeTurn turn = mock(RuntimeTurn.class);
        RuntimePlan plan = mock(RuntimePlan.class);
        when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.COMMITTED);
        when(turn.plan()).thenReturn(plan);
        when(plan.judgment()).thenReturn("Glowkindle은 보상 조건을 설명했다.");
        RuntimeTurnRepository turns = mock(RuntimeTurnRepository.class);
        when(turns.findAllByAdventureId(adventureId)).thenReturn(List.of(turn));

        var view = new AdventureConversationController(adventures, players, turns).read(adventureId.value());

        assertEquals(2, view.entries().size());
        assertEquals("NPC가 협상 조건을 설명하는 직접 대사.", view.entries().get(1).content());
    }

    @Test
    void includes_the_saved_current_scene_in_the_conversation_projection() {
        UUID ownerId = UUID.randomUUID();
        Adventure adventure = Adventure.create(AdventureId.generate(), SessionId.generate(), new OwnerPlayerId(ownerId),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("beer-cellar", null, null, null));
        AdventureRepository adventures = new AdventureRepository() {
            @Override public Optional<Adventure> findById(AdventureId id) { return Optional.of(adventure); }
            @Override public List<Adventure> findSavedByOwner(OwnerPlayerId owner) { return List.of(); }
            @Override public void save(Adventure value) { }
        };
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(ownerId);

        AdventureConversationController.ConversationView view = new AdventureConversationController(
                adventures, players, mock(RuntimeTurnRepository.class)).read(adventure.id().value());

        assertEquals("beer-cellar", view.currentScene());
        assertEquals(null, view.pendingRoll());
    }

    @Test
    void restores_the_pending_player_roll_from_the_saved_turn() {
        UUID ownerId = UUID.randomUUID();
        Adventure adventure = Adventure.create(AdventureId.generate(), SessionId.generate(), new OwnerPlayerId(ownerId),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("beer-cellar", null, null, null));
        AdventureRepository adventures = new AdventureRepository() {
            @Override public Optional<Adventure> findById(AdventureId id) { return Optional.of(adventure); }
            @Override public List<Adventure> findSavedByOwner(OwnerPlayerId owner) { return List.of(); }
            @Override public void save(Adventure value) { }
        };
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(ownerId);
        RuntimeTurnRepository turns = mock(RuntimeTurnRepository.class);
        RuntimeTurn turn = mock(RuntimeTurn.class);
        RuntimePlan plan = mock(RuntimePlan.class);
        RuntimeCheckProposal check = mock(RuntimeCheckProposal.class);
        UUID turnId = UUID.randomUUID();
        when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.PENDING_ROLL);
        when(turn.turnId()).thenReturn(turnId);
        when(turn.expectedVersion()).thenReturn(4L);
        when(turn.conversation()).thenReturn(List.of(
                new ConversationEntry(0, "PLAYER", "해치를 연다"),
                new ConversationEntry(1, "AI_GAME_MASTER", "어둠 속에서 희미한 발톱 소리가 들립니다.")));
        when(turn.plan()).thenReturn(plan);
        when(plan.checkProposal()).thenReturn(check);
        when(check.abilityOrSkill()).thenReturn("지각");
        when(check.diceExpression()).thenReturn("1d20");
        when(check.reason()).thenReturn("문 너머의 소리를 확인합니다.");
        when(turns.findAllByAdventureId(adventure.id())).thenReturn(List.of(turn));

        AdventureConversationController.ConversationView view = new AdventureConversationController(
                adventures, players, turns).read(adventure.id().value());

        assertNotNull(view.pendingRoll());
        assertEquals(turnId, view.pendingRoll().pendingTurnId());
        assertEquals("지각", view.pendingRoll().label());
        assertEquals("1d20", view.pendingRoll().diceExpression());
        assertEquals("문 너머의 소리를 확인합니다.", view.pendingRoll().prompt());
        assertEquals(4L, view.pendingRoll().expectedVersion());
        assertEquals("어둠 속에서 희미한 발톱 소리가 들립니다.", view.entries().get(1).content());
    }
}
