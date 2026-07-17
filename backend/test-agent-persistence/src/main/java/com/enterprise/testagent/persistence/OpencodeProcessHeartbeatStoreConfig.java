package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 运行进程心跳存储装配；Redis 是系统必需依赖，运行态快照固定跨实例共享。
 */
@Configuration
public class OpencodeProcessHeartbeatStoreConfig {

    /**
     * 创建跨实例进程心跳存储。
     */
    @Bean
    public OpencodeProcessHeartbeatStore redisOpencodeProcessHeartbeatStore(StringRedisTemplate redisTemplate) {
        return new RedisOpencodeProcessHeartbeatStore(redisTemplate);
    }
}
