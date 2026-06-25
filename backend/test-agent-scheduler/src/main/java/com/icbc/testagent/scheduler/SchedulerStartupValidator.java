package com.icbc.testagent.scheduler;

import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动期校验调度扫描依赖。启用 scheduler 时 Redis 必须显式启用并存在 RedisTemplate。
 */
@Component
public class SchedulerStartupValidator implements SmartInitializingSingleton {

    private final SchedulerProperties properties;
    private final Environment environment;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /**
     * 注入调度配置、环境配置和 RedisTemplate Provider，避免 Redis 关闭时提前建立连接。
     */
    public SchedulerStartupValidator(
            SchedulerProperties properties,
            Environment environment,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
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
        Boolean redisEnabled = environment.getProperty("test-agent.redis.enabled", Boolean.class, Boolean.FALSE);
        if (!Boolean.TRUE.equals(redisEnabled)) {
            throw new IllegalStateException("test-agent.scheduler.enabled=true requires test-agent.redis.enabled=true");
        }
        if (redisTemplateProvider.getIfAvailable() == null) {
            throw new IllegalStateException("test-agent.scheduler.enabled=true requires StringRedisTemplate");
        }
    }
}
