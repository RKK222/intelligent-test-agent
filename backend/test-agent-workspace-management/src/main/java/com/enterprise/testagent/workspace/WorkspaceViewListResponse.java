package com.enterprise.testagent.workspace;

import java.util.List;

/** 工作区组合视图目录列表响应。 */
public record WorkspaceViewListResponse(
        List<WorkspaceViewEntry> entries,
        List<WorkspaceViewWarning> warnings,
        boolean truncated) {

    public WorkspaceViewListResponse {
        entries = List.copyOf(entries);
        warnings = List.copyOf(warnings);
    }
}
