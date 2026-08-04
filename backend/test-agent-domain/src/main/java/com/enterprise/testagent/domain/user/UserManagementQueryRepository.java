package com.enterprise.testagent.domain.user;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;

/**
 * 用户管理组合检索端口，与面向其他业务的通用用户搜索隔离。
 */
public interface UserManagementQueryRepository {

    /**
     * 按关键字、角色、组织及部门条件分页查询用户。
     */
    PageResponse<User> findPage(UserManagementQuery query, PageRequest pageRequest);
}
