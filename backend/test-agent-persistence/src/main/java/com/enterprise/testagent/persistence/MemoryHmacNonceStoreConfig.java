package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.memory.MemoryHmacNonceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 多节点 Java 共享的记忆模型网关 HMAC 防重放装配。 */
@Configuration
public class MemoryHmacNonceStoreConfig {
    @Bean
    public MemoryHmacNonceStore memoryHmacNonceStore(StringRedisTemplate redisTemplate) {
        return new RedisMemoryHmacNonceStore(redisTemplate);
    }
}
