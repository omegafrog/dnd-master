package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.campaign.CampaignPlanPreparationException;
import com.dndmaster.adventure.application.campaign.CampaignPlanRepository;
import com.dndmaster.adventure.application.campaign.CampaignPlanningApplicationService;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.CampaignPlan;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CampaignPlanningApplicationServiceTest {
    @Test
    void generates_source_linked_stages_and_reuses_matching_persisted_plan() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = draftSession(owner, true);
        KnowledgeDocumentId storybookId = new KnowledgeDocumentId(UUID.randomUUID());
        InMemoryPlanRepository plans = new InMemoryPlanRepository();
        AtomicInteger searches = new AtomicInteger();

        CampaignPlanningApplicationService service = service(
                session,
                new SessionKnowledgeSet(session.id(), List.of(storybookId)),
                List.of(document(storybookId, "STORYBOOK", KnowledgeDocumentStatus.INDEXED, 7L)),
                request -> {
                    searches.incrementAndGet();
                    return List.of(new CharacterContextSearchPort.Evidence(
                            storybookId,
                            "STORYBOOK",
                            7L,
                            "page:12:span:3",
                            "The bell tower keeper hides the silver key beneath the broken stair.",
                            0.91));
                },
                plans);

        CampaignPlan first = service.prepare(session.id(), owner);
        CampaignPlan resumed = service.prepare(session.id(), owner);

        assertEquals(first, resumed);
        assertEquals(1L, first.revision());
        assertEquals(1, searches.get());
        assertEquals(7L, first.documents().getFirst().extractionVersion());
        assertEquals(1, first.stages().size());
        assertTrue(first.evidence().stream()
                .anyMatch(evidence -> first.stages().getFirst().evidenceIds().contains(evidence.evidenceId())));
        assertTrue(first.stages().getFirst().cluesAndNpcs().getFirst().contains("silver key"));
    }

    @Test
    void returns_specific_preparation_error_when_storybook_is_not_selected() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = draftSession(owner, true);
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());

        CampaignPlanningApplicationService service = service(
                session,
                new SessionKnowledgeSet(session.id(), List.of(rulebookId)),
                List.of(document(rulebookId, "RULEBOOK", KnowledgeDocumentStatus.INDEXED, 2L)),
                request -> List.of(),
                new InMemoryPlanRepository());

        CampaignPlanPreparationException exception = assertThrows(
                CampaignPlanPreparationException.class,
                () -> service.prepare(session.id(), owner));

        assertEquals(CampaignPlanPreparationException.Code.STORYBOOK_SELECTION_REQUIRED, exception.code());
    }

    @Test
    void returns_specific_preparation_error_when_active_character_sheet_is_missing() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = draftSession(owner, false);
        KnowledgeDocumentId storybookId = new KnowledgeDocumentId(UUID.randomUUID());

        CampaignPlanningApplicationService service = service(
                session,
                new SessionKnowledgeSet(session.id(), List.of(storybookId)),
                List.of(document(storybookId, "STORYBOOK", KnowledgeDocumentStatus.INDEXED, 1L)),
                request -> List.of(),
                new InMemoryPlanRepository());

        CampaignPlanPreparationException exception = assertThrows(
                CampaignPlanPreparationException.class,
                () -> service.prepare(session.id(), owner));

        assertEquals(CampaignPlanPreparationException.Code.ACTIVE_CHARACTER_SHEETS_REQUIRED, exception.code());
    }

    @Test
    void rejects_storybook_without_valid_evidence() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = draftSession(owner, true);
        KnowledgeDocumentId storybookId = new KnowledgeDocumentId(UUID.randomUUID());

        CampaignPlanningApplicationService service = service(
                session,
                new SessionKnowledgeSet(session.id(), List.of(storybookId)),
                List.of(document(storybookId, "STORYBOOK", KnowledgeDocumentStatus.INDEXED, 4L)),
                request -> List.of(),
                new InMemoryPlanRepository());

        CampaignPlanPreparationException exception = assertThrows(
                CampaignPlanPreparationException.class,
                () -> service.prepare(session.id(), owner));

        assertEquals(CampaignPlanPreparationException.Code.STORYBOOK_EVIDENCE_REQUIRED, exception.code());
    }

    private static CampaignPlanningApplicationService service(
            AdventureSession session,
            SessionKnowledgeSet knowledgeSet,
            List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> documents,
            CharacterContextSearchPort search,
            CampaignPlanRepository plans) {
        AdventureSessionRepository sessions = new AdventureSessionRepository() {
            @Override
            public Optional<AdventureSession> findById(SessionId id) {
                return session.id().equals(id) ? Optional.of(session) : Optional.empty();
            }

            @Override
            public void save(AdventureSession value, long expectedVersion) {
                throw new UnsupportedOperationException();
            }
        };
        SessionKnowledgeSetRepository knowledgeSets = new SessionKnowledgeSetRepository() {
            @Override
            public Optional<SessionKnowledgeSet> findBySessionId(SessionId id) {
                return knowledgeSet.sessionId().equals(id) ? Optional.of(knowledgeSet) : Optional.empty();
            }

            @Override
            public void save(SessionKnowledgeSet set) {
                throw new UnsupportedOperationException();
            }
        };
        return new CampaignPlanningApplicationService(
                sessions,
                knowledgeSets,
                ownerId -> documents,
                (sessionId, owner, sheetId) -> {},
                search,
                plans);
    }

    private static AdventureSession draftSession(OwnerPlayerId owner, boolean withParty) {
        AdventureSession session = AdventureSession.create(
                SessionId.generate(),
                owner,
                UUID.randomUUID(),
                3L,
                UUID.randomUUID(),
                2L,
                4,
                new AdventureSessionRuntimeConfiguration(
                        new ScenarioId(UUID.randomUUID()),
                        new RuleSetId(UUID.randomUUID()),
                        List.of(),
                        "ollama",
                        List.of("search"),
                        "opening"));
        if (withParty) {
            session.addPartyMember(new AdventurePartyMember(
                    new CharacterSheetId(UUID.randomUUID()),
                    ControlMode.DIRECT,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true));
        }
        return session;
    }

    private static KnowledgeDocumentLookupPort.KnowledgeDocumentRecord document(
            KnowledgeDocumentId id,
            String type,
            KnowledgeDocumentStatus status,
            long extractionVersion) {
        return new KnowledgeDocumentLookupPort.KnowledgeDocumentRecord(
                id,
                status,
                type.toLowerCase() + ".txt",
                type,
                extractionVersion);
    }

    private static final class InMemoryPlanRepository implements CampaignPlanRepository {
        private CampaignPlan plan;

        @Override
        public Optional<CampaignPlan> findBySessionId(SessionId sessionId) {
            return plan != null && plan.sessionId().equals(sessionId) ? Optional.of(plan) : Optional.empty();
        }

        @Override
        public void save(CampaignPlan value) {
            plan = value;
        }
    }
}
