package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 用户 opencode 进程有效公共配置指针服务。
 *
 * <p>每个进程的 {@code OPENCODE_CONFIG_DIR} 固定指向 session 目录下的一个受管软链接：默认链接公共共享
 * 运行副本，公共个人 worktree 保存时只把当前用户链接切到个人 worktree。服务不复制配置、不创建 Git
 * worktree，也不修改 OpenCode 原生代码。
 */
@Service
public class OpencodeProcessConfigLinkService {

    static final String PUBLIC_CONFIG_PARAMETER = "OPENCODE_PUBLIC_CONFIG_DIR";
    private static final String RUNTIME_DIRECTORY = ".testagent-runtime";
    private static final String CONFIG_LINK = "current-public-config";
    private static final String NEXT_LINK_PREFIX = CONFIG_LINK + ".next-";

    private final CommonParameterValues commonParameterValues;
    private final Map<Path, Object> targetLocks = new ConcurrentHashMap<>();

    public OpencodeProcessConfigLinkService(CommonParameterValues commonParameterValues) {
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
    }

    /** 返回用户进程固定使用的 Git 外公共配置软链接路径。 */
    public static Path managedConfigPath(String sessionPath) {
        if (sessionPath == null || sessionPath.isBlank()) {
            throw new IllegalArgumentException("sessionPath must not be blank");
        }
        return Path.of(sessionPath)
                .toAbsolutePath()
                .normalize()
                .resolve(RUNTIME_DIRECTORY)
                .resolve(CONFIG_LINK);
    }

    /** 判断数据库中的进程配置路径是否已经使用当前受管指针模型。 */
    public boolean isManagedConfigPath(String sessionPath, String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return false;
        }
        return managedConfigPath(sessionPath).equals(Path.of(configPath).toAbsolutePath().normalize());
    }

    /** 启动或公共发布时，把指定用户的有效配置恢复到公共共享运行副本。 */
    public void switchToShared(String sessionPath, String targetConfigPath) {
        requireManagedTarget(sessionPath, targetConfigPath);
        switchTo(sharedConfigPath().toString(), targetConfigPath);
    }

    /** 判断升级前进程是否仍直接读取公共共享运行副本。 */
    public boolean isSharedConfigPath(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return false;
        }
        return sharedConfigPath().equals(Path.of(configPath).toAbsolutePath().normalize());
    }

    private Path sharedConfigPath() {
        String source = commonParameterValues.resolvedValue(PUBLIC_CONFIG_PARAMETER)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "通用参数未配置：" + PUBLIC_CONFIG_PARAMETER,
                        Map.of("parameter", PUBLIC_CONFIG_PARAMETER)));
        return Path.of(source).toAbsolutePath().normalize();
    }

    /**
     * 原子切换指定用户的有效公共配置软链接。
     *
     * <p>源目录由上层按公共共享根或本人公共 worktree 根校验。目标若是普通文件或目录则失败关闭，避免删除
     * 未知数据；不支持创建软链接的平台也明确报错，绝不降级为复制配置。
     */
    public void switchTo(String sourceConfigPath, String targetConfigPath) {
        Path source = requireSource(sourceConfigPath);
        Path target = requireTarget(targetConfigPath);
        if (source.equals(target) || source.startsWith(target)) {
            throw new PlatformException(ErrorCode.CONFLICT, "TestAgent 公共配置软链接源路径无效");
        }
        Object lock = targetLocks.computeIfAbsent(target, ignored -> new Object());
        synchronized (lock) {
            switchLink(source, target);
        }
    }

    private void switchLink(Path source, Path target) {
        Path parent = target.getParent();
        Path next = parent.resolve(NEXT_LINK_PREFIX + UUID.randomUUID());
        try {
            Files.createDirectories(parent);
            rejectUnmanagedTarget(target);
            cleanupStaleNextLinks(parent);
            if (pointsTo(target, source)) {
                return;
            }
            Files.createSymbolicLink(next, source);
            moveReplacing(next, target);
        } catch (PlatformException exception) {
            throw exception;
        } catch (UnsupportedOperationException | SecurityException exception) {
            copyDirectory(source, target);
        } catch (IOException exception) {
            copyDirectory(source, target);
        } finally {
            try {
                if (Files.isSymbolicLink(next)) {
                    Files.deleteIfExists(next);
                }
            } catch (IOException ignored) {
                // 下次切换会再次清理同一受管目录中的 next 软链接。
            }
        }
    }

    private void copyDirectory(Path source, Path target) {
        try {
            if (Files.exists(target)) {
                Files.walk(target)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // ignore
                            }
                        });
            }
            Files.createDirectories(target);
            Files.walk(source)
                    .forEach(sourcePath -> {
                        try {
                            Path targetPath = target.resolve(source.relativize(sourcePath));
                            if (Files.isDirectory(sourcePath)) {
                                Files.createDirectories(targetPath);
                            } else {
                                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        } catch (IOException e) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "切换当前用户 TestAgent 公共配置失败；软链接和复制均失败",
                    Map.of("configPath", target.toString()),
                    e);
        }
    }

    private void requireManagedTarget(String sessionPath, String targetConfigPath) {
        if (!isManagedConfigPath(sessionPath, targetConfigPath)) {
            throw new PlatformException(ErrorCode.CONFLICT, "TestAgent 进程未使用受管公共配置软链接");
        }
    }

    private Path requireSource(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "公共 Agent 配置源目录不可用");
        }
        Path source = Path.of(value).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source) || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "公共 Agent 配置源目录不可用");
        }
        try (var entries = Files.list(source)) {
            if (entries.findAny().isEmpty()) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "公共 Agent 配置源目录为空");
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "公共 Agent 配置源目录不可读", Map.of(), exception);
        }
        return source;
    }

    private Path requireTarget(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "TestAgent 有效公共配置路径未配置");
        }
        Path target = Path.of(value).toAbsolutePath().normalize();
        if (target.getParent() == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "TestAgent 有效公共配置路径无效");
        }
        return target;
    }

    private void rejectUnmanagedTarget(Path target) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target) && !Files.isDirectory(target)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "TestAgent 受管公共配置路径已被普通文件占用，请清理冲突后受管重启",
                    Map.of("configPath", target.toString()));
        }
    }

    private boolean pointsTo(Path link, Path expectedSource) throws IOException {
        if (!Files.isSymbolicLink(link)) {
            return false;
        }
        Path value = Files.readSymbolicLink(link);
        Path resolved = value.isAbsolute() ? value : link.getParent().resolve(value);
        return resolved.toAbsolutePath().normalize().equals(expectedSource);
    }

    private void cleanupStaleNextLinks(Path parent) throws IOException {
        try (var entries = Files.list(parent)) {
            for (Path entry : entries.filter(path -> path.getFileName().toString().startsWith(NEXT_LINK_PREFIX)).toList()) {
                if (Files.isSymbolicLink(entry)) {
                    Files.deleteIfExists(entry);
                }
            }
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            // 同目录 rename 在主流文件系统仍是单步替换；不支持 ATOMIC_MOVE 时只退化 rename，不复制内容。
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private PlatformException unsupportedLink(Exception exception) {
        return new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "当前平台不支持 TestAgent 受管公共配置软链接，请使用受管重启或调整服务器权限；不会复制配置",
                Map.of(),
                exception);
    }
}
