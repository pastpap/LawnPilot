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

interface CoordinatePoint {
  lat: number;
  lng: number;
}

interface CircleSelection {
  center: CoordinatePoint;
  radiusMeters: number;
}

interface Props {
  mowers: MowerPoint[];
  areas: AreaPoint[];
  selectedMowerId?: string;
  pinPlacementEnabled?: boolean;
  candidateStartPin?: CoordinatePoint | null;
  areaCircleDrawingEnabled?: boolean;
  candidateAreaCircle?: CircleSelection | null;
  draftAreaCircle?: CircleSelection | null;
  fleetCircles?: (CircleSelection & { label: string })[];
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (event: "pin-selected", coordinates: CoordinatePoint): void;
  (event: "area-circle-draft", circle: CircleSelection): void;
  (event: "area-circle-selected", circle: CircleSelection): void;
}>();
const mapElement = ref<HTMLElement | null>(null);

let map: L.Map | null = null;
let areaLayer: L.LayerGroup | null = null;
let mowerLayer: L.LayerGroup | null = null;
let pinLayer: L.LayerGroup | null = null;
let areaCircleCenter: L.LatLng | null = null;
const SELECTED_MOWER_ZOOM = 15;

function normalizeCircleSelection(
  center: CoordinatePoint,
  radiusMeters: number,
): CircleSelection {
  return {
    center: {
      lat: Number(center.lat.toFixed(6)),
      lng: Number(center.lng.toFixed(6)),
    },
    radiusMeters: Number(Math.max(0, radiusMeters).toFixed(2)),
  };
}

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

