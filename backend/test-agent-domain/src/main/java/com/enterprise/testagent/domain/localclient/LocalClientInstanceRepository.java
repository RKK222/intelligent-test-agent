package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 本地客户端实例仓储端口。 */
public interface LocalClientInstanceRepository {

    Optional<LocalClientInstance> findById(LocalClientInstanceId clientInstanceId);

    List<LocalClientInstance> findByUserId(UserId userId);

    List<LocalClientInstance> findAll();

    void save(LocalClientInstance instance);

    /** 已替换实例保留历史外键，但不再进入用户侧活动实例投影。 */
    void markReplaced(
            UserId userId,
            LocalClientInstanceId replacedClientInstanceId,
            LocalClientInstanceId replacementClientInstanceId,
            Instant replacedAt);

    /** 旧实例重新完成认证时恢复为活动实例，避免替换标记永久吞掉真实重连。 */
    void clearReplacement(LocalClientInstanceId clientInstanceId);

    void markDisconnected(LocalClientInstanceId clientInstanceId, Instant disconnectedAt);

    void updateLastUpdateStatus(
            LocalClientInstanceId clientInstanceId,
            String status,
            String targetVersion,
            Instant observedAt);
}
