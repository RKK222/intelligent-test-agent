package com.icbc.testagent.api.config;

import com.icbc.testagent.api.web.platform.ManagerControlWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/**
 * 注册 opencode-manager 内部控制面 WebSocket URL。
 */
@Configuration
public class ManagerControlWebSocketConfig {

    /**
     * manager 控制面只暴露 internal platform URL，不提供旧兼容路径。
     */
    @Bean
    HandlerMapping managerControlWebSocketHandlerMapping(ManagerControlWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/api/internal/platform/opencode-runtime/manager/ws", handler));
        mapping.setOrder(-2);
        return mapping;
    }
}
