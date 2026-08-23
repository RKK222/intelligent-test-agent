package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommandExecutor;
import com.enterprise.testagent.common.git.GitCommandResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGitAccessCheckerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldReportAccessibleWhenOriginCanBeRead() {
        LocalGitAccessChecker.GitAccessResult result =
                new LocalGitAccessChecker(executor(null)).check(temporaryDirectory.toString());

        assertThat(result.status()).isEqualTo("ACCESSIBLE");
        assertThat(result.reason()).isNull();
        assertThat(result.message()).isNull();
    }

    @Test
    void shouldDisableOnlyForAuthenticationOrRepositoryDenial() {
        PlatformException denied = new PlatformException(
                ErrorCode.GIT_UNAVAILABLE,
                "Git 不可用",
                Map.of("gitFailureType", "AUTHENTICATION_FAILED"));

        LocalGitAccessChecker.GitAccessResult result =
                new LocalGitAccessChecker(executor(denied)).check(temporaryDirectory.toString());

        assertThat(result.status()).isEqualTo("INACCESSIBLE");
        assertThat(result.reason()).isEqualTo("REPOSITORY_PERMISSION_REQUIRED");
        assertThat(result.message()).isEqualTo("Git 仓库读取权限已失效");
    }

    @Test
    void shouldKeepWorkspaceSelectableWhenNetworkIsUnavailable() {
        PlatformException unavailable = new PlatformException(
                ErrorCode.GIT_UNAVAILABLE,
                "Git 不可用",
                Map.of("gitFailureType", "NETWORK_UNAVAILABLE"));

        LocalGitAccessChecker.GitAccessResult result =
                new LocalGitAccessChecker(executor(unavailable)).check(temporaryDirectory.toString());

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.reason()).isEqualTo("NETWORK_UNAVAILABLE");
    }

    private static GitCommandExecutor executor(PlatformException remoteFailure) {
        return (List<String> command, String privateKey, Duration timeout) -> {
            if (command.contains("rev-parse")) {
                return result("true\n");
            }
            if (command.contains("config")) {
                return result("git@example.test:team/repository.git\n");
            }
            if (command.contains("ls-remote") && remoteFailure != null) {
                throw remoteFailure;
            }
            return result("abc123\trefs/heads/main\n");
        };
    }

    private static GitCommandResult result(String stdout) {
        return new GitCommandResult(0, stdout, stdout.getBytes(StandardCharsets.UTF_8));
    }
}
