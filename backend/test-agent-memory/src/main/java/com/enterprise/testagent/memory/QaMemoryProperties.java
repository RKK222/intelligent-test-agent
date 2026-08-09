package com.enterprise.testagent.memory;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 长期记忆统一策略配置；阈值、预算和超时不得散落在业务分支中。 */
@Component
@ConfigurationProperties(prefix = "test-agent.memory")
public class QaMemoryProperties {
    private boolean enabled;
    private String serviceUrl = "http://127.0.0.1:18888";
    private String serviceApiKey = "";
    private Duration requestTimeout = Duration.ofSeconds(5);
    private Duration retrievalTimeout = Duration.ofMillis(600);
    private Duration grantTtl = Duration.ofMinutes(2);
    private Duration implicitWindow = Duration.ofDays(90);
    private int implicitSessionThreshold = 3;
    private int teamSessionThreshold = 3;
    private int teamUserThreshold = 2;
    private int maxInjectedMemories = 6;
    private int maxContextTokens = 800;
    private int learningMaxAttempts = 8;
    private Duration learningPollInterval = Duration.ofSeconds(5);
    private Duration learningLease = Duration.ofMinutes(2);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
    public String getServiceApiKey() { return serviceApiKey; }
    public void setServiceApiKey(String serviceApiKey) { this.serviceApiKey = serviceApiKey; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = positive(requestTimeout, "requestTimeout"); }
    public Duration getRetrievalTimeout() { return retrievalTimeout; }
    public void setRetrievalTimeout(Duration retrievalTimeout) { this.retrievalTimeout = positive(retrievalTimeout, "retrievalTimeout"); }
    public Duration getGrantTtl() { return grantTtl; }
    public void setGrantTtl(Duration grantTtl) { this.grantTtl = positive(grantTtl, "grantTtl"); }
    public Duration getImplicitWindow() { return implicitWindow; }
    public void setImplicitWindow(Duration implicitWindow) { this.implicitWindow = positive(implicitWindow, "implicitWindow"); }
    public int getImplicitSessionThreshold() { return implicitSessionThreshold; }
    public void setImplicitSessionThreshold(int value) { implicitSessionThreshold = positive(value, "implicitSessionThreshold"); }
    public int getTeamSessionThreshold() { return teamSessionThreshold; }
    public void setTeamSessionThreshold(int value) { teamSessionThreshold = positive(value, "teamSessionThreshold"); }
    public int getTeamUserThreshold() { return teamUserThreshold; }
    public void setTeamUserThreshold(int value) { teamUserThreshold = positive(value, "teamUserThreshold"); }
    public int getMaxInjectedMemories() { return maxInjectedMemories; }
    public void setMaxInjectedMemories(int value) { maxInjectedMemories = positive(value, "maxInjectedMemories"); }
    public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int value) { maxContextTokens = positive(value, "maxContextTokens"); }
    public int getLearningMaxAttempts() { return learningMaxAttempts; }
    public void setLearningMaxAttempts(int value) { learningMaxAttempts = positive(value, "learningMaxAttempts"); }
    public Duration getLearningPollInterval() { return learningPollInterval; }
    public void setLearningPollInterval(Duration value) { learningPollInterval = positive(value, "learningPollInterval"); }
    public Duration getLearningLease() { return learningLease; }
    public void setLearningLease(Duration value) { learningLease = positive(value, "learningLease"); }

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
}
