package com.enterprise.testagent.api.config;

import com.enterprise.testagent.api.web.platform.AgentConfigOperationWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/**
 * Agent 配置进度 WebSocket 入口，只暴露 ticket 保护的 operation 路径。
 */
@Configuration
public class AgentConfigWebSocketConfig {

    @Bean
    HandlerMapping agentConfigWebSocketHandlerMapping(AgentConfigOperationWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/api/internal/platform/workspace-management/agent-config/operations/*/ws", handler));
        mapping.setOrder(-1);
        return mapping;
    }
}
