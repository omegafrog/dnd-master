package com.dndmaster.adventure.application.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.runtime.GmProviderSelection;
import com.dndmaster.adventure.application.runtime.ProviderBinding;
import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.preparation.InitialSituationGmPort;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureAbilityDefinitionPreparationServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void corrects_invalid_structured_output_and_saves_only_after_validation_can_succeed() throws Exception {
        var definitions = mock(AdventureAbilityDefinitionRepository.class);
        var bindings = mock(GmProviderBindingRepository.class);
        var gm = mock(InitialSituationGmPort.class);
        UUID sessionUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID documentUuid = UUID.randomUUID();
        SessionId session = new SessionId(sessionUuid);
        when(bindings.current(sessionUuid)).thenReturn(Optional.of(new ProviderBinding(sessionUuid,
                new GmProviderSelection(endpointId, "codex", "test-model", "medium"), 1)));
        when(gm.generate(any())).thenReturn(JSON.readTree("{\"abilityId\":\"ability:blink\"}"), validDefinition(documentUuid));
        var service = new AdventureAbilityDefinitionPreparationService(definitions,
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), bindings, gm, JSON);
        var evidence = List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentUuid), 3, "page 12", "The ability moves the user.", "cite-1"));

        JsonNode result = service.generateValidated(new AdventureId(UUID.randomUUID()), session,
                new OwnerPlayerId(ownerUuid), "ability:blink", evidence);

        assertThat(result.path("abilityId").asText()).isEqualTo("ability:blink");
        verify(gm, times(2)).generate(any());
    }

    @Test
    void repeated_validation_failure_exhausts_distinct_correction_paths_and_requests_source_input() throws Exception {
        var bindings = mock(GmProviderBindingRepository.class);
        var gm = mock(InitialSituationGmPort.class);
        UUID sessionUuid = UUID.randomUUID();
        UUID documentUuid = UUID.randomUUID();
        var invalid = JSON.readTree("{\"abilityId\":\"ability:blink\"}");
        when(bindings.current(sessionUuid)).thenReturn(Optional.of(new ProviderBinding(sessionUuid,
                new GmProviderSelection(UUID.randomUUID(), "codex", "test-model", "medium"), 1)));
        when(gm.generate(any())).thenReturn(invalid, invalid);
        var service = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), bindings, gm, JSON);
        var evidence = List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentUuid), 3, "page 12", "The ability moves the user.", "cite-1"));

        assertThatThrownBy(() -> service.generateValidated(new AdventureId(UUID.randomUUID()),
                new SessionId(sessionUuid), new OwnerPlayerId(UUID.randomUUID()), "ability:blink", evidence))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gm, times(2)).generate(any());
    }

    @Test
    void rejects_user_input_that_does_not_match_the_fixed_definition_field_types() throws Exception {
        UUID documentId = UUID.randomUUID();
        JsonNode malformed = ((com.fasterxml.jackson.databind.node.ObjectNode) validDefinition(documentId)).put("range", 30);
        var service = new AdventureAbilityDefinitionPreparationService(mock(AdventureAbilityDefinitionRepository.class),
                mock(ScenarioPackageRepository.class), mock(SessionKnowledgeSetRepository.class),
                mock(AdventureSessionRepository.class), mock(RuntimeCharacterSheetReadPort.class),
                mock(RuntimeEvidenceSearchPort.class), mock(GmProviderBindingRepository.class),
                mock(InitialSituationGmPort.class), JSON);
        var evidence = List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentId), 3, "page 12", "The ability moves the user.", "cite-1"));

        assertThatThrownBy(() -> service.validate("ability:blink", malformed, evidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("string or null");
    }

    private static JsonNode validDefinition(UUID documentId) throws Exception {
        return JSON.readTree("""
                {"abilityId":"ability:blink","name":"Blink","actionTiming":"action","resourceCost":null,
                "visibility":null,"range":"self","targeting":"self","origin":"self","effect":"teleport",
                "pathBlocking":null,"effectSpread":null,"check":null,"duration":"instant","trigger":null,
                "exceptions":null,"sourceLocations":[{"documentId":"%s","extractionVersion":3,"locator":"page 12"}]}
                """.formatted(documentId));
    }
}
