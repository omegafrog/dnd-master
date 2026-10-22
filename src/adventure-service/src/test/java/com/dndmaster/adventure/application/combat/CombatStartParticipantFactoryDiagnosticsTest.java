package com.dndmaster.adventure.application.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.runtime.ExplorationCycle;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class CombatStartParticipantFactoryDiagnosticsTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(CombatStartParticipantFactory.class);
    private ListAppender<ILoggingEvent> diagnostics;
    private String previousDiagnosticsFlag;

    @BeforeEach
    void captureDiagnostics() {
        previousDiagnosticsFlag = System.getProperty(DevelopmentDiagnostics.FLAG);
        diagnostics = new ListAppender<>();
        diagnostics.start();
        logger.addAppender(diagnostics);
    }

    @AfterEach
    void restoreDiagnostics() {
        logger.detachAppender(diagnostics);
        if (previousDiagnosticsFlag == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
        else System.setProperty(DevelopmentDiagnostics.FLAG, previousDiagnosticsFlag);
    }

    @Test
    void enabled_logs_report_counts_result_and_unmatched_crosswalk() {
        System.setProperty(DevelopmentDiagnostics.FLAG, "true");
        UUID memberId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID unknownParticipantId = UUID.randomUUID();
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10));
        var result = CombatStartParticipantFactory.applyExplorationAwareness(
                List.of(new CombatParticipant(memberId, "Hero", CombatParticipant.Controller.PLAYER, 10, null)),
                cycle, Map.of(UUID.randomUUID(), unknownParticipantId));

        String messages = diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(messages.contains("dev_exploration_awareness_transfer_begin participantCount=1 cyclePresent=true crosswalkCount=1"));
        assertTrue(messages.contains("dev_exploration_awareness_transfer_success participantCount=1 hostileCount=0 crosswalkCount=0 unmatchedCrosswalkCount=1"));
        assertTrue(messages.contains("awareRelationshipCount=0 awareParticipantCount=0 surprisedParticipantCount=1"));
        assertTrue(messages.contains("result=applied"));
        assertTrue(result.getFirst().surprised());
    }

    @Test
    void disabled_diagnostics_emit_no_awareness_transfer_logs() {
        System.clearProperty(DevelopmentDiagnostics.FLAG);
        UUID groupId = UUID.randomUUID();
        CombatStartParticipantFactory.applyExplorationAwareness(
                List.of(new CombatParticipant(UUID.randomUUID(), "Hero", CombatParticipant.Controller.PLAYER, 10, null)),
                ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10)), Map.of());

        assertFalse(diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("dev_exploration_awareness_transfer_")));
    }
}
