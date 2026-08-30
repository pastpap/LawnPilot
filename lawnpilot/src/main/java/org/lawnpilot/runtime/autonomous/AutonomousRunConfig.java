package org.lawnpilot.runtime.autonomous;

public final class AutonomousRunConfig {

    private final long seed;
    private final int maxSteps;
    private final long timeoutMillis;
    private final ConflictHandlingPolicy conflictHandlingPolicy;

    public AutonomousRunConfig(long seed, int maxSteps, long timeoutMillis,
            ConflictHandlingPolicy conflictHandlingPolicy) {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be greater than 0.");
        }
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeoutMillis must be greater than or equal to 0.");
        }
        this.seed = seed;
        this.maxSteps = maxSteps;
        this.timeoutMillis = timeoutMillis;
        this.conflictHandlingPolicy = conflictHandlingPolicy == null
                ? ConflictHandlingPolicy.BLOCK_ALL
                : conflictHandlingPolicy;
    }

    public long getSeed() {
        return seed;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public ConflictHandlingPolicy getConflictHandlingPolicy() {
        return conflictHandlingPolicy;
    }
}