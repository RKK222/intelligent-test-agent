package com.enterprise.testagent.domain.maintenance;

/**
 * 单张表 identity 序列状态的域记录，作为持久化端口的返回类型。
 *
 * <p>不携带时间戳：查询时间由 system-management 的对外 DTO 在装配时补充，
 * 保持 domain 不依赖具体时间语义。
 *
 * @param table        枚举名，如 USERS
 * @param tableName    真实表名，如 users（仅展示用）
 * @param currentValue identity 序列当前 last_value，表空或序列未初始化时为 null
 * @param maxId        表中当前 max(id)，表空时为 null
 * @param conflict     序列落后于已有主键（currentValue 小于 maxId）时为 true
 */
public record IdentityStatus(
        String table,
        String tableName,
        Long currentValue,
        Long maxId,
        boolean conflict) {
}
