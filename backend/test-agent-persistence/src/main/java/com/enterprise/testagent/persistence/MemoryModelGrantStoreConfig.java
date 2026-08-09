package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.memory.MemoryModelGrantStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 记忆抽取模型的一次性 Redis 授权装配。 */
@Configuration
public class MemoryModelGrantStoreConfig {
    @Bean
    public MemoryModelGrantStore memoryModelGrantStore(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisMemoryModelGrantStore(redisTemplate, objectMapper);
    }
}
