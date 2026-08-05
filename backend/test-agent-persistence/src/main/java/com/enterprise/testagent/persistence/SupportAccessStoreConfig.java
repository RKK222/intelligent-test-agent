package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.supportaccess.SupportAccessGrantStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 排查授权短期 Redis store 装配。 */
@Configuration
public class SupportAccessStoreConfig {

    @Bean
    public SupportAccessGrantStore supportAccessGrantStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisSupportAccessGrantStore(redisTemplate, objectMapper);
    }
}
