package com.enterprise.testagent.domain.user;

/**
 * 用户管理列表的组合检索条件。
 *
 * <p>文本字段由持久层按不区分大小写的包含关系匹配；role 使用角色字典 code 精确匹配，
 * {@link #ROLE_UNASSIGNED} 表示仅查询没有任何有效全局角色的用户。
 */
public record UserManagementQuery(
        String keyword,
        String role,
        String organization,
        String rdDepartment,
        String department) {

    /** 前后端约定的“未分配角色”筛选值，不会写入角色字典。 */
    public static final String ROLE_UNASSIGNED = "UNASSIGNED";

    /**
     * 去除查询参数首尾空白，空字符串统一视为未设置筛选条件。
     */
    public UserManagementQuery {
        keyword = normalize(keyword);
        role = normalize(role);
        organization = normalize(organization);
        rdDepartment = normalize(rdDepartment);
        department = normalize(department);
    }

    /** 是否只查询未分配全局角色的用户。 */
    public boolean unassignedRoleOnly() {
        return ROLE_UNASSIGNED.equals(role);
    }

    /** 返回实际角色 code；未分配筛选不会作为字典值参与查询。 */
    public String assignedRoleCode() {
        return unassignedRoleOnly() ? null : role;
    }

    /** 把 HTTP 空白值收敛为空条件，持久层无需重复判断空串。 */
    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
