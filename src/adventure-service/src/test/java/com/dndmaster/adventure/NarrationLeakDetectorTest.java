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
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin has four of seven remaining.", englishName)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("Four of seven remain.", englishName)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린은 네 중 일곱이 남아 있다.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린의 체력은 일곱이다.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린의 HP는 십오.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린의 HP는 네.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin's HP is currently at 4/7.",
                new ConfirmedCombatState(3, List.of(new ConfirmedCombatState.Enemy(
                        UUID.randomUUID(), "goblin", 4, 7, false))))).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린은 두 점의 체력이 남았다.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린은 두 남았다.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린은 네가 남았다.", combatState)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin has two remaining.",
                new ConfirmedCombatState(3, List.of(new ConfirmedCombatState.Enemy(
                        UUID.randomUUID(), "goblin", 2, 12, false))))).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin has twelve hit points.",
                new ConfirmedCombatState(3, List.of(new ConfirmedCombatState.Enemy(
                        UUID.randomUUID(), "goblin", 12, 20, false))))).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린의 체력은 스물둘.",
                new ConfirmedCombatState(3, List.of(new ConfirmedCombatState.Enemy(
                        UUID.randomUUID(), "고블린", 22, 30, false))))).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린의 체력은 이백삼십오다.",
                new ConfirmedCombatState(3, List.of(new ConfirmedCombatState.Enemy(
                        UUID.randomUUID(), "고블린", 235, 300, false))))).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린의 체력은 천이십.",
                new ConfirmedCombatState(3, List.of(new ConfirmedCombatState.Enemy(
                        UUID.randomUUID(), "고블린", 1020, 1500, false))))).isTrue();
    }

    @Test
    void permits_injury_description_without_exact_hit_points() {
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린은 크게 다쳐 비틀거린다.", combatState)).isFalse();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린 두 마리가 양쪽에서 다가온다.", combatState)).isFalse();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("고블린은 남은 두 발의 화살을 쏜다.", combatState)).isFalse();
        ConfirmedCombatState englishName = new ConfirmedCombatState(3, List.of(
                new ConfirmedCombatState.Enemy(UUID.randomUUID(), "Goblin", 4, 7, false)));
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin takes two steps back.", englishName)).isFalse();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("Goblin fires its remaining two arrows.", englishName)).isFalse();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin tells a tale of seven kings.", englishName)).isFalse();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin has two remaining arrows.", englishName)).isFalse();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure("The goblin fires two of five arrows.", englishName)).isFalse();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure(
                "The goblin has two remaining, with arrows scattered around it.", englishName)).isTrue();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure(
                "The goblin looks healthy and carries two arrows.", englishName)).isFalse();
        assertThat(NarrationLeakDetector.isHitPointValueDisclosure(
                "The goblin's health looks poor, but it fires two arrows.", englishName)).isFalse();
    }
}
