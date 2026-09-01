import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import TrendChart from "./TrendChart.vue";

describe("TrendChart.vue", () => {
    it("renders finite SVG geometry when fed invalid point values", () => {
        const wrapper = mount(TrendChart, {
            props: {
                title: "Area health",
                data: [
                    { label: "A", value: Number.NaN },
                    { label: "B", value: Number.POSITIVE_INFINITY },
                    { label: "C", value: 42 },
                ],
                height: 220,
            },
        });

        const groups = wrapper.findAll("g");
        expect(groups.length).toBe(3);

        for (const group of groups) {
            const transform = group.attributes("transform") ?? "";
            expect(transform.includes("NaN")).toBe(false);
        }

        const rects = wrapper.findAll("rect");
        expect(rects.length).toBe(3);
        for (const rect of rects) {
            const height = rect.attributes("height") ?? "";
            expect(height).not.toBe("NaN");
            expect(Number.isFinite(Number(height))).toBe(true);
        }
    });
});
