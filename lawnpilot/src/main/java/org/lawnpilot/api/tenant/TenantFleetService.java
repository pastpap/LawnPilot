package org.lawnpilot.api.tenant;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.lawnpilot.api.dto.FleetDto;
import org.lawnpilot.api.dto.MowerDto;
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
}
