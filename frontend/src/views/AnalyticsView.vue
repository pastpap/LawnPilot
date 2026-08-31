<script setup lang="ts">
import { computed, onMounted } from "vue";
import LineChart from "../components/LineChart.vue";
import TrendChart from "../components/TrendChart.vue";
import {
  currentTenantId,
  ensureTelemetryLoaded,
  getAreaHealthData,
  getAverageBattery,
  getCoverageRate,
  getMonthlyCoverageTrend,
  getTenant,
  getTenantAreas,
  getTenantMowers,
  getUtilizationTrend,
  getWeeklyRuntimeTrend,
  telemetryMeta,
  tenants,
} from "../data/telemetry";

const tenantId = currentTenantId;

const selectedTenant = computed(() => getTenant(tenantId.value));
const tenantMowers = computed(() => getTenantMowers(tenantId.value));
const tenantAreas = computed(() => getTenantAreas(tenantId.value));

const availabilityPercent = computed(() => {
  if (tenantMowers.value.length === 0) return 0;
  const available = tenantMowers.value.filter(
    (mower) => mower.status !== "maintenance",
  ).length;
  return Math.round((available / tenantMowers.value.length) * 100);
});

const totalRuntimeHours = computed(() => {
  const minutes = tenantMowers.value.reduce(
    (sum, mower) => sum + mower.runtimeMinutesToday,
    0,
  );
  return (minutes / 60).toFixed(1);
});

onMounted(() => {
  void ensureTelemetryLoaded("ADMIN");
});

const dataSourceLabel = computed(() => {
  if (telemetryMeta.value.loading) return "Syncing backend telemetry...";
  if (telemetryMeta.value.loadedFromBackend) return "Backend telemetry";
  if (telemetryMeta.value.error) return "Seed fallback (backend unavailable)";
  return "Seed fallback";
});

const insights = computed(() => {
  const rate = getCoverageRate(tenantId.value);
  const battery = getAverageBattery(tenantId.value);
  return [
    `${selectedTenant.value.displayName} is at ${rate}% daily coverage against target area plans.`,
    `Average mower battery is ${battery}%, with ${availabilityPercent.value}% mower availability.`,
    `${tenantAreas.value.length} service areas contribute to ${tenantMowers.value.length} active mower telemetry points.`,
  ];
});
</script>

<template>
  <div class="analytics-view">
    <header class="analytics-header panel-surface">
      <div>
        <h2>Analytics and trend intelligence</h2>
        <p>Shared telemetry snapshot for {{ selectedTenant.displayName }}</p>
        <p class="data-source">Source: {{ dataSourceLabel }}</p>
      </div>
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
    </header>

    <section class="metrics-grid">
      <article class="panel-surface metric-card">
        <p>Coverage rate</p>
        <strong>{{ getCoverageRate(tenantId) }}%</strong>
      </article>
      <article class="panel-surface metric-card">
        <p>Availability</p>
        <strong>{{ availabilityPercent }}%</strong>
      </article>
      <article class="panel-surface metric-card">
        <p>Avg battery</p>
        <strong>{{ getAverageBattery(tenantId) }}%</strong>
      </article>
      <article class="panel-surface metric-card">
        <p>Runtime today</p>
        <strong>{{ totalRuntimeHours }} h</strong>
      </article>
    </section>

    <section class="chart-stack">
      <article class="panel-surface chart-card">
        <LineChart
          title="Weekly runtime minutes"
          :data="getWeeklyRuntimeTrend(tenantId)"
          :height="240"
        />
      </article>
      <article class="panel-surface chart-card">
        <LineChart
          title="Monthly coverage trend"
          :data="getMonthlyCoverageTrend(tenantId)"
          :height="240"
        />
      </article>
      <article class="panel-surface chart-card">
        <TrendChart
          title="Area health percent"
          :data="getAreaHealthData(tenantId)"
          :height="240"
        />
      </article>
      <article class="panel-surface chart-card">
        <TrendChart
          title="Fleet utilization trend"
          :data="getUtilizationTrend(tenantId)"
          :height="240"
        />
      </article>
    </section>

    <article class="panel-surface insights-card">
      <h3>Operational insights</h3>
      <ul>
        <li v-for="line in insights" :key="line">{{ line }}</li>
      </ul>
    </article>
  </div>
</template>

<style scoped>
.analytics-view {
  display: grid;
  gap: 16px;
}

.analytics-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: end;
}

.analytics-header h2 {
  margin: 0;
}

.analytics-header p {
  margin: 4px 0 0;
  color: var(--ink-soft);
}

.data-source {
  font-size: 0.8rem;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 14px;
  display: grid;
  gap: 6px;
}

.metric-card p {
  color: var(--ink-soft);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-size: 0.78rem;
}

.metric-card strong {
  color: var(--ink);
  font-size: 1.3rem;
}

.chart-stack {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.chart-card,
.insights-card {
  padding: 16px;
}

.insights-card h3 {
  margin: 0 0 8px;
}

.insights-card li {
  color: var(--ink-soft);
  margin: 6px 0;
}

@media (max-width: 980px) {
  .analytics-header,
  .metrics-grid,
  .chart-stack {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
