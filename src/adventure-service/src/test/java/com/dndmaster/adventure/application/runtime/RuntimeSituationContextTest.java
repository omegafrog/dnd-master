package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationProvenance;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeSituationContextTest {
    @Test
    void includes_candidate_names_and_descriptions_and_rejects_context_over_8192_bytes_with_diagnostics() {
        UUID turnId = UUID.randomUUID();
        DetailedStage stage = stageWithCandidates(5, "x".repeat(2000));
        StoryRuntimeState state = StoryRuntimeState.start(stage);
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(RuntimeTurnApplicationService.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> diagnostics = new ListAppender<>();
        diagnostics.start();
        logger.addAppender(diagnostics);
        String previous = System.getProperty(DevelopmentDiagnostics.FLAG);
        try {
            System.setProperty(DevelopmentDiagnostics.FLAG, "true");
            var failure = assertThrows(RuntimeGmInputBudget.InputTooLargeException.class,
                    () -> RuntimeTurnApplicationService.situationSelectionContext(turnId, state, stage, state.activeSituationId()));
            assertTrue(failure.getMessage().contains("maxBytes=8192"));
        } finally {
            if (previous == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previous);
            logger.detachAppender(diagnostics);
        }

        String log = diagnostics.list.stream().map(event -> event.getFormattedMessage())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(log.contains("dev_runtime_situation_context outcome=started"));
        assertTrue(log.contains("expectedCandidateContextBytesMax=8192 actualCandidateContextBytes="));
        assertTrue(log.contains("expectedCandidateCount=5 actualCandidateCount=5"));
        assertTrue(log.contains("failureKind=INPUT_TOO_LARGE"));
        assertTrue(log.contains("nextAction=abort_before_gm_request"));
        assertTrue(!log.contains("x".repeat(100)));
    }

    @Test
    void passes_small_candidate_context_with_specific_name_and_scene_description() {
        DetailedStage stage = stageWithCandidates(2, "The cooper watches the cellar door.");
        String context = RuntimeTurnApplicationService.situationSelectionContext(UUID.randomUUID(),
                StoryRuntimeState.start(stage), stage, "opening");

        assertTrue(context.contains("The cooper watches the cellar door"));
        assertTrue(context.contains("A goblin scout is on patrol"));
        assertTrue(context.contains("A goblin scout circles the brewery yard"));
    }

    private static DetailedStage stageWithCandidates(int candidateCount, String description) {
        var situations = new java.util.ArrayList<SituationDefinition>();
        situations.add(situation("opening", "The keeper opens the brewery", "The keeper waits at the cellar entrance.", true));
        for (int index = 0; index < candidateCount; index++) {
            situations.add(situation(index == 0 ? "candidate-" + index : "candidate-" + index,
                    index == 0 ? "A goblin scout is on patrol" : "A worker checks the cellar",
                    index == 0 ? "A goblin scout circles the brewery yard, watching the cellar door." : description, false));
        }
        return new DetailedStage(UUID.randomUUID(), 1, "stage", 1, "Find the missing bell",
                List.of(new RevelationDefinition("truth", true, List.of())),
                new ThreatDefinition("Something moves in the cellar", List.of()),
                new PressureDefinition("Night is falling", List.of()),
                new FunnelDefinition("Find the bell", List.of("truth"), List.of()), situations, List.of(), List.of());
    }

    private static SituationDefinition situation(String id, String title, String description, boolean opening) {
        return new SituationDefinition(id, List.of(StageIntent.REVELATION), List.of("truth"), List.of(), List.of(),
                List.of(), List.of(), opening, opening, List.of(), SituationProvenance.GM_SUPPLEMENTED,
                null, List.of(), false, title, description);
    }
}
