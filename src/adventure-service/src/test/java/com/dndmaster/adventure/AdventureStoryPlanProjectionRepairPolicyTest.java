package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionRepairPolicy;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation.Repairability;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanProjectionRepairPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void carries_field_specific_violation_without_turning_sensitive_evidence_into_the_message() {
        var violation = new AdventureStoryPlanProjectionViolation(
                "REQUIRED_FIELD_MISSING", 2, "stages[2].transitionCondition", "",
                "citation-7", Repairability.REPAIRABLE,
                "stage 2 transitionCondition is required");

        assertEquals("REQUIRED_FIELD_MISSING", violation.code());
        assertEquals(2, violation.stagePosition());
        assertEquals("stages[2].transitionCondition", violation.fieldPath());
        assertEquals("", violation.rejectedValue());
        assertEquals("citation-7", violation.citationContext());
        assertEquals(Repairability.REPAIRABLE, violation.repairability());
        assertEquals("stage 2 transitionCondition is required", violation.sanitizedMessage());
    }

    @Test
    void repair_request_carries_full_candidate_violations_and_authoritative_registries() {
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", java.util.UUID.randomUUID(), 4, "page:2", "authoritative", .9)
                .withCitationKey("citation-7");
        var violation = new AdventureStoryPlanProjectionViolation(
                "UNKNOWN_CITATION_KEY", 1, "stages[1].evidence[0].citationKey", "citation-999",
                "citation-999", Repairability.SOURCE_EVIDENCE_INSUFFICIENT,
                "stage 1 evidence citation key is not registered");
        var request = new AdventureStoryPlanGenerationPort.RepairRequest(
                "op-019", 3, 1, com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration.defaults(),
                "{\"stages\":[{\"position\":1}]}", List.of(violation),
                List.of("storybook.pdf"), List.of("source excerpt"), List.of(), List.of(citation));

        assertEquals("{\"stages\":[{\"position\":1}]}", request.previousCandidate());
        assertEquals(List.of(violation), request.violations());
        assertEquals("citation-7", request.citations().getFirst().citationKey());
        assertEquals(List.of("storybook.pdf"), request.sourceDocuments());
    }

    @Test
    void rejects_mutation_to_a_field_not_listed_by_the_projection_violation() throws Exception {
        var previous = mapper.readTree("""
                {"stages":[{"position":1,"title":"Start","transitionCondition":"Open"}]}
                """);
        var repaired = mapper.readTree("""
                {"stages":[{"position":1,"title":"Tampered","transitionCondition":"Closed"}]}
                """);
        var violations = List.of(new AdventureStoryPlanProjectionViolation(
                "INVALID_TRANSITION_CONDITION", 1, "stages[0].transitionCondition", "Open",
                "", Repairability.REPAIRABLE, "transitionCondition is not usable"));

        var failure = assertThrows(AdventureStoryPlanProjectionRepairPolicy.UnlistedFieldMutation.class,
                () -> AdventureStoryPlanProjectionRepairPolicy.assertOnlyListedFieldsChanged(previous, repaired, violations));

        assertEquals("stages[0].title", failure.violation().fieldPath());
        assertEquals(Repairability.SYSTEM_CONTRACT_ERROR, failure.violation().repairability());
    }

    @Test
    void accepts_a_full_candidate_that_changes_only_the_listed_field() throws Exception {
        var previous = mapper.readTree("""
                {"stages":[{"position":1,"title":"Start","transitionCondition":"Open"}]}
                """);
        var repaired = mapper.readTree("""
                {"stages":[{"position":1,"title":"Start","transitionCondition":"Closed"}]}
                """);
        var violations = List.of(new AdventureStoryPlanProjectionViolation(
                "INVALID_TRANSITION_CONDITION", 1, "stages[0].transitionCondition", "Open",
                "", Repairability.REPAIRABLE, "transitionCondition is not usable"));

        AdventureStoryPlanProjectionRepairPolicy.assertOnlyListedFieldsChanged(previous, repaired, violations);
    }

    @Test
    void accepts_wildcard_field_paths_for_concrete_stage_and_evidence_indices() throws Exception {
        var previous = mapper.readTree("""
                {"stages":[{"title":"Start","evidence":[{"citationKey":"citation-1"}]}]}
                """);
        var repaired = mapper.readTree("""
                {"stages":[{"title":"Start","evidence":[{"citationKey":"citation-2"}]}]}
                """);
        var violations = List.of(new AdventureStoryPlanProjectionViolation(
                "UNKNOWN_CITATION_KEY", 1, "stages[*].evidence[*].citationKey", "citation-1",
                "citation-1", Repairability.REPAIRABLE, "citation key is not registered"));

        AdventureStoryPlanProjectionRepairPolicy.assertOnlyListedFieldsChanged(previous, repaired, violations);
    }

    @Test
    void exposes_all_four_repairability_classifications_as_part_of_the_contract() {
        assertEquals(java.util.Set.of(Repairability.REPAIRABLE, Repairability.REGENERATE_REQUIRED,
                        Repairability.SOURCE_EVIDENCE_INSUFFICIENT, Repairability.SYSTEM_CONTRACT_ERROR),
                java.util.Set.of(Repairability.values()));
    }
}
