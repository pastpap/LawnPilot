package org.lawnpilot.parser;

import org.lawnpilot.exceptions.InvalidInstructionException;
import org.lawnpilot.command.InstructionCommand;
import org.lawnpilot.command.MowerCommand;

import java.util.ArrayList;
import java.util.List;

final class InstructionSequenceParser {

    ParsedInstructionSequence parse(String line) {
        String instructions = line == null ? "" : line.trim();
        List<MowerCommand> commands = new ArrayList<>();

        for (char token : instructions.toCharArray()) {
            InstructionCommand command = InstructionCommand.tryParse(token)
                    .orElseThrow(() -> new InvalidInstructionException(
                            "Invalid instruction: " + token + ". Expected only L, R, F."));
            commands.add(command);
        }

        return new ParsedInstructionSequence(instructions, commands);
    }

    static final class ParsedInstructionSequence {
        private final String rawInstructions;
        private final List<MowerCommand> commands;

        ParsedInstructionSequence(String rawInstructions, List<MowerCommand> commands) {
            this.rawInstructions = rawInstructions;
            this.commands = List.copyOf(commands);
        }

        String rawInstructions() {
            return rawInstructions;
        }

        List<MowerCommand> commands() {
            return commands;
        }
    }
}