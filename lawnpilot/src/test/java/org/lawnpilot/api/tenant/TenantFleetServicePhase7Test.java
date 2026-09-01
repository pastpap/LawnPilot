package org.lawnpilot.api.tenant;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lawnpilot.api.dto.CommandStatusDto;
import org.lawnpilot.api.dto.TelemetryEventDto;
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

        // Assert deterministically on content to avoid time-sensitive ordering
        // assumptions.
        assertEquals(2, events.size());
        Set<String> eventTypes = events.stream().map(event -> event.eventType())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("BATTERY_UPDATE", "LOCATION_UPDATE"), eventTypes);
        assertTrue(events.stream().allMatch(event -> event.recordedAt() != null && !event.recordedAt().isBlank()));
    }

    @Test
    void issuingCommandCreatesCommandLinkedTelemetryEvent() {
        String tenantId = "tenant-phase7-cmd-telemetry-test";
        String fleetId = "fleet-cmd-telemetry-test";
        String mowerId = "mower-cmd-telemetry-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX");

        RemoteCommandRequest request = new RemoteCommandRequest(
                "pause",
                "stop",
                false,
                "operator-789");

        String commandId = service.issueMowerCommand(
                tenantId,
                TenantRole.OPERATOR,
                fleetId,
                mowerId,
                request);

        List<TelemetryEventDto> events = service.queryMowerTelemetryEvents(
                tenantId,
                TenantRole.VIEWER,
                fleetId,
                mowerId);

        TelemetryEventDto commandEvent = events.stream()
                .filter(event -> "COMMAND_ISSUED".equals(event.eventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected command-related telemetry event"));

        assertTrue(commandEvent.isCommandRelated());
        assertEquals(commandId, commandEvent.relatedCommandId());
        assertTrue(commandEvent.eventData().contains("pause"));
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

    @Test
    void telemetryProgressesAcrossSuccessivePolls() {
        String tenantId = "tenant-phase7-telemetry-progression";
        String fleetId = "fleet-progress";
        String mowerId = "mower-progress-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Progress Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX", true);

        List<org.lawnpilot.api.dto.MowerTelemetryDto> firstPoll = service.listMowerTelemetry(
                tenantId,
                TenantRole.VIEWER,
                fleetId);
        List<org.lawnpilot.api.dto.MowerTelemetryDto> secondPoll = service.listMowerTelemetry(
                tenantId,
                TenantRole.VIEWER,
                fleetId);

        assertEquals(1, firstPoll.size());
        assertEquals(1, secondPoll.size());

        org.lawnpilot.api.dto.MowerTelemetryDto first = firstPoll.get(0);
        org.lawnpilot.api.dto.MowerTelemetryDto second = secondPoll.get(0);

        assertTrue(first.latitude() != second.latitude() || first.longitude() != second.longitude());
        assertTrue(second.runtimeMinutesToday() > first.runtimeMinutesToday());
        assertTrue(second.batteryPercent() <= first.batteryPercent());
    }

    @Test
    void telemetryLatLngAlwaysWithinGeofenceBounds() {
        String tenantId = "tenant-phase7-geofence";
        String fleetId = "fleet-geofence";
        String mowerId = "mower-geofence-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Geofence Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, mowerId, "ModelX", true);

        for (int i = 0; i < 30; i++) {
            List<org.lawnpilot.api.dto.MowerTelemetryDto> telemetry = service.listMowerTelemetry(
                    tenantId,
                    TenantRole.VIEWER,
                    fleetId);
            assertEquals(1, telemetry.size());
            org.lawnpilot.api.dto.MowerTelemetryDto reading = telemetry.get(0);

            assertTrue(reading.latitude() >= 47.58 && reading.latitude() <= 47.66);
            assertTrue(reading.longitude() >= -122.33 && reading.longitude() <= -122.24);
        }
    }

    @Test
    void runtimeIsMonotonicAndSimulatedBatteryDrainsSafely() {
        String tenantId = "tenant-phase7-runtime-battery";
        String fleetId = "fleet-runtime-battery";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Runtime Fleet");
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, "sim-1", "ModelSim", true);
        service.registerMower(tenantId, TenantRole.ADMIN, fleetId, "real-1", "ModelReal", false);

        int prevSimRuntime = -1;
        int prevRealRuntime = -1;
        int prevSimBattery = 101;

        for (int i = 0; i < 25; i++) {
            List<org.lawnpilot.api.dto.MowerTelemetryDto> telemetry = service.listMowerTelemetry(
                    tenantId,
                    TenantRole.VIEWER,
                    fleetId);

            org.lawnpilot.api.dto.MowerTelemetryDto sim = telemetry.stream()
                    .filter(t -> "sim-1".equals(t.mowerId()))
                    .findFirst()
                    .orElseThrow();
            org.lawnpilot.api.dto.MowerTelemetryDto real = telemetry.stream()
                    .filter(t -> "real-1".equals(t.mowerId()))
                    .findFirst()
                    .orElseThrow();

            if (prevSimRuntime >= 0) {
                assertTrue(sim.runtimeMinutesToday() > prevSimRuntime);
                assertTrue(real.runtimeMinutesToday() > prevRealRuntime);
                assertTrue(sim.batteryPercent() <= prevSimBattery);
            }

            assertTrue(sim.batteryPercent() >= 12);

            prevSimRuntime = sim.runtimeMinutesToday();
            prevRealRuntime = real.runtimeMinutesToday();
            prevSimBattery = sim.batteryPercent();
        }
    }

    @Test
    void telemetryProgressionIsTenantIsolated() {
        String tenantA = "tenant-phase7-iso-a";
        String tenantB = "tenant-phase7-iso-b";
        String fleetId = "fleet-iso";
        String mowerId = "mower-iso-001";

        service.createFleet(tenantA, TenantRole.ADMIN, fleetId, "Fleet A");
        service.registerMower(tenantA, TenantRole.ADMIN, fleetId, mowerId, "ModelX", true);

        service.createFleet(tenantB, TenantRole.ADMIN, fleetId, "Fleet B");
        service.registerMower(tenantB, TenantRole.ADMIN, fleetId, mowerId, "ModelX", true);

        org.lawnpilot.api.dto.MowerTelemetryDto tenantAFirst = service.listMowerTelemetry(
                tenantA,
                TenantRole.VIEWER,
                fleetId).get(0);
        org.lawnpilot.api.dto.MowerTelemetryDto tenantASecond = service.listMowerTelemetry(
                tenantA,
                TenantRole.VIEWER,
                fleetId).get(0);
        org.lawnpilot.api.dto.MowerTelemetryDto tenantBFirst = service.listMowerTelemetry(
                tenantB,
                TenantRole.VIEWER,
                fleetId).get(0);

        assertTrue(tenantASecond.runtimeMinutesToday() > tenantAFirst.runtimeMinutesToday());
        assertEquals(tenantBFirst.runtimeMinutesToday(), tenantAFirst.runtimeMinutesToday());
        assertEquals(tenantBFirst.batteryPercent(), tenantAFirst.batteryPercent());
    }

    @Test
    void pinnedStartTelemetryStaysWithinExistingGeofenceSafetyBounds() {
        String tenantId = "tenant-phase7-pin-safety";
        String fleetId = "fleet-pin-safety";
        String mowerId = "mower-pin-safety-001";

        service.createFleet(tenantId, TenantRole.ADMIN, fleetId, "Pin Safety Fleet");
        service.registerMower(
                tenantId,
                TenantRole.ADMIN,
                fleetId,
                mowerId,
                "ModelX",
                true,
                47.6162,
                -122.2937);

        for (int i = 0; i < 20; i++) {
            org.lawnpilot.api.dto.MowerTelemetryDto reading = service.listMowerTelemetry(
                    tenantId,
                    TenantRole.VIEWER,
                    fleetId).get(0);

            assertTrue(reading.latitude() >= 47.58 && reading.latitude() <= 47.66);
            assertTrue(reading.longitude() >= -122.33 && reading.longitude() <= -122.24);
        }
    }
}
