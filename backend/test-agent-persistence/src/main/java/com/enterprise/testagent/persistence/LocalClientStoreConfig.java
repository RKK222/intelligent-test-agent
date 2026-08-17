package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrantStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 本地客户端短期连接和模型授权 Redis 端口装配。 */
@Configuration
public class LocalClientStoreConfig {

    @Bean
    LocalClientConnectionStore localClientConnectionStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisLocalClientConnectionStore(redisTemplate, objectMapper);
    }

    @Bean
    LocalClientModelGrantStore localClientModelGrantStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisLocalClientModelGrantStore(redisTemplate, objectMapper);
    }
}
