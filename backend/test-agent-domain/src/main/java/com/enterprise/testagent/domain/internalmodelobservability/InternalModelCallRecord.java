package com.enterprise.testagent.domain.internalmodelobservability;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 内部模型代理单次调用观测记录。{@code firstByteMillis} 表示响应头到达，
 * {@code firstTokenMillis}/{@code lastTokenMillis} 表示首个/最后一个模型输出到达，
 * {@code outputTokenCount} 是上游返回的准确输出 Token 数；只存结构化字段，禁止存正文或错误文本。
 */
public record InternalModelCallRecord(
        Long id,
        String providerId,
        String model,
        String endpoint,
        InternalModelCallSource source,
        InternalModelCallOutcome outcome,
        Integer httpStatus,
        String errorClass,
        boolean streaming,
        long durationMillis,
        Long firstByteMillis,
        Long firstTokenMillis,
        Long lastTokenMillis,
        Long streamCompleteMillis,
        Long outputTokenCount,
        String traceId,
        String ucid,
        Instant startedAt) {

    public InternalModelCallRecord {
        providerId = DomainValidation.requireText(providerId, "providerId");
        endpoint = endpoint == null || endpoint.isBlank() ? "/" : endpoint.trim();
        source = Objects.requireNonNull(source, "source must not be null");
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        if (httpStatus != null && (httpStatus < 100 || httpStatus > 599)) {
            throw new IllegalArgumentException("httpStatus must be in [100, 599]");
        }
        errorClass = normalize(errorClass);
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must be >= 0");
        }
        if (firstByteMillis != null && firstByteMillis < 0) {
            throw new IllegalArgumentException("firstByteMillis must be >= 0");
        }
        if (firstTokenMillis != null && firstTokenMillis < 0) {
            throw new IllegalArgumentException("firstTokenMillis must be >= 0");
        }
        if (lastTokenMillis != null
                && (firstTokenMillis == null || lastTokenMillis < firstTokenMillis)) {
            throw new IllegalArgumentException(
                    "lastTokenMillis requires firstTokenMillis and must be >= firstTokenMillis");
        }
        if (streamCompleteMillis != null && streamCompleteMillis < 0) {
            throw new IllegalArgumentException("streamCompleteMillis must be >= 0");
        }
        if (streamCompleteMillis != null
                && (firstTokenMillis == null
                        || streamCompleteMillis < firstTokenMillis
                        || (lastTokenMillis != null && streamCompleteMillis < lastTokenMillis))) {
            throw new IllegalArgumentException(
                    "streamCompleteMillis must not precede observed model output");
        }
        if (outputTokenCount != null && outputTokenCount < 0) {
            throw new IllegalArgumentException("outputTokenCount must be >= 0");
        }
        traceId = traceId == null || traceId.isBlank() ? "" : traceId.trim();
        ucid = normalize(ucid);
        Objects.requireNonNull(startedAt, "startedAt must not be null");
    }

    /**
     * ITL/TPOT 只使用完整且可信的样本；没有准确输出 Token 数时不以 SSE chunk 数代替。
     */
    public Double interTokenLatencyMillis() {
        if (firstTokenMillis == null
                || lastTokenMillis == null
                || outputTokenCount == null
                || outputTokenCount < 2) {
            return null;
        }
        return (lastTokenMillis - firstTokenMillis) / (double) (outputTokenCount - 1);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        // 稳定错误信息仍受长度保护，避免异常类名过长或意外注入。
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }
}
