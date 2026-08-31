package org.lawnpilot.api.tenant;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lawnpilot.api.dto.CommandStatusDto;
import org.lawnpilot.api.dto.TelemetryEventDto;
import org.lawnpilot.exceptions.GuardrailViolationException;
import org.lawnpilot.exceptions.NotFoundException;

/**
 * Phase 7: Remote command and telemetry tests.
 * Tests per-mower remote command/control with safety guardrails.
 * Tests IoT-oriented telemetry ingestion and traceability.
 */
class TenantFleetServicePhase7Test {

    private TenantFleetService service;

    @BeforeEach
    void setUp() {
        TenantFleetRepository repository = new InMemoryTenantFleetRepository();
        service = new TenantFleetService(repository);
    }

    // ========== Command Issuance Tests ==========

    @Test
    void issueMowerCommandQueuesCommandSuccessfully() {
        // Setup: Create tenant, fleet, mower
        String tenantId = "tenant-phase7-test";
        String fleetId = "fleet-cmd-test";
        String mowerId = "mower-cmd-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act: Issue a command
        RemoteCommandRequest request = new RemoteCommandRequest(
                "move",
                "forward",
                false,
                "operator-123");
        String commandId = service.issueMowerCommand(
                tenantId,
                TenantRole.OPERATOR,
                fleetId,
                mowerId,
                request);

        // Assert: Command ID is generated and non-empty
        assertNotNull(commandId);
        assertTrue(commandId.startsWith("cmd-"));
        assertTrue(commandId.contains(tenantId));
        assertTrue(commandId.contains(fleetId));
        assertTrue(commandId.contains(mowerId));
    }

    @Test
    void issueMowerCommandRequiresMutationRole() {
        // Setup
        String tenantId = "tenant-phase7-role-test";
        String fleetId = "fleet-role-test";
        String mowerId = "mower-role-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act & Assert: VIEWER role cannot issue commands
        RemoteCommandRequest request = new RemoteCommandRequest(
                "stop",
                "immediate",
                false,
                "viewer-user");

        assertThrows(
                org.lawnpilot.exceptions.RoleAuthorizationException.class,
                () -> service.issueMowerCommand(tenantId, TenantRole.VIEWER, fleetId, mowerId, request));
    }

    @Test
    void issueMowerCommandFailsForNonExistentFleet() {
        // Setup: Tenant exists, fleet doesn't
        String tenantId = "tenant-phase7-fleet-test";
        service.createFleet(tenantId, TenantRole.ADMIN, "some-fleet", "Some Fleet");

        // Act & Assert: Non-existent fleet
        RemoteCommandRequest request = new RemoteCommandRequest(
                "move",
                "forward",
                false,
                "operator");

        assertThrows(
                NotFoundException.class,
                () -> service.issueMowerCommand(
                        tenantId,
                        TenantRole.OPERATOR,
                        "nonexistent-fleet",
                        "mower-001",
                        request));
    }

    @Test
    void issueMowerCommandFailsForNonExistentMower() {
        // Setup: Fleet exists, mower doesn't
        String tenantId = "tenant-phase7-mower-test";
        String fleetId = "fleet-mower-test";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");

        // Act & Assert: Non-existent mower
        RemoteCommandRequest request = new RemoteCommandRequest(
                "move",
                "forward",
                false,
                "operator");

        assertThrows(
                NotFoundException.class,
                () -> service.issueMowerCommand(
                        tenantId,
                        TenantRole.OPERATOR,
                        fleetId,
                        "nonexistent-mower",
                        request));
    }

    // ========== Command Status Query Tests ==========

    @Test
    void queryCommandStatusReturnsCorrectStatus() {
        // Setup
        String tenantId = "tenant-phase7-status-test";
        String fleetId = "fleet-status-test";
        String mowerId = "mower-status-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        RemoteCommandRequest request = new RemoteCommandRequest(
                "pause",
                "safe-stop",
                false,
                "operator-456");
        String commandId = service.issueMowerCommand(
                tenantId,
                TenantRole.OPERATOR,
                fleetId,
                mowerId,
                request);

        // Act: Query command status
        CommandStatusDto status = service.queryCommandStatus(
                tenantId,
                TenantRole.VIEWER,
                fleetId,
                mowerId,
                commandId);

