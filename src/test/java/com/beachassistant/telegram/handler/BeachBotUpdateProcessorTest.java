package com.beachassistant.telegram.handler;

import com.beachassistant.app.BeachProfileParser;
import com.beachassistant.app.usecase.BeachResolverUseCase;
import com.beachassistant.app.usecase.BeachStatusUseCase;
import com.beachassistant.app.usecase.CameraUseCase;
import com.beachassistant.app.usecase.JellyfishUseCase;
import com.beachassistant.app.usecase.LifeguardUseCase;
import com.beachassistant.common.exception.BeachNotFoundException;
import com.beachassistant.config.TelegramProperties;
import com.beachassistant.i18n.I18n;
import com.beachassistant.i18n.I18nTestConfig;
import com.beachassistant.subscriptions.SubscriptionService;
import com.beachassistant.persistence.entity.BotInteractionLogEntity;
import com.beachassistant.persistence.repository.BotInteractionLogRepository;
import com.beachassistant.telegram.formatter.ResponseFormatter;
import com.beachassistant.telegram.outbox.TelegramSender;
import com.beachassistant.telegram.ratelimit.TelegramRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BeachBotUpdateProcessorTest {

    private BeachResolverUseCase beachResolver;
    private BeachStatusUseCase statusUseCase;
    private CameraUseCase cameraUseCase;
    private LifeguardUseCase lifeguardUseCase;
    private JellyfishUseCase jellyfishUseCase;
    private ResponseFormatter formatter;
    private BeachProfileParser beachProfileParser;
    private BotInteractionLogRepository logRepository;
    private TelegramSender sender;
    private TelegramRateLimiter rateLimiter;
    private ChatSingleFlightGuard singleFlight;
    private TelegramProperties telegramProperties;
    private BeachBotUpdateProcessor processor;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
        beachResolver = mock(BeachResolverUseCase.class);
        statusUseCase = mock(BeachStatusUseCase.class);
        cameraUseCase = mock(CameraUseCase.class);
        lifeguardUseCase = mock(LifeguardUseCase.class);
        jellyfishUseCase = mock(JellyfishUseCase.class);
        formatter = mock(ResponseFormatter.class);
        beachProfileParser = mock(BeachProfileParser.class);
        logRepository = mock(BotInteractionLogRepository.class);
        sender = mock(TelegramSender.class);
        rateLimiter = mock(TelegramRateLimiter.class);
        singleFlight = new ChatSingleFlightGuard();
        telegramProperties = new TelegramProperties();
        I18n i18n = new I18n(I18nTestConfig.messageSource());
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        processor = new BeachBotUpdateProcessor(beachResolver, statusUseCase, cameraUseCase,
                lifeguardUseCase, jellyfishUseCase, formatter, beachProfileParser,
                logRepository, sender, rateLimiter, singleFlight, telegramProperties, i18n,
                subscriptionService);
        when(beachResolver.listAll()).thenReturn(List.of());
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Update textUpdate(long chatId, long userId, String text) {
        Update update = new Update();
        Message message = new Message();
        message.setChat(new org.telegram.telegrambots.meta.api.objects.Chat(chatId, "private"));
        User user = new User();
        user.setId(userId);
        user.setFirstName("T");
        message.setFrom(user);
        message.setText(text);
        update.setMessage(message);
        return update;
    }

    @Test
    void rateLimitedRequestSendsCooldownReplyAndSkipsHandler() {
        when(rateLimiter.tryAcquire(100L))
                .thenReturn(TelegramRateLimiter.Decision.deny(true));

        processor.processUpdate(textUpdate(100L, 7L, "/status lido"));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(anyLong(), msg.capture());
        assertThat(msg.getValue()).contains("Слишком много запросов");
        verify(statusUseCase, never()).getStatus(anyString());

        ArgumentCaptor<BotInteractionLogEntity> logged = ArgumentCaptor.forClass(BotInteractionLogEntity.class);
        verify(logRepository).save(logged.capture());
        assertThat(logged.getValue().getResponseStatus()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void rateLimitedWithoutCooldownSkipsReply() {
        when(rateLimiter.tryAcquire(100L))
                .thenReturn(TelegramRateLimiter.Decision.deny(false));

        processor.processUpdate(textUpdate(100L, 7L, "/status lido"));

        verify(sender, never()).sendText(anyLong(), anyString());
    }

    @Test
    void unknownBeachForStatusSendsNotFoundMessage() {
        when(rateLimiter.tryAcquire(anyLong()))
                .thenReturn(TelegramRateLimiter.Decision.grant());
        when(statusUseCase.getStatus("nowhere"))
                .thenThrow(new BeachNotFoundException("nowhere"));

        processor.processUpdate(textUpdate(50L, 1L, "/status nowhere"));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(anyLong(), msg.capture());
        assertThat(msg.getValue()).contains("не найден");
    }

    @Test
    void handlerExceptionProducesGenericErrorReply() {
        when(rateLimiter.tryAcquire(anyLong()))
                .thenReturn(TelegramRateLimiter.Decision.grant());
        when(statusUseCase.getStatus("lido")).thenThrow(new RuntimeException("boom"));

        processor.processUpdate(textUpdate(50L, 1L, "/status lido"));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(anyLong(), msg.capture());
        assertThat(msg.getValue()).contains("Что-то пошло не так");
        assertThat(msg.getValue()).contains("код:");

        ArgumentCaptor<BotInteractionLogEntity> logged = ArgumentCaptor.forClass(BotInteractionLogEntity.class);
        verify(logRepository).save(logged.capture());
        assertThat(logged.getValue().getResponseStatus()).isEqualTo("ERROR");
    }

    @Test
    void transientDomainExceptionGetsTransientReplyAndStatus() {
        when(rateLimiter.tryAcquire(anyLong()))
                .thenReturn(TelegramRateLimiter.Decision.grant());
        when(statusUseCase.getStatus("lido")).thenThrow(
                new com.beachassistant.common.exception.SourceFetchException(
                        com.beachassistant.common.enums.SourceType.SEA_FORECAST, "upstream down"));

        processor.processUpdate(textUpdate(50L, 1L, "/status lido"));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(anyLong(), msg.capture());
        assertThat(msg.getValue()).contains("временно");

        ArgumentCaptor<BotInteractionLogEntity> logged = ArgumentCaptor.forClass(BotInteractionLogEntity.class);
        verify(logRepository).save(logged.capture());
        assertThat(logged.getValue().getResponseStatus()).isEqualTo("TRANSIENT");
    }

    @Test
    void singleFlightGuardBlocksConcurrentDuplicate() {
        when(rateLimiter.tryAcquire(anyLong()))
                .thenReturn(TelegramRateLimiter.Decision.grant());

        // Simulate one still-in-flight handler by manually acquiring the gate:
        singleFlight.tryBegin(42L);

        processor.processUpdate(textUpdate(42L, 1L, "/status lido"));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(anyLong(), msg.capture());
        assertThat(msg.getValue()).contains("Уже обрабатываю");
        verify(statusUseCase, never()).getStatus(anyString());
    }

    @Test
    void unknownInputPromptsHelpMessage() {
        when(rateLimiter.tryAcquire(anyLong()))
                .thenReturn(TelegramRateLimiter.Decision.grant());
        when(beachResolver.resolve(any()))
                .thenThrow(new BeachNotFoundException("gibberish"));

        processor.processUpdate(textUpdate(50L, 1L, "gibberish"));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(anyLong(), msg.capture());
        assertThat(msg.getValue()).contains("/status");
    }
}
