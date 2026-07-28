package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommandExecutor;
import com.enterprise.testagent.common.git.ProcessGitCommandExecutor;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 把冻结的远端提交物化为不含 Git 元数据的源码目录。
 *
 * <p>clone、冻结提交 fetch、checkout 和校验命令复用公共 Git 执行器与同一临时 SSH 私钥；
 * 不触网的本机 sparse-checkout stdin 命令由本类受控执行。所有命令使用独立参数数组，禁止 shell 拼接。
 */
@Component
public class AppSourceGitMaterializer {

    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_COMMAND_OUTPUT = 16 * 1024;

    private final GitCommandExecutor git;
    private final ObjectMapper objectMapper;
    private final DirectoryMover directoryMover;

    public AppSourceGitMaterializer() {
        this(new ProcessGitCommandExecutor(), new ObjectMapper(), DirectoryMover.filesystem());
    }

    AppSourceGitMaterializer(
            GitCommandExecutor git,
            ObjectMapper objectMapper,
            DirectoryMover directoryMover) {
        this.git = Objects.requireNonNull(git);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.directoryMover = Objects.requireNonNull(directoryMover);
    }

    /** 在目标同根 staging 完成全部校验后才原子发布；失败时尽力恢复旧目录。 */
    public Result materialize(Request request) {
        return materialize(request, result -> { });
    }

