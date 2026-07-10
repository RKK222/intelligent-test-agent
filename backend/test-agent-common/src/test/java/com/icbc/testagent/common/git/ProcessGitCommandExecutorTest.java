package com.icbc.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

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

    @Test
    void safeCommandMasksSshUserButKeepsInternalSshShape() {
        String command = ProcessGitCommandExecutor.safeCommand(List.of(
                "git",
                "ls-remote",
                "--heads",
                "ssh://001177621@scm-share.sdc.cs.icbc:29418/testagent/config"));

        assertThat(command)
                .isEqualTo("git ls-remote --heads ssh://***@scm-share.sdc.cs.icbc:29418/testagent/config")
                .doesNotContain("001177621");
    }

    @Test
    void executeOutputsStartAndSuccessLogs() {
        ProcessGitCommandExecutor executor = new ProcessGitCommandExecutor();

        try (CapturedGitLogger logs = CapturedGitLogger.attach()) {
            GitCommandResult result = executor.execute(List.of("/bin/sh", "-c", "printf ok"), null, Duration.ofSeconds(1));

            assertThat(result.stdoutText()).isEqualTo("ok");
            assertThat(logs.messages())
                    .anySatisfy(message -> assertThat(message)
                            .contains("event=git_command_start")
                            .contains("command=/bin/sh -c printf ok"))
                    .anySatisfy(message -> assertThat(message)
                            .contains("event=git_command_success")
                            .contains("command=/bin/sh -c printf ok"));
        }
    }

    @Test
    void failedCommandMasksSshPrincipalInLogsAndDetails() {
        ProcessGitCommandExecutor executor = new ProcessGitCommandExecutor();

        try (CapturedGitLogger logs = CapturedGitLogger.attach()) {
            assertThatThrownBy(() -> executor.execute(
                    List.of(
                            "/bin/sh",
                            "-c",
                            "printf '001177621@scm-share.sdc.cs.icbc: Permission denied (publickey).' >&2; exit 128"),
                    null,
                    Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(PlatformException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.GIT_UNAVAILABLE);
                        assertThat(exception.details().get("stderr").toString())
                                .contains("***@scm-share.sdc.cs.icbc: Permission denied (publickey).")
                                .doesNotContain("001177621");
                        assertThat(exception.details())
                                .containsEntry("gitFailureType", "AUTHENTICATION_FAILED");
                    });

            String messages = String.join("\n", logs.messages());
            assertThat(messages)
                    .contains("event=git_command_failed")
                    .contains("failureType=AUTHENTICATION_FAILED")
                    .contains("stderr=***@scm-share.sdc.cs.icbc: Permission denied (publickey).")
                    .doesNotContain("001177621");
        }
    }

    private static final class InMemoryLogAppender extends AbstractAppender {
        private final List<String> messages = new ArrayList<>();

        private InMemoryLogAppender(String name) {
            super(name, null, null, false, null);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        private List<String> messages() {
            return messages;
        }
    }

    private static final class CapturedGitLogger implements AutoCloseable {
        private final InMemoryLogAppender appender = new InMemoryLogAppender("in-memory-git-log-" + System.nanoTime());
        private final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        private final Configuration configuration = context.getConfiguration();
        private final LoggerConfig loggerConfig = configuration.getLoggerConfig(ProcessGitCommandExecutor.class.getName());
        private final Level originalLevel = loggerConfig.getLevel();

        private static CapturedGitLogger attach() {
            CapturedGitLogger logs = new CapturedGitLogger();
            logs.appender.start();
            logs.configuration.addAppender(logs.appender);
            logs.loggerConfig.setLevel(Level.INFO);
            logs.loggerConfig.addAppender(logs.appender, Level.INFO, null);
            logs.context.updateLoggers();
            return logs;
        }

        private List<String> messages() {
            return appender.messages();
        }

        @Override
        public void close() {
            loggerConfig.removeAppender(appender.getName());
            loggerConfig.setLevel(originalLevel);
            appender.stop();
            context.updateLoggers();
        }
    }
}
