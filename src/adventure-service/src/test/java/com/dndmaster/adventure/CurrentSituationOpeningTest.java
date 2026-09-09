package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentSituationOpeningTest {
    @Test
    void opening_situation_initializes_current_context_from_the_prepared_stage() {
        UUID packageId = UUID.randomUUID();
        DetailedStage stage = new DetailedStage(packageId, 1, "stage-1", 1, "문을 찾아 탈출한다",
                List.of(new RevelationDefinition("truth", true, List.of())),
                new ThreatDefinition("누군가 뒤쫓고 있다", List.of()),
                new PressureDefinition("추격이 가까워진다", List.of()),
                new FunnelDefinition("문을 찾아 회랑을 통과한다", List.of("truth"), List.of()),
                List.of(new SituationDefinition("opening-situation", List.of(StageIntent.REVELATION),
                        List.of("truth"), List.of(), List.of(), List.of(), List.of(), true, List.of())),
                List.of(), List.of());

        CurrentSituation actual = CurrentSituation.fromOpeningStage(stage, "맥주 저장고 너머 회랑");

        assertEquals("맥주 저장고 너머 회랑", actual.location());
        assertEquals("문을 찾아 탈출한다", actual.problem());
        assertEquals("누군가 뒤쫓고 있다", actual.threat());
        assertEquals("문을 찾아 회랑을 통과한다", actual.goal());
    }
}
