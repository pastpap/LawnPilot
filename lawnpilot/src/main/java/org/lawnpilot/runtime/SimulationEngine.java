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
import org.lawnpilot.runtime.autonomous.AutonomousCommandStrategy;
import org.lawnpilot.runtime.autonomous.AutonomousDecisionContext;
import org.lawnpilot.runtime.autonomous.AutonomousRunConfig;
import org.lawnpilot.runtime.autonomous.AutonomousSensorSnapshot;
import org.lawnpilot.runtime.autonomous.ConflictHandlingPolicy;
import org.lawnpilot.runtime.autonomous.SeededAutonomousCommandStrategy;
import org.lawnpilot.runtime.events.ExecutionHaltReason;
import org.lawnpilot.runtime.events.ExecutionTrace;
import org.lawnpilot.runtime.events.MowerStateSnapshot;
import org.lawnpilot.runtime.events.SimulationResult;
import org.lawnpilot.runtime.events.StepEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Clock;

public final class SimulationEngine {

    private final PluginRegistry pluginRegistry;
    private final Clock clock;

    public SimulationEngine(PluginRegistry pluginRegistry) {
        this(pluginRegistry, Clock.systemUTC());
    }

    SimulationEngine(PluginRegistry pluginRegistry, Clock clock) {
        this.pluginRegistry = pluginRegistry;
        this.clock = clock;
    }

    public List<String> run(List<ParsedMowerInstructions> mowerInstructions, Lawn lawn) {
        return runWithTrace(mowerInstructions, lawn).getOutputLines();
    }

    public SimulationResult runWithTrace(List<ParsedMowerInstructions> mowerInstructions, Lawn lawn) {
        List<String> outputLines = new ArrayList<>();
        List<StepEvent> stepEvents = new ArrayList<>();
        List<Mower> allMowers = mowerInstructions.stream().map(ParsedMowerInstructions::getMower).toList();
        List<MowerStateSnapshot> initialStates = snapshots(allMowers);
        int stepIndex = 0;
        long logicalTick = 0L;

        for (int mowerIndex = 0; mowerIndex < mowerInstructions.size(); mowerIndex++) {
            ParsedMowerInstructions parsed = mowerInstructions.get(mowerIndex);
            List<MowerCommand> commands = safeResolveCommands(parsed, lawn);
            for (MowerCommand command : commands) {
                MowerStateSnapshot preState = MowerStateSnapshot.fromMower(parsed.getMower());
                executeCommandWithPolicy(parsed.getMower(), command, lawn, allMowers);
                MowerStateSnapshot postState = MowerStateSnapshot.fromMower(parsed.getMower());
                stepEvents.add(new StepEvent(
                        stepIndex,
                        logicalTick,
                        clock.millis(),
                        mowerIndex,
                        commandLabel(command),
                        ExecutionMode.INSTRUCTION,
                        preState,
                        postState));
                stepIndex++;
                logicalTick++;
            }
            outputLines.add(safeFormat(parsed.getMower()));
        }

        ExecutionTrace trace = new ExecutionTrace(
                ExecutionMode.INSTRUCTION,
                null,
                stepEvents.size(),
                0L,
                initialStates,
                snapshots(allMowers),
                stepEvents);

        return new SimulationResult(outputLines, trace, ExecutionHaltReason.COMPLETED);
    }

    public SimulationResult runAutonomous(List<Mower> mowers, Lawn lawn, AutonomousRunConfig config) {
        return runAutonomous(mowers, lawn, config, new SeededAutonomousCommandStrategy());
    }

    public SimulationResult runAutonomous(List<Mower> mowers, Lawn lawn,
            AutonomousRunConfig config, AutonomousCommandStrategy strategy) {
        List<StepEvent> stepEvents = new ArrayList<>();
        List<MowerStateSnapshot> initialStates = snapshots(mowers);
        Random random = new Random(config.getSeed());
        long startMillis = clock.millis();

        int stepIndex = 0;
        long logicalTick = 0L;
        ExecutionHaltReason haltReason = ExecutionHaltReason.COMPLETED;

        while (stepIndex < config.getMaxSteps()) {
            if (config.getTimeoutMillis() > 0 && clock.millis() - startMillis >= config.getTimeoutMillis()) {
                haltReason = ExecutionHaltReason.TIMEOUT_REACHED;
                break;
            }

            List<MowerCommand> commands = new ArrayList<>();
            List<Position> intendedPositions = new ArrayList<>();
            for (int mowerIndex = 0; mowerIndex < mowers.size(); mowerIndex++) {
                Mower mower = mowers.get(mowerIndex);
                Position nextForward = mower.getDirection().moveForward(mower.getPosition());
                AutonomousSensorSnapshot sensors = buildSensors(mower, nextForward, lawn, mowers);

                AutonomousDecisionContext context = new AutonomousDecisionContext(
                        config.getSeed(),
                        logicalTick,
                        stepIndex,
                        mowerIndex,
                        mower.getPosition(),
                        mower.getDirection(),
                        sensors,
                        random);
                MowerCommand command = strategy.decide(context);
                commands.add(command);
                intendedPositions.add(command == InstructionCommand.F ? nextForward : mower.getPosition());
            }

            Set<Integer> blockedForConflict = resolveConflicts(commands, intendedPositions,
                    config.getConflictHandlingPolicy());
            List<Mower> allMowers = List.copyOf(mowers);
            for (int mowerIndex = 0; mowerIndex < mowers.size() && stepIndex < config.getMaxSteps(); mowerIndex++) {
                Mower mower = mowers.get(mowerIndex);
                MowerCommand command = commands.get(mowerIndex);
                MowerStateSnapshot preState = MowerStateSnapshot.fromMower(mower);

                if (command != null) {
                    if (command == InstructionCommand.F) {
                        Position nextPosition = mower.getDirection().moveForward(mower.getPosition());
                        boolean canMove = !blockedForConflict.contains(mowerIndex)
                                && safeCanMove(mower, nextPosition, lawn, allMowers);
                        if (canMove) {
                            mower.moveForward(lawn);
                        }
                    } else {
                        command.apply(mower, lawn);
                    }
                }

                MowerStateSnapshot postState = MowerStateSnapshot.fromMower(mower);
                stepEvents.add(new StepEvent(
                        stepIndex,
                        logicalTick,
                        clock.millis(),
                        mowerIndex,
                        commandLabel(command),
                        ExecutionMode.AUTONOMOUS,
                        preState,
                        postState));

                stepIndex++;
                logicalTick++;
            }
        }

        if (haltReason == ExecutionHaltReason.COMPLETED && stepIndex >= config.getMaxSteps()) {
            haltReason = ExecutionHaltReason.MAX_STEPS_REACHED;
        }

        List<String> outputLines = mowers.stream().map(this::safeFormat).collect(Collectors.toList());
        ExecutionTrace trace = new ExecutionTrace(
                ExecutionMode.AUTONOMOUS,
                config.getSeed(),
                config.getMaxSteps(),
                config.getTimeoutMillis(),
                initialStates,
                snapshots(mowers),
                stepEvents);

        return new SimulationResult(outputLines, trace, haltReason);
    }

