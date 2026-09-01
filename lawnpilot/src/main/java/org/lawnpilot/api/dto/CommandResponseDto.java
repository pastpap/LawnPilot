package org.lawnpilot.api.dto;

/**
 * Response DTO for a newly issued remote command.
 * 
 * Phase 7: Remote command response.
 */
public record CommandResponseDto(
        String commandId,
        String status,
        String message) {
}
