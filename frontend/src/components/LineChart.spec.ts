import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import LineChart from "./LineChart.vue";

describe("LineChart.vue", () => {
    it("renders finite polyline and point coordinates for invalid values", () => {
        const wrapper = mount(LineChart, {
            props: {
                title: "Runtime trend",
                data: [
                    { time: "Mon", value: Number.NaN },
                    { time: "Tue", value: 35 },
                    { time: "Wed", value: Number.NEGATIVE_INFINITY },
                ],
                height: 180,
            },
        });

        const polyline = wrapper.find("polyline");
        const points = polyline.attributes("points") ?? "";
        expect(points.includes("NaN")).toBe(false);

        const circles = wrapper.findAll("circle");
        expect(circles.length).toBe(3);
        for (const circle of circles) {
            const cx = circle.attributes("cx") ?? "";
            const cy = circle.attributes("cy") ?? "";
            expect(Number.isFinite(Number(cx))).toBe(true);
            expect(Number.isFinite(Number(cy))).toBe(true);
        }
    });
});
