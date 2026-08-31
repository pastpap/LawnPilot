package org.lawnpilot.api.tenant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class FleetState {

    private final String fleetId;
    private final String displayName;
    private final Map<String, MowerRegistration> mowers = new ConcurrentHashMap<>();

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
}
