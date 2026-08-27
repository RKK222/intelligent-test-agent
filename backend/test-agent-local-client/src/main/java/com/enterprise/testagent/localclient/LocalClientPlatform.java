package com.enterprise.testagent.localclient;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

/** 集中描述客户端宿主平台，避免注册、更新和运行路径各自猜测操作系统。 */
record LocalClientPlatform(String platform, String architecture, Family family) {

    enum Family {
        WINDOWS,
        MAC,
        LINUX
    }

    static LocalClientPlatform current() {
        return detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    static LocalClientPlatform detect(String osName, String osArchitecture) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        Family family;
        String platform;
        if (normalizedOs.contains("windows")) {
            family = Family.WINDOWS;
            platform = "windows";
        } else if (normalizedOs.contains("mac") || normalizedOs.contains("darwin")) {
            family = Family.MAC;
            platform = "darwin";
        } else {
            family = Family.LINUX;
            platform = "linux";
        }
        return new LocalClientPlatform(platform, normalizeArchitecture(osArchitecture), family);
    }

    private static String normalizeArchitecture(String architecture) {
        String normalized = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> normalized;
        };
    }

    boolean isWindows() {
        return family == Family.WINDOWS;
    }

    boolean isMac() {
        return family == Family.MAC;
    }

    Path javaExecutable(Path releaseRoot) {
        return releaseRoot.resolve(isWindows() ? "jdk/bin/java.exe" : "jdk/bin/java");
    }

    Path javacExecutable(Path releaseRoot) {
        return releaseRoot.resolve(isWindows() ? "jdk/bin/javac.exe" : "jdk/bin/javac");
    }

    Path managedJavaExecutable(Path releaseRoot) {
        return releaseRoot.resolve(isWindows() ? "jdk/bin/javaw.exe" : "jdk/bin/java");
    }

    Path opencodeExecutable(Path releaseRoot) {
        return releaseRoot.resolve(isWindows() ? "opencode/bin/opencode.exe" : "opencode/bin/opencode");
    }

    /** Windows 依赖 PE 扩展名和用户目录 ACL；POSIX 平台还必须保留执行位。 */
    boolean isExecutable(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                && (isWindows() || Files.isExecutable(path));
    }

    String displayName() {
        return isWindows() ? "windows-" + architecture : isMac() ? "macos-" + architecture : "linux-" + architecture;
    }
}
