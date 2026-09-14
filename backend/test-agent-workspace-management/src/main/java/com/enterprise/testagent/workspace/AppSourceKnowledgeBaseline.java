package com.enterprise.testagent.workspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * 生成 APP_SOURCE 知识基线的同级隐藏路径。
 *
 * <p>基线按 generation 固定命名，不位于用户可编辑 Workspace 根目录内；staging 和 backup
 * 继续使用随机后缀，供物化发布和到期清理安全识别。</p>
 */
final class AppSourceKnowledgeBaseline {

    private AppSourceKnowledgeBaseline() {
    }

    static Path root(Path editableTarget, long generation) {
        requireGeneration(generation);
        Path target = AppSourcePathGuard.requireSafe(Objects.requireNonNull(editableTarget));
        return Objects.requireNonNull(target.getParent())
                .resolve("." + target.getFileName() + ".knowledge.g" + generation);
    }

    static Path staging(Path editableTarget, long generation) {
        return root(editableTarget, generation)
                .resolveSibling(root(editableTarget, generation).getFileName()
                        + "." + UUID.randomUUID() + ".staging");
    }

    static Path backup(Path baseline) {
        Path safe = AppSourcePathGuard.requireSafe(Objects.requireNonNull(baseline));
        return safe.resolveSibling(safe.getFileName() + "." + UUID.randomUUID() + ".backup");
    }

    static String generationPrefix(Path editableTarget, long generation) {
        return root(editableTarget, generation).getFileName().toString() + ".";
    }

    private static void requireGeneration(long generation) {
        if (generation < 1L) {
            throw new IllegalArgumentException("generation must be positive");
        }
    }
}
