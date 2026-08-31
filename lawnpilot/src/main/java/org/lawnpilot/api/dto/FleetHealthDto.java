package org.lawnpilot.api.dto;

/**
 * Fleet health aggregation snapshot.
 *
 * <p>
 * Calculated from current mower telemetry state:
 * - operationalMowerCount: mowers with status in [cutting, idle]
 * - totalMowerCount: all registered mowers in fleet
 * - averageBatteryPercent: mean of all mower battery percentages
 * - fleetCoverageProgressHa: sum of all mower coverageTodayHa
 * - fleetTargetCoverageHa: sum of all mower targetCoverageHa
 * - healthStatus: calculated as:
 * - HEALTHY if operationalRate >= 80% AND averageBattery >= 50%
 * - DEGRADED if operationalRate >= 50%
 * - CRITICAL otherwise
 * - calculatedAt: ISO-8601 timestamp of calculation
 */
public record FleetHealthDto(
        int operationalMowerCount,
        int totalMowerCount,
        int averageBatteryPercent,
        double fleetCoverageProgressHa,
        double fleetTargetCoverageHa,
        String healthStatus, // HEALTHY | DEGRADED | CRITICAL
        String calculatedAt) {
}
