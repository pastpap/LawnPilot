package org.lawnpilot.parser;

import org.lawnpilot.exceptions.InvalidLawnException;
import org.lawnpilot.exceptions.InvalidMowerDefinitionException;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;
import org.lawnpilot.model.ParsedMowerInstructions;

import java.util.ArrayList;
import java.util.List;

public class InputParser {

    private final ListLineTokenizer tokenizer = new ListLineTokenizer();
    private final LawnDefinitionParser lawnDefinitionParser = new LawnDefinitionParser();
    private final MowerDefinitionParser mowerDefinitionParser = new MowerDefinitionParser();
    private final InstructionSequenceParser instructionSequenceParser = new InstructionSequenceParser();

    public Lawn parseLawn(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidLawnException("Missing lawn definition line.");
        }
        return lawnDefinitionParser.parse(tokenizer, lines.get(0));
    }

    public List<ParsedMowerInstructions> parseMowers(List<String> lines) {
        return parseMowers(lines, null);
    }

    public List<ParsedMowerInstructions> parseMowers(List<String> lines, Lawn lawn) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidMowerDefinitionException("Missing mower definitions.");
        }
        if (lines.size() % 2 != 0) {
            throw new InvalidMowerDefinitionException(
                    "Mower definitions must be provided in pairs: position line + instructions line.");
        }

        List<ParsedMowerInstructions> parsedMowers = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += 2) {
            Mower mower = mowerDefinitionParser.parse(tokenizer, lines.get(i), lawn);
            InstructionSequenceParser.ParsedInstructionSequence sequence = instructionSequenceParser
                    .parse(lines.get(i + 1));
            parsedMowers.add(new ParsedMowerInstructions(mower, sequence.rawInstructions(), sequence.commands()));
        }
        return parsedMowers;
    }
}
