package org.lawnpilot.runtime.autonomous;

import org.lawnpilot.model.Direction;
import org.lawnpilot.model.Position;

import java.util.Random;

public final class AutonomousDecisionContext {

    private final long seed;
    private final long logicalTick;
    private final int stepIndex;
    private final int mowerIndex;
    private final Position currentPosition;
    private final Direction currentDirection;
    private final AutonomousSensorSnapshot sensorSnapshot;
    private final Random random;

    public AutonomousDecisionContext(long seed, long logicalTick, int stepIndex, int mowerIndex,
            Position currentPosition, Direction currentDirection,
            AutonomousSensorSnapshot sensorSnapshot, Random random) {
        this.seed = seed;
        this.logicalTick = logicalTick;
        this.stepIndex = stepIndex;
        this.mowerIndex = mowerIndex;
        this.currentPosition = currentPosition;
        this.currentDirection = currentDirection;
        this.sensorSnapshot = sensorSnapshot;
        this.random = random;
    }

    public long getSeed() {
        return seed;
    }

    public long getLogicalTick() {
        return logicalTick;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public int getMowerIndex() {
        return mowerIndex;
    }

    public Position getCurrentPosition() {
        return currentPosition;
    }

    public Direction getCurrentDirection() {
        return currentDirection;
    }

    public AutonomousSensorSnapshot getSensorSnapshot() {
        return sensorSnapshot;
    }

    public Random getRandom() {
        return random;
    }
}