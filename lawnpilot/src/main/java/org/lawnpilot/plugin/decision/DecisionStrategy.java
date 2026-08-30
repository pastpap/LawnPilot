package org.lawnpilot.plugin.decision;

import org.lawnpilot.command.MowerCommand;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.ParsedMowerInstructions;
import org.lawnpilot.plugin.RuntimePlugin;

import java.util.List;

public interface DecisionStrategy extends RuntimePlugin {
    List<MowerCommand> decide(ParsedMowerInstructions mowerInstructions, Lawn lawn);
}
