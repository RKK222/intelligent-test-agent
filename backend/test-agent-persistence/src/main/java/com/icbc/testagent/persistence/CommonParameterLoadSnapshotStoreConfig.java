package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshotStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 通用参数加载快照存储装配；Redis 是系统必需依赖，加载快照固定跨实例共享。
 */
@Configuration
public class CommonParameterLoadSnapshotStoreConfig {

    /**
     * 创建跨实例通用参数加载快照存储。
     */
    @Bean
    public CommonParameterLoadSnapshotStore redisCommonParameterLoadSnapshotStore(StringRedisTemplate redisTemplate) {
        return new RedisCommonParameterLoadSnapshotStore(redisTemplate);
    }
}
