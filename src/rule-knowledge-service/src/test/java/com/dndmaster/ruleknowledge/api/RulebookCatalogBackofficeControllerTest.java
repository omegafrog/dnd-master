package com.dndmaster.ruleknowledge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.definition.GameSystemDefinitionRepository;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookProcessingResult;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RulebookCatalogBackofficeControllerTest {
    private static final String ADMIN = "local-catalog-admin";
    private static final UUID CATALOG_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void adminCanRetryFailedCatalogRulebookThroughTheSharedPipeline() {
        UUID catalogId = UUID.randomUUID();
        RulebookId rulebookId = RulebookId.generate();
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(rulebookId)).thenReturn(Optional.of(registration(rulebookId, ProcessingStatus.FAILED)));
        when(pipeline.retry(rulebookId)).thenReturn(new RulebookProcessingResult(rulebookId, ProcessingStatus.QUEUED, List.of()));
        RulebookCatalogBackofficeController controller = controller(pipeline, registrations,
                catalog(catalogId, rulebookId, CatalogRevisionStatus.FAILED));

        RulebookCatalogBackofficeController.CatalogRulebookRecoveryView response = controller.retryFailed(
                "Bearer " + ADMIN, catalogId);

        assertEquals("QUEUED", response.processingStatus());
        verify(pipeline).retry(rulebookId);
    }

    @Test
    void adminPageRetryPassesExplicitLayoutSelectionsToThePipeline() {
        UUID catalogId = UUID.randomUUID();
        RulebookId rulebookId = RulebookId.generate();
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(rulebookId)).thenReturn(Optional.of(registration(rulebookId, ProcessingStatus.NEEDS_REVIEW)));
        when(pipeline.retryPages(eq(rulebookId), eq("retry-1"), eq(List.of(2)), eq(Map.of(2, Map.of("column_count", 2)))))
                .thenReturn(new RulebookProcessingResult(rulebookId, ProcessingStatus.INDEXED, List.of()));
        RulebookCatalogBackofficeController controller = controller(pipeline, registrations,
                catalog(catalogId, rulebookId, CatalogRevisionStatus.QUEUED));

        controller.retryPages("Bearer " + ADMIN, catalogId,
                new RuleKnowledgeController.RetryPagesRequest("retry-1", List.of(2), Map.of(2, Map.of("column_count", 2))));

        verify(pipeline).retryPages(rulebookId, "retry-1", List.of(2), Map.of(2, Map.of("column_count", 2)));
    }

    @Test
    void nonAdminCannotUseCatalogRecovery() {
        UUID catalogId = UUID.randomUUID();
        RulebookId rulebookId = RulebookId.generate();
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        RulebookCatalogBackofficeController controller = controller(pipeline, registrations,
                catalog(catalogId, rulebookId, CatalogRevisionStatus.FAILED));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.retryFailed("Bearer not-an-admin", catalogId));

        assertEquals(403, exception.getStatusCode().value());
        verify(pipeline, never()).retry(rulebookId);
    }

    private static RulebookCatalogBackofficeController controller(
            RulebookPipelineApplicationService pipeline,
            RulebookRegistrationRepository registrations,
            CatalogRulebookRevision catalog) {
        CatalogRulebookRepository repository = mock(CatalogRulebookRepository.class);
        when(repository.findAll()).thenReturn(List.of(catalog));
        return new RulebookCatalogBackofficeController(repository, pipeline, registrations,
                mock(GameSystemDefinitionRepository.class), ADMIN);
    }

    private static CatalogRulebookRevision catalog(UUID catalogId, RulebookId rulebookId, CatalogRevisionStatus status) {
        Instant now = Instant.now();
        return new CatalogRulebookRevision(catalogId, RulebookEdition.DND_5E_2014, "D&D 5e (2014)",
                rulebookId.value(), 1, status, false, null, now, now);
    }

    private static StoredRulebookRegistration registration(RulebookId rulebookId, ProcessingStatus status) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(rulebookId, new OwnerPlayerId(CATALOG_OWNER), "catalog-operation", "hash",
                RulebookFormat.PDF, 1, "storage", status, null, null, List.of(), null, 1L, now, now,
                DocumentType.RULEBOOK, "rules.pdf");
    }
}
