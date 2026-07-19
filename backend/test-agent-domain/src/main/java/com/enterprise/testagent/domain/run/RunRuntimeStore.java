package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunSessionScope;
import com.enterprise.testagent.domain.event.RunSessionScopeSession;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Run 运行数据面的领域端口。Redis 是生产唯一实现，调用方不得自动降级到数据库或 JVM 内存。
 */
public interface RunRuntimeStore {

    Duration ACTIVE_TTL = Duration.ofHours(3);
    Duration TERMINAL_DETAILS_TTL = Duration.ofHours(24);
    Duration PENDING_ASK_TTL = Duration.ofDays(7);
    Duration PENDING_ASK_RECOVERY_BUFFER = Duration.ofMinutes(5);
    Duration OWNER_LEASE_TTL = Duration.ofSeconds(15);
    Duration OWNER_RENEW_INTERVAL = Duration.ofSeconds(5);
    int MAX_DURABLE_EVENTS = 20_000;
    long MAX_DETAIL_BYTES = 32L * 1024L * 1024L;

    void initialize(RunRuntimeManifest manifest, RunRuntimeInput input);

    /** 在 Redis 中声明 session + clientRequestId；false 表示同一请求已由其它 Run 接管。 */
    boolean claimClientRequest(SessionId sessionId, String clientRequestId, RunId runId);

    /** Run 锚点已持久化后确认幂等映射并延长保留期；false 表示映射已被其它 Run 占用。 */
    boolean confirmClientRequest(SessionId sessionId, String clientRequestId, RunId runId);

    Optional<RunId> findByClientRequest(SessionId sessionId, String clientRequestId);

    void releaseClientRequest(SessionId sessionId, String clientRequestId, RunId runId);

    /**
     * 竞争 Run owner 租约；已有其它 owner 时返回空，同一 owner 重入返回原 fencing token 并续期。
     */
    Optional<RunOwnerLease> claimOwnerLease(RunId runId, String ownerBackendProcessId);

    /**
     * 仅当 manifest 仍为活跃状态且与扫描快照一致时接管 owner；成功后总是提升 fencing token。
     * 调用方使用本方法把“二次确认 + 抢占”收敛为一个 Redis 原子操作。
     */
    Optional<RunOwnerLease> claimOwnerLeaseIfUnchanged(
            RunRuntimeManifest expectedManifest,
            String ownerBackendProcessId);

    /** 仅 owner 与 fencing token 都匹配时续租，返回带新过期时间的租约。 */
    Optional<RunOwnerLease> renewOwnerLease(RunOwnerLease lease);

    /** 仅 owner 与 fencing token 都匹配时释放，避免旧执行者删除新 owner 的租约。 */
    boolean releaseOwnerLease(RunOwnerLease lease);

    /**
     * 清理由当前请求创建、但尚未成功写入 PostgreSQL 锚点的 Redis 详情和外部索引。
     * 该方法只能在确认没有发生远端副作用时调用。
     */
    void discardBeforeDispatch(RunId runId);

    Optional<RunRuntimeManifest> findManifest(RunId runId);

    /** 读取本轮完整输入，仅供终态摘要和故障恢复；调用方不得写入日志或 PostgreSQL。 */
    Optional<RunRuntimeInput> findInput(RunId runId);

    /** 返回低频终态投影所需的 Diff 数量，不读取原始事件。 */
    RunDiffCounts diffCounts(RunId runId);

    /** 首次创建远端 session 后回填 manifest；不触发关系库写入。 */
    void bindRemoteSession(RunId runId, String remoteSessionId);

    /** 带 owner fencing 的远端 session 回填；实现必须在同一原子操作内校验租约。 */
    void bindRemoteSession(RunId runId, String remoteSessionId, RunOwnerLease lease);

    default RunStorageMode storageMode(RunId runId) {
        return findManifest(runId).map(RunRuntimeManifest::storageMode).orElse(RunStorageMode.LEGACY_FULL);
    }

    RunRuntimeAppendResult appendDurable(RunEventDraft draft);

    /** 带 owner fencing 的 durable append；实现必须在写事件前原子校验租约。 */
    RunRuntimeAppendResult appendDurable(RunEventDraft draft, RunOwnerLease lease);

    /**
     * 投影 transient 事件；返回 false 表示该事件会导致终态回退，已被运行数据面原子丢弃。
     */
    boolean projectTransient(RunEventDraft draft);

