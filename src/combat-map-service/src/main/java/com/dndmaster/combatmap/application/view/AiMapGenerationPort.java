package com.dndmaster.combatmap.application.view;

public interface AiMapGenerationPort {
    PreparedMapData generate(String scenarioDescription);

    default PreparedMapData generate(MapGenerationRequest request) {
        return generate(request.selectedScenario());
    }

    /** Runtime map-entry placement is a separate agent responsibility from map preparation. */
    default PreparedMapData proposeEntryPlacement(MapGenerationRequest request) {
        return proposePlacement(request);
    }

    /** 이전 호출부와의 호환성을 위한 이름이다. 새 코드는 proposeEntryPlacement를 사용한다. */
    @Deprecated
    default PreparedMapData proposePlacement(MapGenerationRequest request) {
        return generate(request);
    }
}
