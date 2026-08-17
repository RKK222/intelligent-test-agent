package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void timeoutTerminatesDescendantProcess(@TempDir Path tempDir) throws Exception {
        ProcessGitCommandExecutor executor = new ProcessGitCommandExecutor();
        Path childPidFile = tempDir.resolve("child.pid");

        assertThatThrownBy(() -> executor.execute(
                        List.of(
                                "/bin/sh",
                                "-c",
                                "sleep 30 & child=$!; printf '%s' \"$child\" > \"$1\"; wait \"$child\"",
                                "sh",
                                childPidFile.toString()),
                        null,
                        Duration.ofMillis(100)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.GIT_TIMEOUT));

        long childPid = Long.parseLong(Files.readString(childPidFile));
        boolean childStopped = waitUntilStopped(childPid, Duration.ofSeconds(2));
        assertThat(childStopped).as("超时后 shell 启动的子进程应被一并终止").isTrue();
    }

    private static boolean waitUntilStopped(long pid, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                TimeUnit.MILLISECONDS.sleep(20);
                continue;
            }
            return true;
        }
        return !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    @Test
    void safeCommandMasksSshUserButKeepsInternalSshShape() {
        String command = ProcessGitCommandExecutor.safeCommand(List.of(
                "git",
                "ls-remote",
                "--heads",
                "ssh://001177621@scm-share.sdc.cs.enterprise:29418/testagent/config"));

        assertThat(command)
                .isEqualTo("git ls-remote --heads ssh://***@scm-share.sdc.cs.enterprise:29418/testagent/config")
                .doesNotContain("001177621");
    }

    @Test
    void scopedRedactionMasksExperienceWorkspacePathInCommandDetailsAndLogs() {
        String sensitivePath = "/srv/platform/private-experience";
        ProcessGitCommandExecutor executor = new ProcessGitCommandExecutor();

        try (GitCommandExecutor.LogRedaction ignored =
                        GitCommandExecutor.redactSensitiveArguments(List.of(sensitivePath));
                CapturedGitLogger logs = CapturedGitLogger.attach()) {
            assertThatThrownBy(() -> executor.execute(
                            List.of(
                                    "/bin/sh",
                                    "-c",
                                    "printf 'fatal: cannot change to " + sensitivePath + "' >&2; exit 128",
                                    sensitivePath),
                            null,
                            Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(PlatformException.class, exception -> {
                        assertThat(exception.details().get("command").toString())
                                .contains("<redacted-local-path>")
                                .doesNotContain(sensitivePath);
                        assertThat(exception.details().get("stderr").toString())
                                .contains("<redacted-local-path>")
                                .doesNotContain(sensitivePath);
                    });

            assertThat(String.join("\n", logs.messages()))
                    .contains("<redacted-local-path>")
                    .doesNotContain(sensitivePath);
        }
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
                            "printf '001177621@scm-share.sdc.cs.enterprise: Permission denied (publickey).' >&2; exit 128"),
                    null,
                    Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(PlatformException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.GIT_UNAVAILABLE);
                        assertThat(exception.details().get("stderr").toString())
                                .contains("***@scm-share.sdc.cs.enterprise: Permission denied (publickey).")
                                .doesNotContain("001177621");
                        assertThat(exception.details())
                                .containsEntry("gitFailureType", "AUTHENTICATION_FAILED");
                    });

            String messages = String.join("\n", logs.messages());
            assertThat(messages)
                    .contains("event=git_command_failed")
                    .contains("failureType=AUTHENTICATION_FAILED")
                    .contains("stderr=***@scm-share.sdc.cs.enterprise: Permission denied (publickey).")
                    .doesNotContain("001177621");
        }
    }

    @Test
    void parsesScmIdentityMismatchWithoutLeakingIdentityIntoDetailsOrLogs(@TempDir Path tempDir) throws Exception {
        ProcessGitCommandExecutor executor = new ProcessGitCommandExecutor();
        String stderr = "remote: server internel error(right-control): commit:644d15a提交失败, "
                + "客户端提交者邮箱123456789@mails.icbc对应的姓名应为测试用户,"
                + "您的提交者姓名为测试用户1,校验不一致, 请在客户端修正后重新提交!";
        Path stderrFile = tempDir.resolve("scm-stderr.txt");
        Files.writeString(stderrFile, stderr);

        try (CapturedGitLogger logs = CapturedGitLogger.attach()) {
            assertThatThrownBy(() -> executor.execute(
                    List.of("/bin/sh", "-c", "cat \"$1\" >&2; exit 1", "sh", stderrFile.toString()),
                    null,
                    Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(ScmGitIdentityRejectedException.class, exception -> {
                        assertThat(exception.expectedName()).isEqualTo("测试用户");
                        assertThat(exception.actualName()).isEqualTo("测试用户1");
                        assertThat(exception.email()).isEqualTo("123456789@mails.icbc");
                        assertThat(exception.evidenceCommit()).isEqualTo("644d15a");
                        assertThat(exception.details())
                                .containsEntry("gitFailureReason", "SCM_IDENTITY_MISMATCH")
                                .doesNotContainKeys("stderr");
                        assertThat(exception.details().toString())
                                .doesNotContain("测试用户", "123456789");
                    });

            assertThat(String.join("\n", logs.messages()))
                    .contains("failureReason=SCM_IDENTITY_MISMATCH")
                    .doesNotContain("测试用户", "123456789");
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
