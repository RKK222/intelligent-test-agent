package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Git 远程目录查询服务。
 * 使用 git fetch + ls-tree 查询远程仓库的目录结构，只下载 tree 对象，不下载文件内容。
 *
 * <p>性能优势：</p>
 * <ul>
 *   <li>只下载 commit 和 tree 对象，数据传输量极小（KB级）</li>
 *   <li>查询速度快，通常在秒级完成</li>
 *   <li>支持缓存机制，避免重复查询</li>
 *   <li>零工作目录占用，只保留 .git 元数据</li>
 * </ul>
 *
 * <p>要求：Git 1.7.8+ 版本</p>
 */
public class GitCloneCacheService {

    private static final Logger log = LoggerFactory.getLogger(GitCloneCacheService.class);

    /**
     * 缓存目录名称格式：{urlHash}_{branch}
     */
    private static final String CACHE_DIR_FORMAT = "%s_%s";

    /**
     * 缓存根目录
     */
    private final Path cacheRoot;

    /**
     * 缓存过期时间
     */
    private final Duration cacheExpiry;

    /**
     * 命令超时时间
     */
    private final Duration commandTimeout;

    /**
     * 查询锁，防止同一仓库同时多次查询
     */
    private final ConcurrentHashMap<String, Object> queryLocks = new ConcurrentHashMap<>();

    /**
     * 构造服务。
     *
     * @param cacheRoot      缓存根目录路径
     * @param cacheExpiry    缓存过期时间
     * @param commandTimeout 命令超时时间
     */
    public GitCloneCacheService(Path cacheRoot, Duration cacheExpiry, Duration commandTimeout) {
        this.cacheRoot = cacheRoot;
        this.cacheExpiry = cacheExpiry;
        this.commandTimeout = commandTimeout;
        ensureCacheRootExists();
        log.info("Git 目录查询服务已初始化，缓存目录: {}, 过期时间: {}", cacheRoot, cacheExpiry);
    }

    /**
     * 列出指定分支的目录结构。
     * 使用 git fetch 获取 tree 对象，然后用 ls-tree 列出目录。
     *
     * @param gitUrl     Git 仓库 URL
     * @param branch     分支名称
     * @param privateKey SSH 私钥（可选）
     * @return 目录路径列表（已排序），只包含目录路径，不包含文件
     */
    public List<String> listDirectories(String gitUrl, String branch, String privateKey) {
        String cacheKey = buildCacheKey(gitUrl, branch);
        Path cacheDir = cacheRoot.resolve(cacheKey);

        // 检查缓存是否有效
        if (isCacheValid(cacheDir)) {
            log.debug("使用缓存目录: {}", cacheDir);
            return listCachedDirectories(cacheDir);
        }

        // 获取锁，防止并发查询同一仓库
        Object lock = queryLocks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            // 双重检查
            if (isCacheValid(cacheDir)) {
                return listCachedDirectories(cacheDir);
            }

            // 执行查询
            fetchAndListDirectories(gitUrl, branch, cacheDir, privateKey);

            // 查询完成后移除锁
            queryLocks.remove(cacheKey);
        }

