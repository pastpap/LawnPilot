<script setup lang="ts">
import { ref } from "vue";
import { toApiError, toFriendlyErrorMessage } from "./api/errors";
import { parseSimulationInput } from "./api/simulationInput";
import {
  createFleet,
  getSimulationHistorySummary,
  listFleets,
  listMowers,
  registerMower,
  runTenantSimulation,
} from "./api/tenantApi";
import type {
  FleetDto,
  MowerDto,
  TenantRole,
  TenantSimulationHistorySummaryDto,
} from "./api/types";

const sampleInput = `5 5\n1 2 N\nLFLFLFLFF\n3 3 E\nFFRFFRFRRF`;
const tenantId = ref("tenant-alpha");
const role = ref<TenantRole>("ADMIN");

const fleetId = ref("");
const fleetDisplayName = ref("");
const selectedFleetId = ref("");
const fleets = ref<FleetDto[]>([]);

const mowerId = ref("");
const mowerModel = ref("");
const mowers = ref<MowerDto[]>([]);

const inputText = ref(sampleInput);
const outputLines = ref<string[]>([]);
const historySummary = ref<TenantSimulationHistorySummaryDto | null>(null);

const error = ref("");
const statusMessage = ref("");
const loading = ref(false);

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
    if (fleetList.length === 0) {
      selectedFleetId.value = "";
      mowers.value = [];
      statusMessage.value = "No fleets found for tenant.";
      return;
    }

    const stillExists = fleetList.some((fleet) => fleet.fleetId === selectedFleetId.value);
    if (!stillExists) {
      selectedFleetId.value = fleetList[0].fleetId;
    }

    statusMessage.value = `Loaded ${fleetList.length} fleet(s).`;
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
    await onListMowers();
    await onListFleets();
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
    statusMessage.value = `Loaded ${mowerList.length} mower(s) for fleet '${normalizedFleetId}'.`;
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

function onRoleChanged(): void {
  error.value = "";
  statusMessage.value = `Role set to ${role.value}.`;
}
</script>

<template>
  <main class="panel">
    <header class="panel-header">
      <div>
        <h1>LawnPilot Tenant Console</h1>
        <p>Manage tenant fleets and run scoped simulations with role-based headers.</p>
      </div>
      <div class="role-chip">Role: {{ role }}</div>
    </header>

    <section class="section">
      <h2>Tenant Context</h2>
      <div class="grid two-col">
        <label>
          Tenant id
          <input v-model="tenantId" aria-label="Tenant id" placeholder="tenant-alpha" />
        </label>
        <label>
          Role header
          <select v-model="role" aria-label="Role" @change="onRoleChanged">
            <option value="ADMIN">ADMIN</option>
            <option value="OPERATOR">OPERATOR</option>
            <option value="VIEWER">VIEWER</option>
          </select>
        </label>
      </div>
    </section>

    <section class="section">
      <h2>Fleet Management</h2>
      <div class="grid three-col">
        <label>
          Fleet id
          <input v-model="fleetId" aria-label="Fleet id" placeholder="fleet-1" />
        </label>
        <label>
          Display name
          <input v-model="fleetDisplayName" aria-label="Fleet display name" placeholder="North Campus" />
        </label>
        <div class="actions">
          <button :disabled="loading" @click="onCreateFleet">Create fleet</button>
          <button :disabled="loading" @click="onListFleets">List fleets</button>
        </div>
      </div>

      <div class="grid two-col">
        <label>
          Selected fleet
          <select v-model="selectedFleetId" aria-label="Selected fleet">
            <option value="" disabled>Select a fleet</option>
            <option v-for="fleet in fleets" :key="fleet.fleetId" :value="fleet.fleetId">
              {{ fleet.fleetId }} ({{ fleet.displayName }})
            </option>
          </select>
        </label>
        <div class="list-card" aria-label="Fleets list">
          <strong>Fleets</strong>
          <ul>
            <li v-for="fleet in fleets" :key="fleet.fleetId">
              {{ fleet.fleetId }} - {{ fleet.displayName }} ({{ fleet.mowerCount }} mower(s))
            </li>
            <li v-if="fleets.length === 0">No fleets loaded.</li>
          </ul>
        </div>
      </div>
    </section>

    <section class="section">
      <h2>Mower Registration</h2>
      <div class="grid three-col">
        <label>
          Mower id
          <input v-model="mowerId" aria-label="Mower id" placeholder="mower-42" />
        </label>
        <label>
          Model
          <input v-model="mowerModel" aria-label="Mower model" placeholder="LP-X" />
        </label>
        <div class="actions">
          <button :disabled="loading" @click="onRegisterMower">Register mower</button>
          <button :disabled="loading" @click="onListMowers">List mowers</button>
        </div>
      </div>

      <div class="list-card" aria-label="Mowers list">
        <strong>Mowers for {{ selectedFleetId || "(none selected)" }}</strong>
        <ul>
          <li v-for="mower in mowers" :key="mower.mowerId">
            {{ mower.mowerId }} - {{ mower.model }} - {{ mower.registeredAt }}
          </li>
          <li v-if="mowers.length === 0">No mowers loaded.</li>
        </ul>
      </div>
    </section>

    <section class="section">
      <h2>Tenant Simulation</h2>
      <textarea v-model="inputText" aria-label="Simulation input" />
      <div class="actions">
        <button :disabled="loading" @click="onRunSimulation">
          {{ loading ? "Running..." : "Run tenant simulation" }}
        </button>
        <button :disabled="loading" @click="onLoadHistorySummary">Load history summary</button>
      </div>

      <div v-if="historySummary" class="list-card">
        <strong>History Summary</strong>
        <ul>
          <li>Tenant: {{ historySummary.tenantId }}</li>
          <li>Run count: {{ historySummary.simulationRunCount }}</li>
          <li>Last run at: {{ historySummary.lastSimulationRunAt ?? "N/A" }}</li>
        </ul>
      </div>

      <div v-if="outputLines.length > 0" class="output">
        <div v-for="line in outputLines" :key="line">{{ line }}</div>
      </div>
    </section>

    <p v-if="statusMessage" class="status">{{ statusMessage }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="loading" class="loading">Working...</div>
  </main>
</template>
