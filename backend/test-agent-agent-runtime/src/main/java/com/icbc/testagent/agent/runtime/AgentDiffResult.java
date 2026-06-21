package com.icbc.testagent.agent.runtime;

import java.util.List;

/**
 * 通用 agent Diff 查询结果。
 */
public record AgentDiffResult(List<AgentDiffFile> files) {

    /**
     * 固化文件列表。
     */
    public AgentDiffResult {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
