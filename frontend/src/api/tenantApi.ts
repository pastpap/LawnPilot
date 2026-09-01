import { requestJson } from "./http";
import type {
    FleetCreateRequestDto,
    FleetDto,
    FleetUpdateRequestDto,
    MowerCommandRequestDto,
    MowerCommandResultDto,
    MowerDto,
    MowerRegisterRequestDto,
    MowerTelemetryDto,
    MowerUpdateRequestDto,
    SimulationRequestDto,
    SimulationResponseDto,
    TenantRole,
    TenantSimulationHistorySummaryDto,
} from "./types";

interface TenantRequest {
    tenantId: string;
    role: TenantRole;
}

function tenantPath(tenantId: string): string {
    return `/api/v1/tenants/${encodeURIComponent(tenantId)}`;
}

function roleHeader(role: TenantRole): Record<string, string> {
    return { "X-Role": role };
}

export async function createFleet(
    request: TenantRequest & FleetCreateRequestDto,
): Promise<FleetDto> {
    const body: FleetCreateRequestDto = {
        fleetId: request.fleetId,
        displayName: request.displayName,
        areaId: request.areaId,
        areaName: request.areaName,
        areaGeometryType: request.areaGeometryType,
        ...(Number.isFinite(request.areaCenterLat)
            ? { areaCenterLat: request.areaCenterLat }
            : {}),
        ...(Number.isFinite(request.areaCenterLng)
            ? { areaCenterLng: request.areaCenterLng }
            : {}),
        ...(Number.isFinite(request.areaRadiusMeters)
            ? { areaRadiusMeters: request.areaRadiusMeters }
            : {}),
    };

    return requestJson<FleetDto>(`${tenantPath(request.tenantId)}/fleets`, {
        method: "POST",
        headers: roleHeader(request.role),
        body,
    });
}

export async function listFleets(request: TenantRequest): Promise<FleetDto[]> {
    return requestJson<FleetDto[]>(`${tenantPath(request.tenantId)}/fleets`, {
        method: "GET",
        headers: roleHeader(request.role),
    });
}

export async function updateFleet(
    request: TenantRequest & { fleetId: string } & FleetUpdateRequestDto,
): Promise<FleetDto> {
    const body: FleetUpdateRequestDto = {
        displayName: request.displayName,
        areaId: request.areaId,
        areaName: request.areaName,
        areaGeometryType: request.areaGeometryType,
        ...(Number.isFinite(request.areaCenterLat)
            ? { areaCenterLat: request.areaCenterLat }
            : {}),
        ...(Number.isFinite(request.areaCenterLng)
            ? { areaCenterLng: request.areaCenterLng }
            : {}),
        ...(Number.isFinite(request.areaRadiusMeters)
            ? { areaRadiusMeters: request.areaRadiusMeters }
            : {}),
    };

    return requestJson<FleetDto>(
        `${tenantPath(request.tenantId)}/fleets/${encodeURIComponent(request.fleetId)}`,
        {
            method: "PUT",
            headers: roleHeader(request.role),
            body,
        },
    );
}

export async function registerMower(
    request: TenantRequest & { fleetId: string } & MowerRegisterRequestDto,
): Promise<MowerDto> {
    const hasValidSimulatedPin = request.simulated === true
        && Number.isFinite(request.startLatitude)
        && Number.isFinite(request.startLongitude);

    const body: MowerRegisterRequestDto = {
        mowerId: request.mowerId,
        model: request.model,
        simulated: request.simulated,
        ...(hasValidSimulatedPin
            ? {
                startLatitude: request.startLatitude,
                startLongitude: request.startLongitude,
            }
            : {}),
    };

    return requestJson<MowerDto>(
        `${tenantPath(request.tenantId)}/fleets/${encodeURIComponent(request.fleetId)}/mowers`,
        {
            method: "POST",
            headers: roleHeader(request.role),
            body,
        },
    );
}

export async function listMowers(
    request: TenantRequest & { fleetId: string },
): Promise<MowerDto[]> {
    return requestJson<MowerDto[]>(
        `${tenantPath(request.tenantId)}/fleets/${encodeURIComponent(request.fleetId)}/mowers`,
        {
            method: "GET",
            headers: roleHeader(request.role),
        },
    );
}

export async function updateMower(
    request: TenantRequest & { sourceFleetId: string; mowerId: string } & MowerUpdateRequestDto,
): Promise<MowerDto> {
    const body: MowerUpdateRequestDto = {
        model: request.model,
        ...(typeof request.fleetId === "string" && request.fleetId.trim().length > 0
            ? { fleetId: request.fleetId }
            : {}),
    };

    return requestJson<MowerDto>(
        `${tenantPath(request.tenantId)}/fleets/${encodeURIComponent(request.sourceFleetId)}/mowers/${encodeURIComponent(request.mowerId)}`,
        {
            method: "PUT",
            headers: roleHeader(request.role),
            body,
        },
    );
}

export async function listMowerTelemetry(
    request: TenantRequest & { fleetId?: string },
): Promise<MowerTelemetryDto[]> {
    const query = request.fleetId ? `?fleetId=${encodeURIComponent(request.fleetId)}` : "";
    return requestJson<MowerTelemetryDto[]>(
        `${tenantPath(request.tenantId)}/telemetry/mowers${query}`,
        {
            method: "GET",
            headers: roleHeader(request.role),
        },
    );
}

export async function runTenantSimulation(
    request: TenantRequest & SimulationRequestDto,
): Promise<SimulationResponseDto> {
    return requestJson<SimulationResponseDto>(`${tenantPath(request.tenantId)}/simulations`, {
        method: "POST",
        headers: roleHeader(request.role),
        body: {
            inputLines: request.inputLines,
        } satisfies SimulationRequestDto,
    });
}

export async function getSimulationHistorySummary(
    request: TenantRequest,
): Promise<TenantSimulationHistorySummaryDto> {
    return requestJson<TenantSimulationHistorySummaryDto>(
        `${tenantPath(request.tenantId)}/simulations/history/summary`,
        {
            method: "GET",
            headers: roleHeader(request.role),
        },
    );
}

export async function sendMowerCommand(
    request: TenantRequest & { fleetId: string } & MowerCommandRequestDto,
): Promise<MowerCommandResultDto> {
    return requestJson<MowerCommandResultDto>(
        `${tenantPath(request.tenantId)}/fleets/${encodeURIComponent(request.fleetId)}/mowers/${encodeURIComponent(request.mowerId)}/commands`,
        {
            method: "POST",
            headers: roleHeader(request.role),
            body: {
                mowerId: request.mowerId,
                commandType: request.commandType,
                metadata: request.metadata,
            } satisfies MowerCommandRequestDto,
        },
    );
}

export async function getMowerCommandHistory(
    request: TenantRequest & { fleetId: string; mowerId: string },
): Promise<MowerCommandResultDto[]> {
    return requestJson<MowerCommandResultDto[]>(
        `${tenantPath(request.tenantId)}/fleets/${encodeURIComponent(request.fleetId)}/mowers/${encodeURIComponent(request.mowerId)}/commands`,
        {
            method: "GET",
            headers: roleHeader(request.role),
        },
    );
}