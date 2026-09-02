package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.UUID;

public record SubmitPlayerRollCommand(AdventureId adventureId, OwnerPlayerId ownerPlayerId,
                                      UUID pendingTurnId, int result, long expectedVersion) { }
