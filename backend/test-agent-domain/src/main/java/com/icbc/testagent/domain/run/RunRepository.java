package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.session.SessionId;
import java.util.Optional;

/**
 * Run 持久化端口，状态迁移仍由 Run 聚合自身负责。
 */
public interface RunRepository {

    /**
     * 保存运行聚合当前状态。
     */
    Run save(Run run);

    /**
     * 按运行 ID 查询运行聚合。
     */
    Optional<Run> findById(RunId runId);

    /**
     * 查询指定会话最近的非终态 Run，用于刷新后恢复运行中的 SSE 订阅。
     */
    default Optional<Run> findLatestActiveBySessionId(SessionId sessionId) {
        return Optional.empty();
    }
}
