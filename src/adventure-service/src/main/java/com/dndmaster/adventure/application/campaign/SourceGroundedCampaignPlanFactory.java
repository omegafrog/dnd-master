package com.dndmaster.adventure.application.campaign;

import com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort;
import com.dndmaster.adventure.domain.adventure.CampaignDocumentRevision;
import com.dndmaster.adventure.domain.adventure.CampaignPlan;
import com.dndmaster.adventure.domain.adventure.CampaignPlanEvidence;
import com.dndmaster.adventure.domain.adventure.CampaignStage;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SourceGroundedCampaignPlanFactory {
    public CampaignPlan create(
            UUID planId,
            SessionId sessionId,
            UUID scenarioPackageId,
            long scenarioPackageRevision,
            long revision,
            List<CampaignDocumentRevision> documents,
            List<CharacterSheetId> characterSheetIds,
            List<CharacterContextSearchPort.Evidence> sourceEvidence) {
        List<CampaignPlanEvidence> evidence = sourceEvidence.stream()
                .map(this::toEvidence)
                .toList();
        List<CampaignStage> stages = new ArrayList<>();
        for (int index = 0; index < evidence.size(); index++) {
            CampaignPlanEvidence item = evidence.get(index);
            stages.add(new CampaignStage(
                    index + 1,
                    sourceScene(item.excerpt()),
                    "이 장면의 근거에 명시된 목표 또는 상황을 확인한다.",
                    "근거에 명시된 장애·갈등만 사용해 장면을 진행한다.",
                    List.of(item.excerpt()),
                    "현재 근거의 상황이 해결되거나 다음 근거가 명시될 때 전환한다.",
                    List.of(item.evidenceId())));
        }
        return new CampaignPlan(
                planId,
                sessionId,
                scenarioPackageId,
                scenarioPackageRevision,
                revision,
                "선택된 STORYBOOK " + documents.size() + "개의 근거를 순서대로 진행하는 캠페인 계획이다.",
                documents,
                characterSheetIds,
                evidence,
                stages);
    }

    private CampaignPlanEvidence toEvidence(CharacterContextSearchPort.Evidence source) {
        String fingerprint = source.documentId().value() + ":" + source.extractionVersion() + ":"
                + source.locator() + ":" + source.excerpt();
        return new CampaignPlanEvidence(
                UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)),
                source.documentId(),
                source.extractionVersion(),
                source.locator(),
                source.excerpt());
    }

    private static String sourceScene(String excerpt) {
        String compact = excerpt.replaceAll("\\s+", " ").trim();
        return compact.length() <= 320 ? compact : compact.substring(0, 317) + "...";
    }
}
