package org.lawnpilot.runtime.events;

import org.lawnpilot.runtime.ExecutionMode;

import java.util.List;

public final class ExecutionTrace {

    private final ExecutionMode mode;
    private final Long seed;
    private final int maxSteps;
    private final long timeoutMillis;
    private final List<MowerStateSnapshot> initialStates;
    private final List<MowerStateSnapshot> finalStates;
    private final List<StepEvent> stepEvents;

    public ExecutionTrace(ExecutionMode mode, Long seed, int maxSteps, long timeoutMillis,
            List<MowerStateSnapshot> initialStates,
            List<MowerStateSnapshot> finalStates,
            List<StepEvent> stepEvents) {
        this.mode = mode;
        this.seed = seed;
        this.maxSteps = maxSteps;
        this.timeoutMillis = timeoutMillis;
        this.initialStates = List.copyOf(initialStates);
        this.finalStates = List.copyOf(finalStates);
        this.stepEvents = List.copyOf(stepEvents);
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public Long getSeed() {
        return seed;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public List<MowerStateSnapshot> getInitialStates() {
        return initialStates;
    }

    public List<MowerStateSnapshot> getFinalStates() {
        return finalStates;
    }

    public List<StepEvent> getStepEvents() {
        return stepEvents;
    }
}