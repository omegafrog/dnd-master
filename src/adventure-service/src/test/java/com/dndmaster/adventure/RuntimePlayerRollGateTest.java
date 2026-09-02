package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.PlayerRollRequest;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimePlayerRollGateTest {
    @Test
    void pending_roll_is_a_gate_until_a_valid_submission() {
        assertEquals(RuntimeTurnLifecycle.RESOLVING, RuntimeTurnLifecycle.PENDING_ROLL.transitionTo(RuntimeTurnLifecycle.RESOLVING));
        assertThrows(IllegalStateException.class, () -> RuntimeTurnLifecycle.PENDING_ROLL.transitionTo(RuntimeTurnLifecycle.PRESENTED));
    }

    @Test
    void safe_roll_request_contains_only_player_safe_fields() {
        PlayerRollRequest request = new PlayerRollRequest(UUID.randomUUID(), "지각 판정", "d20", "d20을 굴려 결과를 제출하세요.", 7);

        String json = request.toString();
        assertEquals(7, request.expectedVersion());
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("DC"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("target"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("location"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("comparison"));
    }
}
