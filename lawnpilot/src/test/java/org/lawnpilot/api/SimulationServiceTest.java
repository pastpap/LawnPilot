package org.lawnpilot.api;

import org.junit.jupiter.api.Test;
import org.lawnpilot.exceptions.InvalidInputException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationServiceTest {

    private final SimulationService simulationService = new SimulationService();

    @Test
    void runSimulationReturnsReferenceScenarioOutput() {
        List<String> input = List.of(
                "5 5",
                "1 2 N",
                "LFLFLFLFF",
                "3 3 E",
                "FFRFFRFRRF");

        List<String> output = simulationService.runSimulation(input);

        assertEquals(List.of("1 3 N", "5 1 E"), output);
    }

    @Test
    void runSimulationRejectsMissingInput() {
        assertThrows(InvalidInputException.class, () -> simulationService.runSimulation(List.of()));
    }
}
