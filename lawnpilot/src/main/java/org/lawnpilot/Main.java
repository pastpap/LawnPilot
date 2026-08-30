package org.lawnpilot;

import org.lawnpilot.exceptions.InvalidInputException;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.ParsedMowerInstructions;
import org.lawnpilot.plugin.registry.PluginRegistry;
import org.lawnpilot.parser.InputParser;
import org.lawnpilot.runtime.SimulationEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            runSimulation(readInput(args));
        } catch (InvalidInputException | IOException ex) {
            System.err.println("Input error: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void runSimulation(List<String> data) {
        InputParser parser = new InputParser();
        Lawn lawn = parser.parseLawn(data);
        List<ParsedMowerInstructions> mowers = parser.parseMowers(data.subList(1, data.size()), lawn);
        PluginRegistry pluginRegistry = PluginRegistry.withDefaults();
        SimulationEngine engine = new SimulationEngine(pluginRegistry);

        for (String line : engine.run(mowers, lawn)) {
            System.out.println(line);
        }
    }

    private static List<String> readInput(String[] args) throws IOException {
        if (args.length > 0) {
            return normalizeInputLines(Files.readAllLines(Path.of(args[0])));
        }

        if (System.console() == null) {
            List<String> stdinLines = readStdinLines();
            if (!stdinLines.isEmpty()) {
                return stdinLines;
            }
        }

        return List.of("5 5", "1 2 N", "LFLFLFLFF", "3 3 E", "FFRFFRFRRF");
    }

    private static List<String> readStdinLines() throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return normalizeInputLines(lines);
    }

    private static List<String> normalizeInputLines(List<String> lines) {
        List<String> normalized = new ArrayList<>();
        for (String line : lines) {
            if (line != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized;
    }
}