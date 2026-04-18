package com.beachassistant.telegram.keyboard;

import com.beachassistant.i18n.I18n;
import com.beachassistant.persistence.entity.BeachEntity;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public final class InlineKeyboards {

    private InlineKeyboards() {
    }

    public static InlineKeyboardMarkup beachActionButtons(I18n i18n, String slug, String cityName, boolean hasCamera) {
        return beachActionButtons(i18n, slug, cityName, hasCamera, false);
    }

    /**
     * @param subscriptionsEnabled when {@code true}, exposes the subscribe action so users can subscribe to alerts.
     */
    public static InlineKeyboardMarkup beachActionButtons(I18n i18n, String slug, String cityName, boolean hasCamera,
                                                          boolean subscriptionsEnabled) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button(i18n.t("keyboard.refresh"), "/status " + slug),
                        button(i18n.t("keyboard.details"), "/details " + slug)))
                .keyboardRow(List.of(
                        button(i18n.t("keyboard.hours"), "/hours " + slug),
                        button(i18n.t("keyboard.jellyfish"), "/jellyfish " + slug)));
        if (hasCamera) {
            builder.keyboardRow(List.of(
                    button(i18n.t("keyboard.live"), "/live " + slug),
                    button(i18n.t("keyboard.camera"), "/cam " + slug)));
        }
        if (subscriptionsEnabled) {
            builder.keyboardRow(List.of(
                    button(i18n.t("keyboard.subscribe"), "/subscribe " + slug)));
        }
        builder.keyboardRow(List.of(
                button(i18n.t("keyboard.change_beach"), "CITY_BEACHES:" + cityName),
                button(i18n.t("keyboard.change_city"), "CITY_SELECT")
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

    public static InlineKeyboardMarkup cameraRetryButtons(I18n i18n, String slug, String cityName) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button(i18n.t("keyboard.retry_live"), "/live " + slug)))
                .keyboardRow(List.of(
                        button(i18n.t("keyboard.change_beach"), "CITY_BEACHES:" + cityName),
                        button(i18n.t("keyboard.change_city"), "CITY_SELECT")
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
