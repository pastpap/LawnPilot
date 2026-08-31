import type { components } from "../generated/api";

export type TenantRole = "ADMIN" | "OPERATOR" | "VIEWER";

export type SimulationRequestDto = components["schemas"]["SimulationRequestDto"];
export type SimulationResponseDto = components["schemas"]["SimulationResponseDto"];

export interface FleetCreateRequestDto {
    fleetId: string;
    displayName: string;
}

export interface FleetDto {
    fleetId: string;
    displayName: string;
    mowerCount: number;
}

export interface MowerRegisterRequestDto {
    mowerId: string;
    model: string;
}

export interface MowerDto {
    mowerId: string;
    model: string;
    registeredAt: string;
}

export interface MowerTelemetryDto {
    mowerId: string;
    fleetId: string;
    model: string;
    status: string;
    batteryPercent: number;
    runtimeMinutesToday: number;
    latitude: number;
    longitude: number;
    areaId: string;
    areaName: string;
    targetCoverageHa: number;
    coverageTodayHa: number;
}

export interface TenantSimulationHistorySummaryDto {
    tenantId: string;
    simulationRunCount: number;
    lastSimulationRunAt: string | null;
}