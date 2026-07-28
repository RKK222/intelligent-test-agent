package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 应用源码物理路径守卫：所有已存在祖先和目标都必须是不跟随链接的真实目录项。 */
final class AppSourcePathGuard {

    private AppSourcePathGuard() {
    }

    /** 以目标父目录作为配置根，并从其父级真实锚点开始逐段拒绝配置根、目标或祖先符号链接。 */
    static Path requireSafe(Path target) {
        Path normalized = Objects.requireNonNull(target, "target must not be null")
                .toAbsolutePath()
                .normalize();
        Path trustedRoot = normalized.getParent();
        if (trustedRoot == null) {
            throw forbidden();
        }
        return requireSafe(trustedRoot, normalized);
    }

    /**
     * 配置根的既有父级允许包含操作系统受信链接，先解析为 real path；配置根自身及其下运行态
     * 可替换组件都使用 NOFOLLOW_LINKS，避免把 macOS `/var` 等系统路径误判为应用源码攻击面。
     */
    static Path requireSafe(Path trustedRoot, Path target) {
        Path normalizedRoot = Objects.requireNonNull(trustedRoot, "trustedRoot must not be null")
                .toAbsolutePath()
                .normalize();
        Path normalizedTarget = Objects.requireNonNull(target, "target must not be null")
                .toAbsolutePath()
                .normalize();
        if (!normalizedTarget.startsWith(normalizedRoot) || normalizedTarget.equals(normalizedRoot)) {
            throw forbidden();
        }
        Path existingRoot = normalizedRoot.getParent();
        if (existingRoot == null) {
            throw forbidden();
        }
        while (!Files.exists(existingRoot, LinkOption.NOFOLLOW_LINKS)) {
            existingRoot = existingRoot.getParent();
            if (existingRoot == null) {
                throw forbidden();
            }
        }
        Path current;
        try {
            current = existingRoot.toRealPath();
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "应用源码配置根不可用", Map.of(), exception);
        }
        List<Path> fragments = new java.util.ArrayList<>();
        existingRoot.relativize(normalizedTarget).forEach(fragments::add);
        for (int index = 0; index < fragments.size(); index++) {
            Path fragment = fragments.get(index);
            current = current.resolve(fragment);
            if (Files.isSymbolicLink(current)) {
                throw forbidden();
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            boolean targetEntry = index == fragments.size() - 1;
            if (!targetEntry && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw forbidden();
            }
            if (!targetEntry) {
                try {
                    current.toRealPath(LinkOption.NOFOLLOW_LINKS);
                } catch (IOException exception) {
                    throw new PlatformException(
                            ErrorCode.INTERNAL_ERROR, "校验应用源码托管路径失败", Map.of(), exception);
                }
            }
        }
        return normalizedTarget;
    }

    private static PlatformException forbidden() {
        return new PlatformException(ErrorCode.FORBIDDEN, "应用源码托管路径包含符号链接");
    }
}
