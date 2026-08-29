package com.dndmaster.gmeval.infrastructure;

/** Provider-neutral structured completion boundary used by the evaluation adapter. */
@FunctionalInterface
public interface StructuredOutputCompletionPort {
    String complete(String prompt);
}
