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

interface NormalizedTelemetryEntry {
  mowerId: string;
  fleetId: string;
  model: string;
  status: MowerStatus;
  batteryPercent: number;
  runtimeMinutesToday: number;
  latitude: number;
  longitude: number;
  areaId: string;
  areaName: string;
  targetCoverageHa: number;
  coverageTodayHa: number;
}

export const tenants: TenantRecord[] = [
  { tenantId: "tenant-alpha", displayName: "Northside Grounds", region: "Metro North" },
  { tenantId: "tenant-beta", displayName: "Greenline Estates", region: "Metro East" },
  { tenantId: "tenant-gamma", displayName: "Parkland Ops", region: "Metro South" },
];

const MAX_BACKEND_COORDINATE_DELTA = 0.03;

const seedServiceAreas: ServiceArea[] = [
  { id: "area-nr-a", name: "North Ridge A", tenantId: "tenant-alpha", targetCoverageHa: 12, coverageTodayHa: 11.2, soilMoisturePercent: 58, lat: 47.6683, lng: -122.354 },
  { id: "area-nr-b", name: "North Ridge B", tenantId: "tenant-alpha", targetCoverageHa: 10, coverageTodayHa: 9.4, soilMoisturePercent: 62, lat: 47.6795, lng: -122.3382 },
  { id: "area-lv", name: "Lakeview", tenantId: "tenant-alpha", targetCoverageHa: 9, coverageTodayHa: 8.6, soilMoisturePercent: 54, lat: 47.6714, lng: -122.3057 },
  { id: "area-elm", name: "Elm Park", tenantId: "tenant-beta", targetCoverageHa: 15, coverageTodayHa: 13.9, soilMoisturePercent: 49, lat: 47.6409, lng: -122.294 },
  { id: "area-sunset", name: "Sunset Hills", tenantId: "tenant-beta", targetCoverageHa: 11, coverageTodayHa: 10.7, soilMoisturePercent: 52, lat: 47.6222, lng: -122.288 },
  { id: "area-cedar", name: "Cedar Grove", tenantId: "tenant-gamma", targetCoverageHa: 14, coverageTodayHa: 12.5, soilMoisturePercent: 56, lat: 47.5605, lng: -122.3023 },
  { id: "area-oldtown", name: "Old Town", tenantId: "tenant-gamma", targetCoverageHa: 8, coverageTodayHa: 7.1, soilMoisturePercent: 60, lat: 47.5796, lng: -122.3151 },
];

const seedFleets: FleetRecord[] = [
  { fleetId: "fleet-north", tenantId: "tenant-alpha", displayName: "North Campus", areaIds: ["area-nr-a", "area-nr-b"] },
  { fleetId: "fleet-lake", tenantId: "tenant-alpha", displayName: "Lake District", areaIds: ["area-lv"] },
  { fleetId: "fleet-east", tenantId: "tenant-beta", displayName: "East Campus", areaIds: ["area-elm"] },
  { fleetId: "fleet-sunset", tenantId: "tenant-beta", displayName: "Sunset Crew", areaIds: ["area-sunset"] },
  { fleetId: "fleet-south", tenantId: "tenant-gamma", displayName: "South Park Team", areaIds: ["area-cedar", "area-oldtown"] },
];

