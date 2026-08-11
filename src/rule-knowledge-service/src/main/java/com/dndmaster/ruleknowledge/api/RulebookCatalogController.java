package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
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

    public RulebookCatalogController(CatalogRulebookRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @GetMapping
    List<CatalogRulebookView> list() {
        return repository.findAll().stream().map(CatalogRulebookView::from).toList();
    }

    public record CatalogRulebookView(
            String catalogRevisionId, String edition, String displayName, String rulebookId,
            long revisionNumber, String status) {
        static CatalogRulebookView from(CatalogRulebookRevision revision) {
            return new CatalogRulebookView(revision.id().toString(), revision.edition().name(),
                    revision.displayName(), revision.rulebookId() == null ? null : revision.rulebookId().toString(),
                    revision.revisionNumber(), revision.status().name());
        }
    }
}
