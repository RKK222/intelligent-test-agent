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
     * 仅当当前持久化状态仍等于预期状态时保存快照；成功时返回本次快照，失败时返回数据库当前记录。
     * 用于终态事件和异步错误并发到达时避免旧状态覆盖新终态。
     */
    default Run saveIfStatus(Run run, RunStatus expectedStatus) {
        return findById(run.runId())
                .map(current -> current.status() == expectedStatus ? save(run) : current)
                .orElseGet(() -> save(run));
    }

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
