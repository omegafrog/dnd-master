package com.dndmaster.combatmap.application.view;

public interface AiMapGenerationPort {
    PreparedMapData generate(String scenarioDescription);

    default PreparedMapData generate(MapGenerationRequest request) {
        return generate(request.selectedScenario());
    }
}
