package com.dndmaster.aigamemaster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.aigamemaster.application.intent.IntentClassificationOutput;
import com.dndmaster.aigamemaster.application.intent.QueryIntent;
import org.junit.jupiter.api.Test;

class IntentClassificationTest {
    @Test
    void maps_known_labels_and_falls_back_to_unknown() {
        assertEquals(QueryIntent.RULE, IntentClassificationOutput.fromModelText("RULE").intent());
        assertEquals(QueryIntent.STORY, IntentClassificationOutput.fromModelText("story").intent());
        assertEquals(QueryIntent.MIXED, IntentClassificationOutput.fromModelText(" mixed ").intent());
        assertEquals(QueryIntent.UNKNOWN, IntentClassificationOutput.fromModelText("something else").intent());
        assertEquals(QueryIntent.UNKNOWN, IntentClassificationOutput.fromModelText(null).intent());
    }
}
