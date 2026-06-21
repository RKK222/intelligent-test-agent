package com.icbc.testagent.domain.dictionary;

import com.icbc.testagent.domain.user.UserId;
import java.util.List;

/**
 * 用户角色仓储接口。
 */
public interface UserRoleRepository {

    /**
     * 保存用户角色关联。
     */
    void save(UserRole userRole);

    /**
     * 根据用户 ID 查找角色列表。
     */
    List<UserRole> findByUserId(UserId userId);

    /**
     * 删除用户角色关联。
     */
    void delete(UserRole userRole);
}
