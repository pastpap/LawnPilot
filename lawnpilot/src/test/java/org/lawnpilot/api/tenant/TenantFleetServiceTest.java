package org.lawnpilot.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lawnpilot.api.dto.FleetDto;
import org.lawnpilot.api.dto.MowerDto;
import org.lawnpilot.api.dto.MowerTelemetryDto;
import org.lawnpilot.api.dto.TenantSimulationHistorySummaryDto;
import org.lawnpilot.exceptions.ConflictException;
import org.lawnpilot.exceptions.NotFoundException;
import org.lawnpilot.exceptions.RoleAuthorizationException;
import org.lawnpilot.exceptions.TenantValidationException;

class TenantFleetServiceTest {

        private TenantFleetService tenantFleetService;

        @BeforeEach
        void setUp() {
                tenantFleetService = new TenantFleetService(new InMemoryTenantFleetRepository());
        }

        @Test
        void listFleetsAndMowersAreTenantIsolated() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "alpha-fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "model-a");

                tenantFleetService.createFleet("tenant-beta", TenantRole.ADMIN, "fleet-1", "beta-fleet");
                tenantFleetService.registerMower("tenant-beta", TenantRole.ADMIN, "fleet-1", "mower-9", "model-z");

                List<FleetDto> alphaFleets = tenantFleetService.listFleets("tenant-alpha", TenantRole.VIEWER);
                List<MowerDto> alphaMowers = tenantFleetService.listMowers("tenant-alpha", TenantRole.VIEWER,
                                "fleet-1");

