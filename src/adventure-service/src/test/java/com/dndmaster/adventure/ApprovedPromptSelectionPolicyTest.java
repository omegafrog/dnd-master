package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.ApprovedPromptConfiguration;
import com.dndmaster.adventure.application.runtime.ApprovedPromptSelectionPolicy;
import com.dndmaster.adventure.application.runtime.InMemoryApprovedPromptConfigurationReadPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovedPromptSelectionPolicyTest {
    @Test
    void selects_only_the_exact_active_role_and_preserves_eval_lineage() {
        InMemoryApprovedPromptConfigurationReadPort port = new InMemoryApprovedPromptConfigurationReadPort();
        port.activate(new ApprovedPromptConfiguration("writer", "writer-2", "model-2", "run-7", "writer-1",
                "dataset-3", "eval-3", 2));

        var lineage = new ApprovedPromptSelectionPolicy().select(port, "WRITER");

        assertEquals("WRITER", lineage.role());
        assertEquals("writer-2", lineage.promptVersion());
        assertEquals("dataset-3", lineage.datasetVersion());
        assertEquals("eval-3", lineage.evalVersion());
        assertThrows(IllegalStateException.class, () -> new ApprovedPromptSelectionPolicy().select(port, "PLANNER"));
    }

    @Test
    void rejects_stale_activation_versions() {
        InMemoryApprovedPromptConfigurationReadPort port = new InMemoryApprovedPromptConfigurationReadPort();
        port.activate(new ApprovedPromptConfiguration("PLANNER", "planner-1", "model-1", null, null, "d", "e", 3));
        assertThrows(IllegalStateException.class, () -> port.activate(
                new ApprovedPromptConfiguration("PLANNER", "planner-2", "model-2", null, "planner-1", "d", "e", 3)));
    }
}
