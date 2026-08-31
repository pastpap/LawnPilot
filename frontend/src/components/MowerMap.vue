<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from "vue";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

interface MowerPoint {
  mowerId: string;
  fleetId: string;
  tenantId: string;
  status: string;
  batteryPercent: number;
  lat: number;
  lng: number;
}

interface AreaPoint {
  id: string;
  name: string;
  targetCoverageHa: number;
  coverageTodayHa: number;
  lat: number;
  lng: number;
}

interface Props {
  mowers: MowerPoint[];
  areas: AreaPoint[];
  selectedMowerId?: string;
}

const props = defineProps<Props>();
const mapElement = ref<HTMLElement | null>(null);

let map: L.Map | null = null;
let areaLayer: L.LayerGroup | null = null;
let mowerLayer: L.LayerGroup | null = null;

function statusColor(status: string): string {
  switch (status) {
    case "cutting":
      return "#23b884";
    case "charging":
      return "#4ea3ff";
    case "maintenance":
      return "#f58a3b";
    case "transit":
      return "#ffd166";
    default:
      return "#b4c1d3";
  }
}

function centerMapFromData(): L.LatLngExpression {
  if (props.mowers.length > 0) {
    const meanLat =
      props.mowers.reduce((sum, mower) => sum + mower.lat, 0) /
      props.mowers.length;
    const meanLng =
      props.mowers.reduce((sum, mower) => sum + mower.lng, 0) /
      props.mowers.length;
    return [meanLat, meanLng];
  }

  if (props.areas.length > 0) {
    const meanLat =
      props.areas.reduce((sum, area) => sum + area.lat, 0) / props.areas.length;
    const meanLng =
      props.areas.reduce((sum, area) => sum + area.lng, 0) / props.areas.length;
    return [meanLat, meanLng];
  }

  return [47.6205, -122.3493];
}

function redrawLayers(): void {
  if (!map) return;

  if (!areaLayer) {
    areaLayer = L.layerGroup().addTo(map);
  }
  if (!mowerLayer) {
    mowerLayer = L.layerGroup().addTo(map);
  }

  areaLayer.clearLayers();
  mowerLayer.clearLayers();

  for (const area of props.areas) {
    const coveragePercent = Math.round(
      (area.coverageTodayHa / area.targetCoverageHa) * 100,
    );

    L.circle([area.lat, area.lng], {
      radius: 280,
      color: "#6ca7ff",
      fillColor: "#6ca7ff",
      fillOpacity: 0.1,
      weight: 1,
    })
      .bindTooltip(`${area.name}: ${coveragePercent}% coverage`, {
        permanent: false,
        direction: "top",
      })
      .addTo(areaLayer);
  }

  for (const mower of props.mowers) {
    const selected = props.selectedMowerId === mower.mowerId;
    const marker = L.circleMarker([mower.lat, mower.lng], {
      radius: selected ? 10 : 7,
      color: "#f3f7ff",
      weight: selected ? 3 : 2,
      fillColor: statusColor(mower.status),
      fillOpacity: 0.95,
    });

    marker.bindPopup(
      `${mower.mowerId}<br/>Fleet: ${mower.fleetId}<br/>Status: ${mower.status}<br/>Battery: ${mower.batteryPercent}%`,
    );
    marker.addTo(mowerLayer);
  }

  const points: L.LatLngExpression[] = [
    ...props.areas.map((area) => [area.lat, area.lng] as L.LatLngExpression),
    ...props.mowers.map(
      (mower) => [mower.lat, mower.lng] as L.LatLngExpression,
    ),
  ];

  if (points.length > 0) {
    const bounds = L.latLngBounds(points as [number, number][]);
    map.fitBounds(bounds.pad(0.24));
  }
}

onMounted(() => {
  if (!mapElement.value) return;

  map = L.map(mapElement.value, {
    zoomControl: true,
    attributionControl: true,
  }).setView(centerMapFromData(), 12);

  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors",
  }).addTo(map);

  redrawLayers();
});

watch(
  () => [props.mowers, props.areas, props.selectedMowerId],
  () => {
    redrawLayers();
  },
  { deep: true },
);

onUnmounted(() => {
  if (map) {
    map.remove();
    map = null;
  }
  areaLayer = null;
  mowerLayer = null;
});
</script>

<template>
  <div ref="mapElement" class="mower-map" aria-label="Live map" />
</template>

<style scoped>
.mower-map {
  width: 100%;
  height: 340px;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--border);
}
</style>
