package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationDiagnostics.Stage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 不依赖操作系统创建链接权限，验证特殊条目拒绝时的阶段与安全定位。 */
class PersonalWorkspaceSnapshotDiagnosticsTest {
    @TempDir
    Path root;

    @Test
    void capturesUnsupportedUntrackedEntryStageAndPathWithoutWritingArchive() throws Exception {
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        Path source = Files.createDirectories(root.resolve("source"));
        String relative = ".opencode/private-entry";
        Files.createDirectories(source.resolve(relative));
        when(git.untrackedPaths(source)).thenReturn(List.of(relative));
        var service = new PersonalWorkspaceSnapshotService(git, new ObjectMapper());
        Path archive = root.resolve("snapshot.zip");
        PlatformException error = catchThrowableOfType(PlatformException.class,
                () -> service.exportSnapshot(source, archive, "pwr_test"));
        assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        var failure = PersonalWorkspaceRelocationDiagnostics.describe(error, Stage.EXPORT_SNAPSHOT);
        assertThat(failure.stage()).isEqualTo(Stage.INSPECT_UNTRACKED);
        assertThat(failure.reason()).isEqualTo("UNSUPPORTED_UNTRACKED_ENTRY");
        assertThat(failure.pathRef()).isEqualTo(PersonalWorkspaceRelocationDiagnostics.pathRef(relative));
        assertThat(PersonalWorkspaceRelocationDiagnostics.logFilePath(error)).isEqualTo(relative);
        assertThat(error.details().toString()).doesNotContain(relative);
        assertThat(failure.safeMessage()).doesNotContain("private-entry", source.toString());
        assertThat(Files.exists(archive)).isFalse();
        assertThat(Files.isDirectory(source.resolve(relative))).isTrue();
    }

    @Test
    void capturesInitialGitFailureWithoutChangingCode() throws Exception {
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        Path source = Files.createDirectories(root.resolve("source"));
        when(git.capturePortableTrackedState(source)).thenThrow(new PlatformException(ErrorCode.CONFLICT,
                "private", java.util.Map.of("gitFailureType", "UNMERGED_WORKTREE")));
        var service = new PersonalWorkspaceSnapshotService(git, new ObjectMapper());
        PlatformException error = catchThrowableOfType(PlatformException.class,
                () -> service.exportSnapshot(source, root.resolve("snapshot.zip"), "pwr_test"));
        assertThat(PersonalWorkspaceRelocationDiagnostics.describe(error, Stage.EXPORT_SNAPSHOT).stage())
                .isEqualTo(Stage.CAPTURE_SOURCE_STATE);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void identifiesFileChangedDuringArchiveByFingerprint() throws Exception {
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        Path source = Files.createDirectories(root.resolve("source"));
        String relative = "private.txt";
        Files.writeString(source.resolve(relative), "before");
        var state = new GitWorkspaceService.PortableTrackedState("a".repeat(40), "b".repeat(40), "c".repeat(40), null);
        when(git.capturePortableTrackedState(source)).thenReturn(state);
        when(git.untrackedPaths(source)).thenReturn(List.of(relative));
        // 先采集指纹，再在 bundle 阶段模拟并发写入，确保归档校验能准确定位而不吞掉冲突。
        org.mockito.Mockito.doAnswer(invocation -> {
            Files.writeString(source.resolve(relative), "after");
            return null;
        }).when(git).createPortableBundle(org.mockito.ArgumentMatchers.eq(source),
                org.mockito.ArgumentMatchers.any(Path.class), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(state));
        var service = new PersonalWorkspaceSnapshotService(git, new ObjectMapper());
        PlatformException error = catchThrowableOfType(PlatformException.class,
                () -> service.exportSnapshot(source, root.resolve("snapshot.zip"), "pwr_test"));
        var failure = PersonalWorkspaceRelocationDiagnostics.describe(error, Stage.EXPORT_SNAPSHOT);
        assertThat(failure.stage()).isEqualTo(Stage.WRITE_ARCHIVE);
        assertThat(failure.reason()).isEqualTo("SOURCE_FILE_CHANGED_DURING_ARCHIVE");
        assertThat(failure.pathRef()).isEqualTo(PersonalWorkspaceRelocationDiagnostics.pathRef(relative));
        assertThat(PersonalWorkspaceRelocationDiagnostics.logFilePath(error)).isEqualTo(relative);
        assertThat(error.details().toString()).doesNotContain(relative);
        assertThat(Files.readString(source.resolve(relative))).isEqualTo("after");
    }

    @Test
    void identifiesTrackedStateChangedAfterArchive() throws Exception {
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        Path source = Files.createDirectories(root.resolve("source"));
        var before = new GitWorkspaceService.PortableTrackedState("a".repeat(40), "b".repeat(40), "c".repeat(40), null);
        var after = new GitWorkspaceService.PortableTrackedState("a".repeat(40), "b".repeat(40), "d".repeat(40), null);
        when(git.capturePortableTrackedState(source)).thenReturn(before, after);
        when(git.untrackedPaths(source)).thenReturn(List.of());
        var service = new PersonalWorkspaceSnapshotService(git, new ObjectMapper());
        PlatformException error = catchThrowableOfType(PlatformException.class,
                () -> service.exportSnapshot(source, root.resolve("snapshot.zip"), "pwr_test"));
        var failure = PersonalWorkspaceRelocationDiagnostics.describe(error, Stage.EXPORT_SNAPSHOT);
        assertThat(failure.stage()).isEqualTo(Stage.VERIFY_SOURCE_STATE);
        assertThat(failure.reason()).isEqualTo("SOURCE_CHANGED_DURING_SNAPSHOT");
        assertThat(failure.pathRef()).isEqualTo("NONE");
        assertThat(Files.exists(root.resolve("snapshot.zip"))).isFalse();
    }
}
