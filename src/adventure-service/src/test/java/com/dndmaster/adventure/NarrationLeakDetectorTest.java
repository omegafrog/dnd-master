package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.application.combat.ConfirmedCombatState;
import com.dndmaster.adventure.application.runtime.NarrationLeakDetector;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NarrationLeakDetectorTest {
    private final ConfirmedCombatState combatState = new ConfirmedCombatState(3, List.of(
            new ConfirmedCombatState.Enemy(UUID.randomUUID(), "고블린", 4, 7, false)));

    @Test
    void detects_hit_points_when_narration_rephrases_or_omits_the_enemy_name() {
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린의 HP는 4/7 남았다.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린은 4 HP가 남았다.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("남은 HP는 4/7이다.", combatState)).isTrue();
        ConfirmedCombatState englishName = new ConfirmedCombatState(3, List.of(
                new ConfirmedCombatState.Enemy(UUID.randomUUID(), "Goblin", 4, 7, false)));
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin has four of seven health left.", englishName)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin is down to four of seven.", englishName)).isTrue();
    }

    @Test
    void permits_injury_description_without_exact_hit_points() {
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린은 크게 다쳐 비틀거린다.", combatState)).isFalse();
    }
}
