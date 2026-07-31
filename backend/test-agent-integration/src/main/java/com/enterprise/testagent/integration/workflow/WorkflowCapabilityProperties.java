package com.enterprise.testagent.integration.workflow;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Python workflow与Runner的固定服务身份、安全时限和目标配置。 */
@Component
@ConfigurationProperties(prefix = "test-agent.workflow-capability")
public class WorkflowCapabilityProperties {

    private String hmacSecret;
    private String runnerHmacSecret;
    private String runnerId;
    private String runnerPublicKey;
    private String runnerPublicKeyPath;
    private Duration hmacClockSkew = Duration.ofSeconds(30);
    private Duration nonceTtl = Duration.ofSeconds(60);
    private Duration checkoutTicketTtl = Duration.ofMinutes(2);
    private Duration modelGrantTtl = Duration.ofMinutes(5);
    private String modelGatewayUrl;

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String getRunnerHmacSecret() {
        return runnerHmacSecret;
    }

    public void setRunnerHmacSecret(String runnerHmacSecret) {
        this.runnerHmacSecret = runnerHmacSecret;
    }

    public String getRunnerId() {
        return runnerId;
    }

    public void setRunnerId(String runnerId) {
        this.runnerId = runnerId;
    }

    public String getRunnerPublicKey() {
        return runnerPublicKey;
    }

    public void setRunnerPublicKey(String runnerPublicKey) {
        this.runnerPublicKey = runnerPublicKey;
    }

    public String getRunnerPublicKeyPath() {
        return runnerPublicKeyPath;
    }

    public void setRunnerPublicKeyPath(String runnerPublicKeyPath) {
        this.runnerPublicKeyPath = runnerPublicKeyPath;
    }

    /** 企业EnvironmentFile无法可靠承载多行PEM，允许从只读绝对路径加载Runner公钥。 */
    public String configuredRunnerPublicKey() {
        boolean hasInline = runnerPublicKey != null && !runnerPublicKey.isBlank();
        boolean hasPath = runnerPublicKeyPath != null && !runnerPublicKeyPath.isBlank();
        if (hasInline == hasPath) {
            throw new IllegalStateException("runnerPublicKey与runnerPublicKeyPath必须且只能配置一个");
        }
        if (hasInline) {
            return validatePublicKey(runnerPublicKey);
        }
        Path path = Path.of(runnerPublicKeyPath);
        if (!path.isAbsolute() || !Files.isRegularFile(path)) {
            throw new IllegalStateException("Runner公钥文件必须是存在的绝对普通文件");
        }
        try {
            return validatePublicKey(Files.readString(path, StandardCharsets.US_ASCII));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取Runner公钥文件", exception);
        }
    }

    public Duration getHmacClockSkew() {
        return hmacClockSkew;
    }

    public void setHmacClockSkew(Duration hmacClockSkew) {
        this.hmacClockSkew = positive(hmacClockSkew, "hmacClockSkew");
    }

    public Duration getNonceTtl() {
        return nonceTtl;
    }

    public void setNonceTtl(Duration nonceTtl) {
        this.nonceTtl = positive(nonceTtl, "nonceTtl");
    }

    public Duration getCheckoutTicketTtl() {
        return checkoutTicketTtl;
    }

    public void setCheckoutTicketTtl(Duration checkoutTicketTtl) {
        this.checkoutTicketTtl = positive(checkoutTicketTtl, "checkoutTicketTtl");
    }

    public Duration getModelGrantTtl() {
        return modelGrantTtl;
    }

    public void setModelGrantTtl(Duration modelGrantTtl) {
        this.modelGrantTtl = positive(modelGrantTtl, "modelGrantTtl");
    }

    public String getModelGatewayUrl() {
        String value = modelGatewayUrl == null ? "" : modelGatewayUrl.strip();
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("模型网关必须配置为绝对HTTP地址", exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getPath() == null
                || uri.getPath().isBlank()) {
            throw new IllegalStateException("模型网关必须配置为绝对HTTP地址");
        }
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("模型网关地址不得包含凭据、查询或片段");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public void setModelGatewayUrl(String modelGatewayUrl) {
        this.modelGatewayUrl = modelGatewayUrl;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String validatePublicKey(String value) {
        String normalized = value.strip() + System.lineSeparator();
        if (normalized.getBytes(StandardCharsets.US_ASCII).length > 16 * 1024
                || !normalized.contains("-----BEGIN PUBLIC KEY-----")
                || !normalized.contains("-----END PUBLIC KEY-----")) {
            throw new IllegalStateException("Runner公钥不是受支持的PEM公钥");
        }
        return normalized;
    }
}
