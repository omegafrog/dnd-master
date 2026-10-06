package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.application.runtime.RuntimePlanningRequest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable source scope and enemy candidates retained by durable preparation work. */
public record EnemySheetPreparationRequest(UUID sourceTurnId, UUID adventureId, List<Enemy> enemies,
                                           RuntimePlanningRequest planningRequest) {
    public EnemySheetPreparationRequest {
        Objects.requireNonNull(sourceTurnId);
        Objects.requireNonNull(adventureId);
        enemies = List.copyOf(Objects.requireNonNull(enemies));
    }

    public EnemySheetPreparationRequest(UUID sourceTurnId, UUID adventureId, List<Enemy> enemies) {
        this(sourceTurnId, adventureId, enemies, null);
    }

    public record Enemy(EnemyCharacterSheetIdentity identity, CombatEnemyProposal proposal) {
        public Enemy {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(proposal);
        }
    }
}
