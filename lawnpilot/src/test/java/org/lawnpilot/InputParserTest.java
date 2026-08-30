package org.lawnpilot;

import org.junit.jupiter.api.Test;

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
}
