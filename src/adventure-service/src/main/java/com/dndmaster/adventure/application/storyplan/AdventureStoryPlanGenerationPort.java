package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import java.util.UUID;
import java.util.List;

/** AI boundary for source-grounded adventure outline generation. */
public interface AdventureStoryPlanGenerationPort {
    List<AdventureStoryPlanStage> generate(Request request);

    record Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                   List<String> sourceDocuments, List<String> resolutionEvidence, List<MapContext> maps,
                   List<SourceCitation> citations) {
        public Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                List<String> sourceDocuments, List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence, List.of(), List.of());
        }
        public Request(String operationId, long packageRevision, int partySize, List<String> sourceDocuments,
                List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, AdventurePlanConfiguration.defaults(), sourceDocuments, resolutionEvidence, List.of(), List.of());
        }
    }

    record MapContext(UUID mapDefinitionId, String assetId, String assetLocator, String sourceLocator, double confidence, String safetyStatus) {}
    record SourceCitation(String documentType, UUID documentId, long extractionVersion, String locator, String quote, double confidence) {}
}
