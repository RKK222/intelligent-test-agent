package com.icbc.testagent.api.config;

import com.icbc.testagent.api.web.platform.WorkspaceFileWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/**
 * 工作空间文件 RPC WebSocket 入口，只暴露 ticket 保护的内部平台路径。
 */
@Configuration
public class WorkspaceFileWebSocketConfig {

    /**
     * 注册文件 WebSocket 路径。
     */
    @Bean
    HandlerMapping workspaceFileWebSocketHandlerMapping(WorkspaceFileWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/api/internal/platform/workspace-management/file/ws", handler));
        mapping.setOrder(-1);
        return mapping;
    }
}
