import { ref } from "vue";
import type { DataPoint } from "../components/TrendChart.vue";
import type { TimeSeriesPoint } from "../components/LineChart.vue";
import { listFleets, listMowerTelemetry } from "../api/tenantApi";
import type { MowerTelemetryDto, TenantRole } from "../api/types";

export type MowerStatus = "cutting" | "charging" | "idle" | "maintenance" | "transit";

export interface ServiceArea {
  id: string;
  name: string;
  tenantId: string;
  targetCoverageHa: number;
  coverageTodayHa: number;
  soilMoisturePercent: number;
  lat: number;
  lng: number;
}

export interface FleetRecord {
  fleetId: string;
  tenantId: string;
  displayName: string;
  areaIds: string[];
}

export interface MowerRecord {
  mowerId: string;
  tenantId: string;
  fleetId: string;
  areaId: string;
  model: string;
  status: MowerStatus;
  batteryPercent: number;
  runtimeMinutesToday: number;
  coverageTodayHa: number;
  lat: number;
  lng: number;
}

export interface TenantRecord {
  tenantId: string;
  displayName: string;
  region: string;
}

export interface TelemetrySnapshotMeta {
  loadedFromBackend: boolean;
  loading: boolean;
  lastSyncAt: string | null;
  error: string;
}

export const currentTenantId = ref("tenant-alpha");
export const currentFleetId = ref("fleet-north");

export const telemetryMeta = ref<TelemetrySnapshotMeta>({
  loadedFromBackend: false,
  loading: false,
  lastSyncAt: null,
  error: "",
});

export const tenants: TenantRecord[] = [
  { tenantId: "tenant-alpha", displayName: "Northside Grounds", region: "Metro North" },
  { tenantId: "tenant-beta", displayName: "Greenline Estates", region: "Metro East" },
  { tenantId: "tenant-gamma", displayName: "Parkland Ops", region: "Metro South" },
];

const seedServiceAreas: ServiceArea[] = [
  { id: "area-nr-a", name: "North Ridge A", tenantId: "tenant-alpha", targetCoverageHa: 12, coverageTodayHa: 11.2, soilMoisturePercent: 58, lat: 47.6338, lng: -122.3431 },
  { id: "area-nr-b", name: "North Ridge B", tenantId: "tenant-alpha", targetCoverageHa: 10, coverageTodayHa: 9.4, soilMoisturePercent: 62, lat: 47.6402, lng: -122.3321 },
  { id: "area-lv", name: "Lakeview", tenantId: "tenant-alpha", targetCoverageHa: 9, coverageTodayHa: 8.6, soilMoisturePercent: 54, lat: 47.6242, lng: -122.3231 },
  { id: "area-elm", name: "Elm Park", tenantId: "tenant-beta", targetCoverageHa: 15, coverageTodayHa: 13.9, soilMoisturePercent: 49, lat: 47.6141, lng: -122.3018 },
  { id: "area-sunset", name: "Sunset Hills", tenantId: "tenant-beta", targetCoverageHa: 11, coverageTodayHa: 10.7, soilMoisturePercent: 52, lat: 47.6035, lng: -122.3122 },
  { id: "area-cedar", name: "Cedar Grove", tenantId: "tenant-gamma", targetCoverageHa: 14, coverageTodayHa: 12.5, soilMoisturePercent: 56, lat: 47.5928, lng: -122.3376 },
  { id: "area-oldtown", name: "Old Town", tenantId: "tenant-gamma", targetCoverageHa: 8, coverageTodayHa: 7.1, soilMoisturePercent: 60, lat: 47.5856, lng: -122.3237 },
];

const seedFleets: FleetRecord[] = [
  { fleetId: "fleet-north", tenantId: "tenant-alpha", displayName: "North Campus", areaIds: ["area-nr-a", "area-nr-b"] },
  { fleetId: "fleet-lake", tenantId: "tenant-alpha", displayName: "Lake District", areaIds: ["area-lv"] },
  { fleetId: "fleet-east", tenantId: "tenant-beta", displayName: "East Campus", areaIds: ["area-elm"] },
  { fleetId: "fleet-sunset", tenantId: "tenant-beta", displayName: "Sunset Crew", areaIds: ["area-sunset"] },
  { fleetId: "fleet-south", tenantId: "tenant-gamma", displayName: "South Park Team", areaIds: ["area-cedar", "area-oldtown"] },
];

