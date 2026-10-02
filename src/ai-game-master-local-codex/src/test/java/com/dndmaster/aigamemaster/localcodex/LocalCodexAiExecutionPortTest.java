package com.dndmaster.aigamemaster.localcodex;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCodexAiExecutionPortTest {
    @TempDir Path tempDir;

    @Test
    void doesNotPassJsonNullAsAnOutputSchema() throws Exception {
        Path fakeCodex = tempDir.resolve("fake-codex");
        Path capturedArguments = tempDir.resolve("arguments.txt");
        Files.writeString(fakeCodex, "#!/bin/sh\n"
                + "printf '%s\\n' \"$@\" > '" + capturedArguments + "'\n"
                + "output=''\nprevious=''\n"
                + "for arg in \"$@\"; do\n"
                + "  if [ \"$previous\" = '--output-last-message' ]; then output=\"$arg\"; fi\n"
                + "  previous=\"$arg\"\n"
                + "done\n"
                + "cat >/dev/null\nprintf 'OK' > \"$output\"\n");
        assertThat(fakeCodex.toFile().setExecutable(true)).isTrue();

        var port = new LocalCodexAiExecutionPort(fakeCodex.toString(), tempDir, Duration.ofSeconds(5), new ObjectMapper());
        var result = port.execute(new com.dndmaster.aigamemaster.application.ai.AiExecutionRequest(
                UUID.randomUUID(), "request-null-schema", "operation", "prompt", "gpt-5.6-luna", "medium", "json",
                com.fasterxml.jackson.databind.node.NullNode.getInstance(), "", null));

        assertThat(result).isInstanceOf(AiExecutionSuccess.class);
        assertThat(((AiExecutionSuccess) result).finalText()).isEqualTo("OK");
        assertThat(Files.readString(capturedArguments)).doesNotContain("--output-schema");
    }
}
