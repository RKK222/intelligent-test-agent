package com.enterprise.testagent.opencode.runtime.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 创建受控 shell 进程。shell 只能来自 ticket 中后端解析出的默认 shell。
 */
@Component
public class TerminalProcessFactory {

    private static final String SERVER_SHELL_RC_RESOURCE = "/terminal/server-terminal.bashrc";

    private final int maxOutputFrameBytes;
    private final int maxOutputConnectionBytes;
    private final Path serverShellRcFile;

    /**
     * 创建进程工厂，并归一化输出预算配置。
     */
    public TerminalProcessFactory(
            @Value("${test-agent.terminal.max-output-frame-bytes:16384}") int maxOutputFrameBytes,
            @Value("${test-agent.terminal.max-output-connection-bytes:1048576}") int maxOutputConnectionBytes) {
        this.maxOutputFrameBytes = Math.max(1, maxOutputFrameBytes);
        this.maxOutputConnectionBytes = Math.max(this.maxOutputFrameBytes, maxOutputConnectionBytes);
        this.serverShellRcFile = materializeServerShellRcFile();
    }

    /**
     * 启动受控交互式 shell 进程，cwd 和 shell 均来自已校验 ticket。
     */
    public TerminalProcessSession start(TerminalTicket ticket) {
        try {
            List<String> command = ticket.serverShell()
                    ? serverShellCommand(ticket.shell(), serverShellRcFile)
                    : shellCommand(ticket.shell());
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

    /** 服务器 Bash 使用平台临时 rcfile 提供基础配色；用户自己的 bashrc 仍由该文件按兼容顺序加载。 */
    static List<String> serverShellCommand(String shell, Path rcFile) {
        if (!"bash".equals(Path.of(shell).getFileName().toString())) {
            throw new IllegalArgumentException("server shell must be bash");
        }
        return List.of(shell, "--rcfile", rcFile.toString(), "-i", "-s");
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
        environment.put("COLORTERM", "truecolor");
        environment.put("CLICOLOR", "1");
        return environment;
    }

    /**
     * 将 jar 内的平台配色脚本释放为当前 Java 用户创建的随机临时文件，避免修改用户主目录或使用固定共享路径。
     */
    private static Path materializeServerShellRcFile() {
        try (InputStream input = TerminalProcessFactory.class.getResourceAsStream(SERVER_SHELL_RC_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("服务器终端配色脚本不存在");
            }
            Path rcFile = Files.createTempFile("test-agent-server-terminal-", ".bashrc");
            Files.copy(input, rcFile, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.setPosixFilePermissions(rcFile, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows 没有 POSIX 权限位，临时文件仍由当前 Java 用户创建并受系统 ACL 保护。
            }
            rcFile.toFile().deleteOnExit();
            return rcFile;
        } catch (IOException exception) {
            throw new IllegalStateException("服务器终端配色脚本初始化失败", exception);
        }
    }

    private static String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
