import type { components } from "../generated/api";

export type TenantRole = "ADMIN" | "OPERATOR" | "VIEWER";

export type SimulationRequestDto = components["schemas"]["SimulationRequestDto"];
export type SimulationResponseDto = components["schemas"]["SimulationResponseDto"];

export interface FleetCreateRequestDto {
    fleetId: string;
    displayName: string;
    areaId?: string;
    areaName?: string;
    areaGeometryType?: "CIRCLE";
    areaCenterLat?: number;
    areaCenterLng?: number;
    areaRadiusMeters?: number;
}

export interface FleetUpdateRequestDto {
    displayName: string;
    areaId?: string;
    areaName?: string;
    areaGeometryType?: "CIRCLE";
    areaCenterLat?: number;
    areaCenterLng?: number;
    areaRadiusMeters?: number;
}

export interface FleetDto {
    fleetId: string;
    displayName: string;
    mowerCount: number;
    areaCenterLat?: number;
    areaCenterLng?: number;
    areaRadiusMeters?: number;
}

export interface MowerRegisterRequestDto {
    mowerId: string;
    model: string;
    simulated?: boolean;
    startLatitude?: number;
    startLongitude?: number;
}

export interface MowerUpdateRequestDto {
    model: string;
    fleetId?: string;
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

export type MowerCommandType = "PAUSE" | "RESUME" | "RETURN_HOME" | "OVERRIDE";

export interface MowerCommandRequestDto {
    mowerId: string;
    commandType: MowerCommandType;
    metadata?: Record<string, string | number | boolean>;
}

export interface MowerCommandResultDto {
    commandId: string;
    mowerId: string;
    commandType: MowerCommandType;
    status: "PENDING" | "ACCEPTED" | "EXECUTING" | "COMPLETED" | "FAILED" | "REJECTED";
    errorMessage?: string;
    createdAt: string;
    acknowledgedAt?: string;
    completedAt?: string;
}