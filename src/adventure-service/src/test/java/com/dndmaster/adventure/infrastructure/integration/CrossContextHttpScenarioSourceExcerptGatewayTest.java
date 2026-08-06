package com.dndmaster.adventure.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrossContextHttpScenarioSourceExcerptGatewayTest {
    @Test
    void preservesPluralCheckResolutionBlockWhenAbbreviatingAChunk() {
        String excerpt = "The zombie attacks and deals damage. Roll initiative. ".repeat(20)
                + "Those who know anything about fighting zombies, have them make DC 10 Intelligence checks. "
                + "Those who succeed might recall that a zombie is vulnerable to radiant damage. "
                + "A trailing paragraph. ".repeat(80);

        String abbreviated = CrossContextHttpScenarioSourceExcerptGateway.abbreviate(excerpt);

        assertThat(abbreviated).contains("DC 10 Intelligence checks");
        assertThat(abbreviated).contains("Those who succeed might recall");
    }
}
