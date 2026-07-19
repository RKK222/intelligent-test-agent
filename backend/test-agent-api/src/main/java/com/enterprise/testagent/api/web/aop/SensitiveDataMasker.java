package com.enterprise.testagent.api.web.aop;

import java.util.Set;

/**
 * 敏感数据脱敏工具，用于日志输出前对敏感字段进行脱敏处理。
 *
 * <p>脱敏规则：
 * <ul>
 *   <li>password、token、secret 等敏感字段值替换为 "***"</li>
 *   <li>超长字符串截断到 2000 字符</li>
 * </ul>
 */
public final class SensitiveDataMasker {

    /** 需要脱敏的字段名（不区分大小写匹配） */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "oldpassword", "newpassword",
            "token", "accesstoken", "refreshtoken", "contexttoken",
            "secret", "apikey", "credential", "authorization",
            "privatekey", "passphrase",
            "sourcevalue", "memoryvalue"
    );

    /** 日志最大长度，超长部分截断 */
    private static final int MAX_BODY_LENGTH = 2000;

    private SensitiveDataMasker() {
    }

    /**
     * 对 JSON 字符串中的敏感字段进行脱敏处理。
     *
     * @param json 原始 JSON 字符串
     * @return 脱敏后的字符串，超长部分已截断
     */
    public static String mask(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        String result = json;

        // 脱敏格式: "fieldName": "value" -> "fieldName": "***"
        for (String field : SENSITIVE_FIELDS) {
            // 匹配双引号包裹的字段名和完整 JSON 字符串值，转义引号不能提前结束匹配。
            result = result.replaceAll(
                    "(?i)(\"" + field + "\"\\s*:\\s*\")(?:\\\\.|[^\"\\\\])*\"",
                    "$1***\""
            );
        }

        // 截断过长的日志
        if (result.length() > MAX_BODY_LENGTH) {
            return result.substring(0, MAX_BODY_LENGTH) + "...(truncated)";
        }
        return result;
    }

    /**
     * 脱敏 Authorization 请求头值。
     *
     * @param authHeader 原始 Authorization 头值
     * @return 脱敏后的值，如 "Bearer ***"
     */
    public static String maskAuthHeader(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return authHeader;
        }
        if (authHeader.toLowerCase().startsWith("bearer ")) {
            return "Bearer ***";
        }
        if (authHeader.toLowerCase().startsWith("basic ")) {
            return "Basic ***";
        }
        return "***";
    }

    /**
     * 截断超长字符串，用于非 JSON 内容的日志输出。
     *
     * @param content 原始内容
     * @return 截断后的内容
     */
    public static String truncate(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() > MAX_BODY_LENGTH) {
            return content.substring(0, MAX_BODY_LENGTH) + "...(truncated)";
        }
        return content;
    }
}
