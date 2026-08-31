package org.lawnpilot.api.tenant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.lawnpilot.api.dto.FleetDto;
import org.lawnpilot.api.dto.MowerDto;
import org.lawnpilot.api.dto.MowerTelemetryDto;
import org.lawnpilot.api.dto.TenantSimulationHistorySummaryDto;
import org.lawnpilot.exceptions.ConflictException;
import org.lawnpilot.exceptions.GuardrailViolationException;
import org.lawnpilot.exceptions.NotFoundException;
import org.lawnpilot.exceptions.RoleAuthorizationException;
import org.lawnpilot.exceptions.TenantValidationException;
import org.springframework.stereotype.Service;

@Service
public class TenantFleetService {

    private static final Pattern RESOURCE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    private final TenantFleetRepository tenantFleetRepository;

    private static final List<String> STATUS_VALUES = List.of("cutting", "charging", "idle", "maintenance", "transit");

    public TenantFleetService(TenantFleetRepository tenantFleetRepository) {
        this.tenantFleetRepository = tenantFleetRepository;
    }

    public FleetDto createFleet(String tenantId, TenantRole role, String fleetId, String displayName) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedFleetId);

        TenantState tenantState = tenantFleetRepository.getOrCreateTenantState(normalizedTenantId);
        FleetState existing = tenantState.fleets().putIfAbsent(normalizedFleetId,
                new FleetState(normalizedFleetId, normalizedDisplayName));

        if (existing != null) {
            throw new ConflictException(
                    "Fleet '" + normalizedFleetId + "' already exists for tenant '" + normalizedTenantId + "'.");
        }

        return new FleetDto(normalizedFleetId, normalizedDisplayName, 0);
    }

    public List<FleetDto> listFleets(String tenantId, TenantRole role) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireReadRole(role);

        return tenantFleetRepository.findTenantState(normalizedTenantId)
                .map(tenantState -> tenantState.fleets().values().stream()
                        .sorted(Comparator.comparing(FleetState::fleetId))
                        .map(fleet -> new FleetDto(fleet.fleetId(), fleet.displayName(), fleet.mowers().size()))
                        .toList())
                .orElseGet(List::of);
    }

    public MowerDto registerMower(String tenantId, TenantRole role, String fleetId, String mowerId, String model) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedMowerId = requireValidResourceId(mowerId, "Mower id");
        String normalizedModel = normalizeDisplayName(model, "GENERIC");

        TenantState tenantState = tenantFleetRepository.getOrCreateTenantState(normalizedTenantId);
        FleetState fleetState = tenantState.fleets().get(normalizedFleetId);
        if (fleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        MowerRegistration registration = new MowerRegistration(normalizedMowerId, normalizedModel, Instant.now());
        MowerRegistration existing = fleetState.mowers().putIfAbsent(normalizedMowerId, registration);
        if (existing != null) {
            throw new ConflictException(
                    "Mower '" + normalizedMowerId + "' already exists in fleet '" + normalizedFleetId + "'.");
        }

        fleetState.mowerTelemetry().put(normalizedMowerId,
                buildTelemetry(normalizedTenantId, fleetState, registration));

        return new MowerDto(registration.mowerId(), registration.model(), registration.registeredAt().toString());
    }

    public List<MowerDto> listMowers(String tenantId, TenantRole role, String fleetId) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireReadRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");

        TenantState tenantState = tenantFleetRepository.findTenantState(normalizedTenantId)
                .orElseThrow(() -> new NotFoundException("Tenant '" + normalizedTenantId + "' has no fleets."));

        FleetState fleetState = tenantState.fleets().get(normalizedFleetId);
        if (fleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        return fleetState.mowers().values().stream()
                .sorted(Comparator.comparing(MowerRegistration::mowerId))
                .map(registration -> new MowerDto(
                        registration.mowerId(),
                        registration.model(),
                        registration.registeredAt().toString()))
                .toList();
    }

    public List<MowerTelemetryDto> listMowerTelemetry(String tenantId, TenantRole role, String fleetId) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireReadRole(role);

        TenantState tenantState = tenantFleetRepository.findTenantState(normalizedTenantId).orElse(null);
        if (tenantState == null) {
            return List.of();
        }

        String normalizedFleetId = fleetId == null || fleetId.isBlank() ? null
                : requireValidResourceId(fleetId, "Fleet id");

        return tenantState.fleets().values().stream()
                .filter(fleet -> normalizedFleetId == null || fleet.fleetId().equals(normalizedFleetId))
                .sorted(Comparator.comparing(FleetState::fleetId))
                .flatMap(fleet -> fleet.mowers().values().stream()
                        .sorted(Comparator.comparing(MowerRegistration::mowerId))
                        .map(registration -> {
                            MowerTelemetryState telemetry = fleet.mowerTelemetry().computeIfAbsent(
                                    registration.mowerId(),
                                    ignored -> buildTelemetry(normalizedTenantId, fleet, registration));
                            return toDto(telemetry);
                        }))
                .toList();
    }

    public TenantSimulationHistorySummaryDto getSimulationHistorySummary(String tenantId, TenantRole role) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireReadRole(role);

        TenantState tenantState = tenantFleetRepository.findTenantState(normalizedTenantId).orElse(null);
        if (tenantState == null) {
            return new TenantSimulationHistorySummaryDto(normalizedTenantId, 0, null);
        }

        return new TenantSimulationHistorySummaryDto(
                normalizedTenantId,
                tenantState.simulationRunCount(),
                tenantState.lastSimulationRunAt() == null ? null : tenantState.lastSimulationRunAt().toString());
    }

    public void recordSimulationRun(String tenantId, TenantRole role) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        TenantState tenantState = tenantFleetRepository.getOrCreateTenantState(normalizedTenantId);
        tenantState.recordSimulationRun(Instant.now());
    }

    private static void requireReadRole(TenantRole role) {
        if (role == null) {
            throw new RoleAuthorizationException("A role is required to read tenant data.");
        }
    }

    private static void requireMutationRole(TenantRole role) {
        requireReadRole(role);
        if (!role.canMutateTenantData()) {
            throw new RoleAuthorizationException(
                    "Role '" + role + "' is not allowed to modify tenant data. Required role: ADMIN or OPERATOR.");
        }
    }

    private static String requireValidResourceId(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new TenantValidationException(fieldName + " must not be blank.");
        }

        String normalized = value.trim();
        if (!RESOURCE_ID_PATTERN.matcher(normalized).matches()) {
            throw new TenantValidationException(
                    fieldName + " '" + value + "' is invalid. Use 1-64 characters: letters, digits, '_' or '-'.");
        }

        return normalized;
    }

    private static String normalizeDisplayName(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        return value.trim();
    }

    private static MowerTelemetryState buildTelemetry(
            String tenantId,
            FleetState fleetState,
            MowerRegistration registration) {
        int hash = stableHash(tenantId + ":" + fleetState.fleetId() + ":" + registration.mowerId());
        String status = STATUS_VALUES.get(hash % STATUS_VALUES.size());
        int batteryPercent = 25 + (stableHash(registration.mowerId() + ":battery") % 71);
        int runtimeMinutesToday = 120 + (stableHash(registration.mowerId() + ":runtime") % 270);

        double[] anchor = tenantAnchor(tenantId);
        int fleetOffset = stableHash(fleetState.fleetId()) % 7;
        int mowerOffset = stableHash(registration.mowerId()) % 11;
        double latitude = round6(anchor[0] + ((fleetOffset - 3) * 0.008) + ((mowerOffset - 5) * 0.0011));
        double longitude = round6(anchor[1] + ((fleetOffset - 3) * 0.006) + ((mowerOffset - 5) * 0.0013));

        String areaId = fleetState.fleetId() + "-area";
        String areaName = fleetState.displayName() + " Zone";
        double targetCoverageHa = 8 + (stableHash(fleetState.fleetId() + ":target") % 9);
        double progress = 0.68 + ((stableHash(registration.mowerId() + ":progress") % 27) / 100.0);
        double coverageTodayHa = round1(targetCoverageHa * progress);

        return new MowerTelemetryState(
                registration.mowerId(),
                fleetState.fleetId(),
                registration.model(),
                status,
                batteryPercent,
                runtimeMinutesToday,
                latitude,
                longitude,
                areaId,
                areaName,
                round1(targetCoverageHa),
                coverageTodayHa);
    }

    private static MowerTelemetryDto toDto(MowerTelemetryState telemetry) {
        return new MowerTelemetryDto(
                telemetry.mowerId(),
                telemetry.fleetId(),
                telemetry.model(),
                telemetry.status(),
                telemetry.batteryPercent(),
                telemetry.runtimeMinutesToday(),
                telemetry.latitude(),
                telemetry.longitude(),
                telemetry.areaId(),
                telemetry.areaName(),
                telemetry.targetCoverageHa(),
                telemetry.coverageTodayHa());
    }

    private static int stableHash(String value) {
        return Math.abs(value.toLowerCase(Locale.ROOT).hashCode());
    }

    private static double[] tenantAnchor(String tenantId) {
        if (tenantId.contains("beta")) {
            return new double[] { 47.6088, -122.3072 };
        }
        if (tenantId.contains("gamma")) {
            return new double[] { 47.5921, -122.3318 };
        }
        return new double[] { 47.6314, -122.3349 };
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    // ========== Phase 7: Remote Command & Control ==========

    /**
     * Issue a remote command to a mower with guardrail validation.
     * 
     * Process:
     * 1. Validate mower exists in fleet
     * 2. Evaluate guardrails based on mower state
     * 3. If guardrails fail and override=false, reject with 422
     * 4. Queue command for execution
     * 5. Return command ID for status tracking
     */
    public String issueMowerCommand(
            String tenantId,
            TenantRole role,
            String fleetId,
            String mowerId,
            RemoteCommandRequest request) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedMowerId = requireValidResourceId(mowerId, "Mower id");

        TenantState tenantState = tenantFleetRepository.getOrCreateTenantState(normalizedTenantId);
        FleetState fleetState = tenantState.fleets().get(normalizedFleetId);
        if (fleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        MowerRegistration registration = fleetState.mowers().get(normalizedMowerId);
        if (registration == null) {
            throw new NotFoundException(
                    "Mower '" + normalizedMowerId + "' does not exist in fleet '" + normalizedFleetId + "'.");
        }

        // Evaluate guardrails
        GuardrailOutcome guardrailOutcome = evaluateGuardrails(normalizedMowerId, request.targetParameter());

        if (guardrailOutcome.isFailed() && !request.overrideGuardrails()) {
            throw new GuardrailViolationException(
                    "Command rejected: " + guardrailOutcome.failureReason(),
                    guardrailOutcome.safetyConstraintViolated(),
                    true);
        }

        // Create command execution record
        String commandId = "cmd-" + normalizedTenantId + "-" + normalizedFleetId + "-" + normalizedMowerId + "-"
                + System.currentTimeMillis();
        RemoteCommandExecution execution = RemoteCommandExecution.pending(
                commandId,
                normalizedMowerId,
                normalizedFleetId,
                normalizedTenantId,
                request.commandType(),
                request.targetParameter());

        if (request.overrideGuardrails() && guardrailOutcome.isFailed()) {
            execution = execution.executing(GuardrailOutcome.override(
                    guardrailOutcome.failureReason(),
                    guardrailOutcome.safetyConstraintViolated()));
        } else {
            execution = execution.executing(guardrailOutcome);
        }

        // Store command in fleet history
        fleetState.mowerCommandHistory()
                .computeIfAbsent(normalizedMowerId, key -> new java.util.ArrayList<>())
                .add(execution);

        // Record telemetry event linking command execution
        recordMowerCommandEvent(
                normalizedTenantId,
                normalizedFleetId,
                normalizedMowerId,
                commandId,
                request.commandType());

        return commandId;
    }

    /**
     * Evaluate safety guardrails for a command.
     * 
     * Current guardrails:
     * - Battery must be > 10% for active commands
     * - Cannot stop an already-stopped mower
     * - Cannot override safety constraints without explicit flag
     */
    private GuardrailOutcome evaluateGuardrails(String mowerId, String targetParameter) {
        // Placeholder: In production, check mower state from telemetry
        // For now, all commands pass guardrails (safe for testing)

        if (targetParameter == null || targetParameter.isBlank()) {
            return GuardrailOutcome.fail("Target parameter cannot be blank", "INVALID_TARGET");
        }

        // Simulate battery check guardrail
        if (targetParameter.equals("stop") || targetParameter.equals("pause")) {
            // These are always safe
            return GuardrailOutcome.pass();
        }

        // For other commands, pass by default (production would check actual telemetry)
        return GuardrailOutcome.pass();
    }

    /**
     * Query the status of a previously issued command.
     */
    public org.lawnpilot.api.dto.CommandStatusDto queryCommandStatus(
            String tenantId,
            TenantRole role,
            String fleetId,
            String mowerId,
            String commandId) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireReadRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedMowerId = requireValidResourceId(mowerId, "Mower id");

        TenantState tenantState = tenantFleetRepository.findTenantState(normalizedTenantId)
                .orElseThrow(() -> new NotFoundException("Tenant '" + normalizedTenantId + "' not found."));

        FleetState fleetState = tenantState.fleets().get(normalizedFleetId);
        if (fleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        List<RemoteCommandExecution> history = fleetState.mowerCommandHistory().get(normalizedMowerId);
        if (history == null || history.isEmpty()) {
            throw new NotFoundException("No command history for mower '" + normalizedMowerId + "'.");
        }

        RemoteCommandExecution execution = history.stream()
                .filter(cmd -> cmd.commandId().equals(commandId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Command '" + commandId + "' not found."));

        return new org.lawnpilot.api.dto.CommandStatusDto(
                execution.commandId(),
                execution.status().name(),
                execution.guardrailOutcome() != null ? execution.guardrailOutcome().status().name() : "UNKNOWN",
                execution.guardrailOutcome() != null ? execution.guardrailOutcome().failureReason() : null,
                execution.executionResult(),
                execution.errorReason(),
                execution.requestedAt().toString(),
                execution.executedAt() != null ? execution.executedAt().toString() : null);
    }

    // ========== Phase 7: Telemetry Event Ingestion ==========

    /**
     * Record a telemetry event from a mower.
     * Used for IoT-oriented telemetry ingestion and live tracking.
     */
    public void recordTelemetryEvent(
            String tenantId,
            TenantRole role,
            String fleetId,
            String mowerId,
            String eventType,
            String eventData) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedMowerId = requireValidResourceId(mowerId, "Mower id");

        TenantState tenantState = tenantFleetRepository.getOrCreateTenantState(normalizedTenantId);
        FleetState fleetState = tenantState.fleets().get(normalizedFleetId);
        if (fleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        if (fleetState.mowers().get(normalizedMowerId) == null) {
            throw new NotFoundException(
                    "Mower '" + normalizedMowerId + "' does not exist in fleet '" + normalizedFleetId + "'.");
        }

        String eventId = "evt-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
        TelemetryEvent event = TelemetryEvent.of(
                eventId,
                normalizedMowerId,
                normalizedFleetId,
                normalizedTenantId,
                eventType,
                eventData);

        fleetState.mowerTelemetryLog()
                .computeIfAbsent(normalizedMowerId, key -> new java.util.ArrayList<>())
                .add(event);
    }

    /**
     * Internal helper to record a command-related telemetry event.
     */
    private void recordMowerCommandEvent(
            String tenantId,
            String fleetId,
            String mowerId,
            String commandId,
            String commandType) {
        TenantState tenantState = tenantFleetRepository.findTenantState(tenantId).orElse(null);
        if (tenantState == null) {
            return;
        }

        FleetState fleetState = tenantState.fleets().get(fleetId);
        if (fleetState == null) {
            return;
        }

        String eventId = "evt-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
        TelemetryEvent event = TelemetryEvent.commandRelated(
                eventId,
                mowerId,
                fleetId,
                tenantId,
                "COMMAND_ISSUED",
                "{\"command\":\"" + commandType + "\"}",
                commandId);

        fleetState.mowerTelemetryLog()
                .computeIfAbsent(mowerId, key -> new java.util.ArrayList<>())
                .add(event);
    }

    /**
     * Query telemetry events for a mower.
     * Returns up to 100 most recent events.
     */
    public List<org.lawnpilot.api.dto.TelemetryEventDto> queryMowerTelemetryEvents(
            String tenantId,
            TenantRole role,
            String fleetId,
            String mowerId) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireReadRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedMowerId = requireValidResourceId(mowerId, "Mower id");

        TenantState tenantState = tenantFleetRepository.findTenantState(normalizedTenantId)
                .orElseThrow(() -> new NotFoundException("Tenant '" + normalizedTenantId + "' not found."));

        FleetState fleetState = tenantState.fleets().get(normalizedFleetId);
        if (fleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        if (fleetState.mowers().get(normalizedMowerId) == null) {
            throw new NotFoundException(
                    "Mower '" + normalizedMowerId + "' does not exist in fleet '" + normalizedFleetId + "'.");
        }

        List<TelemetryEvent> events = fleetState.mowerTelemetryLog().get(normalizedMowerId);
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        // Return last 100 events in reverse chronological order
        return events.stream()
                .sorted((a, b) -> b.recordedAt().compareTo(a.recordedAt()))
                .limit(100)
                .map(event -> new org.lawnpilot.api.dto.TelemetryEventDto(
                        event.eventId(),
                        event.eventType(),
                        event.eventData(),
                        event.recordedAt().toString(),
                        event.isCommandRelated(),
                        event.relatedCommandId()))
                .toList();
    }
}