    /** 带 owner fencing 的 transient 投影；实现必须在写事件前原子校验租约。 */
    boolean projectTransient(RunEventDraft draft, RunOwnerLease lease);

    void saveSnapshot(RunRuntimeSnapshot snapshot);

    RunRuntimeReplay replayAfter(RunId runId, long lastSeq, int limit);

    /** 按 runtimeVersion 读取 durable + transient 有序尾部；容量换代时返回 reset snapshot。 */
    RunRuntimeTail tailAfter(RunId runId, long runtimeVersion, int limit);

    void saveScope(RunSessionScope scope, RunSessionScopeSession session);

    /** 带 owner fencing 的 scope 保存；实现必须在写入前原子校验租约。 */
    void saveScope(RunSessionScope scope, RunSessionScopeSession session, RunOwnerLease lease);

    Optional<RunSessionScopeSession> findScopeSession(RunId runId, String sessionId);

    long scopeVersion(RunId runId);

    boolean claimRawEvent(RunId runId, String sessionId, String rawEventId);

    /** 带 owner fencing 的 raw event 去重声明；实现必须在声明前原子校验租约。 */
    boolean claimRawEvent(
            RunId runId,
            String sessionId,
            String rawEventId,
            RunOwnerLease lease);

    void appendPending(String sessionId, RunEventDraft draft);

    /** 带 owner fencing 的 pending append；实现必须在追加前原子校验租约。 */
    void appendPending(String sessionId, RunEventDraft draft, RunOwnerLease lease);

    List<RunEventDraft> drainPending(RunId runId, String sessionId);

    /** 带 owner fencing 的 pending drain；实现必须在删除队列前原子校验租约。 */
    List<RunEventDraft> drainPending(RunId runId, String sessionId, RunOwnerLease lease);

    Optional<RunRuntimeManifest> findActiveBySession(SessionId sessionId);

    /** 查询仍在详情 TTL 内的最近 Run，供历史按 Redis → OpenCode → PostgreSQL 顺序恢复。 */
    List<RunRuntimeManifest> findRecentBySession(SessionId sessionId, int limit);

    List<RunRuntimeManifest> findActiveByUser(UserId userId);

    /** 用户是否已经进入 Redis 运行态链路；即使当前无 active Run 也用于阻止回退数据库轮询。 */
    boolean hasUserRuntimeState(UserId userId);

    /**
     * 原子申请当前用户的运行态 dispose 闸门；实现必须在申请时同时确认用户 active Run 索引为空。
     * 未接入 Redis 运行态的兼容实现默认放行，由上层会话快照继续做保守校验。
     */
    default boolean tryAcquireUserRuntimeDispose(UserId userId, String token, Duration ttl) {
        return true;
    }

    /** 返回当前用户是否已经有另一个 dispose 闸门，供 Run 启动入口拒绝竞态请求。 */
    default boolean isUserRuntimeDisposeActive(UserId userId) {
        return false;
    }

    /** 仅在 token 仍匹配时续租当前用户 dispose 闸门；false 表示租约已失效。 */
    default boolean renewUserRuntimeDispose(UserId userId, String token, Duration ttl) {
        return true;
    }

    /** 仅释放 token 仍匹配的当前用户 dispose 闸门。 */
    default void releaseUserRuntimeDispose(UserId userId, String token) {
        // 兼容未接入用户级 Redis 闸门的旧实现无需释放动作。
    }

    List<RunRuntimeManifest> findActiveByServer(String linuxServerId);

    /** 查询同服务器恢复索引中尚未完成 PostgreSQL CAS 的终态 outbox；单次最多返回 limit 条。 */
    List<RunTerminalProjectionPending> findTerminalProjectionPendingByServer(
            String linuxServerId,
            int limit);

    /** 查询单 Run 当前待投影终态，供正常终态路径和数据库重试关联 outbox version。 */
    Optional<RunTerminalProjectionPending> findTerminalProjectionPending(RunId runId);

    /** 仅 version 仍匹配时确认终态投影；false 表示已确认或已有晚到终态覆盖。 */
    boolean ackTerminalProjection(RunId runId, long expectedVersion);

    void updateStatus(RunId runId, RunStatus status, long expectedStatusVersion, String attention);

    void touch(RunId runId);
}
