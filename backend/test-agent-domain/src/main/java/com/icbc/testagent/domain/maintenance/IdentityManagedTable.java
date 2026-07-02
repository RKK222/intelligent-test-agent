package com.icbc.testagent.domain.maintenance;

import java.util.Optional;

/**
 * 受运维管理的 identity 主键表白名单。
 *
 * <p>枚举名对外暴露给前端，真实表名仅用于在白名单内拼接 SQL，杜绝任意表名注入。
 * 新增受运维表时只需在此追加常量，调用方与持久化实现自动可用。
 */
public enum IdentityManagedTable {

    USERS("users"),
    USER_ROLES("user_roles"),
    DICTIONARIES("dictionaries"),
    USER_LOGIN_LOGS("user_login_logs");

    private final String tableName;

    IdentityManagedTable(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 真实物理表名，已限定为本枚举常量，可安全拼入 SQL。
     */
    public String tableName() {
        return tableName;
    }

    /**
     * 大小写无关解析表码为枚举，非白名单返回空 Optional。
     *
     * @param code 前端传入的表码，如 "USERS"
     * @return 命中白名单返回对应枚举，否则空
     */
    public static Optional<IdentityManagedTable> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        for (IdentityManagedTable table : values()) {
            if (table.name().equalsIgnoreCase(code)) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }
}
