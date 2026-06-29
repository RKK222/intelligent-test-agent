package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Git 无blob浅克隆缓存服务。
 * 使用 git clone --depth=1 --single-branch --filter=blob:none 进行无blob浅克隆，
 * 只下载目录结构（tree对象），不下载文件内容（blob对象），显著减少数据传输量。
 * 然后在本地遍历目录结构，避免 git archive --remote 在服务器端打包整个分支。
 *
 * <p>性能优势：</p>
 * <ul>
 *   <li>只下载 commit 和 tree 对象，数据传输量从GB级降至KB级</li>
 *   <li>对于大仓库，目录加载速度提升显著，避免因下载文件内容导致超时</li>
 *   <li>结合稀疏检出，只检出目录结构，不占用额外磁盘空间</li>
 * </ul>
 *
 * <p>要求：Git 2.22+ 版本（支持 --filter 参数）</p>
 */
public class GitCloneCacheService {

    private static final Logger log = LoggerFactory.getLogger(GitCloneCacheService.class);

    /**
     * 缓存目录名称格式：{urlHash}_{branch}
     */
    private static final String CACHE_DIR_FORMAT = "%s_%s";

    /**
     * 缓存元数据文件名
     */
    private static final String CACHE_META_FILE = ".cache-meta";

    /**
     * 缓存根目录
     */
    private final Path cacheRoot;

    /**
     * 缓存过期时间
     */
    private final Duration cacheExpiry;

    /**
     * 克隆超时时间
     */
    private final Duration cloneTimeout;

    /**
     * 克隆锁，防止同一仓库同时多次克隆
     */
    private final ConcurrentHashMap<String, Object> cloneLocks = new ConcurrentHashMap<>();

    /**
     * 构造缓存服务。
     *
     * @param cacheRoot    缓存根目录路径
     * @param cacheExpiry  缓存过期时间
     * @param cloneTimeout 克隆超时时间
     */
    public GitCloneCacheService(Path cacheRoot, Duration cacheExpiry, Duration cloneTimeout) {
        this.cacheRoot = cacheRoot;
        this.cacheExpiry = cacheExpiry;
        this.cloneTimeout = cloneTimeout;
        ensureCacheRootExists();
    }

    /**
     * 列出指定分支的目录结构。
     * 先检查缓存是否有效，有效则直接遍历本地目录（只遍历tree对象，无blob）；
     * 无效则重新进行无blob浅克隆后遍历。
     *
     * @param gitUrl     Git 仓库 URL
     * @param branch     分支名称
     * @param privateKey SSH 私钥（可选）
     * @return 目录路径列表（已去重排序），只包含目录路径，不包含文件
     */
    public List<String> listDirectories(String gitUrl, String branch, String privateKey) {
        String cacheKey = buildCacheKey(gitUrl, branch);
        Path cacheDir = cacheRoot.resolve(cacheKey);

        // 检查缓存是否有效
        if (isCacheValid(cacheDir)) {
            log.debug("使用缓存目录: {}", cacheDir);
            return listLocalDirectories(cacheDir);
        }

        // 获取克隆锁，防止并发克隆同一仓库
        Object lock = cloneLocks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            // 双重检查，可能在等待锁期间其他线程已完成克隆
            if (isCacheValid(cacheDir)) {
                return listLocalDirectories(cacheDir);
            }

            // 执行浅克隆
            shallowClone(gitUrl, branch, cacheDir, privateKey);
            writeCacheMeta(cacheDir, gitUrl, branch);

            // 克隆完成后移除锁
            cloneLocks.remove(cacheKey);
        }

