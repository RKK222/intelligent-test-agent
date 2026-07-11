package com.icbc.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
}
