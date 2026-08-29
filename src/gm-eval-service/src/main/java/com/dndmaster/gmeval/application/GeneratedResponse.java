package com.dndmaster.gmeval.application;

public record GeneratedResponse(String response, String generatorMetadata) {
    public GeneratedResponse {
        if (response == null || response.isBlank()) throw new IllegalArgumentException("generated response required");
        generatorMetadata = generatorMetadata == null ? "" : generatorMetadata;
    }
}
