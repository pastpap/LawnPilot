package org.lawnpilot.runtime.events;

import java.util.List;

public final class SimulationResult {

    private final List<String> outputLines;
    private final ExecutionTrace trace;
    private final ExecutionHaltReason haltReason;

    public SimulationResult(List<String> outputLines, ExecutionTrace trace, ExecutionHaltReason haltReason) {
        this.outputLines = List.copyOf(outputLines);
        this.trace = trace;
        this.haltReason = haltReason;
    }

    public List<String> getOutputLines() {
        return outputLines;
    }

    public ExecutionTrace getTrace() {
        return trace;
    }

    public ExecutionHaltReason getHaltReason() {
        return haltReason;
    }
}