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
    }
}
