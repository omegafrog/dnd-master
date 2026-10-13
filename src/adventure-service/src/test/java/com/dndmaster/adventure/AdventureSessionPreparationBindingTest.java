package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.session.AdventureSessionStartCoordinator;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.BlueprintProvenance;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import com.dndmaster.adventure.domain.scenario.CompilationOutcome;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionReviewPreparation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AdventureSessionPreparationBindingTest {
    private static com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition actionDefinition(boolean required, boolean active) {
        var source = com.dndmaster.adventure.domain.scenario.BasicRulebookSpellInventory.load(
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()), 1).getFirst();
        return new com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition(source.id(), source.name(),
                source.sourceDocumentId(), source.sourceLocator(), source.sourceVersion(), source.extractionVersion(),
                source.level(), source.castingTime(), source.rangeArea(), source.components(), source.duration(),
                source.school(), source.attackSave(), source.damageEffect(), source.ownerPlanNumbers(), source.ownerEvidence(),
                false, com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.ReviewStatus.PENDING,
                required, source.actionKind(), source.rangeProfile(), source.targetingProfile(), active);
    }

    @Test
    void session_gate_requires_approval_only_for_active_required_definitions() {
        for (var scenario : List.of(new boolean[] { false, true, true },
                new boolean[] { true, false, true }, new boolean[] { true, true, false })) {
            boolean required = scenario[0];
            boolean active = scenario[1];
            boolean shouldCreate = scenario[2];
            UUID packageId = UUID.randomUUID();
            UUID preparationId = UUID.randomUUID();
            OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
            var pending = actionDefinition(required, active);
            var blueprint = new CharacterCreationBlueprint(1, CharacterCreationBlueprintStatus.PUBLISHED,
                    List.of(new CharacterCreationBlueprint.Field("class", List.of(), true, "RULEBOOK", List.of(), "EXTRACTED", List.of())),
                    List.of(), new BlueprintProvenance(1, 1, List.of("RULEBOOK"), "OTHER_SYSTEM"));
            ScenarioPackage scenarioPackage = ScenarioPackage.rehydrate(packageId, ScenarioBundleId.generate(), 1,
                    "selective-action-review", List.of(), List.of(),
                    new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of(), CompilationOutcome.COMPLETE),
                    CharacterLimit.defaultLimit(), blueprint).withSpellDefinitions(List.of(pending));
            var review = new SpellDefinitionReviewPreparation(preparationId, packageId, owner.value(), 0, false, List.of(pending));
            ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
            when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
            when(packages.findSpellDefinitionPreparation(preparationId)).thenReturn(Optional.of(review));
            AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
            AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions, packages,
                    mock(AdventureRepository.class), mock(RuntimeBindingApplicationService.class),
                    mock(AdventureSessionStartCoordinator.class));

            if (!shouldCreate) {
                assertThrows(IllegalStateException.class, () -> service.create(owner, packageId, packageId, 1,
                        null, null, preparationId));
                verify(sessions, never()).save(any(), eq(0L));
                verify(packages, never()).bindSpellDefinitionPreparation(eq(preparationId), eq(packageId), eq(owner.value()), any(), eq(0L));
            } else {
                service.create(owner, packageId, packageId, 1, null, null, preparationId);
                verify(sessions).save(any(), eq(0L));
                verify(packages).bindSpellDefinitionPreparation(eq(preparationId), eq(packageId), eq(owner.value()), any(), eq(0L));
            }
        }
    }

    @Test
    void logs_binding_failure_context_and_preserves_exception() {
        UUID packageId = UUID.randomUUID();
        UUID preparationId = UUID.randomUUID();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        var blueprint = new CharacterCreationBlueprint(1, CharacterCreationBlueprintStatus.PUBLISHED,
                List.of(new CharacterCreationBlueprint.Field("class", List.of(), true, "RULEBOOK", List.of(), "EXTRACTED", List.of())),
                List.of(), new BlueprintProvenance(1, 1, List.of("RULEBOOK"), "OTHER_SYSTEM"));
        ScenarioPackage scenarioPackage = ScenarioPackage.rehydrate(packageId, ScenarioBundleId.generate(), 1,
                "preparation-bind-failure", List.of(), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of(), CompilationOutcome.COMPLETE),
                CharacterLimit.defaultLimit(), blueprint);
        var review = new SpellDefinitionReviewPreparation(preparationId, packageId, owner.value(), 4, false, List.of());
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
        when(packages.findSpellDefinitionPreparation(preparationId)).thenReturn(Optional.of(review));
        IllegalStateException bindingFailure = new IllegalStateException("simulated binding failure");
        doThrow(bindingFailure).when(packages).bindSpellDefinitionPreparation(eq(preparationId), eq(packageId),
                eq(owner.value()), any(), eq(4L));
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions, packages,
                mock(AdventureRepository.class), mock(RuntimeBindingApplicationService.class),
                mock(AdventureSessionStartCoordinator.class));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AdventureSessionApplicationService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            assertSame(bindingFailure, assertThrows(IllegalStateException.class, () ->
                    service.create(owner, packageId, packageId, 1, null, null, preparationId)));
            org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream().map(event -> event.getFormattedMessage()).anyMatch(message ->
                    message.contains("operation=adventure_session_create outcome=failed")
                            && message.contains("expectedPreparationId=" + preparationId)
                            && message.contains("actualPreparationId=unconfirmed")
                            && message.contains("expectedPackageId=" + packageId)
                            && message.contains("actualPackageId=unconfirmed")
                            && message.contains("expectedOwnerId=" + owner.value())
                            && message.matches(".*expectedSessionId=[0-9a-f-]{36} actualSessionId=unconfirmed.*")
                            && message.contains("expectedRevision=4 actualRevision=unknown")
                            && message.contains("failureType=IllegalStateException")
                            && message.contains("reason=preparation_bind_failed")
                            && message.contains("recovery=retry_after_preparation_reload")));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
        verify(sessions).save(any(), eq(0L));
    }

    @Test
    void failed_session_save_does_not_bind_preparation_and_retry_can_succeed() {
        UUID packageId = UUID.randomUUID();
        UUID preparationId = UUID.randomUUID();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        var blueprint = new CharacterCreationBlueprint(1, CharacterCreationBlueprintStatus.PUBLISHED,
                List.of(new CharacterCreationBlueprint.Field("class", List.of(), true, "RULEBOOK", List.of(), "EXTRACTED", List.of())),
                List.of(), new BlueprintProvenance(1, 1, List.of("RULEBOOK"), "OTHER_SYSTEM"));
        ScenarioPackage scenarioPackage = ScenarioPackage.rehydrate(packageId, ScenarioBundleId.generate(), 1,
                "preparation-bind-retry", List.of(), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of(), CompilationOutcome.COMPLETE),
                CharacterLimit.defaultLimit(), blueprint);
        var review = new SpellDefinitionReviewPreparation(preparationId, packageId, owner.value(), 0, false, List.of());
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
        when(packages.findSpellDefinitionPreparation(preparationId)).thenReturn(Optional.of(review));
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AtomicBoolean failFirstSave = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failFirstSave.getAndSet(false)) throw new IllegalStateException("simulated session persistence failure");
            return null;
        }).when(sessions).save(any(), eq(0L));
        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions, packages,
                mock(AdventureRepository.class), mock(RuntimeBindingApplicationService.class),
                mock(AdventureSessionStartCoordinator.class));

        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AdventureSessionApplicationService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            assertThrows(IllegalStateException.class, () -> service.create(owner, packageId, packageId, 1,
                    null, null, preparationId));
            verify(packages, never()).bindSpellDefinitionPreparation(eq(preparationId), eq(packageId), eq(owner.value()), any(), eq(0L));
            org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream().map(event -> event.getFormattedMessage()).anyMatch(message ->
                    message.contains("operation=adventure_session_create outcome=failed")
                            && message.contains("packageId=" + packageId)
                            && message.contains("preparationId=" + preparationId)
                            && message.contains("expectedVersion=0 actualVersion=0")
                            && message.contains("reason=session_persist_failed")));
            service.create(owner, packageId, packageId, 1, null, null, preparationId);
            org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream().map(event -> event.getFormattedMessage()).anyMatch(message ->
                    message.contains("operation=adventure_session_create outcome=completed")
                            && message.contains("packageId=" + packageId)
                            && message.contains("preparationId=" + preparationId)
                            && message.contains("reason=session_persisted_and_preparation_bound")));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
        verify(sessions, times(2)).save(any(), eq(0L));
        verify(packages).bindSpellDefinitionPreparation(eq(preparationId), eq(packageId), eq(owner.value()), any(), eq(0L));
    }
}
