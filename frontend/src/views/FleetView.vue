<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { toApiError, toFriendlyErrorMessage } from "../api/errors";
import {
  createFleet,
  listFleets,
  listMowers,
  registerMower,
  updateFleet,
  updateMower,
} from "../api/tenantApi";
import type { FleetDto, MowerDto, TenantRole } from "../api/types";
import MowerMap from "../components/MowerMap.vue";
import {
  addSimulatedMowerToTelemetry,
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
  updateMowerInTelemetry,
  upsertFleetInTelemetry,
} from "../data/telemetry";

const tenantId = currentTenantId;
const role = computed<TenantRole>(() => "ADMIN");
const selectedFleetId = currentFleetId;

type ModalMode = "create" | "edit";
type FleetAreaCircle = {
  center: { lat: number; lng: number };
  radiusMeters: number;
};

const fleetModalMode = ref<ModalMode>("create");
const fleetId = ref("");
const fleetDisplayName = ref("");
const fleetTenantId = ref("");
const editingFleetId = ref("");

const mowerModalMode = ref<ModalMode>("create");
const mowerTenantId = ref("");
const mowerFleetId = ref("");
const mowerId = ref("");
const mowerModel = ref("");
const editingMowerId = ref("");
const editingMowerSourceFleetId = ref("");
const mowerSimulated = ref(false);
const fleets = ref<FleetDto[]>([]);
const mowers = ref<MowerDto[]>([]);
const error = ref("");
const statusMessage = ref("");
const loading = ref(false);
const showCreateFleetModal = ref(false);
const showRegisterMowerModal = ref(false);
const mowerPinPlacementMode = ref(false);
const candidateMowerStartPin = ref<{ lat: number; lng: number } | null>(null);
const fleetAreaDrawingMode = ref(false);
const candidateFleetAreaCircle = ref<FleetAreaCircle | null>(null);
const draftFleetAreaCircle = ref<FleetAreaCircle | null>(null);
const isAnyModalOpen = computed(
  () => showCreateFleetModal.value || showRegisterMowerModal.value,
);

const TELEMETRY_POLL_INTERVAL_MS = 3000;
let telemetryPollTimer: ReturnType<typeof setInterval> | null = null;
let telemetryPollInFlight = false;

const selectedTenant = computed(() => getTenant(tenantId.value));
const tenantFleets = computed(() => getTenantFleets(tenantId.value));
const tenantAreas = computed(() => getTenantAreas(tenantId.value));
const tenantMowers = computed(() => getTenantMowers(tenantId.value));
const selectedFleetMowers = computed(() =>
  getFleetMowers(selectedFleetId.value),
);
const fleetModalAreas = computed(() => getTenantAreas(fleetTenantId.value));
const mowerModalAreas = computed(() => getTenantAreas(mowerTenantId.value));
const mowerTenantFleets = computed(() => getTenantFleets(mowerTenantId.value));
const activeMowerFleetGeometry = computed(
  () => fleets.value.find((f) => f.fleetId === mowerFleetId.value) ?? null,
);
const startPinOutsideGeofence = computed(() => {
  const geo = activeMowerFleetGeometry.value;
  const pin = candidateMowerStartPin.value;
  if (
    !geo ||
    !pin ||
    !geo.areaCenterLat ||
    !geo.areaCenterLng ||
    !geo.areaRadiusMeters
  )
    return false;
  const latBound = Math.max(0.015, geo.areaRadiusMeters / 111320);
  const lngBound = Math.max(
    0.015,
    geo.areaRadiusMeters /
      (111320 * Math.cos((geo.areaCenterLat * Math.PI) / 180)),
  );
  return (
    Math.abs(pin.lat - geo.areaCenterLat) > latBound ||
    Math.abs(pin.lng - geo.areaCenterLng) > lngBound
  );
});
const tenantFleetCircles = computed(() =>
  fleets.value
    .filter(
      (f) =>
        f.areaCenterLat !== undefined &&
        f.areaCenterLng !== undefined &&
        (f.areaRadiusMeters ?? 0) > 0,
    )
    .map((f) => ({
      center: { lat: f.areaCenterLat!, lng: f.areaCenterLng! },
      radiusMeters: f.areaRadiusMeters!,
      label: f.displayName || f.fleetId,
    })),
);
const mowerModalMowers = computed(() =>
  mowerFleetId.value
    ? getFleetMowers(mowerFleetId.value)
    : getTenantMowers(mowerTenantId.value),
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
  startTelemetryPolling();
  window.addEventListener("keydown", onWindowKeydown);
  void listFleets({ tenantId: tenantId.value, role: role.value })
    .then((list) => {
      fleets.value = list;
    })
    .catch(() => {});
});

