package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.campaign.CampaignPlanningApplicationService;
import com.dndmaster.adventure.domain.adventure.CampaignDocumentRevision;
import com.dndmaster.adventure.domain.adventure.CampaignPlan;
import com.dndmaster.adventure.domain.adventure.CampaignPlanEvidence;
import com.dndmaster.adventure.domain.adventure.CampaignStage;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/adventure-sessions/{sessionId}/campaign-plan")
public final class CampaignPlanController {
    private final CampaignPlanningApplicationService service;
    private final AuthenticatedPlayerResolver playerResolver;

    public CampaignPlanController(
            CampaignPlanningApplicationService service,
            AuthenticatedPlayerResolver playerResolver) {
        this.service = service;
        this.playerResolver = playerResolver;
    }

    @PostMapping
    CampaignPlanView prepare(@PathVariable UUID sessionId) {
        return CampaignPlanView.from(service.prepare(new SessionId(sessionId), owner()));
    }

    @GetMapping
    CampaignPlanView read(@PathVariable UUID sessionId) {
        return CampaignPlanView.from(service.read(new SessionId(sessionId), owner()));
    }

    private OwnerPlayerId owner() {
        return new OwnerPlayerId(playerResolver.playerId());
    }

    public record CampaignPlanView(
            UUID planId,
            UUID sessionId,
            UUID scenarioPackageId,
            long scenarioPackageRevision,
            long revision,
            String overview,
            List<DocumentRevisionView> documents,
            List<UUID> characterSheetIds,
            List<EvidenceView> evidence,
            List<StageView> stages) {
        static CampaignPlanView from(CampaignPlan plan) {
            return new CampaignPlanView(
                    plan.planId(),
                    plan.sessionId().value(),
                    plan.scenarioPackageId(),
                    plan.scenarioPackageRevision(),
                    plan.revision(),
                    plan.overview(),
                    plan.documents().stream().map(DocumentRevisionView::from).toList(),
                    plan.characterSheetIds().stream().map(id -> id.value()).toList(),
                    plan.evidence().stream().map(EvidenceView::from).toList(),
                    plan.stages().stream().map(StageView::from).toList());
        }
    }

    public record DocumentRevisionView(
            UUID knowledgeDocumentId,
            long extractionVersion,
            String originalFilename) {
        static DocumentRevisionView from(CampaignDocumentRevision document) {
            return new DocumentRevisionView(
                    document.knowledgeDocumentId().value(),
                    document.extractionVersion(),
                    document.originalFilename());
        }
    }

    public record EvidenceView(
            UUID evidenceId,
            UUID knowledgeDocumentId,
            long extractionVersion,
            String locator,
            String excerpt) {
        static EvidenceView from(CampaignPlanEvidence evidence) {
            return new EvidenceView(
                    evidence.evidenceId(),
                    evidence.knowledgeDocumentId().value(),
                    evidence.extractionVersion(),
                    evidence.locator(),
                    evidence.excerpt());
        }
    }

    public record StageView(
            int order,
            String scene,
            String goal,
            String conflict,
            List<String> cluesAndNpcs,
            String transitionCondition,
            List<UUID> evidenceIds) {
        static StageView from(CampaignStage stage) {
            return new StageView(
                    stage.order(),
                    stage.scene(),
                    stage.goal(),
                    stage.conflict(),
                    stage.cluesAndNpcs(),
                    stage.transitionCondition(),
                    stage.evidenceIds());
        }
    }
}
