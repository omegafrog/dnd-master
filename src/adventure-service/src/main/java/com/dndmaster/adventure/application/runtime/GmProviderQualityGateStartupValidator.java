package com.dndmaster.adventure.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Fails application startup when the provider corpus is not deployable. */
public final class GmProviderQualityGateStartupValidator {
    public GmProviderQualityGateStartupValidator(GmProviderQualityGateService gate, ObjectMapper mapper,
                                                  boolean enforce) {
        Objects.requireNonNull(gate); Objects.requireNonNull(mapper);
        if (!enforce) return;
        try (var input = getClass().getResourceAsStream("/gm-quality-gate-corpus.json")) {
            if (input == null) throw new IllegalStateException("GM quality gate corpus missing");
            gate.requireDeployable(Arrays.asList(mapper.readValue(input, GmQualityCaseResult[].class)));
        } catch (IOException failure) {
            throw new IllegalStateException("GM quality gate corpus could not be loaded", failure);
        }
    }
}
