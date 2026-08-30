package org.lawnpilot.parser;

import org.lawnpilot.exceptions.InvalidInputException;
import org.lawnpilot.exceptions.InvalidLawnException;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Position;
import org.lawnpilot.model.geometry.MaskedLawnGeometry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LawnDefinitionParser {

    private static final String MASK_PREFIX = "MASK";

    Lawn parse(ListLineTokenizer tokenizer, String line) {
        String[] values = tokenizer.tokenize(line);
        if (values.length == 0) {
            throw new InvalidLawnException("Missing lawn definition line.");
        }

        if (MASK_PREFIX.equalsIgnoreCase(values[0])) {
            return parseMaskedLawn(values);
        }

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

    private Lawn parseMaskedLawn(String[] values) {
        if (values.length < 2) {
            throw new InvalidLawnException("Masked lawn must define at least one cell: MASK x,y [x,y ...].");
        }

        List<Position> allowedCells = new ArrayList<>();
        Set<String> seenCells = new HashSet<>();
        for (int index = 1; index < values.length; index++) {
            Position cell = parseMaskedCell(values[index]);
            String key = cell.getX() + ":" + cell.getY();
            if (!seenCells.add(key)) {
                throw new InvalidLawnException("Duplicate masked lawn cell is not allowed: " + values[index]);
            }
            allowedCells.add(cell);
        }

        return new Lawn(new MaskedLawnGeometry(allowedCells));
    }

    private Position parseMaskedCell(String token) {
        String[] coordinates = token.split(",", -1);
        if (coordinates.length != 2 || coordinates[0].isBlank() || coordinates[1].isBlank()) {
            throw new InvalidLawnException("Invalid masked lawn cell '" + token + "'. Expected x,y.");
        }

        int x = parseMaskedCoordinate(coordinates[0].trim(), token, "x");
        int y = parseMaskedCoordinate(coordinates[1].trim(), token, "y");
        if (x < 0 || y < 0) {
            throw new InvalidLawnException("Masked lawn cell coordinates must be non-negative: " + token);
        }
        return new Position(x, y);
    }

    private int parseMaskedCoordinate(String token, String cellToken, String axisName) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            throw new InvalidLawnException(
                    "Invalid masked lawn " + axisName + " value in cell '" + cellToken + "': " + token);
        }
    }

    private int parseCoordinate(String token, String fieldName) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            throw new InvalidInputException("Invalid integer for " + fieldName + ": " + token);
        }
    }
}