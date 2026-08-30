package org.lawnpilot;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class InputParser {

    public Lawn parseLawn(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidLawnException("Missing lawn definition line.");
        }

        String[] values = tokenize(lines.get(0));
        if (values.length != 2) {
            throw new InvalidLawnException("Lawn definition must contain exactly two integers: maxX maxY.");
        }

        int maxX = parseCoordinate(values[0], "lawn maxX");
        int maxY = parseCoordinate(values[1], "lawn maxY");
        if (maxX < 0 || maxY < 0) {
            throw new InvalidLawnException("Lawn bounds must be non-negative.");
        }

        return new Lawn(maxX, maxY);
    }

    public List<MowerData> parseMowers(List<String> lines) {
        return parseMowers(lines, null);
    }

    public List<MowerData> parseMowers(List<String> lines, Lawn lawn) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidMowerDefinitionException("Missing mower definitions.");
        }
        if (lines.size() % 2 != 0) {
            throw new InvalidMowerDefinitionException(
                    "Mower definitions must be provided in pairs: position line + instructions line.");
        }

        List<MowerData> r = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += 2) {
            String[] values = tokenize(lines.get(i));
            if (values.length != 3) {
                throw new InvalidMowerDefinitionException("Mower position line must contain: x y direction.");
            }

            int x = parseCoordinate(values[0], "mower x");
            int y = parseCoordinate(values[1], "mower y");
            if (x < 0 || y < 0) {
                throw new InvalidMowerDefinitionException("Mower coordinates must be non-negative.");
            }

            Direction direction = parseDirection(values[2]);
            if (lawn != null && !lawn.isInside(x, y)) {
                throw new InvalidMowerDefinitionException("Mower start position must be inside lawn bounds.");
            }

            String instructions = lines.get(i + 1) == null ? "" : lines.get(i + 1).trim();
            validateInstructions(instructions);

            Mower mower = new Mower(x, y, direction);
            r.add(new MowerData(mower, instructions));
        }
        return r;
    }

    private String[] tokenize(String line) {
        if (line == null || line.isBlank()) {
            return new String[0];
        }
        return line.trim().split("\\s+");
    }

    private int parseCoordinate(String token, String fieldName) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            throw new InvalidInputException("Invalid integer for " + fieldName + ": " + token);
        }
    }

    private Direction parseDirection(String token) {
        try {
            return Direction.valueOf(token);
        } catch (IllegalArgumentException ex) {
            throw new InvalidMowerDefinitionException("Invalid direction: " + token + ". Expected one of N, E, S, W.");
        }
    }

    private void validateInstructions(String instructions) {
        for (char c : instructions.toCharArray()) {
            if (c != 'L' && c != 'R' && c != 'F') {
                throw new InvalidInstructionException("Invalid instruction: " + c + ". Expected only L, R, F.");
            }
        }
    }

    @Getter
    public static class MowerData {
        private final Mower mower;
        private final String instructions;

        public MowerData(Mower mower, String instructions) {
            this.mower = mower;
            this.instructions = instructions;
        }
    }
}
