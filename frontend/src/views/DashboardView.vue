<script setup lang="ts">
import { computed, onMounted } from "vue";
import {
  currentTenantId,
  currentFleetId,
  ensureTelemetryLoaded,
  getAverageBattery,
  getCoverageRate,
  getTenant,
  getTenantAreas,
  getTenantFleets,
  getTenantMowers,
  telemetryMeta,
  tenants,
} from "../data/telemetry";

const tenantId = currentTenantId;
const fleetId = currentFleetId;

const selectedTenant = computed(() => getTenant(tenantId.value));
const tenantFleets = computed(() => getTenantFleets(tenantId.value));
const tenantMowers = computed(() => getTenantMowers(tenantId.value));
const tenantAreas = computed(() => getTenantAreas(tenantId.value));

onMounted(() => {
  void ensureTelemetryLoaded("ADMIN");
});

const dashboardKpis = computed(() => [
  {
    label: "Tenants",
    value: tenants.length.toString(),
    hint: "Configured tenants",
  },
  {
    label: "Fleets",
    value: tenantFleets.value.length.toString(),
    hint: `${selectedTenant.value.displayName}`,
  },
  {
    label: "Mowers",
    value: tenantMowers.value.length.toString(),
    hint: "Connected mower units",
  },
  {
    label: "Coverage",
    value: `${getCoverageRate(tenantId.value)}%`,
    hint: "Across tenant areas",
  },
  {
    label: "Avg Battery",
    value: `${getAverageBattery(tenantId.value)}%`,
    hint: "Current telemetry snapshot",
  },
]);

const selectedFleetName = computed(
  () =>
    tenantFleets.value.find((fleet) => fleet.fleetId === fleetId.value)
      ?.displayName ?? "No fleet selected",
);

const dataSourceLabel = computed(() => {
  if (telemetryMeta.value.loading) return "Syncing backend telemetry...";
  if (telemetryMeta.value.loadedFromBackend) return "Backend telemetry";
  if (telemetryMeta.value.error) return "Seed fallback (backend unavailable)";
  return "Seed fallback";
});
</script>

<template>
  <div class="dashboard-view">
    <header class="panel-surface dashboard-header">
      <div>
        <p class="eyebrow">Dashboard</p>
        <h1>Fleet IoT overview</h1>
        <p class="mini-caption">Source: {{ dataSourceLabel }}</p>
      </div>
      <label>
        Active tenant
        <select v-model="tenantId" aria-label="Active tenant">
          <option
            v-for="tenant in tenants"
            :key="tenant.tenantId"
            :value="tenant.tenantId"
          >
            {{ tenant.displayName }} ({{ tenant.region }})
          </option>
        </select>
      </label>
    </header>

    <section class="kpi-grid">
      <article
        v-for="kpi in dashboardKpis"
        :key="kpi.label"
        class="panel-surface kpi-card"
      >
        <p>{{ kpi.label }}</p>
        <strong>{{ kpi.value }}</strong>
        <span>{{ kpi.hint }}</span>
      </article>
    </section>

    <section class="dashboard-grid">
      <article class="panel-surface section-card">
        <header class="section-header">
          <h2>Fleet routing context</h2>
          <span class="pill">{{ selectedFleetName }}</span>
        </header>
        <p class="mini-caption">
          Use this page as the control-room summary before opening detailed
          Tracking and Analytics modules.
        </p>
        <ul>
          <li>
            Fleet module: create fleets, register mowers, and run simulation
            commands.
          </li>
          <li>
            Tracking module: follow each mower on a real map with live status
            and battery.
          </li>
          <li>
            Analytics module: trend runtime, area health, and utilization from
            shared telemetry.
          </li>
        </ul>
      </article>

      <article class="panel-surface section-card">
        <h2>Tenant area health</h2>
        <div class="area-list">
          <div v-for="area in tenantAreas" :key="area.id" class="area-item">
            <strong>{{ area.name }}</strong>
            <span
              >{{ area.coverageTodayHa.toFixed(1) }} /
              {{ area.targetCoverageHa.toFixed(1) }} ha</span
            >
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.dashboard-view {
  display: grid;
  gap: 16px;
}

.dashboard-header {
  padding: 18px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
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
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.kpi-card strong {
  font-size: 1.35rem;
  color: var(--ink);
}

.kpi-card span {
  color: var(--ink-soft);
  font-size: 0.8rem;
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

.mini-caption {
  color: var(--ink-soft);
  font-size: 0.88rem;
}

.area-list {
  display: grid;
  gap: 8px;
}

.area-item {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 12px;
  color: var(--ink-soft);
}

@media (max-width: 860px) {
  .dashboard-header,
  .dashboard-grid {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
