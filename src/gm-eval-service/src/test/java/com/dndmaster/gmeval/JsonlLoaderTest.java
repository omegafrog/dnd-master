package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.gmeval.infrastructure.JsonlEvalDatasetLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JsonlLoaderTest {
    @Test void loadsVersionedCaseAndRejectsWrongVersion() throws Exception {
        Path file = Files.createTempFile("eval", ".jsonl");
        Files.writeString(file, "{\"schemaVersion\":1,\"caseId\":\"x\",\"playerInput\":\"look\",\"context\":{\"worldState\":{},\"playerKnowledge\":[],\"storyStage\":\"start\"},\"hardExpectations\":[],\"rubrics\":[]}\n");
        assertEquals("x", new JsonlEvalDatasetLoader().load(file).getFirst().caseId());
        Files.writeString(file, "{\"schemaVersion\":2,\"caseId\":\"x\"}\n");
        assertThrows(IllegalArgumentException.class, () -> new JsonlEvalDatasetLoader().load(file));
    }
}
