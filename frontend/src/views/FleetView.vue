<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { toApiError, toFriendlyErrorMessage } from "../api/errors";
import { parseSimulationInput } from "../api/simulationInput";
import {
  createFleet,
  getSimulationHistorySummary,
  listFleets,
  listMowers,
  registerMower,
  runTenantSimulation,
} from "../api/tenantApi";
import type {
  FleetDto,
  MowerDto,
  TenantRole,
  TenantSimulationHistorySummaryDto,
} from "../api/types";
import MowerMap from "../components/MowerMap.vue";
import {
  currentFleetId,
  currentTenantId,
  ensureTelemetryLoaded,
  getAverageBattery,
  getCoverageRate,
  getFleetMowers,
  getTenant,
  getTenantAreas,
  getTenantFleets,
  getTenantMowers,
  refreshTenantTelemetry,
  telemetryMeta,
  tenants,
} from "../data/telemetry";

const sampleInput = `5 5\n1 2 N\nLFLFLFLFF\n3 3 E\nFFRFFRFRRF`;
const tenantId = currentTenantId;
const role = computed<TenantRole>(() => "ADMIN");
const selectedFleetId = currentFleetId;

const fleetId = ref("");
const fleetDisplayName = ref("");
const mowerId = ref("");
const mowerModel = ref("");
const inputText = ref(sampleInput);
const outputLines = ref<string[]>([]);
const fleets = ref<FleetDto[]>([]);
const mowers = ref<MowerDto[]>([]);
const historySummary = ref<TenantSimulationHistorySummaryDto | null>(null);
const error = ref("");
const statusMessage = ref("");
const loading = ref(false);

const selectedTenant = computed(() => getTenant(tenantId.value));
const tenantFleets = computed(() => getTenantFleets(tenantId.value));
const tenantAreas = computed(() => getTenantAreas(tenantId.value));
const tenantMowers = computed(() => getTenantMowers(tenantId.value));
const selectedFleetMowers = computed(() =>
  getFleetMowers(selectedFleetId.value),
);

const fleetKpis = computed(() => [
  {
    label: "Tenant fleets",
    value: tenantFleets.value.length.toString(),
    detail: selectedTenant.value.displayName,
  },
  {
    label: "Connected mowers",
    value: tenantMowers.value.length.toString(),
    detail: `${getAverageBattery(tenantId.value)}% avg battery`,
  },
  {
    label: "Coverage rate",
    value: `${getCoverageRate(tenantId.value)}%`,
    detail: `${tenantAreas.value.length} service areas`,
  },
  {
    label: "Selected fleet",
    value: selectedFleetId.value || "none",
    detail: `${selectedFleetMowers.value.length} mower(s)`,
  },
]);

onMounted(() => {
  void ensureTelemetryLoaded("ADMIN");
});

const dataSourceLabel = computed(() => {
  if (telemetryMeta.value.loading) return "Syncing backend telemetry...";
  if (telemetryMeta.value.loadedFromBackend) return "Backend telemetry";
  if (telemetryMeta.value.error) return "Seed fallback (backend unavailable)";
  return "Seed fallback";
});

watch(
  tenantId,
  () => {
    const stillValid = tenantFleets.value.some(
      (fleet) => fleet.fleetId === selectedFleetId.value,
    );
    if (!stillValid) {
      selectedFleetId.value = tenantFleets.value[0]?.fleetId ?? "";
    }
  },
  { immediate: true },
);

function ensureTenantId(): string {
  const normalized = tenantId.value.trim();
  if (!normalized) {
    throw new Error("Tenant id is required.");
  }

  return normalized;
}

async function executeAction(action: () => Promise<void>): Promise<void> {
  loading.value = true;
  error.value = "";
  statusMessage.value = "";

  try {
    await action();
  } catch (requestError) {
    if (requestError instanceof Error && !("status" in requestError)) {
      error.value = requestError.message;
    } else {
      error.value = toFriendlyErrorMessage(toApiError(requestError));
    }
  } finally {
    loading.value = false;
  }
}

async function onCreateFleet(): Promise<void> {
  await executeAction(async () => {
    const normalizedTenantId = ensureTenantId();
    const normalizedFleetId = fleetId.value.trim();

    if (!normalizedFleetId) {
      throw new Error("Fleet id is required.");
    }

    await createFleet({
      tenantId: normalizedTenantId,
      role: role.value,
      fleetId: normalizedFleetId,
      displayName: fleetDisplayName.value.trim(),
    });

    statusMessage.value = `Fleet '${normalizedFleetId}' created.`;
    fleetId.value = "";
    fleetDisplayName.value = "";
    await refreshTenantTelemetry(normalizedTenantId, role.value);
    await onListFleets();
  });
}

