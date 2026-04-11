package com.beachassistant.telegram.webhook;

import com.beachassistant.config.TelegramProperties;
import com.beachassistant.telegram.handler.BeachBotUpdateProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TelegramWebhookController.class)
@EnableConfigurationProperties(TelegramProperties.class)
@TestPropertySource(properties = {
        "beach.telegram.mode=webhook",
        "beach.telegram.token=test-token",
        "beach.telegram.username=test_bot",
        "beach.telegram.webhook-path=/api/telegram/webhook",
        "beach.telegram.webhook-secret-token=mysecret"
})
class TelegramWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BeachBotUpdateProcessor beachBotUpdateProcessor;

    @Test
    void rejectsWhenSecretHeaderMissing() throws Exception {
        String body = minimalStartUpdateJson();
        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsWhenSecretHeaderMatches() throws Exception {
        String body = minimalStartUpdateJson();
        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(TelegramWebhookController.SECRET_TOKEN_HEADER, "mysecret")
                        .content(body))
                .andExpect(status().isOk());
        verify(beachBotUpdateProcessor).processUpdate(any());
    }

    private static String minimalStartUpdateJson() {
        return """
                {
                  "update_id": 100,
                  "message": {
                    "message_id": 1,
                    "date": 1700000000,
                    "from": {"id": 42, "is_bot": false, "first_name": "t"},
                    "chat": {"id": 42, "type": "private"},
                    "text": "/start"
                  }
                }
                """;
    }
}