const seedMowers: MowerRecord[] = [
  { mowerId: "M-014", tenantId: "tenant-alpha", fleetId: "fleet-north", areaId: "area-nr-a", model: "LP-X3", status: "cutting", batteryPercent: 78, runtimeMinutesToday: 298, coverageTodayHa: 1.7, lat: 47.6676, lng: -122.3531 },
  { mowerId: "M-021", tenantId: "tenant-alpha", fleetId: "fleet-north", areaId: "area-nr-b", model: "LP-X2", status: "charging", batteryPercent: 32, runtimeMinutesToday: 241, coverageTodayHa: 1.2, lat: 47.6801, lng: -122.3369 },
  { mowerId: "M-039", tenantId: "tenant-alpha", fleetId: "fleet-lake", areaId: "area-lv", model: "LP-X4", status: "idle", batteryPercent: 66, runtimeMinutesToday: 189, coverageTodayHa: 1.1, lat: 47.6707, lng: -122.3042 },
  { mowerId: "M-048", tenantId: "tenant-alpha", fleetId: "fleet-lake", areaId: "area-lv", model: "LP-X4", status: "cutting", batteryPercent: 84, runtimeMinutesToday: 304, coverageTodayHa: 1.9, lat: 47.6721, lng: -122.3063 },
  { mowerId: "M-055", tenantId: "tenant-beta", fleetId: "fleet-east", areaId: "area-elm", model: "LP-Z1", status: "cutting", batteryPercent: 73, runtimeMinutesToday: 287, coverageTodayHa: 2.2, lat: 47.6414, lng: -122.2932 },
  { mowerId: "M-061", tenantId: "tenant-beta", fleetId: "fleet-east", areaId: "area-elm", model: "LP-Z1", status: "maintenance", batteryPercent: 11, runtimeMinutesToday: 75, coverageTodayHa: 0.4, lat: 47.6402, lng: -122.2951 },
  { mowerId: "M-068", tenantId: "tenant-beta", fleetId: "fleet-sunset", areaId: "area-sunset", model: "LP-Z2", status: "transit", batteryPercent: 57, runtimeMinutesToday: 228, coverageTodayHa: 1.3, lat: 47.6228, lng: -122.2867 },
  { mowerId: "M-083", tenantId: "tenant-beta", fleetId: "fleet-sunset", areaId: "area-sunset", model: "LP-Z2", status: "charging", batteryPercent: 41, runtimeMinutesToday: 210, coverageTodayHa: 1.0, lat: 47.6214, lng: -122.2892 },
  { mowerId: "M-102", tenantId: "tenant-gamma", fleetId: "fleet-south", areaId: "area-cedar", model: "LP-Q5", status: "cutting", batteryPercent: 88, runtimeMinutesToday: 322, coverageTodayHa: 2.5, lat: 47.5612, lng: -122.3011 },
  { mowerId: "M-109", tenantId: "tenant-gamma", fleetId: "fleet-south", areaId: "area-cedar", model: "LP-Q5", status: "idle", batteryPercent: 69, runtimeMinutesToday: 201, coverageTodayHa: 1.4, lat: 47.5598, lng: -122.3034 },
  { mowerId: "M-118", tenantId: "tenant-gamma", fleetId: "fleet-south", areaId: "area-oldtown", model: "LP-Q4", status: "cutting", batteryPercent: 76, runtimeMinutesToday: 275, coverageTodayHa: 1.8, lat: 47.5801, lng: -122.3142 },
  { mowerId: "M-122", tenantId: "tenant-gamma", fleetId: "fleet-south", areaId: "area-oldtown", model: "LP-Q4", status: "charging", batteryPercent: 28, runtimeMinutesToday: 168, coverageTodayHa: 0.9, lat: 47.5788, lng: -122.3162 },
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

function roundTo(value: number, decimals: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }

  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}

function toFiniteNumber(
  value: unknown,
  fallback: number,
  context: string,
): number {
  const candidate = typeof value === "number" ? value : Number(value);
  if (Number.isFinite(candidate)) {
    return candidate;
  }

  console.warn(`[telemetry] invalid numeric field (${context})`, { value, fallback });
  return fallback;
}

function toNormalizedText(value: unknown, fallback: string): string {
  if (typeof value !== "string") {
    return fallback;
  }

  const normalized = value.trim();
  return normalized.length > 0 ? normalized : fallback;
}

function getAreaAnchors(tenantId: string): Map<string, { lat: number; lng: number }> {
  const anchors = new Map<string, { lat: number; lng: number }>();

  seedServiceAreas
    .filter((area) => area.tenantId === tenantId)
    .forEach((area) => {
      anchors.set(area.id, { lat: area.lat, lng: area.lng });
    });

  serviceAreasRef.value
    .filter((area) => area.tenantId === tenantId)
    .forEach((area) => {
      if (!anchors.has(area.id)) {
        anchors.set(area.id, { lat: area.lat, lng: area.lng });
      }
    });

  return anchors;
}

function clampToAnchor(value: number, anchor: number, maxDelta: number): number {
  return Number(clamp(anchor - maxDelta, anchor + maxDelta, value).toFixed(6));
}

