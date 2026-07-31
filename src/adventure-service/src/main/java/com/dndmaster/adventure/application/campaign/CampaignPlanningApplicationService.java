package com.dndmaster.adventure.application.campaign;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.session.CharacterSheetOwnershipPort;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.CampaignDocumentRevision;
import com.dndmaster.adventure.domain.adventure.CampaignPlan;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CampaignPlanningApplicationService {
    private static final int MAX_EVIDENCE_PER_DOCUMENT = 4;
    private static final String CAMPAIGN_SEARCH_QUERY =
            "Find source-grounded campaign scenes, objectives, conflicts, clues, named NPCs, and explicit transition conditions. "
                    + "Return only facts present in this STORYBOOK and preserve source order when possible.";

    private final AdventureSessionRepository sessionRepository;
    private final SessionKnowledgeSetRepository knowledgeSetRepository;
    private final KnowledgeDocumentLookupPort documentLookup;
    private final CharacterSheetOwnershipPort characterSheetOwnership;
    private final CharacterContextSearchPort contextSearch;
    private final CampaignPlanRepository planRepository;
    private final SourceGroundedCampaignPlanFactory planFactory;

    public CampaignPlanningApplicationService(
            AdventureSessionRepository sessionRepository,
            SessionKnowledgeSetRepository knowledgeSetRepository,
            KnowledgeDocumentLookupPort documentLookup,
            CharacterSheetOwnershipPort characterSheetOwnership,
            CharacterContextSearchPort contextSearch,
            CampaignPlanRepository planRepository) {
        this(sessionRepository, knowledgeSetRepository, documentLookup, characterSheetOwnership,
                contextSearch, planRepository, new SourceGroundedCampaignPlanFactory());
    }

    CampaignPlanningApplicationService(
            AdventureSessionRepository sessionRepository,
            SessionKnowledgeSetRepository knowledgeSetRepository,
            KnowledgeDocumentLookupPort documentLookup,
            CharacterSheetOwnershipPort characterSheetOwnership,
            CharacterContextSearchPort contextSearch,
            CampaignPlanRepository planRepository,
            SourceGroundedCampaignPlanFactory planFactory) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "session repository must not be null");
        this.knowledgeSetRepository = Objects.requireNonNull(knowledgeSetRepository, "knowledge set repository must not be null");
        this.documentLookup = Objects.requireNonNull(documentLookup, "document lookup must not be null");
        this.characterSheetOwnership = Objects.requireNonNull(characterSheetOwnership, "character sheet ownership must not be null");
        this.contextSearch = Objects.requireNonNull(contextSearch, "context search must not be null");
        this.planRepository = Objects.requireNonNull(planRepository, "plan repository must not be null");
        this.planFactory = Objects.requireNonNull(planFactory, "plan factory must not be null");
    }

    public CampaignPlan prepare(SessionId sessionId, OwnerPlayerId ownerPlayerId) {
        AdventureSession session = loadAndAuthorize(sessionId, ownerPlayerId);
        List<CharacterSheetId> characterSheetIds = validateActiveParty(session, ownerPlayerId);
        List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> storybooks =
                validateSelectedStorybooks(sessionId, ownerPlayerId);
        List<CampaignDocumentRevision> documentRevisions = storybooks.stream()
                .map(document -> new CampaignDocumentRevision(
                        document.knowledgeDocumentId(),
                        document.extractionVersion(),
                        document.originalFilename()))
                .toList();

        CampaignPlan existing = planRepository.findBySessionId(sessionId).orElse(null);
        if (existing != null && existing.matches(
                session.scenarioPackageRevision(), documentRevisions, characterSheetIds)) {
            return existing;
        }
        if (session.status() != AdventureSession.Status.DRAFT
                && session.status() != AdventureSession.Status.STARTING) {
            throw new CampaignPlanPreparationException(
                    CampaignPlanPreparationException.Code.SESSION_NOT_PREPARABLE,
                    "현재 세션 상태에서는 변경된 캠페인 계획을 다시 만들 수 없습니다.");
        }

        List<CharacterContextSearchPort.Evidence> evidence = collectEvidence(ownerPlayerId, storybooks);
        long revision = existing == null ? 1 : existing.revision() + 1;
        UUID planId = existing == null ? UUID.nameUUIDFromBytes(
                ("campaign-plan:" + sessionId.value()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : existing.planId();
        CampaignPlan plan = planFactory.create(
                planId,
                sessionId,
                session.scenarioPackageId(),
                session.scenarioPackageRevision(),
                revision,
                documentRevisions,
                characterSheetIds,
                evidence);
        planRepository.save(plan);
        return plan;
    }

    public CampaignPlan read(SessionId sessionId, OwnerPlayerId ownerPlayerId) {
        loadAndAuthorize(sessionId, ownerPlayerId);
        return planRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CampaignPlanPreparationException(
                        CampaignPlanPreparationException.Code.STORYBOOK_EVIDENCE_REQUIRED,
                        "아직 저장된 캠페인 계획이 없습니다."));
    }

    private AdventureSession loadAndAuthorize(SessionId sessionId, OwnerPlayerId ownerPlayerId) {
        AdventureSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CampaignPlanPreparationException(
                        CampaignPlanPreparationException.Code.SESSION_NOT_FOUND,
                        "모험 세션을 찾을 수 없습니다."));
        if (!session.ownerPlayerId().equals(ownerPlayerId)) {
            throw new CampaignPlanPreparationException(
                    CampaignPlanPreparationException.Code.SESSION_ACCESS_DENIED,
                    "모험 세션에 접근할 수 없습니다.");
        }
        return session;
    }

    private List<CharacterSheetId> validateActiveParty(AdventureSession session, OwnerPlayerId ownerPlayerId) {
        if (session.party().isEmpty()) {
            throw new CampaignPlanPreparationException(
                    CampaignPlanPreparationException.Code.ACTIVE_CHARACTER_SHEETS_REQUIRED,
                    "캠페인 계획을 만들려면 활성 캐릭터 시트가 한 개 이상 필요합니다.");
        }
        List<CharacterSheetId> characterSheetIds = session.party().stream()
                .map(member -> member.characterSheetId())
                .toList();
        for (CharacterSheetId characterSheetId : characterSheetIds) {
            try {
                characterSheetOwnership.verify(session.id(), ownerPlayerId, characterSheetId);
            } catch (RuntimeException exception) {
                throw new CampaignPlanPreparationException(
                        CampaignPlanPreparationException.Code.ACTIVE_CHARACTER_SHEET_UNAVAILABLE,
                        "활성 캐릭터 시트를 확인할 수 없습니다: " + characterSheetId.value(),
                        exception);
            }
        }
        return characterSheetIds;
    }

    private List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> validateSelectedStorybooks(
            SessionId sessionId, OwnerPlayerId ownerPlayerId) {
        var knowledgeSet = knowledgeSetRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CampaignPlanPreparationException(
                        CampaignPlanPreparationException.Code.STORYBOOK_SELECTION_REQUIRED,
                        "세션에 선택된 STORYBOOK이 없습니다."));
        if (knowledgeSet.knowledgeDocumentIds().isEmpty()) {
            throw new CampaignPlanPreparationException(
                    CampaignPlanPreparationException.Code.STORYBOOK_SELECTION_REQUIRED,
                    "세션에 선택된 STORYBOOK이 없습니다.");
        }

        Map<KnowledgeDocumentId, KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> owned = new LinkedHashMap<>();
        for (KnowledgeDocumentLookupPort.KnowledgeDocumentRecord document
                : documentLookup.findOwnedDocuments(ownerPlayerId.value())) {
            owned.put(document.knowledgeDocumentId(), document);
        }

        List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> selected = new ArrayList<>();
        for (KnowledgeDocumentId documentId : knowledgeSet.knowledgeDocumentIds()) {
            KnowledgeDocumentLookupPort.KnowledgeDocumentRecord document = owned.get(documentId);
            if (document == null) {
                throw new CampaignPlanPreparationException(
                        CampaignPlanPreparationException.Code.STORYBOOK_SELECTION_INVALID,
                        "선택된 문서를 현재 소유 문서에서 찾을 수 없습니다: " + documentId.value());
            }
            if ("STORYBOOK".equals(document.documentType().toUpperCase(Locale.ROOT))) {
                selected.add(document);
            }
        }
        if (selected.isEmpty()) {
            throw new CampaignPlanPreparationException(
                    CampaignPlanPreparationException.Code.STORYBOOK_SELECTION_REQUIRED,
                    "세션에 선택된 STORYBOOK이 없습니다.");
        }
        List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> unready = selected.stream()
                .filter(document -> document.status() != KnowledgeDocumentStatus.INDEXED)
                .toList();
        if (!unready.isEmpty()) {
            throw new CampaignPlanPreparationException(
                    CampaignPlanPreparationException.Code.STORYBOOK_NOT_READY,
                    "INDEXED 상태가 아닌 STORYBOOK이 있습니다: "
                            + unready.stream().map(KnowledgeDocumentLookupPort.KnowledgeDocumentRecord::originalFilename)
                            .toList());
        }
        return List.copyOf(selected);
    }

    private List<CharacterContextSearchPort.Evidence> collectEvidence(
            OwnerPlayerId ownerPlayerId,
            List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> storybooks) {
        List<CharacterContextSearchPort.Evidence> collected = new ArrayList<>();
        for (KnowledgeDocumentLookupPort.KnowledgeDocumentRecord document : storybooks) {
            CharacterContextSearchPort.DocumentScope scope = new CharacterContextSearchPort.DocumentScope(
                    document.knowledgeDocumentId(),
                    "STORYBOOK",
                    document.extractionVersion());
            List<CharacterContextSearchPort.Evidence> found;
            try {
                found = contextSearch.search(new CharacterContextSearchPort.Request(
                        ownerPlayerId.value(),
                        List.of(scope),
                        CAMPAIGN_SEARCH_QUERY,
                        Map.of("STORYBOOK", 0.15),
                        2400));
            } catch (RuntimeException exception) {
                throw new CampaignPlanPreparationException(
                        CampaignPlanPreparationException.Code.STORYBOOK_EVIDENCE_REQUIRED,
                        "STORYBOOK 근거 검색에 실패했습니다: " + document.originalFilename(),
                        exception);
            }
            List<CharacterContextSearchPort.Evidence> valid = found.stream()
                    .filter(item -> "STORYBOOK".equalsIgnoreCase(item.documentType()))
                    .filter(item -> item.documentId().equals(document.knowledgeDocumentId()))
                    .filter(item -> item.extractionVersion() == document.extractionVersion())
                    .distinct()
                    .limit(MAX_EVIDENCE_PER_DOCUMENT)
                    .toList();
            if (valid.isEmpty()) {
                throw new CampaignPlanPreparationException(
                        CampaignPlanPreparationException.Code.STORYBOOK_EVIDENCE_REQUIRED,
                        "캠페인 단계를 만들 유효한 STORYBOOK 근거가 없습니다: " + document.originalFilename());
            }
            collected.addAll(valid);
        }
        return List.copyOf(collected);
    }
}
