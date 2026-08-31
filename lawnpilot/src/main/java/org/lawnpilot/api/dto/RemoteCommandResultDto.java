package org.lawnpilot.api.dto;

import java.time.Instant;

/**
 * Remote Command Result (Phase 7)
 * 
 * Response from a remote command submission containing:
 * - commandId: Echo of the submitted command ID
 * - sequence: Confirmed sequence number
 * - status: Execution status (PENDING, ACKNOWLEDGED, EXECUTED, FAILED)
 * - receivedAt: Timestamp when command was received by backend
 * - executedAt: Timestamp when command was executed (null if not yet executed)
 * - errorMessage: Error details if status is FAILED
 */
public record RemoteCommandResultDto(
        String commandId,
        int sequence,
        String status,
        Instant receivedAt,
        Instant executedAt,
        String errorMessage
) {
}
