package com.beachassistant.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministically hashes Telegram identifiers (chatId, userId) for log output so operators can
 * correlate across log lines without storing raw identifiers in log aggregation systems. Uses a
 * process-wide random salt unless {@code BEACH_LOG_ID_SALT} overrides it; without a salt hashes
 * are still stable across a single JVM run which is enough for correlation within one deploy.
 */
public final class ChatIdHasher {

    private static final String SALT = resolveSalt();

    private ChatIdHasher() {
    }

    public static String hash(long id) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SALT.getBytes(StandardCharsets.UTF_8));
            md.update(Long.toString(id).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest()).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return Long.toHexString(id);
        }
    }

    private static String resolveSalt() {
        String env = System.getenv("BEACH_LOG_ID_SALT");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return "beach-log-" + java.util.UUID.randomUUID();
    }
}