onUnmounted(() => {
  if (telemetryPollTimer) {
    clearInterval(telemetryPollTimer);
    telemetryPollTimer = null;
  }

  window.removeEventListener("keydown", onWindowKeydown);
  document.body.classList.remove("fleet-modal-open");
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

watch(mowerPinPlacementMode, (enabled) => {
  if (!enabled) {
    candidateMowerStartPin.value = null;
  }
});

watch(showRegisterMowerModal, (open) => {
  if (!open) {
    clearCandidateStartPin();
  }
});

watch(fleetAreaDrawingMode, (enabled) => {
  if (!enabled) {
    draftFleetAreaCircle.value = null;
  }
});

watch(showCreateFleetModal, (open) => {
  if (!open) {
    clearFleetAreaCircle();
  }
});

watch(mowerTenantId, (nextTenantId) => {
  const fallbackFleetId = getTenantFleets(nextTenantId)[0]?.fleetId ?? "";
  if (
    !getTenantFleets(nextTenantId).some(
      (fleet) => fleet.fleetId === mowerFleetId.value,
    )
  ) {
    mowerFleetId.value = fallbackFleetId;
  }
});

watch(
  isAnyModalOpen,
  (open) => {
    document.body.classList.toggle("fleet-modal-open", open);
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

function startTelemetryPolling(): void {
  if (telemetryPollTimer) {
    return;
  }

  telemetryPollTimer = setInterval(() => {
    if (telemetryPollInFlight) {
      return;
    }

    telemetryPollInFlight = true;
    void refreshTenantTelemetry(tenantId.value, role.value).finally(() => {
      telemetryPollInFlight = false;
    });
  }, TELEMETRY_POLL_INTERVAL_MS);
}

function onStartPinSelected(coordinates: { lat: number; lng: number }): void {
  if (!mowerPinPlacementMode.value) {
    return;
  }

  candidateMowerStartPin.value = {
    lat: Number(coordinates.lat.toFixed(6)),
    lng: Number(coordinates.lng.toFixed(6)),
  };
}

function normalizeFleetAreaCircle(circle: FleetAreaCircle): FleetAreaCircle {
  return {
    center: {
      lat: Number(circle.center.lat.toFixed(6)),
      lng: Number(circle.center.lng.toFixed(6)),
    },
    radiusMeters: Number(Math.max(0, circle.radiusMeters).toFixed(2)),
  };
}

function onFleetAreaCircleDraft(circle: FleetAreaCircle): void {
  if (!fleetAreaDrawingMode.value) {
    return;
  }

  draftFleetAreaCircle.value = normalizeFleetAreaCircle(circle);
}

function onFleetAreaCircleSelected(circle: FleetAreaCircle): void {
  if (!fleetAreaDrawingMode.value) {
    return;
  }

  const normalizedCircle = normalizeFleetAreaCircle(circle);
  candidateFleetAreaCircle.value = normalizedCircle;
  draftFleetAreaCircle.value = normalizedCircle;
}

function clearCandidateStartPin(): void {
  candidateMowerStartPin.value = null;
  mowerPinPlacementMode.value = false;
}

function clearFleetAreaCircle(): void {
  candidateFleetAreaCircle.value = null;
  draftFleetAreaCircle.value = null;
  fleetAreaDrawingMode.value = false;
}

function openCreateFleetModal(): void {
  fleetModalMode.value = "create";
  editingFleetId.value = "";
  fleetTenantId.value = tenantId.value;
  fleetId.value = "";
  fleetDisplayName.value = "";
  clearFleetAreaCircle();
  showCreateFleetModal.value = true;
}

function openEditFleetModal(fleet: {
  fleetId: string;
  displayName: string;
  areaIds: string[];
  tenantId: string;
}): void {
  fleetModalMode.value = "edit";
  editingFleetId.value = fleet.fleetId;
  fleetTenantId.value = fleet.tenantId;
  fleetId.value = fleet.fleetId;
  fleetDisplayName.value = fleet.displayName;
  clearFleetAreaCircle();
  showCreateFleetModal.value = true;
}

function closeCreateFleetModal(): void {
  showCreateFleetModal.value = false;
}

function openRegisterMowerModal(): void {
  mowerModalMode.value = "create";
  editingMowerId.value = "";
  editingMowerSourceFleetId.value = "";
  mowerTenantId.value = tenantId.value;
  mowerFleetId.value =
    selectedFleetId.value || getTenantFleets(tenantId.value)[0]?.fleetId || "";
  mowerId.value = "";
  mowerModel.value = "";
  mowerSimulated.value = false;
  clearCandidateStartPin();
  showRegisterMowerModal.value = true;
  // refresh fleet geometry silently so geofence hints and areaCenterLat/Lng are available
  void listFleets({ tenantId: tenantId.value, role: role.value })
    .then((list) => {
      fleets.value = list;
    })
    .catch(() => {});
}

function openEditMowerModal(mower: {
  mowerId: string;
  model: string;
  tenantId: string;
  fleetId: string;
}): void {
  mowerModalMode.value = "edit";
  editingMowerId.value = mower.mowerId;
  editingMowerSourceFleetId.value = mower.fleetId;
  mowerTenantId.value = mower.tenantId;
  mowerFleetId.value = mower.fleetId;
  mowerId.value = mower.mowerId;
  mowerModel.value = mower.model;
  mowerSimulated.value = false;
  clearCandidateStartPin();
  showRegisterMowerModal.value = true;
}

function closeRegisterMowerModal(): void {
  showRegisterMowerModal.value = false;
}

function closeOpenModals(): void {
  closeCreateFleetModal();
  closeRegisterMowerModal();
}

function onWindowKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape") {
    closeOpenModals();
  }
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
    const normalizedTenantId = fleetTenantId.value.trim();
    const normalizedFleetId = fleetId.value.trim();

    if (!normalizedTenantId) {
      throw new Error("Tenant is required.");
    }

    if (!normalizedFleetId) {
      throw new Error("Fleet id is required.");
    }

    const selectedAreaCircle = candidateFleetAreaCircle.value;
    if (!selectedAreaCircle || selectedAreaCircle.radiusMeters <= 0) {
      throw new Error(
        "Draw a fleet area circle on the map (click, drag, release) before saving.",
      );
    }

    const normalizedDisplayName = fleetDisplayName.value.trim();
    const fleetGeometryPayload = {
      areaGeometryType: "CIRCLE" as const,
      areaCenterLat: selectedAreaCircle.center.lat,
      areaCenterLng: selectedAreaCircle.center.lng,
      areaRadiusMeters: selectedAreaCircle.radiusMeters,
    };
    const telemetryAreaId = `circle:${
      editingFleetId.value || normalizedFleetId
    }`;

    if (fleetModalMode.value === "edit") {
      await updateFleet({
        tenantId: normalizedTenantId,
        role: role.value,
        fleetId: editingFleetId.value || normalizedFleetId,
        displayName: normalizedDisplayName,
        ...fleetGeometryPayload,
      });

      upsertFleetInTelemetry(
        normalizedTenantId,
        editingFleetId.value || normalizedFleetId,
        normalizedDisplayName,
        telemetryAreaId,
      );
      statusMessage.value = `Fleet '${editingFleetId.value || normalizedFleetId}' updated.`;
      closeCreateFleetModal();
      await refreshTenantTelemetry(normalizedTenantId, role.value);
      await onListFleets();
      return;
    }

    await createFleet({
      tenantId: normalizedTenantId,
      role: role.value,
      fleetId: normalizedFleetId,
      displayName: normalizedDisplayName,
      ...fleetGeometryPayload,
    });

    upsertFleetInTelemetry(
      normalizedTenantId,
      normalizedFleetId,
      normalizedDisplayName || normalizedFleetId,
      telemetryAreaId,
    );

    statusMessage.value = `Fleet '${normalizedFleetId}' created.`;
    fleetId.value = "";
    fleetDisplayName.value = "";
    closeCreateFleetModal();
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
    const normalizedTenantId = mowerTenantId.value.trim();
    const normalizedFleetId = mowerFleetId.value.trim();
    const normalizedMowerId = mowerId.value.trim();
    const normalizedModel = mowerModel.value.trim();
    const simulated = mowerSimulated.value;

    if (!normalizedTenantId) {
      throw new Error("Tenant is required.");
    }

    if (!normalizedFleetId) {
      throw new Error("Select a fleet before registering a mower.");
    }

    if (!normalizedMowerId) {
      throw new Error("Mower id is required.");
    }

    const selectedStartPin = candidateMowerStartPin.value
      ? {
          lat: candidateMowerStartPin.value.lat,
          lng: candidateMowerStartPin.value.lng,
        }
      : null;
    const hasValidSelectedStartPin =
      selectedStartPin !== null &&
      Number.isFinite(selectedStartPin.lat) &&
      Number.isFinite(selectedStartPin.lng);

    if (mowerModalMode.value === "edit") {
      const sourceFleetId =
        editingMowerSourceFleetId.value.trim() || normalizedFleetId;

      await updateMower({
        tenantId: normalizedTenantId,
        role: role.value,
        sourceFleetId,
        mowerId: editingMowerId.value || normalizedMowerId,
        model: normalizedModel,
        ...(normalizedFleetId !== sourceFleetId
          ? { fleetId: normalizedFleetId }
          : {}),
      });

      updateMowerInTelemetry(editingMowerId.value || normalizedMowerId, {
        tenantId: normalizedTenantId,
        fleetId: normalizedFleetId,
        model: normalizedModel,
      });

      statusMessage.value = `Mower '${editingMowerId.value || normalizedMowerId}' updated.`;
      closeRegisterMowerModal();
      await refreshTenantTelemetry(normalizedTenantId, role.value);
      await onListMowers();
      return;
    }

    const registerRequest: Parameters<typeof registerMower>[0] = {
      tenantId: normalizedTenantId,
      role: role.value,
      fleetId: normalizedFleetId,
      mowerId: normalizedMowerId,
      model: normalizedModel,
      simulated,
    };

    if (simulated && hasValidSelectedStartPin) {
      Object.assign(registerRequest, {
        startLatitude: selectedStartPin!.lat,
        startLongitude: selectedStartPin!.lng,
      });
    }

    await registerMower(registerRequest);

    if (simulated) {
      if (selectedStartPin) {
        addSimulatedMowerToTelemetry(
          normalizedTenantId,
          normalizedFleetId,
          normalizedMowerId,
          normalizedModel,
          selectedStartPin,
        );
      } else {
        addSimulatedMowerToTelemetry(
          normalizedTenantId,
          normalizedFleetId,
          normalizedMowerId,
          normalizedModel,
        );
      }
    }

    statusMessage.value = simulated
      ? `Simulated mower '${normalizedMowerId}' registered and started mowing in fleet '${normalizedFleetId}'.`
      : `Mower '${normalizedMowerId}' registered to fleet '${normalizedFleetId}'.`;
    mowerId.value = "";
    mowerModel.value = "";
    mowerSimulated.value = false;
    clearCandidateStartPin();
    closeRegisterMowerModal();
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
</script>

<template>
  <div class="fleet-view" :class="{ 'modal-open': isAnyModalOpen }">
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
          :mowers="tenantMowers"
          :fleet-circles="tenantFleetCircles"
          :pin-placement-enabled="false"
          :candidate-start-pin="null"
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

        <article class="panel-surface section-card">
          <h2>Fleet catalog</h2>
          <ul>
            <li
              v-for="fleet in tenantFleets"
              :key="fleet.fleetId"
              class="row-with-action"
            >
              <span>{{ fleet.fleetId }} - {{ fleet.displayName }}</span>
              <button
                type="button"
                :disabled="loading"
                :aria-label="`Edit fleet ${fleet.fleetId}`"
                @click="openEditFleetModal(fleet)"
              >
                Edit
              </button>
            </li>
          </ul>
        </article>

        <article class="panel-surface section-card">
          <h2>Mower catalog</h2>
          <ul>
            <li
              v-for="mower in selectedFleetMowers.length
                ? selectedFleetMowers
                : tenantMowers"
              :key="mower.mowerId"
              class="row-with-action"
            >
              <span>{{ mower.mowerId }} - {{ mower.model }}</span>
              <button
                type="button"
                :disabled="loading"
                :aria-label="`Edit mower ${mower.mowerId}`"
                @click="openEditMowerModal(mower)"
              >
                Edit
              </button>
            </li>
          </ul>
        </article>

        <article class="panel-surface section-card activity-card">
          <h2>Activity</h2>
          <p v-if="statusMessage" class="status">{{ statusMessage }}</p>
          <p v-if="error" class="error">{{ error }}</p>
          <p v-if="loading" class="loading">Working...</p>
          <p class="mini-caption">
            Fleet and mower actions update backend snapshots below.
          </p>
        </article>
      </aside>
    </section>

    <section class="ops-grid">
      <article class="panel-surface section-card">
        <header class="section-header">
          <h2>Fleet operations API</h2>
          <button :disabled="loading" @click="onListFleets">List fleets</button>
        </header>
        <p class="mini-caption">Create fleets from a focused modal form.</p>
        <button :disabled="loading" @click="openCreateFleetModal">
          Add fleet
        </button>
      </article>

      <article class="panel-surface section-card">
        <header class="section-header">
          <h2>Mower operations API</h2>
          <button :disabled="loading" @click="onListMowers">List mowers</button>
        </header>
        <p class="mini-caption">
          Register physical or simulated mowers with optional start pin.
        </p>
        <button :disabled="loading" @click="openRegisterMowerModal">
          Add mower
        </button>
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

    <div
      v-if="showCreateFleetModal"
      class="modal-backdrop"
      role="dialog"
      aria-modal="true"
      :aria-label="fleetModalMode === 'edit' ? 'Edit Fleet' : 'Create Fleet'"
      @click.self="closeCreateFleetModal"
    >
      <article class="panel-surface modal-card modal-card-wide">
        <header class="section-header modal-header">
          <h2>
            {{ fleetModalMode === "edit" ? "Edit fleet" : "Create fleet" }}
          </h2>
          <button
            type="button"
            :disabled="loading"
            @click="closeCreateFleetModal"
          >
            Close
          </button>
        </header>
        <div class="modal-content-grid">
          <div class="form-grid">
            <label>
              Tenant
              <select v-model="fleetTenantId" aria-label="Fleet tenant">
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
              Fleet id
              <input
                v-model="fleetId"
                aria-label="Fleet id"
                placeholder="fleet-west"
                :disabled="fleetModalMode === 'edit'"
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
            <label>
              <input
                v-model="fleetAreaDrawingMode"
                type="checkbox"
                aria-label="Enable fleet area circle drawing"
              />
              Enable circle drawing mode for fleet area
            </label>
            <p
              v-if="fleetAreaDrawingMode && !draftFleetAreaCircle"
              class="mini-caption"
            >
              Circle mode enabled. Click map to set center, drag to define
              radius, and release to finalize.
            </p>
            <p v-if="draftFleetAreaCircle" class="mini-caption">
              Live circle: center
              {{ draftFleetAreaCircle.center.lat.toFixed(6) }},
              {{ draftFleetAreaCircle.center.lng.toFixed(6) }} | radius
              {{ draftFleetAreaCircle.radiusMeters.toFixed(2) }} m
            </p>
            <p v-if="candidateFleetAreaCircle" class="mini-caption">
              Selected circle: center
              {{ candidateFleetAreaCircle.center.lat.toFixed(6) }},
              {{ candidateFleetAreaCircle.center.lng.toFixed(6) }} | radius
              {{ candidateFleetAreaCircle.radiusMeters.toFixed(2) }} m
            </p>
            <button
              v-if="candidateFleetAreaCircle"
              type="button"
              :disabled="loading"
              @click="clearFleetAreaCircle"
            >
              Clear area circle
            </button>
            <p v-if="error" class="error">{{ error }}</p>
            <button :disabled="loading" @click="onCreateFleet">
              {{
                fleetModalMode === "edit"
                  ? "Save fleet changes"
                  : "Create fleet"
              }}
            </button>
          </div>

          <div class="modal-map-panel">
            <h3>Fleet area circle</h3>
            <p class="mini-caption">
              Draw a circle directly on the map: click for center, drag for
              radius, release to save the area shape.
            </p>
            <MowerMap
              :areas="fleetModalAreas"
              :mowers="[]"
              :pin-placement-enabled="false"
              :candidate-start-pin="null"
              :area-circle-drawing-enabled="fleetAreaDrawingMode"
              :candidate-area-circle="candidateFleetAreaCircle"
              :draft-area-circle="draftFleetAreaCircle"
              @area-circle-draft="onFleetAreaCircleDraft"
              @area-circle-selected="onFleetAreaCircleSelected"
            />
          </div>
        </div>
      </article>
    </div>

    <div
      v-if="showRegisterMowerModal"
      class="modal-backdrop"
      role="dialog"
      aria-modal="true"
      :aria-label="mowerModalMode === 'edit' ? 'Edit Mower' : 'Register Mower'"
      @click.self="closeRegisterMowerModal"
    >
      <article class="panel-surface modal-card modal-card-wide">
        <header class="section-header modal-header">
          <h2>
            {{ mowerModalMode === "edit" ? "Edit mower" : "Register mower" }}
          </h2>
          <button
            type="button"
            :disabled="loading"
            @click="closeRegisterMowerModal"
          >
            Close
          </button>
        </header>
        <div class="modal-content-grid">
          <div class="form-grid">
            <label>
              Tenant
              <select v-model="mowerTenantId" aria-label="Mower tenant">
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
              <select v-model="mowerFleetId" aria-label="Mower fleet">
                <option value="">Select fleet</option>
                <option
                  v-for="fleet in mowerTenantFleets"
                  :key="fleet.fleetId"
                  :value="fleet.fleetId"
                >
                  {{ fleet.displayName }}
                </option>
              </select>
            </label>
            <label>
              Mower id
              <input
                v-model="mowerId"
                aria-label="Mower id"
                placeholder="mower-42"
                :disabled="mowerModalMode === 'edit'"
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
            <label class="simulated-field">
              <input
                v-model="mowerSimulated"
                type="checkbox"
                aria-label="Simulated mower"
                :disabled="mowerModalMode === 'edit'"
              />
              Simulated mower (auto-start mowing)
            </label>
            <label class="simulated-field">
              <input
                v-model="mowerPinPlacementMode"
                type="checkbox"
                aria-label="Enable mower start pin placement"
                :disabled="mowerModalMode === 'edit'"
              />
              Enable pin mode, then click map to set start point
            </label>
            <p
              v-if="mowerPinPlacementMode && !candidateMowerStartPin"
              class="mini-caption"
            >
              Pin mode enabled. Click the map to choose start coordinates.
            </p>
            <p v-if="candidateMowerStartPin" class="mini-caption">
              Start pin: {{ candidateMowerStartPin.lat.toFixed(6) }},
              {{ candidateMowerStartPin.lng.toFixed(6) }}
            </p>
            <p v-if="startPinOutsideGeofence" class="error">
              Pin is outside this fleet's area — clear and re-place it inside
              the circle.
            </p>
            <p
              v-else-if="
                mowerSimulated && activeMowerFleetGeometry?.areaCenterLat
              "
              class="mini-caption"
            >
              Fleet area:
              {{ activeMowerFleetGeometry!.areaCenterLat!.toFixed(4) }},
              {{ activeMowerFleetGeometry!.areaCenterLng!.toFixed(4) }} ·
              {{ activeMowerFleetGeometry!.areaRadiusMeters!.toFixed(0) }} m
              radius
            </p>
            <button
              v-if="candidateMowerStartPin"
              type="button"
              :disabled="loading"
              @click="clearCandidateStartPin"
            >
              Clear start pin
            </button>
            <p v-if="error" class="error">{{ error }}</p>
            <button :disabled="loading" @click="onRegisterMower">
              {{
                mowerModalMode === "edit"
                  ? "Save mower changes"
                  : "Register mower"
              }}
            </button>
          </div>

          <div class="modal-map-panel">
            <h3>Start position picker</h3>
            <p class="mini-caption">
              Turn on pin mode, then click map to select mower start
              coordinates.
            </p>
            <MowerMap
              :areas="mowerModalAreas"
              :mowers="mowerModalMowers"
              :pin-placement-enabled="mowerPinPlacementMode"
              :candidate-start-pin="candidateMowerStartPin"
              @pin-selected="onStartPinSelected"
            />
          </div>
        </div>
      </article>
    </div>
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-grid {
  display: grid;
  gap: 10px;
}

.simulated-field {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-soft);
}

.row-with-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

textarea {
  min-height: 120px;
}

.history-box {
  border-radius: 12px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  padding: 12px;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  padding: 20px;
  background: rgba(6, 12, 22, 0.56);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: grid;
  align-items: center;
  justify-items: center;
  z-index: 5000;
}

.modal-card {
  width: min(540px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  padding: 16px;
  display: grid;
  gap: 12px;
  background: #102235;
  opacity: 1;
  box-shadow: 0 30px 70px rgba(0, 0, 0, 0.45);
}

.modal-card-wide {
  width: min(980px, 100%);
}

.modal-header h2 {
  margin: 0;
}

.modal-content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 14px;
}

.modal-map-panel {
  display: grid;
  gap: 8px;
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

.fleet-view.modal-open > :not(.modal-backdrop) {
  pointer-events: none;
  user-select: none;
}

:global(body.fleet-modal-open) {
  overflow: hidden;
}

h3 {
  margin: 0;
}

@media (max-width: 980px) {
  .kpi-grid,
  .fleet-grid,
  .ops-grid,
  .snapshot-grid,
  .filter-row,
  .modal-content-grid {
    grid-template-columns: 1fr;
  }

  .modal-backdrop {
    padding: 12px;
  }
}
</style>
