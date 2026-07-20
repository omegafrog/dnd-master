package com.dndmaster.aigamemaster.infrastructure.ai;

public final class OllamaCircuitOpenException extends IllegalStateException {
    public OllamaCircuitOpenException() {
        super("Ollama circuit is open");
    }
}
