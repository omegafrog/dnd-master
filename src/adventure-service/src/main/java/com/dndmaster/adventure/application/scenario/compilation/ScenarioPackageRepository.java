package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory;
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
    default void lockSpellDefinitionsForStart(UUID packageId, long expectedRevision) {}
}
