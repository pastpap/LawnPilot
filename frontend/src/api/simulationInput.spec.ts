import { describe, expect, it } from "vitest";
import { parseSimulationInput } from "./simulationInput";

describe("parseSimulationInput", () => {
    it("trims and removes empty lines", () => {
        expect(parseSimulationInput(" 5 5 \n\n1 2 N\n LFLF \n   ")).toEqual([
            "5 5",
            "1 2 N",
            "LFLF",
        ]);
    });

    it("returns an empty list for blank input", () => {
        expect(parseSimulationInput("   \n\n  ")).toEqual([]);
    });
});