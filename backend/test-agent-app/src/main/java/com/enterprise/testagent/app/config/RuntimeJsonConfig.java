package com.enterprise.testagent.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为持久化层和 WebFilter 提供统一 Jackson ObjectMapper，避免运行态缺少 JSON 序列化基础 bean。
 */
@Configuration
public class RuntimeJsonConfig {

    /**
     * 提供全局 ObjectMapper 并注册 Java Time 等模块，供持久化 JSON payload 使用。
     */
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
