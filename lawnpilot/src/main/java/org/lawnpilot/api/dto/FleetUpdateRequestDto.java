package org.lawnpilot.api.dto;

public record FleetUpdateRequestDto(
        String displayName,
        String areaId,
        String areaName,
        Double areaCenterLat,
        Double areaCenterLng,
        Double areaRadiusMeters) {
}
