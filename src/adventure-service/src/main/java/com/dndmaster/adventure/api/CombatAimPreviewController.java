package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatMapPlayerTokenResolver;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.combat.CombatAimPreviewPolicy;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionReviewPreparation;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public final class CombatAimPreviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CombatAimPreviewController.class);
    private final CombatEncounterRepository encounters;
    private final AdventureRepository adventures;
    private final AuthenticatedPlayerResolver playerResolver;
    private final ScenarioPackageRepository packages;
    private final CombatMapViewPort maps;

    public CombatAimPreviewController(CombatEncounterRepository encounters, AdventureRepository adventures,
            AuthenticatedPlayerResolver playerResolver, ScenarioPackageRepository packages, CombatMapViewPort maps) {
        this.encounters = encounters;
        this.adventures = adventures;
        this.playerResolver = playerResolver;
        this.packages = packages;
        this.maps = maps;
    }

    @GetMapping("/api/v1/adventures/{adventureId}/combat/actions/definitions")
    public List<DefinitionResponse> definitions(@PathVariable UUID adventureId) {
        Adventure adventure = ownedAdventure(adventureId);
        SpellDefinitionReviewPreparation review = lockedDefinitions(adventure);
        return review.definitions().stream().filter(StructuredSpellDefinition::active)
                .filter(definition -> definition.reviewStatus() == StructuredSpellDefinition.ReviewStatus.APPROVED)
                .filter(CombatAimPreviewController::supportsAimAction)
                .map(definition -> DefinitionResponse.from(definition, review.revision())).toList();
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat/actions/preview")
    public AimPreviewResponse preview(@PathVariable UUID adventureId, @RequestBody AimPreviewRequest request) {
        Adventure adventure = ownedAdventure(adventureId);
        long startedAt = DevelopmentDiagnostics.begin(LOGGER, "combat_action_aim_preview",
                "adventureId=" + adventureId + " definitionId=" + safeId(request.definitionId())
                        + " definitionVersion=" + request.definitionVersion() + " target=" + request.targetX() + "," + request.targetY());
        try {
            var encounter = encounters.findActive(adventureId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "COMBAT_NOT_ACTIVE"));
            if (request.expectedEncounterVersion() != encounter.version()) throw new ResponseStatusException(HttpStatus.CONFLICT, "ENCOUNTER_VERSION_CHANGED");
            SpellDefinitionReviewPreparation review = lockedDefinitions(adventure);
            if (request.definitionVersion() != review.revision()) throw new ResponseStatusException(HttpStatus.CONFLICT, "DEFINITION_VERSION_CHANGED");
            StructuredSpellDefinition definition = review.definitions().stream()
                    .filter(item -> item.id().equals(request.definitionId()) && item.active()
                            && item.reviewStatus() == StructuredSpellDefinition.ReviewStatus.APPROVED
                            && supportsAimAction(item))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "ACTION_DEFINITION_UNAVAILABLE"));
            var view = maps.playerView(adventureId, adventure.ownerPlayerId().value())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMBAT_MAP_NOT_FOUND"));
            if (request.expectedMapVersion() != view.version()) throw new ResponseStatusException(HttpStatus.CONFLICT, "MAP_VERSION_CHANGED");
            var member = CombatMapPlayerTokenResolver.resolve(adventure, adventure.ownerPlayerId().value(), request.tokenId(), maps);
            if (!encounter.currentParticipantId().equals(member.characterSheetId().value())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AIM_TOKEN_NOT_CURRENT_ACTOR");
            }
            var actor = view.tokens().stream().filter(token -> token.id().equals(request.tokenId()) && "PLAYER".equals(token.type()))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "AIM_TOKEN_NOT_PUBLIC"));
            if (view.grid() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "MAP_GRID_UNAVAILABLE");
            var target = new CombatAimPreviewPolicy.Cell(request.targetX(), request.targetY());
            if (!visible(view, target)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TARGET_CELL_NOT_PUBLIC");
            List<CombatAimPreviewPolicy.Cell> blockers = publicBlockers(view);
            int targetElevationFeet = view.current() != null && view.current().stream()
                    .anyMatch(cell -> cell.x() == target.x() && cell.y() == target.y())
                    ? view.tokens().stream().filter(token -> token.x() == target.x() && token.y() == target.y())
                            .mapToInt(CombatMapViewPort.Token::elevationFeet).findFirst().orElse(0)
                    : 0;
            var preview = CombatAimPreviewPolicy.preview(definition,
                    new CombatAimPreviewPolicy.Cell(actor.x(), actor.y()), actor.elevationFeet(), target,
                    targetElevationFeet, view.grid().distanceUnit(),
                    view.grid().width(), view.grid().height(), blockers, publicBoundaries(view));
            List<CombatAimPreviewPolicy.Cell> publicPath = preview.path().stream().filter(cell -> visible(view, cell)).toList();
            List<CombatAimPreviewPolicy.Cell> publicEffect = preview.effectCells().stream().filter(cell -> visible(view, cell)).toList();
            boolean pathPublic = publicPath.size() == preview.path().size();
            if (!pathPublic && DevelopmentDiagnostics.enabled()) LOGGER.info(
                    "dev_flow operation=combat_action_aim_preview outcome=fallback context=reason=PATH_CROSSES_UNEXPLORED expected=public-cells actual=clipped");
            if (!preview.supported() && DevelopmentDiagnostics.enabled()) LOGGER.info(
                    "dev_flow operation=combat_action_aim_preview outcome=fallback context=definitionId={} definitionVersion={} reason=UNSUPPORTED_DEFINITION expected=supported-range-and-targeting actual=unsupported",
                    safeId(definition.id()), review.revision());
            DevelopmentDiagnostics.complete(LOGGER, "combat_action_aim_preview",
                    "definitionId=" + safeId(definition.id()) + " definitionVersion=" + review.revision()
                            + " pathCells=" + publicPath.size() + " effectCells=" + publicEffect.size()
                            + " distanceFeet=" + preview.distanceFeet() + " maxRangeFeet=" + maximumRange(definition)
                            + " range=" + (preview.inRange() ? "in-range" : "out-of-range")
                            + " pathPublic=" + pathPublic + " blockReason=" + preview.blockReason(), startedAt);
            return AimPreviewResponse.from(preview, publicPath, publicEffect, pathPublic, encounter.version(), view.version(), review.revision());
        } catch (RuntimeException | Error failure) {
            DevelopmentDiagnostics.fail(LOGGER, "combat_action_aim_preview",
                    "adventureId=" + adventureId + " definitionId=" + safeId(request.definitionId())
                            + " target=" + request.targetX() + "," + request.targetY(), startedAt, failure);
            throw failure;
        }
    }

    private Adventure ownedAdventure(UUID adventureId) {
        Adventure adventure = adventures.findById(new AdventureId(adventureId)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return adventure;
    }

    private SpellDefinitionReviewPreparation lockedDefinitions(Adventure adventure) {
        return packages.findSpellDefinitionPreparationBySession(adventure.sessionId().value())
                .filter(review -> review.locked() && review.ownerId().equals(adventure.ownerPlayerId().value()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "ACTION_DEFINITIONS_NOT_LOCKED"));
    }

    private static boolean visible(CombatMapViewPort.View view, CombatAimPreviewPolicy.Cell target) {
        if (target.x() < 0 || target.y() < 0 || target.x() >= view.grid().width() || target.y() >= view.grid().height()) return false;
        return view.current() != null && view.current().stream().anyMatch(cell -> cell.x() == target.x() && cell.y() == target.y())
                || view.explored() != null && view.explored().stream().anyMatch(cell -> cell.x() == target.x() && cell.y() == target.y());
    }

    private static boolean supportsAimAction(StructuredSpellDefinition definition) {
        return definition.rangeProfile() != null && definition.targetingProfile() != null
                && switch (definition.actionKind()) {
                    case SPELL, RANGED_ATTACK, THROWN -> true;
                    default -> false;
                };
    }

    private static Integer maximumRange(StructuredSpellDefinition definition) {
        var range = definition.rangeProfile();
        if (range == null) return null;
        return range.form() == StructuredSpellDefinition.RangeForm.NORMAL_AND_LONG
                ? range.longRangeFeet() : range.normalRangeFeet();
    }

    static List<CombatAimPreviewPolicy.Cell> publicBlockers(CombatMapViewPort.View view) {
        List<CombatAimPreviewPolicy.Cell> blockers = new ArrayList<>();
        if (view.obstacles() != null) view.obstacles().stream()
                .map(cell -> new CombatAimPreviewPolicy.Cell(cell.x(), cell.y()))
                .filter(cell -> visible(view, cell)).forEach(blockers::add);
        if (view.doors() != null) view.doors().stream().filter(door -> !door.open())
                .map(door -> new CombatAimPreviewPolicy.Cell(door.x(), door.y()))
                .filter(cell -> visible(view, cell)).forEach(blockers::add);
        return List.copyOf(blockers);
    }

    static List<CombatAimPreviewPolicy.Boundary> publicBoundaries(CombatMapViewPort.View view) {
        List<CombatAimPreviewPolicy.Boundary> boundaries = new ArrayList<>();
        if (view.layers() == null) return List.of();
        for (CombatMapViewPort.Layer layer : view.layers()) {
            if (layer == null || !"MAP_BOUNDARIES".equals(layer.type()) || layer.value() == null || layer.value().isBlank()) continue;
            for (String value : layer.value().split(";")) {
                try {
                    String[] parts = value.split(",");
                    if (parts.length != 5) throw new IllegalArgumentException("invalid boundary record");
                    int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]);
                    String orientation = parts[2].trim().toUpperCase(java.util.Locale.ROOT);
                    String kind = parts[3].trim().toUpperCase(java.util.Locale.ROOT);
                    if (!(orientation.equals("HORIZONTAL") || orientation.equals("VERTICAL"))
                            || !(kind.equals("WALL") || kind.equals("DOOR"))
                            || !(parts[4].trim().equalsIgnoreCase("true") || parts[4].trim().equalsIgnoreCase("false"))) {
                        throw new IllegalArgumentException("invalid boundary enum or state");
                    }
                    boolean open = Boolean.parseBoolean(parts[4].trim());
                    if (!insideBoundary(view, x, y, orientation)) throw new IllegalArgumentException("boundary outside map");
                    if (adjacentCells(view, x, y, orientation).stream().allMatch(cell -> visible(view, cell))) {
                        boundaries.add(new CombatAimPreviewPolicy.Boundary(x, y, orientation, kind, open));
                    }
                } catch (RuntimeException malformed) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "MAP_BOUNDARY_DATA_INVALID", malformed);
                }
            }
        }
        return List.copyOf(boundaries);
    }

    private static boolean insideBoundary(CombatMapViewPort.View view, int x, int y, String orientation) {
        return "HORIZONTAL".equals(orientation)
                ? x >= 0 && x < view.grid().width() && y >= 0 && y <= view.grid().height()
                : x >= 0 && x <= view.grid().width() && y >= 0 && y < view.grid().height();
    }

    private static List<CombatAimPreviewPolicy.Cell> adjacentCells(CombatMapViewPort.View view, int x, int y, String orientation) {
        List<CombatAimPreviewPolicy.Cell> cells = new ArrayList<>(2);
        if ("HORIZONTAL".equals(orientation)) {
            if (y > 0) cells.add(new CombatAimPreviewPolicy.Cell(x, y - 1));
            if (y < view.grid().height()) cells.add(new CombatAimPreviewPolicy.Cell(x, y));
        } else {
            if (x > 0) cells.add(new CombatAimPreviewPolicy.Cell(x - 1, y));
            if (x < view.grid().width()) cells.add(new CombatAimPreviewPolicy.Cell(x, y));
        }
        return cells;
    }

    private static String safeId(String value) { return value == null ? "none" : value.replaceAll("[^A-Za-z0-9_-]", "_"); }

    public record AimPreviewRequest(UUID commandId, String definitionId, long definitionVersion, UUID tokenId,
            int targetX, int targetY, long expectedEncounterVersion, long expectedMapVersion) { }
    public record DefinitionResponse(String id, String name, long version, StructuredSpellDefinition.ActionKind actionKind,
            StructuredSpellDefinition.RangeProfile rangeProfile, StructuredSpellDefinition.TargetingProfile targetingProfile) {
        static DefinitionResponse from(StructuredSpellDefinition definition, long version) {
            return new DefinitionResponse(definition.id(), definition.name(), version, definition.actionKind(),
                    definition.rangeProfile(), definition.targetingProfile());
        }
    }
    public record AimPreviewResponse(boolean supported, int distanceFeet, boolean inRange, boolean longRange,
            boolean clearPath, String blockReason, List<CombatAimPreviewPolicy.Cell> path,
            List<CombatAimPreviewPolicy.Cell> effectCells, long encounterVersion, long mapVersion, long definitionVersion) {
        static AimPreviewResponse from(CombatAimPreviewPolicy.Preview preview, List<CombatAimPreviewPolicy.Cell> path,
                List<CombatAimPreviewPolicy.Cell> effectCells, boolean pathPublic, long encounterVersion, long mapVersion, long definitionVersion) {
            return new AimPreviewResponse(preview.supported(), preview.distanceFeet(), preview.inRange(), preview.longRange(),
                    preview.clearPath() && pathPublic, pathPublic ? preview.blockReason() : "PATH_NOT_PUBLIC", path, effectCells,
                    encounterVersion, mapVersion, definitionVersion);
        }
    }
}
