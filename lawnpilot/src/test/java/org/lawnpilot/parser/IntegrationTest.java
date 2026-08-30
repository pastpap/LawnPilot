package org.lawnpilot.parser;

import org.junit.jupiter.api.Test;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.ParsedMowerInstructions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegrationTest {

    @Test
    void referenceScenarioProducesExpectedOutput() {
        List<String> data = List.of(
                "5 5",
                "1 2 N",
                "LFLFLFLFF",
                "3 3 E",
                "FFRFFRFRRF");

        InputParser parser = new InputParser();
        Lawn lawn = parser.parseLawn(data);
        List<ParsedMowerInstructions> mowers = parser.parseMowers(data.subList(1, data.size()), lawn);

        for (ParsedMowerInstructions mowerData : mowers) {
            mowerData.getMower().execute(mowerData.getCommands(), lawn);
        }

        assertEquals("1 3 N", mowers.get(0).getMower().toString());
        assertEquals("5 1 E", mowers.get(1).getMower().toString());
    }

    @Test
    void originStartScenarioProducesExpectedOutput() {
        List<String> data = List.of(
                "5 5",
                "0 0 N",
                "FFRFF");

        InputParser parser = new InputParser();
        Lawn lawn = parser.parseLawn(data);
        List<ParsedMowerInstructions> mowers = parser.parseMowers(data.subList(1, data.size()), lawn);

        for (ParsedMowerInstructions mowerData : mowers) {
            mowerData.getMower().execute(mowerData.getCommands(), lawn);
        }

        assertEquals("2 2 E", mowers.get(0).getMower().toString());
    }
}