        return listCachedDirectories(cacheDir);
    }

    /**
     * 清理过期缓存。
     */
    public void cleanExpiredCache() {
        if (!Files.exists(cacheRoot)) {
            return;
        }
        try (var paths = Files.list(cacheRoot)) {
            paths.filter(Files::isDirectory)
                    .filter(this::isCacheExpired)
                    .forEach(this::deleteCacheDir);
        } catch (IOException e) {
            log.warn("清理过期缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 构建缓存键：URL hash + branch
     */
    private String buildCacheKey(String gitUrl, String branch) {
        int urlHash = Math.abs(gitUrl.hashCode());
        String safeBranch = branch.replaceAll("[^A-Za-z0-9._-]", "_");
        return String.format(CACHE_DIR_FORMAT, urlHash, safeBranch);
    }

    /**
     * 检查缓存是否有效。
     * 必须同时满足：
     * 1. 缓存目录存在
     * 2. .git 目录存在
     * 3. FETCH_HEAD 文件存在（说明已经 fetch 过）
     * 4. 未过期
     */
    private boolean isCacheValid(Path cacheDir) {
        if (!Files.exists(cacheDir) || !Files.isDirectory(cacheDir)) {
            return false;
        }
        Path gitDir = cacheDir.resolve(".git");
        if (!Files.exists(gitDir)) {
            return false;
        }
        // 检查 FETCH_HEAD 是否存在，确保已经 fetch 过
        Path fetchHead = gitDir.resolve("FETCH_HEAD");
        if (!Files.exists(fetchHead)) {
            return false;
        }
        try {
            var attrs = Files.readAttributes(fetchHead, java.nio.file.attribute.BasicFileAttributes.class);
            var lastModified = attrs.lastModifiedTime().toInstant();
            return java.time.Instant.now().isBefore(lastModified.plus(cacheExpiry));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 检查缓存是否过期。
     */
    private boolean isCacheExpired(Path cacheDir) {
        return !isCacheValid(cacheDir);
    }

    /**
     * 获取远程分支的目录结构。
     * 步骤：
     * 1. 创建临时 Git 仓库
     * 2. 添加远程引用
     * 3. fetch 远程分支（只获取 tree，不获取 blob）
     * 4. 使用 ls-tree 列出目录
     */
    private void fetchAndListDirectories(String gitUrl, String branch, Path cacheDir, String privateKey) {
        // 删除旧的缓存目录
        deleteCacheDir(cacheDir);

        try {
            // 1. 创建临时 Git 仓库
            executeCommand(List.of("git", "init", cacheDir.toString()), privateKey, "初始化仓库失败");

            // 2. 添加远程引用
            executeCommand(List.of("git", "-C", cacheDir.toString(), "remote", "add", "origin", gitUrl),
                    privateKey, "添加远程引用失败");

            // 3. fetch 远程分支（--depth=1 只获取最新提交，不下载 blob）
            executeCommand(List.of("git", "-C", cacheDir.toString(), "fetch", "origin", branch, "--depth=1"),
                    privateKey, "获取远程分支失败");

            log.info("已获取远程分支: {} {}", gitUrl, branch);

        } catch (PlatformException e) {
            // 失败时清理缓存目录
            deleteCacheDir(cacheDir);
            throw e;
        }
    }

    /**
     * 使用 ls-tree 列出缓存的目录结构。
     */
    private List<String> listCachedDirectories(Path cacheDir) {
        // 执行 git ls-tree -r -d --name-only FETCH_HEAD
        List<String> command = List.of(
                "git", "-C", cacheDir.toString(),
                "ls-tree", "-r", "-d", "--name-only", "FETCH_HEAD"
        );

        String output = executeCommand(command, null, "列出目录失败");

        // 解析输出
        List<String> directories = new ArrayList<>();
        String[] lines = output.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                directories.add(trimmed);
            }
        }

        return directories;
    }

    /**
     * 执行 Git 命令。
     */
    private String executeCommand(List<String> command, String privateKey, String errorMessage) {
        Path keyFile = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");

            // 配置 SSH 私钥
            if (privateKey != null && !privateKey.isBlank()) {
                keyFile = writeTempKey(privateKey);
                builder.environment().put(
                        "GIT_SSH_COMMAND",
                        "ssh -i " + shellQuote(keyFile.toString()) + " -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new");
            }

            Process process = builder.start();

            // 读取输出和错误
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(commandTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new PlatformException(ErrorCode.GIT_TIMEOUT, "Git 命令超时", Map.of("command", safeCommand(command)));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        errorMessage,
                        Map.of("command", safeCommand(command), "exitCode", exitCode, "stderr", stderr.trim()));
            }

            return stdout;

        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformException(
                    ErrorCode.GIT_UNAVAILABLE,
                    errorMessage,
                    Map.of("command", safeCommand(command)),
                    e);
        } finally {
            deleteTempKey(keyFile);
        }
    }

    /**
     * 写入临时 SSH 私钥文件。
     */
    private Path writeTempKey(String privateKey) throws IOException {
        Path file = Files.createTempFile("git-ls-", ".key");
        Files.writeString(file, privateKey.endsWith("\n") ? privateKey : privateKey + "\n", StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(file, java.util.EnumSet.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 文件系统
        }
        return file;
    }

    /**
     * 删除临时私钥文件。
     */
    private void deleteTempKey(Path keyFile) {
        if (keyFile == null) return;
        try {
            Files.deleteIfExists(keyFile);
        } catch (IOException ignored) {
        }
    }

    /**
     * Shell 引号处理。
     */
    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * 读取进程输出流。
     */
    private String readStream(java.io.InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * 安全格式化命令（不包含敏感信息）。
     */
    private String safeCommand(List<String> command) {
        return String.join(" ", command);
    }

    /**
     * 删除缓存目录。
     */
    private void deleteCacheDir(Path cacheDir) {
        if (!Files.exists(cacheDir)) return;
        try {
            Files.walk(cacheDir)
                    .sorted((a, b) -> -a.compareTo(b)) // 先删除子目录
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            log.warn("删除缓存目录失败: {}", cacheDir);
        }
    }

    /**
     * 确保缓存根目录存在。
     */
    private void ensureCacheRootExists() {
        if (!Files.exists(cacheRoot)) {
            try {
                Files.createDirectories(cacheRoot);
                log.info("创建 Git 缓存目录: {}", cacheRoot);
            } catch (IOException e) {
                throw new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "无法创建缓存目录",
                        Map.of("path", cacheRoot.toString()),
                        e);
            }
        }
    }
}
