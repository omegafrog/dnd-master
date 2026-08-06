package com.dndmaster.aigamemaster.retrieval;

public record RetrievalReference(String documentId, String locator, String version) {
    public RetrievalReference { required(documentId, "documentId"); required(locator, "locator"); required(version, "version"); }
    private static void required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required"); }
}

