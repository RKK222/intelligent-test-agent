package com.enterprise.testagent.domain.internalmodelobservability;

import java.util.List;

/**
 * 面向看板的调用结果大类。底层 outcome 继续保留精确原因，本枚举只负责筛选和聚合展示，
 * 避免运营视图直接暴露过多排障分类。
 */
public enum InternalModelCallOutcomeGroup {
    /** 调用正常完成。 */
    SUCCESS(List.of(InternalModelCallOutcome.SUCCESS)),
    /** 请求本身或平台供应商配置存在问题，尚未进入正常上游调用。 */
    REQUEST_OR_CONFIGURATION(List.of(
            InternalModelCallOutcome.PROXY_AUTH_FAILED,
            InternalModelCallOutcome.PROVIDER_UNAVAILABLE,
            InternalModelCallOutcome.REQUEST_INVALID)),
    /** 上游服务、网络或流式返回过程异常。 */
    UPSTREAM_FAILURE(List.of(
            InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED,
            InternalModelCallOutcome.UPSTREAM_FIRST_RESPONSE_TIMEOUT,
            InternalModelCallOutcome.UPSTREAM_FIRST_EVENT_TIMEOUT,
            InternalModelCallOutcome.UPSTREAM_STREAM_IDLE_TIMEOUT,
            InternalModelCallOutcome.UPSTREAM_HTTP_ERROR,
            InternalModelCallOutcome.UPSTREAM_STREAM_INTERRUPTED,
            InternalModelCallOutcome.UPSTREAM_STREAM_FAILED)),
    /** 调用方在回答完成前主动断开。 */
    CALLER_INTERRUPTED(List.of(InternalModelCallOutcome.CLIENT_DISCONNECTED)),
    /** 当前信号不足以判断具体责任方。 */
    OTHER(List.of(InternalModelCallOutcome.UNKNOWN_ERROR));

    private final List<InternalModelCallOutcome> outcomes;

    InternalModelCallOutcomeGroup(List<InternalModelCallOutcome> outcomes) {
        this.outcomes = List.copyOf(outcomes);
    }

    /** 返回本大类包含的稳定底层结果，供查询层生成低基数 IN 条件。 */
    public List<InternalModelCallOutcome> outcomes() {
        return outcomes;
    }

    /** 将单条底层结果归入唯一大类；枚举新增值未维护映射时失败关闭。 */
    public static InternalModelCallOutcomeGroup from(InternalModelCallOutcome outcome) {
        for (InternalModelCallOutcomeGroup group : values()) {
            if (group.outcomes.contains(outcome)) {
                return group;
            }
        }
        throw new IllegalArgumentException("unsupported internal model call outcome: " + outcome);
    }
}
