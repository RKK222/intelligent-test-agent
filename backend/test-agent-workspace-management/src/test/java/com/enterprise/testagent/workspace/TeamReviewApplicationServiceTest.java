package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.enterprise.testagent.common.error.*;
import com.enterprise.testagent.domain.team.TeamReviewModels.*;
import com.enterprise.testagent.domain.team.TeamReviewScopeStore;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 真实来源选择与失败边界的单元契约，不以 UI 快照替代文件版本校验。 */
class TeamReviewApplicationServiceTest {
    private final Source a = new Source("a", "成员甲", "pa", "wa", "server-a");
    private final Source b = new Source("b", "成员乙", "pb", "wb", "server-b");
    private final TeamReviewFileGateway files = mock(TeamReviewFileGateway.class);
    private final TeamReviewScopeStore store = mock(TeamReviewScopeStore.class);
    private final TeamReviewApplicationService service = new TeamReviewApplicationService(store, files);
    private Scope scope() { return new Scope("scope", "admin", "digest", TeamScopeMode.MY_TEAM, "admin", "v", null, List.of(a, b), Instant.now().plusSeconds(60)); }
    private File file(String version, String time, String type) {
        return new File("spec/a.md", "a.md", false, 10, Instant.parse("2026-09-01T00:00:00Z"), version,
                "实际作者", time == null ? null : Instant.parse(time), type, "COMMITTED", false);
    }

