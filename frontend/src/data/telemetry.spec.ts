import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MowerTelemetryDto } from "../api/types";

const tenantApiMocks = vi.hoisted(() => ({
    listFleets: vi.fn(),
    listMowerTelemetry: vi.fn(),
}));

vi.mock("../api/tenantApi", () => ({
    listFleets: tenantApiMocks.listFleets,
    listMowerTelemetry: tenantApiMocks.listMowerTelemetry,
}));

async function loadTelemetryModule() {
    return import("./telemetry");
}

function expectFiniteSeries(
    points: Array<{ label?: string; time?: string; value: number }>,
): void {
    for (const point of points) {
        expect(Number.isFinite(point.value)).toBe(true);
    }
}

function expectBoundedSeries(
    points: Array<{ value: number }>,
    min: number,
    max: number,
): void {
    for (const point of points) {
        expect(point.value).toBeGreaterThanOrEqual(min);
        expect(point.value).toBeLessThanOrEqual(max);
    }
}

function collectDashboardAndTrackingSeries(telemetry: Awaited<ReturnType<typeof loadTelemetryModule>>) {
    const tenantId = "tenant-alpha";

    return {
        statusBreakdown: telemetry.getStatusBreakdown(tenantId),
        areaHealth: telemetry.getAreaHealthData(tenantId),
        weeklyRuntime: telemetry.getWeeklyRuntimeTrend(tenantId),
        monthlyCoverage: telemetry.getMonthlyCoverageTrend(tenantId),
        utilization: telemetry.getUtilizationTrend(tenantId),
    };
}

