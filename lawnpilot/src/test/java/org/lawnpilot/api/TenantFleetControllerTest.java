package org.lawnpilot.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.lawnpilot.api.dto.FleetDto;
import org.lawnpilot.api.dto.MowerDto;
import org.lawnpilot.api.dto.MowerTelemetryDto;
import org.lawnpilot.api.tenant.TenantFleetService;
import org.lawnpilot.exceptions.RoleAuthorizationException;
import org.lawnpilot.exceptions.TenantValidationException;
import org.lawnpilot.config.CorsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import java.util.List;

@WebMvcTest(controllers = TenantFleetController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class TenantFleetControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private TenantFleetService tenantFleetService;

        @MockBean
        private SimulationService simulationService;

        @Test
        void tenantMowerUpdatePreflightIncludesCorsHeadersForFrontendOrigin() throws Exception {
                mockMvc.perform(options("/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers/mower-1")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "PUT")
                                .header("Access-Control-Request-Headers", "X-Role,Content-Type"))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                                .andExpect(header().string("Access-Control-Allow-Methods",
                                                org.hamcrest.Matchers.containsString("PUT")))
                                .andExpect(header().string("Access-Control-Allow-Headers",
                                                org.hamcrest.Matchers.containsString("X-Role")));
        }

        @Test
        void createFleetRejectsInvalidRoleHeaderWith400() throws Exception {
                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ROOT")
                                .content("{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "Invalid X-Role header 'ROOT'. Allowed values: ADMIN, OPERATOR, VIEWER."));
        }

        @Test
        void createFleetRejectsViewerRoleWith403() throws Exception {
                when(tenantFleetService.createFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("demo"),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull()))
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
                when(tenantFleetService.createFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("demo"),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull()))
                                .thenReturn(new FleetDto("fleet-1", "demo", 0, "fleet-1-area", "demo Zone", 47.6229,
                                                -122.2891, 1500.0));

                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content("{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\"}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\",\"mowerCount\":0,\"areaId\":\"fleet-1-area\",\"areaName\":\"demo Zone\",\"areaCenterLat\":47.6229,\"areaCenterLng\":-122.2891,\"areaRadiusMeters\":1500.0}"));
        }

        @Test
        void createFleetAcceptsCircleGeometryPayload() throws Exception {
                when(tenantFleetService.createFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("demo"),
                                isNull(),
                                eq("East Circle"),
                                eq(47.6211),
                                eq(-122.3301),
                                eq(800.0)))
                                .thenReturn(new FleetDto("fleet-1", "demo", 0, "fleet-1-area", "East Circle", 47.6211,
                                                -122.3301, 800.0));

                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content(
                                                "{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\",\"areaName\":\"East Circle\",\"areaCenterLat\":47.6211,\"areaCenterLng\":-122.3301,\"areaRadiusMeters\":800}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"fleetId\":\"fleet-1\",\"displayName\":\"demo\",\"mowerCount\":0,\"areaId\":\"fleet-1-area\",\"areaName\":\"East Circle\",\"areaCenterLat\":47.6211,\"areaCenterLng\":-122.3301,\"areaRadiusMeters\":800.0}"));
        }

        @Test
        void createFleetCircleGeometryPayloadReturnsCenterAndRadiusInResponse() throws Exception {
                when(tenantFleetService.createFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-geo"),
                                eq("Geo Fleet"),
                                isNull(),
                                eq("Geo Circle"),
                                eq(47.612345),
                                eq(-122.30321),
                                eq(275.5)))
                                .thenReturn(new FleetDto("fleet-geo", "Geo Fleet", 0, "fleet-geo-area", "Geo Circle",
                                                47.612345, -122.30321, 275.5));

                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content(
                                                "{\"fleetId\":\"fleet-geo\",\"displayName\":\"Geo Fleet\",\"areaName\":\"Geo Circle\",\"areaCenterLat\":47.612345,\"areaCenterLng\":-122.30321,\"areaRadiusMeters\":275.5}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"fleetId\":\"fleet-geo\",\"displayName\":\"Geo Fleet\",\"mowerCount\":0,\"areaId\":\"fleet-geo-area\",\"areaName\":\"Geo Circle\",\"areaCenterLat\":47.612345,\"areaCenterLng\":-122.30321,\"areaRadiusMeters\":275.5}"));
        }

        @Test
        void createFleetRejectsInvalidCircleGeometryWithClearErrorMessage() throws Exception {
                when(tenantFleetService.createFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-geo"),
                                eq("Geo Fleet"),
                                isNull(),
                                nullable(String.class),
                                eq(91.0),
                                eq(-122.30321),
                                eq(-4.0)))
                                .thenThrow(new TenantValidationException(
                                                "areaCenterLat must be between -90 and 90."));

                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content(
                                                "{\"fleetId\":\"fleet-geo\",\"displayName\":\"Geo Fleet\",\"areaCenterLat\":91.0,\"areaCenterLng\":-122.30321,\"areaRadiusMeters\":-4}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "areaCenterLat must be between -90 and 90."));
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

        @Test
        void registerMowerAcceptsLegacyPayloadWithoutSimulatedFlag() throws Exception {
                when(tenantFleetService.registerMower(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("mower-1"),
                                eq("LP-X"),
                                isNull(),
                                isNull(),
                                isNull()))
                                .thenReturn(new MowerDto("mower-1", "LP-X", "2026-08-31T22:00:51+02:00", false));

                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content("{\"mowerId\":\"mower-1\",\"model\":\"LP-X\"}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"mowerId\":\"mower-1\",\"model\":\"LP-X\",\"registeredAt\":\"2026-08-31T22:00:51+02:00\",\"simulated\":false}"));
        }

        @Test
        void registerMowerPassesOptionalPinnedCoordinates() throws Exception {
                when(tenantFleetService.registerMower(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("mower-9"),
                                eq("LP-Z"),
                                eq(Boolean.TRUE),
                                nullable(Double.class),
                                nullable(Double.class)))
                                .thenReturn(new MowerDto("mower-9", "LP-Z", "2026-08-31T22:00:52+02:00", true));

                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content(
                                                "{\"mowerId\":\"mower-9\",\"model\":\"LP-Z\",\"simulated\":true,\"startLatitude\":47.6234,\"startLongitude\":-122.2895}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"mowerId\":\"mower-9\",\"model\":\"LP-Z\",\"registeredAt\":\"2026-08-31T22:00:52+02:00\",\"simulated\":true}"));
        }

        @Test
        void registerMowerRejectsPartialPinnedCoordinatesWithClearMessage() throws Exception {
                when(tenantFleetService.registerMower(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("mower-10"),
                                eq("LP-Z"),
                                eq(Boolean.TRUE),
                                eq(47.6234),
                                isNull()))
                                .thenThrow(new TenantValidationException(
                                                "startLatitude and startLongitude must both be provided when setting pinned mower coordinates."));

                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content(
                                                "{\"mowerId\":\"mower-10\",\"model\":\"LP-Z\",\"simulated\":true,\"startLatitude\":47.6234}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "startLatitude and startLongitude must both be provided when setting pinned mower coordinates."));
        }

        @Test
        void registerMowerRejectsOutOfBoundsPinnedCoordinatesWithClearMessage() throws Exception {
                when(tenantFleetService.registerMower(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("mower-11"),
                                eq("LP-Z"),
                                eq(Boolean.TRUE),
                                eq(47.7),
                                eq(-122.1)))
                                .thenThrow(new TenantValidationException(
                                                "Pinned mower coordinates are outside fleet 'fleet-1' geofence. Allowed latitude range: [47.6079, 47.6379], longitude range: [-122.3041, -122.2741]."));

                mockMvc.perform(post("/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content(
                                                "{\"mowerId\":\"mower-11\",\"model\":\"LP-Z\",\"simulated\":true,\"startLatitude\":47.7,\"startLongitude\":-122.1}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "Pinned mower coordinates are outside fleet 'fleet-1' geofence. Allowed latitude range: [47.6079, 47.6379], longitude range: [-122.3041, -122.2741]."));
        }

        @Test
        void updateFleetAcceptsDisplayAndAreaPayload() throws Exception {
                when(tenantFleetService.updateFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("North Crew"),
                                eq("north-zone"),
                                eq("North Zone"),
                                isNull(),
                                isNull(),
                                isNull()))
                                .thenReturn(new FleetDto("fleet-1", "North Crew", 2, "north-zone", "North Zone",
                                                47.6201,
                                                -122.2899, 1200.0));

                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "OPERATOR")
                                .content("{\"displayName\":\"North Crew\",\"areaId\":\"north-zone\",\"areaName\":\"North Zone\"}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"fleetId\":\"fleet-1\",\"displayName\":\"North Crew\",\"mowerCount\":2,\"areaId\":\"north-zone\",\"areaName\":\"North Zone\",\"areaCenterLat\":47.6201,\"areaCenterLng\":-122.2899,\"areaRadiusMeters\":1200.0}"));
        }

        @Test
        void updateFleetAcceptsCircleGeometryPayload() throws Exception {
                when(tenantFleetService.updateFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("North Crew"),
                                isNull(),
                                eq("North Circle"),
                                eq(47.6198),
                                eq(-122.2877),
                                eq(950.0)))
                                .thenReturn(new FleetDto("fleet-1", "North Crew", 2, "fleet-1-area", "North Circle",
                                                47.6198,
                                                -122.2877, 950.0));

                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "OPERATOR")
                                .content(
                                                "{\"displayName\":\"North Crew\",\"areaName\":\"North Circle\",\"areaCenterLat\":47.6198,\"areaCenterLng\":-122.2877,\"areaRadiusMeters\":950}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"fleetId\":\"fleet-1\",\"displayName\":\"North Crew\",\"mowerCount\":2,\"areaId\":\"fleet-1-area\",\"areaName\":\"North Circle\",\"areaCenterLat\":47.6198,\"areaCenterLng\":-122.2877,\"areaRadiusMeters\":950.0}"));
        }

        @Test
        void updateFleetCircleGeometryPayloadReturnsCenterAndRadiusInResponse() throws Exception {
                when(tenantFleetService.updateFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("North Crew"),
                                eq("north-zone"),
                                eq("North Zone"),
                                eq(47.61001),
                                eq(-122.30199),
                                eq(220.0)))
                                .thenReturn(new FleetDto("fleet-1", "North Crew", 2, "north-zone", "North Zone",
                                                47.61001, -122.30199, 220.0));

                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "OPERATOR")
                                .content(
                                                "{\"displayName\":\"North Crew\",\"areaId\":\"north-zone\",\"areaName\":\"North Zone\",\"areaCenterLat\":47.61001,\"areaCenterLng\":-122.30199,\"areaRadiusMeters\":220}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"fleetId\":\"fleet-1\",\"displayName\":\"North Crew\",\"mowerCount\":2,\"areaId\":\"north-zone\",\"areaName\":\"North Zone\",\"areaCenterLat\":47.61001,\"areaCenterLng\":-122.30199,\"areaRadiusMeters\":220.0}"));
        }

        @Test
        void updateFleetRejectsInvalidCircleGeometryWithClearErrorMessage() throws Exception {
                when(tenantFleetService.updateFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("North Crew"),
                                eq("north-zone"),
                                eq("North Zone"),
                                eq(47.61001),
                                eq(-122.30199),
                                eq(0.0)))
                                .thenThrow(new TenantValidationException(
                                                "areaRadiusMeters must be greater than 0."));

                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "OPERATOR")
                                .content(
                                                "{\"displayName\":\"North Crew\",\"areaId\":\"north-zone\",\"areaName\":\"North Zone\",\"areaCenterLat\":47.61001,\"areaCenterLng\":-122.30199,\"areaRadiusMeters\":0}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "areaRadiusMeters must be greater than 0."));
        }

        @Test
        void listFleetsIncludesPersistedCircleGeometryAfterCreateAndUpdate() throws Exception {
                when(tenantFleetService.listFleets(eq("tenant-alpha"), any()))
                                .thenReturn(List.of(new FleetDto("fleet-geo", "Geo Fleet", 3, "fleet-geo-area",
                                                "Geo Circle",
                                                47.612345, -122.30321, 275.5)));

                mockMvc.perform(get("/api/v1/tenants/tenant-alpha/fleets")
                                .header("X-Role", "VIEWER"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "[{\"fleetId\":\"fleet-geo\",\"displayName\":\"Geo Fleet\",\"mowerCount\":3,\"areaId\":\"fleet-geo-area\",\"areaName\":\"Geo Circle\",\"areaCenterLat\":47.612345,\"areaCenterLng\":-122.30321,\"areaRadiusMeters\":275.5}]"));
        }

        @Test
        void updateMowerAcceptsModelAndFleetReassignmentPayload() throws Exception {
                when(tenantFleetService.updateMower(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("mower-9"),
                                eq("LP-Z2"),
                                eq("fleet-2")))
                                .thenReturn(new MowerDto("mower-9", "LP-Z2", "2026-08-31T22:00:52+02:00", true));

                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers/mower-9")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content("{\"model\":\"LP-Z2\",\"fleetId\":\"fleet-2\"}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                "{\"mowerId\":\"mower-9\",\"model\":\"LP-Z2\",\"registeredAt\":\"2026-08-31T22:00:52+02:00\",\"simulated\":true}"));
        }

        @Test
        void updateMowerRejectsInvalidRoleHeaderWith400() throws Exception {
                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers/mower-9")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ROOT")
                                .content("{\"model\":\"LP-Z2\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "Invalid X-Role header 'ROOT'. Allowed values: ADMIN, OPERATOR, VIEWER."));
        }

        @Test
        void updateFleetRejectsMismatchedAreaPayloadWith400() throws Exception {
                when(tenantFleetService.updateFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("North Crew"),
                                eq("north-zone"),
                                eq(""),
                                isNull(),
                                isNull(),
                                isNull()))
                                .thenThrow(new TenantValidationException(
                                                "areaName must be provided when areaId is set for fleet area association."));

                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "OPERATOR")
                                .content("{\"displayName\":\"North Crew\",\"areaId\":\"north-zone\",\"areaName\":\"\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "areaName must be provided when areaId is set for fleet area association."));
        }

        @Test
        void updateFleetRejectsPartialCircleGeometryWith400() throws Exception {
                when(tenantFleetService.updateFleet(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("North Crew"),
                                isNull(),
                                eq("North Circle"),
                                eq(47.6198),
                                isNull(),
                                eq(950.0)))
                                .thenThrow(new TenantValidationException(
                                                "areaCenterLat, areaCenterLng, and areaRadiusMeters must all be provided when setting fleet area geometry."));

                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "OPERATOR")
                                .content(
                                                "{\"displayName\":\"North Crew\",\"areaName\":\"North Circle\",\"areaCenterLat\":47.6198,\"areaRadiusMeters\":950}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "areaCenterLat, areaCenterLng, and areaRadiusMeters must all be provided when setting fleet area geometry."));
        }

        @Test
        void updateMowerRejectsInvalidTargetFleetIdWith400() throws Exception {
                when(tenantFleetService.updateMower(
                                eq("tenant-alpha"),
                                any(),
                                eq("fleet-1"),
                                eq("mower-9"),
                                eq("LP-Z2"),
                                eq("fleet invalid")))
                                .thenThrow(new TenantValidationException(
                                                "Fleet id 'fleet invalid' is invalid. Use 1-64 characters: letters, digits, '_' or '-'."));

                mockMvc.perform(put("/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers/mower-9")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Role", "ADMIN")
                                .content("{\"model\":\"LP-Z2\",\"fleetId\":\"fleet invalid\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(
                                                "Fleet id 'fleet invalid' is invalid. Use 1-64 characters: letters, digits, '_' or '-'."));
        }
}
