package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 记忆正文统一安全边界：阻止密钥、控制字符和明显的指令注入被长期复用。 */
@Component
public class MemorySafetyPolicy {
    private static final int MAX_MEMORY_CODE_POINTS = 2_000;
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?is)(?:password|passwd|api[_-]?key|access[_-]?token|secret)\\s*[:=]\\s*[^\\s,;]{6,}");
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    private static final Pattern PROMPT_OVERRIDE = Pattern.compile(
            "(?is)(?:忽略|覆盖|绕过).{0,20}(?:系统|上文|之前).{0,20}(?:指令|规则)|ignore.{0,20}(?:system|previous).{0,20}instructions");

    public String requireSafeContent(String value) {
        if (value == null || value.isBlank()) {
            throw validation("记忆内容不能为空");
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > MAX_MEMORY_CODE_POINTS) {
            throw validation("单条记忆不能超过 2000 字");
        }
        if (normalized.chars().anyMatch(ch -> ch == 0 || ch < 0x20 && ch != '\n' && ch != '\r' && ch != '\t')) {
            throw validation("记忆内容包含不允许的控制字符");
        }
        if (SECRET_ASSIGNMENT.matcher(normalized).find() || PRIVATE_KEY.matcher(normalized).find()) {
            throw validation("记忆内容疑似包含凭据或私钥");
        }
        if (PROMPT_OVERRIDE.matcher(normalized).find()) {
            throw validation("记忆内容包含不允许长期复用的指令覆盖语句");
        }
        return normalized;
    }

    public String displaySummary(String content) {
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return truncate(oneLine, 500);
    }

    public String evidenceSummary(String content) {
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return truncate(oneLine, 200);
    }

    private String truncate(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints - 1);
        return value.substring(0, end) + "…";
    }

    private PlatformException validation(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }
}
