package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeGmPromptComposerTest {
    @Test
    void rejects_essential_material_before_provider_call_when_input_budget_is_exceeded() {
        String legacy = "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[]\nRUNTIME_CONTEXT={}\nACTION=go"
                + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json";
        assertThrows(RuntimeGmInputBudget.InputTooLargeException.class,
                () -> RuntimeGmPromptComposer.compose(legacy, List.of("latest turn"),
                        List.of("x".repeat(1000)), Map.of(), 1000));
    }

    @Test
    void budgets_five_zones_and_keeps_latest_turn_untrimmed() {
        int budget = RuntimeGmInputBudget.inputLimit(1000);
        assertEquals(800, budget);
        assertThrows(RuntimeGmInputBudget.InputTooLargeException.class,
                () -> RuntimeGmInputBudget.selectRecent(1000, "f".repeat(241), "", "", List.of(), "c"));
        assertThrows(RuntimeGmInputBudget.InputTooLargeException.class,
                () -> RuntimeGmInputBudget.selectRecent(1000, "f", "", "", List.of("r".repeat(161)), "c"));
        assertEquals(List.of("new"), RuntimeGmInputBudget.selectRecent(1000, "f", "", "",
                List.of("old".repeat(100), "new"), "c".repeat(200)));
        assertEquals(List.of(), RuntimeGmInputBudget.selectRecent(1000, "f", "", "", List.of(), "c"));
    }

    @Test
    void keeps_latest_complete_turn_and_pending_player_material_together() {
        List<String> entries = List.of("PLAYER: earlier", "AI_GAME_MASTER: old reply",
                "PLAYER: latest", "AI_GAME_MASTER: latest scene", "AI_GAME_MASTER: latest judgment",
                "PLAYER: pending choice");
        assertEquals(entries.subList(2, 6), RuntimeGmInputBudget.selectRecent(1000, "f", "", "",
                entries, "c"));
        assertThrows(RuntimeGmInputBudget.InputTooLargeException.class,
                () -> RuntimeGmInputBudget.selectRecent(100, "f", "", "",
                        entries.subList(2, 6), "c"));
        assertThrows(RuntimeGmInputBudget.InputTooLargeException.class,
                () -> RuntimeGmInputBudget.selectRecent(300, "f", "", "",
                        List.of("PLAYER: " + "x".repeat(50), "AI_GAME_MASTER: scene"), "c"));
    }
    @Test
    void fixed_rules_precede_five_stable_zones_and_current_evidence_is_last() {
        String legacy = "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[]\nRUNTIME_CONTEXT={}\nACTION=go"
                + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json";
        String prompt = RuntimeGmPromptComposer.compose(legacy, List.of("recent"),
                List.of("current sheet"), Map.of("currentSituation", "here"));

        assertTrue(prompt.startsWith("고정 지침·잠긴 자료\nROLE=RUNTIME_GM\nLOOKUP_ORDER_RULE=rules"));
        int fixed = prompt.indexOf("고정 지침·잠긴 자료");
        int memory = prompt.indexOf("현재 상황 관련 장기 기록");
        int summary = prompt.indexOf("압축된 이전 대화");
        int recent = prompt.indexOf("압축하지 않은 최근 대화");
        int current = prompt.indexOf("최신 캐릭터 시트·Current Situation·이번 턴 근거·플레이어 입력");
        assertTrue(fixed < memory && memory < summary && summary < recent && recent < current);
        assertTrue(prompt.substring(current).contains("current sheet"));
        assertTrue(prompt.substring(current).contains("COMPOSITE_FACT_LOOKUP_RESULTS"));
        assertEquals(1, prompt.split("ACTION=go", -1).length - 1);
    }
    @Test
    void fixed_prefix_is_identical_for_different_turn_content() {
        String first = "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[one]\nRUNTIME_CONTEXT={}\nACTION=go"
                + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json";
        String second = "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[two]\nRUNTIME_CONTEXT={}\nACTION=wait"
                + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json";
        String one = RuntimeGmPromptComposer.compose(first, List.of(), List.of("sheet one"), Map.of(), 1000);
        String two = RuntimeGmPromptComposer.compose(second, List.of(), List.of("sheet two"), Map.of(), 1000);
        assertEquals(one.substring(0, one.indexOf("현재 상황 관련 장기 기록")),
                two.substring(0, two.indexOf("현재 상황 관련 장기 기록")));
    }
}
