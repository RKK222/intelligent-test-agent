package com.enterprise.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class TerminalProcessFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void interactiveShellExplicitlyReadsCommandsFromStandardInput() {
        assertThat(TerminalProcessFactory.shellCommand("/bin/zsh"))
                .containsExactly("/bin/zsh", "-i", "-s");
        assertThat(TerminalProcessFactory.shellCommand("/usr/local/bin/bash"))
                .containsExactly("/usr/local/bin/bash", "-i", "-s");
        assertThat(TerminalProcessFactory.shellCommand("/bin/sh"))
                .containsExactly("/bin/sh", "-i", "-s");
        assertThatThrownBy(() -> TerminalProcessFactory.shellCommand("/bin/fish"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported shell");
    }

    @Test
    void serverShellUsesPlatformRcFileAndOnlyAcceptsBash() {
        Path rcFile = tempDir.resolve("server-terminal.bashrc");

        assertThat(TerminalProcessFactory.serverShellCommand("/bin/bash", rcFile))
                .containsExactly("/bin/bash", "--rcfile", rcFile.toString(), "-i", "-s");
        assertThatThrownBy(() -> TerminalProcessFactory.serverShellCommand("/bin/zsh", rcFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server shell must be bash");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void startedShellReadsCommandsFromStandardInput() {
        TerminalProcessSession session = new TerminalProcessFactory(16 * 1024, 1024 * 1024)
                .start(ticket("/bin/sh"));

        session.input("printf 'terminal-factory-marker\\n'\nexit\n").block(Duration.ofSeconds(2));
        String output = session.output()
                .filter(message -> "output".equals(message.type()))
                .map(TerminalServerMessage::data)
                .filter(data -> data.contains("terminal-factory-marker"))
                .blockFirst(Duration.ofSeconds(5));

        assertThat(output).contains("terminal-factory-marker");
    }

    @Test
    void serverShellEnvironmentUsesJavaUserWithoutSensitiveVariables() {
        Map<String, String> environment = TerminalProcessFactory.serverShellEnvironment();

        assertThat(environment.get("USER")).isEqualTo(System.getProperty("user.name"));
        assertThat(environment.get("HOME")).isEqualTo(System.getProperty("user.home"));
        assertThat(environment)
                .containsEntry("SHELL", "/bin/bash")
                .containsEntry("COLORTERM", "truecolor")
                .containsEntry("CLICOLOR", "1");
        assertThat(environment).doesNotContainKeys(
                "TEST_AGENT_TEST_DB_PASSWORD",
                "TEST_AGENT_REDIS_PASSWORD",
                "ENTERPRISE_OPENAI_AUTH_TOKEN");
    }

    @Test
    void bundledServerShellRcDefinesColorsWithoutModifyingUserConfiguration() throws Exception {
        try (var input = TerminalProcessFactory.class.getResourceAsStream("/terminal/server-terminal.bashrc")) {
            assertThat(input).isNotNull();
            String rcFile = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(rcFile)
                    .contains("PS1=")
                    .contains("alias ls='ls --color=auto'")
                    .contains("alias grep='grep --color=auto'")
                    .contains("alias git='git -c color.ui=auto'")
                    .contains(". \"$HOME/.bashrc\"");
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void startedServerShellLoadsPlatformColorEnvironment() {
        TerminalProcessSession session = new TerminalProcessFactory(16 * 1024, 1024 * 1024)
                .start(serverTicket());

        session.input("printf 'server-color:%s:%s\\n' \"$COLORTERM\" \"$CLICOLOR\"\nalias ls\nalias grep\nalias git\nexit\n")
                .block(Duration.ofSeconds(2));
        List<TerminalServerMessage> messages = session.output()
                .collectList()
                .block(Duration.ofSeconds(5));
        String output = messages == null ? "" : messages.stream()
                .filter(message -> "output".equals(message.type()))
                .map(TerminalServerMessage::data)
                .reduce("", String::concat);

        assertThat(output)
                .contains("server-color:truecolor:1")
                .contains("alias ls=")
                .contains("alias grep=")
                .contains("alias git=");
    }

    private TerminalTicket ticket(String shell) {
        return new TerminalTicket(
                "pty_1234567890abcdef",
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                new ExecutionNodeId("node_1234567890abcdef"),
                tempDir,
                tempDir,
                shell,
                80,
                24,
                "trace_1234567890abcdef",
                Instant.now().plusSeconds(60));
    }

    private TerminalTicket serverTicket() {
        return new TerminalTicket(
                "pty_1234567890abcdef",
                TerminalTicket.TARGET_SERVER_SHELL,
                null,
                null,
                null,
                new LinuxServerId("server-local"),
                new UserId("user-super-admin"),
                tempDir,
                tempDir,
                "/bin/bash",
                80,
                24,
                "trace_1234567890abcdef",
                Instant.now().plusSeconds(60));
    }
}
