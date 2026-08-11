package com.enterprise.testagent.memory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 通用长期记忆配置；检索总预算、学习超时和 HMAC 边界集中管理。 */
@Component
@ConfigurationProperties(prefix = "test-agent.memory")
public class QaMemoryProperties {
    private boolean enabled;
    private String serviceUrl = "http://127.0.0.1:18888";
    private String serviceApiKey = "";
    private Duration requestTimeout = Duration.ofSeconds(2);
    private Duration learningTimeout = Duration.ofSeconds(130);
    private Duration retrievalTimeout = Duration.ofSeconds(2);
    private int maxInjectedMemories = 6;
    private int maxContextTokens = 800;
    private int retrievalTopK = 20;
    private double retrievalThreshold = 0.10d;
    private int learningBatchSize = 8;
    private int learningMaxAttempts = 8;
    private Duration learningPollInterval = Duration.ofSeconds(5);
    private Duration learningLease = Duration.ofMinutes(5);
    private String modelGatewayHmacClientId = "mem0-cluster";
    private String modelGatewayHmacSecret = "";
    private Duration modelGatewayHmacClockSkew = Duration.ofSeconds(30);
    private Duration modelGatewayNonceTtl = Duration.ofMinutes(2);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
    public String getServiceApiKey() { return serviceApiKey; }
    public void setServiceApiKey(String serviceApiKey) { this.serviceApiKey = serviceApiKey; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration value) { requestTimeout = positive(value, "requestTimeout"); }
    public Duration getLearningTimeout() { return learningTimeout; }
    public void setLearningTimeout(Duration value) { learningTimeout = positive(value, "learningTimeout"); }
    public Duration getRetrievalTimeout() { return retrievalTimeout; }
    public void setRetrievalTimeout(Duration value) { retrievalTimeout = positive(value, "retrievalTimeout"); }
    public int getMaxInjectedMemories() { return maxInjectedMemories; }
    public void setMaxInjectedMemories(int value) { maxInjectedMemories = positive(value, "maxInjectedMemories"); }
    public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int value) { maxContextTokens = positive(value, "maxContextTokens"); }
    public int getRetrievalTopK() { return retrievalTopK; }
    public void setRetrievalTopK(int value) { retrievalTopK = positive(value, "retrievalTopK"); }
    public double getRetrievalThreshold() { return retrievalThreshold; }
    public void setRetrievalThreshold(double value) { retrievalThreshold = probability(value, "retrievalThreshold"); }
    public int getLearningBatchSize() { return learningBatchSize; }
    public void setLearningBatchSize(int value) { learningBatchSize = positive(value, "learningBatchSize"); }
    public int getLearningMaxAttempts() { return learningMaxAttempts; }
    public void setLearningMaxAttempts(int value) { learningMaxAttempts = positive(value, "learningMaxAttempts"); }
    public Duration getLearningPollInterval() { return learningPollInterval; }
    public void setLearningPollInterval(Duration value) { learningPollInterval = positive(value, "learningPollInterval"); }
    public Duration getLearningLease() { return learningLease; }
    public void setLearningLease(Duration value) { learningLease = positive(value, "learningLease"); }
    public String getModelGatewayHmacClientId() { return modelGatewayHmacClientId; }
    public void setModelGatewayHmacClientId(String value) { modelGatewayHmacClientId = required(value, "modelGatewayHmacClientId"); }
    public String getModelGatewayHmacSecret() { return modelGatewayHmacSecret; }
    public void setModelGatewayHmacSecret(String value) { modelGatewayHmacSecret = value == null ? "" : value; }
    public Duration getModelGatewayHmacClockSkew() { return modelGatewayHmacClockSkew; }
    public void setModelGatewayHmacClockSkew(Duration value) {
        modelGatewayHmacClockSkew = positive(value, "modelGatewayHmacClockSkew");
    }
    public Duration getModelGatewayNonceTtl() { return modelGatewayNonceTtl; }
    public void setModelGatewayNonceTtl(Duration value) { modelGatewayNonceTtl = positive(value, "modelGatewayNonceTtl"); }

    public byte[] requireModelGatewayHmacSecret() {
        byte[] secret = modelGatewayHmacSecret.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("启用长期记忆时 model-gateway-hmac-secret 至少需要 32 字节");
        }
        return secret;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static int positive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static double probability(double value, String field) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
