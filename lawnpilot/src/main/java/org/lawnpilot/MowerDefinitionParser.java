package org.lawnpilot;

final class MowerDefinitionParser {

    Mower parse(ListLineTokenizer tokenizer, String line, Lawn lawn) {
        String[] values = tokenizer.tokenize(line);
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

        return new Mower(new Position(x, y), direction);
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
}