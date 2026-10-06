package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Full identity for reusing a verified enemy sheet inside one adventure's pinned material scope. */
public record EnemyCharacterSheetIdentity(UUID adventureId, UUID scenarioBundleId, long scenarioBundleRevision,
        UUID scenarioPackageId, List<UUID> rulebookIds, String enemyKind) {
    public String identityKey() {
        return adventureId + "|" + scenarioBundleId + "|" + scenarioBundleRevision + "|" + scenarioPackageId
                + "|" + rulebookIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","))
                + "|" + enemyKind;
    }

    public EnemyCharacterSheetIdentity {
        Objects.requireNonNull(adventureId, "adventure id is required");
        Objects.requireNonNull(scenarioBundleId, "scenario bundle id is required");
        Objects.requireNonNull(scenarioPackageId, "scenario package id is required");
        if (scenarioBundleRevision < 1) throw new IllegalArgumentException("scenario bundle revision must be positive");
        rulebookIds = rulebookIds == null ? List.of() : rulebookIds.stream().filter(Objects::nonNull).distinct()
                .sorted().toList();
        if (rulebookIds.isEmpty()) throw new IllegalArgumentException("at least one pinned rulebook is required");
        if (enemyKind == null || enemyKind.isBlank()) throw new IllegalArgumentException("enemy kind is required");
        enemyKind = enemyKind.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
