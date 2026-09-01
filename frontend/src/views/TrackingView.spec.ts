import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import TrackingView from "./TrackingView.vue";
import * as telemetry from "../data/telemetry";

async function flushUi(): Promise<void> {
    await Promise.resolve();
    await nextTick();
    await Promise.resolve();
    await nextTick();
}

describe("TrackingView.vue polling", () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.spyOn(telemetry, "ensureTelemetryLoaded").mockResolvedValue();
        vi.spyOn(telemetry, "refreshTenantTelemetry").mockResolvedValue();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it("refreshes telemetry on interval and cleans up on unmount", async () => {
        const wrapper = mount(TrackingView, {
            global: {
                stubs: {
                    MowerMap: true,
                    TrendChart: true,
                    MowerControlPanel: true,
                },
            },
        });

        await flushUi();

        vi.advanceTimersByTime(3000);
        await flushUi();

        const refreshSpy = vi.mocked(telemetry.refreshTenantTelemetry);
        expect(refreshSpy).toHaveBeenCalledWith("tenant-alpha", "ADMIN");

        const callCountBeforeUnmount = refreshSpy.mock.calls.length;
        wrapper.unmount();

        vi.advanceTimersByTime(6000);
        await flushUi();

        expect(refreshSpy).toHaveBeenCalledTimes(callCountBeforeUnmount);
    });
});
