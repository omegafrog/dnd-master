package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionApplicationService;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceSufficiencyPolicy;
import com.dndmaster.adventure.evidence.SufficiencyDecision;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpeningSceneEvidenceAcquirerTest {
    @Test
    void sends_the_server_confirmed_session_package_and_storybook_scope_to_common_acquisition() {
        UUID owner = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        KnowledgeDocumentId storybook = new KnowledgeDocumentId(UUID.randomUUID());
        EvidenceCandidate candidate = new EvidenceCandidate(
                UUID.randomUUID(), storybook.value().toString(), "STORYBOOK", "page:2", "first playable room");
        AtomicReference<com.dndmaster.adventure.evidence.EvidenceAcquisitionRequest> captured = new AtomicReference<>();
        EvidenceAcquisitionApplicationService acquisition = new EvidenceAcquisitionApplicationService(
                request -> {
                    captured.set(request.acquisitionRequest());
                    return List.of(candidate);
                },
                request -> List.of(candidate.id()),
                request -> SufficiencyDecision.sufficient(List.of(candidate.id()), Map.of(candidate.id(), "opening context")));

        OpeningSceneEvidenceAcquirer acquirer = new OpeningSceneEvidenceAcquirer(acquisition);
        var result = acquirer.acquire(new OwnerPlayerId(owner), session, scenarioPackage(storybook));

        assertTrue(result.sufficient());
        assertEquals(List.of(candidate), result.selectedEvidence());
        assertEquals(EvidenceSufficiencyPolicy.FinalInsufficiency.OPENING_PREPARATION_FAILED,
                result.finalInsufficiency());
        var request = captured.get();
        assertEquals("OPENING_SCENE", request.policyId());
        assertEquals(owner, request.searchScope().ownerId());
        assertEquals(session, request.searchScope().sessionId());
        assertEquals(result.scenarioPackageId(), request.searchScope().scenarioPackageId());
        assertEquals(List.of(new com.dndmaster.adventure.evidence.EvidenceSearchScope.Document(
                storybook.value(), 7, "STORYBOOK")), request.searchScope().documents());
    }

    @Test
    void returns_start_preparation_failure_after_common_acquisition_exhausts_two_additional_searches() {
        KnowledgeDocumentId storybook = new KnowledgeDocumentId(UUID.randomUUID());
        EvidenceCandidate candidate = new EvidenceCandidate(
                UUID.randomUUID(), storybook.value().toString(), "STORYBOOK", "page:2", "partial opening");
        AtomicInteger searches = new AtomicInteger();
        EvidenceAcquisitionApplicationService acquisition = new EvidenceAcquisitionApplicationService(
                request -> {
                    searches.incrementAndGet();
                    return List.of(candidate);
                },
                request -> List.of(candidate.id()),
                request -> SufficiencyDecision.insufficient(
                        List.of(candidate.id()), Map.of(candidate.id(), "does not establish a playable opening"),
                        "the first playable location is missing"));

        OpeningSceneEvidenceAcquirer.Result result = new OpeningSceneEvidenceAcquirer(acquisition)
                .acquire(new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), scenarioPackage(storybook));

        assertFalse(result.sufficient());
        assertEquals(3, searches.get());
        assertEquals(List.of(candidate), result.selectedEvidence());
        assertEquals(Map.of(candidate.id(), "does not establish a playable opening"), result.selectionReasons());
        assertEquals(EvidenceSufficiencyPolicy.FinalInsufficiency.OPENING_PREPARATION_FAILED,
                result.finalInsufficiency());
        assertEquals("the first playable location is missing", result.missing());
    }

    @Test
    void allows_rulebook_only_generation_without_storybook_evidence() {
        ScenarioPackage rulebookOnly = ScenarioPackage.publish(
                ScenarioBundleId.generate(), 1, "rulebook-only", List.of(), List.of(),
                new ScenarioCompilationReport(
                        com.dndmaster.adventure.domain.scenario.ResolutionStatus.COMPLETE, List.of()));

        var result = new OpeningSceneEvidenceAcquirer(nullSafeAcquisition()).acquire(
                new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), rulebookOnly);

        assertTrue(result.sufficient());
        assertTrue(result.selectedEvidence().isEmpty());
        assertTrue(result.rulebookOnlyGenerationAllowed());
    }

    private static EvidenceAcquisitionApplicationService nullSafeAcquisition() {
        EvidenceCandidate candidate = new EvidenceCandidate(UUID.randomUUID(), "doc", "STORYBOOK", "page:1", "unused");
        return new EvidenceAcquisitionApplicationService(
                request -> List.of(candidate), request -> List.of(candidate.id()),
                request -> SufficiencyDecision.sufficient(List.of(candidate.id()), Map.of(candidate.id(), "unused")));
    }

    private static ScenarioPackage scenarioPackage(KnowledgeDocumentId storybook) {
        return ScenarioPackage.publish(
                ScenarioBundleId.generate(), 1, "opening-test",
                List.of(new ScenarioBundleDocumentSelection(
                        storybook, ScenarioBundleDocumentRole.MAIN_SCENARIO,
                        com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                        "scenario.pdf", "STORYBOOK", 7)),
                List.<ScenarioResolutionUnit>of(),
                new ScenarioCompilationReport(
                        com.dndmaster.adventure.domain.scenario.ResolutionStatus.COMPLETE, List.of()));
    }
}
