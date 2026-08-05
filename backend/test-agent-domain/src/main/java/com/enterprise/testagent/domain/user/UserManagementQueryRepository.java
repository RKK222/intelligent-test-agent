package com.enterprise.testagent.domain.user;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import java.util.List;

/**
 * 用户管理组合检索端口，与面向其他业务的通用用户搜索隔离。
 */
public interface UserManagementQueryRepository {

    /**
     * 按关键字、角色、组织及部门条件分页查询用户。
     */
    PageResponse<User> findPage(UserManagementQuery query, PageRequest pageRequest);

    /**
     * 返回全部匹配条件的用户 ID，并在数据库侧排除当前操作者、限制最大读取数量。
     *
     * <p>该能力供用户管理的“选择全部检索结果”复用，避免前端逐页拉取完整用户资料。
     */
    List<UserId> findUserIds(
            UserManagementQuery query,
            UserId excludedUserId,
            int limit);
}
