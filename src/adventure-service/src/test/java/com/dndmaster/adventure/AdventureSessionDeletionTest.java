package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.session.AdventureSessionStartCoordinator;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStatus;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdventureSessionDeletionTest {
    @Test
    void draftAndStartingSessionsCanBeDeletedToReleaseTheirScenarioMaterial() {
        for (AdventureSession.Status status : new AdventureSession.Status[] { AdventureSession.Status.DRAFT, AdventureSession.Status.STARTING }) {
            AdventureSession session = AdventureSession.rehydrate(
                    SessionId.generate(),
                    new OwnerPlayerId(UUID.randomUUID()),
                    UUID.randomUUID(),
                    1,
                    1,
                    java.util.List.of(),
                    null,
                    status,
                    status == AdventureSession.Status.STARTING ? AdventureId.generate() : null,
                    status == AdventureSession.Status.STARTING ? UUID.randomUUID() : null,
                    0);

            session.delete();

            assertEquals(AdventureSession.Status.DELETED, session.status());
        }
    }

    @Test
    void deleting_a_session_retires_the_runtime_adventure_it_already_created() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        SessionId sessionId = SessionId.generate();
        UUID packageId = UUID.randomUUID();
        AdventureId adventureId = AdventureId.generate();
        UUID requestId = UUID.randomUUID();
        CharacterSheetId sheetId = new CharacterSheetId(UUID.randomUUID());
        AdventurePartyMember member = new AdventurePartyMember(sheetId, ControlMode.DIRECT, true, true, true, true, true, true);
        AdventureSession session = AdventureSession.rehydrate(sessionId, owner, packageId, 1, packageId, 1, 1,
                List.of(member), configuration(packageId), AdventureSession.Status.STARTING, adventureId, requestId, 2);
        Adventure adventure = Adventure.beginScenarioRuntime(adventureId, sessionId, owner, new ScenarioId(packageId),
                new RuleSetId(UUID.randomUUID()), packageId, 1, List.of(member), new AdventureContext("opening", null, null, null));
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(adventures.findById(adventureId)).thenReturn(Optional.of(adventure));
        AdventureSessionStartCoordinator coordinator = mock(AdventureSessionStartCoordinator.class);

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(
                sessions, mock(ScenarioPackageRepository.class), adventures, mock(RuntimeBindingApplicationService.class),
                coordinator);

        service.delete(sessionId, owner, session.version());

        assertEquals(AdventureSession.Status.DELETED, session.status());
        assertEquals(AdventureStatus.DELETED, adventure.status());
        verify(adventures).save(adventure);
        verify(sessions).save(session, 2);
    }

    private static com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration configuration(UUID packageId) {
        return new com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration(
                new ScenarioId(packageId), new RuleSetId(UUID.randomUUID()), List.of(), "engine", List.of(), "opening");
    }
}