function clampBackendMowerCoordinates(
  entry: NormalizedTelemetryEntry,
  areaAnchors: Map<string, { lat: number; lng: number }>,
): { lat: number; lng: number } {
  const anchor = areaAnchors.get(entry.areaId);
  if (!anchor) {
    return { lat: entry.latitude, lng: entry.longitude };
  }

  return {
    lat: clampToAnchor(entry.latitude, anchor.lat, MAX_BACKEND_COORDINATE_DELTA),
    lng: clampToAnchor(entry.longitude, anchor.lng, MAX_BACKEND_COORDINATE_DELTA),
  };
}

function normalizeTelemetryEntry(
  tenantId: string,
  entry: MowerTelemetryDto,
  index: number,
  areaAnchors: Map<string, { lat: number; lng: number }>,
): NormalizedTelemetryEntry {
  const defaultAnchor = areaAnchors.values().next().value as { lat: number; lng: number } | undefined;
  const fallbackLat = defaultAnchor?.lat ?? 47.62;
  const fallbackLng = defaultAnchor?.lng ?? -122.33;

  const areaId = toNormalizedText(entry.areaId, `unknown-area-${tenantId}-${index}`);
  const fleetId = toNormalizedText(entry.fleetId, `unknown-fleet-${tenantId}`);
  const mowerId = toNormalizedText(entry.mowerId, `unknown-mower-${tenantId}-${index}`);
  const areaName = toNormalizedText(entry.areaName, areaId);
  const model = toNormalizedText(entry.model, "Unknown");

  const anchor = areaAnchors.get(areaId) ?? defaultAnchor;
  const anchorLat = anchor?.lat ?? fallbackLat;
  const anchorLng = anchor?.lng ?? fallbackLng;

  const targetCoverage = Math.max(
    0.1,
    toFiniteNumber(entry.targetCoverageHa, 0.1, `${mowerId}.targetCoverageHa`),
  );
  const coverage = Math.max(
    0,
    toFiniteNumber(entry.coverageTodayHa, 0, `${mowerId}.coverageTodayHa`),
  );
  const batteryPercent = clamp(
    0,
    100,
    toFiniteNumber(entry.batteryPercent, 0, `${mowerId}.batteryPercent`),
  );
  const runtimeMinutesToday = Math.max(
    0,
    toFiniteNumber(entry.runtimeMinutesToday, 0, `${mowerId}.runtimeMinutesToday`),
  );

  return {
    mowerId,
    fleetId,
    model,
    status: toMowerStatus(toNormalizedText(entry.status, "idle")),
    batteryPercent: Math.round(batteryPercent),
    runtimeMinutesToday: Math.round(runtimeMinutesToday),
    latitude: toFiniteNumber(entry.latitude, anchorLat, `${mowerId}.latitude`),
    longitude: toFiniteNumber(entry.longitude, anchorLng, `${mowerId}.longitude`),
    areaId,
    areaName,
    targetCoverageHa: roundTo(targetCoverage, 1),
    coverageTodayHa: roundTo(coverage, 1),
  };
}

function fromTelemetryDto(
  tenantId: string,
  dto: NormalizedTelemetryEntry,
  coordinates: { lat: number; lng: number } = { lat: dto.latitude, lng: dto.longitude },
): MowerRecord {
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
    lat: coordinates.lat,
    lng: coordinates.lng,
  };
}

