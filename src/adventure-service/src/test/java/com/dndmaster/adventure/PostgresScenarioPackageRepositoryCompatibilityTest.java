package com.dndmaster.adventure.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostgresScenarioPackageRepositoryCompatibilityTest {
    @Test
    void missingOrNullProvenanceUsesEmptyLegacyValue() {
        var missing = PostgresScenarioPackageRepository.readBlueprint(
                "{\"revision\":1,\"status\":\"READY\",\"fields\":[],\"diagnostics\":[]}");
        var nulled = PostgresScenarioPackageRepository.readBlueprint(
                "{\"revision\":1,\"status\":\"READY\",\"fields\":[],\"diagnostics\":[],\"provenance\":null}");

        assertThat(missing.provenance()).isEqualTo(nulled.provenance());
        assertThat(missing.provenance().sourceTypes()).isEmpty();
        assertThat(missing.proposalDecisions()).isEmpty();
    }

    @Test
    void rehydrates_persisted_proposal_decisions_without_losing_state() {
        var blueprint = PostgresScenarioPackageRepository.readBlueprint(
                "{\"revision\":2,\"status\":\"NEEDS_REVIEW\",\"fields\":[],\"diagnostics\":[],"
                        + "\"provenance\":{\"gameSystemDefinitionVersion\":1,\"sourceRevision\":1,\"sourceTypes\":[\"STORYBOOK\"],\"edition\":\"DND_5E_2014\"},"
                        + "\"proposalDecisions\":[{\"proposalId\":\"proposal-1\",\"fieldKey\":\"alignment\",\"state\":\"EXCLUDED\"}]}" );

        assertThat(blueprint.proposalDecisions()).hasSize(1);
        assertThat(blueprint.proposalDecisions().get(0).state().name()).isEqualTo("EXCLUDED");
    }
}
