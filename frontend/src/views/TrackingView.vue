<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import TrendChart from "../components/TrendChart.vue";
import MowerMap from "../components/MowerMap.vue";
import MowerControlPanel from "../components/MowerControlPanel.vue";
import {
  currentFleetId,
  currentTenantId,
  ensureTelemetryLoaded,
  getAreaHealthData,
  getFleetMowers,
  getStatusBreakdown,
  getTenant,
  getTenantAreas,
  getTenantFleets,
  getTenantMowers,
  telemetryMeta,
  tenants,
} from "../data/telemetry";

const tenantId = currentTenantId;
const selectedFleetId = currentFleetId;
const selectedMowerId = ref<string | null>(null);

const selectedTenant = computed(() => getTenant(tenantId.value));
const tenantFleets = computed(() => getTenantFleets(tenantId.value));
const tenantAreas = computed(() => getTenantAreas(tenantId.value));
const tenantMowers = computed(() => getTenantMowers(tenantId.value));

const mapMowers = computed(() => {
  if (!selectedFleetId.value) return tenantMowers.value;
  return getFleetMowers(selectedFleetId.value);
});

const selectedMower = computed(() => {
  if (!selectedMowerId.value) return null;
  return (
    mapMowers.value.find((m) => m.mowerId === selectedMowerId.value) || null
  );
});

const activeMowers = computed(
  () => mapMowers.value.filter((mower) => mower.status === "cutting").length,
);

const alertMowers = computed(
  () =>
    mapMowers.value.filter((mower) => mower.status === "maintenance").length,
);

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

watch(mapMowers, () => {
  if (
    selectedMowerId.value &&
    !mapMowers.value.some((m) => m.mowerId === selectedMowerId.value)
  ) {
    selectedMowerId.value = null;
  }
});

function selectMower(mowerId: string): void {
  selectedMowerId.value = selectedMowerId.value === mowerId ? null : mowerId;
}

function handleCommandSent(commandId: string): void {
  console.log("Command sent:", commandId);
}
</script>

<template>
  <div class="tracking-view">
    <header class="tracking-header panel-surface">
      <div>
        <h2>Live mower tracking</h2>
        <p>{{ selectedTenant.displayName }} - {{ selectedTenant.region }}</p>
        <p class="data-source">Source: {{ dataSourceLabel }}</p>
      </div>
      <div class="tracking-filters">
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
          <select v-model="selectedFleetId" aria-label="Fleet">
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

    <section class="status-grid">
      <article class="panel-surface status-card">
        <p>Visible mowers</p>
        <strong>{{ mapMowers.length }}</strong>
      </article>
      <article class="panel-surface status-card">
        <p>Cutting now</p>
        <strong>{{ activeMowers }}</strong>
      </article>
      <article class="panel-surface status-card">
        <p>Needs maintenance</p>
        <strong>{{ alertMowers }}</strong>
      </article>
      <article class="panel-surface status-card">
        <p>Service areas</p>
        <strong>{{ tenantAreas.length }}</strong>
      </article>
    </section>

    <section class="map-and-control-grid">
      <article class="panel-surface map-card">
        <MowerMap
          :areas="tenantAreas"
          :mowers="mapMowers"
          :selected-mower-id="selectedMowerId || undefined"
        />
      </article>

      <MowerControlPanel
        v-if="selectedMower && selectedFleetId"
        :mower="selectedMower"
        :tenant-id="tenantId"
        :fleet-id="selectedFleetId"
        role="ADMIN"
        @command-sent="handleCommandSent"
      />
    </section>

    <section class="charts-grid">
      <article class="panel-surface chart-card">
        <TrendChart
          title="Mower status breakdown"
          :data="getStatusBreakdown(tenantId)"
          :height="220"
        />
      </article>
      <article class="panel-surface chart-card">
        <TrendChart
          title="Area health score"
          :data="getAreaHealthData(tenantId)"
          :height="220"
        />
      </article>
    </section>

    <article class="panel-surface table-card">
      <h3>Tracked mowers</h3>
      <table class="mower-table">
        <thead>
          <tr>
            <th>Mower</th>
            <th>Status</th>
            <th>Battery</th>
            <th>Fleet</th>
            <th>Area</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="mower in mapMowers"
            :key="mower.mowerId"
            class="mower-row"
            :class="{ 'row-selected': selectedMowerId === mower.mowerId }"
            @click="selectMower(mower.mowerId)"
          >
            <td>{{ mower.mowerId }}</td>
            <td>{{ mower.status }}</td>
            <td>{{ mower.batteryPercent }}%</td>
            <td>{{ mower.fleetId }}</td>
            <td>{{ mower.areaId }}</td>
          </tr>
        </tbody>
      </table>
    </article>
  </div>
</template>

<style scoped>
.tracking-view {
  display: grid;
  gap: 16px;
}

.tracking-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: end;
}

.tracking-header h2 {
  margin: 0;
}

.tracking-header p {
  margin: 4px 0 0;
  color: var(--ink-soft);
}

.data-source {
  font-size: 0.8rem;
}

.tracking-filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(170px, 1fr));
  gap: 10px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-card {
  padding: 14px;
  display: grid;
  gap: 6px;
}

.status-card p {
  color: var(--ink-soft);
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.status-card strong {
  color: var(--ink);
  font-size: 1.35rem;
}

.map-card,
.chart-card,
.table-card {
  padding: 16px;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 10px;
}

th,
td {
  text-align: left;
  padding: 10px;
  border-bottom: 1px solid var(--border);
  color: var(--ink-soft);
  font-size: 0.85rem;
}

th {
  color: var(--ink);
}

.map-and-control-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  align-items: start;
}

.mower-table {
  width: 100%;
  cursor: pointer;
}

.mower-row {
  transition: background-color 0.15s ease;
}

.mower-row:hover {
  background-color: var(--bg-hover, rgba(0, 0, 0, 0.04));
}

.mower-row.row-selected {
  background-color: var(--highlight-bg, rgba(78, 163, 255, 0.1));
  font-weight: 500;
}

.mower-row.row-selected td {
  color: var(--ink);
}

@media (max-width: 980px) {
  .tracking-header {
    display: grid;
  }

  .tracking-filters,
  .status-grid,
  .charts-grid,
  .map-and-control-grid {
    grid-template-columns: 1fr;
  }
}
</style>
