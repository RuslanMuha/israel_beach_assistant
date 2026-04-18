package com.beachassistant.telegram.handler;

import com.beachassistant.app.BeachProfileParser;
import com.beachassistant.app.usecase.BeachResolverUseCase;
import com.beachassistant.app.usecase.BeachStatusUseCase;
import com.beachassistant.app.usecase.CameraUseCase;
import com.beachassistant.app.usecase.JellyfishUseCase;
import com.beachassistant.app.usecase.LifeguardUseCase;
import com.beachassistant.common.exception.BeachAssistantException;
import com.beachassistant.common.exception.BeachNotFoundException;
import com.beachassistant.common.exception.CameraUnavailableException;
import com.beachassistant.common.exception.Transient;
import com.beachassistant.common.exception.UserFacing;
import com.beachassistant.common.util.ChatIdHasher;
import com.beachassistant.common.util.MdcKeys;
import com.beachassistant.config.TelegramProperties;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachProfile;
import com.beachassistant.i18n.I18n;
import com.beachassistant.subscriptions.SubscriptionService;
import com.beachassistant.persistence.entity.BotInteractionLogEntity;
import com.beachassistant.persistence.entity.CameraEndpointEntity;
import com.beachassistant.persistence.repository.BotInteractionLogRepository;
import com.beachassistant.telegram.formatter.ResponseFormatter;
import com.beachassistant.telegram.keyboard.InlineKeyboards;
import com.beachassistant.telegram.outbox.TelegramSender;
import com.beachassistant.telegram.ratelimit.TelegramRateLimiter;
import com.beachassistant.web.dto.JellyfishDto;
import com.beachassistant.web.dto.LifeguardHoursDto;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Telegram-agnostic update handling: routing, use case calls, and outbound messages via
 * {@link TelegramSender}. Rate-limited per chat and guarded by {@link ChatSingleFlightGuard} so
 * duplicate taps do not produce duplicate cards.
 */
@Slf4j
@Service
public class BeachBotUpdateProcessor {

    private final BeachResolverUseCase beachResolver;
    private final BeachStatusUseCase statusUseCase;
    private final CameraUseCase cameraUseCase;
    private final LifeguardUseCase lifeguardUseCase;
    private final JellyfishUseCase jellyfishUseCase;
    private final ResponseFormatter formatter;
    private final BeachProfileParser beachProfileParser;
    private final BotInteractionLogRepository logRepository;
    private final TelegramSender telegramSender;
    private final TelegramRateLimiter rateLimiter;
    private final ChatSingleFlightGuard singleFlightGuard;
    private final TelegramProperties telegramProperties;
    private final I18n i18n;
    private final SubscriptionService subscriptionService;

    public BeachBotUpdateProcessor(BeachResolverUseCase beachResolver,
                                   BeachStatusUseCase statusUseCase,
                                   CameraUseCase cameraUseCase,
                                   LifeguardUseCase lifeguardUseCase,
                                   JellyfishUseCase jellyfishUseCase,
                                   ResponseFormatter formatter,
                                   BeachProfileParser beachProfileParser,
                                   BotInteractionLogRepository logRepository,
                                   TelegramSender telegramSender,
                                   TelegramRateLimiter rateLimiter,
                                   ChatSingleFlightGuard singleFlightGuard,
                                   TelegramProperties telegramProperties,
                                   I18n i18n,
                                   SubscriptionService subscriptionService) {
        this.beachResolver = beachResolver;
        this.statusUseCase = statusUseCase;
        this.cameraUseCase = cameraUseCase;
        this.lifeguardUseCase = lifeguardUseCase;
        this.jellyfishUseCase = jellyfishUseCase;
        this.formatter = formatter;
        this.beachProfileParser = beachProfileParser;
        this.logRepository = logRepository;
        this.telegramSender = telegramSender;
        this.rateLimiter = rateLimiter;
        this.singleFlightGuard = singleFlightGuard;
        this.telegramProperties = telegramProperties;
        this.i18n = i18n;
        this.subscriptionService = subscriptionService;
    }