function buildAreasFromTelemetry(
  tenantId: string,
  telemetry: NormalizedTelemetryEntry[],
  areaAnchors: Map<string, { lat: number; lng: number }>,
): ServiceArea[] {
  const areaMap = new Map<string, ServiceArea>();

  telemetry.forEach((entry) => {
    const current = areaMap.get(entry.areaId);
    if (current) {
      current.coverageTodayHa = roundTo(current.coverageTodayHa + entry.coverageTodayHa, 1);
      current.targetCoverageHa = roundTo(Math.max(current.targetCoverageHa, entry.targetCoverageHa), 1);
      return;
    }

    const anchor = areaAnchors.get(entry.areaId);

    areaMap.set(entry.areaId, {
      id: entry.areaId,
      name: entry.areaName,
      tenantId,
      targetCoverageHa: roundTo(entry.targetCoverageHa, 1),
      coverageTodayHa: roundTo(entry.coverageTodayHa, 1),
      soilMoisturePercent: 45 + ((entry.batteryPercent + entry.runtimeMinutesToday) % 35),
      lat: anchor?.lat ?? entry.latitude,
      lng: anchor?.lng ?? entry.longitude,
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

  const areaAnchors = getAreaAnchors(tenantId);
  const normalizedTelemetry = telemetry.map((entry, index) =>
    normalizeTelemetryEntry(tenantId, entry, index, areaAnchors),
  );
  const areas = buildAreasFromTelemetry(tenantId, normalizedTelemetry, areaAnchors);
  const areaIdsByFleet = new Map<string, string[]>();

  normalizedTelemetry.forEach((entry) => {
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
    mowers: normalizedTelemetry.map((entry) =>
      fromTelemetryDto(
        tenantId,
        entry,
        clampBackendMowerCoordinates(entry, areaAnchors),
      ),
    ),
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
    const existingTenantFleets = fleetsRef.value.filter((fleet) => fleet.tenantId === tenantId);
    const existingTenantAreas = serviceAreasRef.value.filter((area) => area.tenantId === tenantId);
    const existingTenantMowers = mowersRef.value.filter((mower) => mower.tenantId === tenantId);

    // Poll responses can be transiently empty; preserve the latest known tenant slice instead of clearing the UI.
    const nextTenantFleets =
      result.fleets.length > 0
        ? result.fleets
        : existingTenantFleets.length > 0
          ? existingTenantFleets
          : seedFleets.filter((fleet) => fleet.tenantId === tenantId);
    const nextTenantAreas =
      result.areas.length > 0
        ? result.areas
        : existingTenantAreas.length > 0
          ? existingTenantAreas
          : seedServiceAreas.filter((area) => area.tenantId === tenantId);
    const nextTenantMowers =
      result.mowers.length > 0
        ? result.mowers
        : existingTenantMowers.length > 0
          ? existingTenantMowers
          : seedMowers.filter((mower) => mower.tenantId === tenantId);

    const otherFleets = fleetsRef.value.filter((fleet) => fleet.tenantId !== tenantId);
    const otherAreas = serviceAreasRef.value.filter((area) => area.tenantId !== tenantId);
    const otherMowers = mowersRef.value.filter((mower) => mower.tenantId !== tenantId);

    fleetsRef.value = [...otherFleets, ...nextTenantFleets];
    serviceAreasRef.value = [...otherAreas, ...nextTenantAreas];
    mowersRef.value = [...otherMowers, ...nextTenantMowers];

    telemetryMeta.value.loadedFromBackend =
      telemetryMeta.value.loadedFromBackend || result.mowers.length > 0;
    telemetryMeta.value.lastSyncAt = new Date().toISOString();
  } catch (error) {
    telemetryMeta.value.error = error instanceof Error ? error.message : "Unable to refresh tenant telemetry";
  } finally {
    telemetryMeta.value.loading = false;
  }
}

function stableOffset(mowerId: string, axis: "lat" | "lng"): number {
  const source = `${mowerId}:${axis}`;
  let hash = 0;
  for (let i = 0; i < source.length; i += 1) {
    hash = (hash * 31 + source.charCodeAt(i)) % 10000;
  }
  return ((hash % 9) - 4) * 0.00018;
}

export function addSimulatedMowerToTelemetry(
  tenantId: string,
  fleetId: string,
  mowerId: string,
  model: string,
  startPin?: { lat: number; lng: number },
): void {
  const tenantAreas = serviceAreasRef.value.filter((area) => area.tenantId === tenantId);
  const fleet = fleetsRef.value.find(
    (candidate) => candidate.tenantId === tenantId && candidate.fleetId === fleetId,
  );
  const preferredAreaId = fleet?.areaIds.find((areaId) =>
    tenantAreas.some((area) => area.id === areaId),
  );
  const fallbackArea = tenantAreas[0];
  const area = tenantAreas.find((entry) => entry.id === preferredAreaId) ?? fallbackArea;

  if (!area) {
    return;
  }

  const mower: MowerRecord = {
    mowerId,
    tenantId,
    fleetId,
    areaId: area.id,
    model: model || "Simulated",
    status: "cutting",
    batteryPercent: 100,
    runtimeMinutesToday: 1,
    coverageTodayHa: 0.1,
    lat:
      Number.isFinite(startPin?.lat)
        ? Number(startPin.lat.toFixed(6))
        : Number((area.lat + stableOffset(mowerId, "lat")).toFixed(6)),
    lng:
      Number.isFinite(startPin?.lng)
        ? Number(startPin.lng.toFixed(6))
        : Number((area.lng + stableOffset(mowerId, "lng")).toFixed(6)),
  };

  const withoutTarget = mowersRef.value.filter((entry) => entry.mowerId !== mowerId);
  mowersRef.value = [...withoutTarget, mower];

  serviceAreasRef.value = serviceAreasRef.value.map((entry) => {
    if (entry.id !== area.id) {
      return entry;
    }

    return {
      ...entry,
      coverageTodayHa: Number(
        Math.min(entry.targetCoverageHa, entry.coverageTodayHa + mower.coverageTodayHa).toFixed(1),
      ),
    };
  });
}

export function upsertFleetInTelemetry(
  tenantId: string,
  fleetId: string,
  displayName: string,
  areaId: string,
): void {
  const normalizedFleetId = fleetId.trim();
  if (!normalizedFleetId) {
    return;
  }

  const normalizedAreaId = areaId.trim();
  const areaIds = normalizedAreaId ? [normalizedAreaId] : [];
  const fleetIndex = fleetsRef.value.findIndex(
    (fleet) => fleet.tenantId === tenantId && fleet.fleetId === normalizedFleetId,
  );

  if (fleetIndex >= 0) {
    const existing = fleetsRef.value[fleetIndex];
    fleetsRef.value[fleetIndex] = {
      ...existing,
      displayName: displayName.trim() || existing.displayName,
      areaIds,
    };
    return;
  }

  fleetsRef.value.push({
    tenantId,
    fleetId: normalizedFleetId,
    displayName: displayName.trim() || normalizedFleetId,
    areaIds,
  });
}

export function updateMowerInTelemetry(
  mowerId: string,
  updates: {
    tenantId: string;
    fleetId: string;
    model: string;
  },
): boolean {
  const mowerIndex = mowersRef.value.findIndex(
    (mower) => mower.mowerId === mowerId,
  );
  if (mowerIndex < 0) {
    return false;
  }

  const existing = mowersRef.value[mowerIndex];
  mowersRef.value[mowerIndex] = {
    ...existing,
    tenantId: updates.tenantId,
    fleetId: updates.fleetId,
    model: updates.model.trim() || existing.model,
  };
  return true;
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
  const target = areas.reduce((sum, area) => sum + toFiniteNumber(area.targetCoverageHa, 0, `${area.id}.targetCoverageHa`), 0);
  const covered = areas.reduce((sum, area) => sum + toFiniteNumber(area.coverageTodayHa, 0, `${area.id}.coverageTodayHa`), 0);
  if (target === 0) return 0;
  return roundTo((covered / target) * 100, 1);
}

export function getAverageBattery(tenantId: string): number {
  const tenantMowers = getTenantMowers(tenantId);
  if (tenantMowers.length === 0) return 0;
  const totalBattery = tenantMowers.reduce(
    (sum, mower) => sum + toFiniteNumber(mower.batteryPercent, 0, `${mower.mowerId}.batteryPercent`),
    0,
  );
  return Math.round(totalBattery / tenantMowers.length);
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
    value: Math.round(
      (toFiniteNumber(area.coverageTodayHa, 0, `${area.id}.coverageTodayHa`) /
        Math.max(toFiniteNumber(area.targetCoverageHa, 0.1, `${area.id}.targetCoverageHa`), 0.1)) * 100,
    ),
  }));
}

function clamp(min: number, max: number, value: number): number {
  return Math.max(min, Math.min(max, value));
}

export function getWeeklyRuntimeTrend(tenantId: string): TimeSeriesPoint[] {
  const tenantMowers = getTenantMowers(tenantId);
  const total = tenantMowers.reduce(
    (sum, mower) => sum + toFiniteNumber(mower.runtimeMinutesToday, 0, `${mower.mowerId}.runtimeMinutesToday`),
    0,
  );
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
    value: Math.round(clamp(0, 100, utilization + delta)),
  }));
}
