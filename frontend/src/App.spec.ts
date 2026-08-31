import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createRouter, createMemoryHistory } from "vue-router";
import App from "./App.vue";
import DashboardView from "./views/DashboardView.vue";
import FleetView from "./views/FleetView.vue";
import TrackingView from "./views/TrackingView.vue";
import AnalyticsView from "./views/AnalyticsView.vue";

const tenantApiMocks = vi.hoisted(() => ({
  createFleet: vi.fn(),
  getSimulationHistorySummary: vi.fn(),
  listFleets: vi.fn(),
  listMowers: vi.fn(),
  registerMower: vi.fn(),
  runTenantSimulation: vi.fn(),
}));

vi.mock("./api/tenantApi", () => tenantApiMocks);

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", redirect: "/dashboard" },
      { path: "/dashboard", component: DashboardView },
      { path: "/fleet", component: FleetView },
      { path: "/tracking", component: TrackingView },
      { path: "/analytics", component: AnalyticsView },
    ],
  });
}

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await nextTick();
  await Promise.resolve();
  await nextTick();
}

describe("App", () => {
  beforeEach(() => {
    tenantApiMocks.createFleet.mockReset().mockResolvedValue({
      fleetId: "fleet-1",
      displayName: "North",
      mowerCount: 0,
    });
    tenantApiMocks.getSimulationHistorySummary.mockReset().mockResolvedValue({
      tenantId: "tenant-alpha",
      simulationRunCount: 0,
      lastSimulationRunAt: null,
    });
    tenantApiMocks.listFleets.mockReset().mockResolvedValue([]);
    tenantApiMocks.listMowers.mockReset().mockResolvedValue([]);
    tenantApiMocks.registerMower.mockReset().mockResolvedValue({
      mowerId: "mower-1",
      model: "LP-X",
      registeredAt: "2026-08-31T10:00:00Z",
    });
    tenantApiMocks.runTenantSimulation.mockReset().mockResolvedValue({ outputLines: ["1 3 N"] });
  });

  it("renders clean tabs for dashboard, fleet, tracking, and analytics", async () => {
    const router = createTestRouter();
    const wrapper = mount(App, { global: { plugins: [router] } });

    await flushUi();

    expect(wrapper.text()).toContain("Dashboard");
    expect(wrapper.text()).toContain("Fleet");
    expect(wrapper.text()).toContain("Tracking");
    expect(wrapper.text()).toContain("Analytics");
  });

  it("routes to tracking and shows the live tracking module", async () => {
    const router = createTestRouter();
    const wrapper = mount(App, { global: { plugins: [router] } });

    await router.push("/tracking");
    await flushUi();

    expect(wrapper.text()).toContain("Live mower tracking");
    expect(wrapper.text()).toContain("Tracked mowers");
  });

  it("routes to analytics and shows trend intelligence", async () => {
    const router = createTestRouter();
    const wrapper = mount(App, { global: { plugins: [router] } });

    await router.push("/analytics");
    await flushUi();

    expect(wrapper.text()).toContain("Analytics and trend intelligence");
    expect(wrapper.text()).toContain("Operational insights");
  });
});
