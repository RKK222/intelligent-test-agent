package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 通用 agent Diff 文件项。
 */
public record AgentDiffFile(
        String path,
        String patch,
        long additions,
        long deletions,
        String status) {

    /**
     * 校验路径和状态，patch 允许为空字符串。
     */
    public AgentDiffFile {
        path = DomainValidation.requireText(path, "path");
        patch = patch == null ? "" : patch;
        status = status == null || status.isBlank() ? "modified" : status.trim();
    }
}
