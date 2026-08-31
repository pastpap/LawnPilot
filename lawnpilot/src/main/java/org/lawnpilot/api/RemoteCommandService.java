package org.lawnpilot.api;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.lawnpilot.api.dto.RemoteCommandDto;
import org.lawnpilot.api.dto.RemoteCommandResultDto;
import org.lawnpilot.api.tenant.TenantFleetService;
import org.lawnpilot.api.tenant.TenantRole;
import org.lawnpilot.exceptions.RoleAuthorizationException;
import org.lawnpilot.exceptions.TenantValidationException;

/**
 * Remote Command Service (Phase 7)
 * 
 * Manages remote command submission with safety guardrails:
 * - Role-based authorization (OPERATOR and ADMIN only)
 * - Command envelope validation (TTL, commandId, correlationId)
 * - Idempotency tracking per tenant (duplicate commandIds return cached
 * results)
 * - Tenant isolation of command history and execution
 */
public class RemoteCommandService {

    private final TenantFleetService tenantFleetService;

    // Per-tenant command idempotency store: tenantId -> (commandId -> result)
    private final Map<String, Map<String, RemoteCommandResultDto>> commandCache = new ConcurrentHashMap<>();

    public RemoteCommandService(TenantFleetService tenantFleetService) {
        this.tenantFleetService = tenantFleetService;
    }

    /**
     * Send a remote command to a mower.
     * 
     * @param tenantId tenant identifier
     * @param role     requester's role
     * @param fleetId  target fleet
     * @param command  remote command envelope
     * @return Optional containing command result if successful
     * @throws RoleAuthorizationException if role cannot send commands
     * @throws TenantValidationException  if tenantId is invalid
     * @throws IllegalArgumentException   if command envelope is invalid or stale
     */
    public Optional<RemoteCommandResultDto> sendCommand(
            String tenantId,
            TenantRole role,
            String fleetId,
            RemoteCommandDto command) {
        // Validate tenant
        String normalized = tenantId != null ? tenantId.trim() : "";
        if (normalized.isEmpty()) {
            throw new TenantValidationException("Tenant ID cannot be blank");
        }

        // Validate role has permission (OPERATOR and ADMIN can send commands)
        if (role == TenantRole.VIEWER) {
            throw new RoleAuthorizationException(
                    "Role 'VIEWER' is not allowed to send remote commands. Required role: OPERATOR or ADMIN.");
        }

        // Validate command envelope
        validateCommandEnvelope(command);

        // Validate mower exists in fleet
        validateMowerExists(normalized, role, fleetId, command.mowerId());

        // Check idempotency cache
        Map<String, RemoteCommandResultDto> tenantCache = commandCache.computeIfAbsent(normalized,
                k -> new ConcurrentHashMap<>());

        RemoteCommandResultDto cached = tenantCache.get(command.commandId());
        if (cached != null) {
            return Optional.of(cached);
        }

        // Create and cache result
        RemoteCommandResultDto result = new RemoteCommandResultDto(
                command.commandId(),
                command.sequence(),
                "PENDING",
                Instant.now(),
                null,
                null);

        tenantCache.put(command.commandId(), result);
        return Optional.of(result);
    }

    private void validateCommandEnvelope(RemoteCommandDto command) {
        if (command.commandId() == null || command.commandId().isEmpty()) {
            throw new IllegalArgumentException("Command ID cannot be null or empty");
        }

        if (command.correlationId() == null || command.correlationId().isEmpty()) {
            throw new IllegalArgumentException("Correlation ID cannot be null or empty");
        }

        if (command.mowerId() == null || command.mowerId().isEmpty()) {
            throw new IllegalArgumentException("Mower ID cannot be null or empty");
        }

        if (command.commandType() == null || command.commandType().isEmpty()) {
            throw new IllegalArgumentException("Command type cannot be null or empty");
        }

        // Validate command type is known
        Set<String> validCommandTypes = Set.of(
                "MOVE_FORWARD", "TURN_LEFT", "TURN_RIGHT", "STOP", "PAUSE", "RESUME");
        if (!validCommandTypes.contains(command.commandType())) {
            throw new IllegalArgumentException(
                    "Invalid command type '" + command.commandType() + "'. Allowed types: " + validCommandTypes);
        }

        // Validate expiry is in the future
        if (command.expiresAt() != null && command.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Command has expired");
        }
    }

    private void validateMowerExists(String tenantId, TenantRole role, String fleetId, String mowerId) {
        if (mowerId == null || mowerId.isEmpty() || !mowerId.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid mower ID format");
        }

        // Verify mower actually exists in the tenant's fleet
        var mowers = tenantFleetService.listMowers(tenantId, role, fleetId);
        boolean mowerExists = mowers.stream()
                .anyMatch(m -> m.mowerId().equals(mowerId));

        if (!mowerExists) {
            throw new IllegalArgumentException("Mower '" + mowerId + "' not found in fleet '" + fleetId + "'");
        }
    }
}
