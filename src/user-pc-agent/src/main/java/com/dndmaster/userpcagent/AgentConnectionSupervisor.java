package com.dndmaster.userpcagent;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Recreates the authenticated agent session after every WebSocket disconnect. */
final class AgentConnectionSupervisor {
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private AgentConnectionSupervisor() {}

    static void run(
            SessionFactory sessionFactory,
            Sleeper sleeper,
            BooleanSupplier stopping,
            Consumer<Retry> retryLogger) {
        Objects.requireNonNull(sessionFactory, "sessionFactory must not be null");
        Objects.requireNonNull(sleeper, "sleeper must not be null");
        Objects.requireNonNull(stopping, "stopping must not be null");
        Objects.requireNonNull(retryLogger, "retryLogger must not be null");

        int consecutiveFailures = 0;
        while (!stopping.getAsBoolean()) {
            Exception failure = null;
            try (Session session = sessionFactory.create()) {
                session.connectAndWait();
                if (!stopping.getAsBoolean()) {
                    failure = new IllegalStateException("agent WebSocket session ended");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                failure = exception;
            }
            if (failure != null) {
                consecutiveFailures++;
                Duration delay = retryDelay(consecutiveFailures);
                retryLogger.accept(new Retry(consecutiveFailures, delay, failure));
                if (stopping.getAsBoolean()) return;
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static Duration retryDelay(int failures) {
        long baseMillis = INITIAL_RETRY_DELAY.toMillis();
        int shift = Math.min(Math.max(failures - 1, 0), 20);
        long cappedMillis = Math.min(baseMillis * (1L << shift), MAX_RETRY_DELAY.toMillis());
        long jitterCeiling = Math.min(cappedMillis / 5, MAX_RETRY_DELAY.toMillis() - cappedMillis);
        long jitterMillis = ThreadLocalRandom.current().nextLong(Math.max(1, jitterCeiling + 1));
        return Duration.ofMillis(cappedMillis + jitterMillis);
    }

    @FunctionalInterface
    interface SessionFactory {
        Session create() throws Exception;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    interface Session extends AutoCloseable {
        void connectAndWait() throws Exception;

        @Override
        void close() throws Exception;
    }

    record Retry(int consecutiveFailures, Duration delay, Exception failure) {}
}
