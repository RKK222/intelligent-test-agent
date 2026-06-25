package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 运行进程心跳存储装配；Redis 可用时跨实例共享，未启用时回退到数据库快照判断。
 */
@Configuration
public class OpencodeProcessHeartbeatStoreConfig {

    /**
     * Redis 启用时创建跨实例进程心跳存储。
     */
    @Bean
    @ConditionalOnProperty(prefix = "test-agent.redis", name = "enabled", havingValue = "true")
    public OpencodeProcessHeartbeatStore redisOpencodeProcessHeartbeatStore(StringRedisTemplate redisTemplate) {
        return new RedisOpencodeProcessHeartbeatStore(redisTemplate);
    }

    /**
     * Redis 未启用时创建 no-op 实现，避免业务服务需要判断 Bean 是否存在。
     */
    @Bean
    @ConditionalOnMissingBean(OpencodeProcessHeartbeatStore.class)
    public OpencodeProcessHeartbeatStore noopOpencodeProcessHeartbeatStore() {
        return new NoopOpencodeProcessHeartbeatStore();
    }
}
