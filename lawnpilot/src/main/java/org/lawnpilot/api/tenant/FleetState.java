package org.lawnpilot.api.tenant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class FleetState {

    private final String fleetId;
    private volatile String displayName;
    private volatile String areaId;
    private volatile String areaName;
    private volatile double areaCenterLat;
    private volatile double areaCenterLng;
    private volatile double areaRadiusMeters;
    private final Map<String, MowerRegistration> mowers = new ConcurrentHashMap<>();
    private final Map<String, MowerTelemetryState> mowerTelemetry = new ConcurrentHashMap<>();
    private final Map<String, Integer> mowerTelemetryTicks = new ConcurrentHashMap<>();
    // Phase 7: Command execution history per mower
    private final Map<String, List<RemoteCommandExecution>> mowerCommandHistory = new ConcurrentHashMap<>();
    // Phase 7: Telemetry event log per mower
    private final Map<String, List<TelemetryEvent>> mowerTelemetryLog = new ConcurrentHashMap<>();

    FleetState(
            String fleetId,
            String displayName,
            String areaId,
            String areaName,
            double areaCenterLat,
            double areaCenterLng,
            double areaRadiusMeters) {
        this.fleetId = fleetId;
        this.displayName = displayName;
        this.areaId = areaId;
        this.areaName = areaName;
        this.areaCenterLat = areaCenterLat;
        this.areaCenterLng = areaCenterLng;
        this.areaRadiusMeters = areaRadiusMeters;
    }

    String fleetId() {
        return fleetId;
    }

    String displayName() {
        return displayName;
    }

    String areaId() {
        return areaId;
    }

    String areaName() {
        return areaName;
    }

    double areaCenterLat() {
        return areaCenterLat;
    }

    double areaCenterLng() {
        return areaCenterLng;
    }

    double areaRadiusMeters() {
        return areaRadiusMeters;
    }

    synchronized void updateMetadata(
            String displayName,
            String areaId,
            String areaName,
            double areaCenterLat,
            double areaCenterLng,
            double areaRadiusMeters) {
        this.displayName = displayName;
        this.areaId = areaId;
        this.areaName = areaName;
        this.areaCenterLat = areaCenterLat;
        this.areaCenterLng = areaCenterLng;
        this.areaRadiusMeters = areaRadiusMeters;
    }

    Map<String, MowerRegistration> mowers() {
        return mowers;
    }

    Map<String, MowerTelemetryState> mowerTelemetry() {
        return mowerTelemetry;
    }

    int nextTelemetryTick(String mowerId) {
        return mowerTelemetryTicks.compute(mowerId, (key, current) -> current == null ? 1 : current + 1) - 1;
    }

    void removeTelemetryTick(String mowerId) {
        mowerTelemetryTicks.remove(mowerId);
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
