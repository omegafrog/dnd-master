package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.List;

@FunctionalInterface
public interface GmCompletionAdapter {
    <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser);

    default <T> GmCompletionResult<T> completeWithSelection(
            String operationId, String prompt, StructuredResponseParser<T> parser,
            RequestedGmProviderSelection requested) {
        throw new UnsupportedOperationException("provider selection is not supported by this adapter");
    }

    /** Runs one initial completion and, only for a malformed candidate, one repair. */
    default <T> GmCandidateLifecycleResult<T> completeWithOneRepair(
            String operationId, String prompt, java.util.function.Function<GmRepairContext, String> repairPrompt,
            StructuredResponseParser<T> parser, RequestedGmProviderSelection requested) {
        java.util.concurrent.atomic.AtomicReference<String> raw = new java.util.concurrent.atomic.AtomicReference<>("");
        StructuredResponseParser<T> capturingParser = json -> {
            raw.set(json == null ? "" : json);
            return parser.parse(json);
        };
        try {
            return new GmCandidateLifecycleResult<>(completeWithSelection(operationId, prompt, capturingParser, requested), 1);
        } catch (ProviderMalformedResponseException malformed) {
            return new GmCandidateLifecycleResult<>(completeWithSelection(operationId + ":repair",
                    repairPrompt.apply(new GmRepairContext(raw.get(), List.of(new GmCandidateViolation(
                            "MALFORMED_JSON", "candidate", malformed.getMessage())))), capturingParser, requested), 2);
        } catch (GmCandidateValidationException invalid) {
            return new GmCandidateLifecycleResult<>(completeWithSelection(operationId + ":repair",
                    repairPrompt.apply(new GmRepairContext(raw.get(), invalid.violations())), capturingParser, requested), 2);
        }
    }
    default <T> GmCandidateLifecycleResult<T> completeWithOneRepair(
            String operationId, String prompt, java.util.function.Function<GmRepairContext, String> repairPrompt,
            StructuredResponseContract<T> contract, RequestedGmProviderSelection requested) {
        return completeWithOneRepair(operationId, prompt, repairPrompt, contract.parser(), requested);
    }

    default <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser,
                            GmProviderRequest provider) {
        return complete(operationId, prompt, parser);
    }
}
