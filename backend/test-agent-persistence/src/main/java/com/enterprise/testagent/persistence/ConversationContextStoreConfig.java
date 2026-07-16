package com.enterprise.testagent.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 会话运行上下文存储装配；Redis 是唯一实现，不提供数据库或 JVM 内存降级。
 */
@Configuration
public class ConversationContextStoreConfig {

    /**
     * 创建 Redis 会话上下文存储 Bean。
     */
    @Bean
    public ConversationContextStore conversationContextStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisConversationContextStore(redisTemplate, objectMapper);
    }
}
