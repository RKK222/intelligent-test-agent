package com.enterprise.testagent.domain.hub;

import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import java.nio.file.Path;

/**
 * 应用远端 push 成功后的 Hub 索引扩展点。失败不得反转已经成功的 Git push，调用方负责记录并由对账任务重试。
 */
@FunctionalInterface
public interface AgentSkillHubPushIndexer {

    void indexSuccessfulPush(ApplicationWorkspaceVersion version, Path repoRoot, Path workspaceRoot, String commitHash);
}
