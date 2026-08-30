package org.lawnpilot.runtime;

import org.lawnpilot.command.InstructionCommand;
import org.lawnpilot.command.MowerCommand;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;
import org.lawnpilot.model.ParsedMowerInstructions;
import org.lawnpilot.model.Position;
import org.lawnpilot.plugin.collision.CollisionPolicy;
import org.lawnpilot.plugin.decision.DecisionStrategy;
import org.lawnpilot.plugin.output.OutputFormatter;
import org.lawnpilot.plugin.registry.PluginRegistry;

import java.util.ArrayList;
import java.util.List;

public final class SimulationEngine {

    private final PluginRegistry pluginRegistry;

    public SimulationEngine(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public List<String> run(List<ParsedMowerInstructions> mowerInstructions, Lawn lawn) {
        List<String> outputLines = new ArrayList<>();
        List<Mower> allMowers = mowerInstructions.stream().map(ParsedMowerInstructions::getMower).toList();

        for (ParsedMowerInstructions parsed : mowerInstructions) {
            List<MowerCommand> commands = safeResolveCommands(parsed, lawn);
            executeWithPolicy(parsed.getMower(), commands, lawn, allMowers);
            outputLines.add(safeFormat(parsed.getMower()));
        }

        return outputLines;
    }

    private List<MowerCommand> safeResolveCommands(ParsedMowerInstructions parsed, Lawn lawn) {
        try {
            DecisionStrategy strategy = pluginRegistry.decisionStrategy();
            return strategy.decide(parsed, lawn);
        } catch (RuntimeException ex) {
            return parsed.getCommands();
        }
    }

    private void executeWithPolicy(Mower mower, List<MowerCommand> commands, Lawn lawn, List<Mower> allMowers) {
        for (MowerCommand command : commands) {
            if (command == InstructionCommand.F) {
                Position nextPosition = mower.getDirection().moveForward(mower.getPosition());
                if (safeCanMove(mower, nextPosition, lawn, allMowers)) {
                    mower.moveForward(lawn);
                }
            } else {
                command.apply(mower, lawn);
            }
        }
    }

    private boolean safeCanMove(Mower mower, Position nextPosition, Lawn lawn, List<Mower> allMowers) {
        try {
            CollisionPolicy policy = pluginRegistry.collisionPolicy();
            return policy.canMove(mower, nextPosition, lawn, allMowers);
        } catch (RuntimeException ex) {
            return lawn.isInside(nextPosition);
        }
    }

    private String safeFormat(Mower mower) {
        try {
            OutputFormatter formatter = pluginRegistry.outputFormatter();
            return formatter.format(mower);
        } catch (RuntimeException ex) {
            return mower.toString();
        }
    }
}