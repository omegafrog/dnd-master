package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.rule.SourceEvidence;
import com.dndmaster.aigamemaster.application.scene.ScenarioPrompt;
import com.dndmaster.aigamemaster.application.scene.ScenarioAlignment;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneModelContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsRuntimeFactsAndChoicesOnlyWithACompleteNumberedContract() {
        UUID scenario = UUID.randomUUID();
        UUID rules = UUID.randomUUID();
        ScenarioPrompt prompt = new ScenarioPrompt("scene [E1]", scenario, rules);
        String response = "{\"facts\":[{\"evidence\":1,\"text\":\"문이 보인다.\",\"grounding\":\"CANONICAL\"},"
                + "{\"evidence\":0,\"text\":\"수문장이 잠시 망설인다.\",\"grounding\":\"RUNTIME\"}],"
                + "\"choices\":[{\"evidence\":0,\"number\":1,\"text\":\"조건을 협상한다.\",\"grounding\":\"RUNTIME\"},"
                + "{\"evidence\":0,\"number\":2,\"text\":\"새 질문을 한다.\",\"grounding\":\"RUNTIME\"},"
                + "{\"evidence\":0,\"number\":3,\"text\":\"주변을 살핀다.\",\"grounding\":\"RUNTIME\"}]}";
        var model = new AiGameMasterApiConfiguration().sceneModelPort(fixed(response), mapper);

        var output = model.generateScene(prompt);

        assertEquals("[E1] 문이 보인다.\n[RUNTIME_FACT] 수문장이 잠시 망설인다.\n"
                + "[RUNTIME] 1. 조건을 협상한다.\n[RUNTIME] 2. 새 질문을 한다.\n[RUNTIME] 3. 주변을 살핀다.", output.scene());
        assertEquals(ScenarioAlignment.RUNTIME_INTERACTION, output.alignment());
    }

    @Test
    void rejectsMissingFactsOrDuplicateChoiceNumbersBeforeGrounding() {
        UUID scenario = UUID.randomUUID();
        UUID rules = UUID.randomUUID();
        ScenarioPrompt prompt = new ScenarioPrompt("scene [E1]", scenario, rules);
        String response = "{\"facts\":[{\"evidence\":1,\"text\":\"문이 보인다.\"}],"
                + "\"choices\":[{\"evidence\":1,\"number\":1,\"text\":\"첫째\"},"
                + "{\"evidence\":1,\"number\":1,\"text\":\"둘째\"},"
                + "{\"evidence\":1,\"number\":3,\"text\":\"셋째\"}]}";
        var model = new AiGameMasterApiConfiguration().sceneModelPort(fixed(response), mapper);

        assertThrows(IllegalArgumentException.class, () -> model.generateScene(prompt));
    }

    private static GmCompletionAdapter fixed(String response) {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                return parser.parse(response);
            }
        };
    }
}
