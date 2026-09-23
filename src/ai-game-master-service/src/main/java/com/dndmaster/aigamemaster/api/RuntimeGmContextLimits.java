package com.dndmaster.aigamemaster.api;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Explicit context limits keyed by the provider and model actually selected for a call. */
final class RuntimeGmContextLimits {
    private final Map<String, Integer> limits;

    RuntimeGmContextLimits(String configured) {
        Map<String, Integer> parsed = new HashMap<>();
        if (configured != null && !configured.isBlank()) {
            for (String item : configured.split(",")) {
                String[] pair = item.trim().split("=", -1);
                if (pair.length != 2 || !pair[0].contains("/")) {
                    throw new IllegalArgumentException("invalid runtime context limit configuration");
                }
                int limit = Integer.parseInt(pair[1].trim());
                if (limit <= 0 || parsed.putIfAbsent(pair[0].trim(), limit) != null) {
                    throw new IllegalArgumentException("invalid or duplicate runtime context limit");
                }
            }
        }
        limits = Map.copyOf(parsed);
    }

    int require(String provider, String model) {
        Integer limit = limits.get(provider + "/" + model);
        if (limit == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "selected GM model has no configured context limit");
        }
        return limit;
    }
}
