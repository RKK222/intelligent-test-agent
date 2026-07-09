package com.icbc.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessGitCommandExecutorTest {

    @Test
    void timeoutDetailsMaskCredentialsInCommand() {
        ProcessGitCommandExecutor executor = new ProcessGitCommandExecutor();

        assertThatThrownBy(() -> executor.execute(
                List.of("/bin/sh", "-c", "sleep 2", "https://token@example.com/team/repo.git"),
                null,
                Duration.ofMillis(10)))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.GIT_TIMEOUT);
                    assertThat(exception.details().get("command").toString())
                            .contains("https://***@example.com/team/repo.git")
                            .doesNotContain("token@example.com");
                    assertThat(exception.details())
                            .containsEntry("gitFailureType", "TIMEOUT")
                            .containsEntry("timeoutMillis", 10L);
                });
    }
}
