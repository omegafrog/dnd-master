package com.dndmaster.ruleknowledge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulebookCatalogControllerTest {
    @Test
    void public_catalog_contains_only_published_ready_indexed_rulebooks() {
        UUID included = UUID.randomUUID();
        UUID unpublished = UUID.randomUUID();
        UUID notReady = UUID.randomUUID();
        UUID notIndexed = UUID.randomUUID();
        UUID storybook = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(
                revision(included, CatalogRevisionStatus.READY, true),
                revision(unpublished, CatalogRevisionStatus.READY, false),
                revision(notReady, CatalogRevisionStatus.QUEUED, true),
                revision(notIndexed, CatalogRevisionStatus.READY, true),
                revision(storybook, CatalogRevisionStatus.READY, true)));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenAnswer(invocation -> {
            UUID id = ((RulebookId) invocation.getArgument(0)).value();
            if (id.equals(notIndexed)) return java.util.Optional.of(registration(id, ProcessingStatus.PROCESSING, DocumentType.RULEBOOK));
            if (id.equals(storybook)) return java.util.Optional.of(registration(id, ProcessingStatus.INDEXED, DocumentType.STORYBOOK));
            return java.util.Optional.of(registration(id, ProcessingStatus.INDEXED, DocumentType.RULEBOOK));
        });

        var result = new RulebookCatalogController(catalog, registrations).list();

        assertThat(result).extracting(RulebookCatalogController.CatalogRulebookView::rulebookId)
                .containsExactly(included.toString());
        assertThat(result.getFirst().published()).isTrue();
    }

    private static CatalogRulebookRevision revision(UUID rulebookId, CatalogRevisionStatus status, boolean published) {
        Instant now = Instant.now();
        return new CatalogRulebookRevision(UUID.randomUUID(), RulebookEdition.DND_5E_2014,
                "D&D 5e", rulebookId, 1, status, published, null, now, now);
    }

    private static StoredRulebookRegistration registration(UUID id, ProcessingStatus status, DocumentType type) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(new RulebookId(id), new OwnerPlayerId(UUID.randomUUID()),
                "op-" + id, "hash-" + id, RulebookFormat.PDF, 1, "storage-" + id, status,
                ExtractionStatus.SUCCESS, "content", List.of(), null, 1, now, now, type, "rulebook.pdf");
    }
}
