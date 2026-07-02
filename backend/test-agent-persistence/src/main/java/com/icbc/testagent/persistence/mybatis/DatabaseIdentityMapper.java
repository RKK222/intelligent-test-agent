package com.icbc.testagent.persistence.mybatis;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * identity 运维 MyBatis mapper；SQL 维护在 DatabaseIdentityMapper.xml。
 *
 * <p>注意：tableName 与 targetValue 使用 ${} 拼接，调用方
 * （MyBatisDatabaseIdentityMaintenanceRepository）必须保证 tableName 来自白名单枚举常量、
 * targetValue 为正整数且大于当前 max(id)，杜绝 SQL 注入与往回滚。其余参数使用 #{} 绑定。
 */
@Mapper
public interface DatabaseIdentityMapper {

    /**
     * 查询一张表的 identity 序列当前值（last_value）与表中 max(id)。
     *
     * @param tableName 白名单内的真实表名
     * @return Map 含 currentValue（Long，可能 null）与 maxId（Long，可能 null）
     */
    Map<String, Object> queryIdentityStatus(@Param("tableName") String tableName);

    /**
     * 把指定表 identity 重置到目标值：ALTER TABLE ... ALTER COLUMN id RESTART WITH targetValue。
     *
     * @param tableName   白名单内的真实表名
     * @param targetValue 目标值（已由调用方校验为正整数且大于 max(id)）
     * @return 受影响行数（DDL 通常为 0）
     */
    int restartIdentity(@Param("tableName") String tableName, @Param("targetValue") long targetValue);
}
