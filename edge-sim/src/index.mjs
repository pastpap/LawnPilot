import path from "node:path";
import process from "node:process";
import "dotenv/config";
import { loadSeedMowers } from "./lib/data-loader.mjs";
import { createMowerSimulator } from "./lib/simulator.mjs";

function boolFromEnv(value, defaultValue = false) {
  if (value == null || value === "") {
    return defaultValue;
  }
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

function intFromEnv(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizeBaseUrl(raw) {
  const value = (raw || "http://localhost:8080/api/v1").trim();
  return value.endsWith("/") ? value.slice(0, -1) : value;
}

function parseTenantFilter(raw) {
  if (!raw) {
    return [];
  }
  return raw
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function buildConfig() {
  return {
    cwd: process.cwd(),
    apiBaseUrl: normalizeBaseUrl(process.env.EDGE_SIM_API_BASE_URL),
    intervalMs: intFromEnv(process.env.EDGE_SIM_INTERVAL_MS, 5000),
    role: (process.env.EDGE_SIM_ROLE || "OPERATOR").trim(),
    mowersFile:
      process.env.EDGE_SIM_MOWERS_FILE || "../frontend/src/data/telemetry.ts",
    tenantFilter: parseTenantFilter(process.env.EDGE_SIM_TENANT_FILTER),
    dryRun: boolFromEnv(process.env.EDGE_SIM_DRY_RUN, false),
    maxTicks: intFromEnv(process.env.EDGE_SIM_MAX_TICKS, 0),
  };
}

function telemetryEndpoint(config, telemetry) {
  return `${config.apiBaseUrl}/tenants/${encodeURIComponent(telemetry.tenantId)}/fleets/${encodeURIComponent(telemetry.fleetId)}/mowers/${encodeURIComponent(telemetry.mowerId)}/telemetry/events`;
}

function fleetEndpoint(config, tenantId) {
  return `${config.apiBaseUrl}/tenants/${encodeURIComponent(tenantId)}/fleets`;
}

function mowerEndpoint(config, tenantId, fleetId) {
  return `${config.apiBaseUrl}/tenants/${encodeURIComponent(tenantId)}/fleets/${encodeURIComponent(fleetId)}/mowers`;
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  const bodyText = await response.text();
  let parsedBody = null;

  if (bodyText) {
    try {
      parsedBody = JSON.parse(bodyText);
    } catch {
      parsedBody = bodyText;
    }
  }

  return {
    ok: response.ok,
    status: response.status,
    body: parsedBody,
  };
}

async function ensureFleet(config, tenantId, fleetId) {
  const response = await requestJson(fleetEndpoint(config, tenantId), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Role": config.role,
    },
    body: JSON.stringify({
      fleetId,
      displayName: fleetId,
    }),
  });

  if (response.ok || response.status === 409) {
    return;
  }

  throw new Error(
    `Fleet ensure failed (${tenantId}/${fleetId}): HTTP ${response.status} ${JSON.stringify(response.body)}`,
  );
}

async function ensureMower(config, mower) {
  const response = await requestJson(
    mowerEndpoint(config, mower.tenantId, mower.fleetId),
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Role": config.role,
      },
      body: JSON.stringify({
        mowerId: mower.mowerId,
        model: mower.model,
      }),
    },
  );

  if (response.ok || response.status === 409) {
    return;
  }

  throw new Error(
    `Mower ensure failed (${mower.tenantId}/${mower.fleetId}/${mower.mowerId}): HTTP ${response.status} ${JSON.stringify(response.body)}`,
  );
}

async function ensureTopology(config, mowers) {
  const fleetKeys = new Set();
  for (const mower of mowers) {
    fleetKeys.add(`${mower.tenantId}::${mower.fleetId}`);
  }

  for (const key of fleetKeys) {
    const [tenantId, fleetId] = key.split("::");
    await ensureFleet(config, tenantId, fleetId);
  }

  for (const mower of mowers) {
    await ensureMower(config, mower);
  }
}

