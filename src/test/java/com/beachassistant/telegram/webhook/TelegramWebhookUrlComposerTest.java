package com.beachassistant.telegram.webhook;

import com.beachassistant.config.TelegramProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramWebhookUrlComposerTest {

    @Test
    void composesUrlNormalizingBaseAndPath() {
        TelegramProperties p = new TelegramProperties();
        p.setPublicBaseUrl("https://example.com/");
        p.setWebhookPath("/api/telegram/webhook");
        assertThat(TelegramWebhookUrlComposer.compose(p)).isEqualTo("https://example.com/api/telegram/webhook");
    }

    @Test
    void addsLeadingSlashToPathWhenMissing() {
        TelegramProperties p = new TelegramProperties();
        p.setPublicBaseUrl("https://example.com");
        p.setWebhookPath("api/telegram/webhook");
        assertThat(TelegramWebhookUrlComposer.compose(p)).isEqualTo("https://example.com/api/telegram/webhook");
    }
}
