package org.lawnpilot.runtime.events;

import java.util.ArrayList;
import java.util.List;

public final class TraceReplayService {

    public ReplayVerificationResult verify(ExecutionTrace trace) {
        List<MowerStateSnapshot> currentStates = new ArrayList<>(trace.getInitialStates());

        for (StepEvent event : trace.getStepEvents()) {
            if (event.getMowerIndex() < 0 || event.getMowerIndex() >= currentStates.size()) {
                return new ReplayVerificationResult(false,
                        "Invalid mower index in event: " + event.getMowerIndex());
            }

            MowerStateSnapshot current = currentStates.get(event.getMowerIndex());
            if (!current.equals(event.getPreState())) {
                return new ReplayVerificationResult(false,
                        "Replay divergence at step " + event.getStepIndex()
                                + " for mower " + event.getMowerIndex());
            }

            currentStates.set(event.getMowerIndex(), event.getPostState());
        }

        if (!currentStates.equals(trace.getFinalStates())) {
            return new ReplayVerificationResult(false, "Final state mismatch after replay.");
        }

        return new ReplayVerificationResult(true, "Replay verification successful.");
    }
}