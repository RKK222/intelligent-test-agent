package com.icbc.testagent.domain.run;

/** Redis 中安全终态投影等待写入 PostgreSQL 的状态。 */
public enum RunTerminalRetryState {
    /** Run 已在 Redis 收敛为终态，但 PostgreSQL 终态事务尚未成功。 */
    TERMINAL_PENDING_DB
}
