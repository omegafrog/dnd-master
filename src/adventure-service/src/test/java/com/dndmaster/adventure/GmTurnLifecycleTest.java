package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.runtime.GmInput;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.domain.runtime.GmTurnStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmTurnLifecycleTest {
    @Test
    void acceptsTypedInputsAndAllowsOnlyForwardLifecycleTransitions() {
        GmInput input = new GmInput.TextInput("open the door");
        GmTurn turn = GmTurn.start(UUID.randomUUID(), UUID.randomUUID(), 4L, input);

        assertEquals(GmTurnStatus.STARTED, turn.status());
        assertEquals(GmTurnStatus.PROCESSING, turn.process().status());
        assertEquals(GmTurnStatus.COMMITTED, turn.process().commit("provider-x").status());
        assertThrows(IllegalStateException.class, () -> turn.process().commit("provider-x").fail("late"));
    }

    @Test
    void rejectsCommandReuseWhenPayloadFingerprintDiffers() {
        UUID commandId = UUID.randomUUID();
        GmTurn first = GmTurn.start(UUID.randomUUID(), commandId, 0L, new GmInput.TextInput("look"));

        assertThrows(IllegalStateException.class,
                () -> first.assertSameCommand(new GmInput.TextInput("move")));
        first.assertSameCommand(new GmInput.TextInput("look"));
        assertThrows(IllegalStateException.class,
                () -> first.assertSameCommand(1L, new GmInput.TextInput("look")));
    }

    @Test
    void failed_retryable_is_terminal_and_cannot_be_reopened() {
        GmTurn turn = GmTurn.start(UUID.randomUUID(), UUID.randomUUID(), 4L,
                new GmInput.TextInput("look"));

        GmTurn failed = turn.process().failRetryable("GM_CANDIDATE_MALFORMED");

        assertEquals(GmTurnStatus.FAILED_RETRYABLE, failed.status());
        assertThrows(IllegalStateException.class, failed::process);
        assertThrows(IllegalStateException.class, () -> failed.failRetryable("again"));
    }

    @Test
    void mapInputRequiresMapVersionAndAction() {
        assertThrows(IllegalArgumentException.class, () -> new GmInput.MapActionInput(null, 1L, "move"));
        assertThrows(IllegalArgumentException.class, () -> new GmInput.MapActionInput(UUID.randomUUID(), -1L, "move"));
        assertThrows(IllegalArgumentException.class, () -> new GmInput.MapActionInput(UUID.randomUUID(), 1L, " "));
        assertEquals("question", new GmInput.MetaQuestionInput("question").actionText());
    }
}
