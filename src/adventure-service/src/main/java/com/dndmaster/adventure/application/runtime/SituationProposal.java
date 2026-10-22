package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** The GM's proposed next playable situation and the fact that supports it. */
public record SituationProposal(SituationUpdateProposal update, Basis basis, String reference, boolean required,
        String selectedSituationId, String title, String description, boolean mapNeeded,
        java.util.UUID mapDefinitionId) {
    public enum Basis { SCENARIO, RAG, FALLBACK }

    public SituationProposal {
        update = Objects.requireNonNull(update, "situation update must not be null");
        basis = Objects.requireNonNull(basis, "situation basis must not be null");
        reference = reference == null ? "" : reference.trim();
        selectedSituationId = selectedSituationId == null || selectedSituationId.isBlank() ? null : selectedSituationId.trim();
        title = optional(title);
        description = optional(description);
        if ((title == null) != (description == null)) throw new IllegalArgumentException("new situation title and description must be provided together");
        if (title != null && title.codePointCount(0, title.length()) > 120) throw new IllegalArgumentException("new situation title is too long");
        if (description != null && description.codePointCount(0, description.length()) > 2000) throw new IllegalArgumentException("new situation description is too long");
        if (selectedSituationId != null && title != null) throw new IllegalArgumentException("selected and new situations are mutually exclusive");
        if (title != null && mapNeeded != (mapDefinitionId != null)) throw new IllegalArgumentException("new situation map-needed choice must match map definition");
        if (basis != Basis.FALLBACK && reference.isBlank()) {
            throw new IllegalArgumentException("situation source reference must not be blank");
        }
        if (basis == Basis.FALLBACK && !required) {
            throw new IllegalArgumentException("fallback situation must be required to continue play");
        }
    }

    public SituationProposal(SituationUpdateProposal update, Basis basis, String reference, boolean required) {
        this(update, basis, reference, required, null, null, null, false, null);
    }

    public SituationProposal(SituationUpdateProposal update, Basis basis, String reference, boolean required,
            String selectedSituationId) {
        this(update, basis, reference, required, selectedSituationId, null, null, false, null);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
