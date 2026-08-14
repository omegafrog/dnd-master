package com.dndmaster.ruleknowledge.application.catalog;

import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import java.util.List;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;

public interface CatalogRulebookRepository {
    List<CatalogRulebookRevision> findPublished();
    List<CatalogRulebookRevision> findAll();
    void save(CatalogRulebookRevision revision);
    void publish(CatalogRulebookRevision revision);
}
