package com.example.testagent.app.run;

import java.util.List;

/**
 * Run 级 Diff 查询结果，按平台稳定字段返回给 Web DTO。
 */
public record RunDiffResponse(String runId, List<RunDiffFileResponse> files) {

    public RunDiffResponse {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
