package org.lawnpilot.api.dto;

public record MowerTelemetryDto(
        String mowerId,
        String fleetId,
        String model,
        String status,
        int batteryPercent,
        int runtimeMinutesToday,
        double latitude,
        double longitude,
        String areaId,
        String areaName,
        double targetCoverageHa,
        double coverageTodayHa) {
}
