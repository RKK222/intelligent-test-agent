package com.icbc.testagent.opencode.runtime.run;

import java.util.List;

/**
 * Run 级 Diff 查询结果，按平台稳定字段返回给 Web DTO。
 */
public record RunDiffResponse(String runId, List<RunDiffFileResponse> files) {

    /**
     * 固化 Diff 文件列表，null 结果按空 Diff 返回。
     */
    public RunDiffResponse {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
