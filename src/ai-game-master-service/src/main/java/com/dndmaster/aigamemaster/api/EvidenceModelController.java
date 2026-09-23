package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.evidence.EvidenceModelOutputException;
import com.dndmaster.aigamemaster.application.evidence.EvidenceCandidate;
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
import java.util.UUID;

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

    EvidenceRerankResponse rerank(String token, com.dndmaster.aigamemaster.application.evidence.EvidenceRerankRequest request) {
        return rerank(token, null, request);
    }

    @PostMapping("/internal/v1/gm/evidence-rerank")
    EvidenceRerankResponse rerank(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Solo-Player-Id", required = false) String soloPlayerId,
            @RequestBody EvidenceRerankRequest request) {
        requestGuard.internal(token);
        try {
            return reranker.rerank(Objects.requireNonNull(request, "request is required"), parsePlayerId(soloPlayerId));
        } catch (IllegalStateException error) {
            if (!soloPlayerIdIsMissing(soloPlayerId) || !isCodexIdentityUnavailable(error)) throw error;
            // This internal preparation call has no player identity to pass to Codex.
            // Preserve the search order so session startup can still complete.
            return new EvidenceRerankResponse(request.candidates().stream()
                    .map(EvidenceCandidate::evidenceId)
                    .toList());
        } catch (EvidenceModelOutputException error) {
            throw invalidModelOutput(error);
        }
    }

    EvidenceSufficiencyResponse judge(String token, com.dndmaster.aigamemaster.application.evidence.EvidenceSufficiencyRequest request) {
        return judge(token, null, request);
    }

    @PostMapping("/internal/v1/gm/evidence-sufficiency")
    EvidenceSufficiencyResponse judge(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Solo-Player-Id", required = false) String soloPlayerId,
            @RequestBody EvidenceSufficiencyRequest request) {
        requestGuard.internal(token);
        try {
            return judge.judge(Objects.requireNonNull(request, "request is required"), parsePlayerId(soloPlayerId));
        } catch (IllegalStateException error) {
            if (!soloPlayerIdIsMissing(soloPlayerId) || !isCodexIdentityUnavailable(error)) throw error;
            var candidates = request.candidates();
            if (candidates.isEmpty()) {
                return new EvidenceSufficiencyResponse(false, java.util.List.of(), java.util.Map.of(),
                        "no evidence candidates were available");
            }
            var selected = candidates.get(0).evidenceId();
            return new EvidenceSufficiencyResponse(true, java.util.List.of(selected),
                    java.util.Map.of(selected, "첫 번째 검색 근거를 사용했습니다."), "");
        } catch (EvidenceModelOutputException error) {
            throw invalidModelOutput(error);
        }
    }

    private static boolean isCodexIdentityUnavailable(IllegalStateException error) {
        return error.getMessage() != null
                && error.getMessage().contains("server-confirmed Solo Player ID");
    }

    private static boolean soloPlayerIdIsMissing(String value) {
        return value == null || value.isBlank();
    }

    private static UUID parsePlayerId(String value) {
        if (soloPlayerIdIsMissing(value)) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid Solo Player ID", error);
        }
    }

    private static ResponseStatusException invalidModelOutput(EvidenceModelOutputException error) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "EVIDENCE_MODEL_OUTPUT_INVALID", error);
    }
}
