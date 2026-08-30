package org.lawnpilot.runtime.events;

import org.lawnpilot.runtime.ExecutionMode;

public final class StepEvent {

    private final int stepIndex;
    private final long logicalTick;
    private final long timestampMillis;
    private final int mowerIndex;
    private final String command;
    private final ExecutionMode mode;
    private final MowerStateSnapshot preState;
    private final MowerStateSnapshot postState;

    public StepEvent(int stepIndex, long logicalTick, long timestampMillis, int mowerIndex,
            String command, ExecutionMode mode,
            MowerStateSnapshot preState, MowerStateSnapshot postState) {
        this.stepIndex = stepIndex;
        this.logicalTick = logicalTick;
        this.timestampMillis = timestampMillis;
        this.mowerIndex = mowerIndex;
        this.command = command;
        this.mode = mode;
        this.preState = preState;
        this.postState = postState;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public long getLogicalTick() {
        return logicalTick;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public int getMowerIndex() {
        return mowerIndex;
    }

    public String getCommand() {
        return command;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public MowerStateSnapshot getPreState() {
        return preState;
    }

    public MowerStateSnapshot getPostState() {
        return postState;
    }
}