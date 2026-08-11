package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import java.util.List;

/** AI boundary for source-grounded adventure outline generation. */
public interface AdventureStoryPlanGenerationPort {
    List<AdventureStoryPlanStage> generate(Request request);

    record Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                   List<String> sourceDocuments, List<String> resolutionEvidence) {
        public Request(String operationId, long packageRevision, int partySize, List<String> sourceDocuments,
                List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, AdventurePlanConfiguration.defaults(), sourceDocuments, resolutionEvidence);
        }
    }
}
