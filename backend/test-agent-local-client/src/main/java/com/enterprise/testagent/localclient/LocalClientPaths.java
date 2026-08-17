package com.enterprise.testagent.localclient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 按登录用户和操作系统解析客户端配置、状态与日志目录。 */
final class LocalClientPaths {

    private LocalClientPaths() {
    }

    static Path configDirectory() {
        String override = System.getenv("TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (isMac()) {
            return userHome.resolve("Library/Application Support/TestAgent/local-opencode-client");
        }
        String xdgConfig = System.getenv("XDG_CONFIG_HOME");
        Path configHome = xdgConfig == null || xdgConfig.isBlank()
                ? userHome.resolve(".config")
                : Path.of(xdgConfig).toAbsolutePath().normalize();
        return configHome.resolve("testagent/local-opencode-client");
    }

    static Path stateDirectory() {
        String override = System.getenv("TEST_AGENT_LOCAL_CLIENT_STATE_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (isMac()) {
            return userHome.resolve("Library/Application Support/TestAgent/local-opencode-client/state");
        }
        String xdgState = System.getenv("XDG_STATE_HOME");
        Path stateHome = xdgState == null || xdgState.isBlank()
                ? userHome.resolve(".local/state")
                : Path.of(xdgState).toAbsolutePath().normalize();
        return stateHome.resolve("testagent/local-opencode-client");
    }

    static Path logsDirectory() {
        return stateDirectory().resolve("logs");
    }

    static Path downloadsDirectory() {
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path downloads = userHome.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : userHome;
    }

    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