const seedMowers: MowerRecord[] = [
  { mowerId: "M-014", tenantId: "tenant-alpha", fleetId: "fleet-north", areaId: "area-nr-a", model: "LP-X3", status: "cutting", batteryPercent: 78, runtimeMinutesToday: 298, coverageTodayHa: 1.7, lat: 47.6342, lng: -122.3412 },
  { mowerId: "M-021", tenantId: "tenant-alpha", fleetId: "fleet-north", areaId: "area-nr-b", model: "LP-X2", status: "charging", batteryPercent: 32, runtimeMinutesToday: 241, coverageTodayHa: 1.2, lat: 47.6398, lng: -122.3334 },
  { mowerId: "M-039", tenantId: "tenant-alpha", fleetId: "fleet-lake", areaId: "area-lv", model: "LP-X4", status: "idle", batteryPercent: 66, runtimeMinutesToday: 189, coverageTodayHa: 1.1, lat: 47.6231, lng: -122.3245 },
  { mowerId: "M-048", tenantId: "tenant-alpha", fleetId: "fleet-lake", areaId: "area-lv", model: "LP-X4", status: "cutting", batteryPercent: 84, runtimeMinutesToday: 304, coverageTodayHa: 1.9, lat: 47.6256, lng: -122.3218 },
  { mowerId: "M-055", tenantId: "tenant-beta", fleetId: "fleet-east", areaId: "area-elm", model: "LP-Z1", status: "cutting", batteryPercent: 73, runtimeMinutesToday: 287, coverageTodayHa: 2.2, lat: 47.6155, lng: -122.3002 },
  { mowerId: "M-061", tenantId: "tenant-beta", fleetId: "fleet-east", areaId: "area-elm", model: "LP-Z1", status: "maintenance", batteryPercent: 11, runtimeMinutesToday: 75, coverageTodayHa: 0.4, lat: 47.6135, lng: -122.3036 },
  { mowerId: "M-068", tenantId: "tenant-beta", fleetId: "fleet-sunset", areaId: "area-sunset", model: "LP-Z2", status: "transit", batteryPercent: 57, runtimeMinutesToday: 228, coverageTodayHa: 1.3, lat: 47.6046, lng: -122.3139 },
  { mowerId: "M-083", tenantId: "tenant-beta", fleetId: "fleet-sunset", areaId: "area-sunset", model: "LP-Z2", status: "charging", batteryPercent: 41, runtimeMinutesToday: 210, coverageTodayHa: 1.0, lat: 47.6024, lng: -122.3101 },
  { mowerId: "M-102", tenantId: "tenant-gamma", fleetId: "fleet-south", areaId: "area-cedar", model: "LP-Q5", status: "cutting", batteryPercent: 88, runtimeMinutesToday: 322, coverageTodayHa: 2.5, lat: 47.5925, lng: -122.3361 },
  { mowerId: "M-109", tenantId: "tenant-gamma", fleetId: "fleet-south", areaId: "area-cedar", model: "LP-Q5", status: "idle", batteryPercent: 69, runtimeMinutesToday: 201, coverageTodayHa: 1.4, lat: 47.5911, lng: -122.3394 },
  { mowerId: "M-118", tenantId: "tenant-gamma", fleetId: "fleet-south", areaId: "area-oldtown", model: "LP-Q4", status: "cutting", batteryPercent: 76, runtimeMinutesToday: 275, coverageTodayHa: 1.8, lat: 47.5848, lng: -122.3228 },
  { mowerId: "M-122", tenantId: "tenant-gamma", fleetId: "fleet-south", areaId: "area-oldtown", model: "LP-Q4", status: "charging", batteryPercent: 28, runtimeMinutesToday: 168, coverageTodayHa: 0.9, lat: 47.5865, lng: -122.3252 },
];

const serviceAreasRef = ref<ServiceArea[]>(seedServiceAreas);
const fleetsRef = ref<FleetRecord[]>(seedFleets);
const mowersRef = ref<MowerRecord[]>(seedMowers);

let initialLoadPromise: Promise<void> | null = null;

function toMowerStatus(status: string): MowerStatus {
  if (status === "cutting" || status === "charging" || status === "idle" || status === "maintenance" || status === "transit") {
    return status;
  }
  return "idle";
}

function fromTelemetryDto(tenantId: string, dto: MowerTelemetryDto): MowerRecord {
  return {
    mowerId: dto.mowerId,
    tenantId,
    fleetId: dto.fleetId,
    areaId: dto.areaId,
    model: dto.model,
    status: toMowerStatus(dto.status),
    batteryPercent: dto.batteryPercent,
    runtimeMinutesToday: dto.runtimeMinutesToday,
    coverageTodayHa: dto.coverageTodayHa,
    lat: dto.latitude,
    lng: dto.longitude,
  };
}

