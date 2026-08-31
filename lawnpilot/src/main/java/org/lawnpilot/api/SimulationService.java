package org.lawnpilot.api;

import org.lawnpilot.exceptions.InvalidInputException;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.ParsedMowerInstructions;
import org.lawnpilot.parser.InputParser;
import org.lawnpilot.plugin.registry.PluginRegistry;
import org.lawnpilot.runtime.SimulationEngine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimulationService {

    public List<String> runSimulation(List<String> inputLines) {
        if (inputLines == null || inputLines.isEmpty()) {
            throw new InvalidInputException("Input must contain at least the lawn definition line.");
        }

        InputParser parser = new InputParser();
        Lawn lawn = parser.parseLawn(inputLines);
        List<ParsedMowerInstructions> mowers = parser.parseMowers(inputLines.subList(1, inputLines.size()), lawn);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        return engine.run(mowers, lawn);
    }
}
