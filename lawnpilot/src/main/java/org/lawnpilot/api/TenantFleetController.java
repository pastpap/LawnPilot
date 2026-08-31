package org.lawnpilot.api;

import java.util.List;
import org.lawnpilot.api.dto.FleetCreateRequestDto;
import org.lawnpilot.api.dto.FleetDto;
import org.lawnpilot.api.dto.MowerDto;
import org.lawnpilot.api.dto.MowerRegisterRequestDto;
import org.lawnpilot.api.dto.MowerTelemetryDto;
import org.lawnpilot.api.dto.SimulationRequestDto;
import org.lawnpilot.api.dto.SimulationResponseDto;
import org.lawnpilot.api.dto.TenantSimulationHistorySummaryDto;
import org.lawnpilot.api.tenant.TenantFleetService;
import org.lawnpilot.api.tenant.TenantRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class TenantFleetController {

    private final TenantFleetService tenantFleetService;
    private final SimulationService simulationService;

    public TenantFleetController(TenantFleetService tenantFleetService, SimulationService simulationService) {
        this.tenantFleetService = tenantFleetService;
        this.simulationService = simulationService;
    }

    @PostMapping("/fleets")
    public FleetDto createFleet(
            @PathVariable String tenantId,
            @RequestHeader("X-Role") String roleHeader,
            @RequestBody FleetCreateRequestDto request) {
        return tenantFleetService.createFleet(
                tenantId,
                TenantRole.fromHeader(roleHeader),
                request.fleetId(),
                request.displayName());
    }

    @GetMapping("/fleets")
    public List<FleetDto> listFleets(@PathVariable String tenantId, @RequestHeader("X-Role") String roleHeader) {
        return tenantFleetService.listFleets(tenantId, TenantRole.fromHeader(roleHeader));
    }

    @PostMapping("/fleets/{fleetId}/mowers")
    public MowerDto registerMower(
            @PathVariable String tenantId,
            @PathVariable String fleetId,
            @RequestHeader("X-Role") String roleHeader,
            @RequestBody MowerRegisterRequestDto request) {
        return tenantFleetService.registerMower(
                tenantId,
                TenantRole.fromHeader(roleHeader),
                fleetId,
                request.mowerId(),
                request.model());
    }

    @GetMapping("/fleets/{fleetId}/mowers")
    public List<MowerDto> listMowers(
            @PathVariable String tenantId,
            @PathVariable String fleetId,
            @RequestHeader("X-Role") String roleHeader) {
        return tenantFleetService.listMowers(tenantId, TenantRole.fromHeader(roleHeader), fleetId);
    }

    @GetMapping("/telemetry/mowers")
    public List<MowerTelemetryDto> listMowerTelemetry(
            @PathVariable String tenantId,
            @RequestHeader("X-Role") String roleHeader,
            @RequestParam(required = false) String fleetId) {
        return tenantFleetService.listMowerTelemetry(
                tenantId,
                TenantRole.fromHeader(roleHeader),
                fleetId);
    }

    @PostMapping("/simulations")
    public SimulationResponseDto runTenantSimulation(
            @PathVariable String tenantId,
            @RequestHeader("X-Role") String roleHeader,
            @RequestBody SimulationRequestDto request) {
        TenantRole role = TenantRole.fromHeader(roleHeader);
        List<String> outputLines = simulationService.runSimulation(request.inputLines());
        tenantFleetService.recordSimulationRun(tenantId, role);
        return new SimulationResponseDto(outputLines);
    }

    @GetMapping("/simulations/history/summary")
    public TenantSimulationHistorySummaryDto getSimulationHistorySummary(
            @PathVariable String tenantId,
            @RequestHeader("X-Role") String roleHeader) {
        return tenantFleetService.getSimulationHistorySummary(tenantId, TenantRole.fromHeader(roleHeader));
    }
}
