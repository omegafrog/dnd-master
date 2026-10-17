package com.dndmaster.character.application;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Opt-in diagnostics for character resource commands, following the runtime dev_flow format. */
final class CharacterResourceDiagnostics {
    private static final String FLAG = "ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED";

    private CharacterResourceDiagnostics() {}

    static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(FLAG, System.getenv(FLAG)));
    }

    static long begin(Logger logger, String commandId, String resourceId, int inputCount, long expectedVersion) {
        if (!enabled()) return 0L;
        logger.info("dev_flow operation=character_resource outcome=started commandId=" + commandId
                + " resourceId=" + resourceId + " inputCount=" + inputCount + " expectedVersion=" + expectedVersion);
        return System.nanoTime();
    }

    static void complete(Logger logger, long started, String commandId, String branch, long expectedVersion,
            long actualVersion) {
        if (started == 0L) return;
        logger.info("dev_flow operation=character_resource outcome=completed commandId=" + commandId + " branch=" + branch
                + " expectedVersion=" + expectedVersion + " actualVersion=" + actualVersion + " elapsedMs="
                + ((System.nanoTime() - started) / 1_000_000));
    }

    static void fail(Logger logger, long started, String commandId, long expectedVersion, long actualVersion,
            Throwable failure) {
        if (started == 0L) return;
        logger.log(Level.WARNING, "dev_flow operation=character_resource outcome=failed commandId={0} expectedVersion={1} actualVersion={2} failureClass={3} elapsedMs={4}",
                new Object[]{commandId, expectedVersion, actualVersion < 0 ? "unknown" : actualVersion,
                        failure.getClass().getSimpleName(), (System.nanoTime() - started) / 1_000_000});
    }
}
