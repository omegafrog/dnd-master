package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local Codex app-server JSON-RPC client. One app-server process is shared per local configuration. */
public final class CodexAppServerClient implements AutoCloseable, CodexAccountClient {
    private static final ConcurrentHashMap<String, CodexAppServerClient> SHARED = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(CodexAppServerClient.class);
    private static final Pattern MAX_OUTPUT_TOKENS_VALUE = Pattern.compile("(?i)max_output_tokens\\D{0,16}(\\d{1,10})");
    private static final Pattern ALLOWED_OUTPUT_TOKENS_VALUE = Pattern.compile("(?i)(?:maximum|max|limit)\\D{0,24}(\\d{1,10})\\s*(?:output\\s*)?tokens?");
    private static final Pattern ACTUAL_OUTPUT_TOKENS_VALUE = Pattern.compile("(?i)(?:actual|generated|used|produced)\\D{0,24}(\\d{1,10})\\s*(?:output\\s*)?tokens?");

    public static CodexAppServerClient shared(String executable, Path workDirectory, Duration timeout, ObjectMapper mapper) {
        return shared(executable, workDirectory, timeout, mapper, List.of());
    }

    public static CodexAppServerClient shared(String executable, Path workDirectory, Duration timeout,
            ObjectMapper mapper, List<String> startupConfigOverrides) {
        List<String> overrides = startupConfigOverrides == null ? List.of() : List.copyOf(startupConfigOverrides);
        String key = executable + "|" + workDirectory.toAbsolutePath().normalize() + "|" + overrides;
        return SHARED.computeIfAbsent(key, ignored -> new CodexAppServerClient(
                executable, workDirectory, timeout, mapper, overrides));
    }

    private final String executable;
    private final Path workDirectory;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final List<String> startupConfigOverrides;
    private volatile Process process;
    private volatile Thread descendantMonitor;
    private final java.util.Set<ProcessHandle> ownedDescendants = ConcurrentHashMap.newKeySet();
    private BufferedWriter input;
    private BufferedReader output;
    private long requestId;
    private LoginWaitResult loginWaitResult = LoginWaitResult.PENDING;
    private final java.util.concurrent.atomic.AtomicBoolean aborted = new java.util.concurrent.atomic.AtomicBoolean();

    private CodexAppServerClient(String executable, Path workDirectory, Duration timeout, ObjectMapper mapper,
            List<String> startupConfigOverrides) {
        this.executable = require(executable, "Codex executable");
        this.workDirectory = workDirectory.toAbsolutePath().normalize();
        this.timeout = timeout;
        this.mapper = mapper;
        this.startupConfigOverrides = List.copyOf(startupConfigOverrides);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Codex timeout must be positive");
    }

    public synchronized String complete(String operationId, String prompt, String requestedModel) {
        return complete(operationId, prompt, requestedModel, "unknown");
    }

    public synchronized String complete(String operationId, String prompt, String requestedModel, String reasoning) {
        return complete(operationId, prompt, requestedModel, reasoning, null);
    }
    public synchronized String complete(String operationId, String prompt, String requestedModel, String reasoning, JsonNode outputSchema) {
        return complete(operationId, prompt, requestedModel, reasoning, outputSchema, "");
    }

    public synchronized String complete(String operationId, String prompt, String requestedModel, String reasoning,
            JsonNode outputSchema, String imageDataUri) {
        return completeWithUsage(operationId, prompt, requestedModel, reasoning, outputSchema, imageDataUri).finalText();
    }

    public record Completion(String finalText, AiExecutionUsage usage) {}

    public synchronized Completion completeWithUsage(String operationId, String prompt, String requestedModel, String reasoning,
            JsonNode outputSchema, String imageDataUri) {
        return completeWithUsage(operationId, prompt, requestedModel, reasoning, outputSchema, imageDataUri, List.of());
    }

