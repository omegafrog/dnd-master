package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.evidence.EvidenceModelOutputException;
import com.dndmaster.aigamemaster.application.evidence.EvidenceRerankRequest;
import com.dndmaster.aigamemaster.application.evidence.EvidenceRerankResponse;
import com.dndmaster.aigamemaster.application.evidence.EvidenceRerankerService;
import com.dndmaster.aigamemaster.application.evidence.EvidenceSufficiencyJudgeService;
import com.dndmaster.aigamemaster.application.evidence.EvidenceSufficiencyRequest;
import com.dndmaster.aigamemaster.application.evidence.EvidenceSufficiencyResponse;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Internal, fixed-policy evidence-model HTTP boundary. */
@RestController
public final class EvidenceModelController {
    private final EvidenceRerankerService reranker;
    private final EvidenceSufficiencyJudgeService judge;
    private final ApiRequestGuard requestGuard;

    public EvidenceModelController(EvidenceRerankerService reranker, EvidenceSufficiencyJudgeService judge,
            ApiRequestGuard requestGuard) {
        this.reranker = Objects.requireNonNull(reranker, "reranker must not be null");
        this.judge = Objects.requireNonNull(judge, "judge must not be null");
        this.requestGuard = Objects.requireNonNull(requestGuard, "requestGuard must not be null");
    }

    @PostMapping("/internal/v1/gm/evidence-rerank")
    EvidenceRerankResponse rerank(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody EvidenceRerankRequest request) {
        requestGuard.internal(token);
        try {
            return reranker.rerank(Objects.requireNonNull(request, "request is required"));
        } catch (EvidenceModelOutputException error) {
            throw invalidModelOutput(error);
        }
    }

    @PostMapping("/internal/v1/gm/evidence-sufficiency")
    EvidenceSufficiencyResponse judge(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody EvidenceSufficiencyRequest request) {
        requestGuard.internal(token);
        try {
            return judge.judge(Objects.requireNonNull(request, "request is required"));
        } catch (EvidenceModelOutputException error) {
            throw invalidModelOutput(error);
        }
    }

    private static ResponseStatusException invalidModelOutput(EvidenceModelOutputException error) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "EVIDENCE_MODEL_OUTPUT_INVALID", error);
    }
}
