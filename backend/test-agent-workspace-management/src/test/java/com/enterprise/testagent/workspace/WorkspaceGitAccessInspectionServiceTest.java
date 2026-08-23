package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheck;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheckRepository;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkspaceGitAccessInspectionServiceTest {

    @Test
    void shouldPersistEveryCompletedRemoteProbeFailureAsInaccessible() {
        WorkspaceGitAccessCheckRepository checks = mock(WorkspaceGitAccessCheckRepository.class);
        ManagedWorkspaceApplicationService managed = mock(ManagedWorkspaceApplicationService.class);
        ScheduledTaskContext context = mock(ScheduledTaskContext.class);
        UserId userId = new UserId("usr_1");
        var candidates = List.of(
                new WorkspaceGitAccessCheckRepository.ApplicationWorkspaceCandidate(userId, "awp_ok", "awv_ok"),
                new WorkspaceGitAccessCheckRepository.ApplicationWorkspaceCandidate(userId, "awp_denied", "awv_denied"),
                new WorkspaceGitAccessCheckRepository.ApplicationWorkspaceCandidate(userId, "awp_network", "awv_network"));
        when(checks.findApplicationWorkspaceCandidatesAfter(isNull(), isNull(), anyInt()))
                .thenReturn(candidates);
        Instant inspectionStartedAt = Instant.parse("2026-08-23T12:00:00Z");
        when(managed.checkVersionGitAccess("awv_ok", userId, inspectionStartedAt)).thenReturn(
                new ManagedWorkspaceResponses.GitRepositoryAccessResponse(
                        true, "repo_ok", "可访问仓库", "main", null));
        when(managed.checkVersionGitAccess("awv_denied", userId, inspectionStartedAt)).thenReturn(
                new ManagedWorkspaceResponses.GitRepositoryAccessResponse(
                        false, "repo_denied", "无权限仓库", "main", "REPOSITORY_PERMISSION_REQUIRED"));
        when(managed.checkVersionGitAccess("awv_network", userId, inspectionStartedAt)).thenThrow(new PlatformException(
                ErrorCode.GIT_UNAVAILABLE,
                "Git 网络不可用",
                Map.of("gitFailureType", "NETWORK_UNAVAILABLE")));
        WorkspaceGitAccessInspectionService service = new WorkspaceGitAccessInspectionService(
                checks,
                managed,
                Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC));

        WorkspaceGitAccessInspectionService.InspectionResult result =
                service.inspectApplicationWorkspaces(context);

        assertThat(result).isEqualTo(new WorkspaceGitAccessInspectionService.InspectionResult(3, 1, 2, 0));
        ArgumentCaptor<WorkspaceGitAccessCheck> saved = ArgumentCaptor.forClass(WorkspaceGitAccessCheck.class);
        verify(checks, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(WorkspaceGitAccessCheck::targetId, WorkspaceGitAccessCheck::status)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("awp_ok", WorkspaceGitAccessCheck.Status.ACCESSIBLE),
                        org.assertj.core.groups.Tuple.tuple("awp_denied", WorkspaceGitAccessCheck.Status.INACCESSIBLE),
                        org.assertj.core.groups.Tuple.tuple("awp_network", WorkspaceGitAccessCheck.Status.INACCESSIBLE));
        assertThat(saved.getAllValues().stream()
                .filter(value -> value.targetId().equals("awp_denied"))
                .findFirst().orElseThrow().message()).isEqualTo("Git 仓库读取权限已失效");
        assertThat(saved.getAllValues().stream()
                .filter(value -> value.targetId().equals("awp_network"))
                .findFirst().orElseThrow().message()).contains("SSL/TLS");
    }
}
