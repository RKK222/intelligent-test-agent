package com.enterprise.testagent.opencode.runtime.terminal;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.file.Path;
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
            // 终端当前以受控 stdin/stdout 管道承载交互；-s 明确要求 shell 从 stdin 读取命令，
            // 避免 zsh 等交互 shell 在没有原生 TTY 时只完成启动脚本却不继续消费输入。
            Process process = new ProcessBuilder(shellCommand(ticket.shell()))
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

    static List<String> shellCommand(String shell) {
        String executable = Path.of(shell).getFileName().toString();
        if (!List.of("sh", "bash", "zsh").contains(executable)) {
            throw new IllegalArgumentException("unsupported shell: " + executable);
        }
        return List.of(shell, "-i", "-s");
    }
}
