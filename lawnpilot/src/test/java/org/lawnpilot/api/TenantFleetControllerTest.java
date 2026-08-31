package org.lawnpilot.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.lawnpilot.api.dto.FleetDto;
import org.lawnpilot.api.dto.MowerTelemetryDto;
import org.lawnpilot.api.tenant.TenantFleetService;
import org.lawnpilot.exceptions.RoleAuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.List;

@WebMvcTest(controllers = TenantFleetController.class)
class TenantFleetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantFleetService tenantFleetService;

    @MockBean
    private SimulationService simulationService;

    @Test
    void createFleetRejectsInvalidRoleHeaderWith400() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Role", "ROOT")
                .content("{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid X-Role header 'ROOT'. Allowed values: ADMIN, OPERATOR, VIEWER."));
    }

    @Test
    void createFleetRejectsViewerRoleWith403() throws Exception {
        when(tenantFleetService.createFleet(eq("tenant-alpha"), any(), eq("fleet-1"), eq("demo")))
                .thenThrow(new RoleAuthorizationException(
                        "Role 'VIEWER' is not allowed to modify tenant data. Required role: ADMIN or OPERATOR."));

        mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Role", "VIEWER")
                .content("{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(
                        "Role 'VIEWER' is not allowed to modify tenant data. Required role: ADMIN or OPERATOR."));
    }

    @Test
    void createFleetSuccessStillReturnsPayload() throws Exception {
        when(tenantFleetService.createFleet(eq("tenant-alpha"), any(), eq("fleet-1"), eq("demo")))
                .thenReturn(new FleetDto("fleet-1", "demo", 0));

        mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Role", "ADMIN")
                .content("{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\",\"mowerCount\":0}"));
    }

    @Test
    void telemetryEndpointReturnsPayload() throws Exception {
        when(tenantFleetService.listMowerTelemetry(eq("tenant-alpha"), any(), eq("fleet-1")))
                .thenReturn(List.of(new MowerTelemetryDto(
                        "mower-1",
                        "fleet-1",
                        "LP-X",
                        "cutting",
                        74,
                        210,
                        47.621,
                        -122.333,
                        "fleet-1-area",
                        "North Zone",
                        10.0,
                        8.4)));

        mockMvc.perform(get("/api/v1/tenants/tenant-alpha/telemetry/mowers")
                .header("X-Role", "VIEWER")
                .queryParam("fleetId", "fleet-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "[{\"mowerId\":\"mower-1\",\"fleetId\":\"fleet-1\",\"model\":\"LP-X\",\"status\":\"cutting\",\"batteryPercent\":74,\"runtimeMinutesToday\":210,\"latitude\":47.621,\"longitude\":-122.333,\"areaId\":\"fleet-1-area\",\"areaName\":\"North Zone\",\"targetCoverageHa\":10.0,\"coverageTodayHa\":8.4}]"));
    }
}
