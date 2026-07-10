package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunSessionScope;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
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
    int MAX_DURABLE_EVENTS = 20_000;
    long MAX_DETAIL_BYTES = 32L * 1024L * 1024L;

    void initialize(RunRuntimeManifest manifest, RunRuntimeInput input);

    Optional<RunRuntimeManifest> findManifest(RunId runId);

    default RunStorageMode storageMode(RunId runId) {
        return findManifest(runId).map(RunRuntimeManifest::storageMode).orElse(RunStorageMode.LEGACY_FULL);
    }

    RunRuntimeAppendResult appendDurable(RunEventDraft draft);

    /**
     * 投影 transient 事件；返回 false 表示该事件会导致终态回退，已被运行数据面原子丢弃。
     */
    boolean projectTransient(RunEventDraft draft);

    void saveSnapshot(RunRuntimeSnapshot snapshot);

    RunRuntimeReplay replayAfter(RunId runId, long lastSeq, int limit);

    /** 按 runtimeVersion 读取 durable + transient 有序尾部；容量换代时返回 reset snapshot。 */
    RunRuntimeTail tailAfter(RunId runId, long runtimeVersion, int limit);

    void saveScope(RunSessionScope scope, RunSessionScopeSession session);

    Optional<RunSessionScopeSession> findScopeSession(RunId runId, String sessionId);

    long scopeVersion(RunId runId);

    boolean claimRawEvent(RunId runId, String sessionId, String rawEventId);

    void appendPending(String sessionId, RunEventDraft draft);

    List<RunEventDraft> drainPending(RunId runId, String sessionId);

    Optional<RunRuntimeManifest> findActiveBySession(SessionId sessionId);

    List<RunRuntimeManifest> findActiveByUser(UserId userId);

    /** 用户是否已经进入 Redis 运行态链路；即使当前无 active Run 也用于阻止回退数据库轮询。 */
    boolean hasUserRuntimeState(UserId userId);

    List<RunRuntimeManifest> findActiveByServer(String linuxServerId);

    void updateStatus(RunId runId, RunStatus status, long expectedStatusVersion, String attention);

    void touch(RunId runId);
}
