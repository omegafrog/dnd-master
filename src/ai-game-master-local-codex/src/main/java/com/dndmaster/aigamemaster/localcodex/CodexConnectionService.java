package com.dndmaster.aigamemaster.localcodex;

import com.dndmaster.aigamemaster.infrastructure.ai.CodexAccountClient;
import com.dndmaster.aigamemaster.infrastructure.ai.LoginWaitResult;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Owns this installation's enabled flag and transient asynchronous login operations. */
public final class CodexConnectionService {
    private final CodexAccountClient account;
    private final InstallationLinkStore links;
    private final Executor executor;
    private final Duration loginTimeout;
    private final Duration pollInterval;
    private final Map<String, MutableOperation> operations = new ConcurrentHashMap<>();
    private String activeOperationId;
    private boolean reauthenticationRequired;

    public CodexConnectionService(CodexAccountClient account, InstallationLinkStore links, Executor executor,
                                  Duration loginTimeout, Duration pollInterval) {
        this.account = account;
        this.links = links;
        this.executor = executor;
        this.loginTimeout = positive(loginTimeout, "loginTimeout");
        this.pollInterval = positive(pollInterval, "pollInterval");
        this.reauthenticationRequired = links.getReauthenticationRequired().orElse(false);
    }

    public synchronized ConnectionStatus getStatus() {
        boolean available = account.isAvailable();
        if (!available) return new ConnectionStatus(ProviderConnectionStatus.CLI_UNAVAILABLE, false, activeOperationId);
        if (activeOperationId != null) return new ConnectionStatus(
                ProviderConnectionStatus.AUTHENTICATING, true, activeOperationId);
        if (reauthenticationRequired) return new ConnectionStatus(
                ProviderConnectionStatus.REAUTH_REQUIRED, true, null);
        Optional<Boolean> enabled = links.getEnabled();
        if (enabled.equals(Optional.of(false))) return new ConnectionStatus(ProviderConnectionStatus.DISCONNECTED, true, activeOperationId);
        try {
            if (account.isAuthenticated()) {
                if (enabled.isEmpty()) links.setEnabled(true);
                return new ConnectionStatus(ProviderConnectionStatus.CONNECTED, true, activeOperationId);
            }
        } catch (RuntimeException failure) {
            return new ConnectionStatus(enabled.orElse(false) ? ProviderConnectionStatus.REAUTH_REQUIRED
                    : ProviderConnectionStatus.AUTH_REQUIRED, true, activeOperationId);
        }
        return new ConnectionStatus(enabled.orElse(false) ? ProviderConnectionStatus.REAUTH_REQUIRED
                : ProviderConnectionStatus.AUTH_REQUIRED, true, activeOperationId);
    }

    /** Checks whether an explicitly enabled installation may start one Codex AI request. */
    public synchronized ConnectionStatus getExecutionStatus() {
        if (!account.isAvailable()) return new ConnectionStatus(ProviderConnectionStatus.CLI_UNAVAILABLE, false, activeOperationId);
        if (activeOperationId != null) return new ConnectionStatus(
                ProviderConnectionStatus.AUTHENTICATING, true, activeOperationId);
        if (reauthenticationRequired) return new ConnectionStatus(
                ProviderConnectionStatus.REAUTH_REQUIRED, true, null);
        Optional<Boolean> enabled = links.getEnabled();
        if (!enabled.orElse(false)) return new ConnectionStatus(
                enabled.isEmpty() ? ProviderConnectionStatus.AUTH_REQUIRED : ProviderConnectionStatus.DISCONNECTED,
                true, null);
        try {
            return new ConnectionStatus(account.isAuthenticated() ? ProviderConnectionStatus.CONNECTED
                    : ProviderConnectionStatus.REAUTH_REQUIRED, true, null);
        } catch (RuntimeException failure) {
            return new ConnectionStatus(ProviderConnectionStatus.REAUTH_REQUIRED, true, null);
        }
    }

