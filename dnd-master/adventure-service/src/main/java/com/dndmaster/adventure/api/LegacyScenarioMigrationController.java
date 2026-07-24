package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationApplicationService;
import com.dndmaster.adventure.application.scenario.ScenarioUpload;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/adventures/legacy-scenarios")
public class LegacyScenarioMigrationController {
    private final LegacyScenarioMigrationApplicationService service;

    public LegacyScenarioMigrationController(LegacyScenarioMigrationApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{scenarioId}/migrate")
    LegacyScenarioMigrationResponse migrate(
            @PathVariable UUID scenarioId,
            @RequestHeader("Authorization") String authorization) {
        OwnerPlayerId owner = ownerFromAuthorization(authorization);
        return LegacyScenarioMigrationResponse.from(service.migrate(new ScenarioId(scenarioId), owner));
    }

    @PostMapping("/{scenarioId}/reupload")
    LegacyScenarioMigrationResponse reupload(
            @PathVariable UUID scenarioId,
            @RequestHeader("Authorization") String authorization,
            @RequestPart("file") MultipartFile file) throws Exception {
        OwnerPlayerId owner = ownerFromAuthorization(authorization);
        ScenarioUpload upload = new ScenarioUpload(
                owner, file.getOriginalFilename(), file.getBytes());
        return LegacyScenarioMigrationResponse.from(
                service.reupload(new ScenarioId(scenarioId), owner, upload));
    }

    private static OwnerPlayerId ownerFromAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is required");
        }
        try {
            return new OwnerPlayerId(UUID.fromString(authorization.substring("Bearer ".length())));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is invalid", exception);
        }
    }

    public record LegacyScenarioMigrationResponse(
            UUID scenarioId,
            UUID bundleId,
            UUID packageId,
            UUID knowledgeDocumentId,
            boolean requiresReupload,
            boolean reupload,
            String sourceFilename,
            String message) {
        static LegacyScenarioMigrationResponse from(
                LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult result) {
            return new LegacyScenarioMigrationResponse(
                    result.scenarioId().value(), result.bundleId(), result.packageId(),
                    result.knowledgeDocumentId() == null ? null : result.knowledgeDocumentId().value(),
                    result.requiresReupload(), result.reupload(), result.sourceFilename(), result.message());
        }
    }
}
