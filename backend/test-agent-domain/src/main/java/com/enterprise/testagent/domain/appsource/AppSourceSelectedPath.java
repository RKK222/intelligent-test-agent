package com.enterprise.testagent.domain.appsource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/** 快照内冻结的仓库相对路径；禁止绝对路径和父目录穿越。 */
public record AppSourceSelectedPath(String path, AppSourcePathType pathType) {

    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:/.*");

    public AppSourceSelectedPath {
        Objects.requireNonNull(pathType, "pathType must not be null");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("selected path must not be blank");
        }
        String normalizedText = path.trim().replace('\\', '/');
        Path normalized = Path.of(normalizedText).normalize();
        String normalizedValue = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute()
                || WINDOWS_ABSOLUTE.matcher(normalizedText).matches()
                || normalizedValue.equals("..")
                || normalizedValue.startsWith("../")) {
            throw new IllegalArgumentException("selected path must stay inside repository");
        }
        path = normalizedValue;
    }
}
