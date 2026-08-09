package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 记忆正文只做结构安全校验；语义取舍完全交给 Mem0 原生能力和人工治理。 */
@Component
public class MemorySafetyPolicy {
    private static final int MAX_MEMORY_CODE_POINTS = 8_000;
    private static final int MAX_SKILL_DRAFT_CODE_POINTS = 100_000;
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?is)(?:password|passwd|api[_-]?key|access[_-]?token|secret)\\s*[:=]\\s*[^\\s,;]{6,}");
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    private static final Pattern PROMPT_OVERRIDE = Pattern.compile(
            "(?is)(?:忽略|覆盖|绕过).{0,20}(?:系统|上文|之前).{0,20}(?:指令|规则)|ignore.{0,20}(?:system|previous).{0,20}instructions");

    public String requireSafeContent(String value) {
        if (value == null || value.isBlank()) {
            throw validation("记忆内容不能为空");
        }
        if (value.codePointCount(0, value.length()) > MAX_MEMORY_CODE_POINTS) {
            throw validation("单条记忆不能超过 8000 字");
        }
        if (hasForbiddenControlCharacter(value)) {
            throw validation("记忆内容包含不允许的控制字符");
        }
        // 不再判断 QA 类型、临时性、置信度、凭据语义或提示词语义，保持 Mem0 结果原样。
        return value;
    }

    /** Skill 草稿允许完整方法说明，但沿用凭据、控制字符和提示覆盖防护。 */
    public String requireSafeSkillDraft(String value) {
        return requireSafe(value, MAX_SKILL_DRAFT_CODE_POINTS,
                "SKILL.md 草稿不能为空", "SKILL.md 草稿不能超过 100000 字");
    }

    private String requireSafe(String value, int maxCodePoints, String emptyMessage, String lengthMessage) {
        if (value == null || value.isBlank()) {
            throw validation(emptyMessage);
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw validation(lengthMessage);
        }
        if (hasForbiddenControlCharacter(normalized)) {
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

    private boolean hasForbiddenControlCharacter(String value) {
        return value.chars().anyMatch(ch -> ch == 0 || ch < 0x20 && ch != '\n' && ch != '\r' && ch != '\t');
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
