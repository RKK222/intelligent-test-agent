package com.example.testagent.persistence;

import com.example.testagent.domain.auth.TokenStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Token 存储的 Spring 配置。当 Redis 启用时使用 RedisTokenStore，否则回退到 InMemoryTokenStore。
 */
@Configuration
public class TokenStoreConfig {

    /**
     * Redis 启用时创建 RedisTokenStore Bean。
     */
    @Bean
    @ConditionalOnProperty(prefix = "test-agent.redis", name = "enabled", havingValue = "true")
    public TokenStore redisTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisTokenStore(redisTemplate, objectMapper);
    }

    /**
     * Redis 未启用时创建 InMemoryTokenStore Bean 作为降级。
     */
    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore inMemoryTokenStore() {
        return new InMemoryTokenStore();
    }
}
