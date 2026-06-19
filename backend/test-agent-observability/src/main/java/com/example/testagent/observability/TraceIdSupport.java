package com.example.testagent.observability;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * TraceId 生成和入口透传规则。非法入站值会被替换，避免日志和响应头携带不可信内容。
 */
public final class TraceIdSupport {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_BYTES = 16;

    private TraceIdSupport() {
    }

    public static String resolve(String inboundTraceId) {
        if (isValid(inboundTraceId)) {
            return inboundTraceId.trim();
        }
        return generate();
    }

    public static String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder("trace_");
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    public static boolean isValid(String traceId) {
        if (traceId == null) {
            return false;
        }
        String trimmed = traceId.trim();
        if (trimmed.length() < 16 || trimmed.length() > 96 || !trimmed.startsWith("trace_")) {
            return false;
        }
        for (int index = 6; index < trimmed.length(); index++) {
            char value = trimmed.charAt(index);
            if (!isTraceChar(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTraceChar(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9')
                || value == '_'
                || value == '-';
    }
}