                assertEquals(1, alphaFleets.size());
                assertEquals("fleet-1", alphaFleets.get(0).fleetId());
                assertEquals("alpha-fleet", alphaFleets.get(0).displayName());
                assertEquals("fleet-1-area", alphaFleets.get(0).areaId());
                assertEquals("alpha-fleet Zone", alphaFleets.get(0).areaName());
                assertTrue(alphaFleets.get(0).areaRadiusMeters() > 0d);
                assertEquals(1, alphaMowers.size());
                assertEquals("mower-1", alphaMowers.get(0).mowerId());
                assertEquals(false, alphaMowers.get(0).simulated());
        }

        @Test
        void viewerCannotCreateFleetOrRegisterMower() {
                assertThrows(RoleAuthorizationException.class,
                                () -> tenantFleetService.createFleet("tenant-alpha", TenantRole.VIEWER, "fleet-1",
                                                "fleet"));

                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "fleet");

                assertThrows(RoleAuthorizationException.class,
                                () -> tenantFleetService.registerMower("tenant-alpha", TenantRole.VIEWER, "fleet-1",
                                                "mower-1",
                                                "model-a"));
        }

        @Test
        void blankTenantIdIsRejected() {
                assertThrows(TenantValidationException.class,
                                () -> tenantFleetService.listFleets("   ", TenantRole.VIEWER));
        }

        @Test
        void simulationHistorySummaryIsTenantIsolated() {
                tenantFleetService.recordSimulationRun("tenant-alpha", TenantRole.OPERATOR);
                tenantFleetService.recordSimulationRun("tenant-alpha", TenantRole.OPERATOR);
                tenantFleetService.recordSimulationRun("tenant-beta", TenantRole.OPERATOR);

                TenantSimulationHistorySummaryDto alphaSummary = tenantFleetService.getSimulationHistorySummary(
                                "tenant-alpha",
                                TenantRole.VIEWER);
                TenantSimulationHistorySummaryDto betaSummary = tenantFleetService.getSimulationHistorySummary(
                                "tenant-beta",
                                TenantRole.VIEWER);

                assertEquals(2, alphaSummary.simulationRunCount());
                assertEquals(1, betaSummary.simulationRunCount());
        }

        @Test
        void telemetryReturnsServerDrivenMowerStateAndSupportsFleetFilter() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "alpha-fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "model-a");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-2", "model-b");

                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-2", "beta-fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-2", "mower-9", "model-z");

                List<MowerTelemetryDto> allTelemetry = tenantFleetService.listMowerTelemetry("tenant-alpha",
                                TenantRole.VIEWER,
                                null);
                List<MowerTelemetryDto> fleetTelemetry = tenantFleetService.listMowerTelemetry("tenant-alpha",
                                TenantRole.VIEWER, "fleet-1");

                assertEquals(3, allTelemetry.size());
                assertEquals(2, fleetTelemetry.size());
                assertEquals("mower-1", fleetTelemetry.get(0).mowerId());
                assertEquals("fleet-1", fleetTelemetry.get(0).fleetId());
        }

        @Test
        void simulatedMowerIsActiveImmediatelyAndTelemetryCoordinatesStayInLandSafeZone() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-ready-test", "Ready Fleet");
                MowerDto mower = tenantFleetService.registerMower(
                                "tenant-alpha",
                                TenantRole.ADMIN,
                                "fleet-ready-test",
                                "mower-ready-4",
                                "model-r",
                                true);

                List<MowerTelemetryDto> telemetry = tenantFleetService.listMowerTelemetry(
                                "tenant-alpha",
                                TenantRole.VIEWER,
                                "fleet-ready-test");

                assertEquals(1, telemetry.size());
                MowerTelemetryDto telemetryDto = telemetry.get(0);

                assertEquals("mower-ready-4", telemetryDto.mowerId());
                assertEquals("cutting", telemetryDto.status());
                assertEquals("fleet-ready-test-area", telemetryDto.areaId());
                assertEquals("Ready Fleet Zone", telemetryDto.areaName());
                assertEquals(true, mower.simulated());

                assertTrue(telemetryDto.batteryPercent() >= 25 && telemetryDto.batteryPercent() <= 95);
                assertTrue(telemetryDto.runtimeMinutesToday() >= 180 && telemetryDto.runtimeMinutesToday() <= 359);
                assertTrue(telemetryDto.coverageTodayHa() > 0);
                assertTrue(telemetryDto.coverageTodayHa() <= telemetryDto.targetCoverageHa());

                assertTrue(telemetryDto.latitude() >= 47.605 && telemetryDto.latitude() <= 47.641);
                assertTrue(telemetryDto.longitude() >= -122.306 && telemetryDto.longitude() <= -122.272);
                assertNotNull(telemetryDto.model());
        }

        @Test
        void registerMowerAcceptsPinnedStartCoordinatesAndFirstTelemetryMatchesPinThenProgresses() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-pin-test", "Pinned Fleet");
                double[] fleetAnchor = fleetAnchor("tenant-alpha", "fleet-pin-test");

                tenantFleetService.registerMower(
                                "tenant-alpha",
                                TenantRole.ADMIN,
                                "fleet-pin-test",
                                "mower-pin-1",
                                "model-p",
                                true,
                                fleetAnchor[0],
                                fleetAnchor[1]);

                List<MowerTelemetryDto> firstRead = tenantFleetService.listMowerTelemetry(
                                "tenant-alpha",
                                TenantRole.VIEWER,
                                "fleet-pin-test");
                List<MowerTelemetryDto> secondRead = tenantFleetService.listMowerTelemetry(
                                "tenant-alpha",
                                TenantRole.VIEWER,
                                "fleet-pin-test");

                assertEquals(1, firstRead.size());
                assertEquals(1, secondRead.size());

                MowerTelemetryDto first = firstRead.get(0);
                MowerTelemetryDto second = secondRead.get(0);

                assertEquals(fleetAnchor[0], first.latitude(), 0.000001d);
                assertEquals(fleetAnchor[1], first.longitude(), 0.000001d);
                assertTrue(first.latitude() != second.latitude() || first.longitude() != second.longitude());
                assertTrue(second.runtimeMinutesToday() > first.runtimeMinutesToday());
        }

        @Test
        void registerMowerRejectsPinnedCoordinatesOutsideFleetGeofenceBounds() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-pin-bounds", "Bounds Fleet");

                TenantValidationException exception = assertThrows(TenantValidationException.class,
                                () -> tenantFleetService.registerMower(
                                                "tenant-alpha",
                                                TenantRole.ADMIN,
                                                "fleet-pin-bounds",
                                                "mower-pin-out",
                                                "model-p",
                                                true,
                                                47.7000,
                                                -122.1000));

                assertTrue(exception.getMessage().contains("fleet 'fleet-pin-bounds' geofence"));
                assertTrue(exception.getMessage().contains("Allowed latitude range"));
                assertTrue(exception.getMessage().contains("longitude range"));
        }

        @Test
        void registerMowerForFleetFfUsesEditedFleetGeometryForPinnedStartValidation() {
                double[] anchor = fleetAnchor("tenant-alpha", "ff");
                double updatedCenterLat = anchor[0] + 0.025;
                double updatedCenterLng = anchor[1] + 0.02;

                tenantFleetService.createFleet(
                                "tenant-alpha",
                                TenantRole.ADMIN,
                                "ff",
                                "Fleet FF",
                                null,
                                null,
                                updatedCenterLat,
                                updatedCenterLng,
                                5000d);

                MowerDto registered = tenantFleetService.registerMower(
                                "tenant-alpha",
                                TenantRole.ADMIN,
                                "ff",
                                "mower-ff-geo-1",
                                "model-r",
                                true,
                                updatedCenterLat,
                                updatedCenterLng);

                List<MowerDto> mowers = tenantFleetService.listMowers(
                                "tenant-alpha",
                                TenantRole.VIEWER,
                                "ff");

                assertEquals("mower-ff-geo-1", registered.mowerId());
                assertEquals(true, registered.simulated());
                assertEquals(1, mowers.size());
                assertEquals("mower-ff-geo-1", mowers.get(0).mowerId());
        }

        @Test
        void nonSimulatedRegistrationIgnoresPinnedCoordinatesFromUiPayload() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "ff", "Fleet FF");

                MowerDto registered = tenantFleetService.registerMower(
                                "tenant-alpha",
                                TenantRole.ADMIN,
                                "ff",
                                "mower-ff-1",
                                "model-r",
                                false,
                                47.7000,
                                -122.1000);

                assertEquals("mower-ff-1", registered.mowerId());
                assertEquals(false, registered.simulated());

                List<MowerTelemetryDto> telemetry = tenantFleetService.listMowerTelemetry(
                                "tenant-alpha",
                                TenantRole.VIEWER,
                                "ff");
                assertEquals(1, telemetry.size());
                assertEquals("mower-ff-1", telemetry.get(0).mowerId());
        }

        @Test
        void registerMowerRejectsSinglePinnedCoordinateWithClearMessage() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-pin-partial", "Partial Fleet");

                TenantValidationException exception = assertThrows(TenantValidationException.class,
                                () -> tenantFleetService.registerMower(
                                                "tenant-alpha",
                                                TenantRole.ADMIN,
                                                "fleet-pin-partial",
                                                "mower-pin-partial",
                                                "model-p",
                                                true,
                                                47.6222,
                                                null));

                assertEquals(
                                "startLatitude and startLongitude must both be provided when setting pinned mower coordinates.",
                                exception.getMessage());
        }

        @Test
        void invalidPinnedCoordinateRequestDoesNotBlockSubsequentNoPinRegistration() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-pin-recovery",
                                "Recovery Fleet");

                assertThrows(TenantValidationException.class,
                                () -> tenantFleetService.registerMower(
                                                "tenant-alpha",
                                                TenantRole.ADMIN,
                                                "fleet-pin-recovery",
                                                "mower-pin-recovery",
                                                "model-r",
                                                true,
                                                47.7000,
                                                -122.1000));

                MowerDto registered = tenantFleetService.registerMower(
                                "tenant-alpha",
                                TenantRole.ADMIN,
                                "fleet-pin-recovery",
                                "mower-no-pin-recovery",
                                "model-r",
                                false,
                                null,
                                null);

                assertEquals("mower-no-pin-recovery", registered.mowerId());
                assertEquals(false, registered.simulated());

                List<MowerDto> mowers = tenantFleetService.listMowers(
                                "tenant-alpha",
                                TenantRole.VIEWER,
                                "fleet-pin-recovery");
                assertEquals(1, mowers.size());
                assertEquals("mower-no-pin-recovery", mowers.get(0).mowerId());
        }

        @Test
        void updateFleetPersistsDisplayNameAndAreaAssociationForTelemetry() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "LP-X");

                FleetDto updatedFleet = tenantFleetService.updateFleet(
                                "tenant-alpha",
                                TenantRole.OPERATOR,
                                "fleet-1",
                                "North Crew",
                                "north-zone",
                                "North Zone");

                List<FleetDto> fleets = tenantFleetService.listFleets("tenant-alpha", TenantRole.VIEWER);
                List<MowerTelemetryDto> telemetry = tenantFleetService.listMowerTelemetry(
                                "tenant-alpha",
                                TenantRole.VIEWER,
                                "fleet-1");

                assertEquals("North Crew", updatedFleet.displayName());
                assertEquals("north-zone", updatedFleet.areaId());
                assertEquals("North Zone", updatedFleet.areaName());
                assertEquals("North Crew", fleets.get(0).displayName());
                assertEquals("north-zone", telemetry.get(0).areaId());
                assertEquals("North Zone", telemetry.get(0).areaName());
        }

        @Test
        void updateFleetRejectsAreaIdWithoutAreaName() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");

                TenantValidationException exception = assertThrows(TenantValidationException.class,
                                () -> tenantFleetService.updateFleet(
                                                "tenant-alpha",
                                                TenantRole.ADMIN,
                                                "fleet-1",
                                                "North Crew",
                                                "north-zone",
                                                null));

                assertEquals("areaName must be provided when areaId is set for fleet area association.",
                                exception.getMessage());
        }

        @Test
        void updateFleetCanPersistCircleGeometryWithoutAreaIdChange() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "LP-X");

                FleetDto updatedFleet = tenantFleetService.updateFleet(
                                "tenant-alpha",
                                TenantRole.ADMIN,
                                "fleet-1",
                                "Alpha Fleet",
                                null,
                                "Circle North",
                                47.612345,
                                -122.30321,
                                275.5);

                List<FleetDto> fleets = tenantFleetService.listFleets("tenant-alpha", TenantRole.VIEWER);

                assertEquals("fleet-1-area", updatedFleet.areaId());
                assertEquals("Circle North", updatedFleet.areaName());
                assertEquals(47.612345, updatedFleet.areaCenterLat());
                assertEquals(-122.30321, updatedFleet.areaCenterLng());
                assertEquals(275.5, updatedFleet.areaRadiusMeters());

                assertEquals(1, fleets.size());
                assertEquals("Circle North", fleets.get(0).areaName());
                assertEquals(47.612345, fleets.get(0).areaCenterLat());
                assertEquals(-122.30321, fleets.get(0).areaCenterLng());
                assertEquals(275.5, fleets.get(0).areaRadiusMeters());
        }

        @Test
        void updateFleetRejectsPartialCircleGeometry() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");

                TenantValidationException exception = assertThrows(TenantValidationException.class,
                                () -> tenantFleetService.updateFleet(
                                                "tenant-alpha",
                                                TenantRole.ADMIN,
                                                "fleet-1",
                                                "North Crew",
                                                null,
                                                "North Circle",
                                                47.61001,
                                                null,
                                                220.0));

                assertEquals(
                                "areaCenterLat, areaCenterLng, and areaRadiusMeters must all be provided when setting fleet area geometry.",
                                exception.getMessage());
        }

        @Test
        void updateFleetRejectsInvalidCircleRadius() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");

                TenantValidationException exception = assertThrows(TenantValidationException.class,
                                () -> tenantFleetService.updateFleet(
                                                "tenant-alpha",
                                                TenantRole.ADMIN,
                                                "fleet-1",
                                                "North Crew",
                                                null,
                                                "North Circle",
                                                47.61001,
                                                -122.30199,
                                                0.0));

                assertEquals("areaRadiusMeters must be greater than 0.", exception.getMessage());
        }

        @Test
        void updateMowerCanChangeModelWithinFleet() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "LP-X");

                MowerDto updatedMower = tenantFleetService.updateMower(
                                "tenant-alpha",
                                TenantRole.OPERATOR,
                                "fleet-1",
                                "mower-1",
                                "LP-X2",
                                null);

                List<MowerDto> mowers = tenantFleetService.listMowers("tenant-alpha", TenantRole.VIEWER, "fleet-1");

                assertEquals("LP-X2", updatedMower.model());
                assertEquals("LP-X2", mowers.get(0).model());
        }

        @Test
        void updateMowerCanReassignFleetAndTelemetryReferencesNewFleet() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-2", "Beta Fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "LP-X");

                tenantFleetService.updateMower(
                                "tenant-alpha",
                                TenantRole.ADMIN,
                                "fleet-1",
                                "mower-1",
                                "LP-X3",
                                "fleet-2");

                List<MowerDto> fleetOneMowers = tenantFleetService.listMowers("tenant-alpha", TenantRole.VIEWER,
                                "fleet-1");
                List<MowerDto> fleetTwoMowers = tenantFleetService.listMowers("tenant-alpha", TenantRole.VIEWER,
                                "fleet-2");
                List<MowerTelemetryDto> telemetry = tenantFleetService.listMowerTelemetry(
                                "tenant-alpha",
                                TenantRole.VIEWER,
                                "fleet-2");

                assertEquals(0, fleetOneMowers.size());
                assertEquals(1, fleetTwoMowers.size());
                assertEquals("LP-X3", fleetTwoMowers.get(0).model());
                assertEquals("fleet-2", telemetry.get(0).fleetId());
                assertEquals("mower-1", telemetry.get(0).mowerId());
        }

        @Test
        void updateMowerFailsWhenTargetFleetIsMissing() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "LP-X");

                NotFoundException exception = assertThrows(NotFoundException.class,
                                () -> tenantFleetService.updateMower(
                                                "tenant-alpha",
                                                TenantRole.ADMIN,
                                                "fleet-1",
                                                "mower-1",
                                                "LP-X2",
                                                "fleet-9"));

                assertEquals("Fleet 'fleet-9' does not exist for tenant 'tenant-alpha'.", exception.getMessage());
        }

        @Test
        void updateMowerFailsWhenTargetFleetAlreadyContainsMowerId() {
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "Alpha Fleet");
                tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-2", "Beta Fleet");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-1", "mower-1", "LP-X");
                tenantFleetService.registerMower("tenant-alpha", TenantRole.ADMIN, "fleet-2", "mower-1", "LP-Y");

                ConflictException exception = assertThrows(ConflictException.class,
                                () -> tenantFleetService.updateMower(
                                                "tenant-alpha",
                                                TenantRole.ADMIN,
                                                "fleet-1",
                                                "mower-1",
                                                "LP-X2",
                                                "fleet-2"));

                assertEquals("Mower 'mower-1' already exists in fleet 'fleet-2'.", exception.getMessage());
        }

        private static double[] fleetAnchor(String tenantId, String fleetId) {
                double[] tenantAnchor = tenantAnchor(tenantId);
                int fleetOffset = stableHash(fleetId) % 7;
                return new double[] {
                                tenantAnchor[0] + ((fleetOffset - 3) * 0.004),
                                tenantAnchor[1] + ((fleetOffset - 3) * 0.0035)
                };
        }

        private static double[] tenantAnchor(String tenantId) {
                if (tenantId.contains("beta")) {
                        return new double[] { 47.6134, -122.2874 };
                }
                if (tenantId.contains("gamma")) {
                        return new double[] { 47.6018, -122.2828 };
                }
                return new double[] { 47.6229, -122.2891 };
        }

        private static int stableHash(String value) {
                return Math.abs(value.toLowerCase(Locale.ROOT).hashCode());
        }
}
