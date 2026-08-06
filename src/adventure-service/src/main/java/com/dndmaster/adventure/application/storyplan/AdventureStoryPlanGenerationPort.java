package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.List;

/** AI boundary for source-grounded adventure outline generation. */
public interface AdventureStoryPlanGenerationPort {
    List<AdventureStoryPlanStage> generate(Request request);

    record Request(String operationId, long packageRevision, int partySize, List<String> sourceDocuments,
                   List<String> resolutionEvidence) {}
}
