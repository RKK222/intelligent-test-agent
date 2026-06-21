package com.icbc.testagent.opencode.runtime.terminal;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 创建受控 shell 进程。shell 只能来自 ticket 中后端解析出的默认 shell。
 */
@Component
public class TerminalProcessFactory {

    private final int maxOutputFrameBytes;
    private final int maxOutputConnectionBytes;

    /**
     * 创建进程工厂，并归一化输出预算配置。
     */
    public TerminalProcessFactory(
            @Value("${test-agent.terminal.max-output-frame-bytes:16384}") int maxOutputFrameBytes,
            @Value("${test-agent.terminal.max-output-connection-bytes:1048576}") int maxOutputConnectionBytes) {
        this.maxOutputFrameBytes = Math.max(1, maxOutputFrameBytes);
        this.maxOutputConnectionBytes = Math.max(this.maxOutputFrameBytes, maxOutputConnectionBytes);
    }

    /**
     * 启动受控交互式 shell 进程，cwd 和 shell 均来自已校验 ticket。
     */
    public TerminalProcessSession start(TerminalTicket ticket) {
        try {
            Process process = new ProcessBuilder(List.of(ticket.shell(), "-i"))
                    .directory(ticket.cwd().toFile())
                    .redirectErrorStream(true)
                    .start();
            return new TerminalProcessSession(process, new TerminalOutputLimiter(
                    maxOutputFrameBytes,
                    maxOutputConnectionBytes));
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "PTY 后端不可用",
                    Map.of("sessionId", ticket.sessionId().value()),
                    exception);
        }
    }
}
