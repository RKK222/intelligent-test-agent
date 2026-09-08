package com.enterprise.testagent.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为持久化层和 WebFilter 提供统一 Jackson ObjectMapper，避免运行态缺少 JSON 序列化基础 bean。
 */
@Configuration
public class RuntimeJsonConfig {

    /**
     * 提供全局 ObjectMapper 并注册 Java Time 等模块，供持久化 JSON payload 使用。
     *
     * <p>禁用 {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS}，让 {@link java.time.Instant}
     * 等时间类型序列化为 ISO 8601 字符串而非 epoch 数字，保证前端契约校验和跨语言互操作一致。
     * 已有数据库中以 epoch 数字存储的 JSON payload 反序列化不受影响，JavaTimeModule 兼容两种格式。</p>
     */
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
