package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;

@FunctionalInterface
public interface StageDetailedGenerationPort {
    DetailedStage generate(Request request);

    default List<DetailedStage> generateAll(List<Request> requests) {
        if (requests.isEmpty()) return List.of();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(Math.min(4, requests.size()));
        try {
            var pending = requests.stream().map(request -> executor.submit(() -> generate(request))).toList();
            var result = new java.util.ArrayList<DetailedStage>(pending.size());
            RuntimeException failure = null;
            for (var task : pending) {
                try { result.add(task.get()); }
                catch (java.util.concurrent.ExecutionException failed) {
                    result.add(null);
                    if (failure == null) failure = failed.getCause() instanceof RuntimeException runtime
                            ? runtime : new IllegalStateException("initial situation preparation failed", failed.getCause());
                }
            }
            if (failure != null) throw failure;
            return List.copyOf(result);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("initial situation preparation was interrupted", interrupted);
        } finally {
            executor.shutdownNow();
        }
    }

    record Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
            long backboneRevision, List<ScenarioSourceReference> evidence, List<RequiredSituation> requiredSituations,
            boolean openingSituationRequired, List<String> alreadyAssignedRequiredSituationIds) {
        public Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
                long backboneRevision, List<ScenarioSourceReference> evidence) {
            this(adventureId, sessionId, scenarioPackageId, stageId, backboneRevision, evidence, List.of(), false, List.of());
        }
        public Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
                long backboneRevision, List<ScenarioSourceReference> evidence, List<RequiredSituation> requiredSituations) {
            this(adventureId, sessionId, scenarioPackageId, stageId, backboneRevision, evidence, requiredSituations, false, List.of());
        }
        public Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
                long backboneRevision, List<ScenarioSourceReference> evidence, List<RequiredSituation> requiredSituations,
                boolean openingSituationRequired) {
            this(adventureId, sessionId, scenarioPackageId, stageId, backboneRevision, evidence, requiredSituations,
                    openingSituationRequired, List.of());
        }
        public Request { evidence = List.copyOf(evidence); requiredSituations = List.copyOf(requiredSituations);
            alreadyAssignedRequiredSituationIds = List.copyOf(alreadyAssignedRequiredSituationIds); }
    }
}
