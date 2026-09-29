package com.dndmaster.aigamemaster.application.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EvidenceModelStageExecutorTest {
    private static final String PRIVATE_QUERY = "PRIVATE_QUERY_DO_NOT_LOG";
    private static final String PRIVATE_OUTPUT = "PRIVATE_PROVIDER_OUTPUT_DO_NOT_LOG";
    private static final String PRIVATE_ERROR_DETAIL = "PRIVATE_JSON_FRAGMENT_DO_NOT_LOG";

    @Test
    void logsCorrelatedStructuredOutputFailureWithoutSensitiveContent(CapturedOutput output) {
        EvidenceModelPort model = (playerId, operationId, instruction) -> {
            assertThat(instruction).contains(PRIVATE_QUERY);
            return "{\"orderedCandidateIds\":[\"" + PRIVATE_OUTPUT + "\"],\"private\":\"" + PRIVATE_ERROR_DETAIL;
        };
        var service = new EvidenceRerankerService(model, new com.fasterxml.jackson.databind.ObjectMapper());

        assertThrows(EvidenceModelOutputException.class, () -> service.rerank(new EvidenceRerankRequest(
                PRIVATE_QUERY, "PRIVATE_TASK_CONTEXT_DO_NOT_LOG", java.util.List.of())));

        assertThat(output).contains("evidence model output rejected requestId=evidence-rerank:")
                .contains("category=MALFORMED_JSON")
                .contains("causeType=JsonEOFException")
                .doesNotContain(PRIVATE_QUERY, PRIVATE_OUTPUT, PRIVATE_ERROR_DETAIL,
                        "PRIVATE_TASK_CONTEXT_DO_NOT_LOG", "Unexpected end-of-input");
    }
}
