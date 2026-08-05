package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户管理组合检索 MyBatis mapper；SQL 统一维护在 XML 中。
 */
@Mapper
public interface UserManagementQueryMapper {

    List<UserManagementRow> findUsers(
            @Param("keywordPattern") String keywordPattern,
            @Param("roleCode") String roleCode,
            @Param("unassignedRoleOnly") boolean unassignedRoleOnly,
            @Param("organizationPattern") String organizationPattern,
            @Param("rdDepartmentPattern") String rdDepartmentPattern,
            @Param("departmentPattern") String departmentPattern,
            @Param("excludedUserId") String excludedUserId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    List<String> findUserIds(
            @Param("keywordPattern") String keywordPattern,
            @Param("roleCode") String roleCode,
            @Param("unassignedRoleOnly") boolean unassignedRoleOnly,
            @Param("organizationPattern") String organizationPattern,
            @Param("rdDepartmentPattern") String rdDepartmentPattern,
            @Param("departmentPattern") String departmentPattern,
            @Param("excludedUserId") String excludedUserId,
            @Param("limit") int limit);

    long countUsers(
            @Param("keywordPattern") String keywordPattern,
            @Param("roleCode") String roleCode,
            @Param("unassignedRoleOnly") boolean unassignedRoleOnly,
            @Param("organizationPattern") String organizationPattern,
            @Param("rdDepartmentPattern") String rdDepartmentPattern,
            @Param("departmentPattern") String departmentPattern,
            @Param("excludedUserId") String excludedUserId);
}
