package com.enterprise.testagent.system.management.maintenance;

import com.enterprise.testagent.domain.maintenance.IdentityManagedTable;

/**
 * identity 运维相关的对外响应 DTO 与命令记录。
 *
 * <p>对外 DTO 在 domain {@link com.enterprise.testagent.domain.maintenance.IdentityStatus} 基础上
 * 补充查询时间戳，供前端展示。
 */
public final class DatabaseIdentityResponses {

    private DatabaseIdentityResponses() {
    }

    /**
     * 单张表的 identity 状态快照（对外响应）。
     *
     * @param table        枚举名，如 USERS
     * @param tableName    真实表名，如 users（仅展示）
     * @param currentValue identity 序列当前 last_value，表空或序列未初始化时为 null
     * @param maxId        表中当前 max(id)，表空时为 null
     * @param conflict     序列落后于已有主键（currentValue 小于 maxId）时为 true
     * @param lastUpdatedAt 本次查询时间
     */
    public record IdentityStatusDto(
            String table,
            String tableName,
            Long currentValue,
            Long maxId,
            boolean conflict,
            java.time.Instant lastUpdatedAt) {
    }

    /** 手动 RESTART WITH 的命令。 */
    public record RestartIdentityCommand(IdentityManagedTable table, long targetValue) {
    }
}
