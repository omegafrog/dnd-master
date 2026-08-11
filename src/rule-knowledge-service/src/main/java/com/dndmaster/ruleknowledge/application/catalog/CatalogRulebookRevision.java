package com.dndmaster.ruleknowledge.application.catalog;

import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import java.time.Instant;
import java.util.UUID;

public record CatalogRulebookRevision(
        UUID id,
        RulebookEdition edition,
        String displayName,
        UUID rulebookId,
        long revisionNumber,
        CatalogRevisionStatus status,
        boolean published,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {}
