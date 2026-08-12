package com.enterprise.testagent.localclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** 安装脚本写入的非敏感客户端配置；client key 必须放在独立私有文件。 */
record LocalClientConfiguration(
        URI serverBaseUri,
        String clientName,
        Path opencodeExecutable,
        Path opencodeConfigDirectory,
        Path opencodeDataDirectory,
        int portMin,
        int portMax,
        boolean allowInsecureControl) {

    static LocalClientConfiguration load() throws IOException {
        Path configDirectory = LocalClientPaths.configDirectory();
        Path configFile = configDirectory.resolve("client.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configFile)) {
            properties.load(input);
        }
        URI serverUri = URI.create(required(properties, "serverUrl"));
        boolean allowInsecure = Boolean.parseBoolean(properties.getProperty("allowInsecureControl", "false"));
        int portMin = integer(properties, "portMin", 4096);
        int portMax = integer(properties, "portMax", 4195);
        if (portMin < 1024 || portMax > 65535 || portMin > portMax) {
            throw new IllegalArgumentException("local OpenCode port range is invalid");
        }
        Path executable = Path.of(required(properties, "opencodeExecutable")).toAbsolutePath().normalize();
        Path opencodeConfig = Path.of(properties.getProperty(
                        "opencodeConfigDirectory", configDirectory.resolve("opencode-config").toString()))
                .toAbsolutePath().normalize();
        Path opencodeData = Path.of(properties.getProperty(
                        "opencodeDataDirectory", LocalClientPaths.stateDirectory().resolve("opencode-data").toString()))
                .toAbsolutePath().normalize();
        String defaultName = System.getProperty("user.name", "user") + "@" + hostname();
        return new LocalClientConfiguration(
                normalizeBaseUri(serverUri),
                properties.getProperty("clientName", defaultName).trim(),
                executable,
                opencodeConfig,
                opencodeData,
                portMin,
                portMax,
                allowInsecure);
    }

    LocalClientConfiguration {
        Objects.requireNonNull(serverBaseUri, "serverBaseUri must not be null");
        String scheme = serverBaseUri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !(allowInsecureControl && "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("serverUrl must use https unless explicitly allowing http");
        }
        if (serverBaseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("serverUrl must not contain user information");
        }
        if (clientName == null || clientName.isBlank() || clientName.length() > 255) {
            throw new IllegalArgumentException("clientName is invalid");
        }
        Objects.requireNonNull(opencodeExecutable, "opencodeExecutable must not be null");
        Objects.requireNonNull(opencodeConfigDirectory, "opencodeConfigDirectory must not be null");
        Objects.requireNonNull(opencodeDataDirectory, "opencodeDataDirectory must not be null");
    }

    URI websocketUri() {
        String scheme = "https".equalsIgnoreCase(serverBaseUri.getScheme()) ? "wss" : "ws";
        return URI.create(scheme + "://" + serverBaseUri.getRawAuthority()
                + "/api/internal/platform/local-opencode-client/connections/ws");
    }

    URI modelProxyUri(String suffix) {
        String normalizedSuffix = suffix == null || suffix.isBlank() ? "/" : suffix;
        if (!normalizedSuffix.startsWith("/")) {
            normalizedSuffix = "/" + normalizedSuffix;
        }
        return serverBaseUri.resolve(
                "/api/internal/platform/local-opencode-client/model-proxy/v1" + normalizedSuffix);
    }

    private static URI normalizeBaseUri(URI uri) {
        if (uri.getHost() == null || uri.getRawAuthority() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("serverUrl is invalid");
        }
        return URI.create(uri.getScheme() + "://" + uri.getRawAuthority());
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    private static String hostname() {
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || hostname.isBlank() ? "local" : hostname.trim();
    }
}
