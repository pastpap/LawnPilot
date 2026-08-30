package org.lawnpilot.parser;

import org.junit.jupiter.api.Test;
import org.lawnpilot.exceptions.InvalidInstructionException;
import org.lawnpilot.exceptions.InvalidLawnException;
import org.lawnpilot.exceptions.InvalidMowerDefinitionException;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.ParsedMowerInstructions;
import org.lawnpilot.model.Position;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputParserTest {

    private final InputParser parser = new InputParser();

    @Test
    void parsesValidInput() {
        List<String> lines = List.of(
                "5 5",
                "1 2 N",
                "LFLFLFLFF");

        Lawn lawn = parser.parseLawn(lines);
        List<ParsedMowerInstructions> mowers = parser.parseMowers(lines.subList(1, lines.size()), lawn);

        assertEquals(5, lawn.getMaxX());
        assertEquals(5, lawn.getMaxY());
        assertEquals(1, mowers.size());
        assertEquals("LFLFLFLFF", mowers.get(0).getInstructions());
        assertEquals(9, mowers.get(0).getCommands().size());
    }

    @Test
    void rejectsOddMowerDefinitionLineCount() {
        List<String> mowerLines = List.of("1 2 N");

        assertThrows(InvalidMowerDefinitionException.class,
                () -> parser.parseMowers(mowerLines, new Lawn(5, 5)));
    }

    @Test
    void rejectsInvalidInstructionCharacter() {
        List<String> mowerLines = List.of("1 2 N", "LFX");

        assertThrows(InvalidInstructionException.class,
                () -> parser.parseMowers(mowerLines, new Lawn(5, 5)));
    }

    @Test
    void rejectsOutOfBoundsStartPosition() {
        List<String> mowerLines = List.of("9 9 N", "F");

        assertThrows(InvalidMowerDefinitionException.class,
                () -> parser.parseMowers(mowerLines, new Lawn(5, 5)));
    }

    @Test
    void rejectsInvalidDirectionToken() {
        List<String> mowerLines = List.of("1 2 X", "F");

        assertThrows(InvalidMowerDefinitionException.class,
                () -> parser.parseMowers(mowerLines, new Lawn(5, 5)));
    }

    @Test
    void rejectsNegativeLawnBounds() {
        List<String> lines = List.of("-1 5");

        assertThrows(InvalidLawnException.class,
                () -> parser.parseLawn(lines));
    }

    @Test
    void parsesMaskedLawnDefinitionAndKeepsExpectedBounds() {
        Lawn lawn = parser.parseLawn(List.of("MASK 0,0 1,0 1,1 2,1"));

        assertEquals(2, lawn.getMaxX());
        assertEquals(1, lawn.getMaxY());
        assertEquals(true, lawn.isInside(new Position(1, 1)));
        assertEquals(false, lawn.isInside(new Position(2, 0)));
    }

    @Test
    void rejectsMaskedLawnWithoutCells() {
        assertThrows(InvalidLawnException.class,
                () -> parser.parseLawn(List.of("MASK")));
    }

    @Test
    void rejectsMaskedLawnWithMalformedCellToken() {
        assertThrows(InvalidLawnException.class,
                () -> parser.parseLawn(List.of("MASK 0,0 1x1")));
    }

    @Test
    void rejectsMaskedLawnWithDuplicateCells() {
        assertThrows(InvalidLawnException.class,
                () -> parser.parseLawn(List.of("MASK 0,0 1,1 1,1")));
    }

    @Test
    void rejectsMowerStartOutsideMaskedLawn() {
        Lawn lawn = parser.parseLawn(List.of("MASK 0,0 1,0 1,1"));
        List<String> mowerLines = List.of("0 1 N", "F");

        assertThrows(InvalidMowerDefinitionException.class,
                () -> parser.parseMowers(mowerLines, lawn));
    }
}