describe("telemetry seed and ingestion sanity", () => {
    beforeEach(() => {
        vi.resetModules();
        tenantApiMocks.listFleets.mockReset();
        tenantApiMocks.listMowerTelemetry.mockReset();
    });

    it("keeps seeded mower coordinates inside known garden operating zones", async () => {
        const telemetry = await loadTelemetryModule();
        const { tenants, getTenantAreas, getTenantMowers } = telemetry;

        for (const tenant of tenants) {
            const areas = getTenantAreas(tenant.tenantId);
            const mowers = getTenantMowers(tenant.tenantId);
            const areaById = new Map(areas.map((area) => [area.id, area]));

            expect(areas.length).toBeGreaterThan(0);
            expect(mowers.length).toBeGreaterThan(0);

            for (const mower of mowers) {
                expect(Number.isFinite(mower.lat)).toBe(true);
                expect(Number.isFinite(mower.lng)).toBe(true);

                // Acceptable Seattle-area simulator zone, avoids random/ocean outliers.
                expect(mower.lat).toBeGreaterThanOrEqual(47.55);
                expect(mower.lat).toBeLessThanOrEqual(47.70);
                expect(mower.lng).toBeGreaterThanOrEqual(-122.37);
                expect(mower.lng).toBeLessThanOrEqual(-122.28);

                const area = areaById.get(mower.areaId);
                expect(area).toBeDefined();
                if (area) {
                    expect(Math.abs(mower.lat - area.lat)).toBeLessThanOrEqual(0.03);
                    expect(Math.abs(mower.lng - area.lng)).toBeLessThanOrEqual(0.03);
                }
            }
        }
    });

    it("maps backend telemetry deterministically into fleet, area, and mower records", async () => {
        tenantApiMocks.listFleets.mockResolvedValueOnce([
            { fleetId: "fleet-north", displayName: "North Campus", mowerCount: 2 },
        ]);

        const backendTelemetry: MowerTelemetryDto[] = [
            {
                mowerId: "M-900",
                fleetId: "fleet-north",
                model: "LP-X3",
                status: "cutting",
                batteryPercent: 80,
                runtimeMinutesToday: 240,
                latitude: 47.6689,
                longitude: -122.3528,
                areaId: "area-nr-a",
                areaName: "North Ridge A",
                targetCoverageHa: 12,
                coverageTodayHa: 1.4,
            },
            {
                mowerId: "M-901",
                fleetId: "fleet-north",
                model: "LP-X2",
                status: "charging",
                batteryPercent: 45,
                runtimeMinutesToday: 120,
                latitude: 47.6698,
                longitude: -122.3515,
                areaId: "area-nr-a",
                areaName: "North Ridge A",
                targetCoverageHa: 12,
                coverageTodayHa: 1.8,
            },
        ];
        tenantApiMocks.listMowerTelemetry.mockResolvedValueOnce(backendTelemetry);

        const telemetry = await loadTelemetryModule();
        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");

        const refreshedMowers = telemetry.getTenantMowers("tenant-alpha");
        const refreshedAreas = telemetry.getTenantAreas("tenant-alpha");

        expect(refreshedMowers).toHaveLength(2);
        expect(refreshedMowers[0].lat).toBe(47.6689);
        expect(refreshedMowers[1].lng).toBe(-122.3515);
        expect(refreshedMowers.map((mower) => mower.status)).toEqual(["cutting", "charging"]);

        expect(refreshedAreas).toHaveLength(1);
        expect(refreshedAreas[0].id).toBe("area-nr-a");
        expect(refreshedAreas[0].coverageTodayHa).toBe(3.2);
        expect(refreshedAreas[0].targetCoverageHa).toBe(12);

        expect(telemetry.telemetryMeta.value.loadedFromBackend).toBe(true);
    });

    it("clamps backend outlier mower coordinates to area anchor bounds", async () => {
        tenantApiMocks.listFleets.mockResolvedValueOnce([
            { fleetId: "fleet-north", displayName: "North Campus", mowerCount: 1 },
        ]);

        tenantApiMocks.listMowerTelemetry.mockResolvedValueOnce([
            {
                mowerId: "M-902",
                fleetId: "fleet-north",
                model: "LP-X3",
                status: "cutting",
                batteryPercent: 77,
                runtimeMinutesToday: 220,
                latitude: 47.72,
                longitude: -122.42,
                areaId: "area-nr-a",
                areaName: "North Ridge A",
                targetCoverageHa: 12,
                coverageTodayHa: 1.2,
            },
        ]);

        const telemetry = await loadTelemetryModule();
        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");

        const clamped = telemetry.getTenantMowers("tenant-alpha").find((mower) => mower.mowerId === "M-902");
        const area = telemetry.getTenantAreas("tenant-alpha").find((entry) => entry.id === "area-nr-a");

        expect(clamped).toBeDefined();
        expect(area).toBeDefined();
        if (!clamped || !area) {
            return;
        }

        expect(clamped.lat).toBe(47.6983);
        expect(clamped.lng).toBe(-122.384);
        expect(Math.abs(clamped.lat - area.lat)).toBeLessThanOrEqual(0.030001);
        expect(Math.abs(clamped.lng - area.lng)).toBeLessThanOrEqual(0.030001);
    });

    it("keeps backend in-bounds coordinates unchanged and within per-area bounds", async () => {
        tenantApiMocks.listFleets.mockResolvedValueOnce([
            { fleetId: "fleet-north", displayName: "North Campus", mowerCount: 2 },
        ]);

        tenantApiMocks.listMowerTelemetry.mockResolvedValueOnce([
            {
                mowerId: "M-903",
                fleetId: "fleet-north",
                model: "LP-X3",
                status: "cutting",
                batteryPercent: 70,
                runtimeMinutesToday: 200,
                latitude: 47.691,
                longitude: -122.36,
                areaId: "area-nr-a",
                areaName: "North Ridge A",
                targetCoverageHa: 12,
                coverageTodayHa: 1.0,
            },
            {
                mowerId: "M-904",
                fleetId: "fleet-north",
                model: "LP-X2",
                status: "idle",
                batteryPercent: 64,
                runtimeMinutesToday: 180,
                latitude: 47.6401,
                longitude: -122.374,
                areaId: "area-nr-a",
                areaName: "North Ridge A",
                targetCoverageHa: 12,
                coverageTodayHa: 0.9,
            },
        ]);

        const telemetry = await loadTelemetryModule();
        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");

        const mowers = telemetry.getTenantMowers("tenant-alpha");
        const areaById = new Map(telemetry.getTenantAreas("tenant-alpha").map((area) => [area.id, area]));
        const mower903 = mowers.find((mower) => mower.mowerId === "M-903");
        const mower904 = mowers.find((mower) => mower.mowerId === "M-904");

        expect(mower903?.lat).toBe(47.691);
        expect(mower903?.lng).toBe(-122.36);
        expect(mower904?.lat).toBe(47.6401);
        expect(mower904?.lng).toBe(-122.374);

        for (const mower of mowers) {
            const area = areaById.get(mower.areaId);
            expect(area).toBeDefined();
            if (area) {
                expect(Math.abs(mower.lat - area.lat)).toBeLessThanOrEqual(0.03);
                expect(Math.abs(mower.lng - area.lng)).toBeLessThanOrEqual(0.03);
            }
        }
    });

    it("adds simulated mower as cutting immediately for selected tenant fleet", async () => {
        const telemetry = await loadTelemetryModule();

        telemetry.addSimulatedMowerToTelemetry(
            "tenant-alpha",
            "fleet-north",
            "sim-seed-1",
            "LP-SIM",
        );

        const simulated = telemetry
            .getTenantMowers("tenant-alpha")
            .find((mower) => mower.mowerId === "sim-seed-1");

        expect(simulated).toBeDefined();
        expect(simulated?.status).toBe("cutting");
        expect(simulated?.fleetId).toBe("fleet-north");
        expect(simulated?.areaId).toBe("area-nr-a");
        expect(simulated?.runtimeMinutesToday).toBe(1);
    });

    it("uses explicit start pin coordinates for optimistic simulated insertion", async () => {
        const telemetry = await loadTelemetryModule();

        telemetry.addSimulatedMowerToTelemetry(
            "tenant-alpha",
            "fleet-north",
            "sim-pin-2",
            "LP-SIM",
            { lat: 47.6123456, lng: -122.3456789 },
        );

        const simulated = telemetry
            .getTenantMowers("tenant-alpha")
            .find((mower) => mower.mowerId === "sim-pin-2");

        expect(simulated).toBeDefined();
        expect(simulated?.lat).toBe(47.612346);
        expect(simulated?.lng).toBe(-122.345679);
    });

    it("keeps derived dashboard and tracking series finite with partial invalid telemetry numerics", async () => {
        tenantApiMocks.listFleets.mockResolvedValueOnce([
            { fleetId: "fleet-north", displayName: "North Campus", mowerCount: 2 },
        ]);

        tenantApiMocks.listMowerTelemetry.mockResolvedValueOnce([
            {
                mowerId: "M-950",
                fleetId: "fleet-north",
                model: "LP-X3",
                status: "cutting",
                batteryPercent: Number.NaN,
                runtimeMinutesToday: Number.NaN,
                latitude: Number.NaN,
                longitude: Number.POSITIVE_INFINITY,
                areaId: "area-nr-a",
                areaName: "North Ridge A",
                targetCoverageHa: Number.NaN,
                coverageTodayHa: Number.NaN,
            },
            {
                mowerId: "M-951",
                fleetId: "fleet-north",
                model: "LP-X2",
                status: "charging",
                batteryPercent: 72,
                runtimeMinutesToday: 180,
                latitude: 47.6685,
                longitude: -122.3534,
                areaId: "area-nr-a",
                areaName: "North Ridge A",
                targetCoverageHa: 12,
                coverageTodayHa: 1.8,
            },
        ] as MowerTelemetryDto[]);

        const telemetry = await loadTelemetryModule();
        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");

        const mowers = telemetry.getTenantMowers("tenant-alpha");
        expect(mowers).toHaveLength(2);
        for (const mower of mowers) {
            expect(Number.isFinite(mower.lat)).toBe(true);
            expect(Number.isFinite(mower.lng)).toBe(true);
            expect(Number.isFinite(mower.runtimeMinutesToday)).toBe(true);
            expect(Number.isFinite(mower.batteryPercent)).toBe(true);
            expect(Number.isFinite(mower.coverageTodayHa)).toBe(true);
        }

        const chartSeries = collectDashboardAndTrackingSeries(telemetry);
        expectFiniteSeries(chartSeries.statusBreakdown);
        expectFiniteSeries(chartSeries.areaHealth);
        expectFiniteSeries(chartSeries.weeklyRuntime);
        expectFiniteSeries(chartSeries.monthlyCoverage);
        expectFiniteSeries(chartSeries.utilization);
    });

    it("keeps chart point values finite across first refresh then first poll update", async () => {
        tenantApiMocks.listFleets.mockResolvedValue(
            [{ fleetId: "fleet-north", displayName: "North Campus", mowerCount: 1 }],
        );

        tenantApiMocks.listMowerTelemetry
            .mockResolvedValueOnce([
                {
                    mowerId: "M-960",
                    fleetId: "fleet-north",
                    model: "LP-X3",
                    status: "cutting",
                    batteryPercent: 78,
                    runtimeMinutesToday: 240,
                    latitude: 47.6688,
                    longitude: -122.3529,
                    areaId: "area-nr-a",
                    areaName: "North Ridge A",
                    targetCoverageHa: 12,
                    coverageTodayHa: 1.6,
                },
            ] as MowerTelemetryDto[])
            .mockResolvedValueOnce([
                {
                    mowerId: "M-960",
                    fleetId: "fleet-north",
                    model: "LP-X3",
                    status: "cutting",
                    batteryPercent: Number.NaN,
                    runtimeMinutesToday: Number.NaN,
                    latitude: Number.NaN,
                    longitude: Number.NaN,
                    areaId: "area-nr-a",
                    areaName: "North Ridge A",
                    targetCoverageHa: Number.NaN,
                    coverageTodayHa: Number.NaN,
                },
            ] as MowerTelemetryDto[]);

        const telemetry = await loadTelemetryModule();

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");
        const firstRefreshSeries = collectDashboardAndTrackingSeries(telemetry);
        expectFiniteSeries(firstRefreshSeries.statusBreakdown);
        expectFiniteSeries(firstRefreshSeries.areaHealth);
        expectFiniteSeries(firstRefreshSeries.weeklyRuntime);
        expectFiniteSeries(firstRefreshSeries.monthlyCoverage);
        expectFiniteSeries(firstRefreshSeries.utilization);

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");
        const firstPollSeries = collectDashboardAndTrackingSeries(telemetry);
        expectFiniteSeries(firstPollSeries.statusBreakdown);
        expectFiniteSeries(firstPollSeries.areaHealth);
        expectFiniteSeries(firstPollSeries.weeklyRuntime);
        expectFiniteSeries(firstPollSeries.monthlyCoverage);
        expectFiniteSeries(firstPollSeries.utilization);
    });

    it("preserves existing tenant snapshot when first live poll returns empty backend payloads", async () => {
        tenantApiMocks.listFleets
            .mockResolvedValueOnce([
                { fleetId: "fleet-north", displayName: "North Campus", mowerCount: 1 },
            ])
            .mockResolvedValueOnce([]);

        tenantApiMocks.listMowerTelemetry
            .mockResolvedValueOnce([
                {
                    mowerId: "M-980",
                    fleetId: "fleet-north",
                    model: "LP-X3",
                    status: "cutting",
                    batteryPercent: 81,
                    runtimeMinutesToday: 210,
                    latitude: 47.6687,
                    longitude: -122.3527,
                    areaId: "area-nr-a",
                    areaName: "North Ridge A",
                    targetCoverageHa: 12,
                    coverageTodayHa: 1.5,
                },
            ] as MowerTelemetryDto[])
            .mockResolvedValueOnce([] as MowerTelemetryDto[]);

        const telemetry = await loadTelemetryModule();

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");
        const baselineFleets = telemetry.getTenantFleets("tenant-alpha");
        const baselineAreas = telemetry.getTenantAreas("tenant-alpha");
        const baselineMowers = telemetry.getTenantMowers("tenant-alpha");

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");

        expect(telemetry.getTenantFleets("tenant-alpha")).toEqual(baselineFleets);
        expect(telemetry.getTenantAreas("tenant-alpha")).toEqual(baselineAreas);
        expect(telemetry.getTenantMowers("tenant-alpha")).toEqual(baselineMowers);
    });

    it("applies partial poll updates without dropping missing tenant fleet slice", async () => {
        tenantApiMocks.listFleets
            .mockResolvedValueOnce([
                { fleetId: "fleet-north", displayName: "North Campus", mowerCount: 1 },
            ])
            .mockResolvedValueOnce([]);

        tenantApiMocks.listMowerTelemetry
            .mockResolvedValueOnce([
                {
                    mowerId: "M-990",
                    fleetId: "fleet-north",
                    model: "LP-X3",
                    status: "cutting",
                    batteryPercent: 70,
                    runtimeMinutesToday: 180,
                    latitude: 47.6686,
                    longitude: -122.3526,
                    areaId: "area-nr-a",
                    areaName: "North Ridge A",
                    targetCoverageHa: 12,
                    coverageTodayHa: 1.2,
                },
            ] as MowerTelemetryDto[])
            .mockResolvedValueOnce([
                {
                    mowerId: "M-990",
                    fleetId: "fleet-north",
                    model: "LP-X3",
                    status: "charging",
                    batteryPercent: 55,
                    runtimeMinutesToday: 240,
                    latitude: 47.6685,
                    longitude: -122.3525,
                    areaId: "area-nr-a",
                    areaName: "North Ridge A",
                    targetCoverageHa: 12,
                    coverageTodayHa: 1.9,
                },
            ] as MowerTelemetryDto[]);

        const telemetry = await loadTelemetryModule();

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");
        const fleetsBeforePartialPoll = telemetry.getTenantFleets("tenant-alpha");

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");

        const fleetsAfterPartialPoll = telemetry.getTenantFleets("tenant-alpha");
        const mowersAfterPartialPoll = telemetry.getTenantMowers("tenant-alpha");

        expect(fleetsAfterPartialPoll).toEqual(fleetsBeforePartialPoll);
        expect(mowersAfterPartialPoll).toHaveLength(1);
        expect(mowersAfterPartialPoll[0].status).toBe("charging");
        expect(mowersAfterPartialPoll[0].runtimeMinutesToday).toBe(240);
    });

    it("applies deterministic bounded fallback outputs when telemetry numerics are invalid", async () => {
        tenantApiMocks.listFleets.mockResolvedValue(
            [{ fleetId: "fleet-north", displayName: "North Campus", mowerCount: 1 }],
        );

        const malformedTelemetry: MowerTelemetryDto[] = [
            {
                mowerId: "M-970",
                fleetId: "fleet-north",
                model: "LP-X3",
                status: "cutting",
                batteryPercent: Number.NaN,
                runtimeMinutesToday: Number.NaN,
                latitude: Number.NaN,
                longitude: Number.NaN,
                areaId: "area-nr-a",
                areaName: "North Ridge A",
                targetCoverageHa: Number.NaN,
                coverageTodayHa: Number.NaN,
            },
        ];

        tenantApiMocks.listMowerTelemetry
            .mockResolvedValueOnce(malformedTelemetry)
            .mockResolvedValueOnce(malformedTelemetry);

        const telemetry = await loadTelemetryModule();

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");
        const first = collectDashboardAndTrackingSeries(telemetry);

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");
        const second = collectDashboardAndTrackingSeries(telemetry);

        expect(first.statusBreakdown).toEqual(second.statusBreakdown);
        expect(first.areaHealth).toEqual(second.areaHealth);
        expect(first.weeklyRuntime).toEqual(second.weeklyRuntime);
        expect(first.monthlyCoverage).toEqual(second.monthlyCoverage);
        expect(first.utilization).toEqual(second.utilization);

        expectFiniteSeries(first.statusBreakdown);
        expectFiniteSeries(first.areaHealth);
        expectFiniteSeries(first.weeklyRuntime);
        expectFiniteSeries(first.monthlyCoverage);
        expectFiniteSeries(first.utilization);

        expectBoundedSeries(first.areaHealth, 0, 100);
        expectBoundedSeries(first.monthlyCoverage, 40, 100);
        expectBoundedSeries(first.utilization, 0, 100);
    });

    it("keeps initial tenant snapshot visible after first empty live refresh payload", async () => {
        tenantApiMocks.listFleets
            .mockResolvedValueOnce([
                { fleetId: "fleet-north", displayName: "North Campus", mowerCount: 1 },
            ])
            .mockResolvedValueOnce([]);

        tenantApiMocks.listMowerTelemetry
            .mockResolvedValueOnce([
                {
                    mowerId: "M-980",
                    fleetId: "fleet-north",
                    model: "LP-X3",
                    status: "cutting",
                    batteryPercent: 81,
                    runtimeMinutesToday: 251,
                    latitude: 47.6687,
                    longitude: -122.353,
                    areaId: "area-nr-a",
                    areaName: "North Ridge A",
                    targetCoverageHa: 12,
                    coverageTodayHa: 1.7,
                },
            ] as MowerTelemetryDto[])
            .mockResolvedValueOnce([] as MowerTelemetryDto[]);

        const telemetry = await loadTelemetryModule();

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");
        const initialFleets = telemetry.getTenantFleets("tenant-alpha");
        const initialAreas = telemetry.getTenantAreas("tenant-alpha");
        const initialMowers = telemetry.getTenantMowers("tenant-alpha");

        expect(initialFleets.length).toBeGreaterThan(0);
        expect(initialAreas.length).toBeGreaterThan(0);
        expect(initialMowers.length).toBeGreaterThan(0);

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");

        const afterEmptyPollFleets = telemetry.getTenantFleets("tenant-alpha");
        const afterEmptyPollAreas = telemetry.getTenantAreas("tenant-alpha");
        const afterEmptyPollMowers = telemetry.getTenantMowers("tenant-alpha");

        expect(afterEmptyPollFleets).toEqual(initialFleets);
        expect(afterEmptyPollAreas).toEqual(initialAreas);
        expect(afterEmptyPollMowers).toEqual(initialMowers);
    });

    it("keeps map and graph telemetry selectors non-empty on transient empty tenant poll", async () => {
        tenantApiMocks.listFleets
            .mockResolvedValueOnce([
                { fleetId: "fleet-north", displayName: "North Campus", mowerCount: 2 },
            ])
            .mockResolvedValueOnce([]);

        tenantApiMocks.listMowerTelemetry
            .mockResolvedValueOnce([
                {
                    mowerId: "M-981",
                    fleetId: "fleet-north",
                    model: "LP-X3",
                    status: "cutting",
                    batteryPercent: 80,
                    runtimeMinutesToday: 240,
                    latitude: 47.6687,
                    longitude: -122.353,
                    areaId: "area-nr-a",
                    areaName: "North Ridge A",
                    targetCoverageHa: 12,
                    coverageTodayHa: 1.4,
                },
                {
                    mowerId: "M-982",
                    fleetId: "fleet-north",
                    model: "LP-X2",
                    status: "charging",
                    batteryPercent: 45,
                    runtimeMinutesToday: 120,
                    latitude: 47.6692,
                    longitude: -122.3522,
                    areaId: "area-nr-a",
                    areaName: "North Ridge A",
                    targetCoverageHa: 12,
                    coverageTodayHa: 1.3,
                },
            ] as MowerTelemetryDto[])
            .mockResolvedValueOnce([] as MowerTelemetryDto[]);

        const telemetry = await loadTelemetryModule();

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");
        const baselineBreakdown = telemetry.getStatusBreakdown("tenant-alpha");
        const baselineAreaHealth = telemetry.getAreaHealthData("tenant-alpha");
        const baselineUtilization = telemetry.getUtilizationTrend("tenant-alpha");

        await telemetry.refreshTenantTelemetry("tenant-alpha", "ADMIN");

        const fleetSelector = telemetry.getTenantFleets("tenant-alpha");
        const areaSelector = telemetry.getTenantAreas("tenant-alpha");
        const mowerSelector = telemetry.getTenantMowers("tenant-alpha");
        const statusBreakdown = telemetry.getStatusBreakdown("tenant-alpha");
        const areaHealth = telemetry.getAreaHealthData("tenant-alpha");
        const weeklyRuntime = telemetry.getWeeklyRuntimeTrend("tenant-alpha");
        const monthlyCoverage = telemetry.getMonthlyCoverageTrend("tenant-alpha");
        const utilization = telemetry.getUtilizationTrend("tenant-alpha");

        expect(fleetSelector.length).toBeGreaterThan(0);
        expect(areaSelector.length).toBeGreaterThan(0);
        expect(mowerSelector.length).toBeGreaterThan(0);

        expect(statusBreakdown.length).toBeGreaterThan(0);
        expect(areaHealth.length).toBeGreaterThan(0);
        expect(weeklyRuntime.length).toBeGreaterThan(0);
        expect(monthlyCoverage.length).toBeGreaterThan(0);
        expect(utilization.length).toBeGreaterThan(0);

        expect(statusBreakdown).toEqual(baselineBreakdown);
        expect(areaHealth).toEqual(baselineAreaHealth);
        expect(utilization).toEqual(baselineUtilization);

        expectFiniteSeries(statusBreakdown);
        expectFiniteSeries(areaHealth);
        expectFiniteSeries(weeklyRuntime);
        expectFiniteSeries(monthlyCoverage);
        expectFiniteSeries(utilization);
    });
});
