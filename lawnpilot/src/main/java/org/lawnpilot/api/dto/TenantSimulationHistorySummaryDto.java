package org.lawnpilot.api.dto;

public record TenantSimulationHistorySummaryDto(String tenantId, long simulationRunCount, String lastSimulationRunAt) {
}
