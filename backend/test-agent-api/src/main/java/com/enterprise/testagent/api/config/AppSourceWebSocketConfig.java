package com.enterprise.testagent.api.config;

import com.enterprise.testagent.api.web.platform.AppSourceOperationWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/** 注册 ticket 保护的应用源码操作进度 WebSocket。 */
@Configuration
public class AppSourceWebSocketConfig {

    @Bean
    HandlerMapping appSourceWebSocketHandlerMapping(AppSourceOperationWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of(
                "/api/internal/platform/workspace-management/app-source-operations/*/ws",
                handler));
        mapping.setOrder(-1);
        return mapping;
    }
}
