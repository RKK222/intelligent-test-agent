package com.icbc.testagent.domain.run;

/**
 * Run 数据保存模式。该值会进入公共 API 和 PostgreSQL，禁止用 shadow/dual-write 临时策略扩展枚举。
 */
public enum RunStorageMode {
    /** 历史兼容模式：RunEvent、scope 和原始消息仍按旧链路落 PostgreSQL。 */
    LEGACY_FULL,
    /** Redis 运行数据面模式：运行中详情只进 Redis，终态仅向 PostgreSQL 写摘要。 */
    REDIS_SUMMARY
}
