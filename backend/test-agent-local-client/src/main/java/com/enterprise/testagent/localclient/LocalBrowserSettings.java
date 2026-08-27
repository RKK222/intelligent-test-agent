package com.enterprise.testagent.localclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** 本地浏览器非敏感配置与麒麟桌面入口发现；浏览器 profile 固定由客户端管理。 */
final class LocalBrowserSettings {

    private static final String SETTINGS_FILE = "browser.properties";
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final List<Path> KYLIN_CANDIDATES = List.of(
            Path.of("/opt/apps/com.qihoo.browser/files/360browser"),
            Path.of("/opt/apps/com.qihoo.browser/files/360chrome"),
            Path.of("/opt/360browser/360browser"),
            Path.of("/usr/bin/360browser"),
            Path.of("/usr/bin/360chrome"));

    private final Path configDirectory;
    private final Path stateDirectory;

    LocalBrowserSettings(Path configDirectory, Path stateDirectory) {
        this.configDirectory = configDirectory.toAbsolutePath().normalize();
        this.stateDirectory = stateDirectory.toAbsolutePath().normalize();
    }

    static LocalBrowserSettings production() {
        return new LocalBrowserSettings(LocalClientPaths.configDirectory(), LocalClientPaths.stateDirectory());
    }

    static boolean supportedPlatform(LocalClientPlatform platform) {
        return platform != null
                && platform.family() == LocalClientPlatform.Family.LINUX
                && "arm64".equals(platform.architecture());
    }

    Path profileDirectory() {
        return stateDirectory.resolve("browser/profile");
    }

    Path prepareProfileDirectory() throws IOException {
        Path profile = profileDirectory();
        Files.createDirectories(profile);
        if (Files.isSymbolicLink(profile) || !Files.isDirectory(profile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("浏览器 profile 目录无效");
        }
        try {
            Files.setPosixFilePermissions(profile, PRIVATE_DIRECTORY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // 麒麟使用 POSIX 权限；其它开发平台由用户目录 ACL 保护。
        }
        return profile;
    }

    Path configuredExecutable() {
        Properties properties = readProperties();
        String value = properties.getProperty("browserExecutable", "").trim();
        return value.isEmpty() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    /** 显式配置优先；未配置时只从受控候选和 360 desktop entry 中发现可执行文件。 */
    Path resolveExecutable() {
        Path configured = configuredExecutable();
        if (configured != null) {
            return requireExecutable(configured);
        }
        List<Path> candidates = new ArrayList<>(KYLIN_CANDIDATES);
        candidates.addAll(desktopEntryCandidates());
        return candidates.stream()
                .map(LocalBrowserSettings::canonicalCandidate)
                .filter(java.util.Objects::nonNull)
                .filter(LocalBrowserSettings::isExecutable)
                .distinct()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未发现可用的 360 浏览器，请从客户端托盘选择浏览器程序"));
    }

    void saveExecutable(Path executable) throws IOException {
        Path validated = requireExecutable(executable);
        Files.createDirectories(configDirectory);
        Properties properties = readProperties();
        properties.setProperty("browserExecutable", validated.toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        properties.store(output, "TestAgent local browser settings");
        writePrivateFile(configDirectory.resolve(SETTINGS_FILE), output.toByteArray());
    }

    private List<Path> desktopEntryCandidates() {
        Path userHome = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        List<Path> roots = List.of(
                userHome.resolve(".local/share/applications"),
                Path.of("/usr/share/applications"));
        List<Path> entries = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".desktop"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(entries::add);
            } catch (IOException ignored) {
                // 单个桌面目录不可读时继续检查系统候选，不扩大权限读取。
            }
        }
        List<Path> candidates = new ArrayList<>();
        for (Path entry : entries) {
            parseDesktopExecutable(entry).ifPresent(candidates::add);
        }
        return candidates;
    }

    static java.util.Optional<Path> parseDesktopExecutable(Path desktopFile) {
        try {
            if (Files.size(desktopFile) > 64 * 1024) {
                return java.util.Optional.empty();
            }
            String content = Files.readString(desktopFile, StandardCharsets.UTF_8);
            boolean brandedEntry = desktopFile.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*(?:360|qihoo).*")
                    || content.lines()
                            .filter(line -> line.startsWith("Name="))
                            .map(line -> line.toLowerCase(Locale.ROOT))
                            .anyMatch(line -> line.contains("360") || line.contains("qihoo"));
            for (String line : content.split("\\R")) {
                if (!line.startsWith("Exec=")) {
                    continue;
                }
                String command = line.substring(5).trim();
                String executable = firstDesktopArgument(command);
                if (executable == null || executable.isBlank()) {
                    return java.util.Optional.empty();
                }
                String normalizedExecutable = executable.toLowerCase(Locale.ROOT);
                if (!brandedEntry
                        && !normalizedExecutable.contains("360")
                        && !normalizedExecutable.contains("qihoo")) {
                    return java.util.Optional.empty();
                }
                Path path = Path.of(executable);
                return path.isAbsolute() ? java.util.Optional.of(path) : java.util.Optional.empty();
            }
        } catch (IOException | RuntimeException ignored) {
            // 非法 desktop entry 不影响其它候选。
        }
        return java.util.Optional.empty();
    }

    /** 只解析 desktop Exec 的第一个绝对路径参数，不执行 shell，也不展开变量。 */
    static String firstDesktopArgument(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < command.length(); index++) {
            char current = command.charAt(index);
            if (escaped) {
                value.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\' && quote != '\'') {
                escaped = true;
                continue;
            }
            if ((current == '\'' || current == '"')) {
                if (quote == 0) {
                    quote = current;
                    continue;
                }
                if (quote == current) {
                    quote = 0;
                    continue;
                }
            }
            if (Character.isWhitespace(current) && quote == 0) {
                break;
            }
            value.append(current);
        }
        return value.toString();
    }

    private Properties readProperties() {
        Properties properties = new Properties();
        Path file = configDirectory.resolve(SETTINGS_FILE);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("浏览器设置无法读取", exception);
        }
    }

    private static Path requireExecutable(Path candidate) {
        Path canonical = canonicalCandidate(candidate);
        if (canonical == null || !isExecutable(canonical)) {
            throw new IllegalArgumentException("选择的浏览器程序不可执行");
        }
        return canonical;
    }

    private static Path canonicalCandidate(Path candidate) {
        try {
            return candidate.toAbsolutePath().normalize().toRealPath();
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static boolean isExecutable(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }

    private static void writePrivateFile(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.setPosixFilePermissions(temporary, PRIVATE_FILE_PERMISSIONS);
            } catch (UnsupportedOperationException ignored) {
                // 麒麟使用 POSIX 权限；其它开发平台由用户目录 ACL 保护。
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
