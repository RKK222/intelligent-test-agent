package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.SessionId;
import java.time.Instant;
import java.util.List;
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
     * 批量读取可见 Run，反馈历史查询等低频场景覆盖此方法以避免逐条数据库访问。
     */
    default List<Run> findByIds(List<RunId> runIds) {
        return runIds.stream().map(this::findById).flatMap(Optional::stream).toList();
    }

    /**
     * 查询指定会话最近的非终态 Run，用于刷新后恢复运行中的 SSE 订阅。
     */
    default Optional<Run> findLatestActiveBySessionId(SessionId sessionId) {
        return Optional.empty();
    }

    /**
     * 查询更新时间早于指定时间的非终态 Run，用于后台定时收敛失去事件订阅的历史运行。
     */
    default List<Run> findStaleActiveRuns(Instant updatedBefore, int limit) {
        return List.of();
    }

    /**
     * 查询更新时间早于指定时间的非终态旁路问答 Run，供临时远端会话孤儿回收任务使用。
     */
    default List<Run> findStaleActiveSideQuestionRuns(Instant updatedBefore, int limit) {
        return List.of();
    }
}
