package org.lawnpilot.parser;

import org.lawnpilot.exceptions.InvalidInputException;
import org.lawnpilot.exceptions.InvalidLawnException;
import org.lawnpilot.model.Lawn;

final class LawnDefinitionParser {

    Lawn parse(ListLineTokenizer tokenizer, String line) {
        String[] values = tokenizer.tokenize(line);
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

    private int parseCoordinate(String token, String fieldName) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            throw new InvalidInputException("Invalid integer for " + fieldName + ": " + token);
        }
    }
}