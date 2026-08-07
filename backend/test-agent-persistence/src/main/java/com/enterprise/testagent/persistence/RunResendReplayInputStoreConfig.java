package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 装配重发精确输入 Redis 存储。 */
@Configuration
public class RunResendReplayInputStoreConfig {

    @Bean
    RunResendReplayInputStore runResendReplayInputStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisRunResendReplayInputStore(redisTemplate, objectMapper);
    }
}
