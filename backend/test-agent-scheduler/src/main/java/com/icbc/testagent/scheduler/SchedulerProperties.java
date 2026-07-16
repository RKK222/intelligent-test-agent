package com.icbc.testagent.scheduler;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 定时任务框架配置。字段安全回退为关闭，应用入口通过 application.yml 将部署默认值绑定为开启。
 */
@Component
@ConfigurationProperties(prefix = "test-agent.scheduler")
public class SchedulerProperties {

    private boolean enabled = false;
    private Duration scanInterval = Duration.ofSeconds(30);
    private int dueTaskLimit = 50;
    private int manualRunLimit = 50;
    private String instanceId = "backend-" + UUID.randomUUID();

    /**
     * 返回是否启用后台扫描线程。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 绑定是否启用后台扫描线程。
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = Boolean.TRUE.equals(enabled);
    }

    /**
     * 返回后台扫描间隔。
     */
    public Duration getScanInterval() {
        return scanInterval;
    }

    /**
     * 绑定后台扫描间隔，必须为正数。
     */
    public void setScanInterval(Duration scanInterval) {
        if (scanInterval == null || scanInterval.isZero() || scanInterval.isNegative()) {
            throw new IllegalArgumentException("scanInterval must be positive");
        }
        this.scanInterval = scanInterval;
    }

    /**
     * 返回单轮最多扫描的 due task 数量。
     */
    public int getDueTaskLimit() {
        return dueTaskLimit;
    }

    /**
     * 绑定单轮最多扫描的 due task 数量。
     */
    public void setDueTaskLimit(int dueTaskLimit) {
        if (dueTaskLimit < 1) {
            throw new IllegalArgumentException("dueTaskLimit must be positive");
        }
        this.dueTaskLimit = dueTaskLimit;
    }

    /**
     * 返回单轮最多处理的手动触发 pending 运行数。
     */
    public int getManualRunLimit() {
        return manualRunLimit;
    }

    /**
     * 绑定单轮最多处理的手动触发 pending 运行数。
     */
    public void setManualRunLimit(int manualRunLimit) {
        if (manualRunLimit < 1) {
            throw new IllegalArgumentException("manualRunLimit must be positive");
        }
        this.manualRunLimit = manualRunLimit;
    }

    /**
     * 返回当前后端实例 ID，会写入运行记录 owner_instance_id。
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * 绑定当前后端实例 ID。
     */
    public void setInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId must not be blank");
        }
        this.instanceId = instanceId.trim();
    }
}
