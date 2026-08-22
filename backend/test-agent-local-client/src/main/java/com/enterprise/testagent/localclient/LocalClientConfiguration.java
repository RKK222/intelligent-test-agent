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
        URI webBaseUri,
        String clientName,
        Path opencodeExecutable,
        Path opencodeConfigDirectory,
        Path opencodeDataDirectory,
        int portMin,
        int portMax,
        boolean allowInsecureControl,
        URI downloadBaseUri,
        String signingPublicKeyBase64,
        Path installRoot) {

    LocalClientConfiguration(
            URI serverBaseUri,
            String clientName,
            Path opencodeExecutable,
            Path opencodeConfigDirectory,
            Path opencodeDataDirectory,
            int portMin,
            int portMax,
            boolean allowInsecureControl) {
        this(
                serverBaseUri,
                serverBaseUri,
                clientName,
                opencodeExecutable,
                opencodeConfigDirectory,
                opencodeDataDirectory,
                portMin,
                portMax,
                allowInsecureControl,
                null,
                null,
                null);
    }

    /** 兼容 release 托盘可为“控制服务”和“打开网页”分别配置入口的既有调用方。 */
    LocalClientConfiguration(
            URI serverBaseUri,
            URI webBaseUri,
            String clientName,
            Path opencodeExecutable,
            Path opencodeConfigDirectory,
            Path opencodeDataDirectory,
            int portMin,
            int portMax,
            boolean allowInsecureControl) {
        this(
                serverBaseUri,
                webBaseUri,
                clientName,
                opencodeExecutable,
                opencodeConfigDirectory,
                opencodeDataDirectory,
                portMin,
                portMax,
                allowInsecureControl,
                null,
                null,
                null);
    }

    /** 兼容稳定启动器只配置平台根地址的场景，网页入口默认复用同一安全根地址。 */
    LocalClientConfiguration(
            URI serverBaseUri,
            String clientName,
            Path opencodeExecutable,
            Path opencodeConfigDirectory,
            Path opencodeDataDirectory,
            int portMin,
            int portMax,
            boolean allowInsecureControl,
            URI downloadBaseUri,
            String signingPublicKeyBase64,
            Path installRoot) {
        this(
                serverBaseUri,
                serverBaseUri,
                clientName,
                opencodeExecutable,
                opencodeConfigDirectory,
                opencodeDataDirectory,
                portMin,
                portMax,
                allowInsecureControl,
                downloadBaseUri,
                signingPublicKeyBase64,
                installRoot);
    }

    static LocalClientConfiguration load() throws IOException {
        Path configDirectory = LocalClientPaths.configDirectory();
        Path configFile = configDirectory.resolve("client.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configFile)) {
            properties.load(input);
        }
        URI serverUri = URI.create(required(properties, "serverUrl"));
        boolean allowInsecure = Boolean.parseBoolean(properties.getProperty("allowInsecureControl", "false"));
        URI webUri = URI.create(properties.getProperty("webUrl", serverUri.toString()).trim());
        int portMin = integer(properties, "portMin", 4096);
        int portMax = integer(properties, "portMax", 4195);
        if (portMin < 1024 || portMax > 65535 || portMin > portMax) {
            throw new IllegalArgumentException("local OpenCode port range is invalid");
        }
        Path executable = resolveOpencodeExecutable(
                properties,
                System.getProperty(LocalClientFirstRunSetup.PACKAGED_OPENCODE_PROPERTY, ""));
        Path opencodeConfig = Path.of(properties.getProperty(
                        "opencodeConfigDirectory", configDirectory.resolve("opencode-config").toString()))
                .toAbsolutePath().normalize();
        Path opencodeData = Path.of(properties.getProperty(
                        "opencodeDataDirectory", LocalClientPaths.stateDirectory().resolve("opencode-data").toString()))
                .toAbsolutePath().normalize();
        String defaultName = System.getProperty("user.name", "user") + "@" + hostname();
        String downloadBase = optional(properties, "downloadBaseUrl");
        String publicKeyBase64 = optional(properties, "signingPublicKeyBase64");
        String installRootValue = optional(properties, "installRoot");
        return new LocalClientConfiguration(
                normalizeBaseUri(serverUri),
                normalizeWebUri(webUri, allowInsecure),
                properties.getProperty("clientName", defaultName).trim(),
                executable,
                opencodeConfig,
                opencodeData,
                portMin,
                portMax,
                allowInsecure,
                downloadBase == null ? null : URI.create(downloadBase),
                publicKeyBase64,
                installRootValue == null ? null : Path.of(installRootValue).toAbsolutePath().normalize());
    }

    /**
     * 原生安装包每次升级都可能切换完整 release 目录，因此启动器明确传入的受管 OpenCode 路径
     * 必须覆盖首次安装时留下的旧配置；裸 JAR/麒麟稳定启动器未传该参数时仍严格读取配置文件。
     */
    static Path resolveOpencodeExecutable(Properties properties, String packagedExecutable) {
        String candidate = packagedExecutable == null ? "" : packagedExecutable.trim();
        if (candidate.isEmpty()) {
            candidate = required(properties, "opencodeExecutable");
        }
        return Path.of(candidate).toAbsolutePath().normalize();
    }

    LocalClientConfiguration {
        Objects.requireNonNull(serverBaseUri, "serverBaseUri must not be null");
        Objects.requireNonNull(webBaseUri, "webBaseUri must not be null");
        String scheme = serverBaseUri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !(allowInsecureControl && "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("serverUrl must use https unless explicitly allowing http");
        }
        if (serverBaseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("serverUrl must not contain user information");
        }
        String webScheme = webBaseUri.getScheme();
        if (!"https".equalsIgnoreCase(webScheme)
                && !(allowInsecureControl && "http".equalsIgnoreCase(webScheme))) {
            throw new IllegalArgumentException("webUrl must use https unless explicitly allowing http");
        }
        if (webBaseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("webUrl must not contain user information");
        }
        if (clientName == null || clientName.isBlank() || clientName.length() > 255) {
            throw new IllegalArgumentException("clientName is invalid");
        }
        Objects.requireNonNull(opencodeExecutable, "opencodeExecutable must not be null");
        Objects.requireNonNull(opencodeConfigDirectory, "opencodeConfigDirectory must not be null");
        Objects.requireNonNull(opencodeDataDirectory, "opencodeDataDirectory must not be null");
        boolean anySelfUpdate = downloadBaseUri != null || signingPublicKeyBase64 != null || installRoot != null;
        if (anySelfUpdate && (downloadBaseUri == null || signingPublicKeyBase64 == null || installRoot == null)) {
            throw new IllegalArgumentException("self-update configuration must include downloadBaseUrl, signingPublicKeyBase64 and installRoot");
        }
        if (downloadBaseUri != null) {
            String downloadScheme = downloadBaseUri.getScheme();
            if (downloadBaseUri.getHost() == null
                    || !("http".equalsIgnoreCase(downloadScheme) || "https".equalsIgnoreCase(downloadScheme))
                    || downloadBaseUri.getUserInfo() != null
                    || downloadBaseUri.getRawQuery() != null
                    || downloadBaseUri.getRawFragment() != null) {
                throw new IllegalArgumentException("self-update downloadBaseUrl is invalid");
            }
            if (signingPublicKeyBase64.isBlank()
                    || signingPublicKeyBase64.length() > 16 * 1024
                    || !signingPublicKeyBase64.matches("[A-Za-z0-9+/=]+")) {
                throw new IllegalArgumentException("self-update signing public key is invalid");
            }
            installRoot = installRoot.toAbsolutePath().normalize();
        }
    }

    boolean selfUpdateConfigured() {
        return downloadBaseUri != null && signingPublicKeyBase64 != null && installRoot != null;
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

    private static URI normalizeWebUri(URI uri, boolean allowInsecure) {
        URI normalized = normalizeBaseUri(uri);
        String scheme = normalized.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !(allowInsecure && "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("webUrl must use https unless explicitly allowing http");
        }
        return normalized;
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

    private static String optional(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String hostname() {
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || hostname.isBlank() ? "local" : hostname.trim();
    }
}