    public synchronized Completion completeWithUsage(String operationId, String prompt, String requestedModel, String reasoning,
            JsonNode outputSchema, String imageDataUri, List<AiExecutionRequest.Subtask> subtasks) {
        if (operationId == null || operationId.isBlank() || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("operation id and prompt required");
        }
        long startedAt = System.nanoTime();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        String model = requestedModel == null || requestedModel.isBlank() ? "default" : requestedModel;
        LOGGER.info("ai_agent_call_started provider=codex operationId={} stage={} model={} reasoning={} promptChars={} estimatedPromptTokens={} turnId={} turnCompletedReceived={} timeout={}",
                safe(operationId), stage(operationId), safe(model), safe(reasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), "unknown", false, false);
        String threadId = "unknown";
        boolean turnCompletedReceived = false;
        java.util.Map<String, Long> collaborationItemsStartedAt = new java.util.HashMap<>();
        java.util.concurrent.atomic.AtomicBoolean delegationStarted = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean delegationInvalid = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<JsonNode> delegatedResults = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<AiExecutionUsage> delegatedUsage = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            ensureStarted(deadlineNanos);
            ObjectNode threadParams = mapper.createObjectNode();
            threadParams.put("cwd", workDirectory.toString());
            threadParams.put("approvalPolicy", "never");
            threadParams.put("sandbox", "read-only");
            threadParams.put("ephemeral", true);
            putModel(threadParams, requestedModel);
            if (!subtasks.isEmpty()) threadParams.set("dynamicTools", delegationTool(subtasks));
            JsonNode thread = request("thread/start", threadParams, deadlineNanos).path("thread");
            threadId = thread.path("id").asText("");
            if (threadId.isBlank()) throw new IllegalStateException("Codex app-server did not return a thread id");

            ObjectNode turnParams = mapper.createObjectNode();
            turnParams.put("threadId", threadId);
            ArrayNode inputItems = turnParams.putArray("input");
            ObjectNode text = inputItems.addObject();
            text.put("type", "text");
            text.put("text", prompt);
            if (imageDataUri != null && !imageDataUri.isBlank()) {
                ObjectNode image = inputItems.addObject();
                image.put("type", "image");
                image.put("url", imageDataUri);
            }
            putModel(turnParams, requestedModel);
            turnParams.put("approvalPolicy", "never");
            turnParams.putObject("sandboxPolicy").put("type", "readOnly");
            if (List.of("low", "medium", "high", "ultra").contains(reasoning)) turnParams.put("effort", reasoning);
            if (outputSchema != null && !outputSchema.isNull()) turnParams.set("outputSchema", outputSchema);
            String turnId = request("turn/start", turnParams, deadlineNanos).path("turn").path("id").asText("");

            StringBuilder response = new StringBuilder();
            java.util.Map<String, StringBuilder> agentMessageDeltas = new java.util.HashMap<>();
            int agentMessagesCompleted = 0;
            String selectedMessageId = "legacy_delta_buffer";
            String selectedMessagePhase = "unknown";
            boolean finalAnswerSelected = false;
            AiExecutionUsage reportedUsage = AiExecutionUsage.unknown();
            Long reportedReasoningTokens = null;
            String lastMethod = "turn/start";
            while (true) {
                JsonNode message;
                try {
                    message = readMessageUntil(deadlineNanos);
                } catch (ProviderTimeoutException exception) {
                    CodexTurnTimeoutException timeoutFailure = exception instanceof CodexTurnTimeoutException structured
                            ? structured
                            : timeoutFailure(operationId, threadId, model, reasoning, startedAt, lastMethod, exception);
                    LOGGER.error("ai_agent_call_timeout provider=codex operationId={} stage={} model={} reasoning={} promptChars={} estimatedPromptTokens={} responseChars={} turnId={} turnCompletedReceived={} timeout=true lastMethod={} timeoutMs={}",
                            safe(operationId), stage(operationId), safe(model), safe(reasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), response.length(), safe(threadId), turnCompletedReceived, safe(lastMethod), timeout.toMillis());
                    throw timeoutFailure;
                }
                String method = message.path("method").asText("");
                if (!method.isBlank()) lastMethod = method;
                JsonNode params = message.path("params");
                if ("item/started".equals(method) || "item/completed".equals(method)) {
                    logCollaborationItem(operationId, method, params, collaborationItemsStartedAt);
                }
                if ("item/tool/call".equals(method)) {
                    ObjectNode result = mapper.createObjectNode();
                    RuntimeException delegationFailure = null;
                    try {
                        if (subtasks.isEmpty() || !"delegate_stage_tasks".equals(params.path("tool").asText())
                                || !delegationStarted.compareAndSet(false, true)) throw new IllegalArgumentException("unexpected or repeated stage delegation call");
                        JsonNode outputs = executeSubtasks(operationId, params.path("arguments"), subtasks, requestedModel,
                                reasoning, deadlineNanos, delegatedUsage);
                        delegatedResults.set(outputs);
                        result.put("success", true);
                        result.putArray("contentItems").addObject().put("type", "inputText").put("text", outputs.toString());
                    } catch (RuntimeException failed) {
                        delegationFailure = failed;
                        delegationInvalid.set(true);
                        result.put("success", false);
                        result.putArray("contentItems").addObject().put("type", "inputText").put("text", "Stage delegation failed; do not create or replace stage results.");
                        if (runtimeDiagnosticsEnabled()) LOGGER.warn("dev_gm_stage_delegation outcome=failed operationId={} expectedStageIds={} actualStageIds={} failureClass={} elapsedMs={} recoveryAction=reject_parent_turn",
                                safe(operationId), subtasks.stream().map(AiExecutionRequest.Subtask::stageId).toList(),
                                textValues(params.path("arguments").path("stageIds")), failed.getClass().getSimpleName(), elapsedMillis(startedAt));
                    }
                    ObjectNode rpcResponse = mapper.createObjectNode().put("id", message.path("id").asLong());
                    rpcResponse.set("result", result);
                    send(rpcResponse);
                    if (delegationFailure != null) {
                        throw new IllegalStateException("stage delegation failed", delegationFailure);
                    }
                    continue;
                }
                if (!method.isBlank() && !"item/agentMessage/delta".equals(method)) {
                    LOGGER.info("ai_agent_event provider=codex operationId={} stage={} method={} turnId={}", safe(operationId), stage(operationId), safe(method), safe(threadId));
                }
                if ("item/agentMessage/delta".equals(method)) {
                    String delta = params.path("delta").asText("");
                    String itemId = params.path("itemId").asText("");
                    if (!itemId.isBlank()) agentMessageDeltas.computeIfAbsent(itemId, ignored -> new StringBuilder()).append(delta);
                    if (!finalAnswerSelected) response.append(delta);
                }
                if ("item/completed".equals(method)) {
                    JsonNode item = params.path("item");
                    if ("agentMessage".equals(item.path("type").asText())) {
                        agentMessagesCompleted++;
                        if (runtimeDiagnosticsEnabled()) logAgentMessageCompletion(operationId, item,
                                agentMessageDeltas.get(item.path("id").asText("")), agentMessagesCompleted);
                        String itemId = item.path("id").asText("");
                        String phase = item.path("phase").asText("");
                        String completedText = item.path("text").asText("");
                        StringBuilder itemDelta = agentMessageDeltas.get(itemId);
                        if (completedText.isBlank() && itemDelta != null) completedText = itemDelta.toString();
                        if ("final_answer".equals(phase)) {
                            replaceResponse(response, completedText);
                            selectedMessageId = itemId;
                            selectedMessagePhase = phase;
                            finalAnswerSelected = true;
                        } else if (!finalAnswerSelected && "partial_answer".equals(phase)) {
                            replaceResponse(response, completedText);
                            selectedMessageId = itemId;
                            selectedMessagePhase = phase;
                        } else if (!finalAnswerSelected && "commentary".equals(phase)) {
                            response.setLength(0);
                            selectedMessageId = itemId;
                            selectedMessagePhase = phase;
                        } else if (!finalAnswerSelected && phase.isBlank() && !completedText.isBlank()) {
                            replaceResponse(response, completedText);
                            selectedMessageId = itemId;
                            selectedMessagePhase = "unknown";
                        }
                    }
                }
                if ("thread/tokenUsage/updated".equals(method)
                        && threadId.equals(params.path("threadId").asText())
                        && turnId.equals(params.path("turnId").asText())) {
                    JsonNode total = params.path("tokenUsage").path("total");
                    reportedUsage = new AiExecutionUsage(reportedCount(total, "inputTokens"),
                            reportedCount(total, "cachedInputTokens"), reportedCount(total, "outputTokens"));
                    Long reasoningCount = reportedCount(params.path("tokenUsage").path("total"), "reasoningTokens");
                    if (reasoningCount == null)
                        reasoningCount = reportedCount(params.path("tokenUsage").path("outputTokensDetails"), "reasoningTokens");
                    if (reasoningCount != null) reportedReasoningTokens = reasoningCount;
                }
                if ("turn/completed".equals(method)) {
                    turnCompletedReceived = true;
                    JsonNode turnError = params.path("turn").path("error");
                    if (!turnError.isMissingNode() && !turnError.isNull()) {
                        throw turnFailure(operationId, threadId, method, turnError, reportedUsage, reportedReasoningTokens);
                    }
                    break;
                }
                if ("turn/failed".equals(method) || "turn/aborted".equals(method)) {
                    throw turnFailure(operationId, threadId, method, params.path("error"), reportedUsage, reportedReasoningTokens);
                }
                if (message.has("error")) throw rpcError(message);
            }
            if (runtimeDiagnosticsEnabled()) logSelectedAgentResponse(operationId, selectedMessageId,
                    selectedMessagePhase, agentMessagesCompleted, response.toString());
            if (response.isEmpty()) throw new ProviderMalformedResponseException("Codex app-server response missing text");
            String result = response.toString().trim();
            if (!subtasks.isEmpty()) validateDelegatedParent(result, outputSchema, subtasks, delegatedResults.get(), delegationStarted.get(), delegationInvalid.get());
            AiExecutionUsage combinedUsage = addUsage(reportedUsage, delegatedUsage.get());
            LOGGER.info("ai_agent_call_completed provider=codex operationId={} stage={} durationMs={} promptChars={} estimatedPromptTokens={} responseChars={} model={} reasoning={} turnId={} turnCompletedReceived=true timeout=false",
                    safe(operationId), stage(operationId), elapsedMillis(startedAt), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), result.length(), safe(model), safe(reasoning), safe(threadId));
            return new Completion(result, combinedUsage);
        } catch (InterruptedException exception) {
            if (aborted.get()) {
                Thread.currentThread().interrupt();
                LOGGER.info("ai_agent_call_cancelled provider=codex operationId={} stage={} durationMs={} turnId={} cancellationSource=parent_delegation",
                        safe(operationId), stage(operationId), elapsedMillis(startedAt), safe(threadId));
                throw new java.util.concurrent.CancellationException("Codex app-server child was cancelled by its parent");
            }
            Thread.currentThread().interrupt();
            LOGGER.warn("ai_agent_call_interrupted provider=codex operationId={} stage={} durationMs={} turnId={} turnCompletedReceived={} timeout=true", safe(operationId), stage(operationId), elapsedMillis(startedAt), safe(threadId), turnCompletedReceived);
            throw new ProviderTimeoutException(exception);
        } catch (IOException exception) {
            if (aborted.get()) {
                LOGGER.info("ai_agent_call_cancelled provider=codex operationId={} stage={} durationMs={} turnId={} cancellationSource=parent_delegation",
                        safe(operationId), stage(operationId), elapsedMillis(startedAt), safe(threadId));
                throw new java.util.concurrent.CancellationException("Codex app-server child was cancelled by its parent");
            }
            LOGGER.error("ai_agent_call_failed provider=codex operationId={} durationMs={} errorType={} message={}", operationId,
                    elapsedMillis(startedAt), exception.getClass().getSimpleName(), safeMessage(exception));
            throw new ProviderTimeoutException(exception);
        } catch (ProviderTimeoutException exception) {
            CodexTurnTimeoutException timeoutFailure = exception instanceof CodexTurnTimeoutException structured
                    ? structured
                    : timeoutFailure(operationId, threadId, model, reasoning, startedAt, "request", exception);
            sendTurnCancel(threadId, operationId);
            LOGGER.error("ai_agent_call_timeout provider=codex operationId={} stage={} model={} reasoning={} promptChars={} estimatedPromptTokens={} responseChars={} turnId={} turnCompletedReceived={} timeout=true phase=app-server-request timeoutMs={}",
                    safe(operationId), stage(operationId), safe(model), safe(reasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), 0, safe(threadId), turnCompletedReceived, timeout.toMillis());
            throw timeoutFailure;
        } catch (RuntimeException exception) {
            LOGGER.error("ai_agent_call_failed provider=codex operationId={} durationMs={} errorType={} message={}", operationId,
                    elapsedMillis(startedAt), exception.getClass().getSimpleName(), safeMessage(exception));
            throw exception;
        } finally {
            closeRequestProcess(operationId, startedAt);
        }
    }

    private void logCollaborationItem(String operationId, String method, JsonNode params,
            java.util.Map<String, Long> startedAtByItem) {
        JsonNode item = params.path("item");
        if (!"collabAgentToolCall".equals(item.path("type").asText())) return;
        String itemId = item.path("id").asText("unknown");
        String tool = item.path("tool").asText("unknown");
        List<String> receiverThreadIds = new ArrayList<>();
        JsonNode receivers = item.path("receiverThreadIds");
        if (receivers.isArray()) receivers.forEach(receiver -> receiverThreadIds.add(receiver.asText()));
        List<String> agentStates = new ArrayList<>();
        item.path("agentsStates").fields().forEachRemaining(entry ->
                agentStates.add(entry.getKey() + ":" + entry.getValue().path("status").asText("unknown")));
        if ("item/started".equals(method)) {
            startedAtByItem.put(itemId, System.nanoTime());
            if (runtimeDiagnosticsEnabled()) {
                LOGGER.info("dev_gm_subagent_event provider=codex operationId={} event=tool_call_started itemId={} tool={} receiverThreadIds={} status={} agentStates={}",
                        safe(operationId), safe(itemId), safe(tool), receiverThreadIds, safe(item.path("status").asText("unknown")), agentStates);
            }
        } else {
            Long startedAt = startedAtByItem.remove(itemId);
            if (runtimeDiagnosticsEnabled()) {
                LOGGER.info("dev_gm_subagent_event provider=codex operationId={} event=tool_call_completed itemId={} tool={} receiverThreadIds={} status={} agentStates={} elapsedMs={}",
                        safe(operationId), safe(itemId), safe(tool), receiverThreadIds, safe(item.path("status").asText("unknown")),
                        agentStates, startedAt == null ? -1 : elapsedMillis(startedAt));
            }
        }
    }

    private ArrayNode delegationTool(List<AiExecutionRequest.Subtask> subtasks) {
        ArrayNode tools = mapper.createArrayNode();
        ObjectNode tool = tools.addObject().put("type", "function").put("name", "delegate_stage_tasks")
                .put("description", "Run every fixed stage task in parallel and return their schema-validated results. Call exactly once.");
        ObjectNode schema = tool.putObject("inputSchema").put("type", "object").put("additionalProperties", false);
        schema.putArray("required").add("stageIds");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode ids = properties.putObject("stageIds").put("type", "array").put("uniqueItems", true);
        ids.put("minItems", subtasks.size()).put("maxItems", subtasks.size());
        ObjectNode item = ids.putObject("items").put("type", "string");
        ArrayNode allowed = item.putArray("enum");
        subtasks.forEach(task -> allowed.add(task.stageId()));
        return tools;
    }

    private JsonNode executeSubtasks(String operationId, JsonNode arguments,
            List<AiExecutionRequest.Subtask> subtasks, String model, String reasoning, long deadlineNanos,
            java.util.concurrent.atomic.AtomicReference<AiExecutionUsage> usageTotal) {
        List<String> expectedIds = subtasks.stream().map(AiExecutionRequest.Subtask::stageId).toList();
        List<String> requestedIds = new ArrayList<>();
        JsonNode ids = arguments.path("stageIds");
        if (!arguments.isObject() || arguments.size() != 1 || !ids.isArray()) throw new IllegalArgumentException("delegation arguments do not match schema");
        ids.forEach(id -> {
            if (!id.isTextual()) throw new IllegalArgumentException("delegation stageIds must be strings");
            requestedIds.add(id.textValue());
        });
        if (requestedIds.size() != expectedIds.size() || !new java.util.HashSet<>(requestedIds).equals(new java.util.HashSet<>(expectedIds)))
            throw new IllegalArgumentException("delegation stageIds do not match the fixed task set");

        long started = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, subtasks.size()), Thread.ofVirtual().factory());
        List<CodexAppServerClient> children = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<Future<Map.Entry<String, StageExecution>>> futures = new ArrayList<>();
        CompletionService<Map.Entry<String, StageExecution>> completedTasks = new ExecutorCompletionService<>(executor);
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        if (runtimeDiagnosticsEnabled()) LOGGER.info("dev_gm_stage_delegation outcome=started operationId={} expectedTaskCount={} stageIds={} maxParallel={}",
                safe(operationId), subtasks.size(), expectedIds, Math.min(4, subtasks.size()));
        try {
            for (AiExecutionRequest.Subtask task : subtasks) {
                futures.add(completedTasks.submit(() -> {
                    if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new IllegalStateException("stage task was cancelled before start");
                    long remaining = deadlineNanos - System.nanoTime();
                    if (remaining <= 0) throw new ProviderTimeoutException(new TimeoutException("parent turn deadline exceeded before stage start"));
                    CodexAppServerClient child = new CodexAppServerClient(executable, workDirectory,
                            Duration.ofNanos(remaining), mapper, startupConfigOverrides);
                    children.add(child);
                    if (cancelled.get()) child.abort();
                    long taskStarted = System.nanoTime();
                    if (runtimeDiagnosticsEnabled()) LOGGER.info("dev_gm_stage_delegation outcome=child_started operationId={} stageId={} schemaChars={} remainingDeadlineMs={}",
                            safe(operationId), safe(task.stageId()), task.outputSchema().toString().length(), TimeUnit.NANOSECONDS.toMillis(deadlineNanos - taskStarted));
                    try {
                        Completion completion = child.completeWithUsage(operationId + ":stage-child:" + task.stageId(), task.prompt(),
                                model, reasoning, task.outputSchema(), "");
                        String text = completion.finalText();
                        JsonNode output = parseUniqueJson(text);
                        var violations = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                                .getSchema(task.outputSchema()).validate(output);
                        if (!violations.isEmpty()) {
                            if (runtimeDiagnosticsEnabled()) LOGGER.warn("dev_gm_stage_delegation outcome=child_schema_invalid operationId={} stageId={} expectedFields={} actualType={} actualFields={} violationCount={} violationPaths={} recoveryAction=cancel_siblings",
                                    safe(operationId), safe(task.stageId()), textValues(task.outputSchema().path("required")),
                                    output.getNodeType(), nodeFields(output), violations.size(),
                                    violations.stream().limit(20).map(v -> v.getInstanceLocation().toString()).toList());
                            throw new IllegalArgumentException("child output does not match stage outputSchema");
                        }
                        if (System.nanoTime() >= deadlineNanos) throw new ProviderTimeoutException(new TimeoutException("parent deadline elapsed during stage validation"));
                        if (runtimeDiagnosticsEnabled()) LOGGER.info("dev_gm_stage_delegation outcome=child_completed operationId={} stageId={} schemaChars={} outputType={} outputFields={} outputChars={} elapsedMs={}",
                                safe(operationId), safe(task.stageId()), task.outputSchema().toString().length(), output.getNodeType(), nodeFields(output), text.length(), elapsedMillis(taskStarted));
                        return Map.entry(task.stageId(), new StageExecution(output, completion.usage()));
                    } catch (Throwable failure) {
                        if (runtimeDiagnosticsEnabled()) LOGGER.warn("dev_gm_stage_delegation outcome=child_failed operationId={} stageId={} schemaChars={} failureClass={} elapsedMs={} recoveryAction=cancel_siblings",
                                safe(operationId), safe(task.stageId()), task.outputSchema().toString().length(), failure.getClass().getSimpleName(), elapsedMillis(taskStarted));
                        if (runtimeDiagnosticsEnabled()) LOGGER.warn("dev_gm_stage_delegation outcome=child_failure_cause_chain operationId={} stageId={} causeTypes={}",
                                safe(operationId), safe(task.stageId()), failureTypeChain(failure));
                        if (failure instanceof RuntimeException runtime) throw runtime;
                        if (failure instanceof Error error) throw error;
                        throw new IllegalStateException("child stage execution failed", failure);
                    } finally {
                        child.close();
                    }
                }));
            }
            ObjectNode outputs = mapper.createObjectNode();
            for (int i = 0; i < subtasks.size(); i++) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException("parent turn deadline exceeded while waiting for stage tasks");
                Future<Map.Entry<String, StageExecution>> future = completedTasks.poll(remaining, TimeUnit.NANOSECONDS);
                if (future == null) throw new TimeoutException("parent turn deadline exceeded while waiting for stage tasks");
                Map.Entry<String, StageExecution> entry = future.get(remaining, TimeUnit.NANOSECONDS);
                if (outputs.has(entry.getKey())) throw new IllegalArgumentException("duplicate child stageId");
                outputs.set(entry.getKey(), entry.getValue().output());
                usageTotal.set(addUsage(usageTotal.get(), entry.getValue().usage()));
            }
            if (System.nanoTime() >= deadlineNanos) throw new TimeoutException("parent deadline elapsed before delegated results were returned");
            if (runtimeDiagnosticsEnabled()) LOGGER.info("dev_gm_stage_delegation outcome=completed operationId={} taskCount={} stageIds={} elapsedMs={}",
                    safe(operationId), outputs.size(), expectedIds, elapsedMillis(started));
            return outputs;
        } catch (Exception failure) {
            cancelled.set(true);
            children.forEach(CodexAppServerClient::abort);
            if (runtimeDiagnosticsEnabled()) LOGGER.warn("dev_gm_stage_delegation outcome=execution_failed operationId={} failureClass={} causeTypes={} elapsedMs={} recoveryAction=cancel_children",
                    safe(operationId), failure.getClass().getSimpleName(), failureTypeChain(failure), elapsedMillis(started));
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("stage delegation failed", failure);
        } finally {
            executor.shutdownNow();
        }
    }

    void validateDelegatedParent(String text, JsonNode outputSchema,
            List<AiExecutionRequest.Subtask> subtasks, JsonNode delegated, boolean delegationStarted, boolean delegationInvalid) {
        if (!delegationStarted || delegated == null || delegationInvalid) throw new IllegalArgumentException("parent did not complete exactly one valid stage delegation");
        JsonNode parent = parseUniqueJson(text);
        var violations = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(outputSchema).validate(parent);
        if (!violations.isEmpty()) {
            if (runtimeDiagnosticsEnabled()) LOGGER.warn("dev_gm_stage_delegation outcome=parent_schema_invalid expectedStageIds={} actualStageIds={} expectedFields={} actualFields={} violationCount={} violationPaths={} recoveryAction=reject_parent_turn",
                    subtasks.stream().map(AiExecutionRequest.Subtask::stageId).toList(), nodeFields(parent.path("stages")),
                    textValues(outputSchema.path("required")), nodeFields(parent), violations.size(),
                    violations.stream().limit(20).map(v -> v.getInstanceLocation().toString()).toList());
            throw new IllegalArgumentException("parent response does not match final outputSchema");
        }
        JsonNode stages = parent.path("stages");
        if (!stages.equals(delegated) || stages.size() != subtasks.size()
                || subtasks.stream().anyMatch(task -> !stages.has(task.stageId()))) {
            if (runtimeDiagnosticsEnabled()) LOGGER.warn("dev_gm_stage_delegation outcome=parent_child_mismatch expectedStageIds={} actualStageIds={} expectedChildCount={} actualStageCount={} recoveryAction=reject_parent_turn",
                    subtasks.stream().map(AiExecutionRequest.Subtask::stageId).toList(), nodeFields(stages),
                    subtasks.size(), stages.isObject() ? stages.size() : 0);
            throw new IllegalArgumentException("parent stage results differ from validated child results");
        }
    }

    private static List<String> nodeFields(JsonNode node) {
        if (!node.isObject()) return List.of();
        List<String> result = new ArrayList<>();
        node.fieldNames().forEachRemaining(field -> result.add(safe(field)));
        return result;
    }

    private static List<String> failureTypeChain(Throwable failure) {
        List<String> types = new ArrayList<>();
        for (Throwable current = failure; current != null && types.size() < 8; current = current.getCause()) {
            types.add(current.getClass().getSimpleName());
        }
        return types;
    }

    private JsonNode parseUniqueJson(String text) {
        try {
            JsonNode result = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(text);
            if (result == null || !result.isObject()) throw new IllegalArgumentException("stage output must be a JSON object");
            return result;
        } catch (IOException failure) {
            if (runtimeDiagnosticsEnabled()) LOGGER.warn("dev_gm_stage_delegation outcome=json_parse_failed chars={} trimmedChars={} startsWithObject={} endsWithObject={} controlChars={} parserFailure={}",
                    text.length(), text.trim().length(), text.trim().startsWith("{"), text.trim().endsWith("}"),
                    text.chars().filter(character -> Character.isISOControl(character) && !Character.isWhitespace(character)).count(),
                    failure.getClass().getSimpleName());
            throw new IllegalArgumentException("stage output is invalid JSON", failure);
        }
    }

    private static List<String> textValues(JsonNode values) {
        if (!values.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.isTextual() ? safe(value.textValue()) : "<non-string>"));
        return result;
    }

    private static AiExecutionUsage addUsage(AiExecutionUsage first, AiExecutionUsage second) {
        if (first == null) return second;
        if (second == null) return first;
        return new AiExecutionUsage(addCount(first.inputTokens(), second.inputTokens()),
                addCount(first.cachedInputTokens(), second.cachedInputTokens()),
                addCount(first.outputTokens(), second.outputTokens()));
    }

    private static Long addCount(Long first, Long second) {
        return first == null || second == null ? null : first + second;
    }

    private record StageExecution(JsonNode output, AiExecutionUsage usage) {}

    private static boolean runtimeDiagnosticsEnabled() {
        return "true".equalsIgnoreCase(System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"));
    }

    private static Long reportedCount(JsonNode usage, String field) {
        JsonNode value = usage.path(field);
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0
                ? value.longValue() : null;
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private CodexTurnTimeoutException timeoutFailure(String operationId, String turnId, String model,
            String reasoning, long startedAt, String lastEvent, Throwable cause) {
        return new CodexTurnTimeoutException(operationId, turnId, stage(operationId), model, reasoning,
                elapsedMillis(startedAt), timeout.toMillis(), lastEvent, cause);
    }

    /** Sends cancellation best-effort without waiting for the app-server acknowledgement. */
    private void sendTurnCancel(String turnId, String operationId) {
        if (turnId == null || turnId.isBlank() || "unknown".equals(turnId)) return;
        try {
            if (process == null || !process.isAlive() || input == null) return;
            ObjectNode params = mapper.createObjectNode().put("turnId", turnId);
            ObjectNode cancel = mapper.createObjectNode().put("method", "turn/cancel");
            cancel.set("params", params);
            send(cancel);
            LOGGER.info("ai_agent_turn_cancel_sent provider=codex operationId={} turnId={}", safe(operationId), safe(turnId));
        } catch (Exception cancellationFailure) {
            LOGGER.warn("ai_agent_turn_cancel_failed provider=codex operationId={} turnId={} error={}",
                    safe(operationId), safe(turnId), safeMessage(cancellationFailure));
        }
    }

    static String stage(String operationId) {
        if (operationId == null) return "unknown";
        if (operationId.contains(":resolution-candidate-repair")) return "resolution-candidate-repair";
        if (operationId.contains(":resolution-candidates")) return "resolution-candidate-extraction";
        if (operationId.endsWith("-verification")) return "scenario-runtime-verification";
        if (operationId.endsWith("-execution-projection")) return "scenario-runtime-projection";
        if (operationId.endsWith("-projection-repair")) return "scenario-runtime-projection-repair";
        return "scenario-runtime-completion";
    }

    private static String safe(String value) { return AiCallObservability.safe(value); }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return "<none>";
        String normalized = message.replaceAll("[\\r\\n]+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 240));
    }

    private static String compact(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "{}";
        String value = node.toString();
        return value.length() <= 2000 ? value : value.substring(0, 2000) + "…";
    }

    public synchronized boolean isAuthenticated() {
        try {
            long deadlineNanos = System.nanoTime() + timeout.toNanos();
            ensureStarted(deadlineNanos);
            JsonNode account = request("account/read", mapper.createObjectNode(), deadlineNanos).path("account");
            return !account.isMissingNode()
                    && !account.isNull()
                    && !account.path("type").asText("").isBlank();
        } catch (IOException exception) {
            closeProcess();
            throw new IllegalStateException("Codex app-server account check failed");
        } catch (ProviderTimeoutException exception) {
            closeProcess();
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        }
    }

    public synchronized String startBrowserLogin() {
        try {
            long deadlineNanos = System.nanoTime() + timeout.toNanos();
            ensureStarted(deadlineNanos);
            loginWaitResult = LoginWaitResult.PENDING;
            ObjectNode params = mapper.createObjectNode().put("type", "chatgpt");
            String authUrl = request("account/login/start", params, deadlineNanos).path("authUrl").asText("");
            if (authUrl.isBlank()) throw new IllegalStateException("Codex app-server did not return an OAuth URL");
            return authUrl;
        } catch (IOException exception) {
            closeProcess();
            throw new IllegalStateException("Codex OAuth could not be started");
        } catch (ProviderTimeoutException exception) {
            closeProcess();
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        }
    }

    @Override
    public synchronized LoginWaitResult awaitAuthentication(Duration waitTimeout, Duration pollInterval) {
        long deadline = System.nanoTime() + waitTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (loginWaitResult == LoginWaitResult.CANCELLED || loginWaitResult == LoginWaitResult.FAILED) {
                return loginWaitResult;
            }
            try {
                if (isAuthenticated()) return LoginWaitResult.AUTHENTICATED;
            } catch (RuntimeException failure) {
                return LoginWaitResult.FAILED;
            }
            if (loginWaitResult != LoginWaitResult.PENDING) return loginWaitResult;
            try {
                long sleepMillis = Math.max(1, Math.min(pollInterval.toMillis(),
                        TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
                Thread.sleep(sleepMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return LoginWaitResult.CANCELLED;
            }
        }
        return loginWaitResult == LoginWaitResult.PENDING ? LoginWaitResult.TIMED_OUT : loginWaitResult;
    }

    /** Checks the configured executable without starting Codex or an app-server process. */
    public boolean isAvailable() {
        Path configured = Path.of(executable);
        if (configured.getNameCount() > 1 || configured.isAbsolute()) {
            return Files.isRegularFile(configured) && Files.isExecutable(configured);
        }
        String path = System.getenv().getOrDefault("PATH", "");
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) continue;
            Path candidate = Path.of(directory).resolve(configured);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return true;
        }
        return false;
    }

    private void ensureStarted(long deadlineNanos) throws IOException, InterruptedException {
        if (aborted.get()) throw new IllegalStateException("Codex app-server child was cancelled");
        if (process != null && process.isAlive()) return;
        if (!isAvailable()) throw new IllegalStateException("Codex CLI is unavailable; install Codex and retry");
        closeProcess();
        List<String> command = new ArrayList<>(List.of(executable, "app-server", "--stdio"));
        for (String override : startupConfigOverrides) command.addAll(List.of("-c", override));
        process = new ProcessBuilder(command)
                .directory(workDirectory.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        ownedDescendants.clear();
        startDescendantMonitor(process);
        if (aborted.get()) {
            closeProcess();
            throw new IllegalStateException("Codex app-server child was cancelled");
        }
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        ObjectNode capabilities = mapper.createObjectNode();
        capabilities.putArray("optOutNotificationMethods").add("item/agentMessage/delta");
            capabilities.put("experimentalApi", true);
        ObjectNode initialize = mapper.createObjectNode();
        initialize.putObject("clientInfo").put("name", "dnd-master").put("title", "D&D Master").put("version", "0.1.0");
        initialize.set("capabilities", capabilities);
        request("initialize", initialize, deadlineNanos);
        send(mapper.createObjectNode().put("method", "initialized"));
    }

    private JsonNode request(String method, JsonNode params, long deadlineNanos) throws IOException, InterruptedException {
        long id = ++requestId;
        ObjectNode request = mapper.createObjectNode();
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        send(request);
        while (true) {
            JsonNode message = readMessageUntil(deadlineNanos);
            observeLoginEvent(message);
            if (message.path("id").asLong(Long.MIN_VALUE) == id) {
                if (message.has("error")) throw rpcError(message);
                return message.path("result");
            }
            handleServerRequest(message);
        }
    }

    private void observeLoginEvent(JsonNode message) {
        switch (message.path("method").asText("")) {
            case "account/login/completed", "account/updated" -> loginWaitResult = LoginWaitResult.AUTHENTICATED;
            case "account/login/cancelled", "account/login/canceled" -> loginWaitResult = LoginWaitResult.CANCELLED;
            case "account/login/failed" -> loginWaitResult = LoginWaitResult.FAILED;
            default -> { }
        }
    }

    private JsonNode readMessage() throws IOException {
        String line = output.readLine();
        if (line == null) throw new IOException("Codex app-server closed stdout");
        return mapper.readTree(line);
    }

    private JsonNode readMessageUntil(long deadlineNanos) throws IOException, InterruptedException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) throw new ProviderTimeoutException(new TimeoutException("Codex turn event deadline exceeded"));
        CompletableFuture<JsonNode> read = CompletableFuture.supplyAsync(() -> {
            try {
                return readMessage();
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        try {
            return read.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            read.cancel(true);
            throw new ProviderTimeoutException(exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime && runtime.getCause() instanceof IOException io) throw io;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Codex app-server reader failed", cause);
        }
    }

    private void send(JsonNode message) throws IOException {
        input.write(mapper.writeValueAsString(message));
        input.newLine();
        input.flush();
    }

    private void handleServerRequest(JsonNode message) throws IOException {
        if (!message.has("id") || !message.has("method")) return;
        ObjectNode response = mapper.createObjectNode();
        response.put("id", message.path("id").asLong());
        response.putObject("error").put("code", -32000).put("message", "D&D Master does not support interactive app-server requests");
        send(response);
    }

    private static void replaceResponse(StringBuilder response, String text) {
        response.setLength(0);
        if (text != null && !text.isBlank()) response.append(text);
    }

    private void logSelectedAgentResponse(String operationId, String itemId, String phase, int messageCount, String response) {
        JsonMessageShape shape = jsonMessageShape(response);
        String strategy = "final_answer".equals(phase) ? "latest_final_answer_item"
                : "partial_answer".equals(phase) ? "latest_partial_answer_fallback"
                : "legacy_delta_buffer".equals(itemId) ? "legacy_delta_buffer"
                : "latest_phase_unknown_item";
        LOGGER.info("dev_codex_agent_response outcome=selected operationId={} itemId={} phase={} selectionStrategy={} messageCount={} responseChars={} responseFingerprint={} jsonObject={} fields={} parseFailureClass={} parseLine={} parseColumn={}",
                safe(operationId), safe(itemId), safe(phase), strategy, messageCount, response.length(), fingerprint(response),
                shape.jsonObject(), shape.fields(), shape.failureClass(), shape.line(), shape.column());
    }

    private void logAgentMessageCompletion(String operationId, JsonNode item, StringBuilder delta, int messageCount) {
        String text = item.path("text").asText("");
        String deltaText = delta == null ? "" : delta.toString();
        JsonMessageShape deltaShape = jsonMessageShape(deltaText);
        JsonMessageShape completedShape = jsonMessageShape(text);
        LOGGER.info("dev_codex_agent_message outcome=completed operationId={} itemId={} phase={} messageCount={} deltaChars={} deltaFingerprint={} deltaJsonObject={} deltaParseFailureClass={} deltaParseLine={} deltaParseColumn={} completedChars={} completedFingerprint={} completedJsonObject={} completedFields={} completedParseFailureClass={} completedParseLine={} completedParseColumn={}",
                safe(operationId), safe(item.path("id").asText("unknown")), safe(item.path("phase").asText("unknown")),
                messageCount, deltaText.length(), fingerprint(deltaText), deltaShape.jsonObject(), deltaShape.failureClass(),
                deltaShape.line(), deltaShape.column(), text.length(), fingerprint(text), completedShape.jsonObject(),
                completedShape.fields(), completedShape.failureClass(), completedShape.line(), completedShape.column());
    }

    private JsonMessageShape jsonMessageShape(String text) {
        try {
            JsonNode parsed = mapper.readTree(text);
            if (parsed != null && parsed.isObject()) {
                List<String> fields = new ArrayList<>();
                parsed.fieldNames().forEachRemaining(field -> fields.add(safe(field)));
                return new JsonMessageShape(true, List.copyOf(fields), "none", 0, 0);
            }
            return new JsonMessageShape(false, List.of(), "not_object", 0, 0);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalidJson) {
            var location = invalidJson.getLocation();
            return new JsonMessageShape(false, List.of(), invalidJson.getClass().getSimpleName(),
                    location == null ? 0 : location.getLineNr(), location == null ? 0 : location.getColumnNr());
        }
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record JsonMessageShape(boolean jsonObject, List<String> fields, String failureClass, int line, int column) {}

    private static void putModel(ObjectNode params, String model) {
        if (model != null && !model.isBlank() && !"codex-cli".equalsIgnoreCase(model)) params.put("model", model);
    }

    private static IllegalStateException rpcError(JsonNode message) {
        if (isAuthenticationRejection(message.path("error"))) return new CodexAuthenticationRejectedException();
        return new IllegalStateException("Codex app-server request failed");
    }

    private static RuntimeException turnFailure(String operationId, String turnId, String method, JsonNode error,
            AiExecutionUsage usage, Long reasoningTokens) {
        if (isAuthenticationRejection(error)) return new CodexAuthenticationRejectedException();
        JsonNode detail = error.path("codexErrorInfo");
        if (!detail.isObject()) detail = error.path("additionalDetails");
        String code = safeErrorIdentifier(error.path("code").asText(detail.path("code").asText("")));
        String message = error.path("message").asText("");
        String messageClass = classifyProviderMessage(message);
        String category = !Set.of("OTHER", "EMPTY").contains(messageClass) ? messageClass : categorizeTurnFailure(code);
        if (runtimeDiagnosticsEnabled()) {
            LOGGER.warn("dev_codex_turn_failure expectedOutcome=completed actualOutcome=failed operationId={} stage={} turnId={} method={} errorCode={} errorCategory={} messageClass={} messageChars={} errorFields={} detailFields={} codexErrorInfoFields={} additionalDetailsFields={} misalignmentFields={} safeProviderDetails={} outputLimitKind={} requestedOutputTokenLimit={} allowedOutputTokenLimit={} actualOutputTokens={} usageOutputTokens={} usageReasoningTokens={} recoveryAction=propagate_failure",
                    safe(operationId), stage(operationId), safe(turnId), safe(method), code, category,
                    messageClass, message.length(), nodeFields(error),
                    nodeFields(detail), nodeFields(error.path("codexErrorInfo")),
                    nodeFields(error.path("additionalDetails")), nodeFields(error.path("misalignment")),
                    safeProviderDetails(error), category.equals("OUTPUT_LIMIT") ? outputLimitKind(message, error) : "NOT_APPLICABLE",
                    category.equals("OUTPUT_LIMIT") ? first(numericMatch(MAX_OUTPUT_TOKENS_VALUE, message), safeNumericDetail(error,
                            Set.of("max_output_tokens", "maxOutputTokens"), false)) : null,
                    category.equals("OUTPUT_LIMIT") ? first(numericMatch(ALLOWED_OUTPUT_TOKENS_VALUE, message), safeNumericDetail(error,
                            Set.of("allowed_output_tokens", "allowedOutputTokens", "maxAllowedOutputTokens", "limit"), true)) : null,
                    category.equals("OUTPUT_LIMIT") ? first(numericMatch(ACTUAL_OUTPUT_TOKENS_VALUE, message), safeNumericDetail(error,
                            Set.of("actual_output_tokens", "actualOutputTokens", "output_tokens", "outputTokens"), false)) : null,
                    usage == null ? null : usage.outputTokens(), reasoningTokens);
        }
        LOGGER.warn("ai_agent_turn_failed provider=codex operationId={} stage={} turnId={} method={} errorCode={} errorCategory={} errorFields={} detailFields={}",
                safe(operationId), stage(operationId), safe(turnId), safe(method), code, category,
                nodeFields(error), nodeFields(detail));
        return new CodexTurnFailedException(turnId, method, code, category, "Codex app-server execution failed");
    }

    private static String outputLimitKind(String message, JsonNode error) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("max_output_tokens")) return "MAX_OUTPUT_TOKENS";
        for (String field : List.of("kind", "type", "code")) {
            String value = error.path("codexErrorInfo").path(field).asText("").toLowerCase(java.util.Locale.ROOT);
            if (Set.of("output_limit", "output_limit_exceeded", "max_output_tokens", "length").contains(value)) return value.toUpperCase(java.util.Locale.ROOT);
            value = error.path("additionalDetails").path(field).asText("").toLowerCase(java.util.Locale.ROOT);
            if (Set.of("output_limit", "output_limit_exceeded", "max_output_tokens", "length").contains(value)) return value.toUpperCase(java.util.Locale.ROOT);
        }
        return "OTHER_OUTPUT_LIMIT";
    }

    private static Long numericMatch(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) return null;
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Long safeNumericDetail(JsonNode node, Set<String> allowedFields, boolean requireOutputPath) {
        return safeNumericDetail(node, allowedFields, requireOutputPath, "", 0);
    }

    private static Long safeNumericDetail(JsonNode node, Set<String> allowedFields, boolean requireOutputPath,
            String path, int depth) {
        if (!node.isObject() || depth > 3) return null;
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String fieldPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            JsonNode value = entry.getValue();
            if (allowedFields.contains(entry.getKey()) && (!requireOutputPath || fieldPath.toLowerCase(java.util.Locale.ROOT).contains("output"))) {
                Long count = reportedCountValue(value);
                if (count != null) return count;
            }
            Long nested = safeNumericDetail(value, allowedFields, requireOutputPath, fieldPath, depth + 1);
            if (nested != null) return nested;
        }
        return null;
    }

    private static Long reportedCountValue(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null;
    }

    private static Long first(Long preferred, Long fallback) {
        return preferred == null ? fallback : preferred;
    }

    private static String classifyProviderMessage(String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("max_output_tokens")) return "OUTPUT_LIMIT";
        if ((lower.contains("tool") || lower.contains("function")) && lower.contains("schema")) return "TOOL_SCHEMA_REJECTED";
        if (lower.contains("schema") || lower.contains("response_format") || lower.contains("structured output")) return "SCHEMA_REJECTED";
        if (lower.contains("invalid model") || lower.contains("model not found") || lower.contains("model is unavailable")) return "MODEL_UNAVAILABLE";
        if (lower.contains("invalid request") || lower.contains("invalid argument") || lower.contains("bad request")) return "INVALID_REQUEST";
        if (lower.contains("context length") || lower.contains("token limit") || lower.contains("too many tokens")) return "CONTEXT_LIMIT";
        if (lower.contains("rate limit") || lower.contains("quota") || lower.contains("usage limit")) return "USAGE_LIMIT";
        if (lower.contains("auth") || lower.contains("credential")) return "AUTHENTICATION";
        if (lower.contains("temporar") || lower.contains("unavailable") || lower.contains("overloaded")) return "UPSTREAM_UNAVAILABLE";
        if (lower.contains("refus")) return "REFUSAL";
        return message.isBlank() ? "EMPTY" : "OTHER";
    }

    private static List<String> safeProviderDetails(JsonNode error) {
        List<String> values = new ArrayList<>();
        collectSafeProviderDetails(error, "", 0, values);
        return values.stream().limit(20).toList();
    }

    private static void collectSafeProviderDetails(JsonNode node, String parent, int depth, List<String> values) {
        if (!node.isObject() || depth > 2 || values.size() >= 20) return;
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            String path = parent.isEmpty() ? key : parent + "." + key;
            if (Set.of("code", "type", "kind", "status", "httpStatus", "param", "field", "path", "keyword", "property").contains(key)) {
                String text = value.isTextual() ? value.textValue() : value.isNumber() ? value.asText() : "";
                if (!text.isEmpty() && text.length() <= 96 && text.matches("[\\p{L}\\p{N}_.$/:#\\[\\]-]+")) {
                    values.add(path + "=" + text);
                }
            }
            if (value.isObject()) collectSafeProviderDetails(value, path, depth + 1, values);
        });
    }

    private static String safeErrorIdentifier(String value) {
        if (value == null || value.length() > 64 || !value.matches("[A-Za-z0-9_.-]{1,64}")) return "unclassified";
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String categorizeTurnFailure(String code) {
        if (code.contains("max_output_tokens")) return "OUTPUT_LIMIT";
        if (code.contains("schema") || code.contains("format") || code.contains("structured_output")) {
            return "SCHEMA_REJECTED";
        }
        if (code.contains("usage") || code.contains("quota") || code.contains("rate_limit")
                || code.contains("context_length") || code.contains("token_limit")) {
            return "USAGE_OR_CAPACITY";
        }
        return "OTHER";
    }

    private static boolean isAuthenticationRejection(JsonNode error) {
        String codexErrorInfo = error.path("codexErrorInfo").asText("").toLowerCase(java.util.Locale.ROOT);
        String code = error.path("code").asText("").toLowerCase(java.util.Locale.ROOT);
        String message = error.path("message").asText("").toLowerCase(java.util.Locale.ROOT);
        return codexErrorInfo.contains("unauthorized") || codexErrorInfo.contains("authentication")
                || code.contains("unauthorized") || code.contains("authentication")
                || message.contains("login required") || message.contains("not authenticated")
                || message.contains("authentication required") || message.contains("credentials expired");
    }

    private record ProcessCleanup(long pid, int expectedChildCount, long actualAliveChildCount,
            boolean parentAlive, boolean forciblyTerminated) {}

    private ProcessCleanup closeProcess() {
        long cleanupStartedAt = System.nanoTime();
        BufferedWriter currentInput = input;
        BufferedReader currentOutput = output;
        Process active = process;
        List<ProcessHandle> children = collectDescendants(active);
        Thread monitor = descendantMonitor;
        descendantMonitor = null;
        if (monitor != null && monitor != Thread.currentThread()) {
            monitor.interrupt();
            try { monitor.join(100); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        }
        children = mergeDescendants(children, ownedDescendants);
        ownedDescendants.clear();
        input = null;
        output = null;
        process = null;
        ProcessCleanup cleanup = terminateProcess(active, children);
        Thread.startVirtualThread(() -> {
            try { if (currentInput != null) currentInput.close(); } catch (IOException ignored) { }
            try { if (currentOutput != null) currentOutput.close(); } catch (IOException ignored) { }
        });
        if (runtimeDiagnosticsEnabled()) LOGGER.info(
                "dev_codex_app_server_process outcome=closed pid={} expectedChildCount={} actualAliveChildCount={} parentAlive={} forciblyTerminated={} elapsedMs={} gate=shared_process_close",
                cleanup.pid(), cleanup.expectedChildCount(), cleanup.actualAliveChildCount(), cleanup.parentAlive(), cleanup.forciblyTerminated(), elapsedMillis(cleanupStartedAt));
        return cleanup;
    }

    private void closeRequestProcess(String operationId, long startedAt) {
        long cleanupStartedAt = System.nanoTime();
        ProcessCleanup cleanup = closeProcess();
        if (runtimeDiagnosticsEnabled()) LOGGER.info(
                "dev_codex_app_server_process outcome=request_closed operationId={} expectedChildCount={} actualAliveChildCount={} parentAlive={} elapsedMs={} cleanupElapsedMs={} branch=request_finally",
                safe(operationId), cleanup.expectedChildCount(), cleanup.actualAliveChildCount(), cleanup.parentAlive(), elapsedMillis(startedAt), elapsedMillis(cleanupStartedAt));
    }

    private static ProcessCleanup terminateProcess(Process active, List<ProcessHandle> children) {
        if (active == null) return new ProcessCleanup(0, 0, 0, false, false);
        long pid = active.pid();
        children.forEach(ProcessHandle::destroy);
        active.destroy();
        awaitChildren(children, 500);
        boolean forced = false;
        for (ProcessHandle child : children) {
            if (child.isAlive()) { child.destroyForcibly(); forced = true; }
        }
        if (active.isAlive()) { active.destroyForcibly(); forced = true; }
        awaitChildren(children, 500);
        try {
            if (!active.waitFor(500, TimeUnit.MILLISECONDS) && active.isAlive()) {
                active.destroyForcibly();
                forced = true;
                active.waitFor(500, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            active.destroyForcibly();
            forced = true;
            Thread.currentThread().interrupt();
        }
        awaitChildren(children, 500);
        return new ProcessCleanup(pid, children.size(), children.stream().filter(ProcessHandle::isAlive).count(),
                active.isAlive(), forced);
    }

    private static void awaitChildren(List<ProcessHandle> children, long timeoutMillis) {
        try {
            java.util.concurrent.CompletableFuture<?>[] exits = children.stream()
                    .map(ProcessHandle::onExit).toArray(java.util.concurrent.CompletableFuture[]::new);
            java.util.concurrent.CompletableFuture.allOf(exits).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException ignored) {
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException ignored) {
        }
    }

    private void startDescendantMonitor(Process active) {
        descendantMonitor = Thread.startVirtualThread(() -> {
            while (process == active && active.isAlive() && !Thread.currentThread().isInterrupted()) {
                ownedDescendants.addAll(collectDescendants(active));
                try { Thread.sleep(10); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            ownedDescendants.addAll(collectDescendants(active));
        });
    }

    private List<ProcessHandle> collectDescendants(Process active) {
        if (active == null) return List.copyOf(ownedDescendants);
        List<ProcessHandle> descendants = new ArrayList<>(active.toHandle().descendants().toList());
        return mergeDescendants(descendants, ownedDescendants);
    }

    private static List<ProcessHandle> mergeDescendants(List<ProcessHandle> current, Iterable<ProcessHandle> tracked) {
        java.util.Map<Long, ProcessHandle> unique = new java.util.LinkedHashMap<>();
        current.forEach(handle -> unique.put(handle.pid(), handle));
        tracked.forEach(handle -> unique.put(handle.pid(), handle));
        return List.copyOf(unique.values());
    }

    /** Terminates a child app-server immediately when its parent turn is cancelled. */
    private void abort() {
        aborted.set(true);
        Process active = process;
        if (active != null) terminateProcess(active, collectDescendants(active));
    }

    @Override public synchronized void close() { closeProcess(); }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " required");
        return value.trim();
    }
}
