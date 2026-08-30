package org.lawnpilot.plugin.decision;

import org.lawnpilot.command.MowerCommand;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.ParsedMowerInstructions;
import org.lawnpilot.plugin.PluginMetadata;

import java.util.List;

public final class AutonomousDecisionStrategyStub implements DecisionStrategy {

    private static final PluginMetadata METADATA = new PluginMetadata(
            "autonomous-decision-strategy-stub",
            "1.0.0",
            "1.0");

    @Override
    public List<MowerCommand> decide(ParsedMowerInstructions mowerInstructions, Lawn lawn) {
        return List.of();
    }

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }
}
