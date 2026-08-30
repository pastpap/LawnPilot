package org.lawnpilot.runtime;

import org.junit.jupiter.api.Test;
import org.lawnpilot.command.InstructionCommand;
import org.lawnpilot.command.MowerCommand;
import org.lawnpilot.model.Direction;
import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;
import org.lawnpilot.model.ParsedMowerInstructions;
import org.lawnpilot.model.Position;
import org.lawnpilot.model.geometry.MaskedLawnGeometry;
import org.lawnpilot.parser.InputParser;
import org.lawnpilot.plugin.PluginMetadata;
import org.lawnpilot.plugin.PluginType;
import org.lawnpilot.plugin.collision.CollisionPolicy;
import org.lawnpilot.plugin.decision.DecisionStrategy;
import org.lawnpilot.plugin.output.OutputFormatter;
import org.lawnpilot.plugin.registry.PluginRegistry;
import org.lawnpilot.runtime.autonomous.AutonomousCommandStrategy;
import org.lawnpilot.runtime.autonomous.AutonomousDecisionContext;
import org.lawnpilot.runtime.autonomous.AutonomousRunConfig;
import org.lawnpilot.runtime.autonomous.ConflictHandlingPolicy;
import org.lawnpilot.runtime.events.ExecutionHaltReason;
import org.lawnpilot.runtime.events.ReplayVerificationResult;
import org.lawnpilot.runtime.events.SimulationResult;
import org.lawnpilot.runtime.events.StepEvent;
import org.lawnpilot.runtime.events.TraceReplayService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void instructionTraceRemainsReplayableAndKeepsParity() {
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
        SimulationResult result = engine.runWithTrace(parsed, lawn);

        assertEquals(List.of("1 3 N", "5 1 E"), result.getOutputLines());
        assertEquals(ExecutionHaltReason.COMPLETED, result.getHaltReason());

        TraceReplayService replayService = new TraceReplayService();
        ReplayVerificationResult replay = replayService.verify(result.getTrace());
        assertTrue(replay.isSuccess(), replay.getMessage());
    }

    @Test
    void autonomousRunsAreDeterministicForSameSeedAndConfig() {
        Lawn lawnOne = new Lawn(5, 5);
        Lawn lawnTwo = new Lawn(5, 5);
        List<Mower> firstRunMowers = List.of(new Mower(1, 1, Direction.N), new Mower(3, 3, Direction.E));
        List<Mower> secondRunMowers = List.of(new Mower(1, 1, Direction.N), new Mower(3, 3, Direction.E));

        AutonomousRunConfig config = new AutonomousRunConfig(777L, 24, 0L, ConflictHandlingPolicy.BLOCK_ALL);

        SimulationEngine firstEngine = new SimulationEngine(PluginRegistry.withDefaults());
        SimulationEngine secondEngine = new SimulationEngine(PluginRegistry.withDefaults());

        SimulationResult first = firstEngine.runAutonomous(firstRunMowers, lawnOne, config);
        SimulationResult second = secondEngine.runAutonomous(secondRunMowers, lawnTwo, config);

        assertEquals(first.getOutputLines(), second.getOutputLines());
        assertEquals(first.getTrace().getStepEvents().size(), second.getTrace().getStepEvents().size());
        assertEquals(first.getTrace().getFinalStates(), second.getTrace().getFinalStates());
    }

    @Test
    void autonomousTraceIsReplayable() {
        Lawn lawn = new Lawn(5, 5);
        List<Mower> mowers = List.of(new Mower(0, 0, Direction.N), new Mower(2, 2, Direction.W));
        AutonomousRunConfig config = new AutonomousRunConfig(123L, 16, 0L, ConflictHandlingPolicy.BLOCK_ALL);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        SimulationResult result = engine.runAutonomous(mowers, lawn, config);

        TraceReplayService replayService = new TraceReplayService();
        ReplayVerificationResult replay = replayService.verify(result.getTrace());
        assertTrue(replay.isSuccess(), replay.getMessage());
    }

    @Test
    void autonomousRunStopsAtMaxStepsBound() {
        Lawn lawn = new Lawn(5, 5);
        List<Mower> mowers = List.of(new Mower(1, 1, Direction.N), new Mower(2, 2, Direction.S));
        AutonomousRunConfig config = new AutonomousRunConfig(12L, 5, 0L, ConflictHandlingPolicy.BLOCK_ALL);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        SimulationResult result = engine.runAutonomous(mowers, lawn, config);

        assertEquals(ExecutionHaltReason.MAX_STEPS_REACHED, result.getHaltReason());
        assertEquals(5, result.getTrace().getStepEvents().size());
    }

    @Test
    void autonomousRunStopsWhenTimeoutIsReached() {
        Lawn lawn = new Lawn(5, 5);
        List<Mower> mowers = List.of(new Mower(1, 1, Direction.N));
        AutonomousRunConfig config = new AutonomousRunConfig(42L, 20, 3L, ConflictHandlingPolicy.BLOCK_ALL);

        AdvancingClock clock = new AdvancingClock(Instant.parse("2026-08-30T00:00:00Z"), 2L);
        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults(), clock);
        SimulationResult result = engine.runAutonomous(mowers, lawn, config);

        assertEquals(ExecutionHaltReason.TIMEOUT_REACHED, result.getHaltReason());
        assertTrue(result.getTrace().getStepEvents().size() < 20);
    }

    @Test
    void conflictHandlingBlocksAllOnSameTargetWhenConfigured() {
        Lawn lawn = new Lawn(5, 5);
        List<Mower> mowers = List.of(new Mower(1, 1, Direction.E), new Mower(3, 1, Direction.W));
        AutonomousRunConfig config = new AutonomousRunConfig(5L, 2, 0L, ConflictHandlingPolicy.BLOCK_ALL);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        SimulationResult result = engine.runAutonomous(mowers, lawn, config, new ForwardOnlyAutonomousStrategy());

        List<StepEvent> events = result.getTrace().getStepEvents();
        assertEquals(2, events.size());
        assertEquals(1, mowers.get(0).getX());
        assertEquals(1, mowers.get(0).getY());
        assertEquals(3, mowers.get(1).getX());
        assertEquals(1, mowers.get(1).getY());
        assertEquals("F", events.get(0).getCommand());
        assertEquals("F", events.get(1).getCommand());
    }

    @Test
    void conflictHandlingAllowsFirstMoverWhenConfigured() {
        Lawn lawn = new Lawn(5, 5);
        List<Mower> mowers = List.of(new Mower(1, 1, Direction.E), new Mower(3, 1, Direction.W));
        AutonomousRunConfig config = new AutonomousRunConfig(5L, 2, 0L, ConflictHandlingPolicy.FIRST_MOVER_WINS);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        SimulationResult result = engine.runAutonomous(mowers, lawn, config, new ForwardOnlyAutonomousStrategy());

        assertEquals(ExecutionHaltReason.MAX_STEPS_REACHED, result.getHaltReason());
        assertEquals(2, mowers.get(0).getX());
        assertEquals(1, mowers.get(0).getY());
        assertEquals(3, mowers.get(1).getX());
        assertEquals(1, mowers.get(1).getY());
    }

    @Test
    void instructionModeBlocksMoveIntoCellOccupiedByAnotherMower() {
        Lawn lawn = new Lawn(5, 5);
        ParsedMowerInstructions first = new ParsedMowerInstructions(
                new Mower(0, 0, Direction.N),
                "F",
                List.of(InstructionCommand.F));
        ParsedMowerInstructions second = new ParsedMowerInstructions(
                new Mower(0, 2, Direction.S),
                "F",
                List.of(InstructionCommand.F));

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        List<String> output = engine.run(List.of(first, second), lawn);

        assertEquals(List.of("0 1 N", "0 2 S"), output);
    }

    @Test
    void autonomousSwapAttemptIsDeterministicallyBlockedForBothMowers() {
        Lawn lawn = new Lawn(5, 5);
        List<Mower> mowers = List.of(new Mower(1, 1, Direction.E), new Mower(2, 1, Direction.W));
        AutonomousRunConfig config = new AutonomousRunConfig(5L, 2, 0L, ConflictHandlingPolicy.FIRST_MOVER_WINS);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        SimulationResult result = engine.runAutonomous(mowers, lawn, config, new ForwardOnlyAutonomousStrategy());

        assertEquals(ExecutionHaltReason.MAX_STEPS_REACHED, result.getHaltReason());
        assertEquals("1 1 E", mowers.get(0).toString());
        assertEquals("2 1 W", mowers.get(1).toString());
    }

    @Test
    void maskedLawnGeometryWorksWithInstructionExecution() {
        Lawn lawn = new Lawn(new MaskedLawnGeometry(List.of(
                new Position(0, 0),
                new Position(1, 0),
                new Position(1, 1))));
        ParsedMowerInstructions parsed = new ParsedMowerInstructions(
                new Mower(0, 0, Direction.E),
                "FFLFF",
                List.of(InstructionCommand.F, InstructionCommand.F, InstructionCommand.L,
                        InstructionCommand.F, InstructionCommand.F));

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        List<String> output = engine.run(List.of(parsed), lawn);

        assertEquals(List.of("1 1 N"), output);
    }

    @Test
    void parserDefinedMaskedLawnIsUsedByRuntimeExecution() {
        List<String> lines = List.of(
                "MASK 0,0 1,0 1,1",
                "0 0 E",
                "FFLFF");

        InputParser parser = new InputParser();
        Lawn lawn = parser.parseLawn(lines);
        List<ParsedMowerInstructions> parsed = parser.parseMowers(lines.subList(1, lines.size()), lawn);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        List<String> output = engine.run(parsed, lawn);

        assertEquals(List.of("1 1 N"), output);
    }

    @Test
    void autonomousStressManyMowersPreservesOccupancyOnConflict() {
        Lawn lawn = new Lawn(39, 19);
        List<Mower> mowers = buildHeadOnConflictMowers();
        List<String> initialStates = mowers.stream().map(Mower::toString).toList();

        AutonomousRunConfig config = new AutonomousRunConfig(99L, mowers.size(), 0L, ConflictHandlingPolicy.BLOCK_ALL);
        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        SimulationResult result = engine.runAutonomous(mowers, lawn, config, new ForwardOnlyAutonomousStrategy());

        assertEquals(ExecutionHaltReason.MAX_STEPS_REACHED, result.getHaltReason());
        assertEquals(mowers.size(), result.getTrace().getStepEvents().size());
        assertEquals(initialStates, mowers.stream().map(Mower::toString).toList());

        Set<String> occupiedCells = new HashSet<>();
        for (Mower mower : mowers) {
            String key = mower.getX() + ":" + mower.getY();
            assertTrue(occupiedCells.add(key), "Duplicate occupied cell found: " + key);
            assertTrue(lawn.isInside(mower.getPosition()), "Mower outside lawn: " + mower);
        }
    }

    @Test
    void autonomousStressLongRunStopsAtConfiguredMaxStepsDeterministically() {
        Lawn lawn = new Lawn(80, 80);
        List<Mower> mowers = List.of(new Mower(40, 40, Direction.N));
        AutonomousRunConfig config = new AutonomousRunConfig(123456L, 5000, 0L, ConflictHandlingPolicy.BLOCK_ALL);

        SimulationEngine engine = new SimulationEngine(PluginRegistry.withDefaults());
        SimulationResult result = engine.runAutonomous(mowers, lawn, config);

        assertEquals(ExecutionHaltReason.MAX_STEPS_REACHED, result.getHaltReason());
        assertEquals(5000, result.getTrace().getStepEvents().size());
        assertEquals(5000, result.getTrace().getMaxSteps());
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

    private static final class ForwardOnlyAutonomousStrategy implements AutonomousCommandStrategy {
        @Override
        public MowerCommand decide(AutonomousDecisionContext context) {
            return InstructionCommand.F;
        }
    }

    private static List<Mower> buildHeadOnConflictMowers() {
        List<Mower> mowers = new java.util.ArrayList<>();
        for (int y = 0; y < 20; y++) {
            for (int block = 0; block < 10; block++) {
                int baseX = block * 4;
                mowers.add(new Mower(baseX, y, Direction.E));
                mowers.add(new Mower(baseX + 2, y, Direction.W));
            }
        }
        return mowers;
    }

    private static final class AdvancingClock extends Clock {
        private final ZoneId zoneId;
        private Instant current;
        private final long incrementMillis;

        private AdvancingClock(Instant start, long incrementMillis) {
            this.zoneId = ZoneId.of("UTC");
            this.current = start;
            this.incrementMillis = incrementMillis;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant now = current;
            current = current.plusMillis(incrementMillis);
            return now;
        }
    }
}