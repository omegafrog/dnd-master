package com.dndmaster.ruleknowledge.application.preprocessing;

public final class PreprocessingProcessException extends RuntimeException {
    private final String code;

    public PreprocessingProcessException(String code) {
        super(code);
        this.code = code;
    }

    public PreprocessingProcessException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
