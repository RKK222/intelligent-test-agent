package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.Map;

/** 应用源码操作统一标识；所有 REST、ticket、WebSocket 与业务命令共用同一可路由边界。 */
public record AppSourceOperationId(String value) {

    public static final int MAX_LENGTH = 128;

    public AppSourceOperationId {
        String original = value;
        value = value == null ? "" : value.trim();
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
