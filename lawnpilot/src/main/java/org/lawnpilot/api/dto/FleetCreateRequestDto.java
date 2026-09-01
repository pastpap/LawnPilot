package org.lawnpilot.api.dto;

public record FleetCreateRequestDto(
        String fleetId,
        String displayName,
        String areaId,
        String areaName,
        Double areaCenterLat,
        Double areaCenterLng,
        Double areaRadiusMeters) {
}
