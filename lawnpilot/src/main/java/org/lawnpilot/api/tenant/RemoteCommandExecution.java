package org.lawnpilot.api.tenant;

import java.time.Instant;

/**
 * Represents the execution state of a remote command.
 * Tracks command lifecycle: queued → executing → completed/failed.
 * 
 * Phase 7: Remote command tracking and status management.
 */
public record RemoteCommandExecution(
        String commandId,
        String mowerId,
        String fleetId,
        String tenantId,
        String commandType,
        String targetParameter,
        RemoteCommandStatus status,
        GuardrailOutcome guardrailOutcome,
        Instant requestedAt,
        Instant executedAt,
        String executionResult,
        String errorReason) {

    /**
     * Create a pending command (queued for execution)
     */
    public static RemoteCommandExecution pending(
            String commandId,
            String mowerId,
            String fleetId,
            String tenantId,
            String commandType,
            String targetParameter) {
        return new RemoteCommandExecution(
                commandId,
                mowerId,
                fleetId,
                tenantId,
                commandType,
                targetParameter,
                RemoteCommandStatus.PENDING,
                null,
                Instant.now(),
                null,
                null,
                null);
    }

    /**
     * Transition to executing state with guardrail evaluation result
     */
    public RemoteCommandExecution executing(GuardrailOutcome guardrailOutcome) {
        if (status != RemoteCommandStatus.PENDING) {
            throw new IllegalStateException("Can only execute from PENDING state");
        }
        return new RemoteCommandExecution(
                commandId,
                mowerId,
                fleetId,
                tenantId,
                commandType,
                targetParameter,
                RemoteCommandStatus.EXECUTING,
                guardrailOutcome,
                requestedAt,
                Instant.now(),
                null,
                null);
    }

    /**
     * Transition to completed state
     */
    public RemoteCommandExecution completed(String result) {
        if (status != RemoteCommandStatus.EXECUTING && status != RemoteCommandStatus.PENDING) {
            throw new IllegalStateException("Can only complete from PENDING or EXECUTING state");
        }
        return new RemoteCommandExecution(
                commandId,
                mowerId,
                fleetId,
                tenantId,
                commandType,
                targetParameter,
                RemoteCommandStatus.COMPLETED,
                guardrailOutcome,
                requestedAt,
                executedAt != null ? executedAt : Instant.now(),
                result,
                null);
    }

    /**
     * Transition to failed state
     */
    public RemoteCommandExecution failed(String errorReason) {
        return new RemoteCommandExecution(
                commandId,
                mowerId,
                fleetId,
                tenantId,
                commandType,
                targetParameter,
                RemoteCommandStatus.FAILED,
                guardrailOutcome,
                requestedAt,
                executedAt != null ? executedAt : Instant.now(),
                null,
                errorReason);
    }
}
