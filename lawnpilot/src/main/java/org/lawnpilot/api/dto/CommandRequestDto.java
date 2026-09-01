package org.lawnpilot.api.dto;

/**
 * Request DTO for issuing a remote command to a mower.
 * 
 * Phase 7: Remote command API.
 */
public record CommandRequestDto(
        String commandType,
        String targetParameter,
        boolean overrideGuardrails,
        String requestedBy) {
}
