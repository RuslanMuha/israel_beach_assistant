package com.beachassistant.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

/**
 * Wires a {@link MessageSource} reading from {@code classpath:i18n/messages[_locale].properties}.
 * Uses UTF-8 so Cyrillic/Hebrew strings stay intact and disables cache in tests-friendly default.
 */
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/messages");
        ms.setDefaultEncoding(StandardCharsets.UTF_8.name());
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(true);
        ms.setCacheSeconds(60);
        return ms;
    }
}
