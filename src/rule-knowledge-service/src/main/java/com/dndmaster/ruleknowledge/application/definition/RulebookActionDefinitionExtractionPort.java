package com.dndmaster.ruleknowledge.application.definition;

import java.util.List;
import java.util.UUID;

public interface RulebookActionDefinitionExtractionPort {
    /**
     * Local estimate of the assigned extraction prompt and schema. Implementations without the HTTP contract
     * use UTF-8 input bytes as a conservative fallback; estimates exclude provider and tool overhead.
     */
    default long estimateAssignedBatchInputTokens(UUID documentId, String extractionVersion,
            List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
            List<RulebookActionQueryCoverage> queryCoverage, UUID executionActorPlayerId) {
        long bytes = 0;
        for (RulebookActionEvidence item : evidence) bytes += item.id().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + item.locator().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + item.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        for (RulebookActionAssignment item : assigned) bytes += (item.candidate().id() + item.candidate().name()
                + item.candidate().actionKind() + item.candidate().sourceLocator() + item.knownFields()
                + item.neededFields() + item.availableSharedRules()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        for (RulebookActionQueryCoverage item : queryCoverage) bytes += item.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return bytes;
    }

    default List<RulebookActionCandidate> discover(UUID documentId, String extractionVersion,
            List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> queryCoverage,
            UUID executionActorPlayerId) {
        return extract(documentId, extractionVersion, evidence, queryCoverage, executionActorPlayerId).stream()
                .map(item -> new RulebookActionCandidate(item.id(), item.name(), item.actionKind(), item.sourceLocator(),
                        item.sourceChunkId(), item.sourceEvidence())).toList();
    }

    default List<RulebookActionDefinitionResult> extractAssignedBatch(UUID documentId, String extractionVersion,
            List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
            List<RulebookActionQueryCoverage> queryCoverage, UUID executionActorPlayerId) {
        java.util.Map<String, RulebookActionEvidence> byId = evidence.stream().collect(java.util.stream.Collectors.toMap(
                RulebookActionEvidence::id, item -> item, (first, ignored) -> first));
        return assigned.stream().map(item -> {
            List<RulebookActionEvidence> scoped = item.evidenceIds().stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
            try {
                List<RulebookActionDefinition> definitions = extractAssigned(documentId, extractionVersion,
                        item.candidate().id(), item.candidate().name(), item.candidate().actionKind(), scoped,
                        queryCoverage, executionActorPlayerId);
                RulebookActionDefinition definition = definitions.stream().filter(value -> value.id().equals(item.candidate().id())
                        && value.name().equals(item.candidate().name()) && value.actionKind().equals(item.candidate().actionKind())).findFirst().orElse(null);
                return definition == null
                        ? new RulebookActionDefinitionResult(item.candidate().id(), "INSUFFICIENT_EVIDENCE", null, "assigned_result_missing")
                        : new RulebookActionDefinitionResult(item.candidate().id(), "COMPLETED", definition, null);
            } catch (RuntimeException failure) {
                return new RulebookActionDefinitionResult(item.candidate().id(), "FAILED", null, failure.getClass().getSimpleName());
            }
        }).toList();
    }

    List<RulebookActionDefinition> extract(UUID documentId, String extractionVersion,
            List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> queryCoverage,
            UUID executionActorPlayerId);

    default List<RulebookActionDefinition> extractAssigned(UUID documentId, String extractionVersion,
            String expectedActionId, String expectedName, String expectedKind, List<RulebookActionEvidence> evidence,
            List<RulebookActionQueryCoverage> queryCoverage, UUID executionActorPlayerId) {
        return extract(documentId, extractionVersion, evidence, queryCoverage, executionActorPlayerId);
    }
}
