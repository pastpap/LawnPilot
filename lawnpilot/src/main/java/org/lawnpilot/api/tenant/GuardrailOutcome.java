package org.lawnpilot.api.tenant;

/**
 * Guardrail validation outcome for remote commands.
 * Enforces safety constraints before command execution.
 * 
 * Phase 7: Safety guardrails for remote control.
 */
public record GuardrailOutcome(
        GuardrailCheckStatus status,
        String failureReason,
        String safetyConstraintViolated) {

    public GuardrailOutcome {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == GuardrailCheckStatus.FAILED && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("failureReason required when status is FAILED");
        }
    }

    /**
     * Create a passed guardrail outcome
     */
    public static GuardrailOutcome pass() {
        return new GuardrailOutcome(GuardrailCheckStatus.PASSED, null, null);
    }

    /**
     * Create a failed guardrail outcome
     */
    public static GuardrailOutcome fail(String reason, String violatedConstraint) {
        return new GuardrailOutcome(GuardrailCheckStatus.FAILED, reason, violatedConstraint);
    }

    /**
     * Create a guardrail override outcome (only if overrideGuardrails flag is true)
     */
    public static GuardrailOutcome override(String reason, String violatedConstraint) {
        return new GuardrailOutcome(GuardrailCheckStatus.OVERRIDE, reason, violatedConstraint);
    }

    public boolean isPassed() {
        return status == GuardrailCheckStatus.PASSED;
    }

    public boolean isFailed() {
        return status == GuardrailCheckStatus.FAILED;
    }

    public boolean isOverridden() {
        return status == GuardrailCheckStatus.OVERRIDE;
    }
}
