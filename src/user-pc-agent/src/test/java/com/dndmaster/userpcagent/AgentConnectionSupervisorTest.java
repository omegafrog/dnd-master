package com.dndmaster.userpcagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentConnectionSupervisorTest {
    @Test
    void reconnectsAfterHandshakeFailureAndUnexpectedWebSocketClosure() {
        AtomicInteger sessions = new AtomicInteger();
        AtomicInteger sleeps = new AtomicInteger();
        AtomicBoolean stopping = new AtomicBoolean();
        List<AgentConnectionSupervisor.Retry> retries = new ArrayList<>();

        AgentConnectionSupervisor.run(
                () -> {
                    int attempt = sessions.incrementAndGet();
                    return new AgentConnectionSupervisor.Session() {
                        @Override
                        public void connectAndWait() throws Exception {
                            if (attempt == 1) throw new IOException("relay unavailable");
                            // A normal return represents the relay closing the WebSocket.
                        }

                        @Override
                        public void close() {}
                    };
                },
                delay -> {
                    assertTrue(delay.compareTo(Duration.ofSeconds(1)) >= 0);
                    sleeps.incrementAndGet();
                    if (sleeps.get() == 2) stopping.set(true);
                },
                stopping::get,
                retries::add);

        assertEquals(2, sessions.get());
        assertEquals(2, retries.size());
        assertEquals(1, retries.get(0).consecutiveFailures());
        assertEquals(2, retries.get(1).consecutiveFailures());
        assertTrue(retries.get(1).delay().compareTo(retries.get(0).delay()) > 0);
    }

    @Test
    void interruptionStopsRetryLoopAndPreservesThreadInterruptFlag() {
        try {
            AgentConnectionSupervisor.run(
                    () -> new AgentConnectionSupervisor.Session() {
                        @Override
                        public void connectAndWait() throws Exception {
                            throw new IOException("relay unavailable");
                        }

                        @Override
                        public void close() {}
                    },
                    delay -> { throw new InterruptedException("shutdown"); },
                    () -> false,
                    ignored -> {});

            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
