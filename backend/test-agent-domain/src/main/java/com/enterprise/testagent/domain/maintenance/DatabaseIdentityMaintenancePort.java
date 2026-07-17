package com.enterprise.testagent.domain.maintenance;

/**
 * 数据库 identity 运维持久化端口。
 *
 * <p>业务模块通过本端口查询白名单表的 identity 状态并重启序列，不直接感知 MyBatis 或表结构。
 * 实现由 persistence 模块提供（MyBatisDatabaseIdentityMaintenanceRepository）。
 *
 * <p>表名只接受 {@link IdentityManagedTable} 枚举，目标值由调用方校验为正整数且大于当前 max(id)，
 * 端口实现据此拼接白名单内 SQL，杜绝任意表名注入与往回滚造成新冲突。
 */
public interface DatabaseIdentityMaintenancePort {

    /**
     * 查询指定表的 identity 序列当前值（last_value）与表中 max(id)。
     *
     * @param table 白名单枚举
     * @return 状态快照；currentValue 与 maxId 在表空或序列未初始化时为 null
     */
    IdentityStatus queryIdentityStatus(IdentityManagedTable table);

    /**
     * 把指定表 identity 重置到目标值：ALTER TABLE ... ALTER COLUMN id RESTART WITH targetValue。
     *
     * <p>调用方负责保证 targetValue 为正整数且大于当前 max(id)；端口实现仅信任白名单表名与该值。
     *
     * @param table       白名单枚举
     * @param targetValue 目标值
     */
    void restartIdentity(IdentityManagedTable table, long targetValue);
}
