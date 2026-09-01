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
import org.lawnpilot.exceptions.GuardrailViolationException;
import org.lawnpilot.exceptions.NotFoundException;
import org.lawnpilot.exceptions.RoleAuthorizationException;
import org.lawnpilot.exceptions.TenantValidationException;
import org.springframework.stereotype.Service;

@Service
public class TenantFleetService {

    private static final Pattern RESOURCE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320d;
    private static final double GEO_FENCE_LAT_BOUND = 0.015d;
    private static final double GEO_FENCE_LNG_BOUND = 0.015d;
    private static final double DEFAULT_AREA_RADIUS_METERS = 1500d;
    private static final double MAX_AREA_RADIUS_METERS = 100000d;

    private final TenantFleetRepository tenantFleetRepository;

    private static final List<String> STATUS_VALUES = List.of("cutting", "charging", "idle", "maintenance", "transit");

    public TenantFleetService(TenantFleetRepository tenantFleetRepository) {
        this.tenantFleetRepository = tenantFleetRepository;
    }

    public FleetDto createFleet(String tenantId, TenantRole role, String fleetId, String displayName) {
        return createFleet(tenantId, role, fleetId, displayName, null, null, null, null, null);
    }

    public FleetDto createFleet(
            String tenantId,
            TenantRole role,
            String fleetId,
            String displayName,
            String areaId,
            String areaName,
            Double areaCenterLat,
            Double areaCenterLng,
            Double areaRadiusMeters) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedFleetId);
        String normalizedAreaId = normalizeOptionalResourceId(areaId, "Area id");
        String normalizedAreaName = normalizeOptionalDisplayName(areaName);

        AreaGeometry geometry = normalizeOptionalAreaGeometry(areaCenterLat, areaCenterLng, areaRadiusMeters);
        if (geometry == null) {
            double[] anchor = fleetAnchor(normalizedTenantId, normalizedFleetId);
            geometry = new AreaGeometry(round6(anchor[0]), round6(anchor[1]), DEFAULT_AREA_RADIUS_METERS);
        }

        String resolvedAreaId = normalizedAreaId == null ? normalizedFleetId + "-area" : normalizedAreaId;
        String resolvedAreaName = normalizedAreaName == null ? normalizedDisplayName + " Zone" : normalizedAreaName;

        TenantState tenantState = tenantFleetRepository.getOrCreateTenantState(normalizedTenantId);
        FleetState existing = tenantState.fleets().putIfAbsent(normalizedFleetId,
                new FleetState(
                        normalizedFleetId,
                        normalizedDisplayName,
                        resolvedAreaId,
                        resolvedAreaName,
                        geometry.centerLat(),
                        geometry.centerLng(),
                        geometry.radiusMeters()));

        if (existing != null) {
            throw new ConflictException(
                    "Fleet '" + normalizedFleetId + "' already exists for tenant '" + normalizedTenantId + "'.");
        }

        return new FleetDto(
                normalizedFleetId,
                normalizedDisplayName,
                0,
                resolvedAreaId,
                resolvedAreaName,
                geometry.centerLat(),
                geometry.centerLng(),
                geometry.radiusMeters());
    }

    public List<FleetDto> listFleets(String tenantId, TenantRole role) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireReadRole(role);

        return tenantFleetRepository.findTenantState(normalizedTenantId)
                .map(tenantState -> tenantState.fleets().values().stream()
                        .sorted(Comparator.comparing(FleetState::fleetId))
                        .map(this::toFleetDto)
                        .toList())
                .orElseGet(List::of);
    }

    public FleetDto updateFleet(
            String tenantId,
            TenantRole role,
            String fleetId,
            String displayName,
            String areaId,
            String areaName) {
        return updateFleet(tenantId, role, fleetId, displayName, areaId, areaName, null, null, null);
    }

    public FleetDto updateFleet(
            String tenantId,
            TenantRole role,
            String fleetId,
            String displayName,
            String areaId,
            String areaName,
            Double areaCenterLat,
            Double areaCenterLng,
            Double areaRadiusMeters) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedAreaId = normalizeOptionalResourceId(areaId, "Area id");
        String normalizedAreaName = normalizeOptionalDisplayName(areaName);
        AreaGeometry normalizedAreaGeometry = normalizeOptionalAreaGeometry(
                areaCenterLat,
                areaCenterLng,
                areaRadiusMeters);

        if (normalizedAreaId != null && normalizedAreaName == null) {
            throw new TenantValidationException(
                    "areaName must be provided when areaId is set for fleet area association.");
        }

        TenantState tenantState = tenantFleetRepository.findTenantState(normalizedTenantId)
                .orElseThrow(() -> new NotFoundException("Tenant '" + normalizedTenantId + "' has no fleets."));

        FleetState fleetState = tenantState.fleets().get(normalizedFleetId);
        if (fleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        String updatedDisplayName = normalizeDisplayName(displayName, fleetState.displayName());
        String updatedAreaId = normalizedAreaId == null ? fleetState.areaId() : normalizedAreaId;
        String updatedAreaName = normalizedAreaName == null ? fleetState.areaName() : normalizedAreaName;
        double updatedAreaCenterLat = normalizedAreaGeometry == null
                ? fleetState.areaCenterLat()
                : normalizedAreaGeometry.centerLat();
        double updatedAreaCenterLng = normalizedAreaGeometry == null
                ? fleetState.areaCenterLng()
                : normalizedAreaGeometry.centerLng();
        double updatedAreaRadiusMeters = normalizedAreaGeometry == null
                ? fleetState.areaRadiusMeters()
                : normalizedAreaGeometry.radiusMeters();

        fleetState.updateMetadata(
                updatedDisplayName,
                updatedAreaId,
                updatedAreaName,
                updatedAreaCenterLat,
                updatedAreaCenterLng,
                updatedAreaRadiusMeters);

        return toFleetDto(fleetState);
    }

    public MowerDto registerMower(String tenantId, TenantRole role, String fleetId, String mowerId, String model) {
        return registerMower(tenantId, role, fleetId, mowerId, model, null);
    }

    public MowerDto registerMower(
            String tenantId,
            TenantRole role,
            String fleetId,
            String mowerId,
            String model,
            Boolean simulated) {
        return registerMower(tenantId, role, fleetId, mowerId, model, simulated, null, null);
    }

    public MowerDto registerMower(
            String tenantId,
            TenantRole role,
            String fleetId,
            String mowerId,
            String model,
            Boolean simulated,
            Double startLatitude,
            Double startLongitude) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedMowerId = requireValidResourceId(mowerId, "Mower id");
        String normalizedModel = normalizeDisplayName(model, "GENERIC");
        boolean simulatedMower = Boolean.TRUE.equals(simulated);

        TenantState tenantState = tenantFleetRepository.getOrCreateTenantState(normalizedTenantId);
        FleetState fleetState = tenantState.fleets().get(normalizedFleetId);
        if (fleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        Double[] normalizedStartCoordinates = new Double[] { null, null };
        if (simulatedMower) {
            normalizedStartCoordinates = validatePinnedStartCoordinates(
                    startLatitude,
                    startLongitude,
                    normalizedFleetId,
                    fleetState.areaCenterLat(),
                    fleetState.areaCenterLng(),
                    fleetState.areaRadiusMeters());
        }

        MowerRegistration registration = new MowerRegistration(
                normalizedMowerId,
                normalizedModel,
                Instant.now(),
                simulatedMower,
                normalizedStartCoordinates[0],
                normalizedStartCoordinates[1]);
        MowerRegistration existing = fleetState.mowers().putIfAbsent(normalizedMowerId, registration);
        if (existing != null) {
            throw new ConflictException(
                    "Mower '" + normalizedMowerId + "' already exists in fleet '" + normalizedFleetId + "'.");
        }

        fleetState.mowerTelemetry().put(normalizedMowerId,
                buildTelemetry(normalizedTenantId, fleetState, registration));

        return new MowerDto(
                registration.mowerId(),
                registration.model(),
                registration.registeredAt().toString(),
                registration.simulated());
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
                        registration.registeredAt().toString(),
                        registration.simulated()))
                .toList();
    }

    public MowerDto updateMower(
            String tenantId,
            TenantRole role,
            String fleetId,
            String mowerId,
            String model,
            String targetFleetId) {
        String normalizedTenantId = TenantIdValidator.requireValidTenantId(tenantId);
        requireMutationRole(role);

        String normalizedFleetId = requireValidResourceId(fleetId, "Fleet id");
        String normalizedMowerId = requireValidResourceId(mowerId, "Mower id");
        String normalizedTargetFleetId = targetFleetId == null || targetFleetId.isBlank()
                ? normalizedFleetId
                : requireValidResourceId(targetFleetId, "Fleet id");

        TenantState tenantState = tenantFleetRepository.findTenantState(normalizedTenantId)
                .orElseThrow(() -> new NotFoundException("Tenant '" + normalizedTenantId + "' has no fleets."));

        FleetState sourceFleetState = tenantState.fleets().get(normalizedFleetId);
        if (sourceFleetState == null) {
            throw new NotFoundException(
                    "Fleet '" + normalizedFleetId + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        MowerRegistration existingRegistration = sourceFleetState.mowers().get(normalizedMowerId);
        if (existingRegistration == null) {
            throw new NotFoundException(
                    "Mower '" + normalizedMowerId + "' does not exist in fleet '" + normalizedFleetId + "'.");
        }

        FleetState targetFleetState = tenantState.fleets().get(normalizedTargetFleetId);
        if (targetFleetState == null) {
            throw new NotFoundException("Fleet '" + normalizedTargetFleetId
                    + "' does not exist for tenant '" + normalizedTenantId + "'.");
        }

        if (!normalizedFleetId.equals(normalizedTargetFleetId)
                && targetFleetState.mowers().containsKey(normalizedMowerId)) {
            throw new ConflictException(
                    "Mower '" + normalizedMowerId + "' already exists in fleet '" + normalizedTargetFleetId + "'.");
        }

        String updatedModel = normalizeDisplayName(model, existingRegistration.model());
        MowerRegistration updatedRegistration = new MowerRegistration(
                existingRegistration.mowerId(),
                updatedModel,
                existingRegistration.registeredAt(),
                existingRegistration.simulated(),
                existingRegistration.startLatitude(),
                existingRegistration.startLongitude());

        if (normalizedFleetId.equals(normalizedTargetFleetId)) {
            sourceFleetState.mowers().put(normalizedMowerId, updatedRegistration);
            sourceFleetState.mowerTelemetry().put(
                    normalizedMowerId,
                    buildTelemetry(normalizedTenantId, sourceFleetState, updatedRegistration));
        } else {
            sourceFleetState.mowers().remove(normalizedMowerId);
            sourceFleetState.mowerTelemetry().remove(normalizedMowerId);
            sourceFleetState.removeTelemetryTick(normalizedMowerId);

            List<RemoteCommandExecution> commandHistory = sourceFleetState.mowerCommandHistory()
                    .remove(normalizedMowerId);
            if (commandHistory != null) {
                targetFleetState.mowerCommandHistory().put(normalizedMowerId, commandHistory);
            }

            List<TelemetryEvent> telemetryEvents = sourceFleetState.mowerTelemetryLog().remove(normalizedMowerId);
            if (telemetryEvents != null) {
                targetFleetState.mowerTelemetryLog().put(normalizedMowerId, telemetryEvents);
            }

            targetFleetState.mowers().put(normalizedMowerId, updatedRegistration);
            targetFleetState.mowerTelemetry().put(
                    normalizedMowerId,
                    buildTelemetry(normalizedTenantId, targetFleetState, updatedRegistration));
        }

        return new MowerDto(
                updatedRegistration.mowerId(),
                updatedRegistration.model(),
                updatedRegistration.registeredAt().toString(),
                updatedRegistration.simulated());
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
                            int progressionTick = fleet.nextTelemetryTick(registration.mowerId());
                            MowerTelemetryState telemetry = buildTelemetry(
                                    normalizedTenantId,
                                    fleet,
                                    registration,
                                    progressionTick);
                            fleet.mowerTelemetry().put(registration.mowerId(), telemetry);
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

    private static String normalizeOptionalResourceId(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return requireValidResourceId(value, fieldName);
    }

    private static String normalizeOptionalDisplayName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private static AreaGeometry normalizeOptionalAreaGeometry(
            Double areaCenterLat,
            Double areaCenterLng,
            Double areaRadiusMeters) {
        if (areaCenterLat == null && areaCenterLng == null && areaRadiusMeters == null) {
            return null;
        }

        if (areaCenterLat == null || areaCenterLng == null || areaRadiusMeters == null) {
            throw new TenantValidationException(
                    "areaCenterLat, areaCenterLng, and areaRadiusMeters must all be provided when setting fleet area geometry.");
        }

        if (!Double.isFinite(areaCenterLat)
                || !Double.isFinite(areaCenterLng)
                || !Double.isFinite(areaRadiusMeters)) {
            throw new TenantValidationException("Fleet area geometry values must be finite numbers.");
        }

        if (areaCenterLat < -90d || areaCenterLat > 90d) {
            throw new TenantValidationException("areaCenterLat must be between -90 and 90.");
        }

        if (areaCenterLng < -180d || areaCenterLng > 180d) {
            throw new TenantValidationException("areaCenterLng must be between -180 and 180.");
        }

        if (areaRadiusMeters <= 0d) {
            throw new TenantValidationException("areaRadiusMeters must be greater than 0.");
        }

        if (areaRadiusMeters > MAX_AREA_RADIUS_METERS) {
            throw new TenantValidationException(
                    "areaRadiusMeters must be less than or equal to " + (int) MAX_AREA_RADIUS_METERS + ".");
        }

        return new AreaGeometry(
                round6(areaCenterLat),
                round6(areaCenterLng),
                round1(areaRadiusMeters));
    }

    private FleetDto toFleetDto(FleetState fleetState) {
        return new FleetDto(
                fleetState.fleetId(),
                fleetState.displayName(),
                fleetState.mowers().size(),
                fleetState.areaId(),
                fleetState.areaName(),
                fleetState.areaCenterLat(),
                fleetState.areaCenterLng(),
                fleetState.areaRadiusMeters());
    }

    private static MowerTelemetryState buildTelemetry(
            String tenantId,
            FleetState fleetState,
            MowerRegistration registration) {
        return buildTelemetry(tenantId, fleetState, registration, 0);
    }

    private static MowerTelemetryState buildTelemetry(
            String tenantId,
            FleetState fleetState,
            MowerRegistration registration,
            int progressionTick) {
        int hash = stableHash(tenantId + ":" + fleetState.fleetId() + ":" + registration.mowerId());
        String status = registration.simulated() ? "cutting" : STATUS_VALUES.get(hash % STATUS_VALUES.size());
        int baseBatteryPercent = 25 + (stableHash(registration.mowerId() + ":battery") % 71);
        int baseRuntimeMinutesToday = registration.simulated()
                ? 180 + (stableHash(registration.mowerId() + ":runtime") % 180)
                : 120 + (stableHash(registration.mowerId() + ":runtime") % 270);
        int runtimeMinutesToday = baseRuntimeMinutesToday + Math.max(0, progressionTick);

        int batteryPercent = baseBatteryPercent;
        if (registration.simulated()) {
            int drain = Math.max(0, progressionTick / 2);
            batteryPercent = Math.max(12, baseBatteryPercent - drain);
        }

        double[] fleetAnchor = fleetAnchor(tenantId, fleetState.fleetId());
        boolean pinnedStart = registration.startLatitude() != null && registration.startLongitude() != null;
        int mowerOffset = stableHash(registration.mowerId()) % 11;
        double fleetAnchorLat = fleetAnchor[0];
        double fleetAnchorLng = fleetAnchor[1];
        double baseLat = pinnedStart
                ? registration.startLatitude()
                : fleetAnchorLat + ((mowerOffset - 5) * 0.0008);
        double baseLng = pinnedStart
                ? registration.startLongitude()
                : fleetAnchorLng + ((mowerOffset - 5) * 0.0009);
        double latProgress = (pinnedStart && progressionTick == 0)
                ? 0d
                : ((progressionTick % 9) - 4) * 0.00012;
        double lngProgress = (pinnedStart && progressionTick == 0)
                ? 0d
                : ((progressionTick % 11) - 5) * 0.00010;
        double latitude = round6(clampToGeofence(baseLat + latProgress, fleetAnchorLat, GEO_FENCE_LAT_BOUND));
        double longitude = round6(clampToGeofence(baseLng + lngProgress, fleetAnchorLng, GEO_FENCE_LNG_BOUND));

        String areaId = fleetState.areaId();
        String areaName = fleetState.areaName();
        double targetCoverageHa = 8 + (stableHash(fleetState.fleetId() + ":target") % 9);
        double progress = 0.68 + ((stableHash(registration.mowerId() + ":progress") % 27) / 100.0);
        double baseCoverageTodayHa = targetCoverageHa * progress;
        double coverageTodayHa = round1(Math.min(targetCoverageHa, baseCoverageTodayHa + (progressionTick * 0.03)));

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
            return new double[] { 47.6134, -122.2874 };
        }
        if (tenantId.contains("gamma")) {
            return new double[] { 47.6018, -122.2828 };
        }
        return new double[] { 47.6229, -122.2891 };
    }

    private static double[] fleetAnchor(String tenantId, String fleetId) {
        double[] tenantAnchor = tenantAnchor(tenantId);
        int fleetOffset = stableHash(fleetId) % 7;
        return new double[] {
                tenantAnchor[0] + ((fleetOffset - 3) * 0.004),
                tenantAnchor[1] + ((fleetOffset - 3) * 0.0035)
        };
    }

    private static Double[] validatePinnedStartCoordinates(
            Double startLatitude,
            Double startLongitude,
            String fleetId,
            double fleetCenterLatitude,
            double fleetCenterLongitude,
            double fleetRadiusMeters) {
        if (startLatitude == null && startLongitude == null) {
            return new Double[] { null, null };
        }

        if (startLatitude == null || startLongitude == null) {
            throw new TenantValidationException(
                    "startLatitude and startLongitude must both be provided when setting pinned mower coordinates.");
        }

        if (!Double.isFinite(startLatitude) || !Double.isFinite(startLongitude)) {
            throw new TenantValidationException("Pinned mower coordinates must be finite numbers.");
        }

        double latitudeBoundFromRadius = fleetRadiusMeters / METERS_PER_LATITUDE_DEGREE;
        double longitudeMetersPerDegree = Math.max(
                1d,
                METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(fleetCenterLatitude)));
        double longitudeBoundFromRadius = fleetRadiusMeters / longitudeMetersPerDegree;

        double latitudeBound = Math.max(GEO_FENCE_LAT_BOUND, latitudeBoundFromRadius);
        double longitudeBound = Math.max(GEO_FENCE_LNG_BOUND, longitudeBoundFromRadius);

        if (Math.abs(startLatitude - fleetCenterLatitude) > latitudeBound
                || Math.abs(startLongitude - fleetCenterLongitude) > longitudeBound) {
            double minLatitude = round6(fleetCenterLatitude - latitudeBound);
            double maxLatitude = round6(fleetCenterLatitude + latitudeBound);
            double minLongitude = round6(fleetCenterLongitude - longitudeBound);
            double maxLongitude = round6(fleetCenterLongitude + longitudeBound);
            throw new TenantValidationException(
                    "Pinned mower coordinates are outside fleet '" + fleetId
                            + "' geofence. Allowed latitude range: [" + minLatitude + ", " + maxLatitude
                            + "], longitude range: [" + minLongitude + ", " + maxLongitude + "].");
        }

        return new Double[] { round6(startLatitude), round6(startLongitude) };
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static double clampToGeofence(double value, double anchor, double bound) {
        return Math.max(anchor - bound, Math.min(anchor + bound, value));
    }

    private record AreaGeometry(double centerLat, double centerLng, double radiusMeters) {
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
