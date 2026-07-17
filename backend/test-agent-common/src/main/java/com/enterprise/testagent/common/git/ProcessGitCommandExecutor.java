package com.enterprise.testagent.common.git;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于本机 git 二进制的命令执行器，统一处理临时 SSH key、超时、脱敏日志和错误归因。
 */
public class ProcessGitCommandExecutor implements GitCommandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessGitCommandExecutor.class);
    private static final int MAX_STDOUT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STDERR_BYTES = 16 * 1024;
    private static final String TIMEOUT_HINT = "请检查后端服务器到 Git 远端的网络、DNS、SSH 端口连通性，或确认远端仓库响应时间是否超过平台超时。";
    private static final Pattern URI_USER_INFO_PATTERN = Pattern.compile("(?i)(https?://|ssh://)[^\\s/@]+@");
    private static final Pattern SSH_PRINCIPAL_PATTERN = Pattern.compile(
            "(?<![\\w./-])([A-Za-z0-9._%+-]+)@([A-Za-z0-9._-]+(?::\\d+)?)(?=[:\\s/]|$)");

    @Override
    public GitCommandResult execute(List<String> command, String privateKey, Duration timeout) {
        GitCommandExecutor.record(command);
        Path keyFile = null;
        long startedAt = System.nanoTime();
        String safeCommand = safeCommand(command);
        LOGGER.info("event=git_command_start timeoutMs={} command={}", timeout.toMillis(), safeCommand);
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.PIPE);
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            builder.environment().put("LANG", "en_US.UTF-8");
            builder.environment().put("LC_ALL", "en_US.UTF-8");
            if (privateKey != null && !privateKey.isBlank()) {
                keyFile = writeTempKey(privateKey);
                builder.environment().put(
                        "GIT_SSH_COMMAND",
                        "ssh -i " + shellQuote(keyFile.toString())
                                + " -o IdentitiesOnly=yes"
                                + " -o StrictHostKeyChecking=accept-new"
                                + " -o BatchMode=yes"
                                + " -o ConnectTimeout=10"
                                + " -o ServerAliveInterval=5"
                                + " -o ServerAliveCountMax=2");
            }
            Process process = builder.start();
            process.getOutputStream().close();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread out = pump(process.getInputStream(), stdout, MAX_STDOUT_BYTES);
            Thread err = pump(process.getErrorStream(), stderr, MAX_STDERR_BYTES);
            boolean finished = process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                long durationMs = elapsedMillis(startedAt);
                LOGGER.warn(
                        "event=git_command_timeout durationMs={} timeoutMs={} failureType={} failureHint={} command={}",
                        durationMs,
                        timeout.toMillis(),
                        "TIMEOUT",
                        TIMEOUT_HINT,
                        safeCommand);
                throw new PlatformException(
                        ErrorCode.GIT_TIMEOUT,
                        "Git 操作超时",
                        Map.of(
                                "command", safeCommand,
                                "timeoutMillis", timeout.toMillis(),
                                "durationMillis", durationMs,
                                "gitFailureType", "TIMEOUT",
                                "gitFailureHint", TIMEOUT_HINT));
            }
            out.join(1000);
            err.join(1000);
            int exit = process.exitValue();
            if (exit != 0) {
                String stderrText = safeStderr(stderr);
                GitCommandFailure failure = GitCommandFailureClassifier.classify(command, stderrText);
                LOGGER.warn(
                        "event=git_command_failed durationMs={} exitCode={} failureType={} failureHint={} command={} stderr={}",
                        elapsedMillis(startedAt),
                        exit,
                        failure.type(),
                        failure.hint(),
                        safeCommand,
                        stderrText);
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        failure.message(),
                        Map.of(
                                "command", safeCommand,
                                "exitCode", exit,
                                "stderr", stderrText,
                                "gitFailureType", failure.type(),
                                "gitFailureHint", failure.hint()));
            }
            byte[] stdoutBytes = stdout.toByteArray();
            long durationMs = elapsedMillis(startedAt);
            if (durationMs >= 5000) {
                LOGGER.info("event=git_command_slow durationMs={} command={}", durationMs, safeCommand);
            } else {
                LOGGER.info("event=git_command_success durationMs={} command={}", durationMs, safeCommand);
            }
            return new GitCommandResult(exit, new String(stdoutBytes, StandardCharsets.UTF_8), stdoutBytes);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn(
                    "event=git_command_unavailable durationMs={} command={} error={}",
                    elapsedMillis(startedAt),
                    safeCommand,
                    exception.toString());
            throw new PlatformException(
                    ErrorCode.GIT_UNAVAILABLE,
                    "Git 命令不可用",
                    Map.of("command", safeCommand),
                    exception);
        } finally {
            deleteTempKey(keyFile);
        }
    }

    /**
     * 写入临时私钥文件并尽量收紧权限；finally 中必须删除。
     */
    private Path writeTempKey(String privateKey) throws Exception {
        Path file = Files.createTempFile("test-agent-git-", ".key");
        Files.writeString(file, privateKey.endsWith("\n") ? privateKey : privateKey + "\n", StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 文件系统下仍依赖临时目录权限；文件会在命令结束后删除。
        }
        return file;
    }

    private static Thread pump(java.io.InputStream input, ByteArrayOutputStream output, int limit) {
        Thread thread = Thread.ofVirtual().unstarted(() -> {
            byte[] buffer = new byte[4096];
            int total = 0;
            try {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    int writable = Math.min(read, Math.max(0, limit - total));
                    if (writable > 0) {
                        output.write(buffer, 0, writable);
                        total += writable;
                    }
                }
            } catch (Exception ignored) {
                // 命令结束时流关闭属于正常路径，调用方只关心进程退出码。
            }
        });
        thread.start();
        return thread;
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    static String safeCommand(List<String> command) {
        return String.join(" ", command.stream().map(ProcessGitCommandExecutor::safeArgument).toList());
    }

    private static String safeArgument(String argument) {
        if (argument == null || argument.isBlank()) {
            return argument;
        }
        String value = argument.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("ssh://")) {
            return maskUriUserInfo(value);
        }
        int at = value.indexOf('@');
        int colon = value.indexOf(':', at + 1);
        if (at > 0 && colon > at + 1 && !value.substring(0, at).contains("/")) {
            return "***@" + value.substring(at + 1);
        }
        return argument;
    }

    private static String maskUriUserInfo(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getUserInfo() == null || uri.getHost() == null) {
                return value;
            }
            String authority = "***@" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
            return new URI(
                    uri.getScheme(),
                    authority,
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()).toString();
        } catch (Exception ignored) {
            return value.replaceFirst("^(https?://|ssh://)[^/@]+@", "$1***@");
        }
    }

    private static String safeStderr(ByteArrayOutputStream stderr) {
        String text = new String(stderr.toByteArray(), StandardCharsets.UTF_8)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        text = URI_USER_INFO_PATTERN.matcher(text).replaceAll("$1***@");
        return SSH_PRINCIPAL_PATTERN.matcher(text).replaceAll("***@$2");
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void deleteTempKey(Path keyFile) {
        if (keyFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(keyFile);
        } catch (Exception ignored) {
            // 删除失败不抛出，避免掩盖原始 Git 错误；文件名不进入日志。
        }
    }
}
