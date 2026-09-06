package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.ReactionChoice;
import java.util.UUID;

public record ResolveReactionCommand(UUID adventureId, UUID reactionId, UUID actorId,
                                     ReactionChoice choice, long expectedVersion) {}
