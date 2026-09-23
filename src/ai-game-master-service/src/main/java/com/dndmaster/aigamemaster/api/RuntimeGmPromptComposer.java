package com.dndmaster.aigamemaster.api;

import java.util.List;
import java.util.Map;

/** Keeps the provider instructions ahead of all turn-specific material. */
final class RuntimeGmPromptComposer {
    private static final String RULE_MARKER = "\nLOOKUP_ORDER_RULE=";

    private RuntimeGmPromptComposer() { }

    static String compose(String existingPrompt, List<String> recentTurns,
            List<String> characterSheets, Map<String, Object> runtimeContext) {
        return compose(existingPrompt, recentTurns, characterSheets, runtimeContext, 128000);
    }

    static String compose(String existingPrompt, List<String> recentTurns,
            List<String> characterSheets, Map<String, Object> runtimeContext, int contextLimit) {
        int rules = existingPrompt.indexOf(RULE_MARKER);
        if (rules < 0) throw new IllegalArgumentException("runtime GM rules are missing");
        int action = existingPrompt.indexOf("\nACTION=");
        if (action < 0 || action > rules) throw new IllegalArgumentException("runtime GM action is missing");
        int evidence = existingPrompt.indexOf("\nCOMPOSITE_FACT_LOOKUP_RESULTS=");
        if (evidence < 0 || evidence > action) throw new IllegalArgumentException("runtime GM evidence is missing");
        int runtime = existingPrompt.indexOf("\nRUNTIME_CONTEXT=", evidence);
        if (runtime < 0 || runtime > action) throw new IllegalArgumentException("runtime GM context is missing");
        String fixed = existingPrompt.substring(0, evidence) + existingPrompt.substring(rules);
        String actionText = existingPrompt.substring(action, rules);
        String evidenceText = existingPrompt.substring(evidence, runtime);
        Map<String, Object> current = new java.util.LinkedHashMap<>(runtimeContext);
        current.remove("recentTurns");
        current.remove("characterSnapshots");
        String currentJson;
        try {
            currentJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(current);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("runtime GM context cannot be serialized", exception);
        }
        String fixedZone = "고정 지침·잠긴 자료\n" + fixed;
        String memoryZone = "\n\n현재 상황 관련 장기 기록\n";
        String summaryZone = "\n\n압축된 이전 대화\n";
        String recentHeading = "\n\n압축하지 않은 최근 대화\n";
        String currentZone = "\n\n최신 캐릭터 시트·Current Situation·이번 턴 근거·플레이어 입력\n"
                + "CHARACTER_SHEETS=" + characterSheets
                + "\nRUNTIME_CONTEXT=" + currentJson + evidenceText + actionText;
        List<String> selected = RuntimeGmInputBudget.selectRecent(contextLimit, fixedZone,
                memoryZone, summaryZone, recentTurns, currentZone + recentHeading);
        String prompt = fixedZone + memoryZone + summaryZone + recentHeading + selected + currentZone;
        if (prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > RuntimeGmInputBudget.inputLimit(contextLimit)) {
            throw new RuntimeGmInputBudget.InputTooLargeException();
        }
        return prompt;
    }
}
