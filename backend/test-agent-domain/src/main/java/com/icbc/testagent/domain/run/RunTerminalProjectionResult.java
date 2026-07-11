package com.icbc.testagent.domain.run;

/** Run 终态投影的 compare-and-set 结果。 */
public enum RunTerminalProjectionResult {
    /** 版本匹配，Run、摘要和 Session 时间已经在同一事务内更新。 */
    APPLIED,
    /** 持久化版本已变化，本次调用没有写摘要或 Session。 */
    VERSION_CONFLICT,
    /** Redis 已保存安全终态投影，等待 PostgreSQL 恢复后按退避重试。 */
    TERMINAL_PENDING_DB
}
