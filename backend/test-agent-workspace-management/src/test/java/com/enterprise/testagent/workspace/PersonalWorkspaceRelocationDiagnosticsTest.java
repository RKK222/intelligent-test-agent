package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationDiagnostics.Stage;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证安全诊断拒绝任意外部详情，不泄露异常消息或路径。 */
class PersonalWorkspaceRelocationDiagnosticsTest {
    @Test
    void rejectsUntrustedDetailsAndMessages() {
        var error = new PlatformException(ErrorCode.CONFLICT, "token=secret /home/private",
                Map.of("reason", "bad\nsecret", "relocationStage", "private-path", "pathRef", "/home/private"));
        var result = PersonalWorkspaceRelocationDiagnostics.describe(error, Stage.TRANSFER);
        assertThat(result.stage()).isEqualTo(Stage.TRANSFER);
        assertThat(result.reason()).isEqualTo("UNCLASSIFIED");
        assertThat(result.pathRef()).isEqualTo("NONE");
        assertThat(result.safeMessage()).doesNotContain("secret", "/home", "\n");
        assertThat(result.safeMessage().length()).isLessThanOrEqualTo(512);
    }

    @Test
    void retainsGitFailureTypeAndHashesRelativePath() {
        var error = new PlatformException(ErrorCode.CONFLICT, "private",
                Map.of("gitFailureType", "UNMERGED_WORKTREE"));
        var annotated = PersonalWorkspaceRelocationDiagnostics.atStage(error, Stage.CAPTURE_SOURCE_STATE);
        var result = PersonalWorkspaceRelocationDiagnostics.describe(annotated, Stage.EXPORT_SNAPSHOT);
        assertThat(annotated.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(annotated.getCause()).isSameAs(error);
        assertThat(result.stage()).isEqualTo(Stage.CAPTURE_SOURCE_STATE);
        assertThat(result.reason()).isEqualTo("UNMERGED_WORKTREE");
        assertThat(PersonalWorkspaceRelocationDiagnostics.pathRef("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(PersonalWorkspaceRelocationDiagnostics.causeType(
                new IllegalStateException("secret", new java.nio.file.AccessDeniedException("/private"))))
                .isEqualTo("AccessDeniedException");
    }
    @Test
    void logsInternalPathOnlyAndEscapesControlCharacters() {
        var error = new PlatformException(ErrorCode.CONFLICT, "safe", Map.of("filePath", "/untrusted"));
        assertThat(PersonalWorkspaceRelocationDiagnostics.logFilePath(error)).isEqualTo("NONE");
        var annotated = PersonalWorkspaceRelocationDiagnostics.atStage(
                PersonalWorkspaceRelocationDiagnostics.withFilePath(error, ".opencode/a\n\r\t\"\\b"),
                Stage.INSPECT_UNTRACKED);
        assertThat(PersonalWorkspaceRelocationDiagnostics.logFilePath(annotated))
                .isEqualTo(".opencode/a\\u000a\\u000d\\u0009\\\"\\\\b");
        assertThat(annotated.details().toString()).doesNotContain(".opencode");
        assertThat(PersonalWorkspaceRelocationDiagnostics.describe(annotated, Stage.EXPORT_SNAPSHOT)
                .safeMessage()).doesNotContain(".opencode");
        var longPath = PersonalWorkspaceRelocationDiagnostics.withFilePath(error, "a".repeat(3000));
        assertThat(PersonalWorkspaceRelocationDiagnostics.logFilePath(longPath))
                .hasSize(2062).endsWith("...[truncated]");
    }
}
