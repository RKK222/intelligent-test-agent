package com.enterprise.testagent.workspace;

import java.time.Instant;
import java.util.List;

/** 工作区组合视图的一层目录项。 */
public record WorkspaceViewEntry(
        String id,
        String path,
        String name,
        boolean directory,
        long size,
        Instant lastModifiedAt,
        WorkspaceViewLocator locator,
        WorkspaceViewSource source,
        boolean merged,
        boolean collision,
        boolean readonly,
        String workspacePath,
        List<String> referenceAliases) {

    public WorkspaceViewEntry {
        referenceAliases = referenceAliases == null ? List.of() : List.copyOf(referenceAliases);
    }
}
