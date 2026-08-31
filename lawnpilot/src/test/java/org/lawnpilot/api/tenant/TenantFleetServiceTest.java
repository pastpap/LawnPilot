package org.lawnpilot.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lawnpilot.api.dto.FleetDto;
import org.lawnpilot.api.dto.MowerDto;
import org.lawnpilot.api.dto.TenantSimulationHistorySummaryDto;
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
        List<MowerDto> alphaMowers = tenantFleetService.listMowers("tenant-alpha", TenantRole.VIEWER, "fleet-1");

        assertEquals(1, alphaFleets.size());
        assertEquals("fleet-1", alphaFleets.get(0).fleetId());
        assertEquals("alpha-fleet", alphaFleets.get(0).displayName());
        assertEquals(1, alphaMowers.size());
        assertEquals("mower-1", alphaMowers.get(0).mowerId());
    }

    @Test
    void viewerCannotCreateFleetOrRegisterMower() {
        assertThrows(RoleAuthorizationException.class,
                () -> tenantFleetService.createFleet("tenant-alpha", TenantRole.VIEWER, "fleet-1", "fleet"));

        tenantFleetService.createFleet("tenant-alpha", TenantRole.ADMIN, "fleet-1", "fleet");

        assertThrows(RoleAuthorizationException.class,
                () -> tenantFleetService.registerMower("tenant-alpha", TenantRole.VIEWER, "fleet-1", "mower-1", "model-a"));
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

        TenantSimulationHistorySummaryDto alphaSummary =
                tenantFleetService.getSimulationHistorySummary("tenant-alpha", TenantRole.VIEWER);
        TenantSimulationHistorySummaryDto betaSummary =
                tenantFleetService.getSimulationHistorySummary("tenant-beta", TenantRole.VIEWER);

        assertEquals(2, alphaSummary.simulationRunCount());
        assertEquals(1, betaSummary.simulationRunCount());
    }
}
