package com.enterprise.testagent.domain.team;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 系统管理员团队关系的关系型持久化端口。 */
public interface SystemAdminTeamRepository {

    PageResponse<SystemAdminTeamMemberView> findMembers(UserId ownerUserId, String keyword, PageRequest pageRequest);

    PageResponse<User> findCandidates(UserId ownerUserId, String keyword, PageRequest pageRequest);

    Optional<SystemAdminTeamMember> find(UserId ownerUserId, UserId memberUserId);

    List<UserId> findActiveMemberIds(UserId ownerUserId);

    boolean isActiveMember(UserId ownerUserId, UserId memberUserId);

    void save(SystemAdminTeamMember member);

    void deactivate(UserId ownerUserId, UserId memberUserId, Instant deletedAt);
}
