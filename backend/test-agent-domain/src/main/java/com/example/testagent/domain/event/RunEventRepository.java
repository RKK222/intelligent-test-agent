package com.example.testagent.domain.event;

import com.example.testagent.domain.run.RunId;
import java.util.List;

/**
 * RunEvent 追加和回放端口，保证调用方只依赖 append-only 语义，不依赖数据库实现。
 */
public interface RunEventRepository {

    RunEvent append(RunEventDraft draft);

    List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit);
}
