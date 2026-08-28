package com.dndmaster.aigamemaster.infrastructure.ai;

public final class GmProviderSelectionUnresolvedException extends RuntimeException {
    private final RequestedGmProviderSelection requested;

    public GmProviderSelectionUnresolvedException(RequestedGmProviderSelection requested) {
        super("GM provider selection could not be resolved");
        this.requested = java.util.Objects.requireNonNull(requested, "requested selection required");
    }

    public String code() { return "GM_PROVIDER_SELECTION_UNRESOLVED"; }
    public RequestedGmProviderSelection requested() { return requested; }
}
