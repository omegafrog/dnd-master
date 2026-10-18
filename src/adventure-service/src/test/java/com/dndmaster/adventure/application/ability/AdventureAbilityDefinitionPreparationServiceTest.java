package com.dndmaster.adventure.application.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.runtime.GmProviderSelection;
import com.dndmaster.adventure.application.runtime.ProviderBinding;
import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceLocationLookupPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.preparation.InitialSituationGmPort;
import com.dndmaster.adventure.application.scenario.preparation.TransientInitialSituationGmFailureException;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import com.dndmaster.adventure.domain.scenario.KnowledgeDocumentStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureAbilityDefinitionPreparationServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void corrects_invalid_structured_output_and_saves_only_after_validation_can_succeed() throws Exception {
        var definitions = mock(AdventureAbilityDefinitionRepository.class);
        var bindings = mock(GmProviderBindingRepository.class);
        var gm = mock(InitialSituationGmPort.class);
        UUID sessionUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID documentUuid = UUID.randomUUID();
        SessionId session = new SessionId(sessionUuid);
        when(bindings.current(sessionUuid)).thenReturn(Optional.of(new ProviderBinding(sessionUuid,
                new GmProviderSelection(endpointId, "codex", "test-model", "medium"), 1)));
        when(gm.generate(any())).thenReturn(JSON.readTree("{\"abilityId\":\"ability:blink\"}"), validDefinition(documentUuid));
        var service = new AdventureAbilityDefinitionPreparationService(definitions,
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), mock(RuntimeEvidenceLocationLookupPort.class), bindings, gm, JSON);
        var evidence = List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentUuid), 3, "page 12", "The ability moves the user.", "cite-1"));

        JsonNode result = service.generateValidated(new AdventureId(UUID.randomUUID()), session,
                new OwnerPlayerId(ownerUuid), "ability:blink", evidence, java.util.Map.of(documentUuid, 3L));

        assertThat(result.path("abilityId").asText()).isEqualTo("ability:blink");
        verify(gm, times(2)).generate(any());
    }

    @Test
    void repeated_validation_failure_exhausts_distinct_correction_paths_and_requests_source_input() throws Exception {
        var bindings = mock(GmProviderBindingRepository.class);
        var gm = mock(InitialSituationGmPort.class);
        UUID sessionUuid = UUID.randomUUID();
        UUID documentUuid = UUID.randomUUID();
        var invalid = JSON.readTree("{\"abilityId\":\"ability:blink\"}");
        when(bindings.current(sessionUuid)).thenReturn(Optional.of(new ProviderBinding(sessionUuid,
                new GmProviderSelection(UUID.randomUUID(), "codex", "test-model", "medium"), 1)));
        when(gm.generate(any())).thenReturn(invalid, invalid);
        var service = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), mock(RuntimeEvidenceLocationLookupPort.class), bindings, gm, JSON);
        var evidence = List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentUuid), 3, "page 12", "The ability moves the user.", "cite-1"));

        assertThatThrownBy(() -> service.generateValidated(new AdventureId(UUID.randomUUID()),
                new SessionId(sessionUuid), new OwnerPlayerId(UUID.randomUUID()), "ability:blink", evidence,
                java.util.Map.of(documentUuid, 3L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gm, times(2)).generate(any());
    }

    @Test
    void retries_once_after_a_classified_transient_provider_failure() throws Exception {
        var bindings = mock(GmProviderBindingRepository.class);
        var gm = mock(InitialSituationGmPort.class);
        UUID sessionUuid = UUID.randomUUID();
        UUID documentUuid = UUID.randomUUID();
        when(bindings.current(sessionUuid)).thenReturn(Optional.of(new ProviderBinding(sessionUuid,
                new GmProviderSelection(UUID.randomUUID(), "codex", "test-model", "medium"), 1)));
        when(gm.generate(any())).thenThrow(new TransientInitialSituationGmFailureException(
                new java.io.IOException("temporary provider failure")))
                .thenReturn(validDefinition(documentUuid));
        var service = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), mock(RuntimeEvidenceLocationLookupPort.class), bindings, gm, JSON);
        var evidence = List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentUuid), 3, "page 12", "The ability moves the user.", "cite-1"));

        JsonNode result = service.generateValidated(new AdventureId(UUID.randomUUID()), new SessionId(sessionUuid),
                new OwnerPlayerId(UUID.randomUUID()), "ability:blink", evidence,
                java.util.Map.of(documentUuid, 3L));

        assertThat(result.path("abilityId").asText()).isEqualTo("ability:blink");
        verify(gm, times(2)).generate(any());
    }

    @Test
    void validation_repair_diagnostics_include_failure_code_and_expected_actual_field_shapes() throws Exception {
        var bindings = mock(GmProviderBindingRepository.class);
        var gm = mock(InitialSituationGmPort.class);
        UUID sessionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(bindings.current(sessionId)).thenReturn(Optional.of(new ProviderBinding(sessionId,
                new GmProviderSelection(UUID.randomUUID(), "codex", "test-model", "medium"), 1)));
        when(gm.generate(any())).thenReturn(JSON.readTree("{\"abilityId\":\"ability:blink\"}"), validDefinition(documentId));
        var service = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), mock(RuntimeEvidenceLocationLookupPort.class), bindings, gm, JSON);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                AdventureAbilityDefinitionPreparationService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(DevelopmentDiagnostics.FLAG);
        System.setProperty(DevelopmentDiagnostics.FLAG, "true");
        try {
            service.generateValidated(new AdventureId(UUID.randomUUID()), new SessionId(sessionId),
                    new OwnerPlayerId(UUID.randomUUID()), "ability:blink",
                    List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK, new KnowledgeDocumentId(documentId),
                            3, "page 12", "The ability moves the user.", "cite-1")),
                    java.util.Map.of(documentId, 3L));

            String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("outcome=structured_output_repair", "abilityId=ability:blink",
                    "validation_code=SCHEMA_OR_IDENTIFIER", "expectedFields=abilityId,name,actionTiming",
                    "actualFields=abilityId", "recovery=regenerate_with_validation_feedback");
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previous);
        }
    }

    @Test
    void does_not_retry_a_nontransient_generation_failure() {
        var bindings = mock(GmProviderBindingRepository.class);
        var gm = mock(InitialSituationGmPort.class);
        UUID sessionUuid = UUID.randomUUID();
        when(bindings.current(sessionUuid)).thenReturn(Optional.of(new ProviderBinding(sessionUuid,
                new GmProviderSelection(UUID.randomUUID(), "codex", "test-model", "medium"), 1)));
        when(gm.generate(any())).thenThrow(new IllegalStateException("provider selection is invalid"));
        var service = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), mock(RuntimeEvidenceLocationLookupPort.class), bindings, gm, JSON);
        var evidence = List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(UUID.randomUUID()), 3, "page 12", "The ability moves the user.", "cite-1"));

        assertThatThrownBy(() -> service.generateValidated(new AdventureId(UUID.randomUUID()), new SessionId(sessionUuid),
                new OwnerPlayerId(UUID.randomUUID()), "ability:blink", evidence, java.util.Map.of()))
                .isInstanceOf(IllegalStateException.class);
        verify(gm, times(1)).generate(any());
    }

    @Test
    void rejects_user_input_that_does_not_match_the_fixed_definition_field_types() throws Exception {
        UUID documentId = UUID.randomUUID();
        JsonNode malformed = ((com.fasterxml.jackson.databind.node.ObjectNode) validDefinition(documentId)).put("range", 30);
        var service = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), mock(RuntimeEvidenceLocationLookupPort.class), mock(GmProviderBindingRepository.class),
                mock(InitialSituationGmPort.class), JSON);
        var evidence = List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentId), 3, "page 12", "The ability moves the user.", "cite-1"));

        assertThatThrownBy(() -> service.validate("ability:blink", malformed, evidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("string or null");
        JsonNode invalidEnum = ((com.fasterxml.jackson.databind.node.ObjectNode) validDefinition(documentId))
                .put("actionKind", "OTHER");
        assertThatThrownBy(() -> service.validate("ability:blink", invalidEnum, evidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enum value is not supported");
    }

    @Test
    void accepts_source_based_definition_when_rag_has_no_excerpt_but_source_is_selected_for_the_adventure() throws Exception {
        UUID documentId = UUID.randomUUID();
        JsonNode sourceDefinition = validDefinition(documentId);
        var service = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), mock(RuntimeEvidenceLocationLookupPort.class), mock(GmProviderBindingRepository.class),
                mock(InitialSituationGmPort.class), JSON);

        service.validate("ability:blink", sourceDefinition, List.of(), java.util.Map.of(documentId, 3L), true);
        var sourceEvidence = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK, new KnowledgeDocumentId(documentId),
                3, "page 12", "The ability moves the user.", "cite-1");
        var locations = mock(RuntimeEvidenceLocationLookupPort.class);
        when(locations.findOnSamePages(any(), any(), org.mockito.ArgumentMatchers.eq(1))).thenReturn(List.of(sourceEvidence));
        var sourceService = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), locations, mock(GmProviderBindingRepository.class),
                mock(InitialSituationGmPort.class), JSON);
        var scope = new EvidenceSearchScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ability-definition", "RULE_LOOKUP", List.of(new EvidenceSearchScope.Document(documentId, 3, "RULEBOOK")), List.of());
        org.assertj.core.api.Assertions.assertThatCode(() -> sourceService.validateSourceLocators(sourceDefinition,
                List.of(), scope)).doesNotThrowAnyException();
    }

    @Test
    void prepare_saves_valid_source_input_after_search_recovery_would_have_been_exhausted() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        SessionId sessionKey = new SessionId(sessionId);
        var definitions = mock(AdventureAbilityDefinitionRepository.class);
        var packages = mock(ScenarioPackageRepository.class);
        var knowledgeSets = mock(SessionKnowledgeSetRepository.class);
        var sessions = mock(AdventureSessionRepository.class);
        var evidenceSearch = mock(RuntimeEvidenceSearchPort.class);
        var locations = mock(RuntimeEvidenceLocationLookupPort.class);
        when(definitions.find(packageId, "ability:blink")).thenReturn(Optional.empty());
        when(sessions.findById(sessionKey)).thenReturn(Optional.of(AdventureSession.rehydrate(sessionKey,
                new OwnerPlayerId(ownerId), packageId, 1, 1, List.of(), 0)));
        var document = new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(documentId),
                ScenarioBundleDocumentRole.RULEBOOK, mock(KnowledgeDocumentStatus.class), "rules.pdf", "RULEBOOK", 3);
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.documents()).thenReturn(List.of(document));
        when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
        when(knowledgeSets.findBySessionId(sessionKey)).thenReturn(Optional.of(new SessionKnowledgeSet(sessionKey,
                List.of(new KnowledgeDocumentId(documentId)))));
        when(locations.findOnSamePages(any(), any(), org.mockito.ArgumentMatchers.eq(1))).thenReturn(List.of(
                new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK, new KnowledgeDocumentId(documentId), 3,
                        "page 12", "indexed source location", "source-anchor")));
        var service = new AdventureAbilityDefinitionPreparationService(definitions, packages, knowledgeSets,
                sessions, mock(RuntimeCharacterSheetReadPort.class), evidenceSearch, locations,
                mock(GmProviderBindingRepository.class), mock(InitialSituationGmPort.class), JSON);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                AdventureAbilityDefinitionPreparationService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(DevelopmentDiagnostics.FLAG);
        System.setProperty(DevelopmentDiagnostics.FLAG, "true");
        try {
            service.prepare(new AdventureId(packageId), sessionKey, new OwnerPlayerId(ownerId),
                    List.of("ability:blink"), validDefinition(documentId));
            assertThatThrownBy(() -> service.prepare(new AdventureId(packageId), sessionKey,
                    new OwnerPlayerId(ownerId), List.of("ability:blink"), JSON.readTree("{\"abilityId\":\"other\"}")))
                    .isInstanceOf(AbilityDefinitionSourceInputRequiredException.class);
            org.mockito.Mockito.doThrow(new IllegalStateException("definition store unavailable"))
                    .when(definitions).prepare(packageId, "ability:blink", validDefinition(documentId));
            assertThatThrownBy(() -> service.prepare(new AdventureId(packageId), sessionKey,
                    new OwnerPlayerId(ownerId), List.of("ability:blink"), validDefinition(documentId)))
                    .isInstanceOf(IllegalStateException.class)
                    .isNotInstanceOf(AbilityDefinitionSourceInputRequiredException.class);

            verify(evidenceSearch, org.mockito.Mockito.never()).search(any());
            verify(definitions, times(2)).prepare(packageId, "ability:blink", validDefinition(documentId));
            String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("dev_ability_preparation", "outcome=started", "outcome=complete",
                    "adventureId=" + packageId, "sessionId=" + sessionId, "abilityId=ability:blink",
                    "expected=valid_definition_saved", "actual=sourceDefinitionFields=",
                    "outcome=source_input_required", "failureKind=invalid_source_input_set",
                    "actualInputType=object", "recovery=resubmit_source_definition",
                    "outcome=failed", "expected=definition_saved", "definitionSaveCompleted=false",
                    "recovery=resolve_failure_then_retry_operation");
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previous);
        }
    }

    @Test
    void keeps_story_source_evidence_when_independent_rule_source_search_fails() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID ruleDocumentId = UUID.randomUUID();
        UUID storyDocumentId = UUID.randomUUID();
        SessionId sessionKey = new SessionId(sessionId);
        var definitions = mock(AdventureAbilityDefinitionRepository.class);
        var packages = mock(ScenarioPackageRepository.class);
        var knowledgeSets = mock(SessionKnowledgeSetRepository.class);
        var sessions = mock(AdventureSessionRepository.class);
        var evidenceSearch = mock(RuntimeEvidenceSearchPort.class);
        var bindings = mock(GmProviderBindingRepository.class);
        var gm = mock(InitialSituationGmPort.class);
        when(definitions.find(packageId, "ability:blink")).thenReturn(Optional.empty());
        when(sessions.findById(sessionKey)).thenReturn(Optional.of(AdventureSession.rehydrate(sessionKey,
                new OwnerPlayerId(ownerId), packageId, 1, 1, List.of(), 0)));
        var ruleDocument = selectedDocument(ruleDocumentId, "RULEBOOK");
        var storyDocument = selectedDocument(storyDocumentId, "STORYBOOK");
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.documents()).thenReturn(List.of(ruleDocument, storyDocument));
        when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
        when(knowledgeSets.findBySessionId(sessionKey)).thenReturn(Optional.of(new SessionKnowledgeSet(sessionKey,
                List.of(new KnowledgeDocumentId(ruleDocumentId), new KnowledgeDocumentId(storyDocumentId)))));
        when(evidenceSearch.search(any())).thenAnswer(invocation -> {
            com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest request = invocation.getArgument(0);
            if (request.evidenceType() == RuntimeEvidenceType.RULEBOOK)
                throw new IllegalStateException("rule source search unavailable");
            return List.of(new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, new KnowledgeDocumentId(storyDocumentId),
                    3, "page 12", "The ability moves the user.", "story-cite"));
        });
        when(bindings.current(sessionId)).thenReturn(Optional.of(new ProviderBinding(sessionId,
                new GmProviderSelection(UUID.randomUUID(), "codex", "test-model", "medium"), 1)));
        when(gm.generate(any())).thenReturn(validDefinition(storyDocumentId));
        var service = new AdventureAbilityDefinitionPreparationService(definitions, packages, knowledgeSets,
                sessions, mock(RuntimeCharacterSheetReadPort.class), evidenceSearch,
                mock(RuntimeEvidenceLocationLookupPort.class), bindings, gm, JSON);

        service.prepare(new AdventureId(packageId), sessionKey, new OwnerPlayerId(ownerId), List.of("ability:blink"));

        verify(evidenceSearch, org.mockito.Mockito.atLeastOnce()).search(
                org.mockito.ArgumentMatchers.argThat(request -> request.evidenceType() == RuntimeEvidenceType.STORYBOOK));
        verify(definitions).prepare(packageId, "ability:blink", validDefinition(storyDocumentId));

        when(gm.generate(any())).thenThrow(new TransientInitialSituationGmFailureException(
                new java.io.IOException("temporary provider failure")))
                .thenThrow(new TransientInitialSituationGmFailureException(
                        new java.io.IOException("temporary provider failure")));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                AdventureAbilityDefinitionPreparationService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(DevelopmentDiagnostics.FLAG);
        System.setProperty(DevelopmentDiagnostics.FLAG, "true");
        try {
            assertThatThrownBy(() -> service.prepare(new AdventureId(packageId), sessionKey,
                    new OwnerPlayerId(ownerId), List.of("ability:blink")))
                    .isInstanceOf(TransientInitialSituationGmFailureException.class)
                    .isNotInstanceOf(AbilityDefinitionSourceInputRequiredException.class);
            String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("outcome=generation_retry", "outcome=generation_failed", "outcome=failed",
                    "sourceInputRequired=false", "recovery=resolve_failure_then_retry_operation");
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previous);
        }
        verify(definitions, times(1)).prepare(packageId, "ability:blink", validDefinition(storyDocumentId));

        org.mockito.Mockito.doThrow(new IllegalStateException("evidence search service unavailable"))
                .when(evidenceSearch).search(any());
        assertThatThrownBy(() -> service.prepare(new AdventureId(packageId), sessionKey,
                new OwnerPlayerId(ownerId), List.of("ability:blink")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(AbilityDefinitionSourceInputRequiredException.class);
        verify(definitions, times(1)).prepare(packageId, "ability:blink", validDefinition(storyDocumentId));
    }

    private static ScenarioBundleDocumentSelection selectedDocument(UUID documentId, String type) {
        return new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(documentId),
                "RULEBOOK".equals(type) ? ScenarioBundleDocumentRole.RULEBOOK : ScenarioBundleDocumentRole.MAIN_SCENARIO,
                mock(KnowledgeDocumentStatus.class), type + ".pdf", type, 3);
    }

    private static JsonNode validDefinition(UUID documentId) throws Exception {
        return JSON.readTree("""
                {"abilityId":"ability:blink","name":"Blink","actionTiming":"action","resourceCost":null,
                "visibility":null,"range":"self","targeting":"self","origin":"self","effect":"teleport",
                "pathBlocking":null,"effectSpread":null,"check":null,"duration":"instant","trigger":null,
                "exceptions":null,"actionKind":"SPELL","rangeForm":"SINGLE","targetMode":"SELF",
                "originMode":"ACTOR_TOKEN","effectShape":"NONE","pathBlockingRule":"NONE",
                "propagationRule":"DIRECT","verticalDirection":"UP",
                "sourceLocations":[{"documentId":"%s","extractionVersion":3,"locator":"page 12"}]}
                """.formatted(documentId));
    }
}
