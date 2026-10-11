package com.dndmaster.adventure.application.scenario.preparation;

import java.util.UUID;

public record PreparationSourceView(UUID documentId, String originalFilename, long extractionVersion) { }
