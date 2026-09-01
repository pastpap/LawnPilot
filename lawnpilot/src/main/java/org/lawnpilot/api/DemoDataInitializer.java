package org.lawnpilot.api;

import org.lawnpilot.api.tenant.TenantFleetService;
import org.lawnpilot.api.tenant.TenantRole;
import org.lawnpilot.exceptions.ConflictException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds demo tenant/fleet/mower state on startup so the UI works after every
 * backend restart.
 * Coordinates and fleet IDs mirror the frontend seed data in telemetry.ts.
 */
@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final TenantFleetService tenantFleetService;

    public DemoDataInitializer(TenantFleetService tenantFleetService) {
        this.tenantFleetService = tenantFleetService;
    }

    @Override
    public void run(String... args) {
        seedFleet("tenant-alpha", "fleet-north", "North Campus", "area-nr-a", "North Ridge A", 47.6739, -122.3461,
                2500.0);
        seedFleet("tenant-alpha", "fleet-lake", "Lake District", "area-lv", "Lakeview", 47.6714, -122.3057, 1500.0);
        seedFleet("tenant-beta", "fleet-east", "East Campus", "area-elm", "Elm Park", 47.6409, -122.2940, 1500.0);
        seedFleet("tenant-beta", "fleet-sunset", "Sunset Crew", "area-sunset", "Sunset Hills", 47.6222, -122.2880,
                1500.0);
        seedFleet("tenant-gamma", "fleet-south", "South Park Team", "area-cedar", "Cedar Grove", 47.5700, -122.3087,
                2500.0);

        seedMower("tenant-alpha", "fleet-north", "M-014", "LP-X3", 47.6676, -122.3531);
        seedMower("tenant-alpha", "fleet-north", "M-021", "LP-X2", 47.6801, -122.3369);
        seedMower("tenant-alpha", "fleet-lake", "M-039", "LP-X4", 47.6707, -122.3042);
        seedMower("tenant-alpha", "fleet-lake", "M-048", "LP-X4", 47.6721, -122.3063);
        seedMower("tenant-beta", "fleet-east", "M-055", "LP-Z1", 47.6414, -122.2932);
        seedMower("tenant-beta", "fleet-east", "M-061", "LP-Z1", 47.6402, -122.2951);
        seedMower("tenant-beta", "fleet-sunset", "M-068", "LP-Z2", 47.6228, -122.2867);
        seedMower("tenant-beta", "fleet-sunset", "M-083", "LP-Z2", 47.6214, -122.2892);
        seedMower("tenant-gamma", "fleet-south", "M-102", "LP-Q5", 47.5612, -122.3011);
        seedMower("tenant-gamma", "fleet-south", "M-109", "LP-Q5", 47.5598, -122.3034);
        seedMower("tenant-gamma", "fleet-south", "M-118", "LP-Q4", 47.5801, -122.3142);
        seedMower("tenant-gamma", "fleet-south", "M-122", "LP-Q4", 47.5788, -122.3162);
    }

    private void seedFleet(
            String tenantId, String fleetId, String displayName,
            String areaId, String areaName,
            double centerLat, double centerLng, double radiusMeters) {
        try {
            tenantFleetService.createFleet(
                    tenantId, TenantRole.ADMIN, fleetId, displayName,
                    areaId, areaName, centerLat, centerLng, radiusMeters);
        } catch (ConflictException ignored) {
            // idempotent — already seeded or user-created
        }
    }

    private void seedMower(
            String tenantId, String fleetId,
            String mowerId, String model,
            double startLat, double startLng) {
        try {
            tenantFleetService.registerMower(
                    tenantId, TenantRole.ADMIN, fleetId, mowerId, model,
                    true, startLat, startLng);
        } catch (ConflictException ignored) {
            // idempotent — already seeded or user-registered
        }
    }
}