    public synchronized ConnectionOperationResult start(String operationId, ConnectionOperationType type) {
        required(operationId, "operationId");
        if (type == null) throw new IllegalArgumentException("connection operation type is required");
        MutableOperation existing = operations.get(operationId);
        if (existing != null) return existing.snapshot();
        if (reauthenticationRequired && type == ConnectionOperationType.CONNECT) {
            return save(operationId, ProviderConnectionStatus.REAUTH_REQUIRED, null,
                    "Codex 계정을 다시 인증해 주세요.");
        }
        if (activeOperationId != null) {
            return new ConnectionOperationResult(operationId, ProviderConnectionStatus.FAILED, null,
                    "다른 계정 연결 작업이 진행 중입니다. 잠시 뒤 상태를 다시 확인해 주세요.");
        }
        if (!account.isAvailable()) return save(operationId, ProviderConnectionStatus.CLI_UNAVAILABLE,
                null, "Codex CLI를 설치한 뒤 다시 시도해 주세요.");
        if (type == ConnectionOperationType.SWITCH_ACCOUNT) {
            links.setEnabled(false);
            reauthenticationRequired = false;
            links.setReauthenticationRequired(false);
        }
        if (type == ConnectionOperationType.CONNECT) {
            try {
                if (account.isAuthenticated()) {
                    links.setEnabled(true);
                    return save(operationId, ProviderConnectionStatus.CONNECTED, null, "기존 Codex 로그인을 연결했습니다.");
                }
            } catch (RuntimeException ignored) {
                return save(operationId, ProviderConnectionStatus.FAILED, null, "Codex 계정 상태를 확인하지 못했습니다. 다시 시도해 주세요.");
            }
        }
        final String authUrl;
        try {
            authUrl = account.startBrowserLogin();
        } catch (RuntimeException failure) {
            return save(operationId, ProviderConnectionStatus.FAILED, null,
                    "Codex 로그인을 시작하지 못했습니다. 다시 시도해 주세요.");
        }
        MutableOperation operation = new MutableOperation(operationId, ProviderConnectionStatus.AUTHENTICATING,
                authUrl, "브라우저에서 Codex 계정 승인을 완료해 주세요.");
        operations.put(operationId, operation);
        activeOperationId = operationId;
        executor.execute(() -> awaitLogin(operation));
        return operation.snapshot();
    }

    public synchronized ConnectionOperationResult getOperation(String operationId) {
        MutableOperation operation = operations.get(operationId);
        return operation == null
                ? new ConnectionOperationResult(operationId, ProviderConnectionStatus.FAILED, null,
                        "연결 작업을 찾을 수 없습니다. 새로 연결을 시작해 주세요.")
                : operation.snapshot();
    }

    public synchronized ConnectionStatus disconnect() {
        MutableOperation active = activeOperationId == null ? null : operations.get(activeOperationId);
        if (active != null && active.status == ProviderConnectionStatus.AUTHENTICATING) {
            active.cancelled = true;
            active.status = ProviderConnectionStatus.CANCELLED;
            active.authUrl = null;
            active.message = "현재 설치의 연결을 해제했습니다.";
            activeOperationId = null;
        }
        links.setEnabled(false);
        links.setReauthenticationRequired(false);
        reauthenticationRequired = false;
        return new ConnectionStatus(ProviderConnectionStatus.DISCONNECTED, account.isAvailable(), null);
    }

    public synchronized void requireReauthentication() {
        reauthenticationRequired = true;
        links.setReauthenticationRequired(true);
    }

    private void awaitLogin(MutableOperation operation) {
        LoginWaitResult result = account.awaitAuthentication(loginTimeout, pollInterval);
        synchronized (this) {
            if (operation.cancelled || !operation.operationId.equals(activeOperationId)) return;
            operation.authUrl = null;
            switch (result) {
                case AUTHENTICATED -> {
                    links.setEnabled(true);
                    reauthenticationRequired = false;
                    links.setReauthenticationRequired(false);
                    operation.status = ProviderConnectionStatus.CONNECTED;
                    operation.message = "Codex 계정 연결을 완료했습니다.";
                }
                case CANCELLED -> {
                    operation.status = links.getEnabled().orElse(false)
                            ? ProviderConnectionStatus.REAUTH_REQUIRED : ProviderConnectionStatus.CANCELLED;
                    operation.message = "Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.";
                }
                default -> {
                    operation.status = links.getEnabled().orElse(false)
                            ? ProviderConnectionStatus.REAUTH_REQUIRED : ProviderConnectionStatus.FAILED;
                    operation.message = "Codex 계정 연결을 완료하지 못했습니다. 다시 시도해 주세요.";
                }
            }
            activeOperationId = null;
        }
    }

    private ConnectionOperationResult save(String id, ProviderConnectionStatus status, String authUrl, String message) {
        MutableOperation operation = new MutableOperation(id, status, authUrl, message);
        operations.put(id, operation);
        return operation.snapshot();
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static final class MutableOperation {
        private final String operationId;
        private volatile ProviderConnectionStatus status;
        private volatile String authUrl;
        private volatile String message;
        private volatile boolean cancelled;
        private MutableOperation(String operationId, ProviderConnectionStatus status, String authUrl, String message) {
            this.operationId = operationId;
            this.status = status;
            this.authUrl = authUrl;
            this.message = message;
        }
        private ConnectionOperationResult snapshot() { return new ConnectionOperationResult(operationId, status, authUrl, message); }
    }
}
