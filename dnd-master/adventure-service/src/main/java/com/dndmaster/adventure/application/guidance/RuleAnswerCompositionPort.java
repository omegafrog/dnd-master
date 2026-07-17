package com.dndmaster.adventure.application.guidance;

import java.util.List;

public interface RuleAnswerCompositionPort {
    GuidanceComposition compose(String situation, List<RuleEvidence> evidence);
}