    public void processUpdate(Update update) {
        long start = System.currentTimeMillis();
        String text = null;
        Long userId = null;
        long chatId = 0;
        User fromUser = null;

        if (update.hasCallbackQuery()) {
            text = update.getCallbackQuery().getData();
            fromUser = update.getCallbackQuery().getFrom();
            userId = fromUser.getId();
            chatId = update.getCallbackQuery().getMessage().getChatId();
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            text = update.getMessage().getText().trim();
            fromUser = update.getMessage().getFrom();
            userId = fromUser.getId();
            chatId = update.getMessage().getChatId();
        }

        if (text == null) {
            return;
        }

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        if (userId != null) {
            MDC.put(MdcKeys.TELEGRAM_USER_ID, ChatIdHasher.hash(userId));
        }
        try {
            setLocaleFromTelegramUser(update);

            TelegramRateLimiter.Decision rateDecision = rateLimiter.tryAcquire(chatId);
            if (!rateDecision.allowed()) {
                if (rateDecision.shouldSendCooldownReply()) {
                    sendText(chatId, i18n.t("error.rate_limited"));
                }
                logInteraction(userId, text, chatId, "RATE_LIMITED", System.currentTimeMillis() - start);
                return;
            }

            if (!singleFlightGuard.tryBegin(chatId)) {
                sendText(chatId, i18n.t("error.in_flight"));
                logInteraction(userId, text, chatId, "IN_FLIGHT", System.currentTimeMillis() - start);
                return;
            }

            String responseStatus = "OK";
            try {
                handleText(chatId, text, fromUser);
            } catch (BeachAssistantException domain) {
                responseStatus = classify(domain);
                sendText(chatId, renderDomainError(domain, requestId));
                log.warn("Domain error handling update chat={} code={} msg={}",
                        ChatIdHasher.hash(chatId), domain.getErrorCode(), domain.getMessage());
            } catch (Exception e) {
                responseStatus = "ERROR";
                log.error("Unhandled error handling update chat={}", ChatIdHasher.hash(chatId), e);
                sendText(chatId, i18n.t("error.generic", requestId));
            } finally {
                singleFlightGuard.end(chatId);
            }

            logInteraction(userId, text, chatId, responseStatus, System.currentTimeMillis() - start);
        } finally {
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.TELEGRAM_USER_ID);
        }
    }

    /** Derives interaction outcome tag from the exception marker interfaces. */
    private static String classify(BeachAssistantException e) {
        if (e instanceof Transient) {
            return "TRANSIENT";
        }
        if (e instanceof UserFacing) {
            return "USER_FACING";
        }
        return "ERROR";
    }

    private String renderDomainError(BeachAssistantException e, String requestId) {
        if (e instanceof Transient) {
            return i18n.t("error.transient", requestId);
        }
        if (e instanceof UserFacing) {
            return i18n.t("error.user_facing_prefix") + " " + e.getMessage();
        }
        return i18n.t("error.generic", requestId);
    }

    private void setLocaleFromTelegramUser(Update update) {
        User user = update.hasMessage() && update.getMessage().getFrom() != null
                ? update.getMessage().getFrom()
                : (update.hasCallbackQuery() ? update.getCallbackQuery().getFrom() : null);
        if (user == null || user.getLanguageCode() == null || user.getLanguageCode().isBlank()) {
            return;
        }
        try {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(
                    java.util.Locale.forLanguageTag(user.getLanguageCode()));
        } catch (Exception ignored) {
            // non-critical
        }
    }

    private void handleText(long chatId, String text, User fromUser) {
        if ("CITY_SELECT".equals(text)) {
            showCitySelection(chatId);
            return;
        }

        if (text.startsWith("CITY_BEACHES:")) {
            showBeachesForCity(chatId, text.substring("CITY_BEACHES:".length()));
            return;
        }

        if (text.startsWith("CITY:")) {
            showBeachesForCity(chatId, text.substring("CITY:".length()));
            return;
        }

        if (text.startsWith("BEACH:")) {
            showBeachActions(chatId, text.substring("BEACH:".length()));
            return;
        }

        String command = extractCommand(text);
        String arg = extractArgSafe(text);

        if ("/start".equals(command)) {
            sendText(chatId, formatter.formatWelcome(beachResolver.listAll()));
            showCitySelection(chatId);
            return;
        }

        if ("/beaches".equals(command)) {
            showCitySelection(chatId);
            return;
        }

        if ("/status".equals(command)) {
            if (arg == null || arg.isBlank()) {
                sendTextWithKeyboard(chatId,
                        "Сначала выбери пляж, затем нажми Обновить.",
                        InlineKeyboards.beachSelectionButtons(beachResolver.listAll()));
                return;
            }
            handleStatus(chatId, arg);
            return;
        }

        if ("/details".equals(command)) {
            if (arg == null || arg.isBlank()) {
                sendTextWithKeyboard(chatId,
                        "Сначала выбери пляж, затем нажми Подробнее.",
                        InlineKeyboards.beachSelectionButtons(beachResolver.listAll()));
                return;
            }
            handleDetails(chatId, arg);
            return;
        }

        if ("/hours".equals(command)) {
            if (arg == null || arg.isBlank()) {
                sendTextWithKeyboard(chatId,
                        "Сначала выбери пляж, затем нажми Часы.",
                        InlineKeyboards.beachSelectionButtons(beachResolver.listAll()));
                return;
            }
            LifeguardHoursDto dto = lifeguardUseCase.getHours(arg);
            sendText(chatId, formatter.formatHours(dto));
            return;
        }

        if ("/jellyfish".equals(command)) {
            if (arg == null || arg.isBlank()) {
                sendTextWithKeyboard(chatId,
                        "Сначала выбери пляж, затем нажми Медузы.",
                        InlineKeyboards.beachSelectionButtons(beachResolver.listAll()));
                return;
            }
            JellyfishDto dto = jellyfishUseCase.getJellyfish(arg);
            sendText(chatId, formatter.formatJellyfish(dto));
            return;
        }

        if ("/live".equals(command)) {
            if (arg == null || arg.isBlank()) {
                sendTextWithKeyboard(chatId,
                        "Сначала выбери пляж, затем нажми Live.",
                        InlineKeyboards.beachSelectionButtons(beachResolver.listAll()));
                return;
            }
            handleLive(chatId, arg);
            return;
        }

        if ("/cam".equals(command)) {
            if (arg == null || arg.isBlank()) {
                sendTextWithKeyboard(chatId,
                        "Сначала выбери пляж, затем нажми Камера.",
                        InlineKeyboards.beachSelectionButtons(beachResolver.listAll()));
                return;
            }
            handleCam(chatId, arg);
            return;
        }

        if (telegramProperties.getFeatures().isSubscriptionsEnabled()) {
            if ("/subscribe".equals(command)) {
                handleSubscribe(chatId, fromUser, arg);
                return;
            }
            if ("/unsubscribe".equals(command)) {
                handleUnsubscribe(chatId, fromUser, arg);
                return;
            }
            if ("/mysubs".equals(command)) {
                handleMySubs(chatId, fromUser);
                return;
            }
            if ("/digest".equals(command)) {
                handleDigest(chatId, fromUser, arg);
                return;
            }
        }

        if (!text.startsWith("/")) {
            try {
                String possibleBeach = text.toLowerCase();
                beachResolver.resolve(possibleBeach);
                handleStatus(chatId, possibleBeach);
                return;
            } catch (BeachNotFoundException ignored) {
                // Not a known beach alias
            }
        }

        sendText(chatId, i18n.t("command.unknown"));
    }

    private void handleStatus(long chatId, String beach) {
        try {
            telegramSender.sendChatAction(chatId, ActionType.TYPING);
            BeachDecision decision = statusUseCase.getStatus(beach);
            var resolvedBeach = beachResolver.resolve(decision.getBeachSlug());
            boolean hasCamera = resolvedBeach.isHasCamera();
            BeachProfile profile = beachProfileParser.parse(resolvedBeach);
            String message = formatter.formatStatus(decision, profile, hasCamera);
            telegramSender.sendText(chatId, message, actionButtons(resolvedBeach.getSlug(),
                    resolvedBeach.getCity().getName(), hasCamera));
        } catch (BeachNotFoundException e) {
            sendText(chatId, "Пляж «" + beach + "» не найден. Используй /beaches для списка.");
        }
    }

    private void handleDetails(long chatId, String beach) {
        try {
            telegramSender.sendChatAction(chatId, ActionType.TYPING);
            BeachDecision decision = statusUseCase.getStatus(beach);
            var resolvedBeach = beachResolver.resolve(decision.getBeachSlug());
            boolean hasCamera = resolvedBeach.isHasCamera();
            BeachProfile profile = beachProfileParser.parse(resolvedBeach);
            String message = formatter.formatStatusDetails(decision, profile);
            telegramSender.sendText(chatId, message, actionButtons(resolvedBeach.getSlug(),
                    resolvedBeach.getCity().getName(), hasCamera));
        } catch (BeachNotFoundException e) {
            sendText(chatId, "Пляж «" + beach + "» не найден. Используй /beaches для списка.");
        }
    }

    private void handleLive(long chatId, String beach) {
        try {
            CameraEndpointEntity camera = cameraUseCase.getActiveCamera(beach);
            if ("UNREACHABLE".equalsIgnoreCase(camera.getHealthStatus())) {
                var beachEntity = beachResolver.resolve(beach);
                sendTextWithKeyboard(chatId,
                        formatter.formatCameraTemporarilyUnavailable(beach),
                        InlineKeyboards.cameraRetryButtons(
                                beachEntity.getSlug(),
                                beachEntity.getCity().getName()));
                return;
            }
            var beachEntity = beachResolver.resolve(beach);
            sendTextWithKeyboard(chatId,
                    formatter.formatCameraLive(beach, camera.getLiveUrl(), camera.getHealthStatus()),
                    actionButtons(beachEntity.getSlug(), beachEntity.getCity().getName(), true));
        } catch (CameraUnavailableException e) {
            sendText(chatId, formatter.formatCameraUnavailable(beach));
        } catch (BeachNotFoundException e) {
            sendText(chatId, "Пляж «" + beach + "» не найден.");
        }
    }

    private void handleCam(long chatId, String beach) {
        try {
            var snapshot = cameraUseCase.getLatestSnapshot(beach);
            if (snapshot.isPresent() && snapshot.get().getStorageUrl() != null) {
                var beachEntity = beachResolver.resolve(beach);
                InlineKeyboardMarkup buttons = actionButtons(
                        beachEntity.getSlug(), beachEntity.getCity().getName(), true);
                telegramSender.sendPhoto(chatId,
                        snapshot.get().getStorageUrl(),
                        "📷 " + beachEntity.getDisplayName(),
                        buttons);
            } else {
                CameraEndpointEntity camera = cameraUseCase.getActiveCamera(beach);
                if ("UNREACHABLE".equalsIgnoreCase(camera.getHealthStatus())) {
                    var beachEntity = beachResolver.resolve(beach);
                    sendTextWithKeyboard(chatId,
                            formatter.formatCameraTemporarilyUnavailable(beach),
                            InlineKeyboards.cameraRetryButtons(
                                    beachEntity.getSlug(),
                                    beachEntity.getCity().getName()));
                    return;
                }
                var beachEntity = beachResolver.resolve(beach);
                sendTextWithKeyboard(chatId,
                        "Снимок сейчас недоступен, открываю live-ссылку:\n\n"
                                + formatter.formatCameraLive(beach, camera.getLiveUrl(), camera.getHealthStatus()),
                        actionButtons(beachEntity.getSlug(), beachEntity.getCity().getName(), true));
            }
        } catch (CameraUnavailableException e) {
            sendText(chatId, formatter.formatCameraUnavailable(beach));
        } catch (BeachNotFoundException e) {
            sendText(chatId, "Пляж «" + beach + "» не найден.");
        }
    }

    private void handleSubscribe(long chatId, User fromUser, String arg) {
        if (fromUser == null) {
            return;
        }
        if (arg == null || arg.isBlank()) {
            sendText(chatId, "Использование: /subscribe <slug>");
            return;
        }
        try {
            String lang = fromUser.getLanguageCode();
            SubscriptionService.Result result = subscriptionService.subscribe(
                    fromUser.getId(), chatId, lang, arg.trim().toLowerCase());
            if (result.created()) {
                sendText(chatId, "✅ Подписка оформлена: " + result.beach().getDisplayName());
            } else {
                sendText(chatId, "ℹ️ Вы уже подписаны на " + result.beach().getDisplayName());
            }
        } catch (BeachNotFoundException e) {
            sendText(chatId, "Пляж «" + arg + "» не найден.");
        }
    }

    private void handleUnsubscribe(long chatId, User fromUser, String arg) {
        if (fromUser == null) {
            return;
        }
        if (arg == null || arg.isBlank()) {
            sendText(chatId, "Использование: /unsubscribe <slug>");
            return;
        }
        boolean removed = subscriptionService.unsubscribe(fromUser.getId(), arg.trim().toLowerCase());
        sendText(chatId, removed ? "🗑️ Подписка удалена." : "Подписка не найдена.");
    }

    private void handleMySubs(long chatId, User fromUser) {
        if (fromUser == null) {
            return;
        }
        var subs = subscriptionService.subscriptionsFor(fromUser.getId());
        if (subs.isEmpty()) {
            sendText(chatId, "У вас пока нет подписок. Оформите через /subscribe <slug>.");
            return;
        }
        StringBuilder sb = new StringBuilder("Ваши подписки:\n");
        subs.forEach(s -> sb.append("• ID пляжа ").append(s.getBeachId()).append('\n'));
        sendText(chatId, sb.toString());
    }

    private void handleDigest(long chatId, User fromUser, String arg) {
        if (fromUser == null) {
            return;
        }
        boolean on = arg != null && (arg.equalsIgnoreCase("on") || arg.equalsIgnoreCase("вкл"));
        boolean off = arg != null && (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("выкл"));
        if (!on && !off) {
            sendText(chatId, "Использование: /digest on | off");
            return;
        }
        subscriptionService.setDigestEnabled(fromUser.getId(), chatId, fromUser.getLanguageCode(), on);
        sendText(chatId, on ? "🌅 Утренний дайджест включён." : "Утренний дайджест выключен.");
    }

    private InlineKeyboardMarkup actionButtons(String slug, String cityName, boolean hasCamera) {
        boolean subscriptionsEnabled = telegramProperties.getFeatures().isSubscriptionsEnabled();
        return InlineKeyboards.beachActionButtons(slug, cityName, hasCamera, subscriptionsEnabled);
    }

    private void sendText(long chatId, String text) {
        telegramSender.sendText(chatId, text);
    }

    private void sendTextWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        telegramSender.sendText(chatId, text, keyboard);
    }

    private void showBeachActions(long chatId, String slug) {
        handleStatus(chatId, slug);
    }

    private void showCitySelection(long chatId) {
        List<String> cities = beachResolver.listAll().stream()
                .map(b -> b.getCity().getName())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
        sendTextWithKeyboard(chatId, "Выберите город 👇", InlineKeyboards.citySelectionButtons(cities));
    }

    private void showBeachesForCity(long chatId, String cityName) {
        List<com.beachassistant.persistence.entity.BeachEntity> beaches = beachResolver.listAll().stream()
                .filter(b -> b.getCity().getName().equalsIgnoreCase(cityName))
                .toList();
        if (beaches.isEmpty()) {
            showCitySelection(chatId);
            return;
        }
        sendTextWithKeyboard(chatId,
                "Город: " + cityName + "\nВыберите пляж 👇",
                InlineKeyboards.beachSelectionButtons(beaches));
    }

    private String extractCommand(String text) {
        if (text == null || !text.startsWith("/")) {
            return null;
        }
        String firstToken = text.split("\\s+", 2)[0];
        int at = firstToken.indexOf('@');
        return (at >= 0 ? firstToken.substring(0, at) : firstToken).toLowerCase();
    }

    private String extractArgSafe(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : "";
    }

    private void logInteraction(Long userId, String requestType, long chatId,
                                String status, long latencyMs) {
        try {
            BotInteractionLogEntity log = new BotInteractionLogEntity();
            log.setTelegramUserId(userId);
            log.setRequestType(requestType != null && requestType.length() > 50
                    ? requestType.substring(0, 50) : requestType);
            log.setRequestedAt(ZonedDateTime.now());
            log.setResponseStatus(status);
            log.setLatencyMs(latencyMs);
            logRepository.save(log);
        } catch (Exception e) {
            // Non-critical: logging should never crash the handler
        }
    }
}
