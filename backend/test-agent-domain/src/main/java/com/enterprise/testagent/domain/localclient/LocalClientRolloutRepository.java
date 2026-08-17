package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 本地客户端用户灰度名单仓储；关系型实现必须使用 MyBatis XML。 */
public interface LocalClientRolloutRepository {

    boolean isEnabled(UserId userId);

    Optional<LocalClientRolloutEntry> findByUserId(UserId userId);

    List<LocalClientRolloutEntry> findEnabledPage(long offset, int limit);

    long countEnabled();

    void save(LocalClientRolloutEntry entry);

    boolean disable(UserId userId, UserId updatedByUserId, Instant updatedAt);
}
