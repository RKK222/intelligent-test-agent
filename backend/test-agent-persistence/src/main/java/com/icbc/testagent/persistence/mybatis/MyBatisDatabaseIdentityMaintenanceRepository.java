package com.icbc.testagent.persistence.mybatis;

import com.icbc.testagent.domain.maintenance.DatabaseIdentityMaintenancePort;
import com.icbc.testagent.domain.maintenance.IdentityManagedTable;
import com.icbc.testagent.domain.maintenance.IdentityStatus;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * 数据库 identity 运维护口的 MyBatis 实现，负责端口与 XML mapper 之间的转换。
 *
 * <p>表名来自 {@link IdentityManagedTable} 白名单枚举常量，目标值由 service 层校验后传入，
 * 此处直接交给 mapper 拼接白名单内 SQL，不重复校验。
 */
@Repository
public class MyBatisDatabaseIdentityMaintenanceRepository implements DatabaseIdentityMaintenancePort {

    private final DatabaseIdentityMapper mapper;

    /**
     * 注入 MyBatis mapper；连接、事务和 SQL 执行由 MyBatis-Spring 管理。
     */
    public MyBatisDatabaseIdentityMaintenanceRepository(DatabaseIdentityMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public IdentityStatus queryIdentityStatus(IdentityManagedTable table) {
        Map<String, Object> row = mapper.queryIdentityStatus(table.tableName());
        Long currentValue = longOrNull(row == null ? null : row.get("currentValue"));
        Long maxId = longOrNull(row == null ? null : row.get("maxId"));
        // is_called=true 正常态下 nextval=currentValue+1，安全当 currentValue>=maxId，错位当 currentValue<maxId
        boolean conflict = currentValue != null && maxId != null && currentValue < maxId;
        return new IdentityStatus(table.name(), table.tableName(), currentValue, maxId, conflict);
    }

    @Override
    public void restartIdentity(IdentityManagedTable table, long targetValue) {
        mapper.restartIdentity(table.tableName(), targetValue);
    }

    /**
     * 把查询返回的数值安全转为 Long，兼容不同 JDBC 驱动返回 Integer/Long/BigDecimal 的情况。
     */
    private static Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
