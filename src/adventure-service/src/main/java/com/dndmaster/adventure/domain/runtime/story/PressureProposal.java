package com.dndmaster.adventure.domain.runtime.story;

public record PressureProposal(String pressureId, PressureOperation operation, int expectedProgression, String replacementMaterial) {
    public PressureProposal {
        if (pressureId == null || pressureId.isBlank()) throw new IllegalArgumentException("pressure id is required");
        if (operation == null) throw new IllegalArgumentException("pressure operation is required");
        if (expectedProgression < 0) throw new IllegalArgumentException("expected pressure progression must not be negative");
        if (operation == PressureOperation.REPLACE && (replacementMaterial == null || replacementMaterial.isBlank())) {
            throw new IllegalArgumentException("replacement pressure material is required");
        }
        pressureId = pressureId.trim();
        replacementMaterial = replacementMaterial == null || replacementMaterial.isBlank() ? null : replacementMaterial.trim();
    }
}
