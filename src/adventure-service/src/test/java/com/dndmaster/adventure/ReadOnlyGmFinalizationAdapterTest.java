package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.application.runtime.ReadOnlyGmFinalizationAdapter;
import com.dndmaster.adventure.application.runtime.RuntimeCommandOutcome;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadOnlyGmFinalizationAdapterTest {
    @Test
    void acceptsOnlyTerminalToolOutcomesAndDoesNotChangePlan() {
        RuntimePlan plan = new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of());
        var finalizer = new ReadOnlyGmFinalizationAdapter();

        assertThat(finalizer.finalize(plan, List.of(RuntimeCommandOutcome.applied("17", 1)))).isSameAs(plan);
        assertThatThrownBy(() -> finalizer.finalize(plan, List.of(RuntimeCommandOutcome.unknown("pending"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal tool outcomes");
    }
}
