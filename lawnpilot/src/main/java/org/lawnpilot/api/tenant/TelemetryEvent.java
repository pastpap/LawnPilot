package org.lawnpilot.api.tenant;

import java.time.Instant;

/**
 * Represents a telemetry event from a mower.
 * Used for IoT-oriented telemetry ingestion and traceability.
 * 
 * Phase 7: Telemetry ingestion and live tracking hooks.
 */
public record TelemetryEvent(
        String eventId,
        String mowerId,
        String fleetId,
        String tenantId,
        String eventType,
        String eventData,
        Instant recordedAt,
        boolean isCommandRelated,
        String relatedCommandId) {

    public TelemetryEvent {
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("recordedAt must not be null");
        }
    }

    /**
     * Create a telemetry event with auto-generated timestamp
     */
    public static TelemetryEvent of(
            String eventId,
            String mowerId,
            String fleetId,
            String tenantId,
            String eventType,
            String eventData) {
        return new TelemetryEvent(
                eventId,
                mowerId,
                fleetId,
                tenantId,
                eventType,
                eventData,
                Instant.now(),
                false,
                null);
    }

    /**
     * Create a command-related telemetry event
     */
    public static TelemetryEvent commandRelated(
            String eventId,
            String mowerId,
            String fleetId,
            String tenantId,
            String eventType,
            String eventData,
            String relatedCommandId) {
        return new TelemetryEvent(
                eventId,
                mowerId,
                fleetId,
                tenantId,
                eventType,
                eventData,
                Instant.now(),
                true,
                relatedCommandId);
    }
}
