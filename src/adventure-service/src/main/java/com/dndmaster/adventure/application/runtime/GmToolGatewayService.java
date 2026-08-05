package com.dndmaster.adventure.application.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class GmToolGatewayService implements GmToolGateway {
    private final Map<String, GmToolDefinition> registry;
    private final Clock clock;

    public GmToolGatewayService(Set<GmToolDefinition> definitions, Clock clock) {
        Objects.requireNonNull(definitions); this.clock = Objects.requireNonNull(clock);
        this.registry = Map.copyOf(definitions.stream().collect(Collectors.toUnmodifiableMap(GmToolDefinition::name, Function.identity())));
    }

    @Override
    public GmToolOutcome invoke(TurnCapability capability, GmToolInvocation invocation) {
        Objects.requireNonNull(capability); Objects.requireNonNull(invocation);
        capability.authorize(invocation, Instant.now(clock));
        GmToolDefinition definition = registry.get(invocation.toolName());
        if (definition == null) throw new UnknownToolException(invocation.toolName());
        return Objects.requireNonNull(definition.handler().handle(invocation), "tool outcome must not be null");
    }
}
