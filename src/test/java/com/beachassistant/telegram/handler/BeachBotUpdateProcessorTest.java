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
import com.beachassistant.subscriptions.SubscriptionService;
import com.beachassistant.persistence.entity.BotInteractionLogEntity;
import com.beachassistant.persistence.repository.BotInteractionLogRepository;
import com.beachassistant.telegram.formatter.ResponseFormatter;
import com.beachassistant.telegram.outbox.TelegramSender;
import com.beachassistant.telegram.ratelimit.TelegramRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;

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
        I18n i18n = new I18n(buildMessageSource());
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        processor = new BeachBotUpdateProcessor(beachResolver, statusUseCase, cameraUseCase,
                lifeguardUseCase, jellyfishUseCase, formatter, beachProfileParser,
                logRepository, sender, rateLimiter, singleFlight, telegramProperties, i18n,
                subscriptionService);
        when(beachResolver.listAll()).thenReturn(List.of());
    }

    private static org.springframework.context.MessageSource buildMessageSource() {
        final java.util.Map<String, String> msgs = new java.util.HashMap<>();
        msgs.put("error.generic", "Что-то пошло не так. Попробуйте снова через минуту. (код: {0})");
        msgs.put("error.transient", "\u26A0 Внешний источник временно недоступен. (код: {0})");
        msgs.put("error.user_facing_prefix", "\u26A0");
        msgs.put("error.in_flight", "\u23F3 Уже обрабатываю предыдущий запрос.");
        msgs.put("error.rate_limited", "\u23F3 Слишком много запросов.");
        msgs.put("command.unknown", "Не понял запрос. Попробуй /status или /beaches.");
        return new org.springframework.context.support.AbstractMessageSource() {
            @Override
            protected java.text.MessageFormat resolveCode(String code, java.util.Locale locale) {
                String t = msgs.get(code);
                return t == null ? null : new java.text.MessageFormat(t, locale);
            }
        };
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
