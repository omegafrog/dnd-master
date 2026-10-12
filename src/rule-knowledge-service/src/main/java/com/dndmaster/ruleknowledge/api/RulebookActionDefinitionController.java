package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionExtraction;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionRepository;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Internal read boundary for definitions extracted from the selected published source version. */
@RestController
@RequestMapping("/internal/v1/rulebooks")
public final class RulebookActionDefinitionController {
    private static final UUID CATALOG_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private final RulebookActionDefinitionRepository definitions;
    private final RulebookRegistrationRepository registrations;
    private final String internalToken;

    public RulebookActionDefinitionController(RulebookActionDefinitionRepository definitions,
            RulebookRegistrationRepository registrations,
            @Value("${rule-knowledge.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        this.definitions = definitions; this.registrations = registrations; this.internalToken = internalToken;
    }

    @GetMapping("/{documentId}/action-definitions")
    RulebookActionDefinitionExtraction definitions(@PathVariable UUID documentId,
            @RequestParam long registrationVersion,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "internal access denied");
        var registration = registrations.findById(new RulebookId(documentId))
                .filter(item -> item.documentType() == DocumentType.RULEBOOK && item.ownerPlayerId().value().equals(CATALOG_OWNER))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "rulebook not found"));
        if (registration.version() != registrationVersion || registration.candidateExtractionVersion() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requested action definition version is not current");
        RulebookActionDefinitionExtraction extraction = definitions.find(documentId, registration.candidateExtractionVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "action definitions not found"));
        if (!"COMPLETED".equals(extraction.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "action definitions are not complete");
        return extraction;
    }
}
