package com.enterprise.testagent.domain.user;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import java.util.Optional;

/**
 * 用户仓储接口，定义用户持久化的核心操作。
 */
public interface UserRepository {

    /**
     * 保存用户到持久化存储。
     */
    void save(User user);

    /**
     * 根据用户业务 ID 查找用户。
     */
    Optional<User> findByUserId(UserId userId);

    /**
     * 根据统一认证号查找用户。
     */
    Optional<User> findByUnifiedAuthId(String unifiedAuthId);

    /**
     * 根据用户名查找用户。
     */
    Optional<User> findByUsername(String username);

    /**
     * 按 userId、unifiedAuthId 或 username 任一字段分页搜索用户，用于管理入口选择已有平台用户。
     */
    PageResponse<User> findPage(String keyword, PageRequest pageRequest);

    /**
     * 检查用户名是否已存在。
     */
    boolean existsByUsername(String username);

    /**
     * 检查统一认证号是否已存在。
     */
    boolean existsByUnifiedAuthId(String unifiedAuthId);
}
