import { requestJson } from "./http";
import type {
  FleetCreateRequestDto,
  FleetDto,
  MowerDto,
  MowerRegisterRequestDto,
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
  return requestJson<FleetDto>(`${tenantPath(request.tenantId)}/fleets`, {
    method: "POST",
    headers: roleHeader(request.role),
    body: {
      fleetId: request.fleetId,
      displayName: request.displayName,
    } satisfies FleetCreateRequestDto,
  });
}

export async function listFleets(request: TenantRequest): Promise<FleetDto[]> {
  return requestJson<FleetDto[]>(`${tenantPath(request.tenantId)}/fleets`, {
    method: "GET",
    headers: roleHeader(request.role),
  });
}

export async function registerMower(
  request: TenantRequest & { fleetId: string } & MowerRegisterRequestDto,
): Promise<MowerDto> {
  return requestJson<MowerDto>(
    `${tenantPath(request.tenantId)}/fleets/${encodeURIComponent(request.fleetId)}/mowers`,
    {
      method: "POST",
      headers: roleHeader(request.role),
      body: {
        mowerId: request.mowerId,
        model: request.model,
      } satisfies MowerRegisterRequestDto,
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