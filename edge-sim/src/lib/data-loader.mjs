import fs from "node:fs/promises";
import path from "node:path";
import JSON5 from "json5";

function requireString(value, field, source) {
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(`Seed mower '${field}' is invalid in ${source}`);
  }
  return value.trim();
}

function toNumber(value, fallback = 0) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizeMower(raw, source) {
  return {
    mowerId: requireString(raw.mowerId, "mowerId", source),
    tenantId: requireString(raw.tenantId, "tenantId", source),
    fleetId: requireString(raw.fleetId, "fleetId", source),
    areaId: typeof raw.areaId === "string" ? raw.areaId : `${raw.fleetId}-area`,
    model: requireString(raw.model, "model", source),
    status: typeof raw.status === "string" ? raw.status : "idle",
    batteryPercent: toNumber(raw.batteryPercent, 75),
    runtimeMinutesToday: toNumber(raw.runtimeMinutesToday, 0),
    coverageTodayHa: toNumber(raw.coverageTodayHa, 0),
    lat: toNumber(raw.lat, 47.6314),
    lng: toNumber(raw.lng, -122.3349),
  };
}

async function loadFromTsSeed(filePath) {
  const source = await fs.readFile(filePath, "utf8");
  const match = source.match(/const\s+seedMowers[\s\S]*?=\s*(\[[\s\S]*?\]);/);
  if (!match) {
    throw new Error(`Could not locate seedMowers array in ${filePath}`);
  }
  const parsed = JSON5.parse(match[1]);
  if (!Array.isArray(parsed)) {
    throw new Error(`seedMowers in ${filePath} is not an array`);
  }
  return parsed.map((item) => normalizeMower(item, filePath));
}

async function loadFromJson(filePath) {
  const source = await fs.readFile(filePath, "utf8");
  const parsed = JSON.parse(source);
  const rows = Array.isArray(parsed) ? parsed : parsed?.mowers;
  if (!Array.isArray(rows)) {
    throw new Error(
      `Expected an array or { mowers: [] } JSON shape in ${filePath}`,
    );
  }
  return rows.map((item) => normalizeMower(item, filePath));
}

export async function loadSeedMowers(config) {
  const resolvedPath = path.resolve(config.cwd, config.mowersFile);
  const ext = path.extname(resolvedPath).toLowerCase();

  const mowers =
    ext === ".json"
      ? await loadFromJson(resolvedPath)
      : await loadFromTsSeed(resolvedPath);

  const tenantFilter = config.tenantFilter;
  const filtered =
    tenantFilter.length === 0
      ? mowers
      : mowers.filter((mower) => tenantFilter.includes(mower.tenantId));

  if (filtered.length === 0) {
    throw new Error("No mower records matched EDGE_SIM_TENANT_FILTER");
  }

  return {
    sourcePath: resolvedPath,
    mowers: filtered,
  };
}
