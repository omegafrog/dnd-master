package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import java.util.UUID;
import java.util.List;

/** AI boundary for source-grounded adventure outline generation. */
public interface AdventureStoryPlanGenerationPort {
    List<AdventureStoryPlanStage> generate(Request request);

    default TacticalScenePlanCandidate generateTacticalScene(TacticalSceneRequest request) {
        return TacticalScenePlanCandidate.absent(request.stage().position());
    }

    record Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                   List<String> sourceDocuments, List<String> resolutionEvidence, List<MapContext> maps,
                   List<SourceCitation> citations, List<String> violations, String previousCandidate) {
        public Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                List<String> sourceDocuments, List<String> resolutionEvidence, List<MapContext> maps,
                List<SourceCitation> citations) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence,
                    maps, citations, List.of(), "");
        }
        public Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                List<String> sourceDocuments, List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence,
                    List.of(), List.of(), List.of(), "");
        }
        public Request(String operationId, long packageRevision, int partySize, List<String> sourceDocuments,
                List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, AdventurePlanConfiguration.defaults(), sourceDocuments,
                    resolutionEvidence, List.of(), List.of(), List.of(), "");
        }
        public Request {
            violations = violations == null ? List.of() : List.copyOf(violations);
            previousCandidate = previousCandidate == null ? "" : previousCandidate;
        }
        public Request withViolations(List<String> nextViolations) {
            return new Request(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, maps, citations, nextViolations, previousCandidate);
        }
        public Request withPreviousCandidate(String candidate) {
            return new Request(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, maps, citations, violations, candidate);
        }
    }

    record MapContext(UUID mapDefinitionId, String assetId, String assetLocator, String sourceLocator, double confidence,
            String safetyStatus, List<SourceCitation> relatedEvidence, String context) {
        public MapContext(UUID mapDefinitionId, String assetId, String assetLocator, String sourceLocator, double confidence, String safetyStatus) {
            this(mapDefinitionId, assetId, assetLocator, sourceLocator, confidence, safetyStatus, List.of(), "");
        }
        public MapContext(UUID mapDefinitionId, String assetId, String assetLocator, String sourceLocator, double confidence,
                String safetyStatus, List<SourceCitation> relatedEvidence) {
            this(mapDefinitionId, assetId, assetLocator, sourceLocator, confidence, safetyStatus, relatedEvidence, "");
        }
        public MapContext {
            relatedEvidence = List.copyOf(java.util.Objects.requireNonNull(
                    relatedEvidence, "map related evidence must be explicit"));
            context = context == null ? "" : context.trim();
        }
    }
    record SourceCitation(String documentType, UUID documentId, long extractionVersion, String locator, String quote, double confidence) {}
}