async function onListFleets(): Promise<void> {
  await executeAction(async () => {
    const normalizedTenantId = ensureTenantId();
    const fleetList = await listFleets({
      tenantId: normalizedTenantId,
      role: role.value,
    });

    fleets.value = fleetList;
    statusMessage.value = `Loaded ${fleetList.length} backend fleet record(s).`;
  });
}

async function onRegisterMower(): Promise<void> {
  await executeAction(async () => {
    const normalizedTenantId = ensureTenantId();
    const normalizedFleetId = selectedFleetId.value.trim();
    const normalizedMowerId = mowerId.value.trim();

    if (!normalizedFleetId) {
      throw new Error("Select a fleet before registering a mower.");
    }

    if (!normalizedMowerId) {
      throw new Error("Mower id is required.");
    }

    await registerMower({
      tenantId: normalizedTenantId,
      role: role.value,
      fleetId: normalizedFleetId,
      mowerId: normalizedMowerId,
      model: mowerModel.value.trim(),
    });

    statusMessage.value = `Mower '${normalizedMowerId}' registered to fleet '${normalizedFleetId}'.`;
    mowerId.value = "";
    mowerModel.value = "";
    await refreshTenantTelemetry(normalizedTenantId, role.value);
    await onListMowers();
  });
}

async function onListMowers(): Promise<void> {
  await executeAction(async () => {
    const normalizedTenantId = ensureTenantId();
    const normalizedFleetId = selectedFleetId.value.trim();

    if (!normalizedFleetId) {
      throw new Error("Select a fleet to list mowers.");
    }

    const mowerList = await listMowers({
      tenantId: normalizedTenantId,
      role: role.value,
      fleetId: normalizedFleetId,
    });

    mowers.value = mowerList;
    statusMessage.value = `Loaded ${mowerList.length} backend mower record(s).`;
  });
}

async function onRunSimulation(): Promise<void> {
  await executeAction(async () => {
    const normalizedTenantId = ensureTenantId();
    const lines = parseSimulationInput(inputText.value);

    if (lines.length === 0) {
      throw new Error("Simulation input cannot be empty.");
    }

    outputLines.value = [];

    const response = await runTenantSimulation({
      tenantId: normalizedTenantId,
      role: role.value,
      inputLines: lines,
    });

    outputLines.value = response.outputLines ?? [];
    statusMessage.value = `Simulation completed with ${outputLines.value.length} output line(s).`;
  });
}

async function onLoadHistorySummary(): Promise<void> {
  await executeAction(async () => {
    const normalizedTenantId = ensureTenantId();
    historySummary.value = await getSimulationHistorySummary({
      tenantId: normalizedTenantId,
      role: role.value,
    });

    statusMessage.value = "Loaded tenant simulation history summary.";
  });
}
</script>

