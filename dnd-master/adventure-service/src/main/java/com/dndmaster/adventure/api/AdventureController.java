package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.guidance.AnswerRuleInquiryCommand;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.progress.AdventureProgressApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResult;
import com.dndmaster.adventure.application.runtime.SubmitRuntimeTurnCommand;
import com.dndmaster.adventure.application.saved.CreateAdventureCommand;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.application.scenario.ScenarioUpload;
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.inquiry.InquiryId;
import com.dndmaster.adventure.domain.ruleset.DndEdition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class AdventureController {
    private final SavedAdventureApplicationService savedAdventureService;
    private final RuntimeTurnApplicationService runtimeTurnService;
    private final RuleGuidanceApplicationService guidanceService;
    private final AdventureCombatApplicationService combatService;
    private final AdventureScenarioApplicationService scenarioService;

    public AdventureController(
            SavedAdventureApplicationService savedAdventureService,
            RuntimeTurnApplicationService runtimeTurnService,
            RuleGuidanceApplicationService guidanceService,
            AdventureCombatApplicationService combatService,
            AdventureScenarioApplicationService scenarioService) {
        this.savedAdventureService = savedAdventureService;
        this.runtimeTurnService = runtimeTurnService;
        this.guidanceService = guidanceService;
        this.combatService = combatService;
        this.scenarioService = scenarioService;
    }

    @PostMapping("/api/v1/adventures/scenarios")
    ResponseEntity<Void> uploadScenario(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String authorization) throws Exception {
        UUID ownerId = extractPlayerId(authorization);
        ScenarioUpload upload = new ScenarioUpload(
                new com.dndmaster.adventure.domain.scenario.OwnerPlayerId(ownerId), file.getOriginalFilename(), file.getBytes());
        scenarioService.uploadScenario(upload);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/adventures/{adventureId}/messages")
    RuntimeTurnResponse streamAdventure(
            @PathVariable UUID adventureId, @RequestBody StreamMessageRequest request) {
        // 플레이어 입력을 런타임 턴으로 바꾸고, 서버가 만든 narration을 돌려준다.
        RuntimeTurnResult result = runtimeTurnService.submitTurn(new SubmitRuntimeTurnCommand(
                new AdventureId(adventureId),
                new OwnerPlayerId(request.playerId()),
                request.turnId(),
                request.commandId(),
                request.action()));
        return RuntimeTurnResponse.from(result);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/rule-inquiries")
    RuleInquiryResponse answerRuleInquiry(
            @PathVariable UUID adventureId, @RequestBody RuleInquiryRequest request) {
        AnswerRuleInquiryCommand command = new AnswerRuleInquiryCommand(
                new InquiryId(request.inquiryId()),
                new AdventureId(adventureId),
                new RuleSetId(request.ruleSetId()),
                new OwnerPlayerId(request.playerId()),
                request.situation());
        guidanceService.answerInquiry(command);
        return new RuleInquiryResponse(request.inquiryId(), "answered");
    }

    @GetMapping("/api/v1/adventures/{adventureId}/combat-map")
    CombatMapResponse playerMap(@PathVariable UUID adventureId) {
        return new CombatMapResponse(adventureId, "map-view");
    }

    @PostMapping("/api/v1/adventures/{adventureId}/dice-rolls")
    DiceRollResponse diceRoll(
            @PathVariable UUID adventureId, @RequestBody DiceRollRequest request) {
        CombatActionCommand command = new CombatActionCommand(
                UUID.randomUUID(),
                new AdventureId(adventureId),
                new RuleSetId(request.ruleSetId()),
                new CharacterSheetId(request.characterSheetId()),
                request.combatMapId(),
                CombatActorRole.valueOf(request.role()),
                request.action(),
                null,
                request.ownerPlayerId(),
                request.tokenId(),
                request.expectedVersion());
        combatService.resolveCombatAction(command);
        return new DiceRollResponse(UUID.randomUUID(), request.role(), List.of(), 0);
    }

    @PutMapping("/api/v1/adventures/{adventureId}/save")
    SaveAdventureResponse saveAdventure(
            @PathVariable UUID adventureId, @RequestBody SaveAdventureRequest request) {
        savedAdventureService.preserveProgress(
                new AdventureId(adventureId),
                new OwnerPlayerId(request.playerId()),
                request.expectedVersion(),
                new AdventureContext(request.currentScene(), null, null, null),
                List.of());
        return new SaveAdventureResponse(adventureId, request.expectedVersion() + 1);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/resume")
    ResponseEntity<Void> resumeAdventure(@PathVariable UUID adventureId) {
        savedAdventureService.reopenAdventure(
                new AdventureId(adventureId),
                new OwnerPlayerId(UUID.randomUUID()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/adventures/{adventureId}")
    ResponseEntity<Void> deleteAdventure(
            @PathVariable UUID adventureId, @RequestBody DeleteAdventureRequest request) {
        savedAdventureService.deleteAdventure(
                new AdventureId(adventureId),
                new OwnerPlayerId(request.playerId()),
                request.expectedVersion());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal/v1/adventures")
    List<AdventureSummaryResponse> ownedAdventures(@RequestParam UUID ownerId) {
        return savedAdventureService.listSavedAdventures(new OwnerPlayerId(ownerId)).stream()
                .map(a -> new AdventureSummaryResponse(a.id().value(), a.status().name()))
                .toList();
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/edition")
    EditionResponse appliedEdition(@PathVariable UUID adventureId) {
        return new EditionResponse(adventureId, "DND_5E_2024");
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/roll-conditions")
    RollConditionsResponse rollConditions(@PathVariable UUID adventureId) {
        return new RollConditionsResponse(adventureId, "standard");
    }

    @PostMapping("/internal/v1/adventures/{adventureId}/movement-validations")
    MovementValidationResponse validateMovement(
            @PathVariable UUID adventureId, @RequestBody MovementValidationRequest request) {
        return new MovementValidationResponse(adventureId, true, "valid");
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/gm-context")
    GmContextResponse gmContext(@PathVariable UUID adventureId) {
        return new GmContextResponse(adventureId, "current-scene", "npc-state");
    }

    private static UUID extractPlayerId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer authorization is required");
        }
        return UUID.fromString(authorization.substring("Bearer ".length()));
    }

    public record StreamMessageRequest(UUID playerId, UUID turnId, UUID commandId, String action) {}
    // 프런트가 바로 보여줄 수 있게 턴 결과를 압축한 응답이다.
    public record RuntimeTurnResponse(
            UUID turnId,
            UUID adventureId,
            UUID scenarioPackageId,
            long bindingVersion,
            String narration,
            String judgment,
            String currentScene,
            List<String> sourceRefs,
            List<String> warnings,
            long version) {
        static RuntimeTurnResponse from(RuntimeTurnResult result) {
            return new RuntimeTurnResponse(
                    result.turn().turnId(),
                    result.turn().adventureId().value(),
                    result.turn().scenarioPackageId(),
                    result.turn().bindingVersion(),
                    result.turn().plan().narration(),
                    result.turn().plan().judgment(),
                    result.context().currentScene(),
                    result.turn().citations(),
                    result.turn().warnings(),
                    result.version());
        }
    }
    public record RuleInquiryRequest(UUID inquiryId, UUID ruleSetId, UUID playerId, String situation) {}
    public record RuleInquiryResponse(UUID inquiryId, String status) {}
    public record CombatMapResponse(UUID adventureId, String status) {}
    public record DiceRollRequest(
            UUID ruleSetId,
            UUID characterSheetId,
            UUID combatMapId,
            UUID ownerPlayerId,
            UUID tokenId,
            long expectedVersion,
            String role,
            String action) {}
    public record DiceRollResponse(UUID rollId, String scope, List<Integer> faces, int total) {}
    public record SaveAdventureRequest(UUID playerId, long expectedVersion, String currentScene) {}
    public record SaveAdventureResponse(UUID adventureId, long newVersion) {}
    public record DeleteAdventureRequest(UUID playerId, long expectedVersion) {}
    public record AdventureSummaryResponse(UUID adventureId, String status) {}
    public record EditionResponse(UUID adventureId, String edition) {}
    public record RollConditionsResponse(UUID adventureId, String conditions) {}
    public record MovementValidationRequest(UUID tokenId, int x, int y) {}
    public record MovementValidationResponse(UUID adventureId, boolean valid, String reason) {}
    public record GmContextResponse(UUID adventureId, String currentScene, String npcState) {}
}
