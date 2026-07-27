package com.dndmaster.adventure.application.guidance;

public final class RuleInquiryNotFoundException extends RuntimeException {
    public RuleInquiryNotFoundException() { super("rule inquiry was not found"); }
}
