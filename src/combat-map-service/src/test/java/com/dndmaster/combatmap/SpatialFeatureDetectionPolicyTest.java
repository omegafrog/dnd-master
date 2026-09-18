package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.combatmap.application.spatial.SpatialFeatureDetectionPolicy;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.DetectionSpec;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureProvenance;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenDiscovery;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import com.dndmaster.combatmap.domain.VisibilitySnapshot;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpatialFeatureDetectionPolicyTest {
    @Test
    void only_returns_hidden_features_in_current_public_sight_and_range() {
        PlayerId player = new PlayerId(UUID.randomUUID());
        CombatToken token = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                new GridPosition(1, 1), TokenController.PLAYER, player, TokenDiscovery.REVEALED);
        SpatialFeature visible = hiddenFeature(new GridPosition(3, 1));
        SpatialFeature exploredOnly = hiddenFeature(new GridPosition(4, 1));
        SpatialFeature outsideSight = hiddenFeature(new GridPosition(2, 2));
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new GridSpec(8, 8, 50, 5), player, List.of(token), Set.of(), List.of(),
                0, null, null, List.of(visible, exploredOnly, outsideSight));
        map.replaceVisibility(new VisibilitySnapshot(Set.of(new GridPosition(1, 1), new GridPosition(3, 1)),
                Set.of(new GridPosition(1, 1), new GridPosition(3, 1), new GridPosition(4, 1), new GridPosition(2, 2)),
                Set.of(), List.of(), 0));

        assertEquals(List.of(visible.id()), new SpatialFeatureDetectionPolicy().candidates(map, player, token.id())
                .stream().map(SpatialFeature::id).toList());
    }

    private static SpatialFeature hiddenFeature(GridPosition cell) {
        return SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.TRAP, List.of(cell),
                DetectionSpec.passive("perception", 12), Set.of(SpatialTrigger.ENTER_CELL),
                SpatialFeatureProvenance.storyPlan("story", 0, 0));
    }
}
