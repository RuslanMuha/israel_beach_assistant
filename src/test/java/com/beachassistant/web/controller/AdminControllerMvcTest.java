package com.beachassistant.web.controller;

import com.beachassistant.app.usecase.IngestionUseCase;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.config.SecurityConfig;
import com.beachassistant.persistence.entity.IngestionRunEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.ClosureSnapshotRepository;
import com.beachassistant.persistence.repository.IngestionRunRepository;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
@TestPropertySource(properties = "beach.admin.api-token=test-admin-token")
class AdminControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionUseCase ingestionUseCase;

    @MockBean
    private IngestionRunRepository ingestionRunRepository;

    @MockBean
    private ClosureSnapshotRepository closureSnapshotRepository;

    @MockBean
    private BeachRepository beachRepository;

    @MockBean
    private Clock clock;

    @Test
    void postIngest_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ingest/SEA_FORECAST")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postIngest_withToken_returns202() throws Exception {
        IngestionRunEntity run = new IngestionRunEntity();
        run.setId(42L);
        run.setSourceType(SourceType.SEA_FORECAST);
        run.setStatus("RUNNING");
        when(ingestionUseCase.startIngestionAsync(SourceType.SEA_FORECAST)).thenReturn(Optional.of(run));

        mockMvc.perform(post("/api/v1/admin/ingest/SEA_FORECAST")
                        .header("X-Admin-Token", "test-admin-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/admin/runs/42"))
                .andExpect(jsonPath("$.runId").value(42))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void postIngest_overlap_returns409() throws Exception {
        when(ingestionUseCase.startIngestionAsync(SourceType.JELLYFISH)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/admin/ingest/JELLYFISH")
                        .header("X-Admin-Token", "test-admin-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INGESTION_OVERLAP"));
    }

    @Test
    void getRun_returnsDto() throws Exception {
        IngestionRunEntity run = new IngestionRunEntity();
        run.setId(7L);
        run.setSourceType(SourceType.SEA_FORECAST);
        run.setStatus("SUCCESS");
        when(ingestionRunRepository.findById(7L)).thenReturn(Optional.of(run));

        mockMvc.perform(get("/api/v1/admin/runs/7")
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(7))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
