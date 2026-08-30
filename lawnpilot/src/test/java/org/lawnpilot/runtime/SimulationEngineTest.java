package org.lawnpilot.runtime;

import org.junit.jupiter.api.Test;
import org.lawnpilot.command.InstructionCommand;
import org.lawnpilot.command.MowerCommand;
import org.lawnpilot.model.Direction;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;
import org.lawnpilot.model.ParsedMowerInstructions;
import org.lawnpilot.parser.InputParser;
import org.lawnpilot.plugin.PluginMetadata;
import org.lawnpilot.plugin.PluginType;
import org.lawnpilot.plugin.collision.CollisionPolicy;
import org.lawnpilot.plugin.decision.DecisionStrategy;
import org.lawnpilot.plugin.output.OutputFormatter;
import org.lawnpilot.plugin.registry.PluginRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationEngineTest {

    @Test
    void defaultPluginsPreserveReferenceBehavior() {
        List<String> lines = List.of(
                "5 5",
                "1 2 N",
                "LFLFLFLFF",
                "3 3 E",
                "FFRFFRFRRF");

        InputParser parser = new InputParser();
        Lawn lawn = parser.parseLawn(lines);
        List<ParsedMowerInstructions> parsed = parser.parseMowers(lines.subList(1, lines.size()), lawn);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        List<String> output = engine.run(parsed, lawn);

        assertEquals(List.of("1 3 N", "5 1 E"), output);
    }

    @Test
    void decisionStrategyFailureFallsBackToInstructionCommands() {
        List<String> lines = List.of(
                "5 5",
                "1 2 N",
                "LFLFLFLFF");

        InputParser parser = new InputParser();
        Lawn lawn = parser.parseLawn(lines);
        List<ParsedMowerInstructions> parsed = parser.parseMowers(lines.subList(1, lines.size()), lawn);

        PluginRegistry registry = PluginRegistry.withDefaults();
        registry.registerDecisionStrategy("failing", new FailingDecisionStrategy());
        registry.enable(PluginType.DECISION_STRATEGY, "failing");

        SimulationEngine engine = new SimulationEngine(registry);
        List<String> output = engine.run(parsed, lawn);

        assertEquals(List.of("1 3 N"), output);
    }

    @Test
    void collisionPolicyFailureFallsBackToBoundaryRule() {
        PluginRegistry registry = PluginRegistry.withDefaults();
        registry.registerCollisionPolicy("failing", new FailingCollisionPolicy());
        registry.enable(PluginType.COLLISION_POLICY, "failing");

        SimulationEngine engine = new SimulationEngine(registry);
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(0, 0, Direction.S);
        ParsedMowerInstructions parsed = new ParsedMowerInstructions(mower, "F", List.of(InstructionCommand.F));

        List<String> output = engine.run(List.of(parsed), lawn);

        assertEquals(List.of("0 0 S"), output);
    }

    @Test
    void outputFormatterFailureFallsBackToTextOutput() {
        PluginRegistry registry = PluginRegistry.withDefaults();
        registry.registerOutputFormatter("failing", new FailingOutputFormatter());
        registry.enable(PluginType.OUTPUT_FORMATTER, "failing");

        SimulationEngine engine = new SimulationEngine(registry);
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(1, 2, Direction.N);
        ParsedMowerInstructions parsed = new ParsedMowerInstructions(mower, "", List.of());

        List<String> output = engine.run(List.of(parsed), lawn);

        assertEquals(List.of("1 2 N"), output);
    }

    private static final class FailingDecisionStrategy implements DecisionStrategy {
        @Override
        public List<MowerCommand> decide(ParsedMowerInstructions mowerInstructions, Lawn lawn) {
            throw new IllegalStateException("boom");
        }

        @Override
        public PluginMetadata metadata() {
            return new PluginMetadata("failing-decision-strategy", "1.0.0", "1.0");
        }
    }

    private static final class FailingCollisionPolicy implements CollisionPolicy {
        @Override
        public boolean canMove(Mower mower, org.lawnpilot.model.Position nextPosition, Lawn lawn,
                List<Mower> allMowers) {
            throw new IllegalStateException("boom");
        }

        @Override
        public PluginMetadata metadata() {
            return new PluginMetadata("failing-collision-policy", "1.0.0", "1.0");
        }
    }

    private static final class FailingOutputFormatter implements OutputFormatter {
        @Override
        public String format(Mower mower) {
            throw new IllegalStateException("boom");
        }

        @Override
        public PluginMetadata metadata() {
            return new PluginMetadata("failing-output-formatter", "1.0.0", "1.0");
        }
    }
}