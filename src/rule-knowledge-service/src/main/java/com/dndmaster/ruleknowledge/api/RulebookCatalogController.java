package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read model. Writes stay in the ADMIN backoffice surface. */
@RestController
@RequestMapping("/api/v1/rulebook-catalog")
public final class RulebookCatalogController {
    private final CatalogRulebookRepository repository;
    private final RulebookRegistrationRepository registrations;

    public RulebookCatalogController(CatalogRulebookRepository repository, RulebookRegistrationRepository registrations) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.registrations = Objects.requireNonNull(registrations, "registrations must not be null");
    }

    @GetMapping
    List<CatalogRulebookView> list() {
        return repository.findAll().stream().map(revision -> CatalogRulebookView.from(revision, registrations)).toList();
    }

    public record CatalogRulebookView(
            String catalogRevisionId, String edition, String displayName, String rulebookId,
            long revisionNumber, String status, String processingStatus, long extractionVersion) {
        static CatalogRulebookView from(CatalogRulebookRevision revision, RulebookRegistrationRepository registrations) {
            var registration = revision.rulebookId() == null ? java.util.Optional.<com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration>empty()
                    : registrations.findById(new RulebookId(revision.rulebookId()));
            long extractionVersion = registration.map(item -> item.version()).orElse(0L);
            return new CatalogRulebookView(revision.id().toString(), revision.edition().name(),
                    revision.displayName(), revision.rulebookId() == null ? null : revision.rulebookId().toString(),
                    revision.revisionNumber(), revision.status().name(), registration.map(item -> item.processingStatus().name()).orElse(null), extractionVersion);
        }
    }
}
