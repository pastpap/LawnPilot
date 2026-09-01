import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FleetView from "./FleetView.vue";
import * as tenantApi from "../api/tenantApi";
import * as telemetry from "../data/telemetry";

vi.mock("../api/tenantApi", () => ({
    createFleet: vi.fn(),
    listFleets: vi.fn(),
    listMowers: vi.fn(),
    registerMower: vi.fn(),
    updateFleet: vi.fn(),
    updateMower: vi.fn(),
}));

async function flushUi(): Promise<void> {
    await Promise.resolve();
    await nextTick();
    await Promise.resolve();
    await nextTick();
}

async function openMowerModal(wrapper: ReturnType<typeof mount>): Promise<void> {
    const openButton = wrapper
        .findAll("button")
        .find((button) => button.text().includes("Add mower"));
    expect(openButton).toBeDefined();
    await openButton?.trigger("click");
    await flushUi();
}

async function openFleetModal(wrapper: ReturnType<typeof mount>): Promise<void> {
    const openButton = wrapper
        .findAll("button")
        .find((button) => button.text().includes("Add fleet"));
    expect(openButton).toBeDefined();
    await openButton?.trigger("click");
    await flushUi();
}

async function clickModalMapPinSelector(wrapper: ReturnType<typeof mount>): Promise<void> {
    const pinButtons = wrapper.findAll('button[aria-label="Map pin selector"]');
    const modalPinButton = pinButtons.at(-1);
    expect(modalPinButton).toBeDefined();
    await modalPinButton?.trigger("click");
    await flushUi();
}

async function clickModalMapCircleDrawer(wrapper: ReturnType<typeof mount>): Promise<void> {
    const circleButtons = wrapper.findAll('button[aria-label="Map circle drawer"]');
    const modalCircleButton = circleButtons.at(-1);
    expect(modalCircleButton).toBeDefined();
    await modalCircleButton?.trigger("click");
    await flushUi();
}

