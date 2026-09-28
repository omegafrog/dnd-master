package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import org.junit.jupiter.api.Test;

class RuntimeGmPromptComposerTest {
    @Test
    void includes_only_current_situation_relevant_player_visible_long_term_records() {
        LongTermAdventureFact relevant = new LongTermAdventureFact(AdventureId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                3, "RELATIONSHIP", "성문 경비와 맺은 협력 약속", true, 1);
        LongTermAdventureFact unrelated = new LongTermAdventureFact(AdventureId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                3, "GOAL", "북쪽 탑의 잃어버린 지도", true, 1);
        LongTermAdventureFact undisclosed = new LongTermAdventureFact(AdventureId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                3, "THREAT", "성문 경비의 비밀 배신 계획", false, 1);
        String legacy = "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[]\nRUNTIME_CONTEXT={}\nACTION=go"
                + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json";

        String prompt = RuntimeGmPromptComposer.compose(legacy, List.of(), List.of("current sheet"),
                Map.of("currentSituation", "성문 앞에서 경비에게 약속을 이행해 달라고 요청한다"),
                List.of(relevant, unrelated, undisclosed), 10_000);

        int memory = prompt.indexOf("현재 상황 관련 장기 기록");
        int summary = prompt.indexOf("압축된 이전 대화");
        String memoryZone = prompt.substring(memory, summary);
        assertTrue(memoryZone.contains("성문 경비와 맺은 협력 약속"));
        assertTrue(!memoryZone.contains("북쪽 탑의 잃어버린 지도"));
        assertTrue(!memoryZone.contains("성문 경비의 비밀 배신 계획"));
    }

    @Test
    void keeps_current_situation_records_within_their_dedicated_input_budget() {
        LongTermAdventureFact first = new LongTermAdventureFact(AdventureId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                3, "EVENT", "성문 경비가 약속을 이행했다 ".repeat(6), true, 1);
        LongTermAdventureFact second = new LongTermAdventureFact(AdventureId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                3, "EVENT", "성문 경비가 약속을 이행했다 ".repeat(6), true, 2);
        String legacy = "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[]\nRUNTIME_CONTEXT={}\nACTION=go"
                + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json";

        String prompt = RuntimeGmPromptComposer.compose(legacy, List.of(), List.of(),
                Map.of("currentSituation", "성문 경비에게 약속을 확인한다"), List.of(first, second), 4_000);

        String memory = prompt.substring(prompt.indexOf("현재 상황 관련 장기 기록"), prompt.indexOf("압축된 이전 대화"));
        assertTrue(memory.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 400 + "현재 상황 관련 장기 기록\n".getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertTrue(memory.contains("성문 경비가 약속을 이행했다"));
    }
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
    @Test
    void locked_scenario_model_precedes_changing_turn_material() {
        String legacy = "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[]\nRUNTIME_CONTEXT={}\nACTION=go"
                + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json";
        String prompt = RuntimeGmPromptComposer.compose(legacy, List.of("PLAYER: recent"),
                List.of("current sheet"), Map.of("scenarioContext", "SCENARIO_MODEL={\"location\":\"cellar\"}",
                        "currentSituation", "here"), 1000);
        int memory = prompt.indexOf("현재 상황 관련 장기 기록");
        int recent = prompt.indexOf("압축하지 않은 최근 대화");
        int current = prompt.indexOf("최신 캐릭터 시트·Current Situation·이번 턴 근거·플레이어 입력");
        assertTrue(prompt.indexOf("SCENARIO_MODEL={") < memory);
        assertTrue(prompt.indexOf("SCENARIO_MODEL={") < recent);
        assertTrue(prompt.substring(current).contains("currentSituation"));
        assertTrue(!prompt.substring(current).contains("scenarioContext"));
    }

    @Test
    void current_situation_does_not_change_the_fixed_prefix() {
        String legacy = "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[]\nRUNTIME_CONTEXT={}\nACTION=go"
                + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json";
        String first = RuntimeGmPromptComposer.compose(legacy, List.of(), List.of("sheet"),
                Map.of("scenarioContext", "SCENARIO_MODEL={}", "currentSituation", "cellar"), 1000);
        String second = RuntimeGmPromptComposer.compose(legacy, List.of(), List.of("sheet"),
                Map.of("scenarioContext", "SCENARIO_MODEL={}", "currentSituation", "tower"), 1000);

        int firstChangingZone = first.indexOf("현재 상황 관련 장기 기록");
        int secondChangingZone = second.indexOf("현재 상황 관련 장기 기록");
        assertEquals(first.substring(0, firstChangingZone), second.substring(0, secondChangingZone));
        assertTrue(first.substring(first.indexOf("최신 캐릭터 시트·Current Situation·이번 턴 근거·플레이어 입력"))
                .contains("currentSituation"));
    }
}
