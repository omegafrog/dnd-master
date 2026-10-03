package com.dndmaster.aigamemaster.localcodex;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.aigamemaster.infrastructure.ai.CodexAccountClient;
import com.dndmaster.aigamemaster.infrastructure.ai.LoginWaitResult;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class CodexConnectionServiceTest {
    @Test
    void missingExecutableReturnsInstallStateWithoutStartingLogin() {
        var account = new FakeAccountClient(false, false);
        var links = new MemoryLinkStore();
        var service = service(account, links, new ArrayDeque<>());

        var operation = service.start("missing-cli", ConnectionOperationType.CONNECT);

        assertThat(operation.status()).isEqualTo(ProviderConnectionStatus.CLI_UNAVAILABLE);
        assertThat(operation.authUrl()).isNull();
        assertThat(account.loginStarts).isZero();
        assertThat(links.enabled).isEmpty();
    }

    @Test
    void firstConnectReusesAnExistingCodexLogin() {
        var account = new FakeAccountClient(true, true);
        var links = new MemoryLinkStore();
        var service = service(account, links, new ArrayDeque<>());

        var operation = service.start("reuse-login", ConnectionOperationType.CONNECT);

        assertThat(operation.status()).isEqualTo(ProviderConnectionStatus.CONNECTED);
        assertThat(operation.authUrl()).isNull();
        assertThat(account.loginStarts).isZero();
        assertThat(links.enabled).contains(true);
    }

    @Test
    void statusReusesAnExistingCodexLoginOnFirstInstallation() {
        var account = new FakeAccountClient(true, true);
        var links = new MemoryLinkStore();
        var service = service(account, links, new ArrayDeque<>());

        var status = service.getStatus();

        assertThat(status.status()).isEqualTo(ProviderConnectionStatus.CONNECTED);
        assertThat(links.enabled).contains(true);
    }

    @Test
    void explicitAccountSwitchStartsLoginAndExposesOnlyTheApprovalUrlWhilePending() {
        var account = new FakeAccountClient(true, true);
        var links = new MemoryLinkStore();
        var work = new ArrayDeque<Runnable>();
        var service = service(account, links, work);

        var started = service.start("switch-account", ConnectionOperationType.SWITCH_ACCOUNT);

        assertThat(started.status()).isEqualTo(ProviderConnectionStatus.AUTHENTICATING);
        assertThat(started.authUrl()).isEqualTo("https://auth.example/approve");
        assertThat(account.loginStarts).isEqualTo(1);
        assertThat(links.enabled).contains(false);
        assertThat(service.getStatus().status()).isEqualTo(ProviderConnectionStatus.AUTHENTICATING);
        assertThat(service.getStatus().operationId()).isEqualTo("switch-account");

        work.remove().run();

        assertThat(service.getOperation("switch-account").status()).isEqualTo(ProviderConnectionStatus.CONNECTED);
        assertThat(links.enabled).contains(true);
    }

    @Test
    void failedAccountSwitchLeavesThisInstallationUnlinked() {
        var account = new FakeAccountClient(true, true);
        account.completion = LoginWaitResult.FAILED;
        var links = new MemoryLinkStore();
        links.enabled = Optional.of(true);
        var work = new ArrayDeque<Runnable>();
        var service = service(account, links, work);

        service.start("switch-fails", ConnectionOperationType.SWITCH_ACCOUNT);
        assertThat(links.enabled).contains(false);

        work.remove().run();

        assertThat(service.getOperation("switch-fails").status()).isEqualTo(ProviderConnectionStatus.FAILED);
        assertThat(links.enabled).contains(false);
        assertThat(account.authenticated).isTrue();
    }

    @Test
    void cancelledAccountSwitchLeavesThisInstallationUnlinked() {
        var account = new FakeAccountClient(true, true);
        account.completion = LoginWaitResult.CANCELLED;
        var links = new MemoryLinkStore();
        links.enabled = Optional.of(true);
        var work = new ArrayDeque<Runnable>();
        var service = service(account, links, work);

        service.start("switch-cancelled", ConnectionOperationType.SWITCH_ACCOUNT);
        work.remove().run();

        assertThat(service.getOperation("switch-cancelled").status()).isEqualTo(ProviderConnectionStatus.CANCELLED);
        assertThat(links.enabled).contains(false);
        assertThat(account.authenticated).isTrue();
    }

    @Test
    void disconnectDisablesOnlyThisInstallationAndPreservesCodexLogin() {
        var account = new FakeAccountClient(true, true);
        var links = new MemoryLinkStore();
        links.enabled = Optional.of(true);
        var service = service(account, links, new ArrayDeque<>());

        var status = service.disconnect();

        assertThat(status.status()).isEqualTo(ProviderConnectionStatus.DISCONNECTED);
        assertThat(links.enabled).contains(false);
        assertThat(account.authenticated).isTrue();
        assertThat(account.loginStarts).isZero();
    }

    private static CodexConnectionService service(FakeAccountClient account, MemoryLinkStore links, Queue<Runnable> work) {
        return new CodexConnectionService(account, links, work::add,
                Duration.ofMillis(5), Duration.ofMillis(1));
    }

    private static final class FakeAccountClient implements CodexAccountClient {
        private final boolean available;
        private boolean authenticated;
        private int loginStarts;
        private LoginWaitResult completion = LoginWaitResult.AUTHENTICATED;

        private FakeAccountClient(boolean available, boolean authenticated) {
            this.available = available;
            this.authenticated = authenticated;
        }

        @Override public boolean isAvailable() { return available; }
        @Override public boolean isAuthenticated() { return authenticated; }
        @Override public String startBrowserLogin() { loginStarts++; return "https://auth.example/approve"; }
        @Override public LoginWaitResult awaitAuthentication(Duration waitTimeout, Duration pollInterval) {
            if (completion == LoginWaitResult.AUTHENTICATED) authenticated = true;
            return completion;
        }
    }

    private static final class MemoryLinkStore implements InstallationLinkStore {
        private Optional<Boolean> enabled = Optional.empty();
        @Override public Optional<Boolean> getEnabled() { return enabled; }
        @Override public void setEnabled(boolean value) { enabled = Optional.of(value); }
    }
}
