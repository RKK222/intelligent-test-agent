package com.example.testagent.app.terminal;

import com.example.testagent.app.config.TestAgentRuntimeProperties;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 创建受控 shell 进程。shell 只能来自 ticket 中后端解析出的默认 shell。
 */
@Component
public class TerminalProcessFactory {

    private final TestAgentRuntimeProperties.Terminal terminalProperties;

    public TerminalProcessFactory(TestAgentRuntimeProperties properties) {
        this.terminalProperties = properties.getTerminal();
    }

    public TerminalProcessSession start(TerminalTicket ticket) {
        try {
            Process process = new ProcessBuilder(List.of(ticket.shell(), "-i"))
                    .directory(ticket.cwd().toFile())
                    .redirectErrorStream(true)
                    .start();
            return new TerminalProcessSession(process, new TerminalOutputLimiter(
                    terminalProperties.getMaxOutputFrameBytes(),
                    terminalProperties.getMaxOutputConnectionBytes()));
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "PTY 后端不可用",
                    Map.of("sessionId", ticket.sessionId().value()),
                    exception);
        }
    }
}
