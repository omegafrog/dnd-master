package com.dndmaster.adventure.domain.adventure;

import java.util.List;

public enum StoryPlanGenerationMode {
    SOURCE_BOUND,
    GENERATIVE;

    public static StoryPlanGenerationMode fromDocumentTypes(List<String> documentTypes) {
        return documentTypes != null && documentTypes.stream().anyMatch(type ->
                type != null && "STORYBOOK".equalsIgnoreCase(type.trim())) ? SOURCE_BOUND : GENERATIVE;
    }
}
