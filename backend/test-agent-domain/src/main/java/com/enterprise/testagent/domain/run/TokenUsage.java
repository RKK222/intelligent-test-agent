package com.enterprise.testagent.domain.run;

/**
 * 单次模型调用的 token 消耗快照，字段允许为空以兼容 agent 未返回局部统计的情况。
 */
public record TokenUsage(
        Long input,
        Long output,
        Long reasoning,
        Long cacheRead,
        Long cacheWrite) {

    /**
     * 校验 token 数量不能为负数；null 表示当前 agent 没有提供该维度。
     */
    public TokenUsage {
        requireNonNegative(input, "input");
        requireNonNegative(output, "output");
        requireNonNegative(reasoning, "reasoning");
        requireNonNegative(cacheRead, "cacheRead");
        requireNonNegative(cacheWrite, "cacheWrite");
    }

    /**
     * 返回空 token 快照，便于旧数据和未上报 usage 的运行保持统一表达。
     */
    public static TokenUsage empty() {
        return new TokenUsage(null, null, null, null, null);
    }

    /**
     * 判断是否没有任何 token 维度，持久化和 DTO 可据此省略 usage 字段。
     */
    public boolean isEmpty() {
        return input == null && output == null && reasoning == null && cacheRead == null && cacheWrite == null;
    }

    private static void requireNonNegative(Long value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + " token usage must not be negative");
        }
    }
}
