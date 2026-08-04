package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserManagementQuery;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * 用户管理组合检索的 MyBatis Repository，负责查询条件和领域对象之间的转换。
 */
@Repository
public class MyBatisUserManagementQueryRepository implements UserManagementQueryRepository {

    private final UserManagementQueryMapper mapper;

    /** 注入 MyBatis mapper，连接和事务生命周期由 MyBatis-Spring 管理。 */
    public MyBatisUserManagementQueryRepository(UserManagementQueryMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public PageResponse<User> findPage(UserManagementQuery query, PageRequest pageRequest) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        String keywordPattern = searchPattern(query.keyword());
        String organizationPattern = searchPattern(query.organization());
        String rdDepartmentPattern = searchPattern(query.rdDepartment());
        String departmentPattern = searchPattern(query.department());
        String roleCode = query.assignedRoleCode();

        var rows = mapper.findUsers(
                keywordPattern,
                roleCode,
                query.unassignedRoleOnly(),
                organizationPattern,
                rdDepartmentPattern,
                departmentPattern,
                pageRequest.size(),
                pageRequest.offset());
        long total = mapper.countUsers(
                keywordPattern,
                roleCode,
                query.unassignedRoleOnly(),
                organizationPattern,
                rdDepartmentPattern,
                departmentPattern);
        return new PageResponse<>(
                rows.stream().map(this::toUser).toList(),
                pageRequest.page(),
                pageRequest.size(),
                total);
    }

    /** 把只读查询行恢复为完整用户领域对象。 */
    private User toUser(UserManagementRow row) {
        return new User(
                new UserId(row.userId()),
                row.unifiedAuthId(),
                row.username(),
                row.passwordHash(),
                row.organization(),
                row.rdDepartment(),
                row.department(),
                UserStatus.valueOf(row.status()),
                row.createdAt(),
                row.updatedAt());
    }

    /** 统一生成大小写无关的包含查询 pattern。 */
    private String searchPattern(String value) {
        if (value == null) {
            return null;
        }
        return "%" + value.toLowerCase(Locale.ROOT) + "%";
    }
}
