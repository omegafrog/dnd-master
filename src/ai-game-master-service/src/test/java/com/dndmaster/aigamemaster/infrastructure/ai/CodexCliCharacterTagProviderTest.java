package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodexCliCharacterTagProviderTest {
    @Test
    void invokesCodexMiniViaStdinWithReadOnlyEphemeralIsolation() {
        List<String> command = new ArrayList<>();
        String[] prompt = new String[1];
        CodexCliCharacterTagProvider provider = new CodexCliCharacterTagProvider(
                (actualCommand, actualPrompt, timeout) -> {
                    command.addAll(actualCommand);
                    prompt[0] = actualPrompt;
                    return "{\"candidates\":[]}";
                }, "codex", "gpt-5.4-mini", Path.of("/tmp"), Duration.ofSeconds(30));

        assertEquals("{\"candidates\":[]}", provider.complete("operation-1", "secret rulebook excerpt"));
        assertEquals("secret rulebook excerpt", prompt[0]);
        assertEquals(List.of("codex", "exec", "--ephemeral", "--skip-git-repo-check", "--ignore-user-config",
                "--ignore-rules", "--sandbox", "read-only", "--model", "gpt-5.4-mini", "--cd", "/tmp", "-"), command);
    }
}
