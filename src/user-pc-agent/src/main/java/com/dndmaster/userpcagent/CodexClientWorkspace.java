package com.dndmaster.userpcagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CodexClientWorkspace {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodexClientWorkspace.class);
    private static final String SKILL = "codex-workspace/skills/scenario-element-lookup/SKILL.md";

    private CodexClientWorkspace() {}

    static void install(Path directory) {
        long startedAt = System.nanoTime();
        Path target = directory.resolve(".agents/skills/scenario-element-lookup/SKILL.md");
        if (diagnosticsEnabled()) LOGGER.info("dev_codex_workspace stage=install outcome=started directory={} target={}", directory, target);
        try {
            Files.createDirectories(target.getParent());
            try (var input = CodexClientWorkspace.class.getClassLoader().getResourceAsStream(SKILL)) {
                if (input == null) throw new IOException("bundled scenario lookup skill is missing");
                Path temporary = Files.createTempFile(target.getParent(), "skill-", ".tmp");
                try {
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                    try {
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            if (diagnosticsEnabled()) LOGGER.info("dev_codex_workspace stage=install outcome=completed directory={} skillBytes={} elapsedMs={}",
                    directory, Files.size(target), (System.nanoTime() - startedAt) / 1_000_000);
        } catch (IOException | RuntimeException failure) {
            if (diagnosticsEnabled()) LOGGER.warn("dev_codex_workspace stage=install outcome=failed directory={} failureType={} elapsedMs={} recoveryAction=abort_client_start",
                    directory, failure.getClass().getSimpleName(), (System.nanoTime() - startedAt) / 1_000_000);
            throw new IllegalStateException("Codex 전용 작업 디렉터리에 시나리오 조회 스킬을 설치하지 못했습니다.", failure);
        }
    }

    private static boolean diagnosticsEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")));
    }
}