function buildAreasFromTelemetry(tenantId: string, telemetry: MowerTelemetryDto[]): ServiceArea[] {
  const areaMap = new Map<string, ServiceArea>();

  telemetry.forEach((entry) => {
    const current = areaMap.get(entry.areaId);
    if (current) {
      current.coverageTodayHa = Number((current.coverageTodayHa + entry.coverageTodayHa).toFixed(1));
      current.targetCoverageHa = Number(Math.max(current.targetCoverageHa, entry.targetCoverageHa).toFixed(1));
      return;
    }

    areaMap.set(entry.areaId, {
      id: entry.areaId,
      name: entry.areaName,
      tenantId,
      targetCoverageHa: Number(entry.targetCoverageHa.toFixed(1)),
      coverageTodayHa: Number(entry.coverageTodayHa.toFixed(1)),
      soilMoisturePercent: 45 + ((entry.batteryPercent + entry.runtimeMinutesToday) % 35),
      lat: entry.latitude,
      lng: entry.longitude,
    });
  });

  return [...areaMap.values()].sort((a, b) => a.name.localeCompare(b.name));
}

async function loadTenantFromBackend(
  tenantId: string,
  role: TenantRole,
): Promise<{ fleets: FleetRecord[]; areas: ServiceArea[]; mowers: MowerRecord[] }> {
  const [backendFleets, telemetry] = await Promise.all([
    listFleets({ tenantId, role }),
    listMowerTelemetry({ tenantId, role }),
  ]);

  const areas = buildAreasFromTelemetry(tenantId, telemetry);
  const areaIdsByFleet = new Map<string, string[]>();

  telemetry.forEach((entry) => {
    const existing = areaIdsByFleet.get(entry.fleetId) ?? [];
    if (!existing.includes(entry.areaId)) {
      existing.push(entry.areaId);
      areaIdsByFleet.set(entry.fleetId, existing);
    }
  });

  const fleets: FleetRecord[] = backendFleets.map((fleet) => ({
    fleetId: fleet.fleetId,
    tenantId,
    displayName: fleet.displayName,
    areaIds: areaIdsByFleet.get(fleet.fleetId) ?? [],
  }));

  return {
    fleets,
    areas,
    mowers: telemetry.map((entry) => fromTelemetryDto(tenantId, entry)),
  };
}

export async function refreshTelemetryFromBackend(role: TenantRole = "ADMIN"): Promise<void> {
  telemetryMeta.value.loading = true;
  telemetryMeta.value.error = "";

  try {
    const tenantResults = await Promise.all(
      tenants.map((tenant) =>
        loadTenantFromBackend(tenant.tenantId, role).catch(() => ({
          fleets: seedFleets.filter((fleet) => fleet.tenantId === tenant.tenantId),
          areas: seedServiceAreas.filter((area) => area.tenantId === tenant.tenantId),
          mowers: seedMowers.filter((mower) => mower.tenantId === tenant.tenantId),
        })),
      ),
    );

    const loadedFleets = tenantResults.flatMap((result) => result.fleets);
    const loadedAreas = tenantResults.flatMap((result) => result.areas);
    const loadedMowers = tenantResults.flatMap((result) => result.mowers);

    fleetsRef.value = loadedFleets.length > 0 ? loadedFleets : seedFleets;
    serviceAreasRef.value = loadedAreas.length > 0 ? loadedAreas : seedServiceAreas;
    mowersRef.value = loadedMowers.length > 0 ? loadedMowers : seedMowers;

    telemetryMeta.value.loadedFromBackend = loadedMowers.length > 0;
    telemetryMeta.value.lastSyncAt = new Date().toISOString();
  } catch (error) {
    telemetryMeta.value.error = error instanceof Error ? error.message : "Unable to load telemetry";
    telemetryMeta.value.loadedFromBackend = false;
    fleetsRef.value = seedFleets;
    serviceAreasRef.value = seedServiceAreas;
    mowersRef.value = seedMowers;
  } finally {
    telemetryMeta.value.loading = false;
  }
}

export async function ensureTelemetryLoaded(role: TenantRole = "ADMIN"): Promise<void> {
  if (telemetryMeta.value.lastSyncAt) {
    return;
  }

  if (!initialLoadPromise) {
    initialLoadPromise = refreshTelemetryFromBackend(role).finally(() => {
      initialLoadPromise = null;
    });
  }

  await initialLoadPromise;
}

