package com.icbc.testagent.opencode.runtime.run;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenCode 原生会话命名的兜底策略；标题语义仍由 OpenCode 内置 title agent 决定。
 */
@Component
@ConfigurationProperties(prefix = "test-agent.opencode.session-title")
public class OpencodeSessionTitleProperties {

    private boolean fallbackEnabled = true;
    private Duration fallbackTimeout = Duration.ofSeconds(5);
    private Duration fallbackPollInterval = Duration.ofMillis(100);

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public Duration getFallbackTimeout() {
        return fallbackTimeout;
    }

    public void setFallbackTimeout(Duration fallbackTimeout) {
        this.fallbackTimeout = fallbackTimeout;
    }

    public Duration getFallbackPollInterval() {
        return fallbackPollInterval;
    }

    public void setFallbackPollInterval(Duration fallbackPollInterval) {
        this.fallbackPollInterval = fallbackPollInterval;
    }
}
