package com.example.testagent.observability;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * TraceId 生成和入口透传规则。非法入站值会被替换，避免日志和响应头携带不可信内容。
 */
public final class TraceIdSupport {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_BYTES = 16;

    /**
     * 工具类不允许实例化，traceId 解析和生成都通过静态方法完成。
     */
    private TraceIdSupport() {
    }

    /**
     * 解析入口传入的 traceId；合法值透传并去除首尾空白，缺失或非法值生成新的安全 traceId。
     */
    public static String resolve(String inboundTraceId) {
        if (isValid(inboundTraceId)) {
            return inboundTraceId.trim();
        }
        return generate();
    }

    /**
     * 生成平台 traceId，固定使用 `trace_` 前缀并追加 16 字节随机十六进制内容。
     */
    public static String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder("trace_");
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    /**
     * 校验 traceId 是否可透传到响应头和日志；只允许稳定前缀、长度边界和安全字符集。
     */
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

    /**
     * 判断单个 traceId 字符是否安全，避免斜杠、空白和控制字符进入日志或响应头。
     */
    private static boolean isTraceChar(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9')
                || value == '_'
                || value == '-';
    }
}
