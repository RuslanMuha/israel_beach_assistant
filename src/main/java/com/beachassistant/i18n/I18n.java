package com.beachassistant.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Thin facade around {@link MessageSource} that keeps call sites short and free of noisy
 * default-value boilerplate. Locale is taken from {@link LocaleContextHolder}, which is populated
 * upstream from the Telegram user's {@code language_code}.
 */
@Component
public class I18n {

    private final MessageSource messageSource;

    public I18n(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String t(String key, Object... args) {
        return messageSource.getMessage(key, args, key, currentLocale());
    }

    public String t(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    private static Locale currentLocale() {
        Locale l = LocaleContextHolder.getLocale();
        return l != null ? l : Locale.forLanguageTag("ru");
    }
}
