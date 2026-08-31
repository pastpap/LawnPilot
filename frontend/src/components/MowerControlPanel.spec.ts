import { beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import MowerControlPanel from "./MowerControlPanel.vue";
import type { MowerTelemetryDto } from "../api/types";
import * as tenantApi from "../api/tenantApi";

vi.mock("../api/tenantApi");

describe("MowerControlPanel.vue", () => {
  const mockMower: MowerTelemetryDto = {
    mowerId: "mower-1",
    fleetId: "fleet-1",
    model: "Model X",
    status: "cutting",
    batteryPercent: 75,
    runtimeMinutesToday: 120,
    latitude: 47.6,
    longitude: -122.3,
    areaId: "area-1",
    areaName: "North Field",
    targetCoverageHa: 10.0,
    coverageTodayHa: 5.2,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("displays mower info when mower is provided", () => {
    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    expect(wrapper.text()).toContain("mower-1");
    expect(wrapper.text()).toContain("CUTTING");
    expect(wrapper.text()).toContain("75%");
    expect(wrapper.text()).toContain("Model X");
  });

  it("shows 'no mower selected' when mower is null", () => {
    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: null,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    expect(wrapper.text()).toContain("Select a mower");
  });

  it("disables PAUSE button when mower is already paused", async () => {
    const pausedMower = { ...mockMower, status: "paused" };
    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: pausedMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const pauseBtn = wrapper
      .findAll("button.command-btn")
      .find((btn) => btn.text().includes("PAUSE"));
    expect(pauseBtn?.attributes("disabled")).toBeDefined();
  });

  it("disables RESUME button when mower is not paused", async () => {
    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const resumeBtn = wrapper
      .findAll("button.command-btn")
      .find((btn) => btn.text().includes("RESUME"));
    expect(resumeBtn?.attributes("disabled")).toBeDefined();
  });

  it("sends command on button click", async () => {
    const sendMowerCommand = vi.mocked(tenantApi.sendMowerCommand);
    sendMowerCommand.mockResolvedValue({
      commandId: "cmd-1",
      mowerId: "mower-1",
      commandType: "PAUSE",
      status: "ACCEPTED",
      createdAt: new Date().toISOString(),
    });

    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const pauseBtn = wrapper
      .findAll("button.command-btn")
      .find((btn) => btn.text().includes("PAUSE"));
    expect(pauseBtn).toBeDefined();

    await pauseBtn?.trigger("click");
    await wrapper.vm.$nextTick();

    expect(sendMowerCommand).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      fleetId: "fleet-1",
      mowerId: "mower-1",
      commandType: "PAUSE",
      role: "ADMIN",
    });
  });

  it("emits command-sent event after successful command", async () => {
    const sendMowerCommand = vi.mocked(tenantApi.sendMowerCommand);
    sendMowerCommand.mockResolvedValue({
      commandId: "cmd-123",
      mowerId: "mower-1",
      commandType: "PAUSE",
      status: "ACCEPTED",
      createdAt: new Date().toISOString(),
    });

    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const pauseBtn = wrapper
      .findAll("button.command-btn")
      .find((btn) => btn.text().includes("PAUSE"));

    await pauseBtn?.trigger("click");
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted("command-sent")).toBeTruthy();
    expect(wrapper.emitted("command-sent")?.[0]).toEqual(["cmd-123"]);
  });

  it("displays success message after command sent", async () => {
    const sendMowerCommand = vi.mocked(tenantApi.sendMowerCommand);
    sendMowerCommand.mockResolvedValue({
      commandId: "cmd-1",
      mowerId: "mower-1",
      commandType: "PAUSE",
      status: "ACCEPTED",
      createdAt: new Date().toISOString(),
    });

    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const pauseBtn = wrapper
      .findAll("button.command-btn")
      .find((btn) => btn.text().includes("PAUSE"));

    await pauseBtn?.trigger("click");
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain("accepted");
  });

  it("displays error message when command fails", async () => {
    const sendMowerCommand = vi.mocked(tenantApi.sendMowerCommand);
    sendMowerCommand.mockRejectedValue(new Error("API Error"));

    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const pauseBtn = wrapper
      .findAll("button.command-btn")
      .find((btn) => btn.text().includes("PAUSE"));

    await pauseBtn?.trigger("click");
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain("API Error");
  });

  it("shows command history toggle button", async () => {
    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const historyToggle = wrapper.find(".history-toggle");
    expect(historyToggle.exists()).toBe(true);
    expect(historyToggle.text()).toContain("Show Command History");
  });

  it("displays command history when available", async () => {
    const getMowerCommandHistory = vi.mocked(tenantApi.getMowerCommandHistory);
    getMowerCommandHistory.mockResolvedValue([
      {
        commandId: "cmd-1",
        mowerId: "mower-1",
        commandType: "PAUSE",
        status: "COMPLETED",
        createdAt: "2026-08-31T10:00:00Z",
        completedAt: "2026-08-31T10:00:05Z",
      },
      {
        commandId: "cmd-2",
        mowerId: "mower-1",
        commandType: "RESUME",
        status: "COMPLETED",
        createdAt: "2026-08-31T10:01:00Z",
        completedAt: "2026-08-31T10:01:02Z",
      },
    ]);

    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const historyToggle = wrapper.find(".history-toggle");
    await historyToggle.trigger("click");
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain("PAUSE");
    expect(wrapper.text()).toContain("RESUME");
  });

  it("marks OVERRIDE commands with warning styling", () => {
    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: mockMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const overrideBtn = wrapper
      .findAll("button.command-btn")
      .find((btn) => btn.text().includes("OVERRIDE"));
    expect(overrideBtn?.classes()).toContain("btn-warning");
  });

  it("disables OVERRIDE when mower is in maintenance", async () => {
    const maintenanceMower = { ...mockMower, status: "maintenance" };
    const wrapper = mount(MowerControlPanel, {
      props: {
        mower: maintenanceMower,
        tenantId: "tenant-1",
        fleetId: "fleet-1",
        role: "ADMIN",
      },
    });

    const overrideBtn = wrapper
      .findAll("button.command-btn")
      .find((btn) => btn.text().includes("OVERRIDE"));
    expect(overrideBtn?.attributes("disabled")).toBeDefined();
  });
});
