package com.dndmaster.adventure.application.progress;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public final class AdventureProgressApplicationService {
    private final AdventureRepository repository;
    private final AdventureReadinessPort readinessPort;
    private final AiGameMasterPort aiGameMasterPort;

    public AdventureProgressApplicationService(
            AdventureRepository repository, AdventureReadinessPort readinessPort, AiGameMasterPort aiGameMasterPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.readinessPort = Objects.requireNonNull(readinessPort, "readiness port must not be null");
        this.aiGameMasterPort = Objects.requireNonNull(aiGameMasterPort, "AI Game Master port must not be null");
    }

    public AdventureProgressResult progressAdventure(ProgressAdventureCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Adventure current = repository.findById(command.adventureId())
                .orElseThrow(AdventureProgressNotFoundException::new);
        current.reopen(command.requestingOwner());
        if (!readinessPort.check(current).ready()) throw new AdventureProgressNotReadyException();

        SceneProgress scene;
        ActionJudgment judgment;
        try {
            scene = aiGameMasterPort.advanceScene(new SceneProgressRequest(current.scenarioId(), current.currentContext()));
            if (!current.scenarioId().equals(scene.scenarioId())) throw new ScenarioProgressViolationException();
            judgment = aiGameMasterPort.adjudicate(
                    new ActionJudgmentRequest(current.ruleSetId(), scene, command.action()));
        } catch (TimeoutException exception) {
            throw new CrossContextTimeoutException(exception);
        }
        if (!current.ruleSetId().equals(judgment.ruleSetId())) throw new RuleSetAdjudicationViolationException();

        AdventureContext nextContext = new AdventureContext(
                scene.scene(), scene.npcState(), command.action(), judgment.result());
        var conversation = new ArrayList<>(current.conversation());
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", scene.scene()));
        conversation.add(new ConversationEntry(conversation.size(), "PLAYER", command.action()));
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", judgment.result()));

        Adventure progressed = Adventure.rehydrate(
                current.id(), current.sessionId(), current.ownerPlayerId(), current.scenarioId(), current.ruleSetId(),
                current.characterSheetId(), current.conversation(), current.currentContext(), current.status(), current.version());
        progressed.preserveProgress(command.requestingOwner(), current.version(), nextContext, conversation);
        repository.save(progressed);
        return new AdventureProgressResult(progressed.currentContext(), progressed.conversation(), progressed.version());
    }
}
