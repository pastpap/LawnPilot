package org.lawnpilot.api.tenant;

/**
 * Result of guardrail validation check.
 * 
 * Phase 7: Safety constraint validation outcomes.
 */
public enum GuardrailCheckStatus {
    PASSED("All safety constraints passed"),
    FAILED("Safety constraint violated"),
    OVERRIDE("Constraint violated but overridden");

    private final String description;

    GuardrailCheckStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
