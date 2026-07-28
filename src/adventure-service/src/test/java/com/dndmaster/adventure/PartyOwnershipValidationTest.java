package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.session.*;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartyOwnershipValidationTest {
    @Test
    void rejects_sheet_not_owned_by_session_before_persisting_party() {
        var repository = mock(AdventureSessionRepository.class);
        var ownership = mock(CharacterSheetOwnershipPort.class);
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 2);
        var owner = session.ownerPlayerId();
        var sheet = new CharacterSheetId(UUID.randomUUID());
        when(repository.findById(session.id())).thenReturn(Optional.of(session));
        doThrow(new IllegalStateException("character sheet does not belong to session"))
                .when(ownership).verify(session.id(), owner, sheet);
        var service = new AdventureSessionApplicationService(repository, mock(ScenarioPackageRepository.class), mock(AdventureRepository.class), mock(RuntimeBindingApplicationService.class), mock(AdventureSessionStartCoordinator.class), ownership);

        assertThrows(IllegalStateException.class, () -> service.addMember(session.id(), owner, 0,
                new AdventurePartyMember(sheet, ControlMode.DIRECT, true, true, true, true, true, true)));
        verify(repository, never()).save(any(), anyLong());
    }
}
