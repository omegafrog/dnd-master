package com.dndmaster.aigamemaster.application.ports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MovementPlacementModelPortTest {
    @Test
    void does_not_allow_the_model_boundary_to_pick_an_ambiguous_destination() {
        var point = new MovementPlacementModelPort.Position(1, 2);
        assertEquals("RESOLVED", new MovementPlacementModelPort.MovementPlacementProposal("resolved", point, List.of(), "").status());
        assertEquals("AMBIGUOUS", new MovementPlacementModelPort.MovementPlacementProposal("ambiguous", null,
                List.of(new MovementPlacementModelPort.Candidate(point, .5, "공개된 통로")), "어느 길인지 알려주세요.").status());
        assertThrows(IllegalArgumentException.class, () -> new MovementPlacementModelPort.MovementPlacementProposal("AMBIGUOUS", point, List.of(), ""));
    }
}
