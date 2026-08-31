import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./errors";
import {
    createFleet,
    getSimulationHistorySummary,
    listFleets,
    listMowerTelemetry,
    listMowers,
    registerMower,
    runTenantSimulation,
} from "./tenantApi";

describe("tenantApi", () => {
    const fetchMock = vi.fn<typeof fetch>();

    beforeEach(() => {
        vi.stubGlobal("fetch", fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        fetchMock.mockReset();
    });

    it("creates a fleet using tenant route and X-Role header", async () => {
        fetchMock.mockResolvedValueOnce(
            new Response(JSON.stringify({ fleetId: "fleet-1", displayName: "North", mowerCount: 0 }), {
                status: 200,
            }),
        );

        await createFleet({
            tenantId: "tenant-alpha",
            role: "ADMIN",
            fleetId: "fleet-1",
            displayName: "North",
        });

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const [url, requestInit] = fetchMock.mock.calls[0];
        expect(url).toContain("/api/v1/tenants/tenant-alpha/fleets");
        expect(requestInit?.method).toBe("POST");
        expect((requestInit?.headers as Record<string, string>)["X-Role"]).toBe("ADMIN");
    });

    it("lists fleets and mowers with role header", async () => {
        fetchMock.mockResolvedValueOnce(
            new Response(JSON.stringify([{ fleetId: "fleet-1", displayName: "North", mowerCount: 1 }]), {
                status: 200,
            }),
        );
        fetchMock.mockResolvedValueOnce(
            new Response(JSON.stringify([{ mowerId: "mower-1", model: "LP-X", registeredAt: "2026-08-31" }]), {
                status: 200,
            }),
        );

        const fleets = await listFleets({ tenantId: "tenant-alpha", role: "VIEWER" });
        const mowers = await listMowers({ tenantId: "tenant-alpha", fleetId: "fleet-1", role: "VIEWER" });

        expect(fleets).toHaveLength(1);
        expect(mowers).toHaveLength(1);
        expect(fetchMock).toHaveBeenCalledTimes(2);
        expect((fetchMock.mock.calls[0][1]?.headers as Record<string, string>)["X-Role"]).toBe("VIEWER");
        expect((fetchMock.mock.calls[1][1]?.headers as Record<string, string>)["X-Role"]).toBe("VIEWER");
    });

    it("registers mower and runs simulation/history endpoints", async () => {
        fetchMock.mockResolvedValueOnce(
            new Response(JSON.stringify({ mowerId: "mower-42", model: "LP-X", registeredAt: "2026-08-31T10:00:00Z" }), {
                status: 200,
            }),
        );
        fetchMock.mockResolvedValueOnce(
            new Response(JSON.stringify({ outputLines: ["1 3 N", "5 1 E"] }), { status: 200 }),
        );
        fetchMock.mockResolvedValueOnce(
            new Response(
                JSON.stringify({ tenantId: "tenant-alpha", simulationRunCount: 4, lastSimulationRunAt: "2026-08-31T10:00:00Z" }),
                { status: 200 },
            ),
        );

        const mower = await registerMower({
            tenantId: "tenant-alpha",
            role: "OPERATOR",
            fleetId: "fleet-1",
            mowerId: "mower-42",
            model: "LP-X",
        });
        const simulation = await runTenantSimulation({
            tenantId: "tenant-alpha",
            role: "OPERATOR",
            inputLines: ["5 5", "1 2 N", "LFLFLFLFF"],
        });
        const summary = await getSimulationHistorySummary({ tenantId: "tenant-alpha", role: "VIEWER" });

        expect(mower.mowerId).toBe("mower-42");
        expect(simulation.outputLines).toEqual(["1 3 N", "5 1 E"]);
        expect(summary.simulationRunCount).toBe(4);
    });

    it("loads mower telemetry from backend endpoint", async () => {
        fetchMock.mockResolvedValueOnce(
            new Response(
                JSON.stringify([
                    {
                        mowerId: "mower-1",
                        fleetId: "fleet-1",
                        model: "LP-X",
                        status: "cutting",
                        batteryPercent: 79,
                        runtimeMinutesToday: 220,
                        latitude: 47.621,
                        longitude: -122.333,
                        areaId: "fleet-1-area",
                        areaName: "North Zone",
                        targetCoverageHa: 10,
                        coverageTodayHa: 8.4,
                    },
                ]),
                { status: 200 },
            ),
        );

        const telemetry = await listMowerTelemetry({ tenantId: "tenant-alpha", role: "VIEWER" });

        expect(telemetry).toHaveLength(1);
        expect(fetchMock.mock.calls[0][0]).toContain("/api/v1/tenants/tenant-alpha/telemetry/mowers");
    });

    it("surfaces backend failures as ApiError with status", async () => {
        fetchMock.mockResolvedValueOnce(new Response("Role is invalid", { status: 400, statusText: "Bad Request" }));

        await expect(listFleets({ tenantId: "tenant-alpha", role: "VIEWER" })).rejects.toBeInstanceOf(ApiError);
    });
});