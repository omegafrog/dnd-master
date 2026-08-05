package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Bounded Plan -> Act -> Finalize seam. No provider or transport types cross it. */
public final class GmToolExecutionLoop {
    private final GmToolGateway gateway;
    private final int maxToolCalls;

    public GmToolExecutionLoop(GmToolGateway gateway, int maxToolCalls) {
        this.gateway = Objects.requireNonNull(gateway);
        if (maxToolCalls < 1) throw new IllegalArgumentException("max tool calls must be positive");
        this.maxToolCalls = maxToolCalls;
    }

    public Result act(TurnCapability capability, List<PlannedToolCall> plan,
                      Function<PlannedToolCall, PlannedToolCall> oneRepair) {
        Objects.requireNonNull(capability); Objects.requireNonNull(plan); Objects.requireNonNull(oneRepair);
        List<GmToolOutcome> outcomes = new ArrayList<>();
        boolean repaired = false;
        int calls = 0;
        for (PlannedToolCall planned : plan) {
            if (++calls > maxToolCalls) throw new RequiredToolFailureException("tool call limit");
            GmToolOutcome outcome;
            try { outcome = gateway.invoke(capability, planned.invocation()); }
            catch (ToolArgumentInvalidException failure) {
                if (repaired) throw failure;
                PlannedToolCall repairedCall = oneRepair.apply(planned);
                if (repairedCall == null) throw failure;
                repaired = true;
                if (++calls > maxToolCalls) throw new RequiredToolFailureException("tool call limit");
                outcome = gateway.invoke(capability, repairedCall.invocation());
            }
            outcomes.add(outcome);
            if (planned.required() && outcome.status() != GmToolOutcome.Status.COMPLETED) {
                throw new RequiredToolFailureException(planned.invocation().toolName());
            }
        }
        return new Result(List.copyOf(outcomes), repaired, calls);
    }

    public record PlannedToolCall(GmToolInvocation invocation, boolean required) { public PlannedToolCall { Objects.requireNonNull(invocation); } }
    public record Result(List<GmToolOutcome> outcomes, boolean repaired, int calls) { public Result { outcomes = List.copyOf(outcomes); } }
}