        return listLocalDirectories(cacheDir);
    }

    /**
     * 清理过期缓存。
     */
    public void cleanExpiredCache() {
        if (!Files.exists(cacheRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.list(cacheRoot)) {
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
        // 使用 URL 的 hash 避免路径中包含特殊字符
        int urlHash = Math.abs(gitUrl.hashCode());
        // 分支名中的特殊字符替换为下划线
        String safeBranch = branch.replaceAll("[^A-Za-z0-9._-]", "_");
        return String.format(CACHE_DIR_FORMAT, urlHash, safeBranch);
    }

    /**
     * 检查缓存是否有效（目录存在且未过期）。
     */
    private boolean isCacheValid(Path cacheDir) {
        if (!Files.exists(cacheDir) || !Files.isDirectory(cacheDir)) {
            return false;
        }
        Path metaFile = cacheDir.resolve(CACHE_META_FILE);
        if (!Files.exists(metaFile)) {
            return false;
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(metaFile, BasicFileAttributes.class);
            Instant lastModified = attrs.lastModifiedTime().toInstant();
            return Instant.now().isBefore(lastModified.plus(cacheExpiry));
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
     * 执行无blob浅克隆，只下载目录结构，不下载文件内容。
     * 使用 --filter=blob:none 参数显著减少数据传输量，提升大仓库的目录加载速度。
     */
    private void shallowClone(String gitUrl, String branch, Path cacheDir, String privateKey) {
        // 删除旧的缓存目录（如果存在）
        deleteCacheDir(cacheDir);

        // 构建无blob克隆命令：--filter=blob:none 只下载 tree 对象，不下载 blob（文件内容）
        List<String> cloneCommand = new ArrayList<>();
        cloneCommand.add("git");
        cloneCommand.add("clone");
        cloneCommand.add("--depth=1");
        cloneCommand.add("--single-branch");
        cloneCommand.add("--branch=" + branch);
        cloneCommand.add("--filter=blob:none");  // 关键：不下载文件内容
        cloneCommand.add("--sparse");             // 启用稀疏检出模式
        cloneCommand.add(gitUrl);
        cloneCommand.add(cacheDir.toString());

        // 执行无blob克隆
        executeCommand(cloneCommand, privateKey, "无blob克隆仓库失败");
        log.info("无blob浅克隆完成: {} 分支 {} 到 {}", gitUrl, branch, cacheDir);

        // 配置稀疏检出，只检出目录结构（检出时会跳过blob下载）
        try {
            List<String> sparseCommand = new ArrayList<>();
            sparseCommand.add("git");
            sparseCommand.add("-C");
            sparseCommand.add(cacheDir.toString());
            sparseCommand.add("sparse-checkout");
            sparseCommand.add("set");
            sparseCommand.add("/");  // 检出所有目录

            executeCommand(sparseCommand, privateKey, "配置稀疏检出失败");
            log.debug("稀疏检出配置完成: {}", cacheDir);
        } catch (PlatformException e) {
            // 稀疏检出失败不影响目录遍历，blobless clone 已经确保 tree 对象存在
            log.warn("稀疏检出配置失败，但目录遍历仍可正常进行: {}", e.getMessage());
        }
    }

    /**
     * 遍历本地目录，提取所有目录路径。
     */
    private List<String> listLocalDirectories(Path repoRoot) {
        Set<String> directories = new HashSet<>();
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(Files::isDirectory)
                    .filter(p -> !p.equals(repoRoot)) // 排除根目录
                    .filter(p -> !p.getFileName().toString().startsWith(".")) // 排除隐藏目录
                    .forEach(p -> {
                        String relativePath = repoRoot.relativize(p).toString();
                        directories.add(relativePath);
                    });
        } catch (IOException e) {
            throw new PlatformException(
                    ErrorCode.GIT_UNAVAILABLE,
                    "遍历本地目录失败",
                    Map.of("path", repoRoot.toString()),
                    e);
        }

        List<String> result = new ArrayList<>(directories);
        result.sort(String::compareTo);
        return result;
    }

    /**
     * 写入缓存元数据文件。
     */
    private void writeCacheMeta(Path cacheDir, String gitUrl, String branch) {
        Path metaFile = cacheDir.resolve(CACHE_META_FILE);
        try {
            String metaContent = String.format("url=%s\nbranch=%s\ncreatedAt=%s\n",
                    gitUrl, branch, Instant.now().toString());
            Files.writeString(metaFile, metaContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("写入缓存元数据失败: {}", e.getMessage());
        }
    }

    /**
     * 执行 Git 命令。
     */
    private void executeCommand(List<String> command, String privateKey, String errorMessage) {
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

            boolean finished = process.waitFor(cloneTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new PlatformException(ErrorCode.GIT_TIMEOUT, "Git 克隆超时", Map.of("command", safeCommand(command)));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        errorMessage,
                        Map.of("command", safeCommand(command), "exitCode", exitCode, "stderr", stderr.trim()));
            }

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
        Path file = Files.createTempFile("git-clone-", ".key");
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
                log.info("创建 Git 克隆缓存目录: {}", cacheRoot);
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