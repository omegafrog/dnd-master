package com.dndmaster.adventure.application.combat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MovementPlacementModelPortTest {
    @Test
    void accepts_only_the_three_player_safe_interpretation_states() {
        var destination = new MovementPlacementModelPort.Position(2, 3);
        assertEquals("RESOLVED", new MovementPlacementModelPort.MovementPlacementProposal("resolved", destination, List.of(), "").status());
        assertEquals("AMBIGUOUS", new MovementPlacementModelPort.MovementPlacementProposal("ambiguous", null, List.of(
                new MovementPlacementModelPort.Candidate(destination, .6, "공개된 통로")), "어느 통로인지 알려주세요.").status());
        assertEquals("UNRESOLVED", new MovementPlacementModelPort.MovementPlacementProposal("unresolved", null, List.of(), "지도를 눌러 목적지를 선택해주세요.").status());
        assertThrows(IllegalArgumentException.class, () -> new MovementPlacementModelPort.MovementPlacementProposal("AMBIGUOUS", destination, List.of(), ""));
    }

    @Test
    void requires_source_text_before_an_ai_boundary_can_be_called() {
        assertThrows(IllegalArgumentException.class, () -> new MovementPlacementModelPort.MovementPlacementContext(" ", "public map", "1,1", ""));
    }
}
