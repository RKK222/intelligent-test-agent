package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GitPublicAgentConfigRuntimeImpactResolverTest {

    @Test
    void publicToolTypeScriptCommitRequiresProcessRestart() {
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        Path gitRoot = Path.of("/config/repository");
        when(parameters.resolvedValue("OPENCODE_PUBLIC_CONFIG_GIT_ROOT"))
                .thenReturn(Optional.of(gitRoot.toString()));
        when(git.isGitRepository(gitRoot)).thenReturn(true);
        when(git.diffNameStatusBetweenTrees(gitRoot, "commit-old", "commit-new"))
                .thenReturn("M\topencode/tools/workspace-git.ts\n");
        GitPublicAgentConfigRuntimeImpactResolver resolver =
                new GitPublicAgentConfigRuntimeImpactResolver(parameters, git);

        boolean restart = resolver.requiresProcessRestart(
                AgentConfigRolloutScope.PUBLIC, null, "commit-old", "commit-new");

        assertThat(restart).isTrue();
    }

    @Test
    void nestedToolHelperRequiresRestartWhileAgentMarkdownDoesNot() {
        assertThat(GitPublicAgentConfigRuntimeImpactResolver.hasCommittedToolModuleChange(
                "M\topencode/tools/helpers/client.ts\n"))
                .isTrue();
        assertThat(GitPublicAgentConfigRuntimeImpactResolver.hasCommittedToolModuleChange(
                "M\topencode/agents/reviewer.md\n"))
                .isFalse();
    }

    @Test
    void applicationToolCommitUsesCurrentServerVersionReplica() {
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        ManagedWorkspaceRepository repository = mock(ManagedWorkspaceRepository.class);
        WorkspaceServerIdentity serverIdentity = mock(WorkspaceServerIdentity.class);
        ManagedWorkspacePathResolver pathResolver = mock(ManagedWorkspacePathResolver.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        ApplicationWorkspaceVersionReplica replica = mock(ApplicationWorkspaceVersionReplica.class);
        Path gitRoot = Path.of("/managed/repo");
        Path workspaceRoot = gitRoot.resolve("F-GCMS/workspace");
        when(serverIdentity.linuxServerId()).thenReturn("linux-1");
        when(repository.findVersionReplica(new ApplicationWorkspaceVersionId("awv_1"), "linux-1"))
                .thenReturn(Optional.of(replica));
        when(replica.repoRootPath()).thenReturn("/virtual/repo");
        when(replica.workspaceRootPath()).thenReturn("/virtual/repo/F-GCMS/workspace");
        when(pathResolver.resolve("/virtual/repo")).thenReturn(gitRoot);
        when(pathResolver.resolve("/virtual/repo/F-GCMS/workspace")).thenReturn(workspaceRoot);
        when(git.isGitRepository(gitRoot)).thenReturn(true);
        when(git.diffNameStatusBetweenTrees(gitRoot, "commit-old", "commit-new"))
                .thenReturn("M\tF-GCMS/workspace/.opencode/tools/bank-query.ts\n");
        GitPublicAgentConfigRuntimeImpactResolver resolver =
                new GitPublicAgentConfigRuntimeImpactResolver(
                        parameters, repository, serverIdentity, pathResolver, git);

        boolean restart = resolver.requiresProcessRestart(
                AgentConfigRolloutScope.APPLICATION, "awv_1", "commit-old", "commit-new");

        assertThat(restart).isTrue();
    }

    @Test
    void applicationNestedToolHelperRequiresRestart() {
        assertThat(GitPublicAgentConfigRuntimeImpactResolver.hasCommittedApplicationToolModuleChange(
                "M\tF-GCMS/workspace/.opencode/tools/helpers/client.ts\n",
                "F-GCMS/workspace"))
                .isTrue();
    }
}
