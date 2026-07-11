package com.icbc.testagent.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.run.RunTerminalRetryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 终态 PostgreSQL 故障重试装配；Redis 是唯一队列实现，不提供数据库或 JVM 内存降级。 */
@Configuration
public class RunTerminalRetryStoreConfig {

    /** 注入独立于 Run 详情数据面的安全投影重试存储。 */
    @Bean
    public RunTerminalRetryStore runTerminalRetryStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisRunTerminalRetryStore(redisTemplate, objectMapper);
    }
}
