<script setup lang="ts">
import { computed } from "vue";

export interface DataPoint {
  label: string;
  value: number;
}

interface Props {
  title: string;
  data: DataPoint[];
  height?: number;
}

const props = withDefaults(defineProps<Props>(), {
  height: 200,
});

function toSafeChartValue(value: number, label: string): number {
  if (Number.isFinite(value)) {
    return value;
  }

  console.warn(`[TrendChart] invalid value for ${label}`, { value });
  return 0;
}

const safeData = computed(() =>
  props.data.map((point) => ({
    label: point.label,
    value: toSafeChartValue(point.value, point.label),
  })),
);

const maxValue = computed(() =>
  safeData.value.length > 0
    ? Math.max(1, ...safeData.value.map((d) => d.value))
    : 1,
);

const chartWidth = computed(() => Math.max(300, safeData.value.length * 40));

const bars = computed(() =>
  safeData.value.map((point, index) => {
    const rawHeight =
      (point.value / Math.max(maxValue.value, 1)) * (props.height - 60);
    const barHeight = Number.isFinite(rawHeight)
      ? Math.max(0, Math.min(props.height - 60, rawHeight))
      : 0;
    return {
      label: point.label,
      value: point.value,
      height: barHeight,
      x: index * (chartWidth.value / Math.max(safeData.value.length, 1)),
    };
  }),
);
</script>

<template>
  <div class="trend-chart">
    <h3>{{ title }}</h3>
    <div class="chart-container" :style="{ height: `${height + 40}px` }">
      <svg
        :width="chartWidth"
        :height="height"
        class="chart-svg"
        v-if="safeData.length > 0"
      >
        <!-- Grid lines -->
        <line
          v-for="i in 4"
          :key="`grid-${i}`"
          :x1="0"
          :y1="(i * height) / 5"
          :x2="chartWidth"
          :y2="(i * height) / 5"
          class="grid-line"
        />

        <!-- Y-axis label -->
        <text x="10" y="20" class="axis-label">{{ maxValue }}</text>
        <text x="10" :y="height - 10" class="axis-label">0</text>

        <!-- Bars -->
        <g
          v-for="(bar, index) in bars"
          :key="`bar-${index}`"
          :transform="`translate(${bar.x + 30}, ${height - 40 - bar.height})`"
        >
          <rect width="24" :height="bar.height" class="bar" />
          <text y="20" x="12" text-anchor="middle" class="bar-label">
            {{ bar.label }}
          </text>
          <text y="-5" x="12" text-anchor="middle" class="bar-value">
            {{ bar.value }}
          </text>
        </g>
      </svg>
      <div v-else class="empty-state">No data available</div>
    </div>
  </div>
</template>

<style scoped>
.trend-chart {
  display: grid;
  gap: 12px;
}

.trend-chart h3 {
  margin: 0;
  font-size: 0.96rem;
  color: var(--card-text);
  font-weight: 600;
}

.chart-container {
  display: flex;
  align-items: center;
  overflow-x: auto;
  border-radius: 12px;
  background: var(--chart-surface);
  padding: 12px;
  border: 1px solid var(--border);
}

.chart-svg {
  min-width: 100%;
}

.grid-line {
  stroke: var(--chart-grid);
  stroke-width: 1;
  stroke-dasharray: 4, 4;
}

.axis-label {
  font-size: 12px;
  fill: var(--card-text-soft);
  font-weight: 500;
}

.bar {
  fill: color-mix(in oklab, var(--chart-line) 70%, transparent);
  stroke: var(--chart-line);
  stroke-width: 1;
  border-radius: 4px;
}

.bar-label {
  font-size: 11px;
  fill: var(--card-text-soft);
  font-weight: 500;
}

.bar-value {
  font-size: 11px;
  fill: var(--card-text);
  font-weight: 600;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--card-text-soft);
  font-size: 0.88rem;
}
</style>
