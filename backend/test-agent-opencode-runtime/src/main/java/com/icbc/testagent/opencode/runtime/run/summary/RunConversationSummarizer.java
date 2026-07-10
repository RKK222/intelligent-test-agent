package com.icbc.testagent.opencode.runtime.run.summary;

import com.icbc.testagent.domain.session.SessionMessageRole;
import java.text.Normalizer;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 把 Redis 运行态中的可见 USER/ASSISTANT 文本转换为确定性双摘要。
 *
 * <p>清洗顺序固定且不依赖时间、节点或外部模型；任何异常只产生安全 fallback，绝不把原文作为降级结果。
 */
@Component
public class RunConversationSummarizer {

    public static final int SUMMARY_VERSION = 1;
    public static final int USER_MAX_CODE_POINTS = 512;
    public static final int ASSISTANT_MAX_CODE_POINTS = 2_000;

    private static final String USER_FALLBACK = "用户请求摘要不可用";
    private static final String ASSISTANT_FALLBACK = "助手回答摘要不可用";
    private static final String REDACTED = "[REDACTED]";

    private static final String PROTECTED_TAG =
            "context|reasoning|thinking|tool[-_ ]?(?:input|output|result)|attachment(?:s|[-_ ]?body)?|file[-_ ]?content";
    private static final Pattern PROTECTED_XML_BLOCK = Pattern.compile(
            "<\\s*(" + PROTECTED_TAG + ")\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UNCLOSED_PROTECTED_XML_BLOCK = Pattern.compile(
            "<\\s*(?:" + PROTECTED_TAG + ")\\b[^>]*>.*\\z",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PROTECTED_MARKDOWN_FENCE = Pattern.compile(
            "```[\\t ]*(?:" + PROTECTED_TAG + ")\\b[^\\r\\n]*\\R.*?```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PROTECTED_LABELLED_LINE = Pattern.compile(
            "^\\s*(?:" + PROTECTED_TAG + ")\\s*[:=].*(?:\\R|\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern PROTECTED_JSON_STRING_FIELD = Pattern.compile(
            "\"(?:reasoning|thinking|tool[-_ ]?(?:input|output|result)|attachment(?:s|[-_ ]?body)?|file[-_ ]?content)\""
                    + "\\s*:\\s*\"(?:\\\\.|[^\"\\\\])*\"\\s*,?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DATA_URL = Pattern.compile(
            "\\bdata:[^\\s<>\"')\\]]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.*?-----END [A-Z0-9 ]*PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern URI_USER_INFO = Pattern.compile(
            "([a-z][a-z0-9+.-]*://)[^\\s/@:]+:[^\\s/@]+@",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "\\bBearer\\s+[A-Za-z0-9._~+/-]+=*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT_TOKEN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern WELL_KNOWN_SECRET = Pattern.compile(
            "\\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{8,}|gh[pousr]_[A-Za-z0-9]{8,}|AKIA[A-Z0-9]{16})\\b");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?<![A-Za-z0-9])([\"']?[A-Za-z0-9_.-]*"
                    + "(?:password|passwd|pwd|secret|token|api[-_ ]?key|access[-_ ]?key|client[-_ ]?secret|authorization)"
                    + "[\"']?\\s*[:=]\\s*)"
                    + "(?:\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|[^\\s,;&}\\]]+)",
            Pattern.CASE_INSENSITIVE);

    private final UnaryOperator<String> textSanitizer;

    /**
     * 生产构造使用内置确定性清洗规则。
     */
    public RunConversationSummarizer() {
        this(RunConversationSummarizer::sanitizeText);
    }

    /**
     * 包内构造仅用于验证清洗器异常时的安全降级语义。
     */
    RunConversationSummarizer(UnaryOperator<String> textSanitizer) {
        this.textSanitizer = Objects.requireNonNull(textSanitizer, "textSanitizer must not be null");
    }

    /**
     * 为一次 Run 生成固定顺序的 USER/ASSISTANT 双摘要；单侧失败不会泄漏原文，也不会影响另一侧状态判断。
     */
    public RunConversationSummary summarize(String userContent, String assistantContent) {
        return new RunConversationSummary(
                summarizeMessage(SessionMessageRole.USER, userContent, USER_MAX_CODE_POINTS, USER_FALLBACK),
                summarizeMessage(
                        SessionMessageRole.ASSISTANT,
                        assistantContent,
                        ASSISTANT_MAX_CODE_POINTS,
                        ASSISTANT_FALLBACK));
    }

    private RunMessageSummary summarizeMessage(
            SessionMessageRole role,
            String source,
            int maxCodePoints,
            String fallback) {
        try {
            String sanitized = textSanitizer.apply(source == null ? "" : source);
            String normalized = collapseWhitespaceAndRemoveControls(sanitized == null ? "" : sanitized);
            if (normalized.isBlank()) {
                return fallback(role, fallback);
            }
            int codePoints = normalized.codePointCount(0, normalized.length());
            if (codePoints <= maxCodePoints) {
                return summary(role, normalized, RunSummaryStatus.COMPLETE);
            }
            // 末尾省略号计入字符预算，按 code point 定位可避免截断 emoji 的 UTF-16 代理对。
            int end = normalized.offsetByCodePoints(0, maxCodePoints - 1);
            return summary(role, normalized.substring(0, end) + "…", RunSummaryStatus.PARTIAL);
        } catch (RuntimeException ignored) {
            // 异常消息可能携带原文或密钥，因此此处不记录 exception message，只用 FALLBACK 状态向终态投影暴露结果。
            return fallback(role, fallback);
        }
    }

    private RunMessageSummary summary(SessionMessageRole role, String content, RunSummaryStatus status) {
        return new RunMessageSummary(
                role,
                content,
                RunSummaryContentKind.SUMMARY,
                status,
                SUMMARY_VERSION);
    }

    private RunMessageSummary fallback(SessionMessageRole role, String content) {
        return summary(role, content, RunSummaryStatus.FALLBACK);
    }

    private static String sanitizeText(String source) {
        String sanitized = Normalizer.normalize(source, Normalizer.Form.NFKC);
        sanitized = PROTECTED_XML_BLOCK.matcher(sanitized).replaceAll(" ");
        sanitized = UNCLOSED_PROTECTED_XML_BLOCK.matcher(sanitized).replaceAll(" ");
        sanitized = PROTECTED_MARKDOWN_FENCE.matcher(sanitized).replaceAll(" ");
        sanitized = PROTECTED_LABELLED_LINE.matcher(sanitized).replaceAll(" ");
        sanitized = PROTECTED_JSON_STRING_FIELD.matcher(sanitized).replaceAll(" ");
        sanitized = DATA_URL.matcher(sanitized).replaceAll(" ");
        sanitized = PRIVATE_KEY_BLOCK.matcher(sanitized).replaceAll(REDACTED);
        sanitized = URI_USER_INFO.matcher(sanitized).replaceAll("$1" + REDACTED + "@");
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll(REDACTED);
        sanitized = JWT_TOKEN.matcher(sanitized).replaceAll(REDACTED);
        sanitized = WELL_KNOWN_SECRET.matcher(sanitized).replaceAll(REDACTED);
        return SECRET_ASSIGNMENT.matcher(sanitized).replaceAll("$1" + REDACTED);
    }

    private static String collapseWhitespaceAndRemoveControls(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < source.length(); ) {
            int codePoint = source.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.SURROGATE) {
                pendingSpace = pendingSpace || Character.isWhitespace(codePoint);
                continue;
            }
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && !result.isEmpty()) {
                result.append(' ');
            }
            result.appendCodePoint(codePoint);
            pendingSpace = false;
        }
        return result.toString().strip();
    }
}
