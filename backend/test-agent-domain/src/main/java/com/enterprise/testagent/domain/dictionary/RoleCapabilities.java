package com.enterprise.testagent.domain.dictionary;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 平台全局角色能力层级的唯一后端定义。
 *
 * <p>认证主体仍只保存数据库实际授予的角色；能力判断在使用点按
 * {@code SUPER_ADMIN > SYSTEM_ADMIN > APP_ADMIN > USER} 展开，避免把继承角色写回数据库。
 */
public final class RoleCapabilities {

    private static final Map<String, Integer> LEVELS = Map.of(
            Dictionary.ROLE_USER, 0,
            Dictionary.ROLE_APP_ADMIN, 1,
            Dictionary.ROLE_SYSTEM_ADMIN, 2,
            Dictionary.ROLE_SUPER_ADMIN, 3);

    private RoleCapabilities() {
    }

    /** 判断实际角色集合是否具备指定角色能力；未知角色只支持精确匹配。 */
    public static boolean hasCapability(Collection<String> actualRoles, String requiredRole) {
        Objects.requireNonNull(actualRoles, "actualRoles must not be null");
        Objects.requireNonNull(requiredRole, "requiredRole must not be null");
        if (actualRoles.contains(requiredRole)) {
            return true;
        }
        Integer requiredLevel = LEVELS.get(requiredRole);
        if (requiredLevel == null) {
            return false;
        }
        return actualRoles.stream()
                .map(LEVELS::get)
                .filter(Objects::nonNull)
                .anyMatch(level -> level >= requiredLevel);
    }
}
