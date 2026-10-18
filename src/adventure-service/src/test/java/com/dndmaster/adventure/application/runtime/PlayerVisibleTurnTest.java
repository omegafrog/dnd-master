package com.dndmaster.adventure.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerVisibleTurnTest {
    @Test
    void replacing_narration_keeps_the_safe_player_projection_fields() {
        PlayerVisibleTurn planned = new PlayerVisibleTurn("unreviewed plan", "stone room", List.of("known fact"), null);

        PlayerVisibleTurn returned = planned.withNarration("approved narration");

        assertThat(returned.narrationSeed()).isEqualTo("approved narration");
        assertThat(returned.currentScene()).isEqualTo("stone room");
        assertThat(returned.visibleFacts()).containsExactly("known fact");
    }
}
