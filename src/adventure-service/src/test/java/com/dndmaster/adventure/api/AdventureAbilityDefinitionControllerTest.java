package com.dndmaster.adventure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.dndmaster.adventure.application.ability.AdventureAbilityDefinitionPreparationService;
import com.dndmaster.adventure.application.ability.AdventureAbilityDefinitionRepository;
import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadPort;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AdventureAbilityDefinitionControllerTest {
    @Test
    void lookup_diagnostics_identify_outcome_scope_and_failure_without_sheet_contents() {
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        var adventure = mock(Adventure.class);
        when(adventure.id()).thenReturn(new AdventureId(adventureId));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        var adventures = mock(AdventureRepository.class);
        when(adventures.findBySessionId(new SessionId(sessionId))).thenReturn(Optional.of(adventure));
        var sheets = mock(RuntimeCharacterSheetReadPort.class);
        when(sheets.read(sheetId)).thenReturn("{\"sessionId\":\"" + sessionId
                + "\",\"ownerPlayerId\":\"" + ownerId + "\",\"characterState\":\"{\\\"learnedAbilityIds\\\":[]}\"}");
        var controller = new AdventureAbilityDefinitionController(adventures, sheets,
                mock(AdventureAbilityDefinitionPreparationService.class), mock(AdventureAbilityDefinitionRepository.class),
                new ObjectMapper(), "internal-token");
        Logger logger = (Logger) LoggerFactory.getLogger(AdventureAbilityDefinitionController.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(DevelopmentDiagnostics.FLAG);
        System.setProperty(DevelopmentDiagnostics.FLAG, "true");
        try {
            var response = controller.lookup("internal-token",
                    new AdventureAbilityDefinitionController.LookupRequest(adventureId, sessionId, ownerId, sheetId, "ability:blink"));
            assertThat(response.status()).isEqualTo("NOT_LEARNED");

            var failure = org.assertj.core.api.Assertions.catchThrowable(() -> controller.lookup("internal-token",
                    new AdventureAbilityDefinitionController.LookupRequest(UUID.randomUUID(), sessionId, ownerId, sheetId,
                            "ability:blink")));
            assertThat(failure).isInstanceOf(ApiRequestGuard.ApiContractException.class);

            String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("dev_ability_lookup outcome=started", "outcome=completed",
                    "outcome=failed", "abilityId=ability:blink", "expected=learned_ability",
                    "actual=NOT_LEARNED", "actual=ApiContractException", "failureKind=ApiContractException");
            assertThat(logs).doesNotContain("learnedAbilityIds");
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previous);
        }
    }
}
