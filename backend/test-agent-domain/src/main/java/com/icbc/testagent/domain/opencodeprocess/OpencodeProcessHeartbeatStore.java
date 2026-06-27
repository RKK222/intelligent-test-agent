package com.icbc.testagent.domain.opencodeprocess;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 运行中 Java / manager / opencode 进程心跳端口；业务层不依赖具体 Redis 实现。
 */
public interface OpencodeProcessHeartbeatStore {

    /**
     * 写入后端 Java 进程旧式 ID 心跳，保留给兼容调用方；新运行管理以快照为准。
     */
    void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt);

    /**
     * 写入后端 Java 进程完整运行快照，具体实现负责设置 10 秒 TTL。
     */
    void recordBackendSnapshot(BackendRuntimeSnapshot snapshot);

    /**
     * 写入 manager 完整运行快照，具体实现负责设置 10 秒 TTL。
     */
    void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot);

    /**
     * 写入用户 opencode server 进程心跳，具体实现负责设置 5 分钟过期时间。
     */
    void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt);

    /**
     * 返回当前仍有 TTL 的后端 Java 进程快照。
     */
    List<BackendRuntimeSnapshot> liveBackendSnapshots();

    /**
     * 返回当前仍有 TTL 的 manager 快照。
     */
    List<ManagerRuntimeSnapshot> liveManagerSnapshots();

    /**
     * 返回指定容器在时间范围内的原始运行指标样本；调用方负责按展示需要降采样。
     */
    default List<ContainerRuntimeMetricSample> containerMetricSamples(
            OpencodeContainerId containerId,
            Instant from,
            Instant to) {
        return List.of();
    }

    /**
     * 返回指定 Java 后端进程在时间范围内的原始运行指标样本；调用方负责按展示需要降采样。
     */
    default List<BackendRuntimeMetricSample> backendMetricSamples(
            BackendProcessId backendProcessId,
            Instant from,
            Instant to) {
        return List.of();
    }

    /**
     * 返回指定服务器在时间范围内的服务器级原始指标样本；Java 进程重启后仍按 linuxServerId 连续查询。
     */
    default List<ServerRuntimeMetricSample> serverMetricSamples(
            LinuxServerId linuxServerId,
            Instant from,
            Instant to) {
        return List.of();
    }

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
