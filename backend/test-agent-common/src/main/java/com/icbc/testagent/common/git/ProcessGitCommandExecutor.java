package com.icbc.testagent.common.git;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于本机 git 二进制的命令执行器。只用于远端只读配置查询，不执行 clone/fetch。
 */
public class ProcessGitCommandExecutor implements GitCommandExecutor {

    private static final int MAX_STDOUT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STDERR_BYTES = 16 * 1024;

    @Override
    public GitCommandResult execute(List<String> command, String privateKey, Duration timeout) {
        GitCommandExecutor.record(command);
        Path keyFile = null;
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
                        "ssh -i " + shellQuote(keyFile.toString()) + " -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new");
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
                throw new PlatformException(ErrorCode.GIT_TIMEOUT, "Git 操作超时", Map.of("command", safeCommand(command)));
            }
            out.join(1000);
            err.join(1000);
            int exit = process.exitValue();
            if (exit != 0) {
                String stderrText = safeStderr(stderr);
                GitCommandFailure failure = GitCommandFailureClassifier.classify(command, stderrText);
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        failure.message(),
                        Map.of(
                                "command", safeCommand(command),
                                "exitCode", exit,
                                "stderr", stderrText,
                                "gitFailureType", failure.type(),
                                "gitFailureHint", failure.hint()));
            }
            byte[] stdoutBytes = stdout.toByteArray();
            return new GitCommandResult(exit, new String(stdoutBytes, StandardCharsets.UTF_8), stdoutBytes);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.GIT_UNAVAILABLE,
                    "Git 命令不可用",
                    Map.of("command", safeCommand(command)),
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

    private static String safeCommand(List<String> command) {
        return String.join(" ", command);
    }

    private static String safeStderr(ByteArrayOutputStream stderr) {
        return new String(stderr.toByteArray(), StandardCharsets.UTF_8)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
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
