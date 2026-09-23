package com.dndmaster.ruleknowledge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RulebookCatalogControllerTest {
    @Test
    void listExposesActualRulebookProcessingStatusAlongsideCatalogStatus() {
        RulebookId rulebookId = RulebookId.generate();
        UUIDs ids = new UUIDs();
        Instant now = Instant.now();
        CatalogRulebookRevision revision = new CatalogRulebookRevision(ids.catalogRevisionId, RulebookEdition.DND_5E_2014,
                "D&D 5e (2014)", rulebookId.value(), 1, CatalogRevisionStatus.QUEUED, false, null, now, now);
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        StoredRulebookRegistration registration = mock(StoredRulebookRegistration.class);
        when(catalog.findAll()).thenReturn(List.of(revision));
        when(registrations.findById(rulebookId)).thenReturn(Optional.of(registration));
        when(registration.version()).thenReturn(4L);
        when(registration.processingStatus()).thenReturn(ProcessingStatus.INDEXED);

        RulebookCatalogController.CatalogRulebookView view = new RulebookCatalogController(catalog, registrations).list().getFirst();

        assertEquals("QUEUED", view.status());
        assertEquals("INDEXED", view.processingStatus());
        assertEquals(4L, view.extractionVersion());
    }

    private static final class UUIDs {
        private final java.util.UUID catalogRevisionId = java.util.UUID.randomUUID();
    }
}