function redrawLayers(
  options: {
    adjustViewport?: boolean;
    focusSelectedMower?: boolean;
  } = {},
): void {
  if (!map) return;

  if (!areaLayer) {
    areaLayer = L.layerGroup().addTo(map);
  }
  if (!mowerLayer) {
    mowerLayer = L.layerGroup().addTo(map);
  }
  if (!pinLayer) {
    pinLayer = L.layerGroup().addTo(map);
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

  for (const fc of props.fleetCircles ?? []) {
    L.circle([fc.center.lat, fc.center.lng], {
      radius: fc.radiusMeters,
      color: "#8ba7c9",
      fillColor: "#8ba7c9",
      fillOpacity: 0.07,
      weight: 1.5,
      dashArray: "5 4",
    })
      .bindTooltip(fc.label, { permanent: false, direction: "top" })
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

  if (options.focusSelectedMower && props.selectedMowerId) {
    const selectedMower = props.mowers.find(
      (mower) => mower.mowerId === props.selectedMowerId,
    );
    if (selectedMower) {
      map.setView([selectedMower.lat, selectedMower.lng], SELECTED_MOWER_ZOOM);
    }
  } else if (options.adjustViewport && points.length > 0) {
    const bounds = L.latLngBounds(points as [number, number][]);
    map.fitBounds(bounds.pad(0.24));
  }

  pinLayer.clearLayers();
  if (props.candidateStartPin) {
    L.circleMarker([props.candidateStartPin.lat, props.candidateStartPin.lng], {
      radius: 9,
      color: "#f3f7ff",
      weight: 2,
      fillColor: "#f58a3b",
      fillOpacity: 0.95,
    })
      .bindTooltip("Candidate mower start position", {
        permanent: false,
        direction: "top",
      })
      .addTo(pinLayer);
  }

  if (props.draftAreaCircle) {
    L.circle(
      [props.draftAreaCircle.center.lat, props.draftAreaCircle.center.lng],
      {
        radius: props.draftAreaCircle.radiusMeters,
        color: "#ffd166",
        fillColor: "#ffd166",
        fillOpacity: 0.12,
        weight: 2,
        dashArray: "6 4",
      },
    )
      .bindTooltip("Drawing fleet area circle", {
        permanent: false,
        direction: "top",
      })
      .addTo(pinLayer);
  }

  if (props.candidateAreaCircle) {
    L.circle(
      [
        props.candidateAreaCircle.center.lat,
        props.candidateAreaCircle.center.lng,
      ],
      {
        radius: props.candidateAreaCircle.radiusMeters,
        color: "#23b884",
        fillColor: "#23b884",
        fillOpacity: 0.1,
        weight: 2,
      },
    )
      .bindTooltip("Selected fleet area circle", {
        permanent: false,
        direction: "top",
      })
      .addTo(pinLayer);

    L.circleMarker(
      [
        props.candidateAreaCircle.center.lat,
        props.candidateAreaCircle.center.lng,
      ],
      {
        radius: 6,
        color: "#f3f7ff",
        weight: 2,
        fillColor: "#23b884",
        fillOpacity: 1,
      },
    ).addTo(pinLayer);
  }
}

function onMapClick(event: L.LeafletMouseEvent): void {
  if (!props.pinPlacementEnabled || props.areaCircleDrawingEnabled) {
    return;
  }

  emit("pin-selected", {
    lat: Number(event.latlng.lat.toFixed(6)),
    lng: Number(event.latlng.lng.toFixed(6)),
  });
}

function emitAreaCircleDraft(event: L.LeafletMouseEvent): void {
  if (!areaCircleCenter) {
    return;
  }

  const draft = normalizeCircleSelection(
    {
      lat: areaCircleCenter.lat,
      lng: areaCircleCenter.lng,
    },
    areaCircleCenter.distanceTo(event.latlng),
  );
  emit("area-circle-draft", draft);
}

function onMapMouseDown(event: L.LeafletMouseEvent): void {
  if (!props.areaCircleDrawingEnabled) {
    return;
  }

  areaCircleCenter = event.latlng;
  emitAreaCircleDraft(event);
}

function onMapMouseMove(event: L.LeafletMouseEvent): void {
  if (!props.areaCircleDrawingEnabled || !areaCircleCenter) {
    return;
  }

  emitAreaCircleDraft(event);
}

function onMapMouseUp(event: L.LeafletMouseEvent): void {
  if (!props.areaCircleDrawingEnabled || !areaCircleCenter) {
    return;
  }

  const finalizedCircle = normalizeCircleSelection(
    {
      lat: areaCircleCenter.lat,
      lng: areaCircleCenter.lng,
    },
    areaCircleCenter.distanceTo(event.latlng),
  );
  emit("area-circle-selected", finalizedCircle);
  areaCircleCenter = null;
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

  map.on("click", onMapClick);
  map.on("mousedown", onMapMouseDown);
  map.on("mousemove", onMapMouseMove);
  map.on("mouseup", onMapMouseUp);

  if (props.areaCircleDrawingEnabled) {
    map.dragging.disable();
    mapElement.value?.classList.add("drawing-mode");
  }

  redrawLayers({ adjustViewport: true });
});

watch(
  () => [
    props.mowers,
    props.areas,
    props.candidateStartPin,
    props.candidateAreaCircle,
    props.draftAreaCircle,
    props.fleetCircles,
  ],
  () => {
    redrawLayers();
  },
  { deep: true },
);

watch(
  () => props.areaCircleDrawingEnabled,
  (enabled) => {
    if (!map) return;
    if (enabled) {
      map.dragging.disable();
      mapElement.value?.classList.add("drawing-mode");
    } else {
      map.dragging.enable();
      mapElement.value?.classList.remove("drawing-mode");
      areaCircleCenter = null;
    }
  },
);

watch(
  () => props.selectedMowerId,
  (nextSelectedMowerId, previousSelectedMowerId) => {
    redrawLayers({
      focusSelectedMower:
        Boolean(nextSelectedMowerId) &&
        nextSelectedMowerId !== previousSelectedMowerId,
    });
  },
);

onUnmounted(() => {
  if (map) {
    map.off("click", onMapClick);
    map.off("mousedown", onMapMouseDown);
    map.off("mousemove", onMapMouseMove);
    map.off("mouseup", onMapMouseUp);
    map.remove();
    map = null;
  }
  areaLayer = null;
  mowerLayer = null;
  pinLayer = null;
  areaCircleCenter = null;
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

.mower-map.drawing-mode {
  cursor: crosshair;
}
</style>
