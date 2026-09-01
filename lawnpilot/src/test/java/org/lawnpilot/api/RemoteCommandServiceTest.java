package org.lawnpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lawnpilot.api.dto.RemoteCommandDto;
import org.lawnpilot.api.dto.RemoteCommandResultDto;
import org.lawnpilot.api.tenant.TenantFleetService;
import org.lawnpilot.api.tenant.TenantRole;
import org.lawnpilot.api.tenant.InMemoryTenantFleetRepository;
import org.lawnpilot.exceptions.RoleAuthorizationException;
import org.lawnpilot.exceptions.TenantValidationException;

/**
 * Phase 7 Regression Test: Remote Command Guardrails and Safety Gates
 * 
 * Tests validate:
 * - Role-based command restrictions (OPERATOR only, not VIEWER/ADMIN
 * restrictions)
 * - Command envelope validation (commandId, sequence, correlationId, expiry)
 * - Invalid/stale command rejection
 * - Safe command execution and idempotency enforcement
 * - Tenant isolation on remote operations
 */
class RemoteCommandServiceTest {

    private RemoteCommandService remoteCommandService;
    private TenantFleetService tenantFleetService;

    @BeforeEach
    void setUp() {
        tenantFleetService = new TenantFleetService(new InMemoryTenantFleetRepository());
        remoteCommandService = new RemoteCommandService(tenantFleetService);

        // Setup: Create tenant, fleet, and mower
        tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Fleet Alpha");
        tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "LP-X");
    }

    // --- Role-Based Access Control Tests ---

    @Test
    void operatorCanSendRemoteCommand() {
        RemoteCommandDto cmd = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        Optional<RemoteCommandResultDto> result = remoteCommandService.sendCommand(
                "tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd);

        assertTrue(result.isPresent(), "OPERATOR should be able to send commands");
        assertEquals(cmd.commandId(), result.get().commandId());
    }

    @Test
    void viewerCannotSendRemoteCommand() {
        RemoteCommandDto cmd = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        assertThrows(RoleAuthorizationException.class,
                () -> remoteCommandService.sendCommand("tenant-alpha", TenantRole.VIEWER, "fleet-1", cmd),
                "VIEWER should not be able to send commands");
    }

    @Test
    void adminCanSendRemoteCommand() {
        // ADMIN can mutate, so ADMIN should also be able to send commands
        RemoteCommandDto cmd = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        Optional<RemoteCommandResultDto> result = remoteCommandService.sendCommand(
                "tenant-alpha", TenantRole.ADMIN, "fleet-1", cmd);

        assertTrue(result.isPresent(), "ADMIN should be able to send commands");
    }

    // --- Command Envelope Validation Tests ---

    @Test
    void staleCommandIsRejected() {
        // Command with expiry in the past
        RemoteCommandDto staleCmd = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(10));

        assertThrows(IllegalArgumentException.class,
                () -> remoteCommandService.sendCommand("tenant-alpha", TenantRole.OPERATOR, "fleet-1", staleCmd),
                "Expired command should be rejected");
    }

    @Test
    void invalidMowerIdIsRejected() {
        RemoteCommandDto cmd = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-nonexistent",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        assertThrows(IllegalArgumentException.class,
                () -> remoteCommandService.sendCommand("tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd),
                "Command for non-existent mower should be rejected");
    }

    @Test
    void commandWithoutCorrelationIdIsRejected() {
        RemoteCommandDto cmd = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "MOVE_FORWARD",
                1,
                null, // missing correlationId
                Instant.now().plusSeconds(60));

        assertThrows(IllegalArgumentException.class,
                () -> remoteCommandService.sendCommand("tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd),
                "Command without correlationId should be rejected");
    }

    @Test
    void commandWithoutCommandIdIsRejected() {
        RemoteCommandDto cmd = new RemoteCommandDto(
                null, // missing commandId
                "mower-1",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        assertThrows(IllegalArgumentException.class,
                () -> remoteCommandService.sendCommand("tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd),
                "Command without commandId should be rejected");
    }

    // --- Idempotency Tests ---

    @Test
    void duplicateCommandIdIsIdempotent() {
        String commandId = UUID.randomUUID().toString();
        RemoteCommandDto cmd = new RemoteCommandDto(
                commandId,
                "mower-1",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        Optional<RemoteCommandResultDto> result1 = remoteCommandService.sendCommand(
                "tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd);

        Optional<RemoteCommandResultDto> result2 = remoteCommandService.sendCommand(
                "tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd);

        assertTrue(result1.isPresent() && result2.isPresent(), "Both submissions should succeed");
        assertEquals(result1.get().commandId(), result2.get().commandId(), "Same commandId should return same result");
    }

    // --- Tenant Isolation Tests ---

    @Test
    void remoteCommandIstenantIsolated() {
        // Setup second tenant
        tenantFleetService.createFleet("tenant-beta", TenantRole.ADMIN, "fleet-1", "Fleet Beta");
        tenantFleetService.registerMower("tenant-beta", TenantRole.ADMIN, "fleet-1", "mower-1", "LP-Z");

        String commandId = UUID.randomUUID().toString();
        RemoteCommandDto cmd = new RemoteCommandDto(
                commandId,
                "mower-1",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        Optional<RemoteCommandResultDto> alphaResult = remoteCommandService.sendCommand(
                "tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd);

        // Same commandId for different tenant should NOT be treated as duplicate
        Optional<RemoteCommandResultDto> betaResult = remoteCommandService.sendCommand(
                "tenant-beta", TenantRole.OPERATOR, "fleet-1", cmd);

        assertTrue(alphaResult.isPresent() && betaResult.isPresent(),
                "Both tenants should accept the same commandId independently");
    }

    @Test
    void blankTenantIdIsRejected() {
        RemoteCommandDto cmd = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "MOVE_FORWARD",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        assertThrows(TenantValidationException.class,
                () -> remoteCommandService.sendCommand("   ", TenantRole.OPERATOR, "fleet-1", cmd),
                "Blank tenant ID should be rejected");
    }

    // --- Command Sequence Tests ---

    @Test
    void commandSequenceNumbersAreTrackedPerCorrelationId() {
        String correlationId = UUID.randomUUID().toString();

        RemoteCommandDto cmd1 = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "MOVE_FORWARD",
                1,
                correlationId,
                Instant.now().plusSeconds(60));

        RemoteCommandDto cmd2 = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "MOVE_FORWARD",
                2,
                correlationId,
                Instant.now().plusSeconds(60));

        Optional<RemoteCommandResultDto> result1 = remoteCommandService.sendCommand(
                "tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd1);

        Optional<RemoteCommandResultDto> result2 = remoteCommandService.sendCommand(
                "tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd2);

        assertTrue(result1.isPresent() && result2.isPresent(), "Both commands should be accepted");
        assertEquals(1, result1.get().sequence(), "First command should have sequence 1");
        assertEquals(2, result2.get().sequence(), "Second command should have sequence 2");
    }

    // --- Observable Error Handling ---

    @Test
    void invalidCommandTypeIsRejected() {
        RemoteCommandDto cmd = new RemoteCommandDto(
                UUID.randomUUID().toString(),
                "mower-1",
                "INVALID_COMMAND_TYPE",
                1,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60));

        assertThrows(IllegalArgumentException.class,
                () -> remoteCommandService.sendCommand("tenant-alpha", TenantRole.OPERATOR, "fleet-1", cmd),
                "Invalid command type should be rejected");
    }
}