    /**
     * 在旧目录备份仍保留且文件锁仍由 worker 持有时执行数据库 completion；completion 失败会回滚目录。
     */
    public Result materialize(Request request, Completion completion) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(completion, "completion must not be null");
        Path target = AppSourcePathGuard.requireSafe(request.targetRoot());
        Path parent = Objects.requireNonNull(target.getParent(), "target parent must not be null");
        Path staging = parent.resolve("." + target.getFileName() + ".g" + request.generation()
                + "." + UUID.randomUUID() + ".staging");
        try {
            Files.createDirectories(parent);
            AppSourcePathGuard.requireSafe(target);
            AppSourcePathGuard.requireSafe(staging);
            shallowSparseClone(request, staging);
            AppSourcePathGuard.requireSafe(staging);
            boolean shallow = "true".equals(git.execute(
                    List.of("git", "-C", staging.toString(), "rev-parse", "--is-shallow-repository"),
                    request.privateKey(),
                    GIT_TIMEOUT).stdoutText().trim());
            String actualCommit = git.execute(
                    List.of("git", "-C", staging.toString(), "rev-parse", "HEAD"),
                    request.privateKey(),
                    GIT_TIMEOUT).stdoutText().trim();
            if (!request.targetCommit().equals(actualCommit)) {
                throw new PlatformException(ErrorCode.CONFLICT, "源码 staging 提交与冻结提交不一致");
            }
            validateMaterializedPaths(staging, request.selectedPaths());
            validateSymlinksStayInside(staging);
            deleteTree(staging.resolve(".git"));
            byte[] indexBytes = indexBytes(request);
            writeIndexAtomically(staging, indexBytes);
            Result result = new Result(sha256(indexBytes), shallow);
            publish(staging, target, result, completion);
            return result;
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "应用源码物化失败",
                    Map.of("failure", "FILESYSTEM_OR_GIT"),
                    exception);
        } finally {
            deleteTreeQuietly(staging);
        }
    }

    private void shallowSparseClone(Request request, Path staging) throws IOException {
        git.execute(
                List.of(
                        "git", "clone",
                        "--depth", "1",
                        "--filter=blob:none",
                        "--no-checkout",
                        "--single-branch",
                        "--branch", request.branch(),
                        "--",
                        request.gitUrl(),
                        staging.toString()),
                request.privateKey(),
                GIT_TIMEOUT);
        // branch 在受理后可能前进；显式用同一临时凭据抓取冻结 SHA，不能依赖 depth=1 的新 tip。
        git.execute(
                List.of(
                        "git", "-C", staging.toString(), "fetch",
                        "--depth", "1",
                        "--filter=blob:none",
                        "origin", request.targetCommit()),
                request.privateKey(),
                GIT_TIMEOUT);
        git.execute(
                List.of("git", "-C", staging.toString(), "sparse-checkout", "init", "--no-cone"),
                request.privateKey(),
                GIT_TIMEOUT);
        runWithInput(
                List.of("git", "-C", staging.toString(), "sparse-checkout", "set", "--no-cone", "--stdin"),
                sparsePatterns(request.selectedPaths()));
        git.execute(
                List.of("git", "-C", staging.toString(), "checkout", "--detach", request.targetCommit()),
                request.privateKey(),
                GIT_TIMEOUT);
    }

    private byte[] sparsePatterns(List<AppSourceSelectedPath> selectedPaths) {
        String text = selectedPaths.stream()
                .map(path -> {
                    if (".".equals(path.path())) {
                        return "/*";
                    }
                    String escaped = escapeNoConePattern(path.path());
                    return path.pathType() == AppSourcePathType.DIRECTORY
                            ? "/" + escaped + "/"
                            : "/" + escaped;
                })
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "源码选择不能为空"));
        return (text + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * no-cone 输入仍使用 gitignore pattern 语法；把用户路径中的 pattern 元字符逐字符转义，
     * 保证 FILE 不会匹配同前缀/通配文件，DIRECTORY 只扩展明确目录子树。
     */
    private String escapeNoConePattern(String path) {
        StringBuilder escaped = new StringBuilder(path.length());
        for (int index = 0; index < path.length(); index++) {
            char current = path.charAt(index);
            if (current == '\\' || current == '*' || current == '?' || current == '[' || current == ']'
                    || current == '!' || current == '#') {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }

    private void runWithInput(List<String> command, byte[] stdin) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start();
        try {
            process.getOutputStream().write(stdin);
            process.getOutputStream().close();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread outputPump = pump(process.getInputStream(), stdout);
            Thread errorPump = pump(process.getErrorStream(), stderr);
            if (!process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new PlatformException(ErrorCode.GIT_TIMEOUT, "Git sparse-checkout 超时");
            }
            outputPump.join(1000);
            errorPump.join(1000);
            if (process.exitValue() != 0) {
                // 原始 stderr 只留在当前栈内，不写数据库、广播或正式日志。
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        "Git sparse-checkout 失败",
                        Map.of("exitCode", process.exitValue()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("sparse checkout interrupted", exception);
        }
    }

    private Thread pump(java.io.InputStream input, ByteArrayOutputStream output) {
        Thread thread = Thread.ofVirtual().unstarted(() -> {
            byte[] buffer = new byte[2048];
            int total = 0;
            try {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    int writable = Math.min(read, Math.max(0, MAX_COMMAND_OUTPUT - total));
                    if (writable > 0) {
                        output.write(buffer, 0, writable);
                        total += writable;
                    }
                }
            } catch (IOException ignored) {
                // 子进程退出时流关闭属于正常收敛路径。
            }
        });
        thread.start();
        return thread;
    }

    private void validateMaterializedPaths(Path staging, List<AppSourceSelectedPath> selectedPaths) {
        for (AppSourceSelectedPath selected : selectedPaths) {
            if (".".equals(selected.path())) {
                continue;
            }
            Path path = staging.resolve(selected.path()).normalize();
            if (!path.startsWith(staging)
                    || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    || (selected.pathType() == AppSourcePathType.FILE
                            && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    || (selected.pathType() == AppSourcePathType.DIRECTORY
                            && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "源码选择与冻结提交不一致",
                        Map.of("path", selected.path()));
            }
        }
    }

    private void validateSymlinksStayInside(Path staging) throws IOException {
        Path normalizedRoot = staging.toAbsolutePath().normalize();
        try (var paths = Files.walk(staging)) {
            for (Path path : paths.filter(Files::isSymbolicLink).toList()) {
                Path target = Files.readSymbolicLink(path);
                Path resolved = target.isAbsolute()
                        ? target.normalize()
                        : path.getParent().resolve(target).toAbsolutePath().normalize();
                if (!resolved.startsWith(normalizedRoot)) {
                    throw new PlatformException(ErrorCode.FORBIDDEN, "源码快照包含越根符号链接");
                }
            }
        }
    }

    private byte[] indexBytes(Request request) throws IOException {
        return new AppSourceIndexManager(objectMapper).canonicalBytes(
                request.generation(), request.branch(), request.targetCommit(),
                request.expiresAt(), request.selectedPaths());
    }

    private void writeIndexAtomically(Path staging, byte[] bytes) throws IOException {
        Path temporary = staging.resolve("." + AppSourceApplicationService.INDEX_FILE_NAME + ".tmp");
        Path target = staging.resolve(AppSourceApplicationService.INDEX_FILE_NAME);
        Files.write(temporary, bytes);
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private void publish(Path staging, Path target, Result result, Completion completion) throws IOException {
        AppSourcePathGuard.requireSafe(staging);
        AppSourcePathGuard.requireSafe(target);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            directoryMover.moveAtomically(staging, target);
            try {
                completion.complete(result);
            } catch (RuntimeException completionFailure) {
                try {
                    deleteTree(target);
                } catch (IOException rollbackFailure) {
                    completionFailure.addSuppressed(rollbackFailure);
                }
                throw completionFailure;
            }
            return;
        }
        Path backup = target.getParent().resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".backup");
        directoryMover.moveAtomically(target, backup);
        try {
            directoryMover.moveAtomically(staging, target);
            completion.complete(result);
            deleteTree(backup);
        } catch (RuntimeException | IOException publishFailure) {
            try {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    deleteTree(target);
                }
                if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    directoryMover.moveAtomically(backup, target);
                }
            } catch (IOException rollbackFailure) {
                publishFailure.addSuppressed(rollbackFailure);
            }
            throw publishFailure;
        }
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private void deleteTreeQuietly(Path root) {
        try {
            deleteTree(root);
        } catch (Exception ignored) {
            // staging 尽力清理，不能覆盖原始业务失败。
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 物化请求只携带冻结 Git 事实、逻辑选择和目标路径；privateKey 永不进入结果或索引。 */
    public record Request(
            Path targetRoot,
            String gitUrl,
            String branch,
            String targetCommit,
            List<AppSourceSelectedPath> selectedPaths,
            String privateKey,
            long generation,
            Instant expiresAt) {
        public Request {
            Objects.requireNonNull(targetRoot, "targetRoot must not be null");
            gitUrl = requireText(gitUrl, "gitUrl");
            branch = requireText(branch, "branch");
            targetCommit = requireText(targetCommit, "targetCommit");
            selectedPaths = List.copyOf(Objects.requireNonNull(selectedPaths));
            if (selectedPaths.isEmpty() || generation < 1L) {
                throw new IllegalArgumentException("selectedPaths must not be empty and generation must be positive");
            }
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value.trim();
        }
    }

    /** 供 worker 写入数据库的权威索引摘要和浅克隆校验结果。 */
    public record Result(String indexSha256, boolean shallowClone) {
    }

    /** 数据库 completion 只接收摘要结果，不接收凭据或 staging 路径。 */
    @FunctionalInterface
    public interface Completion {
        void complete(Result result);
    }

    /** 原子目录移动测试边界；生产只使用同文件系统 ATOMIC_MOVE。 */
    @FunctionalInterface
    interface DirectoryMover {
        void moveAtomically(Path source, Path target) throws IOException;

        static DirectoryMover filesystem() {
            return (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
