package com.dndmaster.aigamemaster.application.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EvidenceRerankerServiceTest {
    @Test
    void removesDuplicateModelIdsPreservingFirstOccurrence(CapturedOutput output) {
        String previousDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        try {
        EvidenceModelPort model = (playerId, operationId, instruction) ->
                "{\"orderedCandidateIds\":[\"c2\",\"c1\",\"c2\"]}";
        var service = new EvidenceRerankerService(model, new com.fasterxml.jackson.databind.ObjectMapper());
        var request = new EvidenceRerankRequest("query", "PLAYER_ACTION", List.of(
                new EvidenceCandidate("evidence-one", "STORYBOOK", "page=1", "first"),
                new EvidenceCandidate("evidence-two", "STORYBOOK", "page=2", "second")));

        assertThat(service.rerank(request).orderedCandidateIds())
                .containsExactly("evidence-two", "evidence-one");
        assertThat(output).contains("dev_evidence_rerank_duplicates outcome=normalized")
                .contains("actualIdCount=3")
                .contains("uniqueIdCount=2")
                .contains("recoveryAction=preserve_first_occurrence");
        } finally {
            if (previousDiagnostics == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previousDiagnostics);
        }
    }
}
