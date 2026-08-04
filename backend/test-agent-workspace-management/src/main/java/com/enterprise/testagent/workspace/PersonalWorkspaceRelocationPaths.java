package com.enterprise.testagent.workspace;

import java.nio.file.Path;

/** 搬迁期间解析出的受控 Git 路径；仅在业务模块内部传递，不进入 API 或日志。 */
record PersonalWorkspaceRelocationPaths(
        Path applicationRepoRoot,
        Path personalRepoRoot,
        Path workspaceRoot,
        String personalRepoRootValue,
        String workspaceRootValue) {
}
