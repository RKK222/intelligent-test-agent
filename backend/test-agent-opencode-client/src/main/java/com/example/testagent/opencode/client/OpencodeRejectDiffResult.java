package com.example.testagent.opencode.client;

/**
 * opencode Diff 拒绝结果；只表达平台关心的 Run 级回滚是否已提交。
 */
public record OpencodeRejectDiffResult(boolean rejected) {
}
