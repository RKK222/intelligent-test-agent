package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.Map;

/** 应用源码操作统一标识；所有 REST、ticket、WebSocket 与业务命令共用同一可路由边界。 */
public record AppSourceOperationId(String value) {

    public static final int MAX_LENGTH = 128;

    public AppSourceOperationId {
        String original = value;
        value = value == null ? "" : trimEcmaScriptWhitespace(value);
        if (value.isEmpty()
                || value.length() > MAX_LENGTH
                || ".".equals(value)
                || "..".equals(value)
                || containsUnsafePathCharacter(value)) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "operationId 格式无效",
                    Map.of("operationId", original == null ? "" : original));
        }
    }

    /** 返回规范化字符串，便于仍以字符串持久化的端口复用相同校验。 */
    public static String normalize(String value) {
        return new AppSourceOperationId(value).value();
    }

    /**
     * 与 ECMAScript TrimString 对齐，显式固定 WhiteSpace 与 LineTerminator 集合，避免 Java 版本或 locale 差异。
     */
    private static String trimEcmaScriptWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isEcmaScriptWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isEcmaScriptWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isEcmaScriptWhitespace(char character) {
        return switch (character) {
            // 使用数值常量，避免 Java 在词法分析前展开 Unicode 行终止符转义。
            case 0x0009, 0x000A, 0x000B, 0x000C, 0x000D,
                    0x0020, 0x00A0, 0x1680, 0x2028, 0x2029,
                    0x202F, 0x205F, 0x3000, 0xFEFF -> true;
            default -> character >= 0x2000 && character <= 0x200A;
        };
    }

    private static boolean containsUnsafePathCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            // operationId 同时是 URL path segment；控制字符与路径分隔符不能被任何入口接受。
            if (Character.isISOControl(character) || character == '/' || character == '\\') {
                return true;
            }
        }
        return false;
    }
}
