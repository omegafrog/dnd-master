package com.dndmaster.aigamemaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.application.rule.EvidenceStatus;
import com.dndmaster.aigamemaster.application.rule.RuleAnswerRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainValidationTest {
    @Test
    void ruleAnswerRequestRequiresASituationAndCopiesEvidence() {
        UUID ruleSetId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new RuleAnswerRequest(ruleSetId, " ", EvidenceStatus.SUFFICIENT, List.of()));
        assertEquals("grapple",
                new RuleAnswerRequest(ruleSetId, "grapple", EvidenceStatus.SUFFICIENT, List.of()).situation());
    }
}
