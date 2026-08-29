package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.ResolvedTurnPlan;
import com.dndmaster.adventure.application.runtime.TurnPlan;
import com.dndmaster.adventure.application.runtime.WriterContext;
import com.dndmaster.adventure.application.runtime.WriterProse;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnWriterContractTest {
    @Test
    void writerContextContainsOnlyResolvedVisibleInputs() {
        WriterContext context = WriterContext.of(ResolvedTurnPlan.of(
                new TurnPlan("hall", "guard", "door opens", List.of("A bell rings"), List.of("hidden key")), List.of("door opens")));

        assertTrue(context.visibleFacts().contains("A bell rings"));
        assertTrue(context.visibleFacts().stream().noneMatch("hidden key"::equals));
        assertThrows(IllegalArgumentException.class, () -> new WriterProse(" "));
    }
}
