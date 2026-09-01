import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import MowerMap from "./MowerMap.vue";
import L from "leaflet";

vi.mock("leaflet", () => {
    let clickHandler: ((event: { latlng: { lat: number; lng: number } }) => void) | null = null;
    const fitBoundsSpy = vi.fn();
    const setViewSpy = vi.fn();

    const layerGroup = () => ({
        addTo() {
            return this;
        },
        clearLayers() {
            return undefined;
        },
    });

    const markerShape = () => ({
        bindTooltip() {
            return this;
        },
        bindPopup() {
            return this;
        },
        addTo() {
            return this;
        },
    });

    const map = () => ({
        dragging: { disable: vi.fn(), enable: vi.fn() },
        setView(...args: unknown[]) {
            setViewSpy(...args);
            return this;
        },
        on(event: string, handler: typeof clickHandler) {
            if (event === "click") {
                clickHandler = handler;
            }
            return this;
        },
        off(event: string) {
            if (event === "click") {
                clickHandler = null;
            }
            return this;
        },
        fitBounds(...args: unknown[]) {
            fitBoundsSpy(...args);
            return this;
        },
        remove() {
            clickHandler = null;
            return undefined;
        },
    });

    const leafletMock = {
        map,
        layerGroup,
        circle: markerShape,
        circleMarker: markerShape,
        tileLayer: () => ({
            addTo() {
                return this;
            },
        }),
        latLngBounds: () => ({
            pad() {
                return this;
            },
        }),
        __triggerClick(lat: number, lng: number) {
            clickHandler?.({ latlng: { lat, lng } });
        },
        __getViewportCalls() {
            return {
                fitBounds: fitBoundsSpy,
                setView: setViewSpy,
            };
        },
        __reset() {
            clickHandler = null;
            fitBoundsSpy.mockReset();
            setViewSpy.mockReset();
        },
    };

    return { default: leafletMock };
});

describe("MowerMap.vue", () => {
    it("only recenters when the selected mower id changes", async () => {
        (L as unknown as { __reset: () => void }).__reset();

        const wrapper = mount(MowerMap, {
            props: {
                areas: [],
                mowers: [
                    {
                        mowerId: "mower-1",
                        fleetId: "fleet-1",
                        tenantId: "tenant-1",
                        status: "cutting",
                        batteryPercent: 94,
                        lat: 47.61,
                        lng: -122.33,
                    },
                    {
                        mowerId: "mower-2",
                        fleetId: "fleet-1",
                        tenantId: "tenant-1",
                        status: "transit",
                        batteryPercent: 88,
                        lat: 47.612,
                        lng: -122.335,
                    },
                ],
            },
        });

        const viewportCalls = (L as unknown as {
            __getViewportCalls: () => { fitBounds: ReturnType<typeof vi.fn>; setView: ReturnType<typeof vi.fn> };
        }).__getViewportCalls();

        expect(viewportCalls.fitBounds).toHaveBeenCalledTimes(1);

        await wrapper.setProps({ selectedMowerId: "mower-1" });

        expect(viewportCalls.setView).toHaveBeenCalledTimes(2);
        expect(viewportCalls.setView).toHaveBeenLastCalledWith([47.61, -122.33], 15);

        await wrapper.setProps({
            mowers: [
                {
                    mowerId: "mower-1",
                    fleetId: "fleet-1",
                    tenantId: "tenant-1",
                    status: "cutting",
                    batteryPercent: 93,
                    lat: 47.6199,
                    lng: -122.3399,
                },
                {
                    mowerId: "mower-2",
                    fleetId: "fleet-1",
                    tenantId: "tenant-1",
                    status: "transit",
                    batteryPercent: 87,
                    lat: 47.6125,
                    lng: -122.336,
                },
            ],
        });

        expect(viewportCalls.fitBounds).toHaveBeenCalledTimes(1);
        expect(viewportCalls.setView).toHaveBeenCalledTimes(2);

        await wrapper.setProps({ selectedMowerId: "mower-2" });

        expect(viewportCalls.setView).toHaveBeenCalledTimes(3);
        expect(viewportCalls.setView).toHaveBeenLastCalledWith([47.6125, -122.336], 15);
    });

    it("emits pin-selected when map is clicked in pin placement mode", () => {
        (L as unknown as { __reset: () => void }).__reset();

        const wrapper = mount(MowerMap, {
            props: {
                mowers: [],
                areas: [],
                pinPlacementEnabled: true,
            },
        });

        (L as unknown as { __triggerClick: (lat: number, lng: number) => void }).__triggerClick(
            47.6111111,
            -122.3333333,
        );

        expect(wrapper.emitted("pin-selected")).toBeTruthy();
        expect(wrapper.emitted("pin-selected")?.[0]).toEqual([
            { lat: 47.611111, lng: -122.333333 },
        ]);

        (L as unknown as { __triggerClick: (lat: number, lng: number) => void }).__triggerClick(
            47.6012349,
            -122.3987654,
        );

        expect(wrapper.emitted("pin-selected")?.[1]).toEqual([
            { lat: 47.601235, lng: -122.398765 },
        ]);
    });

    it("does not emit pin-selected when pin placement mode is disabled", () => {
        (L as unknown as { __reset: () => void }).__reset();

        const wrapper = mount(MowerMap, {
            props: {
                mowers: [],
                areas: [],
                pinPlacementEnabled: false,
            },
        });

        (L as unknown as { __triggerClick: (lat: number, lng: number) => void }).__triggerClick(
            47.6,
            -122.3,
        );

        expect(wrapper.emitted("pin-selected")).toBeUndefined();
    });

    it("only emits coordinates after pin placement mode is turned on", async () => {
        (L as unknown as { __reset: () => void }).__reset();

        const wrapper = mount(MowerMap, {
            props: {
                mowers: [],
                areas: [],
                pinPlacementEnabled: false,
            },
        });

        (L as unknown as { __triggerClick: (lat: number, lng: number) => void }).__triggerClick(
            47.55,
            -122.41,
        );
        expect(wrapper.emitted("pin-selected")).toBeUndefined();

        await wrapper.setProps({ pinPlacementEnabled: true });
        (L as unknown as { __triggerClick: (lat: number, lng: number) => void }).__triggerClick(
            47.5500004,
            -122.4100004,
        );

        expect(wrapper.emitted("pin-selected")).toEqual([
            [{ lat: 47.55, lng: -122.41 }],
        ]);
    });
});
