package com.enterprise.testagent.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Run 运行数据面装配；Redis 是唯一实现，不提供 PostgreSQL 或 JVM 内存降级。 */
@Configuration
public class RunRuntimeStoreConfig {

    @Bean
    public RunRuntimeStore runRuntimeStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisRunRuntimeStore(redisTemplate, objectMapper);
    }
}
