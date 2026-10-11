package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionReviewPreparation;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationDiagnostic;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ScenarioPackageRepository {
    Optional<ScenarioPackage> findByInputFingerprint(String inputFingerprint);
    Optional<ScenarioPackage> findById(UUID packageId);
    default List<ScenarioPackage> findByBundleId(UUID bundleId) { return List.of(); }
    void save(ScenarioPackage scenarioPackage);

    /** Persists Package, child ScenarioModel, Job completion, and publication event as one unit when supported. */
    default boolean publishAtomically(ScenarioPackage scenarioPackage, ScenarioCompilation compilation,
            List<ScenarioCompilationDiagnostic> diagnostics) {
        save(scenarioPackage);
        return false;
    }

    default void saveBlueprint(UUID packageId, CharacterCreationBlueprint blueprint) {
        throw new UnsupportedOperationException("blueprint updates are not supported by this repository");
    }

    default void saveSpellDefinitions(UUID packageId, List<StructuredSpellDefinition> definitions) {}
    default long spellDefinitionRevision(UUID packageId) { return 0; }
    default List<SpellDefinitionEditHistory> spellDefinitionHistory(UUID packageId) { return List.of(); }
    default long updateSpellDefinitions(UUID packageId, long expectedRevision,
            List<StructuredSpellDefinition> definitions, SpellDefinitionEditHistory history) {
        saveSpellDefinitions(packageId, definitions);
        return expectedRevision + 1;
    }
    default SpellDefinitionReviewPreparation createSpellDefinitionPreparation(UUID packageId, UUID ownerId,
            String requestKey, List<StructuredSpellDefinition> definitions) {
        throw new UnsupportedOperationException("spell review preparations are not supported by this repository");
    }
    default Optional<SpellDefinitionReviewPreparation> findSpellDefinitionPreparation(UUID preparationId) { return Optional.empty(); }
    default List<SpellDefinitionEditHistory> spellDefinitionPreparationHistory(UUID preparationId) { return List.of(); }
    default long updateSpellDefinitionPreparation(UUID preparationId, long expectedRevision,
            List<StructuredSpellDefinition> definitions, SpellDefinitionEditHistory history) {
        throw new UnsupportedOperationException("spell review preparations are not supported by this repository");
    }
    default void bindSpellDefinitionPreparation(UUID preparationId, UUID packageId, UUID ownerId,
            UUID sessionId, long expectedRevision) {
        throw new UnsupportedOperationException("spell review preparations are not supported by this repository");
    }
    default void lockSpellDefinitionPreparationForStart(UUID preparationId, UUID packageId, UUID ownerId,
            UUID sessionId, long expectedRevision) {
        throw new UnsupportedOperationException("spell review preparations are not supported by this repository");
    }
    default Optional<SpellDefinitionReviewPreparation> findSpellDefinitionPreparationBySession(UUID sessionId) { return Optional.empty(); }
}
