package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ScenarioCompilationControllerTest {
    @Test
    void rejectsEmptyManualCompilationBecauseItSkipsAutomaticExtraction() {
        ScenarioCompilationApplicationService service = mock(ScenarioCompilationApplicationService.class);
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        UUID ownerId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        ScenarioCompilationController controller = new ScenarioCompilationController(service, playerResolver);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.compile(UUID.randomUUID(),
                        new ScenarioCompilationController.CompilationRequest(ownerId, List.of(), List.of())));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("use compilation-jobs for automatic candidate extraction", exception.getReason());
    }
}
