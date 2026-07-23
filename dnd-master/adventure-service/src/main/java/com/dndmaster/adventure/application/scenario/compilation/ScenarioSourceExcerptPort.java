package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.util.List;

/** Loads the immutable extraction spans selected by a bundle revision. */
public interface ScenarioSourceExcerptPort {
    List<ResolutionExtractionPort.SourceExcerpt> load(ScenarioSourceBundle bundle);
}
