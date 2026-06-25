package com.icbc.testagent.domain.opencodeprocess;

import java.time.Instant;
import java.util.Set;

/**
 * 运行中 Java / opencode 进程心跳端口；业务层只关心活跃进程 ID，不依赖具体 Redis 实现。
 */
public interface OpencodeProcessHeartbeatStore {

    /**
     * 当前环境是否启用外部心跳存储；未启用时调用方可回退到数据库快照时间。
     */
    boolean enabled();

    /**
     * 写入后端 Java 进程心跳，具体实现负责设置 5 分钟过期时间。
     */
    void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt);

    /**
     * 写入用户 opencode server 进程心跳，具体实现负责设置 5 分钟过期时间。
     */
    void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt);

    /**
     * 返回当前仍有心跳 key 的后端 Java 进程 ID。
     */
    Set<BackendProcessId> liveBackendProcessIds();

    /**
     * 返回当前仍有心跳 key 的用户 opencode server 进程 ID。
     */
    Set<OpencodeProcessId> liveOpencodeProcessIds();

    /**
     * 清理索引中已经没有心跳 key 的进程 ID。
     */
    void cleanupExpiredHeartbeats();
}