<template>
  <div class="fleet-view">
    <p class="data-source">Source: {{ dataSourceLabel }}</p>

    <section class="kpi-grid">
      <article
        v-for="kpi in fleetKpis"
        :key="kpi.label"
        class="panel-surface kpi-card"
      >
        <p>{{ kpi.label }}</p>
        <strong>{{ kpi.value }}</strong>
        <span>{{ kpi.detail }}</span>
      </article>
    </section>

    <section class="fleet-grid">
      <article class="panel-surface section-card map-panel">
        <header class="section-header">
          <h2>Fleet coverage map</h2>
          <div class="filter-row">
            <label>
              Tenant
              <select v-model="tenantId" aria-label="Tenant">
                <option
                  v-for="tenant in tenants"
                  :key="tenant.tenantId"
                  :value="tenant.tenantId"
                >
                  {{ tenant.displayName }}
                </option>
              </select>
            </label>
            <label>
              Fleet
              <select v-model="selectedFleetId" aria-label="Selected fleet">
                <option value="">All fleets</option>
                <option
                  v-for="fleet in tenantFleets"
                  :key="fleet.fleetId"
                  :value="fleet.fleetId"
                >
                  {{ fleet.displayName }}
                </option>
              </select>
            </label>
          </div>
        </header>
        <MowerMap
          :areas="tenantAreas"
          :mowers="
            selectedFleetMowers.length ? selectedFleetMowers : tenantMowers
          "
        />
      </article>

      <aside class="side-column">
        <article class="panel-surface section-card">
          <h2>Area targets</h2>
          <ul>
            <li v-for="area in tenantAreas" :key="area.id">
              {{ area.name }}: {{ area.coverageTodayHa.toFixed(1) }}/{{
                area.targetCoverageHa.toFixed(1)
              }}
              ha
            </li>
          </ul>
        </article>

        <article class="panel-surface section-card activity-card">
          <h2>Activity and history</h2>
          <p v-if="statusMessage" class="status">{{ statusMessage }}</p>
          <p v-if="error" class="error">{{ error }}</p>
          <p v-if="loading" class="loading">Working...</p>

          <div v-if="historySummary" class="history-box">
            <strong>History summary</strong>
            <ul>
              <li>Tenant: {{ historySummary.tenantId }}</li>
              <li>Run count: {{ historySummary.simulationRunCount }}</li>
              <li>
                Last run at: {{ historySummary.lastSimulationRunAt ?? "N/A" }}
              </li>
            </ul>
          </div>
        </article>
      </aside>
    </section>

    <section class="ops-grid">
      <article class="panel-surface section-card">
        <header class="section-header">
          <h2>Fleet operations API</h2>
          <button :disabled="loading" @click="onListFleets">List fleets</button>
        </header>
        <div class="form-grid">
          <label>
            Fleet id
            <input
              v-model="fleetId"
              aria-label="Fleet id"
              placeholder="fleet-west"
            />
          </label>
          <label>
            Display name
            <input
              v-model="fleetDisplayName"
              aria-label="Fleet display name"
              placeholder="West Grounds"
            />
          </label>
          <button :disabled="loading" @click="onCreateFleet">
            Create fleet
          </button>
        </div>
      </article>

      <article class="panel-surface section-card">
        <header class="section-header">
          <h2>Mower operations API</h2>
          <button :disabled="loading" @click="onListMowers">List mowers</button>
        </header>
        <div class="form-grid">
          <label>
            Mower id
            <input
              v-model="mowerId"
              aria-label="Mower id"
              placeholder="mower-42"
            />
          </label>
          <label>
            Model
            <input
              v-model="mowerModel"
              aria-label="Mower model"
              placeholder="LP-X"
            />
          </label>
          <button :disabled="loading" @click="onRegisterMower">
            Register mower
          </button>
        </div>
      </article>

      <article class="panel-surface section-card simulation-card">
        <header class="section-header">
          <h2>Simulation API</h2>
          <button :disabled="loading" @click="onLoadHistorySummary">
            Load history
          </button>
        </header>
        <textarea v-model="inputText" aria-label="Simulation input" />
        <button :disabled="loading" @click="onRunSimulation">
          {{ loading ? "Running..." : "Run tenant simulation" }}
        </button>

        <div
          v-if="outputLines.length > 0"
          class="output"
          aria-label="Simulation output"
        >
          <div v-for="(line, index) in outputLines" :key="`${index}-${line}`">
            {{ line }}
          </div>
        </div>
      </article>
    </section>

    <section class="panel-surface section-card">
      <h2>Backend snapshots</h2>
      <p class="mini-caption">
        These are records returned from API calls in this session.
      </p>
      <div class="snapshot-grid">
        <div>
          <h3>Fleets</h3>
          <ul>
            <li v-for="fleet in fleets" :key="fleet.fleetId">
              {{ fleet.fleetId }} - {{ fleet.displayName }}
            </li>
            <li v-if="fleets.length === 0">No backend fleet records loaded.</li>
          </ul>
        </div>
        <div>
          <h3>Mowers</h3>
          <ul>
            <li v-for="mower in mowers" :key="mower.mowerId">
              {{ mower.mowerId }} - {{ mower.model }}
            </li>
            <li v-if="mowers.length === 0">No backend mower records loaded.</li>
          </ul>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.fleet-view {
  display: grid;
  gap: 16px;
}

.data-source {
  margin: 0;
  color: var(--ink-soft);
  font-size: 0.82rem;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
  align-items: start;
}

.kpi-card {
  padding: 14px;
  display: grid;
  gap: 6px;
}

.kpi-card p {
  color: var(--ink-soft);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-size: 0.78rem;
}

.kpi-card strong {
  color: var(--ink);
  font-size: 1.25rem;
}

.kpi-card span {
  color: var(--ink-soft);
  font-size: 0.82rem;
}

.fleet-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(290px, 1fr);
  gap: 14px;
}

.section-card {
  padding: 16px;
  display: grid;
  gap: 10px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(150px, 1fr));
  gap: 10px;
}

.side-column {
  display: grid;
  gap: 12px;
}

.ops-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.form-grid {
  display: grid;
  gap: 10px;
}

textarea {
  min-height: 120px;
}

.output,
.history-box {
  border-radius: 12px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  padding: 12px;
}

.mini-caption,
.status,
.error,
.loading {
  color: var(--ink-soft);
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

h3 {
  margin: 0;
}

@media (max-width: 980px) {
  .kpi-grid,
  .fleet-grid,
  .ops-grid,
  .snapshot-grid,
  .filter-row {
    grid-template-columns: 1fr;
  }
}
</style>
