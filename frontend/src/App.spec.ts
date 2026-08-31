import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.vue";
import { ApiError } from "./api/errors";

const tenantApiMocks = vi.hoisted(() => ({
  createFleet: vi.fn(),
  getSimulationHistorySummary: vi.fn(),
  listFleets: vi.fn(),
  listMowers: vi.fn(),
  registerMower: vi.fn(),
  runTenantSimulation: vi.fn(),
}));

vi.mock("./api/tenantApi", () => tenantApiMocks);

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await nextTick();
  await Promise.resolve();
  await nextTick();
}

async function clickListFleets(wrapper: ReturnType<typeof mount>): Promise<void> {
  const listFleetsButton = wrapper
    .findAll("button")
    .find((buttonWrapper) => buttonWrapper.text() === "List fleets");

  if (!listFleetsButton) {
    throw new Error("List fleets button not found.");
  }

  await listFleetsButton.trigger("click");
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

  it("updates role display and status when role changes", async () => {
    const wrapper = mount(App);

    expect(wrapper.text()).toContain("Role: ADMIN");

    await wrapper.get('select[aria-label="Role"]').setValue("OPERATOR");
    await flushUi();

    expect(wrapper.text()).toContain("Role: OPERATOR");
    expect(wrapper.text()).toContain("Role set to OPERATOR.");
  });

  it("lists fleets through tenantApi and renders loaded fleet data", async () => {
    tenantApiMocks.listFleets.mockResolvedValueOnce([
      {
        fleetId: "fleet-1",
        displayName: "North Campus",
        mowerCount: 2,
      },
    ]);

    const wrapper = mount(App);

    await clickListFleets(wrapper);
    await flushUi();

    expect(tenantApiMocks.listFleets).toHaveBeenCalledWith({
      tenantId: "tenant-alpha",
      role: "ADMIN",
    });
    expect(wrapper.text()).toContain("Loaded 1 fleet(s).");
    expect(wrapper.text()).toContain("fleet-1 - North Campus (2 mower(s))");
  });

  it("renders friendly backend errors for failed tenant actions", async () => {
    tenantApiMocks.listFleets.mockRejectedValueOnce(
      new ApiError(500, "Internal Server Error", "Database unavailable."),
    );

    const wrapper = mount(App);

    await clickListFleets(wrapper);
    await flushUi();

    expect(wrapper.text()).toContain("Backend error (500). Please retry in a moment.");
    expect(wrapper.text()).toContain("Database unavailable.");
  });
});
