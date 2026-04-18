package com.beachassistant.i18n;

import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

/**
 * Test helper: {@link I18n} backed by the same bundles as production.
 */
public final class I18nTestConfig {

    private I18nTestConfig() {
    }

    public static I18n ru() {
        return new I18n(messageSource());
    }

    public static org.springframework.context.MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/messages");
        ms.setDefaultEncoding(StandardCharsets.UTF_8.name());
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(true);
        return ms;
    }
}
