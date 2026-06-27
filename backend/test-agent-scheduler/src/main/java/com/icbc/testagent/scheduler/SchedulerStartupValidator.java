package com.icbc.testagent.scheduler;

import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动期校验调度扫描依赖。启用 scheduler 时必须存在 RedisTemplate。
 */
@Component
public class SchedulerStartupValidator implements SmartInitializingSingleton {

    private final SchedulerProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /**
     * 注入调度配置和 RedisTemplate Provider，避免启动校验阶段提前建立连接。
     */
    public SchedulerStartupValidator(
            SchedulerProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.redisTemplateProvider = Objects.requireNonNull(redisTemplateProvider, "redisTemplateProvider must not be null");
    }

    /**
     * 校验启用调度时不能走本机锁或静默降级。
     */
    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    /**
     * 暴露给单元测试直接验证启动约束。
     */
    public void validate() {
        if (!properties.isEnabled()) {
            return;
        }
        if (redisTemplateProvider.getIfAvailable() == null) {
            throw new IllegalStateException("test-agent.scheduler.enabled=true requires StringRedisTemplate");
        }
    }
}
