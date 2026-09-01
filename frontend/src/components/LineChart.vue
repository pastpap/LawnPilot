<script setup lang="ts">
import { computed } from "vue";

export interface TimeSeriesPoint {
  time: string;
  value: number;
}

interface Props {
  title: string;
  data: TimeSeriesPoint[];
  height?: number;
}

const props = withDefaults(defineProps<Props>(), {
  height: 180,
});

function toSafeSeriesValue(value: number, time: string): number {
  if (Number.isFinite(value)) {
    return value;
  }

  console.warn(`[LineChart] invalid value for ${time}`, { value });
  return 0;
}

const safeData = computed(() =>
  props.data.map((point) => ({
    time: point.time,
    value: toSafeSeriesValue(point.value, point.time),
  })),
);

const maxValue = computed(() =>
  safeData.value.length > 0
    ? Math.max(...safeData.value.map((d) => d.value))
    : 1,
);

const minValue = computed(() =>
  safeData.value.length > 0
    ? Math.min(...safeData.value.map((d) => d.value))
    : 0,
);

const range = computed(() => maxValue.value - minValue.value || 1);
const chartWidth = 500;
const chartHeight = props.height - 40;

const points = computed(() => {
  if (safeData.value.length <= 1) return "";

  const step = chartWidth / (safeData.value.length - 1);
  const points = safeData.value
    .map((point, index) => {
      const x = index * step;
      const y =
        chartHeight -
        ((point.value - minValue.value) / range.value) * chartHeight;
      return `${x},${y}`;
    })
    .join(" ");

  return points;
});
</script>

<template>
  <div class="line-chart">
    <h3>{{ title }}</h3>
    <div class="chart-container" :style="{ height: `${height}px` }">
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
          :y1="(i * chartHeight) / 5"
          :x2="chartWidth"
          :y2="(i * chartHeight) / 5"
          class="grid-line"
        />

        <!-- Y-axis labels -->
        <text x="5" y="15" class="axis-label">{{ maxValue }}</text>
        <text x="5" :y="chartHeight + 5" class="axis-label">
          {{ minValue }}
        </text>

        <!-- Line -->
        <polyline :points="points" class="line" />

        <!-- Data points -->
        <circle
          v-for="(point, index) in safeData"
          :key="`point-${index}`"
          :cx="(index / Math.max(safeData.length - 1, 1)) * chartWidth"
          :cy="chartHeight - ((point.value - minValue) / range) * chartHeight"
          r="3"
          class="data-point"
        />

        <!-- X-axis labels (first, middle, last) -->
        <text
          v-if="safeData.length > 0"
          :x="0"
          :y="chartHeight + 25"
          text-anchor="start"
          class="x-label"
        >
          {{ safeData[0].time }}
        </text>
        <text
          v-if="safeData.length > 2"
          :x="chartWidth / 2"
          :y="chartHeight + 25"
          text-anchor="middle"
          class="x-label"
        >
          {{ safeData[Math.floor(safeData.length / 2)].time }}
        </text>
        <text
          v-if="safeData.length > 1"
          :x="chartWidth"
          :y="chartHeight + 25"
          text-anchor="end"
          class="x-label"
        >
          {{ safeData[safeData.length - 1].time }}
        </text>
      </svg>
      <div v-else class="empty-state">No data available</div>
    </div>
  </div>
</template>

<style scoped>
.line-chart {
  display: grid;
  gap: 12px;
}

.line-chart h3 {
  margin: 0;
  font-size: 0.96rem;
  color: var(--card-text);
  font-weight: 600;
}

.chart-container {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--chart-surface);
  padding: 12px;
  border: 1px solid var(--border);
  overflow-x: auto;
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
  font-size: 11px;
  fill: var(--card-text-soft);
  font-weight: 500;
}

.line {
  stroke: var(--chart-line);
  stroke-width: 2;
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.data-point {
  fill: var(--chart-point);
  stroke: var(--chart-point-stroke);
  stroke-width: 2;
}

.x-label {
  font-size: 10px;
  fill: var(--card-text-soft);
  font-weight: 500;
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
