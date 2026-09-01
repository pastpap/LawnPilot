package org.lawnpilot.api.dto;

/**
 * Response DTO for telemetry events.
 * 
 * Phase 7: Telemetry event tracking for live status.
 */
public record TelemetryEventDto(
        String eventId,
        String eventType,
        String eventData,
        String recordedAt,
        boolean isCommandRelated,
        String relatedCommandId) {
}
