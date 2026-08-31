package org.lawnpilot.api.tenant;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class TenantState {

    private final Map<String, FleetState> fleets = new ConcurrentHashMap<>();
    private long simulationRunCount;
    private Instant lastSimulationRunAt;

    Map<String, FleetState> fleets() {
        return fleets;
    }

    synchronized void recordSimulationRun(Instant runAt) {
        simulationRunCount += 1;
        lastSimulationRunAt = runAt;
    }

    synchronized long simulationRunCount() {
        return simulationRunCount;
    }

    synchronized Instant lastSimulationRunAt() {
        return lastSimulationRunAt;
    }
}
