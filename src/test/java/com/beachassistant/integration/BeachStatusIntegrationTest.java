package com.beachassistant.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "beach.providers.stub=true"
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BeachStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listBeaches_shouldReturnAshdodBeaches() throws Exception {
        mockMvc.perform(get("/api/v1/beaches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(11))))
                .andExpect(jsonPath("$[0].city", is("Ashdod")));
    }

    @Test
    void getStatus_unknownBeach_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/beaches/does-not-exist/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("BEACH_NOT_FOUND")));
    }

    @Test
    void getStatus_yudAlef_shouldReturnDecision() throws Exception {
        // First run ingestion so that snapshots exist
        mockMvc.perform(post("/api/v1/admin/ingest/SEA_FORECAST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        mockMvc.perform(post("/api/v1/admin/ingest/HEALTH_ADVISORY"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/beaches/yud-alef/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beach", is("Yud Alef")))
                .andExpect(jsonPath("$.recommendation",
                        oneOf("CAN_SWIM", "CAUTION", "DO_NOT_RECOMMEND", "UNKNOWN")))
                .andExpect(jsonPath("$.confidence", notNullValue()))
                .andExpect(jsonPath("$.freshnessStatus", notNullValue()));
    }

    @Test
    void getHours_yudAlef_shouldReturnSchedule() throws Exception {
        mockMvc.perform(get("/api/v1/beaches/yud-alef/hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beach", is("Yud Alef")));
    }

    @Test
    void getJellyfish_yudAlef_afterIngest_shouldReturnData() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ingest/JELLYFISH"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/beaches/yud-alef/jellyfish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beach", is("Yud Alef")));
    }

    @Test
    void getCamera_yudAlef_shouldReturnCameraInfo() throws Exception {
        mockMvc.perform(get("/api/v1/beaches/yud-alef/camera"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveUrl", notNullValue()));
    }

    @Test
    void adminDiagnostics_shouldReturnAllSourceTypes() throws Exception {
        mockMvc.perform(get("/api/v1/admin/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void adminIngest_invalidSourceType_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ingest/INVALID_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INVALID_SOURCE_TYPE")));
    }
}