        // Assert: Status contains expected fields
        assertEquals(commandId, status.commandId());
        assertEquals("EXECUTING", status.status());
        assertNotNull(status.requestedAt());
        assertNull(status.executionResult()); // Not yet completed
    }

    @Test
    void queryCommandStatusFailsForNonExistentCommand() {
        // Setup
        String tenantId = "tenant-phase7-missing-cmd-test";
        String fleetId = "fleet-missing-cmd-test";
        String mowerId = "mower-missing-cmd-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act & Assert
        assertThrows(
                NotFoundException.class,
                () -> service.queryCommandStatus(
                        tenantId,
                        TenantRole.VIEWER,
                        fleetId,
                        mowerId,
                        "nonexistent-command-id"));
    }

    // ========== Telemetry Event Recording Tests ==========

    @Test
    void recordTelemetryEventStoresEventSuccessfully() {
        // Setup
        String tenantId = "tenant-phase7-telemetry-test";
        String fleetId = "fleet-telemetry-test";
        String mowerId = "mower-telemetry-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act: Record telemetry event
        service.recordTelemetryEvent(
                tenantId,
                TenantRole.OPERATOR,
                fleetId,
                mowerId,
                "LOCATION_UPDATE",
                "{\"lat\":47.6, \"lon\":-122.3}");

        // Assert: Event is stored (verified by querying)
        List<TelemetryEventDto> events = service.queryMowerTelemetryEvents(
                tenantId,
                TenantRole.VIEWER,
                fleetId,
                mowerId);

        assertEquals(1, events.size());
        assertEquals("LOCATION_UPDATE", events.get(0).eventType());
        assertTrue(events.get(0).eventData().contains("47.6"));
    }

    @Test
    void recordTelemetryEventRequiresMutationRole() {
        // Setup
        String tenantId = "tenant-phase7-telemetry-role-test";
        String fleetId = "fleet-telemetry-role-test";
        String mowerId = "mower-telemetry-role-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act & Assert: VIEWER role cannot record events
        assertThrows(
                org.lawnpilot.exceptions.RoleAuthorizationException.class,
                () -> service.recordTelemetryEvent(
                        tenantId,
                        TenantRole.VIEWER,
                        fleetId,
                        mowerId,
                        "STATUS_UPDATE",
                        "{}"));
    }

    // ========== Telemetry Event Query Tests ==========

    @Test
    void queryMowerTelemetryEventsReturnsEmptyListForNewMower() {
        // Setup
        String tenantId = "tenant-phase7-empty-telemetry-test";
        String fleetId = "fleet-empty-telemetry-test";
        String mowerId = "mower-empty-telemetry-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act: Query telemetry (no events recorded)
        List<TelemetryEventDto> events = service.queryMowerTelemetryEvents(
                tenantId,
                TenantRole.VIEWER,
                fleetId,
                mowerId);

        // Assert
        assertTrue(events.isEmpty());
    }

    @Test
    void queryMowerTelemetryEventsReturnsMultipleEventsInReverseChronologicalOrder() {
        // Setup
        String tenantId = "tenant-phase7-multi-telemetry-test";
        String fleetId = "fleet-multi-telemetry-test";
        String mowerId = "mower-multi-telemetry-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act: Record multiple events
        service.recordTelemetryEvent(
                tenantId,
                TenantRole.OPERATOR,
                fleetId,
                mowerId,
                "BATTERY_UPDATE",
                "{\"percent\":85}");

        // Small delay to ensure different timestamps
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        service.recordTelemetryEvent(
                tenantId,
                TenantRole.OPERATOR,
                fleetId,
                mowerId,
                "LOCATION_UPDATE",
                "{\"lat\":47.6, \"lon\":-122.3}");

        // Query events
        List<TelemetryEventDto> events = service.queryMowerTelemetryEvents(
                tenantId,
                TenantRole.VIEWER,
                fleetId,
                mowerId);

        // Assert: Events in reverse chronological order (most recent first)
        assertEquals(2, events.size());
        assertEquals("LOCATION_UPDATE", events.get(0).eventType());
        assertEquals("BATTERY_UPDATE", events.get(1).eventType());
    }

    @Test
    void queryMowerTelemetryEventsFailsForNonExistentMower() {
        // Setup
        String tenantId = "tenant-phase7-missing-mower-telemetry-test";
        String fleetId = "fleet-missing-mower-telemetry-test";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");

        // Act & Assert
        assertThrows(
                NotFoundException.class,
                () -> service.queryMowerTelemetryEvents(
                        tenantId,
                        TenantRole.VIEWER,
                        fleetId,
                        "nonexistent-mower"));
    }

    // ========== Guardrail Tests ==========

    @Test
    void commandWithValidGuardrailPasses() {
        // Setup
        String tenantId = "tenant-phase7-guardrail-pass-test";
        String fleetId = "fleet-guardrail-pass-test";
        String mowerId = "mower-guardrail-pass-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act: Issue command with valid guardrail (stop is always safe)
        RemoteCommandRequest request = new RemoteCommandRequest(
                "stop",
                "immediate",
                false,
                "operator");
        String commandId = service.issueMowerCommand(
                tenantId,
                TenantRole.OPERATOR,
                fleetId,
                mowerId,
                request);

        // Assert: Command accepted
        assertNotNull(commandId);

        // Verify status shows guardrail passed
        CommandStatusDto status = service.queryCommandStatus(
                tenantId,
                TenantRole.VIEWER,
                fleetId,
                mowerId,
                commandId);
        assertEquals("EXECUTING", status.status());
    }

    // ========== Cross-Tenant Isolation Tests ==========

    @Test
    void commandsIsolatedBetweenTenants() {
        // Setup: Two different tenants
        String tenant1 = "tenant-phase7-isolation-1";
        String tenant2 = "tenant-phase7-isolation-2";
        String fleetId = "shared-fleet-id";
        String mowerId = "shared-mower-id";

        // Create identical fleet/mower structure in both tenants
        service.createFleet(tenant1, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenant1, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        service.createFleet(tenant2, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenant2, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        // Act: Issue command in tenant1
        RemoteCommandRequest request = new RemoteCommandRequest(
                "move",
                "forward",
                false,
                "operator-1");
        String commandId1 = service.issueMowerCommand(
                tenant1,
                TenantRole.OPERATOR,
                fleetId,
                mowerId,
                request);

        // Assert: Tenant2 cannot see tenant1's command
        assertThrows(
                NotFoundException.class,
                () -> service.queryCommandStatus(
                        tenant2,
                        TenantRole.VIEWER,
                        fleetId,
                        mowerId,
                        commandId1));

        // Assert: Tenant2 has no telemetry events (isolated)
        List<TelemetryEventDto> tenant2Events = service.queryMowerTelemetryEvents(
                tenant2,
                TenantRole.VIEWER,
                fleetId,
                mowerId);
        assertTrue(tenant2Events.isEmpty());
    }
}
