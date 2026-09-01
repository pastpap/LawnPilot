package org.lawnpilot.api.tenant;

import java.time.Instant;

/**
 * Represents a remote control command for a mower.
 * Encapsulates command type, parameters, and execution context.
 * 
 * Phase 7: Per-mower remote command/control with safety guardrails.
 */
public record RemoteCommandRequest(
        String commandType,
        String targetParameter,
        boolean overrideGuardrails,
        String requestedBy) {

    public RemoteCommandRequest {
        if (commandType == null || commandType.trim().isEmpty()) {
            throw new IllegalArgumentException("commandType must not be blank");
        }
        if (targetParameter == null || targetParameter.trim().isEmpty()) {
            throw new IllegalArgumentException("targetParameter must not be blank");
        }
        if (requestedBy == null || requestedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("requestedBy must not be blank");
        }
    }
}
