package org.lawnpilot.exceptions;

/**
 * Thrown when a remote command violates safety guardrails.
 * 
 * Maps to HTTP 422 (Unprocessable Entity) or HTTP 403 (Forbidden) depending on
 * override flag.
 * Phase 7: Guardrail violation handling.
 */
public class GuardrailViolationException extends RuntimeException {
    private final String violatedConstraint;
    private final boolean overrideable;

    public GuardrailViolationException(String message, String violatedConstraint, boolean overrideable) {
        super(message);
        this.violatedConstraint = violatedConstraint;
        this.overrideable = overrideable;
    }

    public String violatedConstraint() {
        return violatedConstraint;
    }

    public boolean isOverrideable() {
        return overrideable;
    }
}
