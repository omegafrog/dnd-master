package com.dndmaster.aigamemaster.localcodex;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.dndmaster.aigamemaster.application.ai.AiExecutionUsage;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexAccountClient;
import com.dndmaster.aigamemaster.infrastructure.ai.LoginWaitResult;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InstallationGuardedAiExecutionPortTest {
    @Test
    void rejectsAnUnlinkedInstallationBeforeCallingTheProvider() {
        var account = new FakeAccountClient(true, true);
        var links = new MemoryLinkStore(Optional.empty());
        var connection = connection(account, links);
        var calls = new AtomicInteger();
        AiExecutionPort provider = ignored -> {
            calls.incrementAndGet();
            return new AiExecutionSuccess("unexpected", AiExecutionUsage.unknown());
        };
        var guarded = new InstallationGuardedAiExecutionPort(provider, connection);

        var result = (AiExecutionFailure) guarded.execute(request());

        assertThat(result.reason()).isEqualTo(AiExecutionFailure.Reason.CONNECTION_REQUIRED);
        assertThat(calls).hasValue(0);
    }

    @Test
    void rejectsAReauthenticationRequiredInstallationBeforeCallingTheProvider() {
        var account = new FakeAccountClient(true, true);
        var connection = connection(account, new MemoryLinkStore(Optional.of(true)));
        connection.requireReauthentication();
        var calls = new AtomicInteger();
        var guarded = new InstallationGuardedAiExecutionPort(ignored -> {
            calls.incrementAndGet();
            return new AiExecutionSuccess("unexpected", AiExecutionUsage.unknown());
        }, connection);

        var result = (AiExecutionFailure) guarded.execute(request());

        assertThat(result.reason()).isEqualTo(AiExecutionFailure.Reason.REAUTH_REQUIRED);
        assertThat(calls).hasValue(0);
    }

    @Test
    void forwardsConnectedRequestsAndMarksAuthenticationRejectionForExplicitRecovery() {
        var account = new FakeAccountClient(true, true);
        var links = new MemoryLinkStore(Optional.of(true));
        var connection = connection(account, links);
        var calls = new AtomicInteger();
        AiExecutionPort provider = ignored -> {
            calls.incrementAndGet();
            return new AiExecutionFailure(AiExecutionFailure.Reason.REAUTH_REQUIRED, "safe failure");
        };
        var guarded = new InstallationGuardedAiExecutionPort(provider, connection);

        var result = (AiExecutionFailure) guarded.execute(request());
        var retry = (AiExecutionFailure) guarded.execute(request());

        assertThat(result.reason()).isEqualTo(AiExecutionFailure.Reason.REAUTH_REQUIRED);
        assertThat(retry.reason()).isEqualTo(AiExecutionFailure.Reason.REAUTH_REQUIRED);
        assertThat(calls).hasValue(1);
        assertThat(links.enabled).contains(true);
    }

    @Test
    void serializesDisconnectUntilTheInFlightProviderRequestFinishes() throws Exception {
        var connection = connection(new FakeAccountClient(true, true),
                new MemoryLinkStore(Optional.of(true)));
        var providerEntered = new CountDownLatch(1);
        var finishProvider = new CountDownLatch(1);
        var disconnectStarted = new CountDownLatch(1);
        var guarded = new InstallationGuardedAiExecutionPort(ignored -> {
            providerEntered.countDown();
            try {
                if (!finishProvider.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test interrupted", interrupted);
            }
            return new AiExecutionSuccess("finished", AiExecutionUsage.unknown());
        }, connection);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var execution = workers.submit(() -> guarded.execute(request()));
            assertThat(providerEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var disconnect = workers.submit(() -> {
                disconnectStarted.countDown();
                return connection.disconnect();
            });
            assertThat(disconnectStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(disconnect.isDone()).isFalse();

            finishProvider.countDown();

            assertThat(execution.get(2, TimeUnit.SECONDS)).isInstanceOf(AiExecutionSuccess.class);
            assertThat(disconnect.get(2, TimeUnit.SECONDS).status()).isEqualTo(ProviderConnectionStatus.DISCONNECTED);
        } finally {
            finishProvider.countDown();
            workers.shutdownNow();
        }
    }

    private static AiExecutionRequest request() {
        return new AiExecutionRequest(UUID.randomUUID(), "request-356", "operation-356", "conversation context",
                "gpt-5.6-luna", "medium", "TEXT", null, "");
    }

    private static CodexConnectionService connection(FakeAccountClient account, MemoryLinkStore links) {
        return new CodexConnectionService(account, links, ignored -> { }, Duration.ofMillis(5), Duration.ofMillis(1));
    }

    private static final class FakeAccountClient implements CodexAccountClient {
        private final boolean available;
        private final boolean authenticated;
        private FakeAccountClient(boolean available, boolean authenticated) {
            this.available = available;
            this.authenticated = authenticated;
        }
        @Override public boolean isAvailable() { return available; }
        @Override public boolean isAuthenticated() { return authenticated; }
        @Override public String startBrowserLogin() { return "https://auth.example/approve"; }
        @Override public LoginWaitResult awaitAuthentication(Duration timeout, Duration pollInterval) {
            return LoginWaitResult.AUTHENTICATED;
        }
    }

    private static final class MemoryLinkStore implements InstallationLinkStore {
        private Optional<Boolean> enabled;
        private Optional<Boolean> reauthenticationRequired = Optional.empty();
        private MemoryLinkStore(Optional<Boolean> enabled) { this.enabled = enabled; }
        @Override public Optional<Boolean> getEnabled() { return enabled; }
        @Override public void setEnabled(boolean value) { enabled = Optional.of(value); }
        @Override public Optional<Boolean> getReauthenticationRequired() { return reauthenticationRequired; }
        @Override public void setReauthenticationRequired(boolean value) { reauthenticationRequired = Optional.of(value); }
    }
}
