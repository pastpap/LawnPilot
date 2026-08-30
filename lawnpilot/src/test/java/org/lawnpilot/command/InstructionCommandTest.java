package org.lawnpilot.command;

import org.junit.jupiter.api.Test;
import org.lawnpilot.model.Direction;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;
import org.lawnpilot.model.ParsedMowerInstructions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstructionCommandTest {

    @Test
    void parsedCommandsDriveMowerToExpectedPosition() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(1, 2, Direction.N);
        ParsedMowerInstructions parsed = new ParsedMowerInstructions(
                mower,
                "LFLFLFLFF",
                List.of(
                        InstructionCommand.L,
                        InstructionCommand.F,
                        InstructionCommand.L,
                        InstructionCommand.F,
                        InstructionCommand.L,
                        InstructionCommand.F,
                        InstructionCommand.L,
                        InstructionCommand.F,
                        InstructionCommand.F));

        parsed.getMower().execute(parsed.getCommands(), lawn);

        assertEquals("1 3 N", parsed.getMower().toString());
    }

    @Test
    void unknownInstructionTokenIsIgnoredByStringExecutor() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(2, 2, Direction.N);

        mower.execute("FXF", lawn);

        assertEquals("2 4 N", mower.toString());
    }
}
