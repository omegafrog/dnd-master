package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.storyplan.RepairScope;
import com.dndmaster.adventure.application.storyplan.StoryPlanScopedMerger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoryPlanScopedMergerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preserves_provider_changes_outside_the_repair_scope() throws Exception {
        String previous = """
                {"stages":[{"title":"Original","transitionCondition":"Open","clearCondition":"Clear"}]}
                """;
        String repaired = """
                {"stages":[{"title":"Provider rewrite","transitionCondition":"Closed","clearCondition":"Provider rewrite"}]}
                """;
        RepairScope scope = new RepairScope(Set.of("stages[0].transitionCondition"), Set.of(), Set.of());

        JsonNode merged = new StoryPlanScopedMerger().merge(previous, repaired, scope);

        assertEquals("Original", merged.at("/stages/0/title").asText());
        assertEquals("Closed", merged.at("/stages/0/transitionCondition").asText());
        assertEquals("Clear", merged.at("/stages/0/clearCondition").asText());
    }

    @Test
    void merges_wildcard_array_members_without_replacing_the_array() throws Exception {
        String previous = """
                {"stages":[{"evidence":[{"citationKey":"citation-1","quote":"keep"},{"citationKey":"citation-2","quote":"keep"}]}]}
                """;
        String repaired = """
                {"stages":[{"evidence":[{"citationKey":"citation-new","quote":"provider"},{"citationKey":"citation-2","quote":"provider"}]}]}
                """;
        RepairScope scope = new RepairScope(Set.of("stages[0].evidence[*].citationKey"), Set.of(), Set.of());

        JsonNode merged = new StoryPlanScopedMerger().merge(mapper.readTree(previous), mapper.readTree(repaired), scope);

        assertEquals("citation-new", merged.at("/stages/0/evidence/0/citationKey").asText());
        assertEquals("citation-2", merged.at("/stages/0/evidence/1/citationKey").asText());
        assertEquals("keep", merged.at("/stages/0/evidence/0/quote").asText());
    }

    @Test
    void repairs_only_missing_stage_ending_ids() throws Exception {
        String previous = """
                {"stages":[{"title":"Keep title","goal":"Keep goal","combatRequirement":"NONE","evidence":[{"citationKey":"citation-1"}]}]}
                """;
        String repaired = """
                {"stages":[{"title":"Provider rewrite","goal":"Provider rewrite","combatRequirement":"REQUIRED","evidence":[{"citationKey":"citation-999"}],"endingIds":["ending-1"]}]}
                """;
        RepairScope scope = new RepairScope(Set.of("stages[0].endingIds"), Set.of(), Set.of());

        JsonNode merged = new StoryPlanScopedMerger().merge(previous, repaired, scope);

        assertEquals("ending-1", merged.at("/stages/0/endingIds/0").asText());
        assertEquals("Keep title", merged.at("/stages/0/title").asText());
        assertEquals("Keep goal", merged.at("/stages/0/goal").asText());
        assertEquals("NONE", merged.at("/stages/0/combatRequirement").asText());
        assertEquals("citation-1", merged.at("/stages/0/evidence/0/citationKey").asText());
    }
}
