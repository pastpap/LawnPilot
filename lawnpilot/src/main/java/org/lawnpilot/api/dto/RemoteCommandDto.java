package org.lawnpilot.api.dto;

import java.time.Instant;

/**
 * Remote Command Envelope (Phase 7)
 * 
 * Represents a command sent to a mower for remote execution with safety properties:
 * - commandId: Unique identifier for idempotency tracking
 * - mowerId: Target mower within the fleet
 * - commandType: Supported command type (MOVE_FORWARD, TURN_LEFT, TURN_RIGHT, STOP, PAUSE, RESUME)
 * - sequence: Sequence number within a correlation group for ordering
 * - correlationId: Groups related commands together (e.g., a multi-command override session)
 * - expiresAt: TTL after which command is stale and rejected
 */
public record RemoteCommandDto(
        String commandId,
        String mowerId,
        String commandType,
        int sequence,
        String correlationId,
        Instant expiresAt
) {
}
