package com.icbc.testagent.opencode.runtime.run;

/**
 * Run 级 Diff 接受或拒绝动作结果。
 */
public record RunDiffActionResponse(
        String runId,
        String action,
        String status,
        int fileCount) {
}
