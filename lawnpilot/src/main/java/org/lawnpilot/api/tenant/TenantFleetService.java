package org.lawnpilot.api.tenant;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.lawnpilot.api.dto.FleetDto;
import org.lawnpilot.api.dto.MowerDto;
import org.lawnpilot.api.dto.MowerTelemetryDto;
import org.lawnpilot.api.dto.TenantSimulationHistorySummaryDto;
import org.lawnpilot.exceptions.ConflictException;
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
}
