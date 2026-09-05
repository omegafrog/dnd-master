package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import java.util.List;
import java.util.Objects;

/** Resolves the interrupt only; the existing operation worker resumes its stored step. */
public final class CombatReactionApplicationService {
    private final CombatEncounterRepository encounterRepository;
    private final CombatEventRepository eventRepository;

    public CombatReactionApplicationService(CombatEncounterRepository encounterRepository, CombatEventRepository eventRepository) {
        this.encounterRepository = Objects.requireNonNull(encounterRepository);
        this.eventRepository = Objects.requireNonNull(eventRepository);
    }

    public ReactionResolutionResponse resolve(ResolveReactionCommand command) {
        CombatEncounter encounter = encounterRepository.findActive(command.adventureId())
                .orElseThrow(() -> new CombatCommandRejectedException("COMBAT_NOT_ACTIVE", List.of("COMBAT_NOT_ACTIVE")));
        if (encounter.pendingReaction() == null) throw new CombatCommandRejectedException("REACTION_NOT_PENDING", List.of("REACTION_NOT_PENDING"));
        var reaction = encounter.pendingReaction();
        final CombatEncounter resolved;
        try {
            resolved = encounter.resolveReaction(reaction.reactionId(), command.actorId(), command.choice(), command.expectedVersion());
        } catch (RuntimeException exception) {
            throw new CombatCommandRejectedException("REACTION_NOT_ALLOWED", List.of(exception.getMessage()));
        }
        encounterRepository.save(resolved, encounter.version());
        eventRepository.append(new CombatEvent(resolved.encounterId(), resolved.eventCursor(), "REACTION_RESOLVED",
                "{\"reactionId\":\"" + reaction.reactionId() + "\",\"operationId\":\"" + reaction.suspendedOperationId()
                        + "\",\"choice\":\"" + command.choice() + "\",\"resumeStep\":\"" + reaction.resumeStep() + "\"}"));
        return new ReactionResolutionResponse(resolved.encounterId(), reaction.reactionId(), reaction.suspendedOperationId(),
                reaction.resumeStep(), command.choice(), resolved.version(), "RESUMED");
    }
}
