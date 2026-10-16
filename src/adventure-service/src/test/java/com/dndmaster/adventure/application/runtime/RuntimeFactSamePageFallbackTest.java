package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService.RuntimeActionContext;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.runtime.GameState;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RuntimeFactSamePageFallbackTest {
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final RuntimeEvidence ANCHOR = evidence("page=3:chunk=anchor", "known passage");
    private static final RuntimeEvidence SAME_PAGE = evidence("page=3:chunk=neighbor", "continued rule passage");
    private String previousDiagnostics;

    @BeforeEach
    void enableDiagnostics() {
        previousDiagnostics = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
    }

    @AfterEach
    void restoreDiagnostics() {
        if (previousDiagnostics == null) {
            System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        } else {
            System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previousDiagnostics);
        }
    }

    @Test
    void supplements_once_only_after_normal_not_found_and_retries_with_the_added_evidence(CapturedOutput output) {
        Fixture fixture = new Fixture();
        when(fixture.lookup.lookup(eq(fixture.owner.value()), any(RuntimeFactLookupRequest.class), any()))
                .thenReturn(RuntimeFactLookupResult.notFound(), RuntimeFactLookupResult.found(
                        RuntimeFactLookupResult.Source.SCENARIO_MODEL, "rule found"));
        var acquirer = new RuntimePlayerActionEvidenceAcquirer(request -> List.of(),
                (scope, anchors, limit) -> {
                    assertEquals(4, limit);
                    assertEquals(List.of(ANCHOR), anchors);
                    assertEquals(1, scope.documents().getFirst().extractionVersion());
                    return List.of(ANCHOR, SAME_PAGE);
                });
        fixture.service.setPlayerActionEvidenceAcquirer(acquirer);

        RuntimeActionContext result = fixture.service.lookupRuntimeFactsWithSamePageExpansion(
                fixture.command, fixture.adventure, fixture.binding, fixture.scenario,
                new EvidencePack(List.of(ANCHOR), List.of(), List.of()), "", List.of());

        assertEquals(List.of(SAME_PAGE), result.evidencePack().storybook().stream()
                .filter(evidence -> !evidence.equals(ANCHOR)).toList());
        assertEquals(RuntimeFactLookupResult.Status.FOUND, result.relatedFacts().getFirst().status());
        ArgumentCaptor<RuntimeFactLookupRequest> request = ArgumentCaptor.forClass(RuntimeFactLookupRequest.class);
        verify(fixture.lookup, org.mockito.Mockito.times(2)).lookup(eq(fixture.owner.value()), request.capture(), any());
        assertEquals(List.of(SAME_PAGE, ANCHOR), request.getAllValues().getLast().evidence());
        org.assertj.core.api.Assertions.assertThat(output.getAll())
                .contains("operation=runtime_fact_same_page_fallback outcome=started")
                .contains("trigger=EXPLICIT_NOT_FOUND")
                .contains("retryStatus=FOUND")
                .contains("selectedCount=1")
                .doesNotContain(ANCHOR.excerpt(), SAME_PAGE.excerpt());
    }

    @Test
    void found_fact_does_not_trigger_supplementary_lookup(CapturedOutput output) {
        Fixture fixture = new Fixture();
        when(fixture.lookup.lookup(eq(fixture.owner.value()), any(RuntimeFactLookupRequest.class), any()))
                .thenReturn(RuntimeFactLookupResult.found(RuntimeFactLookupResult.Source.GAME_STATE, "already known"));
        fixture.service.setPlayerActionEvidenceAcquirer(new RuntimePlayerActionEvidenceAcquirer(request -> List.of(),
                fixture.locationLookup));

        fixture.service.lookupRuntimeFactsWithSamePageExpansion(fixture.command, fixture.adventure, fixture.binding,
                fixture.scenario, new EvidencePack(List.of(ANCHOR), List.of(), List.of()), "", List.of());

        verify(fixture.lookup).lookup(eq(fixture.owner.value()), any(RuntimeFactLookupRequest.class), any());
        verify(fixture.locationLookup, never()).findOnSamePages(any(), any(), anyInt());
        org.assertj.core.api.Assertions.assertThat(output.getAll()).contains("reason=LOOKUP_FOUND result=skipped");
    }

    @Test
    void technical_lookup_failure_does_not_trigger_supplementary_lookup(CapturedOutput output) {
        Fixture fixture = new Fixture();
        when(fixture.lookup.lookup(eq(fixture.owner.value()), any(RuntimeFactLookupRequest.class), any()))
                .thenThrow(new IllegalStateException("lookup unavailable", new IOException("network unavailable")));
        fixture.service.setPlayerActionEvidenceAcquirer(new RuntimePlayerActionEvidenceAcquirer(request -> List.of(),
                fixture.locationLookup));

        RuntimeActionContext result = fixture.service.lookupRuntimeFactsWithSamePageExpansion(
                fixture.command, fixture.adventure, fixture.binding, fixture.scenario,
                new EvidencePack(List.of(ANCHOR), List.of(), List.of()), "", List.of());

        assertEquals(List.of(), result.relatedFacts());
        verify(fixture.lookup).lookup(eq(fixture.owner.value()), any(RuntimeFactLookupRequest.class), any());
        verify(fixture.locationLookup, never()).findOnSamePages(any(), any(), anyInt());
        org.assertj.core.api.Assertions.assertThat(output.getAll())
                .contains("expectedStatus=FOUND_OR_NOT_FOUND actualStatus=UNAVAILABLE fallback=skipped")
                .contains("lookupStatus=UNAVAILABLE technicalFailure=true reason=TECHNICAL_LOOKUP_FAILURE result=skipped");
    }

    @Test
    void technical_supplement_failure_is_not_returned_as_a_normal_not_found(CapturedOutput output) {
        Fixture fixture = new Fixture();
        when(fixture.lookup.lookup(eq(fixture.owner.value()), any(RuntimeFactLookupRequest.class), any()))
                .thenReturn(RuntimeFactLookupResult.notFound());
        when(fixture.locationLookup.findOnSamePages(any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("same-page lookup transport failed", new IOException("offline")));
        fixture.service.setPlayerActionEvidenceAcquirer(new RuntimePlayerActionEvidenceAcquirer(request -> List.of(),
                fixture.locationLookup));

        RuntimeActionContext result = fixture.service.lookupRuntimeFactsWithSamePageExpansion(
                fixture.command, fixture.adventure, fixture.binding, fixture.scenario,
                new EvidencePack(List.of(ANCHOR), List.of(), List.of()), "", List.of());

        assertEquals(List.of(), result.relatedFacts());
        verify(fixture.lookup).lookup(eq(fixture.owner.value()), any(RuntimeFactLookupRequest.class), any());
        org.assertj.core.api.Assertions.assertThat(output.getAll())
                .contains("operation=runtime_fact_same_page_fallback outcome=failed")
                .doesNotContain(ANCHOR.excerpt());
    }

    private static RuntimeEvidence evidence(String locator, String excerpt) {
        return new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, new KnowledgeDocumentId(DOCUMENT_ID), 1, locator, excerpt);
    }

    private static final class Fixture {
        private final OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        private final SessionId sessionId = SessionId.generate();
        private final UUID packageId = UUID.randomUUID();
        private final RuntimeFactLookupService lookup = mock(RuntimeFactLookupService.class);
        private final RuntimeEvidenceLocationLookupPort locationLookup = mock(RuntimeEvidenceLocationLookupPort.class);
        private final Adventure adventure = mock(Adventure.class);
        private final RuntimeBinding binding = mock(RuntimeBinding.class);
        private final ScenarioPackage scenario = mock(ScenarioPackage.class);
        private final SubmitRuntimeTurnCommand command = new SubmitRuntimeTurnCommand(AdventureId.generate(), owner,
                UUID.randomUUID(), UUID.randomUUID(), "find the rule");
        private final RuntimeTurnApplicationService service;

        private Fixture() {
            SessionKnowledgeSetRepository knowledge = mock(SessionKnowledgeSetRepository.class);
            when(knowledge.findBySessionId(sessionId)).thenReturn(Optional.empty());
            service = new RuntimeTurnApplicationService(mock(com.dndmaster.adventure.application.saved.AdventureRepository.class),
                    mock(RuntimeBindingRepository.class), mock(com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository.class),
                    mock(RuntimeTurnRepository.class), mock(RuntimeEvidenceSearchPort.class), mock(RuntimePlanningPort.class),
                    mock(NarrationSafetyPort.class), knowledge);
            service.setRuntimeFactLookupService(lookup);
            when(adventure.ownerPlayerId()).thenReturn(owner);
            when(adventure.sessionId()).thenReturn(sessionId);
            when(adventure.gameState()).thenReturn(GameState.empty());
            when(adventure.runtimeAddedFacts()).thenReturn(List.of());
            when(adventure.currentSituation()).thenReturn(new com.dndmaster.adventure.domain.runtime.CurrentSituation(
                    UUID.randomUUID(), 1, "cellar", "find the rule", "danger", "exit"));
            when(adventure.currentContext()).thenReturn(new AdventureContext("cellar", null, null, null));
            when(binding.scenarioPackageId()).thenReturn(packageId);
            when(binding.activeSourceContext()).thenReturn(null);
            when(scenario.scenarioModel()).thenReturn(mock(ScenarioModel.class));
            when(scenario.documents()).thenReturn(List.of(new ScenarioBundleDocumentSelection(
                    new KnowledgeDocumentId(DOCUMENT_ID), ScenarioBundleDocumentRole.MAIN_SCENARIO,
                    com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                    "scenario.pdf", "STORYBOOK", 1)));
        }
    }
}