describe("FleetView.vue", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        telemetry.currentTenantId.value = "tenant-alpha";
        telemetry.currentFleetId.value = "fleet-north";

        vi.spyOn(telemetry, "ensureTelemetryLoaded").mockResolvedValue();
        vi.spyOn(telemetry, "refreshTenantTelemetry").mockResolvedValue();
        vi.spyOn(telemetry, "addSimulatedMowerToTelemetry").mockImplementation(() => {
            return undefined;
        });
        vi.spyOn(telemetry, "upsertFleetInTelemetry").mockImplementation(() => {
            return undefined;
        });
        vi.spyOn(telemetry, "updateMowerInTelemetry").mockImplementation(() => {
            return true;
        });

        vi.mocked(tenantApi.listFleets).mockResolvedValue([]);
        vi.mocked(tenantApi.listMowers).mockResolvedValue([]);
        vi.mocked(tenantApi.createFleet).mockResolvedValue({
            fleetId: "fleet-new",
            displayName: "New Fleet",
            mowerCount: 0,
        });
        vi.mocked(tenantApi.updateFleet).mockResolvedValue({
            fleetId: "fleet-north",
            displayName: "North Grounds",
            mowerCount: 2,
        });
        vi.mocked(tenantApi.registerMower).mockResolvedValue({
            mowerId: "sim-01",
            model: "LP-SIM",
            registeredAt: "2026-08-31T12:00:00Z",
        });
        vi.mocked(tenantApi.updateMower).mockResolvedValue({
            mowerId: "M-014",
            model: "LP-X3-REV2",
            registeredAt: "2026-08-31T12:00:00Z",
        });
    });

    it("sends simulated flag and shows immediate mowing message for simulated mowers", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.674, lng: -122.301 }, radiusMeters: 210 })">Draw area circle</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper.find('input[aria-label="Mower id"]').setValue("sim-01");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-SIM");
        await wrapper.find('input[aria-label="Simulated mower"]').setChecked(true);

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(tenantApi.registerMower).toHaveBeenCalledWith(
            expect.objectContaining({
                mowerId: "sim-01",
                model: "LP-SIM",
                simulated: true,
            }),
        );

        const payload = vi.mocked(tenantApi.registerMower).mock.calls[0][0] as Record<string, unknown>;
        expect(payload).not.toHaveProperty("startLatitude");
        expect(payload).not.toHaveProperty("startLongitude");

        expect(telemetry.addSimulatedMowerToTelemetry).toHaveBeenCalledWith(
            "tenant-alpha",
            "fleet-north",
            "sim-01",
            "LP-SIM",
        );
        expect(tenantApi.listMowers).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: "tenant-alpha",
                fleetId: "fleet-north",
                role: "ADMIN",
            }),
        );
    });

    it("does not run simulated telemetry shortcut when registering non-simulated mower", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.674, lng: -122.301 }, radiusMeters: 210 })">Draw area circle</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper.find('input[aria-label="Mower id"]').setValue("mower-77");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-X7");

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(tenantApi.registerMower).toHaveBeenCalledWith(
            expect.objectContaining({
                mowerId: "mower-77",
                model: "LP-X7",
                simulated: false,
            }),
        );
        expect(telemetry.addSimulatedMowerToTelemetry).not.toHaveBeenCalled();
    });

    it("includes start coordinates in register payload when a map pin is selected for simulated registration", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["pin-selected"],
                        template:
                            '<button aria-label="Map pin selector" @click="$emit(\'pin-selected\', { lat: 47.6111111, lng: -122.3333333 })">Pick pin</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper
            .find('input[aria-label="Enable mower start pin placement"]')
            .setChecked(true);
        await clickModalMapPinSelector(wrapper);
        await wrapper.find('input[aria-label="Mower id"]').setValue("mower-88");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-PIN");
        await wrapper.find('input[aria-label="Simulated mower"]').setChecked(true);

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(tenantApi.registerMower).toHaveBeenCalledWith(
            expect.objectContaining({
                mowerId: "mower-88",
                model: "LP-PIN",
                simulated: true,
                startLatitude: 47.611111,
                startLongitude: -122.333333,
            }),
        );

        expect(wrapper.find('[aria-label="Register Mower"]').exists()).toBe(false);
        expect(wrapper.text()).not.toContain("Start pin:");
        expect(wrapper.findAll("button").some((button) => button.text() === "Clear start pin")).toBe(false);
    });

    it("does not include start coordinates when pin mode is enabled but no map click occurred", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.674, lng: -122.301 }, radiusMeters: 210 })">Draw area circle</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper
            .find('input[aria-label="Enable mower start pin placement"]')
            .setChecked(true);
        await wrapper.find('input[aria-label="Mower id"]').setValue("mower-89");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-NOPIN");

        expect(wrapper.text()).toContain("Pin mode enabled. Click the map to choose start coordinates.");
        expect(wrapper.text()).not.toContain("Start pin:");

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(tenantApi.registerMower).toHaveBeenCalledTimes(1);
        const payload = vi.mocked(tenantApi.registerMower).mock.calls[0][0] as Record<string, unknown>;
        expect(payload).not.toHaveProperty("startLatitude");
        expect(payload).not.toHaveProperty("startLongitude");
    });

    it("omits start coordinates when pin is selected but mower is not simulated", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["pin-selected"],
                        template:
                            '<button aria-label="Map pin selector" @click="$emit(\'pin-selected\', { lat: 47.6111111, lng: -122.3333333 })">Pick pin</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper
            .find('input[aria-label="Enable mower start pin placement"]')
            .setChecked(true);
        await clickModalMapPinSelector(wrapper);
        await wrapper.find('input[aria-label="Mower id"]').setValue("mower-90");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-PHY");

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        const payload = vi.mocked(tenantApi.registerMower).mock.calls[0][0] as Record<string, unknown>;
        expect(payload).toEqual(
            expect.objectContaining({
                mowerId: "mower-90",
                model: "LP-PHY",
                simulated: false,
            }),
        );
        expect(payload).not.toHaveProperty("startLatitude");
        expect(payload).not.toHaveProperty("startLongitude");
    });

    it("clears selected start pin and exits pin mode when canceling pin placement", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["pin-selected"],
                        template:
                            '<button aria-label="Map pin selector" @click="$emit(\'pin-selected\', { lat: 47.6111111, lng: -122.3333333 })">Pick pin</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper
            .find('input[aria-label="Enable mower start pin placement"]')
            .setChecked(true);
        await clickModalMapPinSelector(wrapper);
        await flushUi();

        expect(wrapper.text()).toContain("Start pin: 47.611111, -122.333333");

        const clearPinButton = wrapper
            .findAll("button")
            .find((button) => button.text() === "Clear start pin");
        expect(clearPinButton).toBeDefined();
        await clearPinButton?.trigger("click");
        await flushUi();

        const pinModeToggle = wrapper.find(
            'input[aria-label="Enable mower start pin placement"]',
        );
        expect((pinModeToggle.element as HTMLInputElement).checked).toBe(false);
        expect(wrapper.text()).not.toContain("Start pin:");
    });

    it("allows no-pin registration after clearing a malformed selected pin", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["pin-selected"],
                        template:
                            '<button aria-label="Map pin selector" @click="$emit(\'pin-selected\', { lat: 47.6111111, lng: Number.NaN })">Pick malformed pin</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper
            .find('input[aria-label="Enable mower start pin placement"]')
            .setChecked(true);
        await clickModalMapPinSelector(wrapper);
        await flushUi();

        const clearPinButton = wrapper
            .findAll("button")
            .find((button) => button.text() === "Clear start pin");
        expect(clearPinButton).toBeDefined();
        await clearPinButton?.trigger("click");

        await wrapper.find('input[aria-label="Mower id"]').setValue("mower-clear-invalid-pin");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-CLEAR");

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(tenantApi.registerMower).toHaveBeenCalledTimes(1);
        expect(tenantApi.registerMower).toHaveBeenCalledWith(
            expect.objectContaining({
                mowerId: "mower-clear-invalid-pin",
                model: "LP-CLEAR",
                simulated: false,
            }),
        );

        const request = vi.mocked(tenantApi.registerMower).mock.calls[0][0] as Record<string, unknown>;
        expect(request).not.toHaveProperty("startLatitude");
        expect(request).not.toHaveProperty("startLongitude");
    });

    it("shows pin placement guidance and omits start coordinates when no map pin is selected", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.674, lng: -122.301 }, radiusMeters: 210 })">Draw area circle</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper
            .find('input[aria-label="Enable mower start pin placement"]')
            .setChecked(true);

        expect(wrapper.text()).toContain("Enable pin mode, then click map to set start point");
        expect(wrapper.text()).toContain("Pin mode enabled. Click the map to choose start coordinates.");

        await wrapper.find('input[aria-label="Mower id"]').setValue("mower-no-pin");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-NOPIN");

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        const request = vi.mocked(tenantApi.registerMower).mock.calls.at(-1)?.[0];
        expect(request).toBeDefined();
        expect(request).not.toHaveProperty("startLatitude");
        expect(request).not.toHaveProperty("startLongitude");
    });

    it("uses selected start pin for optimistic simulated mower placement", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["pin-selected"],
                        template:
                            '<button aria-label="Map pin selector" @click="$emit(\'pin-selected\', { lat: 47.6222222, lng: -122.3111111 })">Pick pin</button>',
                    },
                },
            },
        });

        await openMowerModal(wrapper);

        await wrapper
            .find('input[aria-label="Enable mower start pin placement"]')
            .setChecked(true);
        await clickModalMapPinSelector(wrapper);
        await wrapper.find('input[aria-label="Mower id"]').setValue("sim-pin-1");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-SIM-PIN");
        await wrapper.find('input[aria-label="Simulated mower"]').setChecked(true);

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(telemetry.addSimulatedMowerToTelemetry).toHaveBeenCalledWith(
            "tenant-alpha",
            "fleet-north",
            "sim-pin-1",
            "LP-SIM-PIN",
            { lat: 47.622222, lng: -122.311111 },
        );
    });

    it("sends fleet create payload with drawn circle geometry", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.6714, lng: -122.3057 }, radiusMeters: 245.5 })">Draw circle</button>',
                    },
                },
            },
        });

        const selectedFleetBefore = wrapper.find('select[aria-label="Selected fleet"]');
        expect((selectedFleetBefore.element as HTMLSelectElement).value).toBe("fleet-north");

        await openFleetModal(wrapper);
        await wrapper
            .find('input[aria-label="Enable fleet area circle drawing"]')
            .setChecked(true);
        await clickModalMapCircleDrawer(wrapper);

        await wrapper.find('input[aria-label="Fleet id"]').setValue("fleet-west");
        await wrapper.find('input[aria-label="Fleet display name"]').setValue("West Grounds");

        const createButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Create fleet"));

        expect(createButton).toBeDefined();
        await createButton?.trigger("click");
        await flushUi();

        expect(tenantApi.createFleet).toHaveBeenCalledTimes(1);
        expect(tenantApi.createFleet).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: "tenant-alpha",
                role: "ADMIN",
                fleetId: "fleet-west",
                displayName: "West Grounds",
                areaGeometryType: "CIRCLE",
                areaCenterLat: 47.6714,
                areaCenterLng: -122.3057,
                areaRadiusMeters: 245.5,
            }),
        );

        const payload = vi.mocked(tenantApi.createFleet).mock.calls[0][0] as Record<string, unknown>;
        expect(payload).not.toHaveProperty("areaId");
        expect(payload).not.toHaveProperty("areaIds");
        expect(payload).not.toHaveProperty("startLatitude");
        expect(payload).not.toHaveProperty("startLongitude");

        expect(wrapper.text()).toContain("Loaded 0 backend fleet record(s).");
        const selectedFleetAfter = wrapper.find('select[aria-label="Selected fleet"]');
        expect((selectedFleetAfter.element as HTMLSelectElement).value).toBe("fleet-north");
    });

    it("uses main-page tenant/fleet as mower modal defaults while allowing overrides", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: true,
                },
            },
        });

        await wrapper.find('select[aria-label="Tenant"]').setValue("tenant-beta");
        await flushUi();
        await wrapper.find('select[aria-label="Selected fleet"]').setValue("fleet-sunset");
        await flushUi();

        await openMowerModal(wrapper);

        const mowerTenantSelect = wrapper.find('[aria-label="Register Mower"] select[aria-label="Mower tenant"]');
        const mowerFleetSelect = wrapper.find('[aria-label="Register Mower"] select[aria-label="Mower fleet"]');
        expect(mowerTenantSelect.exists()).toBe(true);
        expect(mowerFleetSelect.exists()).toBe(true);
        expect((mowerTenantSelect.element as HTMLSelectElement).value).toBe("tenant-beta");
        expect((mowerFleetSelect.element as HTMLSelectElement).value).toBe("fleet-sunset");

        await wrapper.find('input[aria-label="Mower id"]').setValue("ctx-01");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-CTX");

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));

        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(tenantApi.registerMower).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: "tenant-beta",
                fleetId: "fleet-sunset",
                mowerId: "ctx-01",
                model: "LP-CTX",
            }),
        );
    });

    it("keeps fleet and mower modals in create mode with deterministic draft reset behavior", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.66, lng: -122.33 }, radiusMeters: 120 })">Draw circle</button>',
                    },
                },
            },
        });

        await openFleetModal(wrapper);
        expect(wrapper.find('[aria-label="Create Fleet"]').text()).toContain("Create fleet");
        expect(wrapper.find('[aria-label="Create Fleet"]').text()).toContain("Create fleet");
        expect(wrapper.find('[aria-label="Create Fleet"]').text()).not.toContain("Edit fleet");
        expect(wrapper.find('[aria-label="Create Fleet"]').text()).not.toContain("Save fleet changes");

        await wrapper.find('input[aria-label="Fleet id"]').setValue("fleet-draft");
        await wrapper.find('input[aria-label="Fleet display name"]').setValue("Draft Fleet");
        await wrapper
            .find('input[aria-label="Enable fleet area circle drawing"]')
            .setChecked(true);
        await clickModalMapCircleDrawer(wrapper);

        const createButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Create fleet"));
        expect(createButton).toBeDefined();
        await createButton?.trigger("click");
        await flushUi();

        expect(tenantApi.createFleet).toHaveBeenCalledWith(
            expect.objectContaining({
                fleetId: "fleet-draft",
                displayName: "Draft Fleet",
            }),
        );

        await openFleetModal(wrapper);
        expect((wrapper.find('input[aria-label="Fleet id"]').element as HTMLInputElement).value).toBe("");
        expect((wrapper.find('input[aria-label="Fleet display name"]').element as HTMLInputElement).value).toBe("");
        expect(wrapper.text()).not.toContain("Selected circle:");

        const closeFleetModalButton = wrapper.find('[aria-label="Create Fleet"] button[type="button"]');
        expect(closeFleetModalButton.exists()).toBe(true);
        await closeFleetModalButton.trigger("click");
        await flushUi();

        await openMowerModal(wrapper);
        expect(wrapper.find('[aria-label="Register Mower"]').text()).toContain("Register mower");
        expect(wrapper.find('[aria-label="Register Mower"]').text()).not.toContain("Edit mower");
        expect(wrapper.find('[aria-label="Register Mower"]').text()).not.toContain("Save mower changes");

        await wrapper.find('input[aria-label="Mower id"]').setValue("mower-draft");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-DRAFT");
        await wrapper.find('input[aria-label="Simulated mower"]').setChecked(true);

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));
        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(tenantApi.registerMower).toHaveBeenCalledWith(
            expect.objectContaining({
                mowerId: "mower-draft",
                model: "LP-DRAFT",
                simulated: true,
            }),
        );

        await openMowerModal(wrapper);
        expect((wrapper.find('input[aria-label="Mower id"]').element as HTMLInputElement).value).toBe("");
        expect((wrapper.find('input[aria-label="Mower model"]').element as HTMLInputElement).value).toBe("");
        expect((wrapper.find('input[aria-label="Simulated mower"]').element as HTMLInputElement).checked).toBe(false);
    });

    it("closes modals when Escape is pressed", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: true,
                },
            },
        });

        await openFleetModal(wrapper);
        expect(wrapper.find('[aria-label="Create Fleet"]').exists()).toBe(true);

        window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
        await flushUi();

        expect(wrapper.find('[aria-label="Create Fleet"]').exists()).toBe(false);
    });

    it("switches fleet modal between create and edit mode affordances", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: true,
                },
            },
        });

        await openFleetModal(wrapper);
        expect(wrapper.find('[aria-label="Create Fleet"]').exists()).toBe(true);
        expect(wrapper.text()).toContain("Create fleet");
        expect(wrapper.findAll("button").some((button) => button.text() === "Create fleet")).toBe(true);

        await wrapper
            .findAll("button")
            .find((button) => button.attributes("aria-label")?.includes("Edit fleet fleet-north"))
            ?.trigger("click");
        await flushUi();

        expect(wrapper.find('[aria-label="Edit Fleet"]').exists()).toBe(true);
        expect(wrapper.text()).toContain("Edit fleet");
        expect(wrapper.findAll("button").some((button) => button.text() === "Save fleet changes")).toBe(true);
        expect((wrapper.find('input[aria-label="Fleet id"]').element as HTMLInputElement).disabled).toBe(true);
    });

    it("selects a fleet area circle from modal map drawing and persists geometry for create flow", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected", "area-circle-draft"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-draft\', { center: { lat: 47.6714, lng: -122.3057 }, radiusMeters: 180 }); $emit(\'area-circle-selected\', { center: { lat: 47.6714, lng: -122.3057 }, radiusMeters: 180 })">Draw area circle</button>',
                    },
                },
            },
        });

        await openFleetModal(wrapper);
        await wrapper
            .find('input[aria-label="Enable fleet area circle drawing"]')
            .setChecked(true);
        await clickModalMapCircleDrawer(wrapper);
        await wrapper.find('input[aria-label="Fleet id"]').setValue("fleet-west");
        await wrapper.find('input[aria-label="Fleet display name"]').setValue("West Grounds");

        const createButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Create fleet"));
        expect(createButton).toBeDefined();
        await createButton?.trigger("click");
        await flushUi();

        expect(tenantApi.createFleet).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: "tenant-alpha",
                fleetId: "fleet-west",
                displayName: "West Grounds",
                areaGeometryType: "CIRCLE",
                areaCenterLat: 47.6714,
                areaCenterLng: -122.3057,
                areaRadiusMeters: 180,
            }),
        );
        expect(telemetry.upsertFleetInTelemetry).toHaveBeenCalledWith(
            "tenant-alpha",
            "fleet-west",
            "West Grounds",
            "circle:fleet-west",
        );
    });

    it("removes nearest-seed association copy from fleet circle placement guidance", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-draft", "area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-draft\', { center: { lat: 47.6714, lng: -122.3057 }, radiusMeters: 160 }); $emit(\'area-circle-selected\', { center: { lat: 47.6714, lng: -122.3057 }, radiusMeters: 160 })">Draw area circle</button>',
                    },
                },
            },
        });

        await openFleetModal(wrapper);
        await wrapper
            .find('input[aria-label="Enable fleet area circle drawing"]')
            .setChecked(true);
        await clickModalMapCircleDrawer(wrapper);
        await flushUi();

        expect(wrapper.text()).toContain("Selected circle: center 47.671400, -122.305700");
        expect(wrapper.text()).toContain("radius 160.00 m");
        expect(wrapper.text()).not.toContain("The nearest area is selected for fleet association.");
    });

    it("includes circle geometry fields in fleet create payload after drag-circle flow", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.6714, lng: -122.3057 }, radiusMeters: 280 })">Draw area circle</button>',
                    },
                },
            },
        });

        await openFleetModal(wrapper);
        await wrapper
            .find('input[aria-label="Enable fleet area circle drawing"]')
            .setChecked(true);
        await clickModalMapCircleDrawer(wrapper);
        await wrapper.find('input[aria-label="Fleet id"]').setValue("fleet-circle-create");
        await wrapper.find('input[aria-label="Fleet display name"]').setValue("Circle Create Fleet");

        const createButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Create fleet"));
        expect(createButton).toBeDefined();
        await createButton?.trigger("click");
        await flushUi();

        expect(tenantApi.createFleet).toHaveBeenCalledWith(
            expect.objectContaining({
                fleetId: "fleet-circle-create",
                displayName: "Circle Create Fleet",
                areaCenterLat: 47.6714,
                areaCenterLng: -122.3057,
                areaRadiusMeters: 280,
            }),
        );
    });

    it("includes circle geometry fields in fleet edit payload submissions", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.6655, lng: -122.3112 }, radiusMeters: 280 })">Draw area circle</button>',
                    },
                },
            },
        });

        await wrapper
            .findAll("button")
            .find((button) => button.attributes("aria-label")?.includes("Edit fleet fleet-north"))
            ?.trigger("click");
        await flushUi();

        await wrapper
            .find('input[aria-label="Enable fleet area circle drawing"]')
            .setChecked(true);
        await clickModalMapCircleDrawer(wrapper);
        await wrapper.find('input[aria-label="Fleet display name"]').setValue("North Circle Fleet");

        const saveButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Save fleet changes"));
        expect(saveButton).toBeDefined();
        await saveButton?.trigger("click");
        await flushUi();

        expect(tenantApi.updateFleet).toHaveBeenCalledWith(
            expect.objectContaining({
                fleetId: "fleet-north",
                displayName: "North Circle Fleet",
                areaCenterLat: 47.6655,
                areaCenterLng: -122.3112,
                areaRadiusMeters: 280,
            }),
        );
    });

    it("defaults mower modal tenant/fleet from page selection and allows override before submit", async () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: true,
                },
            },
        });

        await openMowerModal(wrapper);

        const tenantSelect = wrapper.find('select[aria-label="Mower tenant"]');
        const fleetSelect = wrapper.find('select[aria-label="Mower fleet"]');
        expect((tenantSelect.element as HTMLSelectElement).value).toBe("tenant-alpha");
        expect((fleetSelect.element as HTMLSelectElement).value).toBe("fleet-north");

        await tenantSelect.setValue("tenant-beta");
        await flushUi();
        await fleetSelect.setValue("fleet-east");
        await wrapper.find('input[aria-label="Mower id"]').setValue("mower-210");
        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-BETA");

        const registerButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Register mower"));
        expect(registerButton).toBeDefined();
        await registerButton?.trigger("click");
        await flushUi();

        expect(tenantApi.registerMower).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: "tenant-beta",
                fleetId: "fleet-east",
                mowerId: "mower-210",
                model: "LP-BETA",
            }),
        );
    });

    it("calls backend fleet update endpoint in edit mode and reopens with saved values after reload", async () => {
        const fleetRecords = [
            {
                fleetId: "fleet-north",
                tenantId: "tenant-alpha",
                displayName: "North Campus",
                areaIds: ["area-nr-a"],
            },
        ];
        const getTenantFleetsSpy = vi
            .spyOn(telemetry, "getTenantFleets")
            .mockImplementation((tenant) =>
                fleetRecords.filter((fleet) => fleet.tenantId === tenant),
            );
        vi.mocked(telemetry.upsertFleetInTelemetry).mockImplementation(
            (tenant, fleetId, displayName, areaId) => {
                const existing = fleetRecords.find(
                    (fleet) =>
                        fleet.tenantId === tenant && fleet.fleetId === fleetId,
                );
                if (existing) {
                    existing.displayName = displayName;
                    existing.areaIds = [areaId];
                }
            },
        );

        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: {
                        emits: ["area-circle-selected"],
                        template:
                            '<button aria-label="Map circle drawer" @click="$emit(\'area-circle-selected\', { center: { lat: 47.674, lng: -122.301 }, radiusMeters: 210 })">Draw area circle</button>',
                    },
                },
            },
        });

        const editButton = wrapper
            .findAll("button")
            .find((button) => button.attributes("aria-label")?.includes("Edit fleet fleet-north"));
        expect(editButton).toBeDefined();
        await editButton?.trigger("click");
        await flushUi();

        await wrapper.find('input[aria-label="Fleet display name"]').setValue("North Grounds Rev A");
        await wrapper
            .find('input[aria-label="Enable fleet area circle drawing"]')
            .setChecked(true);

        await clickModalMapCircleDrawer(wrapper);

        const saveButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Save fleet changes"));
        expect(saveButton).toBeDefined();
        await saveButton?.trigger("click");
        await flushUi();

        expect(tenantApi.updateFleet).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: "tenant-alpha",
                role: "ADMIN",
                fleetId: "fleet-north",
                displayName: "North Grounds Rev A",
                areaGeometryType: "CIRCLE",
                areaCenterLat: 47.674,
                areaCenterLng: -122.301,
                areaRadiusMeters: 210,
            }),
        );
        expect(tenantApi.createFleet).not.toHaveBeenCalled();

        const reopenEditButton = wrapper
            .findAll("button")
            .find((button) => button.attributes("aria-label")?.includes("Edit fleet fleet-north"));
        expect(reopenEditButton).toBeDefined();
        await reopenEditButton?.trigger("click");
        await flushUi();

        expect(
            (wrapper.find('input[aria-label="Fleet display name"]').element as HTMLInputElement).value,
        ).toBe("North Grounds Rev A");

        getTenantFleetsSpy.mockRestore();
    });

    it("calls backend mower update endpoint in edit mode and reopens with saved values after reload", async () => {
        const mowerRecords = [
            {
                mowerId: "M-014",
                tenantId: "tenant-alpha",
                fleetId: "fleet-north",
                areaId: "area-nr-a",
                model: "LP-X3",
                status: "cutting" as const,
                batteryPercent: 78,
                runtimeMinutesToday: 298,
                coverageTodayHa: 1.7,
                lat: 47.6676,
                lng: -122.3531,
            },
        ];
        const getFleetMowersSpy = vi
            .spyOn(telemetry, "getFleetMowers")
            .mockImplementation((fleetId) =>
                mowerRecords.filter((mower) => mower.fleetId === fleetId),
            );
        const getTenantMowersSpy = vi
            .spyOn(telemetry, "getTenantMowers")
            .mockImplementation((tenantId) =>
                mowerRecords.filter((mower) => mower.tenantId === tenantId),
            );
        vi.mocked(telemetry.updateMowerInTelemetry).mockImplementation(
            (mowerId, update) => {
                const existing = mowerRecords.find((mower) => mower.mowerId === mowerId);
                if (!existing) {
                    return false;
                }

                existing.model = update.model ?? existing.model;
                existing.tenantId = update.tenantId ?? existing.tenantId;
                existing.fleetId = update.fleetId ?? existing.fleetId;
                return true;
            },
        );

        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: true,
                },
            },
        });

        const editButton = wrapper
            .findAll("button")
            .find((button) => button.attributes("aria-label")?.includes("Edit mower M-014"));
        expect(editButton).toBeDefined();
        await editButton?.trigger("click");
        await flushUi();

        await wrapper.find('input[aria-label="Mower model"]').setValue("LP-X3-REV2");
        await wrapper.find('select[aria-label="Mower tenant"]').setValue("tenant-alpha");
        await wrapper.find('select[aria-label="Mower fleet"]').setValue("fleet-north");

        const saveButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Save mower changes"));
        expect(saveButton).toBeDefined();
        await saveButton?.trigger("click");
        await flushUi();

        expect(telemetry.updateMowerInTelemetry).toHaveBeenCalledWith(
            "M-014",
            expect.objectContaining({
                tenantId: "tenant-alpha",
                fleetId: "fleet-north",
                model: "LP-X3-REV2",
            }),
        );
        expect(tenantApi.updateMower).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: "tenant-alpha",
                role: "ADMIN",
                sourceFleetId: "fleet-north",
                mowerId: "M-014",
                model: "LP-X3-REV2",
            }),
        );
        const updatePayload = vi.mocked(tenantApi.updateMower).mock.calls[0][0] as Record<string, unknown>;
        expect(updatePayload).not.toHaveProperty("fleetId");
        expect(tenantApi.registerMower).not.toHaveBeenCalled();

        const reopenEditButton = wrapper
            .findAll("button")
            .find((button) => button.attributes("aria-label")?.includes("Edit mower M-014"));
        expect(reopenEditButton).toBeDefined();
        await reopenEditButton?.trigger("click");
        await flushUi();

        expect(
            (wrapper.find('input[aria-label="Mower model"]').element as HTMLInputElement).value,
        ).toBe("LP-X3-REV2");

        getFleetMowersSpy.mockRestore();
        getTenantMowersSpy.mockRestore();
    });

    it("sends source fleet path and target fleet payload when editing mower fleet assignment", async () => {
        const mowerRecords = [
            {
                mowerId: "M-014",
                tenantId: "tenant-alpha",
                fleetId: "fleet-north",
                areaId: "area-nr-a",
                model: "LP-X3",
                status: "cutting" as const,
                batteryPercent: 78,
                runtimeMinutesToday: 298,
                coverageTodayHa: 1.7,
                lat: 47.6676,
                lng: -122.3531,
            },
        ];
        const getFleetMowersSpy = vi
            .spyOn(telemetry, "getFleetMowers")
            .mockImplementation((fleetId) =>
                mowerRecords.filter((mower) => mower.fleetId === fleetId),
            );
        const getTenantMowersSpy = vi
            .spyOn(telemetry, "getTenantMowers")
            .mockImplementation((tenantId) =>
                mowerRecords.filter((mower) => mower.tenantId === tenantId),
            );

        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: true,
                },
            },
        });

        const editButton = wrapper
            .findAll("button")
            .find((button) => button.attributes("aria-label")?.includes("Edit mower M-014"));
        expect(editButton).toBeDefined();
        await editButton?.trigger("click");
        await flushUi();

        await wrapper.find('select[aria-label="Mower fleet"]').setValue("fleet-lake");

        const saveButton = wrapper
            .findAll("button")
            .find((button) => button.text().includes("Save mower changes"));
        expect(saveButton).toBeDefined();
        await saveButton?.trigger("click");
        await flushUi();

        expect(tenantApi.updateMower).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: "tenant-alpha",
                role: "ADMIN",
                sourceFleetId: "fleet-north",
                fleetId: "fleet-lake",
                mowerId: "M-014",
            }),
        );
        expect(telemetry.updateMowerInTelemetry).toHaveBeenCalledWith(
            "M-014",
            expect.objectContaining({
                tenantId: "tenant-alpha",
                fleetId: "fleet-lake",
            }),
        );

        getFleetMowersSpy.mockRestore();
        getTenantMowersSpy.mockRestore();
    });

    it("retires the legacy run tenant simulation card", () => {
        const wrapper = mount(FleetView, {
            global: {
                stubs: {
                    MowerMap: true,
                },
            },
        });

        expect(wrapper.text()).not.toContain("Run tenant simulation");
        expect(wrapper.text()).not.toContain("Simulation API");
    });
});
