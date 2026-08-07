package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Set;

public interface RuntimeCapabilityPreflightPort {
    Result check(String engineId, List<String> toolIds, Set<String> documentRoles);

    record Result(List<String> blockers, List<String> warnings, boolean retryable) {
        public Result { blockers = List.copyOf(blockers); warnings = List.copyOf(warnings); }
        public static Result ready() { return new Result(List.of(), List.of(), false); }
    }
}
