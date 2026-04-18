package com.beachassistant.telegram.keyboard;

import com.beachassistant.persistence.entity.BeachEntity;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public final class InlineKeyboards {

    private InlineKeyboards() {
    }

    public static InlineKeyboardMarkup beachActionButtons(String slug, String cityName, boolean hasCamera) {
        return beachActionButtons(slug, cityName, hasCamera, false);
    }

    /**
     * @param subscriptionsEnabled when {@code true}, exposes the "Подписаться" action so users can subscribe to alerts.
     */
    public static InlineKeyboardMarkup beachActionButtons(String slug, String cityName, boolean hasCamera,
                                                          boolean subscriptionsEnabled) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("🔄 Обновить", "/status " + slug),
                        button("📋 Подробнее", "/details " + slug)))
                .keyboardRow(List.of(
                        button("🏊 Часы", "/hours " + slug),
                        button("🪼 Медузы", "/jellyfish " + slug)));
        if (hasCamera) {
            builder.keyboardRow(List.of(
                    button("🔴 Live", "/live " + slug),
                    button("📷 Камера", "/cam " + slug)));
        }
        if (subscriptionsEnabled) {
            builder.keyboardRow(List.of(
                    button("🔔 Подписаться", "/subscribe " + slug)));
        }
        builder.keyboardRow(List.of(
                button("🏖 Сменить пляж", "CITY_BEACHES:" + cityName),
                button("🏙 Сменить город", "CITY_SELECT")
        ));
        return builder.build();
    }

    public static InlineKeyboardMarkup beachSelectionButtons(List<BeachEntity> beaches) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (BeachEntity beach : beaches) {
            rows.add(List.of(button(beach.getDisplayName(), "BEACH:" + beach.getSlug())));
        }
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup citySelectionButtons(List<String> cities) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String city : cities) {
            rows.add(List.of(button(city, "CITY:" + city)));
        }
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup cameraRetryButtons(String slug, String cityName) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("🔄 Проверить снова", "/live " + slug)))
                .keyboardRow(List.of(
                        button("🏖 Сменить пляж", "CITY_BEACHES:" + cityName),
                        button("🏙 Сменить город", "CITY_SELECT")
                ))
                .build();
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}