    @Test void latestCommittedVersionUsesGitTimeNotMemberOrderOrCloneTime() {
        var entry = TeamReviewApplicationService.aggregate(List.of(new Candidate(a, file("old", "2026-09-01T00:00:00Z", "GIT_COMMIT")),
                new Candidate(b, file("new", "2026-09-02T00:00:00Z", "GIT_COMMIT"))));
        assertThat(entry.selected().source()).isEqualTo(b);
        assertThat(entry.latestUncertain()).isFalse();
    }
    @Test void sameContentDeduplicatesButDifferentDirtyOrTiedContentsRequireChoice() {
        var clean = file("same", "2026-09-02T00:00:00Z", "GIT_COMMIT");
        assertThat(TeamReviewApplicationService.aggregate(List.of(new Candidate(a, clean), new Candidate(b, file("same", null, "FILE_TIME")))).latestUncertain()).isFalse();
        assertThat(TeamReviewApplicationService.aggregate(List.of(new Candidate(a, clean), new Candidate(b, file("dirty", null, "FILE_TIME")))).latestUncertain()).isTrue();
        assertThat(TeamReviewApplicationService.aggregate(List.of(new Candidate(a, clean), new Candidate(b, file("other", "2026-09-02T00:00:00Z", "GIT_COMMIT")))).latestUncertain()).isTrue();
    }
    @Test void unavailableMemberNeverBecomesCompleteOrReadableLatest() {
        var scope = scope();
        when(files.list(scope, a, "spec", "trace")).thenReturn(List.of(file("one", null, "FILE_TIME")));
        when(files.list(scope, b, "spec", "trace")).thenThrow(new PlatformException(ErrorCode.CONFLICT, "offline"));
        var listing = service.list(scope, "spec", "trace");
        assertThat(listing.complete()).isFalse();
        assertThat(listing.unavailableMembers()).containsExactly("成员乙");
        assertThatThrownBy(() -> service.read(scope, "spec/a.md", "one", 0, "trace")).isInstanceOf(PlatformException.class).hasMessageContaining("部分成员");
        verify(files, never()).read(any(), any(), anyString(), anyString(), anyLong(), anyString());
    }
    @Test void fileDirectoryCollisionRequiresMemberAndOverBudgetNeverLooksComplete() {
        var directory = new File("spec/a.md", "a.md", true, 0, null, "directory", null, null, "UNKNOWN", "DIRECTORY", false);
        var entry = TeamReviewApplicationService.aggregate(List.of(new Candidate(a, directory),
                new Candidate(b, file("body", null, "FILE_TIME"))));
        assertThat(entry.directory()).isFalse();
        assertThat(entry.latestUncertain()).isTrue();
        assertThat(entry.alternatives()).hasSize(2);
        var many = new ArrayList<File>();
        for (int i = 0; i < 1001; i++)
            many.add(new File("f" + i, "f" + i, false, 1, null, "sha", null, null, "UNKNOWN", "COMMITTED", false));
        when(files.list(any(), eq(a), eq(""), anyString())).thenReturn(many);
        when(files.list(any(), eq(b), eq(""), anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> service.list(scope(), "", "trace"))
                .isInstanceOf(PlatformException.class).hasMessageContaining("超过 1000");
    }
    @Test void sourceAuthorizationRevocationIsNotReportedAsPartialAvailability() {
        when(files.list(any(), eq(a), anyString(), anyString())).thenReturn(List.of());
        for (ErrorCode code : List.of(ErrorCode.FORBIDDEN, ErrorCode.UNAUTHENTICATED)) {
            when(files.list(any(), eq(b), anyString(), anyString())).thenThrow(new PlatformException(code, "revoked"));
            assertThatThrownBy(() -> service.list(scope(), "", "trace"))
                    .isInstanceOfSatisfying(PlatformException.class, failure -> assertThat(failure.errorCode()).isEqualTo(code));
        }
    }
    @Test void directoryTimeoutInterruptsSourceAndReturnsExplicitIncompleteResult() throws Exception {
        var interrupted = new java.util.concurrent.CountDownLatch(1);
        when(files.list(any(), eq(a), anyString(), anyString())).thenReturn(List.of());
        when(files.list(any(), eq(b), anyString(), anyString())).thenAnswer(call -> {
            try { new java.util.concurrent.CountDownLatch(1).await(); }
            catch (InterruptedException exception) { interrupted.countDown(); throw exception; }
            return List.of();
        });
        var bounded = new TeamReviewApplicationService(store, files, java.time.Duration.ofMillis(100));
        var result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(3),
                () -> bounded.list(scope(), "", "trace"));
        assertThat(result.complete()).isFalse();
        assertThat(result.unavailableMembers()).containsExactly("成员乙");
        assertThat(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    @Test void contentVersionChangeAndPermissionRevocationRejectReads() {
        var scope = scope();
        when(files.list(eq(scope), any(), eq("spec"), eq("trace"))).thenReturn(List.of(file("new", null, "FILE_TIME")));
        assertThatThrownBy(() -> service.read(scope, "spec/a.md", "old", 0, "trace")).hasMessageContaining("已经变化");
        when(store.find("scope")).thenReturn(Optional.of(scope));
        doThrow(new PlatformException(ErrorCode.FORBIDDEN, "revoked")).when(files).authorize(scope);
        assertThatThrownBy(() -> service.requireScope("scope", "admin")).hasMessageContaining("revoked");
        assertThatThrownBy(() -> service.requireScope("scope", "other")).hasMessageContaining("已失效");
    }
    @Test void searchReturnsContinuationInsteadOfSilentlyOmittingThe25thFile() {
        when(files.list(any(), eq(b), anyString(), anyString())).thenReturn(List.of());
        when(files.list(any(), eq(a), anyString(), anyString())).thenAnswer(call -> {
            String path = call.getArgument(2);
            if (!path.isEmpty()) return List.of(new File(path + "/a.md", "a.md", false, 1, null, "sha", null, null, "UNKNOWN", "COMMITTED", false));
            List<File> dirs = new ArrayList<>();
            for (int i = 0; i < 40; i++) dirs.add(new File("d" + i, "d" + i, true, 0, null, "directory", null, null, "UNKNOWN", "DIRECTORY", false));
            return dirs;
        });
        var first = service.search(scope(), "", ".md", null, "trace");
        assertThat(first.entries()).hasSize(31);
        assertThat(first.complete()).isFalse();
        var second = service.search(scope(), "", ".md", first.remainingDirectories(), "trace");
        assertThat(second.entries()).hasSize(9);
        assertThat(second.complete()).isTrue();
    }
    @Test void pathTraversalAndGitDirectoryAreForbidden() {
        for (String path : List.of("../secret", "/absolute", "a/../b", "a/.git/config", "C:\\private"))
            assertThatThrownBy(() -> TeamReviewApplicationService.relativePath(path, false)).isInstanceOf(PlatformException.class);
    }
}
