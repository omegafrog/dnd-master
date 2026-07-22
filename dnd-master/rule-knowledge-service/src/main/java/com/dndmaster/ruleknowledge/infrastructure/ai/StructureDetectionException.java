package com.dndmaster.ruleknowledge.infrastructure.ai;

public final class StructureDetectionException extends RuntimeException {
    public StructureDetectionException(String message) {
        super(message);
    }

    public StructureDetectionException(Throwable cause) {
        super(cause);
    }
}