    private List<MowerCommand> safeResolveCommands(ParsedMowerInstructions parsed, Lawn lawn) {
        try {
            DecisionStrategy strategy = pluginRegistry.decisionStrategy();
            return strategy.decide(parsed, lawn);
        } catch (RuntimeException ex) {
            return parsed.getCommands();
        }
    }

    private void executeCommandWithPolicy(Mower mower, MowerCommand command, Lawn lawn, List<Mower> allMowers) {
        if (command == InstructionCommand.F) {
            Position nextPosition = mower.getDirection().moveForward(mower.getPosition());
            if (safeCanMove(mower, nextPosition, lawn, allMowers)) {
                mower.moveForward(lawn);
            }
        } else {
            command.apply(mower, lawn);
        }
    }

    private AutonomousSensorSnapshot buildSensors(Mower mower, Position nextForward, Lawn lawn, List<Mower> mowers) {
        boolean boundaryAhead = !lawn.isInside(nextForward);
        boolean mowerAhead = mowers.stream()
                .filter(other -> other != mower)
                .anyMatch(other -> other.getX() == nextForward.getX() && other.getY() == nextForward.getY());

        int distanceToBoundary = 0;
        Position cursor = mower.getPosition();
        while (true) {
            Position next = mower.getDirection().moveForward(cursor);
            if (!lawn.isInside(next)) {
                break;
            }
            distanceToBoundary++;
            cursor = next;
        }

        return new AutonomousSensorSnapshot(boundaryAhead, mowerAhead, distanceToBoundary);
    }

    private Set<Integer> resolveConflicts(List<MowerCommand> commands, List<Position> intendedPositions,
            ConflictHandlingPolicy conflictPolicy) {
        Set<Integer> blocked = new HashSet<>();
        Map<String, List<Integer>> byTarget = new HashMap<>();

        for (int index = 0; index < commands.size(); index++) {
            if (commands.get(index) == InstructionCommand.F) {
                Position target = intendedPositions.get(index);
                String key = target.getX() + ":" + target.getY();
                byTarget.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
            }
        }

        for (List<Integer> contenders : byTarget.values()) {
            if (contenders.size() <= 1) {
                continue;
            }
            if (conflictPolicy == ConflictHandlingPolicy.BLOCK_ALL) {
                blocked.addAll(contenders);
            } else {
                int winner = contenders.stream().min(Integer::compareTo).orElse(contenders.get(0));
                for (Integer contender : contenders) {
                    if (contender != winner) {
                        blocked.add(contender);
                    }
                }
            }
        }

        return blocked;
    }

    private List<MowerStateSnapshot> snapshots(List<Mower> mowers) {
        return mowers.stream().map(MowerStateSnapshot::fromMower).collect(Collectors.toList());
    }

    private String commandLabel(MowerCommand command) {
        if (command == null) {
            return "NOOP";
        }
        if (command instanceof Enum<?>) {
            return ((Enum<?>) command).name();
        }
        return command.getClass().getSimpleName();
    }

    private boolean safeCanMove(Mower mower, Position nextPosition, Lawn lawn, List<Mower> allMowers) {
        if (isOccupiedByOtherMower(mower, nextPosition, allMowers)) {
            return false;
        }
        try {
            CollisionPolicy policy = pluginRegistry.collisionPolicy();
            return policy.canMove(mower, nextPosition, lawn, allMowers);
        } catch (RuntimeException ex) {
            return lawn.isInside(nextPosition);
        }
    }

    private boolean isOccupiedByOtherMower(Mower mower, Position nextPosition, List<Mower> allMowers) {
        return allMowers.stream()
                .filter(other -> other != mower)
                .anyMatch(other -> other.getX() == nextPosition.getX() && other.getY() == nextPosition.getY());
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