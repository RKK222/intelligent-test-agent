package com.icbc.testagent.domain.event;

import com.icbc.testagent.domain.run.RunId;
import java.util.List;

/**
 * RunEvent 追加和回放端口，保证调用方只依赖 append-only 语义，不依赖数据库实现。
 */
public interface RunEventRepository {

    /**
     * 追加事件草稿，并由持久化实现分配 eventId 和同一 run 内的 seq。
     */
    RunEvent append(RunEventDraft draft);

    /**
     * 读取指定 run 在 lastSeq 之后的事件列表，供 SSE 断线恢复使用。
     */
    List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit);

    /**
     * 读取指定 root session 下的事件列表，供 Session 级历史树恢复状态事件；默认实现避免迁移窗口 JDBC 实现新增 SQL。
     */
    default List<RunEvent> findByRootSessionIdAfter(String rootSessionId, long lastSeq, int limit) {
        return List.of();
    }
}
