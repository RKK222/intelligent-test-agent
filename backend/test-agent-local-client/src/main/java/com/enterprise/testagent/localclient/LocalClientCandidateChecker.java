package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 使用候选发布单元自己的 JDK 启动候选 JAR，并等待无网络自检完成。 */
final class LocalClientCandidateChecker implements LocalClientReleaseDownloader.CandidateChecker {

    private static final Duration SELF_CHECK_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration PROCESS_TERMINATION_TIMEOUT = Duration.ofSeconds(5);

    private final Duration selfCheckTimeout;

    LocalClientCandidateChecker() {
        this(SELF_CHECK_TIMEOUT);
    }

    /** 允许调用方统一约束候选 JVM 与 javac 探测的总等待时间。 */
    LocalClientCandidateChecker(Duration selfCheckTimeout) {
        this.selfCheckTimeout = Objects.requireNonNull(selfCheckTimeout, "selfCheckTimeout must not be null");
        if (selfCheckTimeout.isNegative() || selfCheckTimeout.isZero()) {
            throw new IllegalArgumentException("selfCheckTimeout must be positive");
        }
    }

    @Override
    public void check(
            Path javaExecutable,
            Path clientJar,
            Path releaseDirectory,
            String targetVersion) {
        String version = LocalClientReleaseVersion.parse(targetVersion).value();
        Path releaseRoot = releaseDirectory.toAbsolutePath().normalize();
        LocalClientPlatform platform = LocalClientPlatform.current();
        Path javaPath = javaExecutable.toAbsolutePath().normalize();
        Path javacPath = platform.javacExecutable(releaseRoot);
        Path jarPath = clientJar.toAbsolutePath().normalize();
        if (!version.equals(releaseRoot.getFileName().toString())
                || !javaPath.equals(platform.javaExecutable(releaseRoot))
                || !jarPath.equals(releaseRoot.resolve("test-agent-local-client.jar"))
                || !platform.isExecutable(javaPath)
                || !platform.isExecutable(javacPath)
                || !Files.isRegularFile(jarPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("candidate self-check inputs are invalid");
        }

        verifyJavac21(javacPath);

        List<String> command = List.of(
                javaPath.toString(),
                "-jar",
                jarPath.toString(),
                "self-check",
                releaseRoot.toString(),
                version);
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            // self-check 必须是完全无交互流程；立即关闭 stdin，避免候选进程等待用户输入。
            process.getOutputStream().close();
        } catch (IOException exception) {
            throw new IllegalStateException("candidate self-check could not start", exception);
        }

        try {
            if (!process.waitFor(selfCheckTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
                throw new IllegalStateException("candidate self-check timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("candidate self-check failed");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("candidate self-check was interrupted", exception);
        }
    }

    /** 直接执行私有 JDK 的 javac，不通过 shell 拼接或解析不受信任的脚本文本。 */
    private void verifyJavac21(Path javacPath) {
        Process process;
        try {
            process = new ProcessBuilder(javacPath.toString(), "--version")
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectErrorStream(true)
                    .start();
            process.getOutputStream().close();
        } catch (IOException exception) {
            throw new IllegalStateException("candidate JDK javac could not start", exception);
        }
        byte[] output = readJavacOutputWithinDeadline(process);
        String versionOutput = new String(output, StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0 || !versionOutput.matches("javac 21(?:\\.[0-9]+)*")) {
            throw new IllegalStateException("candidate JDK javac is not version 21");
        }
    }

    /**
     * stdout 读取和进程退出共享同一截止时间：不能先等待 EOF，否则无输出或半行输出的恶意 javac 会永久阻塞。
     */
    private byte[] readJavacOutputWithinDeadline(Process process) {
        long deadlineNanos = System.nanoTime() + selfCheckTimeout.toNanos();
        InputStream input = process.getInputStream();
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "local-client-javac-version-reader");
            thread.setDaemon(true);
            return thread;
        });
        Future<byte[]> reader = readerExecutor.submit(() -> readAtMost512Bytes(input));
        try {
            byte[] output = reader.get(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS);
            if (!process.waitFor(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS)) {
                stopProcessAndWait(process);
                throw new IllegalStateException("candidate JDK javac timed out");
            }
            return output;
        } catch (TimeoutException exception) {
            stopProcessAndWait(process);
            throw new IllegalStateException("candidate JDK javac timed out", exception);
        } catch (ExecutionException exception) {
            stopProcessAndWait(process);
            throw new IllegalStateException("candidate JDK javac output could not be read", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stopProcessAndWait(process);
            throw new IllegalStateException("candidate JDK javac was interrupted", exception);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // 清理阶段已优先终止候选进程，关闭失败不能掩盖原始结果。
            }
            reader.cancel(true);
            readerExecutor.shutdownNow();
        }
    }

    /** 读取上限固定为版本输出所需的 512 字节，读取线程只负责 I/O，超时由调用线程统一裁决。 */
    private static byte[] readAtMost512Bytes(InputStream input) throws IOException {
        byte[] output = new byte[512];
        int offset = 0;
        while (offset < output.length) {
            int count = input.read(output, offset, output.length - offset);
            if (count < 0) {
                break;
            }
            offset += count;
        }
        return Arrays.copyOf(output, offset);
    }

    private static long remainingNanos(long deadlineNanos) throws TimeoutException {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            throw new TimeoutException("candidate JDK javac timed out");
        }
        return remaining;
    }

    /** 超时后先杀子进程再杀 shell 父进程并等待，防止其继承 stdout 管道导致读取线程遗留。 */
    private static void stopProcessAndWait(Process process) {
        List<ProcessHandle> handles = new ArrayList<>();
        process.toHandle().descendants().forEach(handles::add);
        for (ProcessHandle handle : handles) {
            handle.destroy();
        }
        process.destroy();
        try {
            if (!process.waitFor(PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                for (ProcessHandle handle : handles) {
                    if (handle.isAlive()) {
                        handle.destroyForcibly();
                    }
                }
                process.destroyForcibly();
                process.waitFor(PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            for (ProcessHandle handle : handles) {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            }
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
