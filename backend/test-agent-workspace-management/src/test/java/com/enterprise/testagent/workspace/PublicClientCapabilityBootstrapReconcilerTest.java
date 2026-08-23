package com.enterprise.testagent.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskLock;
import com.enterprise.testagent.scheduler.ScheduledTaskLockLease;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证历史公共 HEAD 只在缺少能力制品且取得全局锁时补建。 */
class PublicClientCapabilityBootstrapReconcilerTest {

    private static final String COMMIT = "a".repeat(40);

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesCurrentPublicHeadWhenNoHistoricalReleaseExists() {
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        PublicClientCapabilityPackageService packages = mock(PublicClientCapabilityPackageService.class);
        ScheduledTaskLock lock = mock(ScheduledTaskLock.class);
        ScheduledTaskLockLease lease = mock(ScheduledTaskLockLease.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        Path gitRoot = temporaryDirectory.resolve("public-config");
        when(parameters.resolvedValue(PublicClientCapabilityBootstrapReconciler.PUBLIC_CONFIG_GIT_ROOT))
                .thenReturn(Optional.of(gitRoot.toString()));
        when(git.isGitRepository(gitRoot)).thenReturn(true);
        when(git.headCommit(gitRoot)).thenReturn(COMMIT);
        when(packages.releaseForCommit(COMMIT)).thenReturn(null);
        when(lock.acquire(any(ScheduledTaskKey.class), eq(Duration.ofMinutes(5))))
                .thenReturn(Optional.of(lease));
        PublicClientCapabilityBootstrapReconciler reconciler = new PublicClientCapabilityBootstrapReconciler(
                parameters, packages, lock, git, true, Duration.ofSeconds(60));

        reconciler.reconcileOnce("trace_bootstrap");

        verify(packages).generateForPublishedCommit(gitRoot, COMMIT, "trace_bootstrap");
        verify(lease).close();
    }

    @Test
    void skipsUnconfiguredPublicRepository() {
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        PublicClientCapabilityPackageService packages = mock(PublicClientCapabilityPackageService.class);
        ScheduledTaskLock lock = mock(ScheduledTaskLock.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        when(parameters.resolvedValue(PublicClientCapabilityBootstrapReconciler.PUBLIC_CONFIG_GIT_ROOT))
                .thenReturn(Optional.empty());
        PublicClientCapabilityBootstrapReconciler reconciler = new PublicClientCapabilityBootstrapReconciler(
                parameters, packages, lock, git, true, Duration.ofSeconds(60));

        reconciler.reconcileOnce("trace_skip");

        verify(git, never()).headCommit(any());
        verify(lock, never()).acquire(any(), any());
        verify(packages, never()).generateForPublishedCommit(any(), any(), any());
    }
}
