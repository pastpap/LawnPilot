package org.lawnpilot.api.dto;

public record FleetDto(
        String fleetId,
        String displayName,
        int mowerCount,
        String areaId,
        String areaName,
        double areaCenterLat,
        double areaCenterLng,
        double areaRadiusMeters) {

    public FleetDto(String fleetId, String displayName, int mowerCount) {
        this(fleetId, displayName, mowerCount, null, null, 0d, 0d, 0d);
    }
}
