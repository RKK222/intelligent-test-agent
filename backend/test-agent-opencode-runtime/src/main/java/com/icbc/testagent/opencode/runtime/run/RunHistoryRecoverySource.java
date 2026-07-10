package com.icbc.testagent.opencode.runtime.run;

/**
 * 历史消息实际命中的恢复来源；仅供应用层决定是否还需要 legacy durable 事件补充。
 */
public enum RunHistoryRecoverySource {
    REDIS,
    OPENCODE,
    /** Redis 详情已过期，但通过无原文 Run 锚点成功读取 OpenCode 完整会话。 */
    OPENCODE_REDIS_SUMMARY,
    /** OpenCode 不可用时，以 PostgreSQL 旧轮次摘要补齐 Redis 近期完整详情。 */
    REDIS_POSTGRESQL_SUMMARY,
    POSTGRESQL_SUMMARY,
    NONE
}
