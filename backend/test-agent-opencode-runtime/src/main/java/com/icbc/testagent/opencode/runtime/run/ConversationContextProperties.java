package com.icbc.testagent.opencode.runtime.run;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会话上下文兼容与灰度策略，由 runtime 模块直接绑定，避免运行编排反向依赖 app 配置类。
 */
@Component
@ConfigurationProperties(prefix = "test-agent.redis-summary")
public class ConversationContextProperties {

    private boolean enabled;
    private int rolloutPercentage;
    private boolean legacyRunWithoutContextEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(int rolloutPercentage) {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }
        this.rolloutPercentage = rolloutPercentage;
    }

    public boolean isLegacyRunWithoutContextEnabled() {
        return legacyRunWithoutContextEnabled;
    }

    public void setLegacyRunWithoutContextEnabled(boolean legacyRunWithoutContextEnabled) {
        this.legacyRunWithoutContextEnabled = legacyRunWithoutContextEnabled;
    }
}
