package org.lawnpilot.api.tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class FleetState {

    private final String fleetId;
    private final String displayName;
    private final Map<String, MowerRegistration> mowers = new ConcurrentHashMap<>();
    private final Map<String, MowerTelemetryState> mowerTelemetry = new ConcurrentHashMap<>();
    // Phase 7: Command execution history per mower
    private final Map<String, List<RemoteCommandExecution>> mowerCommandHistory = new ConcurrentHashMap<>();
    // Phase 7: Telemetry event log per mower
    private final Map<String, List<TelemetryEvent>> mowerTelemetryLog = new ConcurrentHashMap<>();

    FleetState(String fleetId, String displayName) {
        this.fleetId = fleetId;
        this.displayName = displayName;
    }

    String fleetId() {
        return fleetId;
    }

    String displayName() {
        return displayName;
    }

    Map<String, MowerRegistration> mowers() {
        return mowers;
    }

    Map<String, MowerTelemetryState> mowerTelemetry() {
        return mowerTelemetry;
    }

    // Phase 7: Command history accessor
    Map<String, List<RemoteCommandExecution>> mowerCommandHistory() {
        return mowerCommandHistory;
    }

    // Phase 7: Telemetry log accessor
    Map<String, List<TelemetryEvent>> mowerTelemetryLog() {
        return mowerTelemetryLog;
    }
}
