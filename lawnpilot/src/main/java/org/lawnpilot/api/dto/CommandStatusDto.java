package org.lawnpilot.api.dto;

/**
 * Response DTO for command execution status.
 * 
 * Phase 7: Remote command status tracking.
 */
public record CommandStatusDto(
        String commandId,
        String status,
        String guardrailStatus,
        String guardrailReason,
        String executionResult,
        String errorReason,
        String requestedAt,
        String executedAt) {
}
