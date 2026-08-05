package com.dndmaster.adventure.application.runtime;

public record ContinuityCommandResult(String value, long version, String planRevisionId, long clockVersion) {
    public String reference() { return "planRevision=" + planRevisionId + ";clockVersion=" + clockVersion + ";version=" + version; }
}