export async function refreshTenantTelemetry(
  tenantId: string,
  role: TenantRole = "ADMIN",
): Promise<void> {
  telemetryMeta.value.loading = true;
  telemetryMeta.value.error = "";

  try {
    const result = await loadTenantFromBackend(tenantId, role);
    const otherFleets = fleetsRef.value.filter((fleet) => fleet.tenantId !== tenantId);
    const otherAreas = serviceAreasRef.value.filter((area) => area.tenantId !== tenantId);
    const otherMowers = mowersRef.value.filter((mower) => mower.tenantId !== tenantId);

    fleetsRef.value = [...otherFleets, ...result.fleets];
    serviceAreasRef.value = [...otherAreas, ...result.areas];
    mowersRef.value = [...otherMowers, ...result.mowers];

    telemetryMeta.value.loadedFromBackend = result.mowers.length > 0;
    telemetryMeta.value.lastSyncAt = new Date().toISOString();
  } catch (error) {
    telemetryMeta.value.error = error instanceof Error ? error.message : "Unable to refresh tenant telemetry";
  } finally {
    telemetryMeta.value.loading = false;
  }
}

export function getTenant(tenantId: string): TenantRecord {
  return tenants.find((tenant) => tenant.tenantId === tenantId) ?? tenants[0];
}

export function getTenantFleets(tenantId: string): FleetRecord[] {
  return fleetsRef.value.filter((fleet) => fleet.tenantId === tenantId);
}

export function getTenantAreas(tenantId: string): ServiceArea[] {
  return serviceAreasRef.value.filter((area) => area.tenantId === tenantId);
}

export function getTenantMowers(tenantId: string): MowerRecord[] {
  return mowersRef.value.filter((mower) => mower.tenantId === tenantId);
}

export function getFleetMowers(fleetId: string): MowerRecord[] {
  return mowersRef.value.filter((mower) => mower.fleetId === fleetId);
}

export function getCoverageRate(tenantId: string): number {
  const areas = getTenantAreas(tenantId);
  const target = areas.reduce((sum, area) => sum + area.targetCoverageHa, 0);
  const covered = areas.reduce((sum, area) => sum + area.coverageTodayHa, 0);
  if (target === 0) return 0;
  return Number(((covered / target) * 100).toFixed(1));
}

export function getAverageBattery(tenantId: string): number {
  const tenantMowers = getTenantMowers(tenantId);
  if (tenantMowers.length === 0) return 0;
  return Math.round(
    tenantMowers.reduce((sum, mower) => sum + mower.batteryPercent, 0) /
      tenantMowers.length,
  );
}

export function getStatusBreakdown(tenantId: string): DataPoint[] {
  const tenantMowers = getTenantMowers(tenantId);
  const statuses: MowerStatus[] = ["cutting", "charging", "idle", "maintenance", "transit"];

  return statuses.map((status) => ({
    label: status,
    value: tenantMowers.filter((mower) => mower.status === status).length,
  }));
}

export function getAreaHealthData(tenantId: string): DataPoint[] {
  return getTenantAreas(tenantId).map((area) => ({
    label: area.name,
    value: Math.round((area.coverageTodayHa / Math.max(area.targetCoverageHa, 0.1)) * 100),
  }));
}

function clamp(min: number, max: number, value: number): number {
  return Math.max(min, Math.min(max, value));
}

export function getWeeklyRuntimeTrend(tenantId: string): TimeSeriesPoint[] {
  const tenantMowers = getTenantMowers(tenantId);
  const total = tenantMowers.reduce((sum, mower) => sum + mower.runtimeMinutesToday, 0);
  const dailyBase = total > 0 ? total / 7 : 180;
  const multipliers = [0.86, 0.95, 1.04, 1.0, 1.08, 0.93, 0.88];
  const labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

  return labels.map((label, index) => ({
    time: label,
    value: Math.round(dailyBase * multipliers[index]),
  }));
}

export function getMonthlyCoverageTrend(tenantId: string): TimeSeriesPoint[] {
  const currentCoverage = getCoverageRate(tenantId);
  const labels = ["Jan", "Feb", "Mar", "Apr", "May", "Jun"];
  const deltas = [-9, -7, -5, -3, -1, 0];

  return labels.map((label, index) => ({
    time: label,
    value: Number(clamp(40, 100, currentCoverage + deltas[index]).toFixed(1)),
  }));
}

export function getUtilizationTrend(tenantId: string): DataPoint[] {
  const tenantMowers = getTenantMowers(tenantId);
  const active = tenantMowers.filter((mower) => mower.status === "cutting" || mower.status === "transit").length;
  const utilization = tenantMowers.length === 0 ? 0 : Math.round((active / tenantMowers.length) * 100);
  const deltas = [-7, -4, -2, 0, 2, -1];

  return deltas.map((delta, index) => ({
    label: `W${index + 1}`,
    value: clamp(0, 100, utilization + delta),
  }));
}
