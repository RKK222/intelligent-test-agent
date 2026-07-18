package com.enterprise.testagent.opencode.runtime.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
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
            List<String> command = shellCommand(ticket.shell());
            PtyProcess process = new PtyProcessBuilder()
                    .setCommand(command.toArray(String[]::new))
                    .setDirectory(ticket.cwd().toString())
                    .setEnvironment(environment(ticket))
                    .setRedirectErrorStream(true)
                    .setInitialColumns(ticket.cols())
                    .setInitialRows(ticket.rows())
                    .start();
            return new TerminalProcessSession(process, new TerminalOutputLimiter(
                    maxOutputFrameBytes,
                    maxOutputConnectionBytes));
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.TERMINAL_UNAVAILABLE,
                    "PTY 后端不可用",
                    Map.of("targetType", ticket.targetType(), "targetId", ticket.auditTargetId()),
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

    /**
     * 服务器终端由 Java 直接启动，因此操作系统用户和权限天然与 Java 一致；这里只构造不含密钥的最小环境。
     * workspace 终端保持既有环境兼容性。
     */
    private Map<String, String> environment(TerminalTicket ticket) {
        Map<String, String> environment = ticket.serverShell() ? serverShellEnvironment() : new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        return environment;
    }

    /** 构造服务器 shell 的安全环境；这些字段只描述 Java 用户，不会切换 UID 或增加权限。 */
    static Map<String, String> serverShellEnvironment() {
        Map<String, String> environment = new HashMap<>();
        String userName = safeValue(System.getProperty("user.name"), "unknown");
        environment.put("PATH", safeValue(System.getenv("PATH"), "/usr/local/bin:/usr/bin:/bin"));
        environment.put("HOME", safeValue(System.getProperty("user.home"), "/"));
        environment.put("USER", userName);
        environment.put("LOGNAME", userName);
        environment.put("SHELL", "/bin/bash");
        environment.put("LANG", safeValue(System.getenv("LANG"), "C"));
        return environment;
    }

    private static String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
