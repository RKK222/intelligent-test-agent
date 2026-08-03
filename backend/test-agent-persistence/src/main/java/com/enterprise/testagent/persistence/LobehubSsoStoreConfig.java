package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.lobehub.LobehubSsoStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 装配共享 Redis 中使用独立前缀的 LobeHub SSO 存储。 */
@Configuration
public class LobehubSsoStoreConfig {

    /** 不提供 JVM 内存降级，Redis 不可用时 SSO 应 fail closed。 */
    @Bean
    public LobehubSsoStore lobehubSsoStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisLobehubSsoStore(redisTemplate, objectMapper);
    }
}
