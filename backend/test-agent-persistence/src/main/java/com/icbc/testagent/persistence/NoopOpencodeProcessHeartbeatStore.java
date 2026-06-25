package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import java.time.Instant;
import java.util.Set;

/**
 * Redis 未启用时的心跳存储降级实现；查询服务会回退到数据库心跳时间判断活跃状态。
 */
public class NoopOpencodeProcessHeartbeatStore implements OpencodeProcessHeartbeatStore {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt) {
        // Redis 未启用时不写外部心跳。
    }

    @Override
    public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {
        // Redis 未启用时不写外部心跳。
    }

    @Override
    public Set<BackendProcessId> liveBackendProcessIds() {
        return Set.of();
    }

    @Override
    public Set<OpencodeProcessId> liveOpencodeProcessIds() {
        return Set.of();
    }

    @Override
    public void cleanupExpiredHeartbeats() {
        // 无外部索引需要清理。
    }
}
