package com.dndmaster.aigamemaster.api;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/gm")
public class InitialSourceContextController {
    @PostMapping("/initial-source-contexts")
    ProposalResponse propose(@RequestBody ProposalRequest request) {
        List<CandidateResponse> candidates = request.candidates() == null ? List.of() : request.candidates().stream()
                .filter(candidate -> candidate != null)
                .map(candidate -> new CandidateResponse(
                        candidate.knowledgeDocumentId(), candidate.extractionVersion(), candidate.locator(),
                        candidate.excerpt(), candidate.score(), candidate.reason()))
                .toList();
        String status = candidates.isEmpty() ? "BLOCKED" : candidates.size() == 1 ? "CLEAR" : "AMBIGUOUS";
        return new ProposalResponse(status, candidates);
    }

    public record ProposalRequest(String packageId, List<CandidateRequest> candidates) {}

    public record CandidateRequest(String knowledgeDocumentId, long extractionVersion, String locator, String excerpt, double score, String reason) {}

    public record ProposalResponse(String status, List<CandidateResponse> candidates) {}

    public record CandidateResponse(String knowledgeDocumentId, long extractionVersion, String locator, String excerpt, double score, String reason) {}
}
