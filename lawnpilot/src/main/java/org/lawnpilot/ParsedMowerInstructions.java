package org.lawnpilot;

import lombok.Getter;

import java.util.List;

@Getter
public final class ParsedMowerInstructions {
    private final Mower mower;
    private final String instructions;
    private final List<MowerCommand> commands;

    public ParsedMowerInstructions(Mower mower, String instructions, List<MowerCommand> commands) {
        this.mower = mower;
        this.instructions = instructions;
        this.commands = List.copyOf(commands);
    }
}