function buildTelemetryEventPayload(telemetry) {
  const now = new Date().toISOString();
  return {
    eventId: `evt-sim-${telemetry.mowerId}-${Date.now()}-${telemetry.sequence}`,
    eventType: "MOWER_TELEMETRY",
    eventData: JSON.stringify({
      mowerId: telemetry.mowerId,
      fleetId: telemetry.fleetId,
      tenantId: telemetry.tenantId,
      model: telemetry.model,
      areaId: telemetry.areaId,
      status: telemetry.status,
      batteryPercent: telemetry.batteryPercent,
      runtimeMinutesToday: telemetry.runtimeMinutesToday,
      coverageTodayHa: telemetry.coverageTodayHa,
      latitude: telemetry.latitude,
      longitude: telemetry.longitude,
      sequence: telemetry.sequence,
      emittedAt: now,
    }),
    recordedAt: now,
    isCommandRelated: false,
    relatedCommandId: null,
  };
}

async function sendTelemetry(config, telemetry) {
  const payload = buildTelemetryEventPayload(telemetry);
  if (config.dryRun) {
    return { dryRun: true, payload };
  }

  const response = await requestJson(telemetryEndpoint(config, telemetry), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Role": config.role,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(
      `Telemetry post failed (${telemetry.tenantId}/${telemetry.fleetId}/${telemetry.mowerId}): HTTP ${response.status} ${JSON.stringify(response.body)}`,
    );
  }

  return { dryRun: false, status: response.status };
}

async function start() {
  const config = buildConfig();
  if (config.intervalMs < 500) {
    throw new Error("EDGE_SIM_INTERVAL_MS must be >= 500");
  }

  const seed = await loadSeedMowers(config);
  const simulators = seed.mowers.map((mower) => createMowerSimulator(mower));

  console.log("[edge-sim] Loaded mowers:", simulators.length);
  console.log(
    "[edge-sim] Seed source:",
    path.relative(config.cwd, seed.sourcePath),
  );
  console.log("[edge-sim] API base:", config.apiBaseUrl);
  console.log("[edge-sim] Interval ms:", config.intervalMs);
  console.log("[edge-sim] Dry-run:", config.dryRun);

  if (!config.dryRun) {
    console.log(
      "[edge-sim] Ensuring tenant/fleet/mower topology in backend...",
    );
    await ensureTopology(config, seed.mowers);
  }

  let tickCount = 0;
  const interval = setInterval(async () => {
    tickCount += 1;
    const tickPrefix = `[edge-sim][tick:${tickCount}]`;

    try {
      const sends = simulators.map(async (simulator) => {
        const telemetry = simulator.step(config.intervalMs);
        const result = await sendTelemetry(config, telemetry);
        if (result.dryRun) {
          return `${simulator.key} status=${telemetry.status} battery=${telemetry.batteryPercent}% lat=${telemetry.latitude} lng=${telemetry.longitude}`;
        }
        return `${simulator.key} posted`;
      });

      const summaries = await Promise.all(sends);
      console.log(`${tickPrefix} streams=${summaries.length}`);
      for (const summary of summaries) {
        console.log(`${tickPrefix} ${summary}`);
      }
    } catch (error) {
      console.error(
        `${tickPrefix} error:`,
        error instanceof Error ? error.message : error,
      );
    }

    if (config.maxTicks > 0 && tickCount >= config.maxTicks) {
      console.log("[edge-sim] Reached EDGE_SIM_MAX_TICKS, stopping.");
      clearInterval(interval);
      process.exit(0);
    }
  }, config.intervalMs);

  const stop = () => {
    clearInterval(interval);
    console.log("[edge-sim] Stopped");
    process.exit(0);
  };

  process.on("SIGINT", stop);
  process.on("SIGTERM", stop);
}

start().catch((error) => {
  console.error(
    "[edge-sim] Fatal:",
    error instanceof Error ? error.message : error,
  );
  process.exit(1);
});
