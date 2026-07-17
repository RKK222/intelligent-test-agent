package com.enterprise.testagent.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 运行装配，供后端 Java 间 SSE 转发等基础设施复用。
 */
@Configuration
public class WebClientConfig {

    /**
     * 提供可注入的 WebClient.Builder，避免业务组件各自创建不可统一配置的 builder。
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
