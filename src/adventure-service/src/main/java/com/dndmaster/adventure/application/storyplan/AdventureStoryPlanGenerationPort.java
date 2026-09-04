package com.dndmaster.adventure.application.storyplan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.SourceConstraintPack;
import com.dndmaster.adventure.domain.adventure.StoryPlanGenerationMode;
import java.util.UUID;
import java.util.List;

/** AI boundary for source-grounded adventure outline generation. */
public interface AdventureStoryPlanGenerationPort {
    ProjectionCandidate generate(Request request);

    /** Bounded repair returns a complete candidate, never a field patch. */
    default ProjectionCandidate repair(RepairRequest request) {
        return generate(request.toGenerationRequest());
    }

    default TacticalScenePlanCandidate generateTacticalScene(TacticalSceneRequest request) {
        return TacticalScenePlanCandidate.absent(request.stage().position());
    }

    record Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                   List<String> sourceDocuments, List<String> resolutionEvidence, List<MapContext> maps,
                   List<SourceCitation> citations, List<String> violations, String previousCandidate,
                   StoryPlanGenerationMode generationMode, SourceConstraintPack sourceConstraintPack,
                   RetrievalContext retrievalContext) {
        public Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                List<String> sourceDocuments, List<String> resolutionEvidence, List<MapContext> maps,
                List<SourceCitation> citations, List<String> violations, String previousCandidate) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence, maps,
                    citations, violations, previousCandidate, StoryPlanGenerationMode.GENERATIVE,
                    new SourceConstraintPack(List.of(), List.of()), RetrievalContext.empty());
        }
        public Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                List<String> sourceDocuments, List<String> resolutionEvidence, List<MapContext> maps,
                List<SourceCitation> citations) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence,
                    maps, citations, List.of(), "", StoryPlanGenerationMode.GENERATIVE,
                    new SourceConstraintPack(List.of(), List.of()), RetrievalContext.empty());
        }
        public Request(String operationId, long packageRevision, int partySize, AdventurePlanConfiguration configuration,
                List<String> sourceDocuments, List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence,
                    List.of(), List.of(), List.of(), "", StoryPlanGenerationMode.GENERATIVE,
                    new SourceConstraintPack(List.of(), List.of()), RetrievalContext.empty());
        }
        public Request(String operationId, long packageRevision, int partySize, List<String> sourceDocuments,
                List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, AdventurePlanConfiguration.defaults(), sourceDocuments,
                    resolutionEvidence, List.of(), List.of(), List.of(), "", StoryPlanGenerationMode.GENERATIVE,
                    new SourceConstraintPack(List.of(), List.of()), RetrievalContext.empty());
        }
        public Request {
            citations = citations == null ? List.of() : List.copyOf(citations);
            violations = violations == null ? List.of() : List.copyOf(violations);
            previousCandidate = previousCandidate == null ? "" : previousCandidate;
            generationMode = generationMode == null ? StoryPlanGenerationMode.GENERATIVE : generationMode;
            sourceConstraintPack = sourceConstraintPack == null ? new SourceConstraintPack(List.of(), List.of()) : sourceConstraintPack;
            retrievalContext = retrievalContext == null ? RetrievalContext.empty() : retrievalContext;
        }
        public Request withCitationKeys() {
            java.util.Set<String> usedKeys = new java.util.HashSet<>();
            for (SourceCitation citation : citations) {
                if (citation.citationKey() != null && !citation.citationKey().isBlank()
                        && !usedKeys.add(citation.citationKey())) {
                    throw new IllegalArgumentException("duplicate citation key: " + citation.citationKey());
                }
            }
            int nextGeneratedKey = 1;
            List<SourceCitation> keyed = new java.util.ArrayList<>();
            for (SourceCitation citation : citations) {
                if (citation.citationKey() != null && !citation.citationKey().isBlank()) {
                    keyed.add(citation);
                    continue;
                }
                String key;
                do {
                    key = "citation-" + nextGeneratedKey++;
                } while (!usedKeys.add(key));
                keyed.add(citation.withCitationKey(key));
            }
            return new Request(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, maps, keyed, violations, previousCandidate, generationMode, sourceConstraintPack,
                    retrievalContext);
        }
        public Request withViolations(List<String> nextViolations) {
            return new Request(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, maps, citations, nextViolations, previousCandidate, generationMode, sourceConstraintPack,
                    retrievalContext);
        }
        public Request withPreviousCandidate(String candidate) {
            return new Request(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, maps, citations, violations, candidate, generationMode, sourceConstraintPack,
                    retrievalContext);
        }
    }

    record RepairRequest(String operationId, long packageRevision, int partySize,
                         AdventurePlanConfiguration configuration, String previousCandidate,
                         List<AdventureStoryPlanProjectionViolation> violations,
                         RepairScope repairScope,
                         List<String> sourceDocuments, List<String> resolutionEvidence,
                         List<MapContext> maps, List<SourceCitation> citations,
                         RetrievalContext retrievalContext) {
        public RepairRequest(String operationId, long packageRevision, int partySize,
                AdventurePlanConfiguration configuration, String previousCandidate,
                List<AdventureStoryPlanProjectionViolation> violations,
                List<String> sourceDocuments, List<String> resolutionEvidence,
                List<MapContext> maps, List<SourceCitation> citations) {
            this(operationId, packageRevision, partySize, configuration, previousCandidate, violations,
                    sourceDocuments, resolutionEvidence, maps, citations, RetrievalContext.empty());
        }

        public RepairRequest(String operationId, long packageRevision, int partySize,
                AdventurePlanConfiguration configuration, String previousCandidate,
                List<AdventureStoryPlanProjectionViolation> violations,
                RepairScope repairScope,
                List<String> sourceDocuments, List<String> resolutionEvidence,
                List<MapContext> maps, List<SourceCitation> citations) {
            this(operationId, packageRevision, partySize, configuration, previousCandidate, violations,
                    repairScope, sourceDocuments, resolutionEvidence, maps, citations, RetrievalContext.empty());
        }

        public RepairRequest(String operationId, long packageRevision, int partySize,
                AdventurePlanConfiguration configuration, String previousCandidate,
                List<AdventureStoryPlanProjectionViolation> violations,
                List<String> sourceDocuments, List<String> resolutionEvidence,
                List<MapContext> maps, List<SourceCitation> citations,
                RetrievalContext retrievalContext) {
            this(operationId, packageRevision, partySize, configuration, previousCandidate, violations,
                    AdventureStoryPlanProjectionDependencyPolicy.scope(previousCandidate, violations),
                    sourceDocuments, resolutionEvidence, maps, citations, retrievalContext);
        }

        public RepairRequest {
            if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("repair operation id must not be blank");
            if (configuration == null) throw new IllegalArgumentException("repair configuration must not be null");
            if (previousCandidate == null || previousCandidate.isBlank()) throw new IllegalArgumentException("previous full candidate must not be blank");
            violations = violations == null ? List.of() : List.copyOf(violations);
            sourceDocuments = sourceDocuments == null ? List.of() : List.copyOf(sourceDocuments);
            resolutionEvidence = resolutionEvidence == null ? List.of() : List.copyOf(resolutionEvidence);
            maps = maps == null ? List.of() : List.copyOf(maps);
            citations = citations == null ? List.of() : List.copyOf(citations);
            retrievalContext = retrievalContext == null ? RetrievalContext.empty() : retrievalContext;
            if (repairScope == null) {
                throw new IllegalArgumentException("deterministic repair scope must be explicit");
            }
            RepairScope expectedScope = AdventureStoryPlanProjectionDependencyPolicy.scope(previousCandidate, violations);
            if (!expectedScope.equals(repairScope)) {
                throw new IllegalArgumentException("repair scope must equal the deterministic blocker dependency closure");
            }
        }

        public Request toGenerationRequest() {
            return new Request(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, maps, citations,
                    violations.stream().map(AdventureStoryPlanProjectionViolation::sanitizedMessage).toList(), previousCandidate,
                    StoryPlanGenerationMode.GENERATIVE, new SourceConstraintPack(List.of(), List.of()), retrievalContext);
        }
    }

    record RetrievalContext(UUID ownerId, List<RetrievalDocument> documents) {
        public RetrievalContext {
            documents = documents == null ? List.of() : List.copyOf(documents);
        }

        public static RetrievalContext empty() {
            return new RetrievalContext(null, List.of());
        }
    }

    record RetrievalDocument(String documentType, UUID documentId, long extractionVersion) {}

    record ProjectionCandidate(String serializedCandidate, List<AdventureStoryPlanStage> stages) {
        public ProjectionCandidate {
            if (serializedCandidate == null || serializedCandidate.isBlank()) throw new IllegalArgumentException("full candidate must not be blank");
            stages = stages == null ? List.of() : List.copyOf(stages);
            AdventureStoryPlanProjectionCandidateConsistency.assertEquivalent(serializedCandidate, stages);
        }

        public static ProjectionCandidate fromStages(List<AdventureStoryPlanStage> stages) {
            return new ProjectionCandidate(AdventureStoryPlanProjectionCandidateConsistency.serialize(stages), stages);
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
    record SourceCitation(String documentType, UUID documentId, long extractionVersion, String locator, String quote,
            double confidence, com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance provenance,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) String citationKey) {
        public SourceCitation(String documentType, UUID documentId, long extractionVersion, String locator,
                String quote, double confidence) {
            this(documentType, documentId, extractionVersion, locator, quote, confidence,
                    new com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance(
                            new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(documentId),
                            extractionVersion, 1, List.of(), List.of(), null, locator), "");
        }
        public SourceCitation(String documentType, UUID documentId, long extractionVersion, String locator,
                String quote, double confidence,
                com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance provenance) {
            this(documentType, documentId, extractionVersion, locator, quote, confidence, provenance, "");
        }
        public SourceCitation withCitationKey(String key) {
            return new SourceCitation(documentType, documentId, extractionVersion, locator, quote, confidence, provenance, key);
        }
        public SourceCitation {
            citationKey = citationKey == null ? "" : citationKey;
        }
    }
}
