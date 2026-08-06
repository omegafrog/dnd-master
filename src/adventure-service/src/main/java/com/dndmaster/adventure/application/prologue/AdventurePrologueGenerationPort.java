package com.dndmaster.adventure.application.prologue;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.List;

public interface AdventurePrologueGenerationPort {
    String generate(Request request);

    record Request(AdventureStoryPlanStage stage, List<CharacterSnapshot> party, List<String> evidence) {
        public Request { party = List.copyOf(party); evidence = List.copyOf(evidence); }
    }

    record CharacterSnapshot(String name, int level) {}
}
