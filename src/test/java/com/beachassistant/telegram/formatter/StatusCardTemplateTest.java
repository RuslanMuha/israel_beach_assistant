package com.beachassistant.telegram.formatter;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCardTemplateTest {

    @Test
    void format_matchesCompactCardLayout() {
        StatusCardModel model = new StatusCardModel(
                "Lido",
                "Ashdod",
                "⚠️",
                "Осторожно",
                "🟢 свежо",
                "Купание без спасателей не рекомендуется",
                "жёлтый",
                "не дежурят",
                "0.9 м",
                "18.5°C",
                "16.8°C",
                "0.9 м/с, СЗ",
                "низкий",
                "family, urban",
                "душ, туалет, спорт, парковка",
                "доступна",
                "20:31",
                Optional.of("Данные: муниципалитет + meteo")
        );
        String text = StatusCardTemplate.format(model);
        assertThat(text).isEqualTo("""
                Lido (Ashdod)
                ⚠️ Осторожно  🟢 свежо
                Купание без спасателей не рекомендуется

                Сейчас:
                - Флаг: жёлтый
                - Спасатели: не дежурят
                - Волна: 0.9 м
                - Вода: 18.5°C

                Условия:
                - Воздух: 16.8°C
                - Ветер: 0.9 м/с, СЗ
                - UV: низкий

                Пляж:
                - Тип: family, urban
                - Удобства: душ, туалет, спорт, парковка
                - Камера: доступна

                Обновлено: 20:31
                Данные: муниципалитет + meteo""");
    }

    @Test
    void format_omitsEmptySections() {
        StatusCardModel model = new StatusCardModel(
                "X",
                "Y",
                "❓",
                "Неизвестно",
                null,
                "Недостаточно данных для уверенной оценки.",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "12:00",
                Optional.empty()
        );
        String text = StatusCardTemplate.format(model);
        assertThat(text).doesNotContain("Сейчас:");
        assertThat(text).doesNotContain("Условия:");
        assertThat(text).doesNotContain("Пляж:");
    }
}
