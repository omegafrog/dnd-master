package com.dndmaster.adventure.application.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class GmToolGatewayService implements GmToolGateway {
    private final Map<String, GmToolDefinition> registry;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final Set<UUID> revokedNonces = ConcurrentHashMap.newKeySet();

    public GmToolGatewayService(Set<GmToolDefinition> definitions, Clock clock) {
        this(definitions, clock, new ObjectMapper());
    }

    public GmToolGatewayService(Set<GmToolDefinition> definitions, Clock clock, ObjectMapper mapper) {
        Objects.requireNonNull(definitions); this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
        this.registry = Map.copyOf(definitions.stream().collect(Collectors.toUnmodifiableMap(GmToolDefinition::name, Function.identity())));
    }

    @Override
    public java.util.List<GmToolSpec> modelTools() {
        return registry.values().stream().map(d -> new GmToolSpec(d.name(), d.inputSchema())).toList();
    }

    @Override
    public void revoke(TurnCapability capability) { revokedNonces.add(capability.nonce()); }

    @Override
    public java.util.Optional<GmToolOutcome> query(String toolName, UUID commandId) {
        GmToolDefinition definition = registry.get(toolName);
        return definition == null ? java.util.Optional.empty() : definition.query().apply(commandId);
    }

    @Override
    public void preflight(TurnCapability capability, GmToolInvocation invocation) {
        Objects.requireNonNull(capability); Objects.requireNonNull(invocation);
        if (revokedNonces.contains(capability.nonce())) throw new ToolAuthorizationException();
        capability.authorize(invocation, Instant.now(clock));
        GmToolDefinition definition = registry.get(invocation.toolName());
        if (definition == null) throw new UnknownToolException(invocation.toolName());
        validate(definition.inputSchema(), invocation.argumentsJson());
    }

    @Override
    public GmToolOutcome invoke(TurnCapability capability, GmToolInvocation invocation) {
        Objects.requireNonNull(capability); Objects.requireNonNull(invocation);
        if (revokedNonces.contains(capability.nonce())) throw new ToolAuthorizationException();
        capability.authorize(invocation, Instant.now(clock));
        GmToolDefinition definition = registry.get(invocation.toolName());
        if (definition == null) throw new UnknownToolException(invocation.toolName());
        validate(definition.inputSchema(), invocation.argumentsJson());
        return Objects.requireNonNull(definition.handler().handle(invocation), "tool outcome must not be null");
    }

    private void validate(String schemaText, String argumentsText) {
        try {
            JsonNode schema = mapper.readTree(schemaText);
            JsonNode args = mapper.readTree(argumentsText);
            if (!args.isObject()) throw new ToolArgumentInvalidException("tool arguments must be a JSON object");
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) for (JsonNode name : required) {
                if (!args.has(name.asText())) throw new ToolArgumentInvalidException("missing tool argument: " + name.asText());
            }
            JsonNode properties = schema.get("properties");
            if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean(true) && args.fieldNames().hasNext()) {
                java.util.Set<String> known = new java.util.HashSet<>(); properties.fieldNames().forEachRemaining(known::add);
                args.fieldNames().forEachRemaining(name -> { if (!known.contains(name)) throw new ToolArgumentInvalidException("unknown tool argument: " + name); });
            }
            if (properties != null && properties.isObject()) properties.fields().forEachRemaining(field -> {
                JsonNode value = args.get(field.getKey()); JsonNode type = field.getValue().get("type");
                if (value != null && type != null && !matches(type.asText(), value)) throw new ToolArgumentInvalidException("invalid tool argument: " + field.getKey());
                JsonNode enums = field.getValue().get("enum");
                if (value != null && enums != null && enums.isArray() && java.util.stream.StreamSupport.stream(enums.spliterator(), false).noneMatch(item -> item.equals(value))) throw new ToolArgumentInvalidException("invalid tool argument: " + field.getKey());
            });
        } catch (ToolArgumentInvalidException e) { throw e; }
        catch (Exception e) { throw new ToolArgumentInvalidException("invalid tool arguments/schema"); }
    }

    private boolean matches(String type, JsonNode value) {
        return switch (type) { case "string" -> value.isTextual(); case "number" -> value.isNumber(); case "integer" -> value.isIntegralNumber(); case "boolean" -> value.isBoolean(); case "array" -> value.isArray(); case "object" -> value.isObject(); default -> true; };
    }
}
