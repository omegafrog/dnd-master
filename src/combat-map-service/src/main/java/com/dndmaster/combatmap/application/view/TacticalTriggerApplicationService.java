package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.MapId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Applies a previously validated tactical trigger to the owned combat-map aggregate. */
public final class TacticalTriggerApplicationService {
    private final CombatMapViewService maps;

    public TacticalTriggerApplicationService(CombatMapViewService maps) {
        this.maps = Objects.requireNonNull(maps, "combat map service must not be null");
    }

    public CombatMap apply(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId,
            String triggerId, String kind, List<String> targetIds) {
        throw new IllegalArgumentException("planned trigger qualifying action required");
    }

    public CombatMap apply(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId,
            String triggerId, String kind, List<String> targetIds, String qualifyingAction) {
        return maps.applyTacticalTrigger(new MapId(mapId), new MapOwnerId(ownerId), expectedVersion, commandId,
                TacticalTriggerEffect.planned(triggerId, TacticalTriggerEffect.Kind.valueOf(kind), targetIds, "", qualifyingAction));
    }
}
