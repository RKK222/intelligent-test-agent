package com.enterprise.testagent.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Token 存储的 Spring 配置；Redis 是系统必需依赖，认证 Token 固定写入 Redis。
 */
@Configuration
public class TokenStoreConfig {

    /**
     * 创建 RedisTokenStore Bean。
     */
    @Bean
    public RedisTokenStore redisTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisTokenStore(redisTemplate, objectMapper);
    }
}
